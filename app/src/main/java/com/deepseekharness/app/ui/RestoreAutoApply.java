package com.deepseekharness.app.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.deepseekharness.app.backup.AndroidBackupFileSystem;
import com.deepseekharness.app.backup.BackupControl;
import com.deepseekharness.app.backup.BackupErrorCode;
import com.deepseekharness.app.backup.ProfileSettingsTransaction;
import com.deepseekharness.app.backup.RetainedCatalogue;
import com.deepseekharness.app.backup.UserDataLayout;
import com.deepseekharness.app.core.PluginRepository;
import com.deepseekharness.app.util.UiText;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 恢复完成后，自动做完原来要在「保留副本与旧树」里逐个点的步骤：
 * 每个 profile 的设置全部字段应用并读回，每个插件“恢复并启用”。用的都是原来那几步的代码，
 * 只是不用再手动点；失败的项留在「保留副本与旧树」里，可以手动重试。
 */
final class RestoreAutoApply {
  private static final MutableLiveData<String> progress = new MutableLiveData<>("");
  private static volatile String task = "";

  private RestoreAutoApply() {}

  /** 当前（或最近一次）自动恢复属于哪次恢复作业。 */
  static String task() {
    return task;
  }

  static LiveData<String> progress() {
    return progress;
  }

  static synchronized void start(Context context, String taskId) {
    if (taskId == null || taskId.isEmpty() || taskId.equals(task)) return;
    task = taskId;
    Context app = context.getApplicationContext();
    progress.postValue(UiText.choose("正在自动恢复设置和插件…", "Restoring settings and plugins…"));
    new Thread(() -> run(app, taskId), "restore-auto-apply").start();
  }

  private static void run(Context app, String taskId) {
    int settings = 0, plugins = 0;
    List<String> failed = new ArrayList<>();
    try {
      var fs = new AndroidBackupFileSystem();
      File files = app.getFilesDir().getCanonicalFile();
      // 恢复作业先报“完成”，之后才清理明文暂存并归档；同时动手会两边删同一批文件（FILESYSTEM_2）。
      // 等它归档完再开始。ponytail: 轮询，最多 60 秒；超时照常继续，失败项仍可手动重试。
      File active =
          new File(com.deepseekharness.app.backup.HostOperationArchive.root(files), taskId);
      for (int i = 0; i < 200 && !fs.stat(active).type.equals("MISSING"); i++) Thread.sleep(300);
      List<RetainedCatalogue.Entry> entries =
          new RetainedCatalogue(fs, files, new UserDataLayout(fs, files).current()).list();
      for (var entry : entries) {
        if (!entry.id.equals(taskId) || entry.kind != RetainedCatalogue.Kind.SETTINGS) continue;
        progress.postValue(UiText.format("正在恢复设置：%s", entry.displayName));
        try {
          Set<String> skipped = new LinkedHashSet<>();
          // 一项写不进去（如引用了本机没有的模型凭据）会让整批失败：去掉那一项再来，其余照常恢复。
          while (true) {
            Map<String, Object> preview =
                ProfileSettingsTransaction.preview(app, entry.key(), new BackupControl(null));
            Set<String> keys = new LinkedHashSet<>();
            if (preview.get("items") instanceof List<?> items)
              for (Object item : items)
                if (item instanceof Map<?, ?> row && row.get("id") instanceof String id) keys.add(id);
            keys.removeAll(skipped);
            // 没有差异就不用写。
            if (keys.isEmpty()) break;
            try {
              ProfileSettingsTransaction.apply(
                  app, (String) preview.get("operation"), keys, new BackupControl(null));
              break;
            } catch (Exception error) {
              String bad = rejected(error, keys);
              if (bad == null) throw error;
              skipped.add(bad);
            }
          }
          settings++;
          for (String id : skipped)
            failed.add(UiText.format("设置 %s（当前环境不接受）", entry.displayName + "/" + id));
        } catch (Exception error) {
          android.util.Log.w("DSHA", "RESTORE_AUTO_SETTINGS " + entry.displayName, error);
          failed.add(entry.displayName + "（" + BackupErrorCode.from(error) + "）");
        }
      }
      PluginRepository repository = PluginRepository.get(app);
      Handler main = new Handler(Looper.getMainLooper());
      for (var entry : entries) {
        if (!entry.id.equals(taskId)
            || entry.kind != RetainedCatalogue.Kind.QUARANTINE
            || !entry.part.startsWith("package-")) continue;
        progress.postValue(UiText.format("正在恢复插件：%s", entry.displayName));
        waitIdle(repository);
        main.post(() -> repository.reviewRestored(entry.id, entry.part));
        // 等它真正开始再等它结束；插件安装要在 Ubuntu 里跑，可能需要几分钟。
        long deadline = System.currentTimeMillis() + 5000;
        while (!repository.isBusy() && System.currentTimeMillis() < deadline) Thread.sleep(100);
        waitIdle(repository);
        var state = repository.state().getValue();
        // 同名插件已经装着（比如同一份备份恢复第二次），不算失败。
        if (repository.installationSucceeded()
            || state != null && String.valueOf(state.message).contains("已存在同名插件")) plugins++;
        else failed.add(entry.displayName + (state == null ? "" : "（" + state.message + "）"));
      }
    } catch (Exception error) {
      android.util.Log.w("DSHA", "RESTORE_AUTO", error);
      failed.add(BackupErrorCode.from(error));
    }
    String summary =
        UiText.format("已自动恢复：设置 %s 项，插件 %s 个。", settings, plugins)
            + (failed.isEmpty()
                ? ""
                : UiText.format(
                    "\n以下未能自动恢复，已保留在「保留副本与旧树」，可手动重试：\n%s",
                    String.join("\n", failed)));
    progress.postValue(summary);
  }

  /** 校验失败时报错里带着出错的那一项（ns/key）；认得出就返回它，否则 null。 */
  static String rejected(Exception error, Set<String> keys) {
    String message = String.valueOf(error.getMessage());
    if (!message.contains("SETTINGS_VALIDATION_FAILED:")) return null;
    for (String id : keys) if (message.endsWith(":" + id)) return id;
    return null;
  }

  private static void waitIdle(PluginRepository repository) throws InterruptedException {
    while (repository.isBusy()) Thread.sleep(300);
  }
}
