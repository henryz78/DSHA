package com.deepseekharness.app.ui.liquid

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 官方示例用的深色判断：本应用的主题可手动切换，所以按当前 miuix 底色判断，而不是跟随系统。 */
@Composable
internal fun isInDarkTheme(): Boolean = MiuixTheme.colorScheme.surface.luminance() < 0.5f
