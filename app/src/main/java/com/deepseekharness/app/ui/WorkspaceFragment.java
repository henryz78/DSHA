package com.deepseekharness.app.ui;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.deepseekharness.app.R;
import com.deepseekharness.app.core.HarnessController;
import com.deepseekharness.app.backup.OneClickBackup;

/**
 * 数据与备份子页：备份（按范围 + 验证）/ 恢复（合并 + 验证）。
 */
public class WorkspaceFragment extends Fragment implements OwnTopBar {

  private final Handler main = new Handler(Looper.getMainLooper());
  private HarnessController controller;
  private com.deepseekharness.app.core.BackupTask task;
  private AlertDialog previewDialog;
  private final WorkspaceUi ui = new WorkspaceUi();
  private OneClickBackup oneClick;
  private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> backupDirectory =
      registerForActivityResult(
          new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
          uri -> {
            if (oneClick != null) oneClick.directoryResult(uri);
          });

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    controller = HarnessController.get(requireContext());
    task = com.deepseekharness.app.core.BackupTask.get(requireContext());
    oneClick = new androidx.lifecycle.ViewModelProvider(this).get(OneClickBackup.class);
    ui.setBackupHint(
        android.os.Build.VERSION.SDK_INT < 29
            ? com.deepseekharness.app.util.UiText.choose(
                "首次选择保存目录，之后复用，无需密码；备份时会安全停止 DSH 和运行任务。包含对话、配置、插件和当前应用内工作区；外置工作区请另备。",
                "Choose a folder once, then reuse it. No password; backup safely stops DSH and running tasks. Includes conversations, settings, plugins and the current app workspace. Back up external workspaces separately.")
            : com.deepseekharness.app.util.UiText.choose(
                "直接保存到 下载/DSHA，无需密码；备份时会安全停止 DSH 和运行任务。包含对话、配置、插件和当前应用内工作区；外置工作区请另备。",
                "Saves directly to Download/DSHA without a password; backup safely stops DSH and running tasks. Includes conversations, settings, plugins and the current app workspace. Back up external workspaces separately."));
    refreshBackupStatus();
    return WorkspaceScreenKt.workspacePage(
        requireContext(),
        ui,
        new WorkspaceActions() {
          public void backup() {
            if (!oneClick.start())
              toast(
                  com.deepseekharness.app.util.UiText.choose(
                      "已有备份或保存位置选择正在进行。",
                      "A backup or destination selection is already in progress."));
          }

          public void cancel() {
            oneClick.cancel();
          }

          public void files() {
            open(WorkspaceFilesActivity.class);
          }

          public void guide() {
            open(StorageHelpActivity.class);
          }

          public void skills() {
            open(SkillsActivity.class);
          }

          public void storage() {
            open(StorageActivity.class);
          }

          public void more() {
            open(NativeDataActivity.class);
          }

          public void history() {
            CardSheet.show(
                requireContext(),
                com.deepseekharness.app.util.UiText.choose(
                    "备份与维护记录", "Backup and maintenance history"),
                ui.getStatus());
          }

          public void reset() {
            if (task.busy() || task.pendingMaintenance()) {
              taskRejected();
              return;
            }
            new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext(), true)
                .setTitle(com.deepseekharness.app.util.UiText.text("重置配置？"))
                .setMessage(
                    com.deepseekharness.app.util.UiText.choose(
                        "先停止 Web、终端和写任务，在隔离服务中重置当前 web profile 的普通设置并读回，再事务提交与工作目录 .env。插件构成、对话和凭据保留；原配置可恢复。无法确认时保留原件并显示原因。",
                        "Stop Web, terminals and writers. Reset ordinary settings of the web profile in an isolated service, read them back, then commit together with the workspace .env. Plugin composition, conversations and credentials are retained. Failures preserve originals and report the reason."))
                .setPositiveButton(
                    com.deepseekharness.app.util.UiText.choose(
                        "保留原件并重置", "Retain originals and reset"),
                    (d, w) -> {
                      if (!task.resetConfig()) taskRejected();
                    })
                .setNegativeButton(com.deepseekharness.app.util.UiText.text("取消"), null)
                .show();
          }
        });
  }

  private void open(Class<?> page) {
    startActivity(new android.content.Intent(requireContext(), page));
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    oneClick.status.observe(getViewLifecycleOwner(), value -> refreshBackupStatus());
    oneClick.busy.observe(getViewLifecycleOwner(), value -> refreshBackupStatus());
    oneClick.chooseDirectory.observe(
        getViewLifecycleOwner(),
        needed -> {
          if (Boolean.TRUE.equals(needed)) pickBackupDirectory();
        });
  }

  private void pickBackupDirectory() {
    if (isResumed() && oneClick != null && oneClick.claimDirectoryPicker())
      backupDirectory.launch(null);
  }

  private void taskRejected() {
    toast(
        task.pendingMaintenance()
            ? com.deepseekharness.app.util.UiText.text("请先点「恢复中断维护」，原环境尚未确认。")
            : com.deepseekharness.app.util.UiText.text("已有数据任务进行中，请等待或处理恢复预览。"));
  }

  private final Runnable refreshTask =
      new Runnable() {
        @Override
        public void run() {
          if (getView() == null || task == null) return;
          refreshBackupStatus();
          main.postDelayed(this, 1_000);
        }
      };

  private void refreshBackupStatus() {
    long time = controller.config().getLastBackupSuccess();
    String success =
        time == 0
            ? com.deepseekharness.app.util.UiText.choose(
                "还没有手动导出备份", "No manually exported backup yet")
            : com.deepseekharness.app.util.UiText.format(
                "最近手动备份：%s\n%s",
                java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(time)),
                controller.config().getLastBackupName());
    long automatic =
        com.deepseekharness.app.backup.AutomaticBackups.prefs(requireContext()).getLong("last", 0);
    StringBuilder summary = new StringBuilder(success);
    if (automatic > 0)
      summary.append(
          com.deepseekharness.app.util.UiText.format(
              "\n最近自动副本：%s",
              java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(automatic))));
    String failure = controller.config().getLastBackupError();
    com.deepseekharness.app.util.BackupTaskState.Snapshot s = task.snapshot();
    EnvironmentUiStatus.Snapshot environment = EnvironmentUiStatus.get(requireContext());
    boolean nativeBusy = oneClick != null && Boolean.TRUE.equals(oneClick.busy.getValue());
    boolean busy = task.busy() || nativeBusy, pending = environment.recovery;
    if (!failure.isEmpty())
      summary.append(com.deepseekharness.app.util.UiText.format("\n上次未完成：%s", failure));
    if (s.id != 0)
      summary
          .append("\n\n")
          .append(com.deepseekharness.app.util.UiStateText.render(s.kind))
          .append("\n")
          .append(com.deepseekharness.app.util.MaintenanceErrorText.render(s.detail));
    if (pending)
      summary.append(com.deepseekharness.app.util.UiText.text("\n\n有未完成的环境维护，请恢复原环境后再继续。"));
    String nativeStatus = oneClick == null ? null : oneClick.status.getValue();
    if (nativeStatus != null && !nativeStatus.isEmpty())
      summary.append("\n\n").append(nativeStatus);
    ui.setCancelVisible(nativeBusy);
    ui.setStatus(summary.toString());
    ui.setEnabled(!busy && !pending);
    if (s.status == com.deepseekharness.app.util.BackupTaskState.Status.PREVIEW
        && previewDialog == null
        && isResumed()) {
      previewDialog =
          new com.deepseekharness.app.ui.DshaDialogBuilder(requireContext())
              .setTitle(com.deepseekharness.app.util.UiText.text("恢复预览"))
              .setMessage(com.deepseekharness.app.util.UiStateText.render(s.detail))
              .setPositiveButton(
                  com.deepseekharness.app.util.UiText.text("恢复此备份"),
                  (d, w) -> task.decide(s.id, true))
              .setNegativeButton(
                  com.deepseekharness.app.util.UiText.text("取消"),
                  (d, w) -> task.decide(s.id, false))
              .setOnCancelListener(d -> task.decide(s.id, false))
              .create();
      previewDialog.setOnDismissListener(d -> previewDialog = null);
      previewDialog.show();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    main.removeCallbacks(refreshTask);
    main.post(refreshTask);
    pickBackupDirectory();
  }

  @Override
  public void onPause() {
    main.removeCallbacks(refreshTask);
    if (previewDialog != null) {
      previewDialog.dismiss();
      previewDialog = null;
    }
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    main.removeCallbacks(refreshTask);
    super.onDestroyView();
  }

  private void toast(String s) {
    Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
  }
}
