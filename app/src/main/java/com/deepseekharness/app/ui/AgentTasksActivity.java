package com.deepseekharness.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.deepseekharness.app.AgentTaskNotifications;
import com.deepseekharness.app.R;
import com.deepseekharness.app.util.UiText;

/** Actual Agent turn facts; all cancellation goes through the authenticated one-shot queue. */
public final class AgentTasksActivity extends AppCompatActivity {
  private HyScreenModel model;
  private String signature = "";
  private final java.util.Map<
          com.deepseekharness.app.util.AgentTaskState.Key, HyLine>
      timings = new java.util.HashMap<>();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          render();
          main.postDelayed(this, 1000);
        }
      };

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    model = new HyScreenModel(UiText.choose("Agent 任务", "Agent tasks"));
    model.note(
        UiText.choose(
            "查看实际对话进度。停止只作用于对应任务。显示最多 32 个活动任务及最近 5 个结束状态。",
            "View actual conversation progress. Stop affects only the selected task. Shows up to 32 active tasks and the 5 most recent ended states."));
    setContentView(HyScreenKt.hyScreen(this, model, true));
  }

  @Override
  protected void onStart() {
    super.onStart();
    main.post(refresh);
  }

  @Override
  protected void onStop() {
    main.removeCallbacks(refresh);
    super.onStop();
  }

  private void render() {
    var source = AgentTaskNotifications.get(this);
    var rows = source.snapshot();
    String next =
        rows.stream()
            .map(
                row ->
                    row.key()
                        + ":"
                        + row.phase()
                        + ":"
                        + row.reason()
                        + ":"
                        + row.active()
                        + ":"
                        + row.stopping())
            .collect(java.util.stream.Collectors.joining("|"));
    if (next.equals(signature) && !timings.isEmpty()) {
      for (var row : rows) timings.get(row.key()).setText(timing(row));
      return;
    }
    signature = next;
    // 第一组是页面说明，其余每组对应一个任务。
    while (model.getGroups().size() > 1) model.getGroups().remove(model.getGroups().size() - 1);
    timings.clear();
    if (rows.isEmpty()) {
      model.section("").text(UiText.choose("暂无任务记录", "No task records"));
      return;
    }
    for (var row : rows) {
      var card = model.section("");
      card.kv(AgentTaskNotifications.phase(row), "");
      timings.put(row.key(), card.text(timing(row)));
      if (row.active() && !row.stopping() && !row.phase().equals("unknown"))
        card.button(
            UiText.choose("停止", "Stop"),
            false,
            () -> {
              source.stop(AgentTaskNotifications.extras(new android.content.Intent(), row));
              render();
            });
      card.arrow(
          UiText.choose("回聊天", "Return to chat"),
          "",
          () ->
              startActivity(
                  BrowserConversationRoute.conversation(
                      this, row.key().generation(), row.key().instance(), row.key().session())));
    }
  }

  private static String timing(com.deepseekharness.app.util.AgentTaskState.View row) {
    return row.key().session()
        + " · "
        + row.key().turn()
        + " · "
        + Math.max(
            0, ((row.active() ? SystemClock.elapsedRealtime() : row.seen()) - row.started()) / 1000)
        + "s";
  }
}
