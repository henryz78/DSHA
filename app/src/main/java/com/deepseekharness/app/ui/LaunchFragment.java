package com.deepseekharness.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;

import com.deepseekharness.app.LanProxyService;
import com.deepseekharness.app.core.HarnessController;
import com.deepseekharness.app.util.Constants;
import com.deepseekharness.app.util.UiText;

/**
 * 启动页：启动 / 进入 / 停止 dsh Web，显示运行状态、鉴权链接与局域网访问地址。
 *
 * <p>界面由 LaunchScreen（miuix）绘制；这里只维护 {@link LaunchState} 和处理操作。
 */
public class LaunchFragment extends Fragment implements OwnTopBar {

  /** 通知点击进入时带上的参数：Web 就绪后自动打开会话。 */
  public static final String ARG_OPEN_WEB = "open_web";

  private HarnessController controller;
  private final LaunchState state = new LaunchState();

  /** 界面挂载后才刷新；宿主测试可以只替换 onCreateView。 */
  private ComposeView screen;

  /** 当前局域网服务地址；空表示不可点。 */
  private String lanService = "";

  /** 启动按钮当前是否处于「进入」态（鉴权链接已就绪）。 */
  private boolean webReady;

  /** 主线程单飞；成功打开后保持占用，直到页面重新可见。 */
  private boolean enteringWeb;

  private boolean webEntryFailure;

  /** 环境需要恢复时，「恢复选项」改为打开环境维护。 */
  private boolean environmentRecovery;

  private boolean preparingStart;
  /** 启动没被接受时（如环境需要更新）留在状态行的原因；下次启动或停止时清掉。 */
  private String startNotice = "";
  private String lastStartMessage = "";
  private final java.util.concurrent.atomic.AtomicLong startRequest =
      new java.util.concurrent.atomic.AtomicLong();
  private static final java.util.concurrent.Executor START_IO =
      java.util.concurrent.Executors.newSingleThreadExecutor();

  /** 本次启动开始时刻（显示耗时用）。 */
  private long startAtMs;

  private long logRevision = -1;
  private String logUrl = "";
  private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

  /** 通知点击进来、待自动进入 Web 的意图；由既有每秒刷新循环消费，就绪即进。 */
  private boolean pendingAutoEnter;

  private final Runnable refreshState =
      new Runnable() {
        @Override
        public void run() {
          refreshRunState();
          refreshLanAddr();
          // 通知点击进来的自动进入：复用这个每秒循环等鉴权链接，不另开定时器。
          // 判据只认「鉴权链接已出现」——它就是进入 Web 的前提，也是按钮变「进入」的
          // 同一条件。不能拿 getWebAuthFailure() 判成败：那是最近一次鉴权结果消息，
          // 初始值与成功值都非 null（成功时是"鉴权成功"），用它当成功判据会永不进入。
          if (pendingAutoEnter && !webEntryUrl().isEmpty()) {
            pendingAutoEnter = false;
            enterWeb();
          }
          ui.postDelayed(this, 1000);
        }
      };

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    controller = HarnessController.get(requireContext());
    final Activity activity = requireActivity();
    logRevision = -1;
    logUrl = "";
    state.setRunState(getString(com.deepseekharness.app.R.string.ui2_stopped));
    state.setSubtitle(getString(com.deepseekharness.app.R.string.ui2_launch_hint));
    state.setStartLabel(UiText.choose("启动", "Start"));
    state.setRecoveryLabel(UiText.text("恢复选项"));

    // 通知点击进来：置待进入标记，由每秒刷新循环在鉴权链接就绪后自动进入。
    if (getArguments() != null && getArguments().getBoolean(ARG_OPEN_WEB, false)) {
      getArguments().remove(ARG_OPEN_WEB);
      pendingAutoEnter = true;
    }

    screen = new ComposeView(requireContext());
    LaunchScreenKt.bindLaunchScreen(
        screen,
        state,
        new LaunchActions() {
          @Override
          public void start() {
            // 未就绪时是「启动」；鉴权链接就绪后自动变为「进入」，点击进 WebUI。
            if (webReady || !webEntryUrl().isEmpty()) {
              enterWeb();
              return;
            }
            doStart(activity, false);
          }

          @Override
          public void restart() {
            // startWeb 本身串行执行「清旧进程 → 启动」，无需拆成两次请求。
            doStart(activity, false);
          }

          @Override
          public void stop() {
            stopWeb(activity);
          }

          @Override
          public void showAddress() {
            showAddresses();
          }

          @Override
          public void showLan() {
            if (!lanService.isEmpty()) showLanAddress(lanService);
          }

          @Override
          public void recovery() {
            if (environmentRecovery)
              startActivity(
                  new Intent(requireContext(), ExtractActivity.class)
                      .putExtra("review_only", true));
            else showRecovery();
          }

          @Override
          public void safeStart() {
            new DshaDialogBuilder(requireContext(), true)
                .setTitle(UiText.text("安全启动 Web？"))
                .setMessage(
                    UiText.text(
                        "停止当前启动，只加载官方基础界面。原插件开关、会话、模型和配置保留；普通重启后回到原配置。安全界面不加载移动插件等扩展。"))
                .setNegativeButton(UiText.text("取消"), null)
                .setPositiveButton(
                    UiText.text("安全启动"), (dialog, which) -> doStart(activity, true))
                .show();
          }

          @Override
          public void downloadLogs() {
            startActivity(DiagnosticActivity.downloadLogs(requireContext()));
          }
        });
    return screen;
  }

  private void showAddresses() {
    String local = controller.getWebAuthUrl();
    String addresses =
        local.isEmpty() ? UiText.choose("启动后可查看访问地址。", "Start DSH to view access addresses.") : local;
    if (state.getLanVisible()) addresses += "\n\n" + state.getLan();
    var dialog =
        new DshaDialogBuilder(requireContext())
            .setTitle(com.deepseekharness.app.R.string.ui2_address)
            .setMessage(addresses)
            .setNegativeButton(android.R.string.cancel, null);
    if (!local.isEmpty())
      dialog.setPositiveButton(
          UiText.choose("复制本机地址", "Copy local URL"), (d, w) -> copyAddr("DSH", local));
    dialog.show();
  }

  private void stopWeb(Activity activity) {
    final View root = getView();
    startRequest.incrementAndGet();
    preparingStart = false;
    startNotice = "";
    FirstRunWebStart.cancel(requireContext());
    invalidateWebEntry();
    controller.stopWeb(
        msg -> {
          long generation = controller.getWebGeneration();
          activity.runOnUiThread(
              () -> {
                if (getView() != root || generation != controller.getWebGeneration()) return;
                state.setStatus(com.deepseekharness.app.util.UiStateText.render(msg));
                refreshRunState();
                refreshLanAddr();
                var stopped = controller.lastStopResult();
                if (stopped != null && !stopped.stopped() || !controller.lastStopError().isEmpty())
                  offerForceStop(activity, root, generation);
              });
        });
    webReady = false;
    state.setStartLabel(UiText.choose("启动", "Start"));
    state.setStatus(UiText.text("停止中…"));
    refreshLanAddr();
    refreshRunState();
  }

  /** 启动 dsh：记录启动时刻，鉴权链接就绪后把「启动」变「进入」并输出 URL 到日志。 */
  private void doStart(Activity activity, boolean safeMode) {
    if (preparingStart || !safeMode && (controller.isStarting() || controller.isStopping())) return;
    final View root = getView();
    if (root == null || !isResumed() || activity.isFinishing() || activity.isDestroyed()) return;
    FirstRunWebStart.cancel(requireContext());
    preparingStart = true;
    startNotice = "";
    lastStartMessage = "";
    long request = startRequest.incrementAndGet();
    invalidateWebEntry();
    startAtMs = System.currentTimeMillis();
    String time =
        new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(new java.util.Date());
    state.setStatus(UiText.format("启动中…（%s）", time));
    state.setStartLabel(UiText.choose("启动", "Start"));
    webReady = false;
    java.util.function.Consumer<String> startStatus =
        msg -> {
          long generation = controller.getWebGeneration();
          activity.runOnUiThread(
              () -> {
                if (getView() != root || generation != controller.getWebGeneration()) return;
                lastStartMessage = com.deepseekharness.app.util.UiStateText.render(msg);
                state.setStatus(lastStartMessage);
                refreshRunState();
                refreshLanAddr();
              });
        };
    refreshRunState();
    boolean queued =
        FirstRunWebStart.enqueueVisible(
            requireActivity(),
            START_IO,
            safeMode,
            safeMode,
            startStatus,
            () -> {
              if (startRequest.get() != request || getView() != root) return;
              preparingStart = false;
              keepRejectedStartReason();
              refreshRunState();
              refreshLanAddr();
            });
    if (!queued) {
      preparingStart = false;
      keepRejectedStartReason();
      refreshRunState();
    }
  }

  /** 启动请求结束后既没在启动、也没就绪：说明被拒绝了，把原因留在状态行，不然看起来像按钮没反应。 */
  private void keepRejectedStartReason() {
    if (!controller.isStarting() && webEntryUrl().isEmpty()) startNotice = lastStartMessage;
  }

  /**
   * 外部（通知点击）请求：下一次刷新周期自动进入 Web。
   *
   * <p>页面不可见时由 {@code onPause} 清掉，避免用户回来时被意外带走。
   */
  void requestAutoEnterWeb() {
    pendingAutoEnter = true;
    if (getView() != null) ui.post(refreshState);
  }

  /** 打开 WebPreviewActivity 进入 dsh WebUI。 */
  private void enterWeb() {
    final View root = getView();
    final Activity activity = getActivity();
    if (enteringWeb
        || root == null
        || activity == null
        || !isResumed()
        || activity.isFinishing()
        || activity.isDestroyed()) return;
    String url = webEntryUrl();
    if (url.isEmpty()) {
      state.setStatus(UiText.text("先点「启动」，等鉴权链接就绪后再进入"));
      return;
    }
    final long generation = webEntryGeneration();
    enteringWeb = true;
    webEntryFailure = false;
    state.setStatus(UiText.choose("正在打开 Web…", "Opening Web…"));
    refreshRunState();
    try {
      if (getView() != root || !isResumed() || activity.isFinishing() || activity.isDestroyed()) {
        invalidateWebEntry();
        return;
      }
      if (generation != webEntryGeneration() || !url.equals(webEntryUrl())) {
        finishWebEntry(UiText.text("Web 状态已变化，请等待就绪后重新进入"));
        return;
      }
      // The browser owns the token -> cookie -> HTTP 200 verification. Doing it here
      // too created two serial exchanges before the first page; no unverified cookie
      // is handed off and neither navigation nor progress reports browser readiness.
      openWebEntry(activity, url, null);
      refreshLanAddr();
    } catch (RuntimeException error) {
      finishWebEntry(UiText.format("无法打开 Web 页面，请重试（%s）", error.getClass().getSimpleName()));
    }
  }

  // Same-package host tests can replace only the Activity destination.
  String webEntryUrl() {
    return controller.getWebAuthUrl();
  }

  long webEntryGeneration() {
    return controller.getWebGeneration();
  }

  void openWebEntry(Activity activity, String url, String cookie) {
    startActivity(WebPreviewActivity.intent(activity, url, cookie));
  }

  private void finishWebEntry(String message) {
    enteringWeb = false;
    webEntryFailure = true;
    state.setStatus(com.deepseekharness.app.util.UiStateText.render(message));
    refreshRunState();
  }

  private void invalidateWebEntry() {
    enteringWeb = false;
  }

  @Override
  public void onResume() {
    super.onResume();
    invalidateWebEntry();
    ui.removeCallbacks(refreshState);
    ui.post(refreshState);
  }

  @Override
  public void onPause() {
    // 页面不可见时撤销自动进入：用户离开了启动页，回来不该被"突袭"打开 Web。
    pendingAutoEnter = false;
    startRequest.incrementAndGet();
    preparingStart = false;
    if (enteringWeb && getView() != null) state.setStatus(UiText.text("返回后可重新进入 Web"));
    invalidateWebEntry();
    ui.removeCallbacks(refreshState);
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    startRequest.incrementAndGet();
    preparingStart = false;
    invalidateWebEntry();
    ui.removeCallbacks(refreshState);
    screen = null;
    super.onDestroyView();
  }

  /** 读取共享状态，不在主线程执行 proot/kill -0；重建页面也能跟随后台启停。 */
  private void refreshRunState() {
    try {
      if (getView() == null || screen == null) return;
      boolean starting = preparingStart || controller.isStarting();
      boolean stopping = controller.isStopping();
      var stopResult = controller.lastStopResult();
      boolean unresolvedStop =
          controller.isUserStopped()
              && !starting
              && (stopResult != null && !stopResult.stopped()
                  || !controller.lastStopError().isEmpty());
      boolean ready = !starting && !stopping && !webEntryUrl().isEmpty();
      boolean restoring = controller.isRestoringWebSession();
      boolean unresolved = controller.hasUnresolvedWebSession();
      com.deepseekharness.app.util.StartupTrace.Snapshot trace =
          controller.startupDiagnostics().snapshot();
      String localUrl = webEntryUrl();
      if (trace.revision != logRevision || !localUrl.equals(logUrl)) {
        // 仅原生本机视图显示当前鉴权地址；归档与导出的诊断仍统一脱敏。
        state.setLog(
            (trace.log.isEmpty() ? UiText.text("还没有日志。") : trace.log)
                + (localUrl.isEmpty()
                    ? ""
                    : UiText.format("\n\n本机 Web 地址（可长按复制）：\n%s", localUrl)));
        logRevision = trace.revision;
        logUrl = localUrl;
      }
      state.setRunState(
          enteringWeb
              ? UiText.choose("正在打开 Web…", "Opening Web…")
              : restoring
                  ? UiText.choose("正在核对原 Web 实例…", "Checking the existing Web instance…")
                  : unresolved
                      ? UiText.choose(
                          "原 Web 实例状态待确认，请查看诊断或正常停止后重试",
                          "The previous Web instance is unconfirmed. Inspect diagnostics or stop it normally before retrying.")
                      : controller.isRestartBlocked()
                          ? UiText.text("自动重启已暂停")
                          : stopping
                              ? UiText.text("DSH 停止中…")
                              : starting
                                  ? UiText.text("DSH 启动中…")
                                  : ready
                                      ? (trace.safe
                                              ? UiText.text("基础界面已就绪 · 安全模式")
                                              : UiText.text("DSH 已就绪，可进入"))
                                          + (controller.isWebCompatibilityFallback()
                                              ? UiText.text(" · 已兼容切换 proot")
                                              : "")
                                      : getString(com.deepseekharness.app.R.string.ui2_stopped));
      state.setPort(ready ? String.valueOf(controller.getWebPort()) : "—");
      state.setEnvironment(EnvironmentUiStatus.get(requireContext()).ready ? "READY" : "—");
      state.setSubtitle(
          ready
              ? (controller.isWebCompatibilityFallback()
                      ? "proot"
                      : controller.proot().runtime().id())
                  + " · dsh "
                  + Constants.DSH_VERSION
              : getString(com.deepseekharness.app.R.string.ui2_launch_hint));
      state.setStatusVisible(
          enteringWeb
              || webEntryFailure
              || starting
              || stopping
              || unresolvedStop
              || !trace.issues.isEmpty()
              || !starting && !startNotice.isEmpty());
      if (!starting && !startNotice.isEmpty()) state.setStatus(startNotice);
      if (starting)
        state.setStatus(
            UiText.format(
                "%s · 本阶段 %s 秒 · 总计 %s 秒\n%s",
                trace.stage,
                trace.stageElapsedMs / 1000,
                trace.elapsedMs / 1000,
                trace.issues.isEmpty()
                    ? UiText.text("下方实时显示启动输出；等待不会自动终止。")
                    : UiText.text("检测到插件或配置异常，可查看恢复选项。")));
      state.setBusy(starting || stopping);
      webReady = ready;
      state.setStartLabel(
          enteringWeb
              ? UiText.text("进入中…")
              : ready ? UiText.choose("进入工作台", "Open workspace") : UiText.choose("启动", "Start"));
      state.setStartEnabled(!enteringWeb && !starting && !stopping && !restoring);
      state.setRestartEnabled(!starting && !stopping);
      state.setStopEnabled(!stopping);
      environmentRecovery = EnvironmentUiStatus.get(requireContext()).recovery;
      if (environmentRecovery) {
        state.setRunState(UiText.text("环境需要恢复"));
        state.setSubtitle(
            UiText.choose("先在下方「环境维护」里处理，完成后才能启动。", "Fix it under Environment maintenance below before starting."));
        state.setStartEnabled(false);
        state.setRestartEnabled(false);
        state.setSafeEnabled(false);
        state.setRecoveryLabel(UiText.text("环境维护"));
      } else {
        state.setSafeEnabled(!starting && !stopping);
        state.setRecoveryLabel(UiText.text("恢复选项"));
      }
    } catch (RuntimeException failure) {
      if (getContext() != null)
        com.deepseekharness.app.core.DiagnosticLog.record(
            getContext(), "UI_REFRESH_FAILED", failure.getClass().getSimpleName());
      state.setRunState(
          UiText.choose("运行状态暂时无法读取，请查看操作记录", "Run state unavailable. See operation history."));
    }
  }

  private void showRecovery() {
    startActivity(new Intent(requireContext(), StartupRecoveryActivity.class));
  }

  private void offerForceStop(Activity activity, View root, long generation) {
    final long consentRevision =
        new com.deepseekharness.app.core.DeviceGrants(activity).consentPolicy().revision();
    // 进程与文件身份核验放在工作线程；本次候选只捕获一次，由对话框局部持有至确认。
    new Thread(
            () -> {
              HarnessController.ForceStopRequest request = null;
              try {
                request = controller.prepareForceStop();
              } catch (java.io.IOException denied) {
                com.deepseekharness.app.core.DiagnosticLog.record(
                    activity.getApplicationContext(),
                    "WEB_FORCE_STOP_CANDIDATE",
                    denied.getClass().getSimpleName());
              }
              final HarnessController.ForceStopRequest candidate = request;
              ui.post(
                  () -> {
                    if (consentRevision
                            != new com.deepseekharness.app.core.DeviceGrants(activity)
                                .consentPolicy()
                                .revision()
                        || getView() != root
                        || !isResumed()
                        || activity.isFinishing()
                        || activity.isDestroyed()
                        || generation != controller.getWebGeneration()) return;
                    if (candidate == null) {
                      state.setStatus(
                          UiText.text("尚未确认 Web 退出；进程身份无法核验，保留运行屏障。请查看诊断后重试停止。"));
                      return;
                    }
                    new DshaDialogBuilder(requireContext(), true)
                        .setTitle(UiText.text("强制结束本次 Web？"))
                        .setMessage(
                            UiText.text(
                                "普通停止尚未确认 Web 退出。本次已核验进程属于 DSHA；强制结束可能中断正在写入的对话或文件。\n\n确认后只结束这次已核验的 Web 进程；身份或状态变化会拒绝执行。取消会保留现场与运行屏障。"))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(
                            UiText.text("强制结束"),
                            (dialog, which) -> {
                              if (consentRevision
                                      != new com.deepseekharness.app.core.DeviceGrants(activity)
                                          .consentPolicy()
                                          .revision()
                                  || getView() != root
                                  || !isResumed()
                                  || activity.isFinishing()
                                  || activity.isDestroyed()) return;
                              boolean accepted =
                                  controller.forceStop(
                                      candidate,
                                      message ->
                                          ui.post(
                                              () -> {
                                                if (getView() != root
                                                    || generation != controller.getWebGeneration())
                                                  return;
                                                state.setStatus(
                                                    com.deepseekharness.app.util.UiStateText.render(
                                                        message));
                                                refreshRunState();
                                                refreshLanAddr();
                                              }));
                              state.setStatus(
                                  accepted
                                      ? UiText.text("正在强制结束本次已核验的 Web…")
                                      : UiText.text("Web 状态或进程身份已变化，本次强制结束未执行。请重新停止后确认。"));
                            })
                        .show();
                  });
            },
            "web-force-stop-candidate")
        .start();
  }

  /** LAN 地址默认只展示服务地址；用户点按后查看并复制本轮连接凭据。 */
  private void refreshLanAddr() {
    if (screen == null || !isAdded()) return;
    boolean lan =
        requireContext()
            .getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE)
            .getBoolean(Constants.KEY_LAN_MODE, false);
    state.setLanVisible(lan);
    lanService = "";
    if (!lan) return;
    if (LanProxyService.isBound()) {
      String ip = HarnessController.getLanAddress();
      if (ip != null && !ip.isEmpty()) {
        lanService = "http://" + ip + ":" + LanProxyService.LAN_PORT + "/";
        state.setLan(
            UiText.format("%s\n点按查看或复制连接链接；流量未加密，请只在可信网络使用。", lanService));
      } else {
        state.setLan(UiText.text("局域网已开启，但还没拿到 WiFi 地址（连上 WiFi 再看）"));
      }
    } else {
      state.setLan(UiText.text("局域网代理正在等待本轮认证"));
    }
    state.setLanClickable(!lanService.isEmpty());
  }

  private void showLanAddress(String service) {
    if (!isAdded() || !LanProxyService.isBound()) return;
    final long generation = controller.getWebGeneration();
    final String token = LanProxyService.getLanToken(requireContext());
    final String address = service + "?token=" + token;
    new DshaDialogBuilder(requireContext())
        .setTitle(UiText.text("局域网连接链接"))
        .setMessage(
            UiText.format(
                "此链接包含访问凭据。局域网使用 HTTP，流量未加密，请只在可信网络使用并只分享给可信设备。DSH 重启或关闭局域网访问后，旧链接会失效，请重新复制。\n\n%s",
                address))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(
            UiText.text("复制连接链接"),
            (dialog, which) -> {
              String ip = HarnessController.getLanAddress();
              String currentService = "http://" + ip + ":" + LanProxyService.LAN_PORT + "/";
              if (!isAdded()
                  || !LanProxyService.isBound()
                  || generation != controller.getWebGeneration()
                  || !service.equals(currentService)
                  || !token.equals(LanProxyService.getLanToken(requireContext()))) {
                if (isAdded())
                  Toast.makeText(
                          requireContext(),
                          UiText.text("连接已变化，请重新查看链接。"),
                          Toast.LENGTH_LONG)
                      .show();
                return;
              }
              copyAddr(UiText.text("局域网地址"), address);
            })
        .show();
  }

  private void copyAddr(String label, String addr) {
    try {
      android.content.ClipboardManager cm =
          (android.content.ClipboardManager)
              requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (cm != null) {
        cm.setPrimaryClip(com.deepseekharness.app.bridge.SensitiveClipboard.text(label, addr));
        Toast.makeText(
                requireContext(),
                UiText.choose(
                    "已复制连接地址，请只分享给可信设备",
                    "Connection address copied. Share only with trusted devices"),
                Toast.LENGTH_SHORT)
            .show();
      }
    } catch (Throwable t) {
      Toast.makeText(
              requireContext(),
              UiText.format("复制失败：%s", t.getMessage()),
              Toast.LENGTH_SHORT)
          .show();
    }
  }
}
