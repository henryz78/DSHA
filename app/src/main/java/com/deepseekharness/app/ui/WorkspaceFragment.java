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
 * 备份与恢复页：立即备份、从备份恢复、自动备份三件事，其余收在“更多”里。
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
    ui.setIncludeKey(OneClickBackup.includesApiKey(requireContext()));
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

          public void restore() {
            // 直接打开文件选择；选好后在数据页里验证、确认并恢复。
            startActivity(
                new android.content.Intent(requireContext(), NativeDataActivity.class)
                    .putExtra("data_mode", "restore")
                    .putExtra("pick", true));
          }

          public void automatic() {
            open(AutomaticBackupActivity.class);
          }

          public void includeKey(boolean on) {
            OneClickBackup.setIncludesApiKey(requireContext(), on);
          }

          public void advanced() {
            open(NativeDataActivity.class);
          }

          public void guide() {
            open(StorageHelpActivity.class);
          }

          public void history() {
            CardSheet.show(
                requireContext(),
                com.deepseekharness.app.util.UiText.choose(
                    "备份与维护记录", "Backup and maintenance history"),
                ui.getStatus());
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
    String where =
        android.os.Build.VERSION.SDK_INT < 29
            ? com.deepseekharness.app.util.UiText.choose(
                "首次选择保存目录，之后复用", "Choose a folder once, then reuse it")
            : com.deepseekharness.app.util.UiText.choose("保存到 下载/DSHA", "Saved to Download/DSHA");
    ui.setLastBackup(
        time == 0
            ? com.deepseekharness.app.util.UiText.choose("还没有备份", "No backup yet") + " · " + where
            : com.deepseekharness.app.util.UiText.format(
                    "上次：%s",
                    java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                        .format(new java.util.Date(time)))
                + " · "
                + where);
    ui.setAutomatic(automaticSummary());
    // 上次备份时间已写在“立即备份”下面，这里只放进度、错误和待处理的任务。
    StringBuilder summary = new StringBuilder();
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
    ui.setStatus(summary.toString().trim());
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

  /** 自动备份当前计划的一句话说明。 */
  private String automaticSummary() {
    var context = requireContext();
    if (!com.deepseekharness.app.backup.AutomaticBackups.enabled(context))
      return com.deepseekharness.app.util.UiText.choose("已关闭", "Off");
    var prefs = com.deepseekharness.app.backup.AutomaticBackups.prefs(context);
    String mode = prefs.getString("mode", "daily");
    if ("interval".equals(mode))
      return com.deepseekharness.app.util.UiText.format("每 %s 小时", prefs.getInt("hours", 24));
    if ("stop".equals(mode))
      return com.deepseekharness.app.util.UiText.choose("每次停止 DSH 后", "After DSH stops");
    int minute = prefs.getInt("minute", 180);
    return com.deepseekharness.app.util.UiText.format(
        "每天 %s",
        String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60));
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
