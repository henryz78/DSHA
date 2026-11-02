package com.deepseekharness.app.core;

import android.content.Context;

/**
 * 「允许 AI 访问手机」总开关，默认关闭。关闭时 Linux 看不到手机存储，设备桥拒绝一切碰手机的接口；
 * Linux 里的程序只能在 Linux 里活动。单独存放、不进备份，不能由恢复的数据替用户打开。
 */
public final class PhoneAccess {
  private static final String PREFS = "phone_access", KEY = "allowed";

  private PhoneAccess() {}

  public static boolean allowed(Context context) {
    return context != null
        && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
  }

  public static void set(Context context, boolean allowed) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, allowed).commit();
  }
}
