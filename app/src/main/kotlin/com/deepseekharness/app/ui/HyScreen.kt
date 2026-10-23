package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.deepseekharness.app.R
import java.util.function.Consumer
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/*
 * 给 Java 页面用的声明式 miuix 页面：先按顺序声明说明、分组和各行，之后只改每行的 HyLine 状态。
 * 适合结构固定、内容随状态变化的页面（后台任务、自动备份、应急 DSH 等）。
 */

/** 一行的可变状态：文字、说明、可用、可见、选中。 */
class HyLine {
  var text by mutableStateOf("")
  var summary by mutableStateOf("")
  var enabled by mutableStateOf(true)
  var visible by mutableStateOf(true)
  var checked by mutableStateOf(false)
  var primary by mutableStateOf(false)
  /** 0 普通 1 强调色 2 错误色 */
  var tone by mutableStateOf(0)
}

sealed class HyRow(val line: HyLine) {
  class Text(line: HyLine, val selectable: Boolean) : HyRow(line)
  class Kv(line: HyLine) : HyRow(line)
  class Arrow(line: HyLine, val icon: Int, val action: Runnable) : HyRow(line)
  class Button(line: HyLine, val action: Runnable) : HyRow(line)
  class Toggle(line: HyLine, val change: Consumer<Boolean>) : HyRow(line)
  class Check(line: HyLine, val change: Consumer<Boolean>) : HyRow(line)
  class Radio(line: HyLine, val action: Runnable) : HyRow(line)
  /** 进度条：line.summary 为空时是不确定进度，否则为 0–100 的数字。 */
  class Progress(line: HyLine) : HyRow(line)
  class Input(line: HyLine, val password: Boolean, val change: Consumer<String>?) : HyRow(line)
}

class HyGroup(title: String) {
  val title = HyLine().also { it.text = title }
  val rows = mutableStateListOf<HyRow>()
  /** 卡片外的说明文字，不画卡片。 */
  var bare = false

  private fun add(row: HyRow): HyLine {
    rows.add(row)
    return row.line
  }

  fun text(text: String): HyLine = add(HyRow.Text(HyLine().also { it.text = text }, false))

  fun selectable(text: String): HyLine = add(HyRow.Text(HyLine().also { it.text = text }, true))

  fun kv(label: String, value: String): HyLine =
    add(HyRow.Kv(HyLine().also { it.text = label; it.summary = value }))

  fun arrow(title: String, summary: String, action: Runnable): HyLine = arrow(title, summary, 0, action)

  fun arrow(title: String, summary: String, icon: Int, action: Runnable): HyLine =
    add(HyRow.Arrow(HyLine().also { it.text = title; it.summary = summary }, icon, action))

  fun button(text: String, primary: Boolean, action: Runnable): HyLine =
    add(HyRow.Button(HyLine().also { it.text = text; it.primary = primary }, action))

  fun toggle(title: String, summary: String, checked: Boolean, change: Consumer<Boolean>): HyLine =
    add(HyRow.Toggle(HyLine().also { it.text = title; it.summary = summary; it.checked = checked }, change))

  fun check(title: String, checked: Boolean, change: Consumer<Boolean>): HyLine =
    add(HyRow.Check(HyLine().also { it.text = title; it.checked = checked }, change))

  fun radio(title: String, checked: Boolean, action: Runnable): HyLine =
    add(HyRow.Radio(HyLine().also { it.text = title; it.checked = checked }, action))

  /** 进度条行；默认不确定进度。 */
  fun progress(): HyLine = add(HyRow.Progress(HyLine()))

  /** 输入框：label 为提示文字，输入内容在 line.text。 */
  fun input(label: String, password: Boolean): HyLine = input(label, password, null)

  /** change 在每次输入变化时回调（例如同步到业务模型）。 */
  fun input(label: String, password: Boolean, change: Consumer<String>?): HyLine =
    add(HyRow.Input(HyLine().also { it.summary = label }, password, change))

  /** 清空本组的行（列表类内容刷新时用）。 */
  fun clear() = rows.clear()
}

class HyScreenModel(val title: String) {
  val groups = mutableStateListOf<HyGroup>()

  /** 卡片外的说明。 */
  fun note(text: String): HyLine {
    val group = HyGroup("").also { it.bare = true }
    groups.add(group)
    return group.text(text)
  }

  /** 新的分组卡片；title 为空时不显示小标题。 */
  fun section(title: String): HyGroup = HyGroup(title).also { groups.add(it) }

  /** 卡片外、与卡片同宽的按钮。 */
  fun button(text: String, primary: Boolean, action: Runnable): HyLine {
    val group = HyGroup("").also { it.bare = true }
    groups.add(group)
    return group.button(text, primary, action)
  }
}

fun hyScreen(context: Context, model: HyScreenModel, back: Boolean): ComposeView =
  composePage(context) {
    HyPage(title = model.title, back = back) {
      model.groups.forEach { group ->
        item {
          if (group.bare) group.rows.forEach { Row(it, inCard = false) }
          else if (group.rows.any { it.line.visible })
            HySection(group.title.text) { group.rows.forEach { Row(it, inCard = true) } }
        }
      }
    }
  }

@Composable
private fun Row(row: HyRow, inCard: Boolean) {
  val line = row.line
  if (!line.visible) return
  val color =
    when (line.tone) {
      1 -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary
      2 -> colorResource(R.color.err)
      else -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
  when (row) {
    is HyRow.Text -> if (inCard) HyText(line.text, color, row.selectable) else HyNote(line.text, color)
    is HyRow.Kv -> HyKv(line.text, line.summary)
    is HyRow.Arrow ->
      ArrowPreference(
        title = line.text,
        summary = line.summary.ifEmpty { null },
        startAction = if (row.icon != 0) ({ HyIcon(row.icon) }) else null,
        enabled = line.enabled,
        onClick = { row.action.run() },
      )
    is HyRow.Button -> HyButton(line.text, primary = line.primary, enabled = line.enabled, inCard = inCard) { row.action.run() }
    is HyRow.Toggle ->
      SwitchPreference(
        title = line.text,
        summary = line.summary.ifEmpty { null },
        checked = line.checked,
        enabled = line.enabled,
        onCheckedChange = { row.change.accept(it) },
      )
    is HyRow.Check ->
      CheckboxPreference(
        title = line.text,
        checked = line.checked,
        enabled = line.enabled,
        onCheckedChange = { row.change.accept(it) },
      )
    is HyRow.Progress ->
      top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
        progress = line.summary.toFloatOrNull()?.div(100f),
        modifier =
          androidx.compose.ui.Modifier.fillMaxWidth()
            .padding(horizontal = if (inCard) 16.dp else 12.dp, vertical = 10.dp),
      )
    is HyRow.Input ->
      top.yukonga.miuix.kmp.basic.TextField(
        value = line.text,
        onValueChange = {
          line.text = it
          row.change?.accept(it)
        },
        enabled = line.enabled,
        modifier =
          androidx.compose.ui.Modifier.fillMaxWidth()
            .padding(horizontal = if (inCard) 16.dp else 12.dp, vertical = 8.dp),
        label = line.summary,
        singleLine = true,
        visualTransformation =
          if (row.password) androidx.compose.ui.text.input.PasswordVisualTransformation()
          else androidx.compose.ui.text.input.VisualTransformation.None,
      )
    is HyRow.Radio ->
      RadioButtonPreference(title = line.text, selected = line.checked, enabled = line.enabled, onClick = { row.action.run() })
  }
}
