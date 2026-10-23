package com.deepseekharness.app.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.deepseekharness.app.DeviceBridgeService;
import com.deepseekharness.app.DshaAccessibilityService;
import com.deepseekharness.app.RootShell;
import com.deepseekharness.app.ShizukuShell;
import com.deepseekharness.app.bridge.AdbBridge;
import com.deepseekharness.app.core.ConfigStore;
import com.deepseekharness.app.core.DeviceGrants;
import com.deepseekharness.app.core.HarnessController;
import com.deepseekharness.app.util.Constants;
import com.deepseekharness.app.util.ScreenConfirmationPolicy;
import com.deepseekharness.app.util.UiText;

/** 设备通道、系统权限与能力预授权的唯一设置页；沿用历史偏好键。界面见 DeviceGrantsScreen.kt。 */
public final class DeviceGrantsFragment extends Fragment implements OwnTopBar {
  private final DeviceGrantsUi ui = new DeviceGrantsUi();
  private boolean rootProbing, shizukuProbing;
  private int adbStatusRequest;
  private FullPermissionControls fullPermissions;
  private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable refreshConnections =
      new Runnable() {
        @Override
        public void run() {
          if (!isResumed()) return;
          refreshChannelLabels();
          if (fullPermissions != null) fullPermissions.refresh();
          main.postDelayed(this, 1000);
        }
      };

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
    Context ctx = requireContext();
    fullPermissions = new FullPermissionControls(this, ui, this::refreshNativePolicyControls);
    ui.setVirtualSupported(com.deepseekharness.app.vscreen.VirtualScreenManager.supported(ctx));
    ui.setShizukuAuthText(UiText.text("授权 Shizuku"));
    refreshNativePolicyControls();
    return DeviceGrantsScreenKt.deviceGrantsPage(
        ctx,
        ui,
        new DeviceGrantsActions() {
          public void action(String key) {
            onAction(key);
          }

          public void toggle(String key, boolean on) {
            onToggle(key, on);
          }

          public void screenMode(int index) {
            var grants = new DeviceGrants(requireContext());
            if (!grants.setScreenPolicy(
                ScreenConfirmationPolicy.Mode.values()[index], grants.screenPolicy().rules))
              toast(UiText.text("授权保存失败，请重试"));
            refreshScreenPolicy();
          }
        });
  }

  private void onAction(String key) {
    Context ctx = requireContext();
    switch (key) {
      case "recover_on" -> fullPermissions.choose(true);
      case "recover_off" -> fullPermissions.choose(false);
      case "rebuild" -> fullPermissions.rebuild();
      case "diagnostics" -> fullPermissions.showDiagnostics();
      case "root_status" -> showStatus(ui.getRootStatus());
      case "shizuku_status" -> showStatus(ui.getShizukuStatus());
      case "adb_status" -> showStatus(ui.getAdbStatus());
      case "root_verify" -> verifyRoot(ctx);
      case "shizuku_auth" -> authorizeShizuku(ctx);
      case "shizuku_open" -> openShizuku();
      case "adb_pair" -> {
        if (android.os.Build.VERSION.SDK_INT < 30)
          new DshaDialogBuilder(ctx)
              .setTitle(UiText.text("当前系统没有配对码接口"))
              .setMessage(
                  UiText.text(
                      "无线调试配对码需要 Android 11+。Android 6—10 可使用 Shizuku、已授权的 root，或由电脑开启 ADB TCP 通道。"))
              .setPositiveButton(UiText.text("知道了"), null)
              .show();
        else startActivity(new Intent(ctx, AdbPairActivity.class));
      }
      case "accessibility" -> startActivity(new Intent(ctx, AccessibilitySetupActivity.class));
      case "screen_rules" -> showScreenRules();
      case "screen_pause" -> {
        com.deepseekharness.app.HttpShellService.setScreenPaused(
            ctx, !com.deepseekharness.app.HttpShellService.screenPaused());
        refreshScreenPolicy();
      }
      case "screen_revoke" -> {
        boolean persisted =
            new DeviceGrants(ctx).setScreenPolicy(ScreenConfirmationPolicy.Mode.MANUAL, java.util.Map.of());
        com.deepseekharness.app.HttpShellService.setScreenPaused(ctx, true);
        com.deepseekharness.app.HttpShellService.revokeScreenGrant(ctx);
        refreshScreenPolicy();
        toast(
            persisted
                ? UiText.choose(
                    "已暂停屏幕操作并撤销预授权；可点恢复后继续",
                    "Screen actions are paused and preauthorizations revoked. Use Resume to continue")
                : UiText.text("授权保存失败，请重试"));
      }
      case "virtual_screen" ->
          startActivity(new Intent(ctx, com.deepseekharness.app.vscreen.VirtualScreenActivity.class));
      case "all_files" -> openAllFilesAccess(ctx);
      case "battery" -> openBatteryOpt(ctx);
      default -> {}
    }
  }

  private void onToggle(String key, boolean enabled) {
    Context ctx = requireContext();
    switch (key) {
      case "global" -> fullPermissions.choose(enabled);
      case "root" -> {
        new ConfigStore(ctx).setRootShellAllowed(enabled);
        if (!enabled) com.deepseekharness.app.vscreen.VirtualScreenManager.from(ctx).revoke();
        ui.setRoot(enabled);
        syncSettings();
        refreshChannelLabels();
      }
      case "adb" -> {
        com.deepseekharness.app.HttpShellService.smsPolicyChanged(ctx);
        DeviceBridgeService.adbIntentChanging();
        savePreference("adb_enabled", enabled);
        ui.setAdb(pref(ctx, "adb_enabled", false));
        if (!enabled) com.deepseekharness.app.vscreen.VirtualScreenManager.from(ctx).revoke();
        if (enabled) {
          DeviceBridgeService.apply(ctx);
          if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).requestLocalNetwork();
        } else ctx.stopService(new Intent(ctx, DeviceBridgeService.class));
        syncSettings();
        refreshAdbStatus();
      }
      case "sensors" -> {
        savePreference("cap_sensors", enabled);
        ui.setSensors(pref(ctx, "cap_sensors", false));
      }
      case "location" -> {
        if (fullPermissions != null) fullPermissions.cancelForLocationChange();
        savePreference("cap_location", enabled);
        ui.setLocation(pref(ctx, "cap_location", false));
        if (enabled
            && ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED)
          requestPermissions(
              new String[] {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
              },
              104);
      }
      case "sms" -> {
        if (!enabled) {
          saveSms(false);
          return;
        }
        // 开关保持关闭，直到用户在确认框里同意。
        new DshaDialogBuilder(ctx, true)
            .setTitle(UiText.text("持续允许读取短信？"))
            .setMessage(
                UiText.text(
                    "当前环境内的助手和插件将能通过已授权的 root 或 ADB 查询短信，包括正文、号码和可能存在的验证码。\n\n后续短信查询不再逐条询问。关闭后立即阻止新查询。"))
            .setPositiveButton(UiText.text("允许短信读取"), (dialog, which) -> saveSms(true))
            .setNegativeButton(UiText.text("取消"), null)
            .show();
      }
      default -> {}
    }
  }

  private void showStatus(String text) {
    CardSheet.show(requireContext(), UiText.choose("设备通道状态", "Device channel status"), text);
  }

  private void verifyRoot(Context ctx) {
    if (!RootShell.enabled(ctx)) {
      toast(UiText.text("请先启用「允许 Root Shell」"));
      return;
    }
    if (!RootShell.present()) {
      toast(UiText.text("未找到 su；请确认手机已 root，且 root 管理器允许本应用使用"));
      return;
    }
    rootProbing = true;
    ui.setRootVerifying(true);
    ui.setRootStatus(UiText.text("正在验证设备通道…"));
    verify(
        () -> RootShell.exec(ctx.getApplicationContext(), "id", -1),
        () -> {
          rootProbing = false;
          ui.setRootVerifying(false);
        });
  }

  private void authorizeShizuku(Context ctx) {
    if (!ShizukuShell.isAvailable()) {
      ui.setShizukuBusy(true);
      com.deepseekharness.app.ShizukuManagerCompat.reconnect(
          requireActivity(),
          () -> {
            if (!isAdded()) return;
            ui.setShizukuBusy(false);
            if (ShizukuShell.isAvailable())
              ShizukuShell.requestPermission(
                  (code, result) -> {
                    if (isAdded()) refreshChannelLabels();
                  });
            else toast(ShizukuShell.userStatus(ctx));
            refreshChannelLabels();
          });
      return;
    }
    if (ShizukuShell.hasPermission()) {
      shizukuProbing = true;
      ui.setShizukuBusy(true);
      ui.setShizukuStatus(UiText.text("正在验证设备通道…"));
      verify(
          () -> ShizukuShell.exec("id"),
          () -> {
            shizukuProbing = false;
            ui.setShizukuBusy(false);
          });
    } else
      ShizukuShell.requestPermission(
          (code, result) -> {
            if (isAdded()) refreshChannelLabels();
          });
  }

  private void refreshScreenPolicy() {
    if (!isAdded()) return;
    var policy = new DeviceGrants(requireContext()).screenPolicy();
    boolean global = new DeviceGrants(requireContext()).consentPolicy().globalAllow();
    ui.setScreenMode(policy.mode.ordinal());
    ui.setScreenPolicyText(
        global
            ? UiText.choose(
                "全局权限已开启：已授权能力免安全确认。可编辑下列保留规则；关闭全局后按这些规则确认。独立撤销或暂停仍有效。",
                "Global permission is on: authorized capabilities skip confirmation. You can edit the retained rules below; they apply when global permission is off. Explicit revocation or pause remains effective.")
            : UiText.choose(
                "仅设置屏幕确认；不会授予系统权限。全部允许包含敏感界面；自定义中未配置的应用仍询问。规则保存在本机，不随备份迁移。",
                "These rules only control confirmations and do not grant system permissions. Allow all includes sensitive screens. Unconfigured apps still ask in custom mode. Rules stay on this device and are excluded from backups."));
    ui.setScreenPauseText(
        com.deepseekharness.app.HttpShellService.screenPaused()
            ? UiText.choose("恢复助手屏幕操作", "Resume assistant screen actions")
            : UiText.choose("暂停助手屏幕操作", "Pause assistant screen actions"));
  }

  private void refreshNativePolicyControls() {
    refreshScreenPolicy();
    if (!isAdded()) return;
    Context ctx = requireContext();
    ui.setRoot(new ConfigStore(ctx).isRootShellAllowed());
    ui.setAdb(pref(ctx, "adb_enabled", false));
    ui.setSensors(pref(ctx, "cap_sensors", false));
    ui.setLocation(pref(ctx, "cap_location", false));
    updateSms();
  }

  private void showScreenRules() {
    var policy = new DeviceGrants(requireContext()).screenPolicy();
    String[] packages = policy.rules.keySet().toArray(new String[0]);
    String[] labels = new String[packages.length];
    for (int i = 0; i < packages.length; i++)
      labels[i] =
          packages[i]
              + " · "
              + (policy.rules.get(packages[i]) == ScreenConfirmationPolicy.Rule.ALLOW
                  ? UiText.choose("允许", "Allow")
                  : UiText.choose("询问", "Ask"));
    new DshaDialogBuilder(requireContext())
        .setTitle(UiText.choose("应用确认规则", "App confirmation rules"))
        .setItems(labels, (dialog, which) -> editScreenRule(packages[which]))
        .setPositiveButton(UiText.choose("添加应用", "Add app"), (dialog, which) -> editScreenRule(""))
        .setNegativeButton(UiText.text("取消"), null)
        .show();
  }

  private void editScreenRule(String existing) {
    var ctx = requireContext();
    var content = new android.widget.LinearLayout(ctx);
    content.setOrientation(android.widget.LinearLayout.VERTICAL);
    var pkg = new androidx.appcompat.widget.AppCompatEditText(ctx);
    pkg.setSingleLine(true);
    pkg.setHint(UiText.choose("精确应用包名", "Exact app package name"));
    pkg.setText(existing);
    pkg.setEnabled(existing.isEmpty());
    content.addView(pkg);
    var allow = new MiuixSwitch(ctx);
    allow.setText(
        UiText.choose(
            "允许该应用免确认（含敏感界面）", "Allow this app without confirmation, including sensitive screens"));
    allow.setChecked(
        new DeviceGrants(ctx).screenPolicy().rules.get(existing) == ScreenConfirmationPolicy.Rule.ALLOW);
    content.addView(allow);
    var scroll = new android.widget.ScrollView(ctx);
    scroll.setFillViewport(true);
    scroll.addView(content);
    var builder =
        new DshaDialogBuilder(ctx)
            .setTitle(UiText.choose("编辑应用确认规则", "Edit app confirmation rules"))
            .setView(scroll)
            .setPositiveButton(
                UiText.text("保存"),
                (dialog, which) -> {
                  String key = pkg.getText().toString().trim();
                  if (!ScreenConfirmationPolicy.validPackage(key)) {
                    toast(UiText.choose("应用包名无效，规则未保存", "Invalid package name. Rule was not saved"));
                    return;
                  }
                  var grants = new DeviceGrants(ctx);
                  var current = grants.screenPolicy();
                  var rules = new java.util.LinkedHashMap<>(current.rules);
                  rules.put(
                      key,
                      allow.isChecked()
                          ? ScreenConfirmationPolicy.Rule.ALLOW
                          : ScreenConfirmationPolicy.Rule.ASK);
                  if (!grants.setScreenPolicy(current.mode, rules))
                    toast(UiText.text("授权保存失败，请重试"));
                  refreshScreenPolicy();
                })
            .setNegativeButton(UiText.text("取消"), null);
    if (!existing.isEmpty())
      builder.setNeutralButton(
          UiText.choose("删除规则", "Remove rule"),
          (dialog, which) -> {
            var grants = new DeviceGrants(ctx);
            var current = grants.screenPolicy();
            var rules = new java.util.LinkedHashMap<>(current.rules);
            rules.remove(existing);
            if (!grants.setScreenPolicy(current.mode, rules))
              toast(UiText.text("授权保存失败，请重试"));
            refreshScreenPolicy();
          });
    builder.show();
  }

  private void savePreference(String key, boolean enabled) {
    boolean success =
        requireContext()
            .getSharedPreferences(Constants.PREFS, 0)
            .edit()
            .putBoolean(key, enabled)
            .commit();
    if (success && !enabled) com.deepseekharness.app.DeviceSense.revoke(requireContext(), key);
    if (!success) toast(UiText.text("设置保存失败，请重试"));
  }

  private void syncSettings() {
    final Context app = requireContext().getApplicationContext();
    new Thread(
            () -> {
              String result = AdbBridge.applySettings(app, HarnessController.get(app).proot());
              main.post(
                  () -> {
                    if (!isAdded()) return;
                    ui.setGuardStatus(
                        UiText.format(
                            "原生授权即时生效；%s", result.replaceFirst("^SETTINGS_[A-Z]+: ", "")));
                  });
            },
            "device-settings")
        .start();
  }

  /** 后台执行验证命令，结果用弹窗展示；done 在主线程恢复按钮状态。 */
  private void verify(java.util.function.Supplier<String> probe, Runnable done) {
    new Thread(
            () -> {
              String output;
              try {
                output = probe.get();
              } catch (Throwable error) {
                output = com.deepseekharness.app.util.SensitiveData.redact(String.valueOf(error));
              }
              final String result = output;
              main.post(
                  () -> {
                    done.run();
                    if (!isAdded()) return;
                    new DshaDialogBuilder(requireContext())
                        .setTitle(UiText.text("设备通道验证"))
                        .setMessage(UiText.raw(result))
                        .setPositiveButton(UiText.text("知道了"), null)
                        .show();
                    refreshChannelLabels();
                  });
            },
            "device-channel-probe")
        .start();
  }

  private void openShizuku() {
    Intent launch = com.deepseekharness.app.ShizukuManagerCompat.launchIntent(requireContext());
    if (launch == null) {
      toast(UiText.text("尚未安装 Shizuku，请安装后启动服务"));
      return;
    }
    try {
      startActivity(launch);
    } catch (RuntimeException e) {
      toast(UiText.format("无法打开 Shizuku：%s", e.getClass().getSimpleName()));
    }
  }

  private void refreshChannelLabels() {
    if (!isAdded()) return;
    if (!shizukuProbing)
      ui.setShizukuStatus(
          com.deepseekharness.app.util.UiStateText.render(ShizukuShell.userStatus(requireContext())));
    ui.setShizukuAuthText(
        ShizukuShell.hasPermission() ? UiText.text("验证 Shizuku 连接") : UiText.text("授权 Shizuku"));
    if (!rootProbing) ui.setRootStatus(RootShell.status(requireContext()));
  }

  private void saveSms(boolean enabled) {
    boolean saved = new DeviceGrants(requireContext()).setSmsReadAllowed(enabled);
    updateSms();
    toast(
        !saved
            ? UiText.text("授权保存失败，请重试")
            : enabled
                ? UiText.text("短信查询已预授权")
                : UiText.choose("已关闭短信读取", "SMS reading disabled"));
  }

  private void updateSms() {
    if (!isAdded()) return;
    boolean allowed = new DeviceGrants(requireContext()).smsReadAllowed();
    ui.setSms(allowed);
    ui.setSmsStatus(
        allowed
            ? UiText.text("已预授权 · 关闭可撤销")
            : UiText.choose("已关闭 · 查询会被阻止", "Off · queries are blocked"));
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request != 104 || !isAdded()) return;
    boolean granted =
        requireContext().checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            || requireContext().checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    if (!granted) {
      savePreference("cap_location", false);
      ui.setLocation(false);
      new DshaDialogBuilder(requireContext())
          .setTitle(UiText.choose("需要系统定位权限", "Location permission required"))
          .setMessage(
              UiText.choose(
                  "位置读取保持关闭。请在系统应用权限中允许定位，再返回这里开启；如果系统定位服务关闭，也需要先开启系统定位。",
                  "Location reading remains off. Allow location in system app permissions, then return and enable it here. System location services must also be enabled."))
          .setNegativeButton(UiText.choose("暂不开启", "Not now"), null)
          .setPositiveButton(
              UiText.choose("打开系统权限设置", "Open app permissions"),
              (dialog, which) -> {
                try {
                  startActivity(
                      new Intent(
                          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          Uri.parse("package:" + requireContext().getPackageName())));
                } catch (RuntimeException missing) {
                  toast(UiText.choose("请在系统设置中打开本应用权限", "Open app permissions in system settings"));
                }
              })
          .show();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshNativePolicyControls();
    if (fullPermissions != null) fullPermissions.resume();
    ShizukuShell.ensureBound(requireContext());
    main.removeCallbacks(refreshConnections);
    main.post(refreshConnections);
    refreshAllFilesStatus();
    refreshA11yStatus();
    refreshAdbStatus();
  }

  @Override
  public void onPause() {
    if (fullPermissions != null) fullPermissions.pause();
    main.removeCallbacks(refreshConnections);
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    if (fullPermissions != null) {
      fullPermissions.close();
      fullPermissions = null;
    }
    adbStatusRequest++;
    super.onDestroyView();
  }

  private boolean pref(Context ctx, String key, boolean fallback) {
    return ctx.getSharedPreferences(Constants.PREFS, 0).getBoolean(key, fallback);
  }

  private void toast(String value) {
    if (getContext() != null) Toast.makeText(getContext(), value, Toast.LENGTH_LONG).show();
  }

  /** 后台读 ADB 通道真实状态（key/deps/端口 + 保活服务的连接状态）。 */
  private void refreshAdbStatus() {
    if (!isAdded()) return;
    final Context app = requireContext().getApplicationContext();
    final int request = ++adbStatusRequest;
    new Thread(
            () -> {
              final String text = computeAdbStatus(app);
              main.post(
                  () -> {
                    if (request != adbStatusRequest || !isAdded()) return;
                    ui.setAdbStatus(UiText.status(text));
                    if (text.startsWith("环境任务进行中") && isResumed())
                      main.postDelayed(
                          () -> {
                            if (request == adbStatusRequest && isResumed()) refreshAdbStatus();
                          },
                          1500);
                  });
            },
            "adb-status")
        .start();
  }

  private String computeAdbStatus(Context app) {
    try {
      if (!pref(app, "adb_enabled", false)) return UiText.text("ADB 已关闭。开启后才会保持连接。");
      String bridge = DeviceBridgeService.adbState;
      String detail = DeviceBridgeService.adbDetail == null ? "" : DeviceBridgeService.adbDetail;
      HarnessController hc = HarnessController.get(app);
      String st = hc.proot().isEnvironmentReady() ? AdbBridge.status(hc.proot()) : "env:not_ready";
      if (st.startsWith("ENVIRONMENT_BUSY") || "environment_busy".equals(bridge))
        return "环境任务进行中，ADB 暂停检查，完成后自动重试。" + (detail.isEmpty() ? "" : "\n" + detail);
      boolean key = st.contains("key=YES");
      String port = "?";
      int p = st.indexOf("port=");
      if (p >= 0) port = st.substring(p + 5).trim();
      if ("need_pair".equals(bridge)) return UiText.text("配对已失效，请重新配对。");
      if ("reconnecting".equals(bridge))
        return UiText.format("正在重连无线调试…%s", (detail.isEmpty() ? "" : "\n" + detail));
      if ("connected".equals(bridge))
        return UiText.format("连接已验证 · 端口 %s%s", port, (detail.isEmpty() ? "" : "\n" + detail));
      if (key) return UiText.format("配对密钥已保存，连接尚未验证。%s", (detail.isEmpty() ? "" : "\n" + detail));
      return UiText.text("尚未配对：点下方「ADB 无线配对」，配对后还会验证连接。");
    } catch (Throwable e) {
      return UiText.format("ADB 状态读取失败：%s", e.getMessage());
    }
  }

  private void openBatteryOpt(Context ctx) {
    openBatterySettings(ctx, this::startActivity, () -> toast(UiText.text("无法打开电池优化设置")));
  }

  /** 只在用户点击后导航到系统设置；不申请或自动修改电池优化白名单。 */
  static void openBatterySettings(
      Context context, java.util.function.Consumer<Intent> open, Runnable failed) {
    try {
      open.accept(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
      return;
    } catch (RuntimeException unavailable) {
      // OEM may omit or restrict the general battery settings Activity.
    }
    try {
      open.accept(
          new Intent(
              Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.parse("package:" + context.getPackageName())));
    } catch (RuntimeException unavailable) {
      failed.run();
    }
  }

  /** 「所有文件访问」入口：Android 11+ 跳系统 MANAGE 设置；Android 6-10 请求 WRITE_EXTERNAL_STORAGE。 */
  private void openAllFilesAccess(Context ctx) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
      try {
        startActivity(
            new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + ctx.getPackageName())));
      } catch (Throwable e) {
        try {
          startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        } catch (Throwable e2) {
          toast(UiText.format("打开设置失败：%s", e2.getMessage()));
        }
      }
      return;
    }
    if (ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 501);
    else toast(UiText.text("存储权限已授予，容器可访问手机存储"));
  }

  /** 刷新「所有文件访问权限」状态（含从系统设置返回后的更新）。 */
  private void refreshAllFilesStatus() {
    if (!isAdded()) return;
    boolean granted =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
            ? Environment.isExternalStorageManager()
            : requireContext().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    ui.setAllFilesGranted(granted);
    ui.setAllFilesStatus(
        granted
            ? UiText.choose(
                "已开启：可读写系统允许的共享存储；其他应用私有目录仍受系统限制",
                "Allowed: shared storage permitted by Android; other apps' private directories remain restricted")
            : UiText.text("未开启：仅能访问 App 私有目录；去系统设置开启后可访问全部文件"));
  }

  /** 分别显示系统开关与本次实际连接；开启开关不代表服务已经绑定。 */
  private void refreshA11yStatus() {
    if (!isAdded()) return;
    String st = DshaAccessibilityService.enabledState(requireContext());
    boolean enabled = "YES".equals(st);
    boolean connected = enabled && DshaAccessibilityService.connected();
    ui.setA11yState(connected ? 2 : enabled ? 1 : 0);
    if (enabled)
      ui.setA11yStatus(
          connected
              ? UiText.choose("已连接，可以在对话中使用 Computer Use。", "Connected. Computer Use is available in conversations.")
              : UiText.choose("开关已开启，正在等待系统连接；可重新开关服务。", "Enabled, waiting for the system to connect. Try toggling the service."));
    else if ("NO".equals(st)) ui.setA11yStatus(UiText.text("未开启 · 点此按两步完成设置"));
    else ui.setA11yStatus(UiText.text("尚未连接 · 点此查看设置步骤"));
  }
}
