package com.deepseekharness.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.MaintenanceCoordinator;
import com.deepseekharness.app.core.BackupTask;
import com.deepseekharness.app.core.InstallRepository;
import com.deepseekharness.app.util.EnvironmentTaskGate;
import com.deepseekharness.app.util.InstallTask;
import com.deepseekharness.app.util.SensitiveData;

/** 安装页只展示应用级任务快照；页面销毁不影响后台任务、取消信号或结果。 */
public class InstallFragment extends Fragment implements OwnTopBar {
  private final InstallUi ui = new InstallUi();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private InstallRepository repository;
  private long shownRevision = -1;
  private String lastRenderKey = "";
  private final Runnable refresh =
      new Runnable() {
        @Override
        public void run() {
          if (!isResumed()) return;
          render();
          handler.postDelayed(this, 1_000);
        }
      };

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
    repository = InstallRepository.get(requireContext());
    shownRevision = -1;
    lastRenderKey = "";
    var controller = com.deepseekharness.app.core.HarnessController.get(requireContext());
    ui.setEnvironment(
        controller.isEnvironmentReady()
            ? com.deepseekharness.app.util.UiText.choose("可用", "Available")
            : com.deepseekharness.app.util.UiText.choose("等待检查", "Needs checking"));
    ui.setAppVersion(com.deepseekharness.app.BuildConfig.VERSION_NAME);
    try {
      var descriptor = controller.proot().installedRuntimeDescriptor();
      var identity = descriptor == null ? java.util.Map.of() : descriptor.json();
      ui.setBase(String.valueOf(identity.getOrDefault("baseVersion", "—")));
      ui.setRuntime(String.valueOf(identity.getOrDefault("dshVersion", "—")));
    } catch (java.io.IOException unknown) {
      ui.setBase("—");
      ui.setRuntime("—");
    }
    View page =
        InstallScreenKt.installPage(
            requireContext(),
            ui,
            new InstallActions() {
              public void speedTest() {
                startActivity(new Intent(requireContext(), ColdSetupSpeedActivity.class));
              }

              public void check() {
                start(false, 0);
              }

              public void repair() {
                start(true, 0);
              }

              public void cancel() {
                repository.cancel();
                render();
              }

              public void copyLog() {
                InstallFragment.this.copyLog();
              }

              public void maintenance() {
                confirmMaintenance();
              }

              public void step(int step) {
                showStep(step);
              }

              public void managedUpdate() {
                startActivity(
                    new Intent(requireContext(), ExtractActivity.class)
                        .putExtra("review_only", true));
              }

              public void recovery() {
                startActivity(new Intent(requireContext(), StartupRecoveryActivity.class));
              }

              public void tasks() {
                BackgroundTasksActivity.open(requireContext());
              }
            });
    render();
    return page;
  }

  @Override
  public void onResume() {
    super.onResume();
    handler.removeCallbacks(refresh);
    handler.post(refresh);
  }

  @Override
  public void onPause() {
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    handler.removeCallbacks(refresh);
    shownRevision = -1;
    lastRenderKey = "";
    super.onDestroyView();
  }

  private void start(boolean repair, int step) {
    if (!repository.start(repair, step))
      Toast.makeText(
              requireContext(),
              BackupTask.get(requireContext()).pendingMaintenance()
                  ? com.deepseekharness.app.util.UiText.text("请先恢复中断维护，再检查或修复环境")
                  : com.deepseekharness.app.util.UiText.text("无法开始安装任务，请稍后重试或先完成正在进行的环境任务"),
              Toast.LENGTH_LONG)
          .show();
    render();
  }

  private void showStep(int step) {
    InstallTask.Snapshot state = repository.snapshot();
    androidx.appcompat.app.AlertDialog.Builder dialog =
        new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext())
            .setTitle(com.deepseekharness.app.util.UiText.text(InstallTask.NAMES[step - 1]))
            .setMessage(
                com.deepseekharness.app.util.MaintenanceErrorText.render(state.details[step - 1]))
            .setNeutralButton(com.deepseekharness.app.util.UiText.text("关闭"), null);
    if (!state.busy()
        && !MaintenanceCoordinator.isEnvironmentTaskBusy()
        && !BackupTask.get(requireContext()).pendingMaintenance()) {
      dialog
          .setPositiveButton(
              com.deepseekharness.app.util.UiText.text("按需修复"), (d, which) -> start(true, step))
          .setNegativeButton(
              com.deepseekharness.app.util.UiText.text("重新检查"), (d, which) -> start(false, step));
    }
    dialog.show();
  }

  private void render() {
    if (repository == null || getContext() == null) return;
    InstallTask.Snapshot state = repository.snapshot();
    boolean environmentBusy = MaintenanceCoordinator.isEnvironmentTaskBusy();
    BackupTask maintenanceTask = BackupTask.get(requireContext());
    boolean pending = maintenanceTask.pendingMaintenanceForUi();
    String renderKey =
        state.revision
            + ":"
            + state.elapsedSeconds
            + ":"
            + state.stageSeconds
            + ":"
            + state.outcome
            + ":"
            + state.cancelRequested
            + ":"
            + environmentBusy
            + ":"
            + pending;
    if (renderKey.equals(lastRenderKey)) return;
    lastRenderKey = renderKey;
    ui.setStatus(
        state.busy()
            ? state.phase
            : environmentBusy
                ? "环境任务进行中：" + EnvironmentTaskGate.activeKind()
                : pending
                    ? com.deepseekharness.app.util.UiText.text("上次环境维护尚未完成，请先恢复原环境")
                    : state.outcome == InstallTask.Outcome.IDLE
                        ? com.deepseekharness.app.util.UiText.text("检查环境，或按需修复缺项")
                        : state.phase);
    ui.setProgress(
        state.outcome == InstallTask.Outcome.IDLE
            ? ""
            : com.deepseekharness.app.util.UiText.format(
                "%s · 总耗时 %s 秒%s%s",
                (state.repair
                    ? com.deepseekharness.app.util.UiText.text("检查与按需修复")
                    : com.deepseekharness.app.util.UiText.text("仅检查")),
                state.elapsedSeconds,
                (state.busy()
                    ? com.deepseekharness.app.util.UiText.format(" · 当前阶段 %s 秒", state.stageSeconds)
                    : ""),
                (state.cancelRequested && state.busy()
                    ? (state.cancellable
                        ? com.deepseekharness.app.util.UiText.text("\n正在取消检查…")
                        : com.deepseekharness.app.util.UiText.text("\n等待当前修复到达安全点，随后停止"))
                    : "")));
    int completed = 0, total = state.selected == 0 ? 6 : 1;
    for (int i = 0; i < state.steps.length; i++) {
      if (state.selected == 0 || state.selected == i + 1) {
        if (state.steps[i] == InstallTask.Step.OK
            || state.steps[i] == InstallTask.Step.FAILED
            || state.steps[i] == InstallTask.Step.SKIPPED) completed++;
      }
      int value;
      switch (state.steps[i]) {
        case RUNNING:
          value = 1;
          break;
        case OK:
          value = 2;
          break;
        case FAILED:
          value = 3;
          break;
        case SKIPPED:
          value = 4;
          break;
        default:
          value = 0;
      }
      ui.setStep(i, value);
    }
    ui.setProgressValue(state.busy() ? completed / (float) total : -1f);
    ui.setCheckEnabled(!state.busy() && !environmentBusy && !pending);
    ui.setMaintenanceText(
        pending
            ? com.deepseekharness.app.util.UiText.text("恢复中断维护")
            : com.deepseekharness.app.util.UiText.text("备份并重建环境"));
    ui.setMaintenanceEnabled(!state.busy() && !environmentBusy);
    ui.setCancelVisible(state.busy());
    ui.setCancelEnabled(!state.cancelRequested);
    ui.setCancelText(
        state.cancelRequested
            ? com.deepseekharness.app.util.UiText.text("等待停止…")
            : state.cancellable
                ? com.deepseekharness.app.util.UiText.text("取消检查")
                : com.deepseekharness.app.util.UiText.text("安全停止后续修复"));
    ui.setError(
        state.failure.isEmpty()
            ? ""
            : com.deepseekharness.app.util.MaintenanceErrorText.render(state.failure));
    if (shownRevision != state.revision) {
      shownRevision = state.revision;
      ui.setLog(state.log);
    }
  }

  private void copyLog() {
    try {
      android.content.ClipboardManager clipboard =
          (android.content.ClipboardManager)
              requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
      if (clipboard != null) {
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                com.deepseekharness.app.util.UiText.text("DSHA 安装日志"), repository.snapshot().log));
        Toast.makeText(
                requireContext(),
                com.deepseekharness.app.util.UiText.text("已复制脱敏日志"),
                Toast.LENGTH_SHORT)
            .show();
      }
    } catch (RuntimeException error) {
      Toast.makeText(
              requireContext(),
              com.deepseekharness.app.util.UiText.text("复制失败，请稍后重试"),
              Toast.LENGTH_SHORT)
          .show();
    }
  }

  private void confirmMaintenance() {
    BackupTask maintenance = BackupTask.get(requireContext());
    if (repository.snapshot().busy() || maintenance.busy()) {
      Toast.makeText(
              requireContext(),
              com.deepseekharness.app.util.UiText.text("已有环境任务进行中，请等待完成"),
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    boolean recovery = maintenance.pendingMaintenance();
    new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext(), true)
        .setTitle(
            recovery
                ? com.deepseekharness.app.util.UiText.text("恢复中断维护？")
                : com.deepseekharness.app.util.UiText.text("备份并重建环境？"))
        .setMessage(
            recovery
                ? com.deepseekharness.app.util.UiText.text("先停止 Web，再回切旧环境；安全备份和失败的新环境均保留。")
                : com.deepseekharness.app.util.UiText.text(
                    "会停止 Web 并中断正在执行的任务，完整备份并校验配置、会话和本地插件，再重建环境并恢复数据。\n\n备份失败不切换环境，后续失败回切旧环境；安全备份和旧环境会保留并占用额外空间。额外安装的系统软件留在旧环境中。"))
        .setPositiveButton(
            recovery
                ? com.deepseekharness.app.util.UiText.text("恢复原环境")
                : com.deepseekharness.app.util.UiText.text("备份并重建"),
            (dialog, which) -> {
              if (!isAdded()) return;
              try {
                // BackupTask 在发布任务前原子取得同一全局锁，旧弹窗也不能绕过互斥。
                if (!(recovery ? maintenance.recoverMaintenance() : maintenance.rebuild())) {
                  Toast.makeText(
                          requireContext(),
                          com.deepseekharness.app.util.UiText.text("已有环境任务或未完成维护，请稍后重试"),
                          Toast.LENGTH_LONG)
                      .show();
                  return;
                }
                startActivity(
                    new Intent(requireContext(), ExtractActivity.class)
                        .putExtra("data_task_id", maintenance.snapshot().id));
              } catch (Throwable error) {
                Toast.makeText(
                        requireContext(),
                        com.deepseekharness.app.util.UiText.format(
                            "无法打开维护页，可到数据与备份页查看任务：%s", SensitiveData.redact(String.valueOf(error))),
                        Toast.LENGTH_LONG)
                    .show();
              }
            })
        .setNegativeButton(com.deepseekharness.app.util.UiText.text("取消"), null)
        .show();
  }
}
