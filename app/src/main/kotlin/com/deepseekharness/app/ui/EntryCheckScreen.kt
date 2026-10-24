package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.R
import com.deepseekharness.app.util.UiText
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 进入主界面前检查运行环境时的等待页。和启动页同一个骨架（左上大标题 HYDSH），检查完换成主界面时标题不动。
 * 检查通常很快：转圈 0.3 秒后才淡入，免得一闪而过；超过 3 秒仍没结束才给出“查看诊断”。
 */
fun entryCheckPage(context: Context, diagnostics: Runnable): ComposeView =
  composePage(context) { EntryCheckScreen(diagnostics) }.apply {
    // 挡住下面还没准备好的主界面，点击不能穿透。
    isClickable = true
  }

@Composable
private fun EntryCheckScreen(diagnostics: Runnable) {
  var loading by remember { mutableStateOf(false) }
  var slow by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(300)
    loading = true
    delay(2700)
    slow = true
  }
  HyPage(title = stringResource(R.string.app_name), tasks = true) {
    item {
      Column(
        Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        AnimatedVisibility(loading, enter = fadeIn(), exit = fadeOut()) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
            Text(
              UiText.choose("正在检查运行环境…", "Checking the runtime…"),
              Modifier.padding(top = 16.dp),
              color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
              fontSize = 14.sp,
            )
          }
        }
        AnimatedVisibility(slow, enter = fadeIn(), exit = fadeOut()) {
          TextButton(
            text = UiText.choose("查看诊断", "View diagnostics"),
            onClick = diagnostics::run,
            modifier = Modifier.padding(top = 24.dp),
          )
        }
      }
    }
  }
}
