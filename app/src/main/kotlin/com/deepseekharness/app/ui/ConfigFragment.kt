package com.deepseekharness.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.deepseekharness.app.HarnessService
import com.deepseekharness.app.LanProxyService
import com.deepseekharness.app.R
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.util.ConfigInput
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.EnvironmentTaskGate
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

private fun t(zh: String, en: String) = UiText.choose(zh, en)

private val PERMISSION_MODES = listOf("danger-full-access", "workspace-write", "read-only")
private val DNS_MODES = listOf("auto", "ipv4", "native")

/**
 * 高级运行选项：端口、运行方式、DNS、网页布局等。所有改动先编辑草稿，点“保存配置”才写入。
 * 局域网、画中画、悬浮条只在设置首页改（这里以前也有一份，保存时会把首页刚改的值盖回去）。
 */
class ConfigFragment : Fragment(), OwnTopBar {
  private lateinit var config: ConfigStore
  private var permission by mutableIntStateOf(0)
  private var proroot by mutableStateOf(false)
  private var automaticPort by mutableStateOf(true)
  private var port by mutableStateOf("")
  private var portError by mutableStateOf("")
  private var dns by mutableIntStateOf(0)
  private var desktop by mutableStateOf(false)
  private var checkUpdate by mutableStateOf(false)
  private var staticLoader by mutableStateOf(false)
  private var noSeccomp by mutableStateOf(false)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
    val c = ConfigStore(requireContext()).also { config = it }
    permission = PERMISSION_MODES.indexOf(c.permissionMode).coerceAtLeast(0)
    proroot = c.isProroot
    automaticPort = "auto" == c.webPortMode
    port = c.port
    dns = DNS_MODES.indexOf(c.dnsMode).coerceAtLeast(0)
    desktop = c.isDesktopMode
    checkUpdate = c.isCheckUpdate
    staticLoader = c.isProrootStaticLoader
    noSeccomp = c.isProotSeccompDisabled
    return composePage(requireContext()) { Screen() }
  }

  @Composable
  private fun Screen() {
    HyPage(title = t("高级运行选项", "Advanced options"), back = true) {
      item {
        HyNote(
          t(
            "模型和 API Key 在 Web UI 中配置。这里的端口与运行方式在下次启动生效。",
            "Models and API keys are configured in the Web UI. Port and runtime changes apply on next start.",
          )
        )
      }
      item {
        HySection(t("运行与网络", "Runtime and network")) {
          OverlayDropdownPreference(
            title = t("运行方式", "Runtime"),
            items = listOf(t("proot（默认，稳定）", "proot (default, stable)"), t("proroot（可选，实验）", "proroot (optional, experimental)")),
            selectedIndex = if (proroot) 1 else 0,
            onSelectedIndexChange = { proroot = it == 1 },
          )
          OverlayDropdownPreference(
            title = getString(R.string.permission_mode_title),
            summary = t("下次启动生效。这是 DSH 的运行策略，不隔离插件与应用本身。", "Applies on next start. A DSH policy; it does not isolate plugins or the app."),
            items =
              listOf(
                getString(R.string.permission_mode_full),
                getString(R.string.permission_mode_workspace),
                getString(R.string.permission_mode_readonly),
              ),
            selectedIndex = permission,
            onSelectedIndexChange = { permission = it },
          )
          SwitchPreference(
            title = t("自动分配 Web 端口（推荐）", "Assign the Web port automatically (recommended)"),
            summary = t("端口占用时保留首选设置，自动使用可用端口。", "Keeps your preferred port and uses a free one when it is busy."),
            checked = automaticPort,
            onCheckedChange = { automaticPort = it },
          )
          if (!automaticPort) {
            TextField(
              value = port,
              onValueChange = {
                port = it
                portError = ""
              },
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
              label = t("首选 Web 端口", "Preferred Web port"),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            HyText(portError, color = colorResource(R.color.err))
          }
          ArrowPreference(
            title = t("恢复旧端口草稿", "Restore old port drafts"),
            onClick = { BrowserOriginRestore.show(requireContext()) },
          )
          OverlayDropdownPreference(
            title = t("DNS 解析策略", "DNS policy"),
            items = listOf(t("自动（推荐）", "Automatic (recommended)"), "IPv4", t("原生", "Native")),
            selectedIndex = dns,
            onSelectedIndexChange = { dns = it },
          )
        }
      }
      item {
        HySection(t("显示与启动", "Display and startup")) {
          SwitchPreference(
            title = t("使用桌面版网页布局", "Use the desktop web layout"),
            checked = desktop,
            onCheckedChange = { desktop = it },
          )
          SwitchPreference(
            title = t("启动时检查更新", "Check for updates at startup"),
            checked = checkUpdate,
            onCheckedChange = { checkUpdate = it },
          )
        }
      }
      item {
        HySection(t("运行时兼容选项", "Runtime compatibility")) {
          SwitchPreference(
            title = t("使用 proroot 静态加载器", "Use the proroot static loader"),
            checked = staticLoader,
            onCheckedChange = { staticLoader = it },
          )
          SwitchPreference(
            title = t("禁用 proot seccomp 加速", "Disable proot seccomp acceleration"),
            checked = noSeccomp,
            onCheckedChange = { noSeccomp = it },
          )
        }
      }
      item { HyButton(t("保存配置", "Save settings"), primary = true, inCard = false) { save() } }
    }
  }

  private fun save() {
    val c = config
    val ctx = requireContext()
    val chosenPort =
      try {
        if (automaticPort) c.portInt else ConfigInput.port(port)
      } catch (invalid: IllegalArgumentException) {
        portError = invalid.message ?: t("端口无效", "Invalid port")
        return
      }
    portError = ""
    val saving = EnvironmentTaskGate.tryAcquire(UiText.text("保存配置"))
    if (saving == null) {
      toast(UiText.format("正在%s，完成后再保存配置", EnvironmentTaskGate.activeKind()))
      return
    }
    try {
      saving.run<Any?> {
        c.setWebPort(if (automaticPort) "auto" else "fixed", chosenPort)
        c.setPermissionMode(PERMISSION_MODES[permission])
        c.setCheckUpdate(checkUpdate)
        c.setDesktopMode(desktop)
        c.setProroot(proroot)
        c.setProrootStaticLoader(staticLoader)
        c.setProotSeccompDisabled(noSeccomp)
        c.setDnsMode(DNS_MODES[dns])
        Toast.makeText(ctx, UiText.text("已保存；网页显示选项重新进入对话生效，端口与运行时需重启 Web"), Toast.LENGTH_LONG).show()
        null
      }
    } catch (error: Exception) {
      toast(UiText.format("配置保存未完成：%s", SensitiveData.redact(error.toString())))
    } finally {
      saving.close()
    }
  }

  private fun toast(text: String) {
    context?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }
  }

  companion object {
    /** 打开系统里本应用的“画中画”权限页；没有这个页面的系统退到应用详情。 */
    @JvmStatic
    fun openPictureSettings(ctx: Context) {
      if (!PictureInPictureActivity.supported(ctx)) {
        Toast.makeText(ctx, R.string.picture_in_picture_unsupported, Toast.LENGTH_LONG).show()
        return
      }
      val app = Uri.parse("package:" + ctx.packageName)
      try {
        ctx.startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", app))
      } catch (unavailable: RuntimeException) {
        try {
          ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
        } catch (ignored: RuntimeException) {
          Toast.makeText(ctx, R.string.picture_in_picture_settings_unavailable, Toast.LENGTH_LONG).show()
        }
      }
    }

    /** 局域网开关真正生效：开启时若 dsh 已鉴权就启动代理，关闭时停掉监听。 */
    @JvmStatic
    fun applyLanMode(ctx: Context, on: Boolean) {
      try {
        if (!on) {
          LanProxyService.stopLanListener()
          HarnessService.refreshPowerMode(ctx)
          return
        }
        HarnessService.ensureLanForeground(ctx)
        val hc = HarnessController.get(ctx)
        val gen = hc.webGeneration
        // dsh 还没起来或还没交换 cookie：等下次进入对话时自动启动。
        if (gen <= 0 || !LanProxyService.hasDshAuth(gen)) return
        LanProxyService.start(hc.proot().rootfsDir.absolutePath, ctx, hc.webPort, gen)
      } catch (t: Throwable) {
        android.util.Log.w("DSHA", UiText.format("LAN 开关生效失败: %s", t.message))
      }
    }
  }
}
