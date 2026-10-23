package com.deepseekharness.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.deepseekharness.app.R;

/** 原生内页共用卡片、分组和固定页脚，业务事件由页面自己绑定。 */
final class CardPage {
  final Context context;
  final LinearLayout root, content, footer;
  final ScrollView scroll;
  final TextView heading;
  private final String title;

  CardPage(Context context, String title, String subtitle) {
    this.context = context;
    this.title = title;
    root = column();
    root.setBackgroundColor(context.getColor(R.color.surface));
    scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    content = column();
    content.setPadding(dp(12), dp(12), dp(12), dp(24));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    heading = text(title, 26, R.color.text);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    heading.setPadding(dp(16), dp(8), dp(16), dp(subtitle.isEmpty() ? 16 : 0));
    content.addView(heading);
    if (!subtitle.isEmpty()) {
      TextView sub = text(subtitle, 14, R.color.text_secondary);
      sub.setPadding(dp(16), dp(8), dp(16), dp(16));
      sub.setLineSpacing(dp(2), 1f);
      content.addView(sub);
    }
    footer = column();
    footer.setPadding(dp(12), dp(8), dp(12), dp(12));
    root.addView(footer);
  }

  /** 独立页面：顶部换成 miuix 顶栏（返回 + 居中标题），去掉正文里重复的大标题。 */
  void topBar(Runnable back) {
    noHeading();
    root.addView(PageBarKt.pageBar(context, title, back), 0);
  }

  /** 主界面里的子页面已有顶栏标题，正文不再重复。 */
  void noHeading() {
    heading.setVisibility(View.GONE);
  }

  int dp(int n) {
    return Math.round(n * context.getResources().getDisplayMetrics().density);
  }

  LinearLayout column() {
    LinearLayout view = new LinearLayout(context);
    view.setOrientation(LinearLayout.VERTICAL);
    return view;
  }

  TextView text(String value, int size, int color) {
    TextView view = new TextView(context);
    view.setText(value);
    view.setTextSize(size);
    view.setIncludeFontPadding(false);
    view.setTextColor(context.getColor(color));
    return view;
  }

  LinearLayout card() {
    LinearLayout card = column();
    card.setBackgroundResource(R.drawable.bg_card);
    card.setPadding(dp(16), dp(4), dp(16), dp(4));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(12);
    content.addView(card, p);
    return card;
  }

  void label(String title) {
    TextView label = text(title, 14, R.color.text_muted);
    label.setPadding(dp(16), dp(12), dp(16), dp(8));
    content.addView(label);
  }

  void kv(LinearLayout card, String label, String value) {
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(56));
    row.addView(text(label, 16, R.color.text), new LinearLayout.LayoutParams(0, -2, 1));
    TextView content = text(value, 14, R.color.text_secondary);
    content.setMaxWidth(dp(210));
    content.setGravity(Gravity.END);
    row.addView(content);
    card.addView(row);
  }

  void entry(LinearLayout card, String title, String subtitle, int icon, Runnable action) {
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(14), 0, dp(14));
    row.setMinimumHeight(dp(64));
    row.setBackgroundResource(R.drawable.bg_action_plain);
    row.setFocusable(true);
    row.setOnClickListener(v -> action.run());
    ImageView mark = new ImageView(context);
    mark.setImageResource(icon);
    mark.setImageTintList(
        android.content.res.ColorStateList.valueOf(context.getColor(R.color.primary)));
    row.addView(mark, new LinearLayout.LayoutParams(dp(24), dp(24)));
    LinearLayout words = column();
    TextView heading = text(title, 16, R.color.text);
    words.addView(heading);
    TextView detail = text(subtitle, 14, R.color.text_secondary);
    detail.setPadding(0, dp(4), 0, 0);
    words.addView(detail);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.leftMargin = dp(16);
    row.addView(words, p);
    ImageView arrow = new ImageView(context);
    arrow.setImageResource(R.drawable.ic_ui2_chevron);
    arrow.setImageTintList(
        android.content.res.ColorStateList.valueOf(context.getColor(R.color.text_muted)));
    row.addView(arrow, new LinearLayout.LayoutParams(dp(16), dp(16)));
    card.addView(row);
  }

  /** miuix 按钮；primary 为主色。按钮自带上下 4dp 留白，这里只再补 4dp。 */
  MiuixButton button(LinearLayout parent, String title, boolean primary, Runnable action) {
    MiuixButton button = new MiuixButton(context);
    button.setText(title);
    button.setPrimary(primary);
    button.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(4);
    parent.addView(button, p);
    return button;
  }

}
