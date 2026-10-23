package com.deepseekharness.app.ui.compose

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * HYDSH 新界面的统一主题：跟随系统深浅色。
 *
 * 旧版 AppCompatActivity 不会把返回分发器挂到视图树上，miuix 的下拉框和对话框找不到它会直接崩溃，
 * 所以这里显式把宿主 Activity 提供出去。
 */
@Composable
fun HydshTheme(content: @Composable () -> Unit) {
  val activity = LocalContext.current.findActivity()
  MiuixTheme(controller = ThemeController(ColorSchemeMode.System)) {
    if (activity == null) content()
    else CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides activity, content = content)
  }
}

private tailrec fun Context.findActivity(): ComponentActivity? =
  when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
