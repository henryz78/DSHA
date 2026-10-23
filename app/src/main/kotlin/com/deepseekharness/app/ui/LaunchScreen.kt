package com.deepseekharness.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.ui.compose.HydshTheme
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private fun t(zh: String, en: String): String = UiText.choose(zh, en)

/** 启动页显示状态；LaunchFragment（Java）负责写入，界面只读。 */
class LaunchState {
  var runState by mutableStateOf("")
  var subtitle by mutableStateOf("")
  var status by mutableStateOf("")
  var statusVisible by mutableStateOf(false)
  var busy by mutableStateOf(false)
  var port by mutableStateOf("—")
  var environment by mutableStateOf("—")
  var startLabel by mutableStateOf("")
  var startEnabled by mutableStateOf(true)
  var restartEnabled by mutableStateOf(true)
  var stopEnabled by mutableStateOf(true)
  var safeEnabled by mutableStateOf(true)
  var recoveryLabel by mutableStateOf("")
  var log by mutableStateOf("")
  var lan by mutableStateOf("")
  var lanVisible by mutableStateOf(false)
  var lanClickable by mutableStateOf(false)
}

/** 启动页的用户操作，由 LaunchFragment 实现。 */
interface LaunchActions {
  fun start()

  fun restart()

  fun stop()

  fun showAddress()

  fun showLan()

  fun recovery()

  fun safeStart()

  fun downloadLogs()
}

fun bindLaunchScreen(view: ComposeView, state: LaunchState, actions: LaunchActions) {
  view.setContent { HydshTheme { LaunchScreen(state, actions) } }
}

@Composable
private fun LaunchScreen(state: LaunchState, actions: LaunchActions) {
  Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().overScrollVertical(),
      contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = 24.dp),
    ) {
      item { StatusCard(state, actions) }
      item { InfoSection(state, actions) }
      item { TroubleSection(state, actions) }
      item { LogSection(state, actions) }
    }
  }
}

@Composable
private fun StatusCard(state: LaunchState, actions: LaunchActions) {
  val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
  Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = state.runState,
          modifier = Modifier.weight(1f),
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
        )
        if (state.busy) InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
      }
      Text(text = state.subtitle, modifier = Modifier.padding(top = 4.dp), color = summary, fontSize = 14.sp)
      if (state.statusVisible && state.status.isNotEmpty())
        Text(text = state.status, modifier = Modifier.padding(top = 8.dp), color = summary, fontSize = 13.sp)
      Spacer(Modifier.height(16.dp))
      TextButton(
        text = state.startLabel,
        onClick = actions::start,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.startEnabled,
        colors = ButtonDefaults.textButtonColorsPrimary(),
      )
      Spacer(Modifier.height(12.dp))
      Row {
        TextButton(
          text = t("重启", "Restart"),
          onClick = actions::restart,
          modifier = Modifier.weight(1f),
          enabled = state.restartEnabled,
        )
        Spacer(Modifier.width(12.dp))
        TextButton(
          text = t("停止", "Stop"),
          onClick = actions::stop,
          modifier = Modifier.weight(1f),
          enabled = state.stopEnabled,
        )
      }
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  SmallTitle(text = title)
  Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { content() }
}

@Composable
private fun Value(text: String) {
  Text(text = text, color = MiuixTheme.colorScheme.onSurfaceVariantActions, fontSize = 15.sp)
}

@Composable
private fun InfoSection(state: LaunchState, actions: LaunchActions) {
  Section(t("运行信息", "Status")) {
    BasicComponent(title = t("Web 端口", "Web port"), endActions = { Value(state.port) })
    BasicComponent(title = t("运行环境", "Environment"), endActions = { Value(state.environment) })
    ArrowPreference(
      title = t("访问地址", "Access address"),
      summary = t("查看并复制本机地址", "View and copy the local address"),
      onClick = actions::showAddress,
    )
    if (state.lanVisible)
      BasicComponent(
        title = t("局域网", "LAN"),
        summary = state.lan,
        onClick = if (state.lanClickable) actions::showLan else null,
      )
  }
}

@Composable
private fun TroubleSection(state: LaunchState, actions: LaunchActions) {
  Section(t("遇到问题", "Troubleshooting")) {
    ArrowPreference(
      title = state.recoveryLabel,
      summary = t("启动失败或插件出错时使用", "Use when startup or a plugin fails"),
      onClick = actions::recovery,
    )
    ArrowPreference(
      title = t("安全启动", "Safe start"),
      summary = t("只加载官方基础界面，不加载插件", "Load only the official base UI, no plugins"),
      onClick = actions::safeStart,
      enabled = state.safeEnabled,
    )
  }
}

@Composable
private fun LogSection(state: LaunchState, actions: LaunchActions) {
  val scroll = rememberScrollState()
  // 用户停在底部时跟随新日志；往上翻看时不打扰。
  var follow by remember { mutableStateOf(true) }
  LaunchedEffect(scroll.isScrollInProgress) {
    if (!scroll.isScrollInProgress) follow = scroll.value >= scroll.maxValue - 48
  }
  LaunchedEffect(state.log) {
    if (follow) {
      withFrameNanos {}
      scroll.scrollTo(scroll.maxValue)
    }
  }
  Section(t("启动日志", "Startup log")) {
    Box(
      modifier =
        Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(scroll).padding(16.dp)
    ) {
      SelectionContainer {
        Text(
          text = state.log,
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
      }
    }
    ArrowPreference(title = t("下载日志", "Download logs"), onClick = actions::downloadLogs)
  }
}
