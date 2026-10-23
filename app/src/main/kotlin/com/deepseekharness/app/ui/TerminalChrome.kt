package com.deepseekharness.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.ui.compose.HydshTheme
import com.deepseekharness.app.util.UiText
import java.util.function.IntConsumer
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 终端标签。 */
class TerminalTabItem(
  @JvmField val id: Long,
  @JvmField val label: String,
  @JvmField val selected: Boolean,
  @JvmField val closing: Boolean,
)

/** 终端标题栏右侧的小按钮，文字和可用状态都可单独更新。 */
class TerminalTool(label: String, @JvmField val action: Runnable) {
  var label by mutableStateOf(label)
  var enabled by mutableStateOf(true)
}

/** PTY 与简易终端共用的顶部状态；Java 片段负责写入。 */
class TerminalHeader {
  var title by mutableStateOf("")
  var tabs by mutableStateOf(listOf<TerminalTabItem>())
  var newEnabled by mutableStateOf(true)
  var ctrl by mutableStateOf(false)
  var alt by mutableStateOf(false)
}

interface TerminalChromeActions {
  fun select(id: Long)

  fun close(id: Long)

  fun newTerminal()
}

fun bindTerminalHeader(
  view: ComposeView,
  header: TerminalHeader,
  tools: List<TerminalTool>,
  actions: TerminalChromeActions,
) {
  view.setContent { HydshTheme { Header(header, tools, actions) } }
}

/** 扩展键行；onKey 收到按键下标。CTRL/ALT 亮起状态来自 header。 */
fun bindTerminalKeys(view: ComposeView, header: TerminalHeader, labels: List<String>, onKey: IntConsumer) {
  view.setContent {
    HydshTheme {
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        labels.forEachIndexed { index, label ->
          val active = label == "CTRL" && header.ctrl || label == "ALT" && header.alt
          Pill(
            text = label,
            selected = active,
            monospace = true,
            onClick = { onKey.accept(index) },
          )
        }
      }
    }
  }
}

@Composable
private fun Header(header: TerminalHeader, tools: List<TerminalTool>, actions: TerminalChromeActions) {
  Column(modifier = Modifier.padding(bottom = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = header.title,
        modifier = Modifier.weight(1f).padding(end = 8.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      tools.forEach { tool ->
        Text(
          text = tool.label,
          modifier =
            Modifier.clip(RoundedCornerShape(12.dp))
              .clickable(enabled = tool.enabled) { tool.action.run() }
              .alpha(if (tool.enabled) 1f else 0.4f)
              .heightIn(min = 40.dp)
              .padding(horizontal = 12.dp, vertical = 10.dp),
          color = MiuixTheme.colorScheme.primary,
          fontSize = 14.sp,
        )
      }
    }
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      header.tabs.forEach { tab -> TabPill(tab, actions) }
      Pill(
        text = UiText.choose("＋ 新建", "+ New"),
        selected = false,
        enabled = header.newEnabled,
        onClick = actions::newTerminal,
      )
    }
  }
}

@Composable
private fun TabPill(tab: TerminalTabItem, actions: TerminalChromeActions) {
  val colors = MiuixTheme.colorScheme
  Row(
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .background(if (tab.selected) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainer),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = tab.label,
      modifier =
        Modifier.clickable { actions.select(tab.id) }
          .heightIn(min = 40.dp)
          .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
      color = if (tab.selected) colors.primary else colors.onSurfaceVariantSummary,
      fontSize = 13.sp,
    )
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(RoundedCornerShape(50))
          .clickable(enabled = !tab.closing) { actions.close(tab.id) }
          .alpha(if (tab.closing) 0.4f else 1f),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = "×", fontSize = 18.sp, color = colors.onSurfaceVariantSummary)
    }
  }
}

@Composable
private fun Pill(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  enabled: Boolean = true,
  monospace: Boolean = false,
) {
  val colors = MiuixTheme.colorScheme
  Text(
    text = text,
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .background(if (selected) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainer)
        .clickable(enabled = enabled, onClick = onClick)
        .alpha(if (enabled) 1f else 0.4f)
        .heightIn(min = 40.dp)
        .widthIn(min = 44.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp),
    color = if (selected) colors.primary else colors.onSurfaceVariantSummary,
    fontSize = 13.sp,
    fontFamily = if (monospace) FontFamily.Monospace else null,
  )
}
