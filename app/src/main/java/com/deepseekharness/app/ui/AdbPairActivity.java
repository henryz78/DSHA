package com.deepseekharness.app.ui;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import com.deepseekharness.app.DeviceBridgeService;
import com.deepseekharness.app.DshaAccessibilityService;
import com.deepseekharness.app.bridge.AdbBridge;
import com.deepseekharness.app.bridge.LocalNetworkAccess;
import com.deepseekharness.app.core.HarnessController;
import com.deepseekharness.app.runtime.ProotBootstrap;
import com.deepseekharness.app.util.AdbResult;
import com.deepseekharness.app.util.AdbEnvironmentTask;
import com.deepseekharness.app.util.SensitiveData;

/** 配对任务由 ViewModel 持有；页面重建不重跑握手，不丢进度与结果。 */
public class AdbPairActivity extends androidx.appcompat.app.AppCompatActivity {
  private HyLine statusText, addressError;
  private HyLine codeEt, hostEt, portEt;
  private HyLine startBtn, verifyBtn, autoBtn, manualBtn;
  private PairModel model;

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    model = new ViewModelProvider(this).get(PairModel.class);
    model.restore(saved);
    setContentView(HyScreenKt.hyScreen(this, buildUi(), true));
    model.status.observe(
        this,
        text -> {
          statusText.setSummary(text == null ? "" : text.split("\n\n", 2)[0]);
          hostEt.setEnabled(!model.busy);
          portEt.setEnabled(!model.busy);
          if (!model.busy && model.pairPort != null && !model.pairPort.isEmpty())
            portEt.setText(model.pairPort);
          startBtn.setEnabled(!model.busy);
          verifyBtn.setEnabled(!model.busy);
          autoBtn.setEnabled(!model.busy);
          manualBtn.setEnabled(!model.busy);
          codeEt.setEnabled(!model.busy);
          startBtn.setText(
              model.busy
                  ? com.deepseekharness.app.util.UiText.text("正在处理，请等待结果…")
                  : com.deepseekharness.app.util.UiText.text("开始配对"));
          if (model.code != null && !codeEt.getText().equals(model.code)) codeEt.setText(model.code);
        });
    if (!LocalNetworkAccess.granted(this)) requestNetworkPermission();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    model.save(out);
    super.onSaveInstanceState(out);
  }

  private void requestNetworkPermission() {
    model.message(com.deepseekharness.app.util.UiText.text("无线 ADB 需要局域网权限，以发现配对端口并连接设备"));
    requestPermissions(new String[] {LocalNetworkAccess.PERMISSION}, 37);
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == 37)
      model.message(
          LocalNetworkAccess.granted(this)
              ? com.deepseekharness.app.util.UiText.text("局域网权限已允许，请输入本次配对码后开始配对")
              : com.deepseekharness.app.util.UiText.text("未允许局域网访问，请在系统应用权限设置中允许后重试"));
  }

  private HyScreenModel buildUi() {
    var page = new HyScreenModel(getString(com.deepseekharness.app.R.string.ui2_adb));
    page.note(
        com.deepseekharness.app.util.UiText.choose(
            "在系统无线调试中取得配对码，再连接当前设备。",
            "Get a pairing code from system wireless debugging, then connect this device."));
    var state = page.section(com.deepseekharness.app.util.UiText.choose("连接状态", "Connection status"));
    statusText =
        state.arrow(
            com.deepseekharness.app.util.UiText.choose("查看连接详情", "Connection details"),
            "",
            () ->
                CardSheet.show(
                    this,
                    com.deepseekharness.app.util.UiText.choose("连接详情", "Connection details"),
                    String.valueOf(model.status.getValue())));
    state.button(
        com.deepseekharness.app.util.UiText.choose("打开无线调试设置", "Open wireless debugging"),
        false,
        this::openWirelessSettings);
    var pair = page.section(com.deepseekharness.app.util.UiText.choose("配对", "Pairing"));
    hostEt =
        pair.input(com.deepseekharness.app.util.UiText.choose("设备地址（留空自动发现）", "Device address (blank to discover)"), false);
    hostEt.setText(model.host == null ? "" : model.host);
    portEt =
        pair.input(com.deepseekharness.app.util.UiText.choose("配对端口（留空自动发现）", "Pairing port (blank to discover)"), false);
    portEt.setText(model.pairPort == null ? "" : model.pairPort);
    // 一次性配对码只放在内存模型里，不写入页面状态。
    codeEt =
        pair.input(
            com.deepseekharness.app.util.UiText.choose("配对码（6 位数字）", "Pairing code (6 digits)"),
            false,
            value -> model.code = value);
    codeEt.setText(model.code == null ? "" : model.code);
    addressError = pair.text("");
    addressError.setTone(2);
    addressError.setVisible(false);
    startBtn =
        pair.button(
            com.deepseekharness.app.util.UiText.text("开始配对"),
            true,
            () -> {
              if (!LocalNetworkAccess.granted(this)) requestNetworkPermission();
              else if (readAddress()) model.start(false, "", 0);
            });
    autoBtn =
        pair.button(
            com.deepseekharness.app.util.UiText.choose("自动读取本次配对码", "Read the pairing code automatically"),
            false,
            this::autoRead);
    manualBtn =
        pair.button(
            com.deepseekharness.app.util.UiText.choose("手动地址与端口", "Manual address and ports"),
            false,
            () -> {
              readAddress();
              manualPorts();
            });
    var manage = page.section(com.deepseekharness.app.util.UiText.choose("已有连接", "Existing connection"));
    verifyBtn =
        manage.button(
            com.deepseekharness.app.util.UiText.choose("验证已有连接", "Verify existing connection"),
            false,
            () -> {
              if (!LocalNetworkAccess.granted(this)) requestNetworkPermission();
              else if (readAddress()) model.start(true, "", 0);
            });
    manage.button(
        com.deepseekharness.app.util.UiText.choose("断开 ADB 连接", "Disconnect ADB"),
        false,
        () -> {
          getSharedPreferences(com.deepseekharness.app.util.Constants.PREFS, 0)
              .edit()
              .putBoolean("adb_enabled", false)
              .apply();
          stopService(new Intent(this, DeviceBridgeService.class));
          model.message(
              com.deepseekharness.app.util.UiText.choose(
                  "ADB 已关闭，配对密钥保留。需要时可验证连接重新启用。",
                  "ADB disabled. Pairing keys are retained; verify the connection to enable it again."));
        });
    manage.arrow(
        com.deepseekharness.app.util.UiText.text("配对与端口说明"),
        "",
        () ->
            new com.deepseekharness.app.ui.DshaDialogBuilder(this)
                .setTitle(com.deepseekharness.app.util.UiText.text("配对与端口说明"))
                .setMessage(
                    com.deepseekharness.app.util.UiText.text(
                        "找不到开发者选项时，到关于手机连续点按系统版本号。系统或厂商未提供无线调试时，填写端口无法启用它。\n\n通常让地址与端口留空即可自动发现。配对端口来自 6 位码弹窗，连接端口来自无线调试主页面，两者不能互换。\n\n首次准备依赖可能较久；配对码失效时请重新获取。自动读码需要屏幕操作权限，手动输入无需此权限。"))
                .setPositiveButton(com.deepseekharness.app.util.UiText.text("知道了"), null)
                .show());
    return page;
  }

  private boolean readAddress() {
    try {
      addressError.setVisible(false);
      String host = hostEt.getText().trim();
      String port = portEt.getText().trim();
      AdbResult.port(port);
      if (!host.isEmpty() && !AdbBridge.localAddresses().contains(host)) {
        addressError.setText(
            com.deepseekharness.app.util.UiText.choose(
                "请填写本机无线调试显示的 IP", "Enter this device's wireless debugging IP"));
        addressError.setVisible(true);
        return false;
      }
      model.host = host;
      model.pairPort = port;
      return true;
    } catch (IllegalArgumentException invalid) {
      addressError.setText(
          com.deepseekharness.app.util.UiText.choose(
              "端口应为 1–65535，或留空自动发现", "Use a port from 1–65535, or leave blank"));
      addressError.setVisible(true);
      return false;
    }
  }

  private void autoRead() {
    if (!LocalNetworkAccess.granted(this)) {
      requestNetworkPermission();
      return;
    }
    if (!DshaAccessibilityService.enabled(this)) {
      startActivity(new Intent(this, AccessibilitySetupActivity.class));
      return;
    }
    model.watch();
    openWirelessSettings();
  }

  /** 直接打开与自动读码共用回退，兼容没有无线调试独立 Activity 的 MIUI。 */
  private void openWirelessSettings() {
    try {
      startActivity(new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"));
    } catch (RuntimeException e) {
      try {
        startActivity(
            new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
      } catch (RuntimeException ignored) {
        model.message(com.deepseekharness.app.util.UiText.text("请手动进入开发者选项 → 无线调试 → 使用配对码配对设备"));
      }
    }
  }

  private void manualPorts() {
    CardPage page =
        new CardPage(
            this,
            com.deepseekharness.app.util.UiText.choose("手动连接设置", "Manual connection"),
            com.deepseekharness.app.util.UiText.choose(
                "配对端口来自六位码弹窗；连接端口来自无线调试主页面。",
                "Pairing port comes from the code dialog; connection port comes from the main Wireless debugging page."));
    LinearLayout card = page.card();
    EditText host =
        portField(
            page,
            card,
            com.deepseekharness.app.util.UiText.choose("本机 IP · 可留空", "Device IP · Optional"),
            model.host,
            false);
    EditText pp =
        portField(
            page,
            card,
            com.deepseekharness.app.util.UiText.choose(
                "配对端口 · 留空自动发现", "Pairing port · Auto if blank"),
            model.pairPort,
            true);
    EditText cp =
        portField(
            page,
            card,
            com.deepseekharness.app.util.UiText.choose(
                "连接端口 · 留空自动发现", "Connection port · Auto if blank"),
            model.connectPort,
            true);
    TextView error = page.text("", 12, com.deepseekharness.app.R.color.err);
    page.content.addView(error);
    var dialog = CardSheet.create(this, page);
    page.button(
        page.footer,
        com.deepseekharness.app.util.UiText.choose("保存", "Save"),
        true,
        () -> {
          try {
            AdbResult.port(pp.getText().toString());
            AdbResult.port(cp.getText().toString());
            String address = host.getText().toString().trim();
            if (!address.isEmpty() && !AdbBridge.localAddresses().contains(address)) {
              host.setError(
                  com.deepseekharness.app.util.UiText.choose(
                      "请填写本机无线调试页面的 IP", "Enter this device's wireless debugging IP"));
              return;
            }
            model.host = address;
            model.pairPort = pp.getText().toString().trim();
            model.connectPort = cp.getText().toString().trim();
            hostEt.setText(address);
            portEt.setText(model.pairPort);
            model.message(
                com.deepseekharness.app.util.UiText.choose(
                    "连接设置已保存，可开始配对或验证已有连接。",
                    "Connection settings saved. Pair or verify the existing connection."));
            dialog.dismiss();
          } catch (IllegalArgumentException invalid) {
            error.setText(
                com.deepseekharness.app.util.UiText.choose(
                    "端口应为 1–65535，或留空自动发现。", "Use a port from 1–65535, or leave blank."));
          }
        });
    page.button(
        page.footer,
        com.deepseekharness.app.util.UiText.choose("取消", "Cancel"),
        false,
        dialog::dismiss);
    CardSheet.show(dialog, this);
  }

  private EditText portField(
      CardPage page, LinearLayout card, String title, String value, boolean number) {
    TextView label = page.text(title, 12, com.deepseekharness.app.R.color.text_secondary);
    label.setPadding(0, page.dp(10), 0, page.dp(8));
    card.addView(label);
    EditText input = new EditText(this);
    input.setText(value);
    input.setTextSize(14);
    input.setTextColor(getColor(com.deepseekharness.app.R.color.text));
    input.setBackgroundResource(com.deepseekharness.app.R.drawable.bg_input);
    input.setPadding(page.dp(12), page.dp(12), page.dp(12), page.dp(12));
    input.setSingleLine(true);
    input.setMinHeight(page.dp(48));
    input.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
    card.addView(input, new LinearLayout.LayoutParams(-1, -2));
    return input;
  }

  /** 只持有 Application；自动读码及后台任务不会捕获旧 Activity。 */
  public static final class PairModel extends AndroidViewModel {
    final MutableLiveData<String> status = new MutableLiveData<>("输入配对码后开始；端口会重新发现。");
    final Handler main = new Handler(Looper.getMainLooper());
    volatile boolean busy, cleared;
    String code = "", host = "", pairPort = "", connectPort = "";
    private Thread task;
    private boolean restored;
    private long watchEpoch;

    public PairModel(Application app) {
      super(app);
    }

    void message(String text) {
      if (!cleared) status.postValue(SensitiveData.redact(text));
    }

    void save(Bundle out) {
      out.putBoolean("adb-pair-busy", busy);
      out.putString("adb-pair-status", status.getValue());
      out.putString("adb-pair-host", host);
      out.putString("adb-pair-port", pairPort);
      out.putString("adb-connect-port", connectPort);
    }

    void restore(Bundle saved) {
      if (restored) return;
      restored = true;
      if (saved == null) return;
      host = saved.getString("adb-pair-host", "");
      pairPort = saved.getString("adb-pair-port", "");
      connectPort = saved.getString("adb-connect-port", "");
      status.setValue(
          saved.getBoolean("adb-pair-busy")
              ? com.deepseekharness.app.util.UiText.text("配对任务被系统中断，结果尚不确定。请先验证已有连接；若仍未配对，再获取新配对码。")
              : saved.getString(
                  "adb-pair-status", com.deepseekharness.app.util.UiText.text("请输入本次配对码")));
    }

    void watch() {
      final long epoch = ++watchEpoch;
      message(com.deepseekharness.app.util.UiText.text("已开始监听，两分钟内有效。请打开系统的「使用配对码配对设备」弹窗。"));
      DshaAccessibilityService.startWatch(
          (value, address, port, connectionPort) ->
              main.post(
                  () -> {
                    if (cleared || busy || epoch != watchEpoch) return;
                    code = value;
                    if (!connectionPort.isEmpty()) connectPort = connectionPort;
                    int p;
                    try {
                      p = AdbResult.port(port);
                    } catch (IllegalArgumentException e) {
                      p = 0;
                    }
                    start(false, address, p);
                  }));
      main.postDelayed(
          () -> {
            if (!cleared && !busy && epoch == watchEpoch) {
              watchEpoch++;
              DshaAccessibilityService.stopWatch();
              message(com.deepseekharness.app.util.UiText.text("自动读码已到期；请重新点自动读取，或手动输入本次配对码。"));
            }
          },
          120_000);
    }

    void start(boolean verify, String readHost, int readPort) {
      if (busy || cleared) return;
      boolean prepareOffline = false;
      // 无线调试关闭时仍可先准备离线依赖，避免准备时间消耗本次配对码的有效期。
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        int wireless = -1;
        try {
          wireless =
              android.provider.Settings.Global.getInt(
                  getApplication().getContentResolver(), "adb_wifi_enabled", -1);
        } catch (RuntimeException ignored) {
        }
        if (wireless == 0 && !verify) {
          watchEpoch++;
          DshaAccessibilityService.stopWatch();
          String detail = com.deepseekharness.app.util.UiText.text("无线调试已关闭，请先开启");
          DeviceBridgeService.adbDetail = detail;
          DeviceBridgeService.adbState = "need_manual";
          status.setValue(detail);
          return;
        }
        prepareOffline = wireless == 0;
      }
      if (!verify && !AdbResult.code(code.trim())) {
        message(com.deepseekharness.app.util.UiText.text("配对码必须恰好为 6 位数字"));
        return;
      }
      if (!prepareOffline && !LocalNetworkAccess.granted(getApplication())) {
        message(com.deepseekharness.app.util.UiText.text("局域网权限未允许，请先在 DSHA 权限设置中允许"));
        return;
      }
      final String value = code.trim();
      final String savedPairPort = pairPort;
      final String pp = readPort > 0 ? String.valueOf(readPort) : pairPort;
      final String cp = connectPort;
      final String selectedHost = readPort > 0 ? readHost : host;
      final boolean offlineOnly = prepareOffline;
      pairPort = ""; // 一次性配对端口不跨请求复用，旧弹窗关闭后必须重新发现。
      busy = true;
      watchEpoch++;
      status.setValue(
          offlineOnly
              ? com.deepseekharness.app.util.UiText.text("正在准备离线 ADB 环境…")
              : verify
                  ? com.deepseekharness.app.util.UiText.text("正在准备已有连接验证…")
                  : com.deepseekharness.app.util.UiText.text("正在准备配对环境…"));
      DshaAccessibilityService.stopWatch();
      task =
          new Thread(
              () -> {
                String result;
                try {
                  result =
                      AdbBridge.runEnvironmentTask(
                          getApplication(),
                          verify
                              ? com.deepseekharness.app.util.UiText.text("ADB 验证完整任务")
                              : com.deepseekharness.app.util.UiText.text("ADB 配对完整任务"),
                          () -> perform(verify, value, pp, cp, selectedHost, offlineOnly));
                } catch (AdbEnvironmentTask.Busy e) {
                  if (!cleared) {
                    DeviceBridgeService.adbDetail = e.getMessage();
                    DeviceBridgeService.adbState = "environment_busy";
                  }
                  result = "ENVIRONMENT_BUSY: " + e.getMessage();
                } catch (Throwable e) {
                  result =
                      com.deepseekharness.app.util.UiText.format(
                          "ADB 操作未完成：%s", SensitiveData.redact(String.valueOf(e)));
                } finally {
                  // finally 只处理取消路径；完成路径统一在主线程更新所有 UI 状态。
                  if (cleared) busy = false;
                }
                final String finished = result;
                main.post(
                    () -> {
                      if (cleared) return;
                      if (AdbResult.marker(finished, "ENVIRONMENT_BUSY")) pairPort = savedPairPort;
                      else code = "";
                      busy = false;
                      status.setValue(SensitiveData.redact(finished));
                    });
              },
              "adb-pair-task");
      task.start();
    }

    /** 整段由同一 Lease.run 执行，准备、发现、配对、验证及授权期间不允许移动环境。 */
    private String perform(
        boolean verify,
        String value,
        String pp,
        String cp,
        String selectedHost,
        boolean offlineOnly)
        throws Exception {
      if (cleared || Thread.currentThread().isInterrupted())
        return com.deepseekharness.app.util.UiText.text("ADB 操作已取消");
      try (com.deepseekharness.app.core.RuntimeTasks ignored =
          com.deepseekharness.app.core.RuntimeTasks.begin()) {
        ProotBootstrap proot = HarnessController.get(getApplication()).proot();
        if (!proot.isEnvironmentReady())
          throw new IllegalStateException(
              com.deepseekharness.app.util.UiText.text("环境未就绪，请先完成环境安装"));
        String prep = AdbBridge.ensureReady(getApplication(), proot, this::message);
        if (AdbResult.marker(prep, "ENVIRONMENT_BUSY")) throw new AdbEnvironmentTask.Busy(prep);
        if (!AdbResult.marker(prep, "SETUP_DONE")) throw new IllegalStateException(prep);
        if (cleared || Thread.currentThread().isInterrupted())
          return com.deepseekharness.app.util.UiText.text("ADB 操作已取消");
        if (offlineOnly) {
          String detail =
              com.deepseekharness.app.util.UiText.text(
                  "ADB_OFFLINE_READY: 离线依赖已准备好，未验证连接。请开启无线调试后再次验证。");
          DeviceBridgeService.adbDetail = detail;
          DeviceBridgeService.adbState = "need_manual";
          return new StringBuilder(prep).append('\n').append(detail).toString();
        }
        String address = selectedHost == null ? "" : selectedHost;
        String port = pp;
        if (!verify && port.isEmpty()) {
          message(com.deepseekharness.app.util.UiText.text("正在重新发现本机配对端口（最多 6 秒）…"));
          AdbBridge.Endpoint endpoint =
              AdbBridge.discover(getApplication(), "_adb-tls-pairing._tcp.", 6000, this::message);
          if (endpoint != null) {
            address = endpoint.host;
            port = String.valueOf(endpoint.port);
          }
        }
        if (cleared || Thread.currentThread().isInterrupted())
          return com.deepseekharness.app.util.UiText.text("ADB 操作已取消");
        message(
            verify
                ? com.deepseekharness.app.util.UiText.text("正在验证已配对的设备连接，最多约 1 分钟…")
                : com.deepseekharness.app.util.UiText.text("正在完成一次配对握手并验证连接，最多约 2 分钟；随后检查自动恢复授权…"));
        String connectionPort = cp;
        if (connectionPort.isEmpty()) {
          AdbBridge.Endpoint endpoint =
              AdbBridge.discover(getApplication(), "_adb-tls-connect._tcp.", 5000, this::message);
          if (endpoint != null) {
            connectionPort = String.valueOf(endpoint.port);
            if (address.isEmpty()) address = endpoint.host;
          }
        }
        String out =
            verify
                ? AdbBridge.verify(proot, connectionPort, address)
                : AdbBridge.pair(proot, value, port, connectionPort, address);
        if (AdbResult.marker(out, "ENVIRONMENT_BUSY")) throw new AdbEnvironmentTask.Busy(out);
        AdbResult.PairState state = AdbResult.pairState(out);
        DeviceBridgeService.recordPairResult(getApplication(), state, out);
        if (state == AdbResult.PairState.CONNECTED)
          return com.deepseekharness.app.util.UiText.format("连接已验证，ADB 设备命令可用。\n\n%s", out);
        else if (state == AdbResult.PairState.PAIRED)
          return com.deepseekharness.app.util.UiText.format(
              "配对已完成，连接尚未验证。请检查连接端口后点「验证已有连接」，无需再次配对。\n\n%s", out);
        else
          return (verify
                  ? com.deepseekharness.app.util.UiText.text("连接验证未通过。\n\n")
                  : com.deepseekharness.app.util.UiText.text("配对未完成，请按以下原因处理。\n\n"))
              + out;
      }
    }

    @Override
    protected void onCleared() {
      cleared = true;
      watchEpoch++;
      code = "";
      DshaAccessibilityService.stopWatch();
      main.removeCallbacksAndMessages(null);
      if (task != null) task.interrupt();
    }
  }
}
