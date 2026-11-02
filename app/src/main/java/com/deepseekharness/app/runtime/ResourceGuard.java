package com.deepseekharness.app.runtime;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.system.Os;
import android.system.OsConstants;
import android.widget.Toast;
import com.deepseekharness.app.core.DiagnosticLog;
import com.deepseekharness.app.util.ResourceGuardPolicy;
import com.deepseekharness.app.util.UiText;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 资源保护：Linux 运行期间每秒检查手机存储、内存和进程数，越线就结束本应用名下除 App 自身以外的全部进程，
 * 并锁住启动，直到用户在主界面确认。所有 Linux 启动入口都经过 {@link #guest}。
 */
public final class ResourceGuard {
  private static final String PREFS = "resource_guard", KEY = "tripped";
  private static final int IDLE_EXIT_SECONDS = 10;

  private static Thread watcher;
  private static int idle;

  private ResourceGuard() {}

  /** 启动前调用：被锁住或存储已不足时拒绝启动；否则开始监控，并给命令套上进程硬上限。 */
  static List<String> guest(Context context, List<String> command) throws IOException {
    if (context != null) {
      Context app = context.getApplicationContext();
      String reason = trippedText(app);
      if (reason != null)
        throw new IOException(
            UiText.choose("资源保护已停止 Linux：", "Resource protection stopped Linux: ")
                + reason
                + UiText.choose("请先在主界面顶部确认。", " Confirm it at the top of the main screen first."));
      if (free(app) < ResourceGuardPolicy.MIN_FREE_BYTES)
        throw new IOException(text(ResourceGuardPolicy.Reason.STORAGE));
      watch(app);
    }
    return ResourceGuardPolicy.limited(command);
  }

  /** 已触发且未确认时返回原因说明，否则 null。 */
  public static String trippedText(Context context) {
    String name = prefs(context).getString(KEY, null);
    if (name == null) return null;
    try {
      return text(ResourceGuardPolicy.Reason.valueOf(name));
    } catch (IllegalArgumentException unknown) {
      return text(ResourceGuardPolicy.Reason.PROCESSES);
    }
  }

  /** 用户确认后解除锁定，允许再次启动。 */
  public static void acknowledge(Context context) {
    prefs(context).edit().remove(KEY).commit();
  }

  static String text(ResourceGuardPolicy.Reason reason) {
    switch (reason) {
      case STORAGE:
        return UiText.choose(
            "手机剩余存储不足 2 GB。可在「文件」里删除大文件，或重置 Linux 环境。",
            "Less than 2 GB of phone storage is left. Delete large files in Files, or reset the Linux environment.");
      case MEMORY:
        return UiText.choose("手机可用内存过低。", "The phone is critically low on memory.");
      default:
        return UiText.choose("Linux 里的进程数异常增多。", "Linux started an abnormal number of processes.");
    }
  }

  private static synchronized void watch(Context app) {
    idle = 0;
    if (watcher != null) return;
    watcher = new Thread(() -> loop(app), "dsha-resource-guard");
    watcher.setDaemon(true);
    watcher.start();
  }

  private static void loop(Context app) {
    ResourceGuardPolicy policy = new ResourceGuardPolicy();
    ActivityManager am = app.getSystemService(ActivityManager.class);
    ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
    while (true) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        return;
      }
      List<Integer> guests = guestPids(am);
      synchronized (ResourceGuard.class) {
        if (guests.isEmpty() && ++idle >= IDLE_EXIT_SECONDS) {
          watcher = null;
          return;
        }
        if (!guests.isEmpty()) idle = 0;
      }
      if (guests.isEmpty()) continue;
      am.getMemoryInfo(memory);
      ResourceGuardPolicy.Reason reason = policy.check(free(app), memory.availMem, guests.size());
      if (reason != null) trip(app, am, reason);
    }
  }

  private static void trip(Context app, ActivityManager am, ResourceGuardPolicy.Reason reason) {
    // 先锁住，防止自动重启在结束进程期间又拉起 Linux。
    prefs(app).edit().putString(KEY, reason.name()).commit();
    // 无限开进程时一轮杀不干净，反复扫到没有为止。
    for (int round = 0; round < 50; round++) {
      List<Integer> guests = guestPids(am);
      if (guests.isEmpty()) break;
      for (int pid : guests)
        try {
          Os.kill(pid, OsConstants.SIGKILL);
        } catch (Exception ignored) {
        }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        break;
      }
    }
    DiagnosticLog.record(app, "RESOURCE_GUARD", reason.name());
    String message = UiText.choose("已停止 Linux 以保护手机：", "Stopped Linux to protect the phone: ") + text(reason);
    new Handler(Looper.getMainLooper())
        .post(() -> Toast.makeText(app, message, Toast.LENGTH_LONG).show());
  }

  /** 本应用名下、但不是 App 自身进程的全部进程（即 Linux 里的进程）。进程号向系统要，不靠名字认。 */
  private static List<Integer> guestPids(ActivityManager am) {
    Set<Integer> own = new HashSet<>();
    own.add(android.os.Process.myPid());
    List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
    if (running != null) for (var info : running) own.add(info.pid);
    int uid = android.os.Process.myUid();
    List<Integer> result = new ArrayList<>();
    String[] names = new File("/proc").list();
    if (names == null) return result;
    for (String name : names) {
      if (name.isEmpty() || !Character.isDigit(name.charAt(0))) continue;
      int pid;
      try {
        pid = Integer.parseInt(name);
      } catch (NumberFormatException e) {
        continue;
      }
      if (own.contains(pid)) continue;
      try {
        if (Os.stat("/proc/" + name).st_uid == uid) result.add(pid);
      } catch (Exception gone) {
        // 进程已退出
      }
    }
    return result;
  }

  private static long free(Context app) {
    try {
      return new StatFs(app.getFilesDir().getAbsolutePath()).getAvailableBytes();
    } catch (IllegalArgumentException e) {
      return Long.MAX_VALUE;
    }
  }

  private static android.content.SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}
