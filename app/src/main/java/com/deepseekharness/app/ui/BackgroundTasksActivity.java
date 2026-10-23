package com.deepseekharness.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.deepseekharness.app.R;
import com.deepseekharness.app.backup.NativeBackupJobs;
import com.deepseekharness.app.core.RuntimeTasks;
import com.deepseekharness.app.util.UiStateText;
import com.deepseekharness.app.util.UiText;

/** 任务观察页只读状态；取消操作仍交给原业务页面的安全提交边界。 */
public final class BackgroundTasksActivity extends AppCompatActivity {
  private HyLine status;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        @Override
        public void run() {
          render();
          handler.postDelayed(this, 1000);
        }
      };

  public static void open(Context context) {
    context.startActivity(new Intent(context, BackgroundTasksActivity.class));
  }

  public static boolean busy(Context context) {
    return RuntimeTasks.isBusy() || NativeBackupJobs.get(context).state().busy;
  }

  private static String t(String zh, String en) {
    return UiText.choose(zh, en);
  }

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    var model = new HyScreenModel(t("后台任务", "Background tasks"));
    model.note(
        t(
            "查看正在执行的操作和进度。需要取消时，请进入对应页面。",
            "View active operations and progress. Open the related page to cancel safely."));
    status = model.section("").selectable("");
    var links = model.section(t("相关页面", "Related pages"));
    links.arrow(
        t("Agent 任务", "Agent tasks"),
        "",
        () -> startActivity(new Intent(this, AgentTasksActivity.class)));
    links.arrow(t("插件管理", "Plugin management"), "", () -> main("open_plugins"));
    links.arrow(t("终端", "Terminals"), "", () -> main("open_terminal"));
    links.arrow(
        t("数据与备份", "Data and backups"),
        "",
        () -> startActivity(new Intent(this, NativeDataActivity.class)));
    links.arrow(
        t("自动备份", "Automatic backups"),
        "",
        () -> startActivity(new Intent(this, AutomaticBackupActivity.class)));
    links.arrow(t("安装与环境", "Installation and environment"), "", () -> main("open_install"));
    setContentView(HyScreenKt.hyScreen(this, model, true));
    render();
  }

  private void main(String extra) {
    startActivity(
        new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(extra, true));
    finish();
  }

  private void render() {
    if (status == null) return;
    StringBuilder text = new StringBuilder();
    var maintenance = com.deepseekharness.app.core.BackupTask.get(this).snapshot();
    if (maintenance.busy())
      text.append(UiStateText.render(maintenance.kind))
          .append('\n')
          .append(UiStateText.render(maintenance.detail))
          .append("\n\n");
    var backup = NativeBackupJobs.get(this).state();
    if (backup.busy)
      text.append(t("数据与备份", "Data and backups"))
          .append("\n")
          .append(AutomaticBackupActivity.stage(backup.stage))
          .append(" · ")
          .append(backup.entries)
          .append(t(" 项", " entries"))
          .append("\n\n");
    for (var row : RuntimeTasks.snapshot()) {
      text.append(UiStateText.render(row.kind))
          .append(" · ")
          .append(row.elapsedMillis / 1000)
          .append(t(" 秒", " s"))
          .append('\n');
      text.append(row.detail.isEmpty() ? t("正在执行", "Running") : UiStateText.render(row.detail))
          .append("\n\n");
    }
    if (text.length() == 0)
      text.append(t("当前没有原生后台任务。", "No native background tasks are running."));
    String value = text.toString().trim();
    if (!value.equals(status.getText())) status.setText(value);
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler.removeCallbacks(refresh);
    handler.post(refresh);
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacks(refresh);
    super.onDestroy();
  }
}
