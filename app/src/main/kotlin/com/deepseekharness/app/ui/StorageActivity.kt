package com.deepseekharness.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepseekharness.app.R
import com.deepseekharness.app.backup.NativeBackupJobs
import com.deepseekharness.app.backup.StorageMaintenance
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.util.Fmt
import com.deepseekharness.app.util.MaintenanceErrorText
import com.deepseekharness.app.util.StorageCategoryLabels
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.preference.ArrowPreference

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 存储与文件：统计各类占用，可清理可再生缓存和多余运行时副本。 */
class StorageActivity : AppCompatActivity() {
  private var rows by mutableStateOf(listOf<Pair<String, String>>())
  private var status by mutableStateOf("")
  private var busy by mutableStateOf(false)

  override fun onCreate(saved: Bundle?) {
    super.onCreate(saved)
    val workdir = HarnessController.get(this).config().workdir
    setContentView(
      composePage(this) {
        HyPage(title = t("存储与文件", "Storage and files"), back = true) {
          item {
            HyNote(
              t(
                "完整 Linux 环境约占 1 GB。历史环境和回退副本另行保留；它们不等于聊天数据。以下为文件大小估算。",
                "The full Linux environment uses about 1 GB. Older environments and rollback copies are retained separately from chats. Sizes below are estimates.",
              )
            )
          }
          item {
            HySection {
              HyKv(t("工作目录", "Workspace"), workdir)
              ArrowPreference(
                title = t("文件共享", "File sharing"),
                summary = t("通过系统文件选择器或 MT 管理器访问 HYDSH", "Access HYDSH from the system file picker or MT Manager"),
                startAction = { HyIcon(R.drawable.ic_ui2_box) },
                onClick = {
                  CardSheet.show(
                    this@StorageActivity,
                    t("文件共享", "File sharing"),
                    t(
                      "在文件管理器中添加本地存储，选择 DocumentsProvider → HYDSH。找不到时，先打开本 App 后重试。",
                      "Add local storage in your file manager and select DocumentsProvider → HYDSH. Open this app first if it is missing.",
                    ),
                  )
                },
              )
            }
          }
          if (rows.isNotEmpty()) item { HySection(t("空间占用", "Usage")) { rows.forEach { (k, v) -> HyKv(k, v) } } }
          item { HyNote(status) }
          item {
            HyButton(t("清理可再生缓存与多余运行时副本", "Clean reproducible caches and excess runtime copies"), primary = true, enabled = !busy, inCard = false) {
              DshaDialogBuilder(this@StorageActivity, true)
                .setTitle(t("清理存储空间", "Clean storage"))
                .setMessage(
                  t(
                    "清理前会安全停止 DSH 和终端，再清理历史验收缓存，以及超过保留数量且已核验的受管回退副本。对话、附件、设置、插件、手动备份和无法核验的原件保留。",
                    "DSH and terminals are stopped safely before cleanup. Removes old verification caches and verified excess managed rollback copies. Keeps chats, attachments, settings, plugins, manual backups and unverified originals.",
                  )
                )
                .setPositiveButton(t("开始清理", "Clean")) { _, _ -> load(true) }
                .setNegativeButton(t("取消", "Cancel"), null)
                .show()
            }
          }
          item { HyButton(t("重新统计", "Refresh sizes"), enabled = !busy, inCard = false) { load(false) } }
        }
      }
    )
    load(false)
  }

  private fun load(remove: Boolean) {
    if (busy) return
    busy = true
    status = t("正在处理…", "Working…")
    Thread(
        {
          try {
            val reclaimed = if (remove) StorageMaintenance.clean(this) else 0L
            val sizes = StorageMaintenance.inspect(this)
            runOnUiThread {
              if (isFinishing || isDestroyed) return@runOnUiThread
              rows =
                sizes.entries.map { (key, value) ->
                  StorageCategoryLabels.label(key, "en" == UiText.language()) to
                    (Fmt.bytes(value.bytes) + if (value.unreadable > 0) t(" + 部分不可读", " + unreadable entries") else "")
                }
              status =
                if (remove) UiText.format("已释放 %s", Fmt.bytes(reclaimed))
                else
                  t(
                    "已完成统计。清理只移除可再生缓存和已核验的多余运行时副本，个人数据与备份保留。",
                    "Sizes updated. Cleanup removes only reproducible caches and verified excess runtime copies. Personal data and backups are retained.",
                  )
              busy = false
            }
          } catch (error: Exception) {
            runOnUiThread {
              if (isFinishing || isDestroyed) return@runOnUiThread
              status = MaintenanceErrorText.render(NativeBackupJobs.code(error))
              busy = false
            }
          }
        },
        "storage-maintenance",
      )
      .start()
  }
}
