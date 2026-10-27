package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.deepseekharness.app.R
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 备份与恢复页的显示状态；WorkspaceFragment 负责写入。 */
class WorkspaceUi {
  var lastBackup by mutableStateOf("")
  var automatic by mutableStateOf("")
  var includeKey by mutableStateOf(false)
  var status by mutableStateOf("")
  var cancelVisible by mutableStateOf(false)
  var enabled by mutableStateOf(true)
}

interface WorkspaceActions {
  fun backup()

  fun cancel()

  fun restore()

  fun automatic()

  fun includeKey(on: Boolean)

  fun advanced()

  fun history()

  fun guide()
}

/** 备份与恢复：常用的就三件事——立即备份、从备份恢复、自动备份；其余收在“更多”里。 */
fun workspacePage(context: Context, ui: WorkspaceUi, actions: WorkspaceActions): ComposeView =
  composePage(context) {
    HyPage(title = t("备份与恢复", "Backup & restore"), back = true) {
      item {
        HySection {
          ArrowPreference(
            title = t("立即备份", "Back up now"),
            summary = ui.lastBackup,
            startAction = { HyIcon(R.drawable.ic_ui_export) },
            enabled = ui.enabled,
            onClick = actions::backup,
          )
          ArrowPreference(
            title = t("从备份恢复", "Restore from backup"),
            summary = t("选择备份文件，对话、设置和插件一起恢复", "Pick a backup file; conversations, settings and plugins come back together"),
            startAction = { HyIcon(R.drawable.ic_recovery_refresh) },
            enabled = ui.enabled,
            onClick = actions::restore,
          )
          ArrowPreference(
            title = t("自动备份", "Automatic backup"),
            summary = ui.automatic,
            startAction = { HyIcon(R.drawable.ic_recovery_clock) },
            onClick = actions::automatic,
          )
          if (ui.cancelVisible) HyButton(t("取消备份", "Cancel backup"), onClick = actions::cancel)
        }
      }
      if (ui.status.isNotEmpty()) item { HyNote(ui.status) }
      item {
        HySection {
          SwitchPreference(
            title = t("备份时带上 API Key", "Include the API key"),
            summary =
              t(
                "恢复后不用重填；密钥会以明文写进备份文件，请把备份放在可信的地方",
                "No need to re-enter it after restoring; the key is stored in plain text, so keep backups somewhere safe",
              ),
            checked = ui.includeKey,
            onCheckedChange = {
              ui.includeKey = it
              actions.includeKey(it)
            },
          )
        }
      }
      item {
        HySection(t("更多", "More")) {
          ArrowPreference(
            title = t("高级备份与救援", "Advanced backup and rescue"),
            summary = t("自选范围导出、只读救援、保留的副本", "Custom export, read-only rescue, retained copies"),
            onClick = actions::advanced,
          )
          ArrowPreference(title = t("备份记录", "Backup history"), onClick = actions::history)
          ArrowPreference(title = t("数据存放说明", "Where data is stored"), onClick = actions::guide)
        }
      }
    }
  }
