package com.deepseekharness.app.ui;

import android.content.Context;
import android.provider.Settings;
import androidx.fragment.app.FragmentTransaction;
import com.deepseekharness.app.R;

/**
 * 页面切换动画，全应用统一：
 * 进入子页面为 MIUI / miuix 的左右推入（新页从右滑入、旧页左移变暗，返回反之）；
 * 同级切换（底部标签、两种终端）只淡入淡出。独立 Activity 由主题里的同一套动画负责。
 * 只动画化页面容器，保持终端进程和业务状态；尊重系统关闭动画设置。
 */
final class UiMotion {
  private UiMotion() {}

  /** 进入下一级页面（会加入返回栈）。 */
  static FragmentTransaction page(Context context, FragmentTransaction transaction) {
    return animate(
        context,
        transaction,
        R.anim.hy_push_enter,
        R.anim.hy_push_exit,
        R.anim.hy_pop_enter,
        R.anim.hy_pop_exit);
  }

  /** 同级切换：底部标签、PTY 与简易终端互换。 */
  static FragmentTransaction tab(Context context, FragmentTransaction transaction) {
    return animate(
        context,
        transaction,
        R.anim.hy_fade_in,
        R.anim.hy_fade_out,
        R.anim.hy_fade_in,
        R.anim.hy_fade_out);
  }

  private static FragmentTransaction animate(
      Context context, FragmentTransaction transaction, int enter, int exit, int popEnter, int popExit) {
    transaction.setReorderingAllowed(true);
    try {
      if (Settings.Global.getFloat(
              context.getContentResolver(), Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
          > 0) transaction.setCustomAnimations(enter, exit, popEnter, popExit);
    } catch (RuntimeException ignored) {
      /* 读取设置失败仍可正常导航。 */
    }
    return transaction;
  }
}
