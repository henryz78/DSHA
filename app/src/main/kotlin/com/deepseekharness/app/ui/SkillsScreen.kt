package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.deepseekharness.app.R
import com.deepseekharness.app.skills.SkillImports
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.preference.ArrowPreference

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 技能页的显示状态；SkillsActivity 负责写入。 */
class SkillsUi {
  var entries by mutableStateOf(listOf<SkillImports.Entry>())
  var unreadable by mutableIntStateOf(0)
  var loaded by mutableStateOf(false)
  var status by mutableStateOf("")
  var busy by mutableStateOf(false)
  var cancelEnabled by mutableStateOf(false)
}

interface SkillsActions {
  fun importSkill()

  fun refresh()

  fun cancel()

  fun open(entry: SkillImports.Entry)
}

fun skillsPage(context: Context, ui: SkillsUi, actions: SkillsActions): ComposeView =
  composePage(context) {
    HyPage(title = t("技能（Skills）", "Skills"), back = true) {
      item {
        HyNote(
          t(
            "从文件导入 SKILL.md，让 DSH 按需读取可复用的任务说明。导入仅复制 SKILL.md，正文中的命令不会自动执行；引用的脚本和资料需用文件入口另行放入同一技能目录。",
            "Import SKILL.md so DSH can load reusable task instructions. Import copies only SKILL.md and runs nothing; add referenced scripts and files to the same folder through Files.",
          )
        )
      }
      item {
        HySection {
          HyKv(t("技能目录", "Skills folder"), SkillImports.GUEST_ROOT)
          HyText(ui.status, selectable = true)
          HyButton(t("导入 SKILL.md", "Import SKILL.md"), primary = true, enabled = !ui.busy, onClick = actions::importSkill)
          HyButton(t("刷新技能", "Refresh skills"), enabled = !ui.busy, onClick = actions::refresh)
          if (ui.busy) HyButton(t("取消读取", "Cancel reading"), enabled = ui.cancelEnabled, onClick = actions::cancel)
        }
      }
      if (ui.loaded)
        item {
          HySection(
            if (ui.entries.isEmpty()) t("尚无可读取的用户全局技能", "No readable global skills yet")
            else UiText.format("可读取的用户全局技能：%s", ui.entries.size)
          ) {
            if (ui.unreadable > 0)
              HyText(
                UiText.format("另有 %s 个技能文件无法通过导入格式检查，原件已保留。", ui.unreadable)
              )
            ui.entries.forEach { entry ->
              ArrowPreference(
                title = entry.document.name,
                summary = entry.document.description,
                startAction = { HyIcon(R.drawable.ic_entry_book) },
                onClick = { actions.open(entry) },
              )
            }
            if (ui.entries.isEmpty() && ui.unreadable == 0)
              HyText(t("点上方“导入 SKILL.md”添加第一个技能。", "Tap Import SKILL.md above to add your first skill."))
          }
        }
    }
  }
