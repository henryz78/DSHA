package com.deepseekharness.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.deepseekharness.app.R
import com.deepseekharness.app.bridge.SensitiveClipboard
import com.deepseekharness.app.core.DiagnosticRepository
import com.deepseekharness.app.core.ErrorLogRepository
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiStateText
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 诊断与日志：环境检查、启动记录、错误日志导出、反馈报告。 */
class DiagnosticActivity : AppCompatActivity() {
  private lateinit var repository: DiagnosticRepository
  private lateinit var logs: ErrorLogRepository
  private var steps by mutableStateOf("")
  private var results by mutableStateOf(listOf<DiagnosticRepository.Result>())
  private var phase by mutableStateOf<DiagnosticRepository.Phase?>(null)
  private var busy by mutableStateOf(false)
  private var logState by mutableStateOf<ErrorLogRepository.State?>(null)
  private var pendingInclude = false
  private var pendingNote = ""
  private var pendingLogExport = false

  private val logExporter =
    registerForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
      val selected = pendingLogExport
      pendingLogExport = false
      if (uri != null && selected) logs.export(uri, pendingInclude, pendingNote)
    }

  private val exporter =
    registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
      if (uri == null) return@registerForActivityResult
      val report = completeReport()
      Thread(
          {
            val message =
              try {
                contentResolver.openOutputStream(uri, "wt").use { stream ->
                  if (stream == null) throw java.io.IOException(UiText.text("无法写入所选位置"))
                  stream.write(report.toByteArray(Charsets.UTF_8))
                }
                UiText.text("诊断报告已导出")
              } catch (error: Exception) {
                UiText.format("导出失败：%s", error.javaClass.simpleName)
              }
            runOnUiThread { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
          },
          "diagnostic-export",
        )
        .start()
    }

  override fun onCreate(saved: Bundle?) {
    super.onCreate(saved)
    repository = ViewModelProvider(this)[DiagnosticRepository::class.java]
    logs = ViewModelProvider(this)[ErrorLogRepository::class.java]
    repository.results.observe(this) { results = it ?: emptyList() }
    repository.phase.observe(this) { phase = it }
    repository.busy.observe(this) { busy = it == true }
    logs.state.observe(this) { logState = it }
    setContentView(
      composePage(this) {
        val (headline, status) =
          when (phase) {
            DiagnosticRepository.Phase.RUNNING -> t("正在检查", "Checking") to UiText.text("正在检查环境…")
            DiagnosticRepository.Phase.FAILED ->
              t("检查未完成", "Check incomplete") to
                t("请打开失败卡片查看原因，也可复制或导出脱敏报告。", "Open the failure card for details, or copy or export the redacted report.")
            DiagnosticRepository.Phase.SUCCEEDED -> t("检查已完成", "Checks completed") to UiText.text("报告保留在本机，复制或导出后可用于反馈")
            else -> t("准备检查", "Ready to check") to UiText.text("报告保留在本机，复制或导出后可用于反馈")
          }
        HyPage(title = t("诊断与日志", "Diagnostics & logs"), back = true) {
          item { HyNote(t("检查组件、存储与服务，生成脱敏报告。", "Check components, storage and services, then build a redacted report.")) }
          item {
            HySection {
              top.yukonga.miuix.kmp.basic.BasicComponent(title = headline, summary = status)
              HyButton(t("重新诊断", "Run diagnostics"), primary = true, enabled = !busy) { repository.generate() }
            }
          }
          if (results.isNotEmpty())
            item {
              HySection(t("检查结果", "Results")) {
                results.forEach { item ->
                  ArrowPreference(
                    title = item.title,
                    summary = item.status,
                    startAction = { HyIcon(R.drawable.ic_ui2_box) },
                    onClick = { CardSheet.show(this@DiagnosticActivity, item.title, item.detail) },
                  )
                }
                HyButton(t("复制反馈", "Copy feedback"), enabled = !busy) { copyReport() }
                HyButton(t("导出报告", "Export report"), enabled = !busy) { exportReport() }
              }
            }
          item {
            HySection(t("启动记录", "Startup records")) {
              ArrowPreference(
                title = t("最近五次启动", "Last five starts"),
                summary = t("阶段、实际错误与退出码", "Stage, actual error and exit code"),
                startAction = { HyIcon(R.drawable.ic_recovery_clock) },
                onClick = ::showHistory,
              )
              ArrowPreference(
                title = t("启动故障处理", "Startup recovery"),
                summary = t("查看问题与恢复方式", "Problems and recovery options"),
                startAction = { HyIcon(R.drawable.ic_recovery_wrench) },
                onClick = { startActivity(Intent(this@DiagnosticActivity, StartupRecoveryActivity::class.java)) },
              )
            }
          }
          item {
            val logBusy = logState?.busy == true
            HySection(t("错误日志", "Error logs")) {
              ArrowPreference(
                title = t("最近导出", "Last export"),
                summary = logState?.let { UiStateText.render(it.message) } ?: "",
                enabled = !logBusy && logState?.uri != null,
                onClick = ::openSavedLog,
              )
              HyButton(t("下载错误日志", "Download error logs"), enabled = !logBusy) { chooseLogExport(false) }
              HyButton(t("另存为", "Save as"), enabled = !logBusy) { chooseLogExport(true) }
            }
          }
          item {
            HySection(t("排查与修复", "Troubleshooting")) {
              HyButton(t("修复工具", "Repair tools"), enabled = !busy) { repository.repairNetworkTools() }
              HyButton(t("打开插件管理", "Open plugin management")) {
                startActivity(Intent(this@DiagnosticActivity, MainActivity::class.java).putExtra("open_plugins", true))
              }
            }
          }
          item {
            HySection(t("补充复现步骤", "Steps to reproduce")) {
              TextField(
                value = steps,
                onValueChange = { steps = it.take(32768) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(16.dp),
                label = t("补充复现步骤，不要填写密钥或私人对话", "Describe the steps; do not include keys or private chats"),
              )
            }
          }
        }
      }
    )
    if (saved == null && intent.getBooleanExtra("download_error_logs", false)) logs.download()
  }

  private fun copyReport() {
    try {
      val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager? ?: throw IllegalStateException(UiText.text("剪贴板不可用"))
      clipboard.setPrimaryClip(SensitiveClipboard.text(UiText.text("HYDSH 诊断"), completeReport()))
      Toast.makeText(this, t("报告已复制，请在公开分享前查看内容", "Report copied. Review its content before public sharing"), Toast.LENGTH_SHORT).show()
    } catch (error: Exception) {
      Toast.makeText(this, UiText.text("复制失败，可尝试导出报告"), Toast.LENGTH_LONG).show()
    }
  }

  private fun exportReport() {
    try {
      exporter.launch(
        "HYDSH-diagnostic-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date()) + ".txt"
      )
    } catch (error: RuntimeException) {
      Toast.makeText(this, UiText.text("无法打开保存位置，请使用复制报告或检查系统文件管理器"), Toast.LENGTH_LONG).show()
    }
  }

  private fun openSavedLog() {
    val state = logs.state.value ?: return
    if (state.uri == null || state.busy) return
    try {
      var uri = state.uri
      if ("file" == uri.scheme)
        uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.updates", java.io.File(uri.path!!))
      startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, logs.savedMimeType()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (error: RuntimeException) {
      Toast.makeText(this, UiText.text("无法打开日志，可在文件管理器的 Download/DSHA 中查看，或重新下载"), Toast.LENGTH_LONG).show()
    }
  }

  /** 附加说明只对本次导出生效，不会保存。 */
  private fun chooseLogExport(saveAs: Boolean) {
    pendingLogExport = false
    val include = android.widget.CheckBox(this)
    include.isChecked = false
    include.text = t("仅本次附加本页主动填写的说明", "Attach the context entered on this page for this export only")
    DshaDialogBuilder(this)
      .setTitle(t("导出故障包", "Export diagnostics"))
      .setMessage(
        t(
          "包含结构化状态、现有脱敏操作记录尾部和摘要。原始进程输出、完整异常链及未采集的历史不会补入。附加说明默认不选，仅包含本页输入，不开启自动业务记录。",
          "Includes structured state, existing redacted operation tails and a summary. Raw process output, full exception chains and uncollected history are unavailable. Additional context is off by default and contains only this page's input; it enables no automatic business capture.",
        )
      )
      .setView(include)
      .setNegativeButton(t("取消", "Cancel")) { _, _ -> pendingLogExport = false }
      .setOnCancelListener { pendingLogExport = false }
      .setPositiveButton(t("继续", "Continue")) { _, _ ->
        val selected = include.isChecked
        val note = if (selected) steps else ""
        if (!saveAs) {
          logs.export(null, selected, note)
          return@setPositiveButton
        }
        pendingInclude = selected
        pendingNote = note
        pendingLogExport = true
        try {
          logExporter.launch(ErrorLogRepository.filename())
        } catch (error: RuntimeException) {
          pendingLogExport = false
          Toast.makeText(this, UiText.text("无法打开文件管理器，请尝试下载到默认目录"), Toast.LENGTH_LONG).show()
        }
      }
      .show()
  }

  private fun showHistory() {
    Thread(
        {
          val entries = HarnessController.get(this).startupDiagnostics().history()
          runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (entries.isEmpty()) {
              CardSheet.show(this, getString(R.string.ui134_recent_startups), t("还没有启动记录。", "No startup records yet."))
              return@runOnUiThread
            }
            val labels =
              entries.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.started)) + "\n" + it.stage + " · " + it.status }
            DshaDialogBuilder(this)
              .setTitle(getString(R.string.ui134_recent_startups))
              .setItems(labels.toTypedArray()) { _, index ->
                val entry = entries[index]
                CardSheet.show(
                  this,
                  java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.started)),
                  entry.stage + "\n\n" + entry.reason + "\n\n" + entry.log,
                )
              }
              .setNegativeButton(t("关闭", "Close"), null)
              .show()
          }
        },
        "diagnostic-history",
      )
      .start()
  }

  private fun completeReport(): String =
    SensitiveData.redact(repository.report.value.toString() + UiText.format("\n用户补充复现步骤：\n%s", steps))

  companion object {
    @JvmStatic
    fun downloadLogs(context: Context): Intent =
      Intent(context, DiagnosticActivity::class.java).putExtra("download_error_logs", true)
  }
}
