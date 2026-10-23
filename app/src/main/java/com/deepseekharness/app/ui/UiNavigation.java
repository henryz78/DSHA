package com.deepseekharness.app.ui;

import android.content.Context;
import android.view.*;
import android.widget.*;
import com.deepseekharness.app.R;

/** 使用矢量返回图标，视觉和触摸中心一致。 */
final class UiNavigation {
  static ImageButton back(Context context, Runnable action) {
    var button = new androidx.appcompat.widget.AppCompatImageButton(context);
    button.setImageResource(R.drawable.ic_ui_back);
    android.util.TypedValue value = new android.util.TypedValue();
    context
        .getTheme()
        .resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
    button.setBackgroundResource(value.resourceId);
    int pad = dp(context, 12);
    button.setPadding(pad, pad, pad, pad);
    button.setContentDescription(com.deepseekharness.app.util.UiText.choose("返回", "Back"));
    button.setOnClickListener(v -> action.run());
    return button;
  }

  /** 非 CardPage 页面的顶栏，与 CardPage.topBar 同为 miuix 样式。 */
  static void addHeader(Context context, LinearLayout root, String title, Runnable action) {
    root.addView(PageBarKt.pageBar(context, title, action), 0);
  }

  private static int dp(Context context, int n) {
    return Math.round(n * context.getResources().getDisplayMetrics().density);
  }
}
