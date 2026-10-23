package com.deepseekharness.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.ui.compose.HydshTheme
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

/** PTY 与简易终端共用的顶部状态；Java 片段负责写入。title 为空时不显示提示行。 */
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

/** 扩展键：前 8 个一行、其余一行，每行等宽铺满。onKey 收到按键下标。 */
fun bindTerminalKeys(view: ComposeView, header: TerminalHeader, labels: List<String>, onKey: IntConsumer) {
  view.setContent {
    HydshTheme {
      Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.withIndex().chunked(8).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { (index, label) ->
              val active = label == "CTRL" && header.ctrl || label == "ALT" && header.alt
              Key(label, active, Modifier.weight(1f)) { onKey.accept(index) }
            }
            // 末行不足 8 个时补空位，保证两行按键同宽对齐。
            repeat(8 - row.size) { Box(Modifier.weight(1f)) }
          }
        }
      }
    }
  }
}

@Composable
private fun Key(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val colors = MiuixTheme.colorScheme
  Box(
    modifier =
      modifier
        .height(38.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (active) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainer)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (active) colors.primary else colors.onSurfaceVariantSummary,
      fontSize = 12.sp,
      fontFamily = FontFamily.Monospace,
      maxLines = 1,
      textAlign = TextAlign.Center,
    )
  }
}

/** 一行：左边终端标签（可横向滑动）和「＋」，右边字号与切换按钮；有提示时下方多一行小字。 */
@Composable
private fun Header(header: TerminalHeader, tools: List<TerminalTool>, actions: TerminalChromeActions) {
  val colors = MiuixTheme.colorScheme
  Column(modifier = Modifier.padding(bottom = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        header.tabs.forEach { tab -> TabPill(tab, actions) }
        Box(
          modifier =
            Modifier.size(34.dp)
              .clip(RoundedCornerShape(50))
              .background(colors.surfaceContainer)
              .clickable(enabled = header.newEnabled, onClick = actions::newTerminal)
              .alpha(if (header.newEnabled) 1f else 0.4f),
          contentAlignment = Alignment.Center,
        ) {
          Text(text = "＋", fontSize = 16.sp, color = colors.onSurfaceVariantSummary)
        }
      }
      tools.forEach { tool ->
        Text(
          text = tool.label,
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .clickable(enabled = tool.enabled) { tool.action.run() }
              .alpha(if (tool.enabled) 1f else 0.4f)
              .padding(horizontal = 8.dp, vertical = 8.dp),
          color = colors.primary,
          fontSize = 14.sp,
        )
      }
    }
    if (header.title.isNotBlank()) {
      Text(
        text = header.title,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        color = colors.onSurfaceVariantSummary,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
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
          .heightIn(min = 34.dp)
          .padding(start = 12.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
      color = if (tab.selected) colors.primary else colors.onSurfaceVariantSummary,
      fontSize = 13.sp,
    )
    Box(
      modifier =
        Modifier.size(34.dp)
          .clip(RoundedCornerShape(50))
          .clickable(enabled = !tab.closing) { actions.close(tab.id) }
          .alpha(if (tab.closing) 0.4f else 1f),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = "×", fontSize = 16.sp, color = colors.onSurfaceVariantSummary)
    }
  }
}
