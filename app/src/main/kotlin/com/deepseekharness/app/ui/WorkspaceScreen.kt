package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.deepseekharness.app.R
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.preference.ArrowPreference

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 备份与恢复页的显示状态；WorkspaceFragment 负责写入。 */
class WorkspaceUi {
  var backupHint by mutableStateOf("")
  var status by mutableStateOf("")
  var cancelVisible by mutableStateOf(false)
  var enabled by mutableStateOf(true)
}

interface WorkspaceActions {
  fun backup()

  fun cancel()

  fun files()

  fun guide()

  fun skills()

  fun storage()

  fun more()

  fun reset()

  fun history()
}

fun workspacePage(context: Context, ui: WorkspaceUi, actions: WorkspaceActions): ComposeView =
  composePage(context) {
    HyPage(title = t("备份与恢复", "Backup & restore"), back = true) {
      item { HyNote(t("导出、恢复与文件共享。", "Export, restore and file sharing.")) }
      item {
        HySection(t("备份", "Backup")) {
          ArrowPreference(
            title = t("一键备份", "One-click backup"),
            summary = ui.backupHint,
            startAction = { HyIcon(R.drawable.ic_ui_export) },
            enabled = ui.enabled,
            onClick = actions::backup,
          )
          ArrowPreference(
            title = t("恢复与更多备份选项", "Restore and more backup options"),
            summary = t("导入备份、只读救援导出、自动备份", "Import, rescue export, automatic backups"),
            startAction = { HyIcon(R.drawable.ic_recovery_refresh) },
            enabled = ui.enabled,
            onClick = actions::more,
          )
          if (ui.cancelVisible) HyButton(t("取消备份", "Cancel backup"), onClick = actions::cancel)
        }
      }
      item {
        HySection(t("文件", "Files")) {
          ArrowPreference(
            title = t("存储与文件", "Storage and files"),
            summary = t("空间占用、缓存清理和文件共享", "Usage, cache cleanup and file sharing"),
            startAction = { HyIcon(R.drawable.ic_ui2_box) },
            onClick = actions::storage,
          )
          ArrowPreference(
            title = t("作品与文件导出", "Work file export"),
            summary = t("浏览工作区 · 保存到手机 · 分享", "Browse workspace · Save · Share"),
            startAction = { HyIcon(R.drawable.ic_ui2_folder) },
            onClick = actions::files,
          )
          ArrowPreference(
            title = t("储存指南", "Storage guide"),
            summary = t("数据位置 · 导出备份 · 恢复与清理", "Data locations · Export · Restore and cleanup"),
            startAction = { HyIcon(R.drawable.ic_entry_book) },
            onClick = actions::guide,
          )
          ArrowPreference(
            title = t("技能（Skills）", "Skills"),
            summary = t("查看技能 · 导入 SKILL.md", "View skills · Import SKILL.md"),
            startAction = { HyIcon(R.drawable.ic_entry_book) },
            onClick = actions::skills,
          )
        }
      }
      item {
        HySection(t("备份记录", "History")) {
          ArrowPreference(
            title = t("备份与维护记录", "Backup and maintenance history"),
            summary = ui.status,
            onClick = actions::history,
          )
        }
      }
      item {
        HySection(t("维护", "Maintenance")) {
          ArrowPreference(
            title = t("重置配置，保留对话", "Reset settings, keep conversations"),
            summary = t("插件、对话和凭据保留；原配置可恢复", "Plugins, conversations and credentials are kept"),
            startAction = { HyIcon(R.drawable.ic_recovery_wrench) },
            enabled = ui.enabled,
            onClick = actions::reset,
          )
        }
      }
    }
  }
