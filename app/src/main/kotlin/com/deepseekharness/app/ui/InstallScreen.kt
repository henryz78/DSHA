package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.R
import com.deepseekharness.app.util.InstallTask
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 环境状态与修复页的显示状态；InstallFragment 负责写入。步骤状态：0 未检查 1 进行中 2 成功 3 失败 4 未完成。 */
class InstallUi {
  var environment by mutableStateOf("")
  var base by mutableStateOf("—")
  var runtime by mutableStateOf("—")
  var appVersion by mutableStateOf("")
  var status by mutableStateOf("")
  var progress by mutableStateOf("")
  var progressValue by mutableStateOf(-1f)
  val steps = mutableStateListOf(0, 0, 0, 0, 0, 0)
  var checkEnabled by mutableStateOf(true)
  var maintenanceText by mutableStateOf("")
  var maintenanceEnabled by mutableStateOf(true)
  var cancelVisible by mutableStateOf(false)
  var cancelEnabled by mutableStateOf(true)
  var cancelText by mutableStateOf("")
  var error by mutableStateOf("")
  var log by mutableStateOf("")

  fun setStep(index: Int, value: Int) {
    if (steps[index] != value) steps[index] = value
  }
}

interface InstallActions {
  fun speedTest()

  fun check()

  fun repair()

  fun cancel()

  fun copyLog()

  fun maintenance()

  fun step(step: Int)

  fun managedUpdate()

  fun recovery()

  fun tasks()
}

private val STEP_LABELS =
  listOf(t("未检查", "Not checked"), t("进行中", "Running"), t("成功", "OK"), t("失败", "Failed"), t("未完成", "Incomplete"))

fun installPage(context: Context, ui: InstallUi, actions: InstallActions): ComposeView =
  composePage(context) {
    HyPage(title = t("环境状态与修复", "Status & repair"), back = true) {
      item { HyNote(t("按组件检查状态，修复需要处理的部分。", "Check each component and repair what needs attention.")) }
      item {
        HySection {
          HyKv(t("环境状态", "Environment"), ui.environment)
          HyKv(t("Ubuntu 基础环境", "Ubuntu base"), ui.base)
          HyKv(t("受管运行时", "Managed runtime"), ui.runtime)
          HyKv(t("应用版本", "App version"), ui.appVersion)
        }
      }
      item {
        HySection(t("检查与修复", "Check and repair")) {
          ArrowPreference(title = ui.status, summary = ui.progress.ifEmpty { null }, onClick = actions::tasks)
          if (ui.progressValue >= 0f)
            LinearProgressIndicator(
              progress = ui.progressValue,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
          HyText(ui.error, color = colorResource(R.color.err), selectable = true)
          HyButton(t("检查全部", "Check all"), primary = true, enabled = ui.checkEnabled, onClick = actions::check)
          if (ui.cancelVisible) HyButton(ui.cancelText, enabled = ui.cancelEnabled, onClick = actions::cancel)
        }
      }
      item {
        HySection(t("六个安装步骤", "Six installation steps")) {
          InstallTask.NAMES.forEachIndexed { index, name ->
            val state = ui.steps[index]
            ArrowPreference(
              title = UiText.text(name),
              summary = t("查看检查结果与修复选项", "View results and repair options"),
              startAction = { HyIcon(R.drawable.ic_ui2_box) },
              endActions = {
                Text(
                  text = STEP_LABELS[state],
                  color =
                    when (state) {
                      2 -> colorResource(R.color.ok)
                      3 -> colorResource(R.color.err)
                      1 -> MiuixTheme.colorScheme.primary
                      else -> MiuixTheme.colorScheme.onSurfaceVariantActions
                    },
                  fontSize = 14.sp,
                )
              },
              onClick = { actions.step(index + 1) },
            )
          }
        }
      }
      item {
        HySection(t("维护与恢复", "Maintenance and recovery")) {
          ArrowPreference(
            title = t("更新受管运行时", "Update managed runtime"),
            summary = t("只更新随包组件，保留个人数据", "Update bundled components only; keep personal data"),
            startAction = { HyIcon(R.drawable.ic_recovery_refresh) },
            onClick = actions::managedUpdate,
          )
          ArrowPreference(
            title = t("按需修复组件", "Repair components"),
            summary = t("检查缺失部分，保留可用组件", "Check missing parts; keep working components"),
            startAction = { HyIcon(R.drawable.ic_recovery_wrench) },
            enabled = ui.checkEnabled,
            onClick = actions::repair,
          )
          ArrowPreference(
            title = t("环境修复与恢复", "Environment recovery"),
            summary = t("查看启动依赖与中断维护", "Startup dependencies and interrupted maintenance"),
            startAction = { HyIcon(R.drawable.ic_ui_shield) },
            onClick = actions::recovery,
          )
          ArrowPreference(
            title = t("首次安装测速", "First-install timing"),
            summary = t("在独立目录完整安装一次并计时", "Time a full install in an isolated folder"),
            startAction = { HyIcon(R.drawable.ic_recovery_clock) },
            onClick = actions::speedTest,
          )
          HyButton(ui.maintenanceText, enabled = ui.maintenanceEnabled, onClick = actions::maintenance)
        }
      }
      item {
        HySection(t("检查与安装日志", "Check and install log")) {
          val scroll = rememberScrollState()
          LaunchedEffect(ui.log) { scroll.scrollTo(scroll.maxValue) }
          SelectionContainer {
            Text(
              text = ui.log.ifEmpty { t("执行后将在此逐行显示脱敏输出。", "Redacted output appears here line by line.") },
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(max = 260.dp)
                  .verticalScroll(scroll)
                  .padding(16.dp),
              color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
            )
          }
          if (ui.log.isNotEmpty()) HyButton(t("复制日志", "Copy log"), onClick = actions::copyLog)
        }
      }
    }
  }
