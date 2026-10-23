package com.deepseekharness.app.ui;

import android.app.Activity;

/** 首次引导共用排版：miuix 大标题页面，信息放在卡片里，主操作在底部。 */
final class OnboardingUi {
  final Activity activity;
  private final HyScreenModel model;
  private final HyGroup info;
  private HyGroup notes;

  OnboardingUi(Activity owner, String step, String title, String subtitle) {
    activity = owner;
    model = new HyScreenModel(title);
    model.note(step + " · " + subtitle);
    info = model.section("");
    owner.setContentView(HyScreenKt.hyScreen(owner, model, true));
  }

  void note(String s) {
    if (notes == null) notes = model.section("");
    notes.text(s);
  }

  void row(String label, String value) {
    info.kv(label, value);
  }

  void primary(String title, Runnable action) {
    model.button(title, true, action);
  }

  void secondary(String title, Runnable action) {
    model.button(title, false, action);
  }
}
