package com.deepseekharness.app.ui

import com.deepseekharness.app.R
import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.deepseekharness.app.HarnessService
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.core.PhoneAccess
import com.deepseekharness.app.util.UiLanguagePreference
import com.deepseekharness.app.util.UiText
import com.deepseekharness.app.util.UiThemePreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.utils.overScrollVertical

private fun t(zh: String, en: String): String = UiText.choose(zh, en)

/** 设置页跳转：子页面暂时沿用旧界面。 */
class SettingsNavigation(
  val openFragment: (Fragment) -> Unit,
  val openActivity: (Class<*>) -> Unit,
)

private val THEMES = listOf(UiThemePreference.SYSTEM, UiThemePreference.LIGHT, UiThemePreference.DARK)
private val LANGUAGES =
  listOf(UiLanguagePreference.SYSTEM, UiLanguagePreference.ZH, UiLanguagePreference.EN)

@Composable
fun SettingsScreen(nav: SettingsNavigation) {
  val context = LocalContext.current
  val config = remember { ConfigStore(context) }

  // HyPage 里的 Scaffold 同时提供下拉框的弹出层宿主。
  HyPage(title = t("设置", "Settings"), tasks = true) {
    item { CommonSection(context, config, nav) }
    item { ConnectionSection(context, config, nav) }
    item { DataSection(nav) }
    item { EnvironmentSection(nav) }
    item { OtherSection(nav) }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  SmallTitle(text = title)
  Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { content() }
}

@Composable
private fun CommonSection(context: Context, config: ConfigStore, nav: SettingsNavigation) {
  var theme by remember { mutableStateOf(THEMES.indexOf(config.uiTheme).coerceAtLeast(0)) }
  val languagePreference = config.uiLanguagePreference
  val language = remember {
    if (UiLanguagePreference.followsSystem(languagePreference)) 0
    else LANGUAGES.indexOf(languagePreference).coerceAtLeast(1)
  }
  var eco by remember { mutableStateOf(config.isEcoMode) }
  var pip by remember { mutableStateOf(config.isPictureInPictureEnabled) }
  var glass by remember { mutableStateOf(GlassBar.enabled) }

  Section(t("常用", "General")) {
    OverlayDropdownPreference(
      startAction = { HyIcon(MiuixIcons.Theme) },
      title = t("外观", "Appearance"),
      items = listOf(t("跟随系统", "Follow system"), t("浅色", "Light"), t("深色", "Dark")),
      selectedIndex = theme,
      onSelectedIndexChange = {
        theme = it
        ThemeController.select(context, THEMES[it])
      },
    )
    OverlayDropdownPreference(
      startAction = { HyIcon(MiuixIcons.Translate) },
      title = t("语言", "Language"),
      items = listOf(t("跟随系统", "Follow system"), "简体中文", "English"),
      selectedIndex = language,
      onSelectedIndexChange = { LanguageController.select(context, LANGUAGES[it]) },
    )
    SwitchPreference(
      startAction = { HyIcon(R.drawable.ic_recovery_clock) },
      title = t("省电模式", "Battery saver"),
      summary =
        if (eco) t("熄屏空闲 1 分钟后减少保活，有任务时继续运行", "Less keep-alive after 1 min idle with screen off")
        else t("始终保持运行，适合长时间任务", "Always keep running for long tasks"),
      checked = eco,
      onCheckedChange = {
        eco = it
        config.setEcoMode(it)
        HarnessService.refreshPowerMode(context)
      },
    )
    val pipSupported = remember { PictureInPictureActivity.supported(context) }
    SwitchPreference(
      startAction = { HyIcon(MiuixIcons.ScreenMirroring) },
      title = t("画中画", "Picture-in-picture"),
      summary =
        if (pipSupported) t("切到后台时以小窗显示对话", "Show the conversation in a small window in background")
        else stringResource(R.string.picture_in_picture_unsupported),
      checked = pip && pipSupported,
      enabled = pipSupported,
      onCheckedChange = {
        pip = it
        config.setPictureInPictureEnabled(it)
        // 系统还没给本应用开“画中画”权限时，打开开关就直接带去系统设置。
        if (it && !PictureInPictureActivity.allowed(context)) ConfigFragment.openPictureSettings(context)
      },
    )
    if (GlassBar.supported)
      SwitchPreference(
        startAction = { HyIcon(MiuixIcons.GridView) },
        title = t("液态玻璃底栏", "Liquid glass bottom bar"),
        summary = t("悬浮的半透明底栏，页面在后面透出", "A floating translucent bar that shows the page behind it"),
        checked = glass,
        onCheckedChange = {
          glass = it
          GlassBar.set(context, it)
        },
      )
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.Layers) },
      title = t("悬浮条", "Floating bar"),
      summary = t("在屏幕上实时显示 AI 输出", "Show AI output on screen"),
      onClick = { nav.openFragment(OverlayFragment()) },
    )
  }
}

@Composable
private fun ConnectionSection(context: Context, config: ConfigStore, nav: SettingsNavigation) {
  var lan by remember { mutableStateOf(config.isLanMode) }

  Section(t("连接", "Connection")) {
    SwitchPreference(
      startAction = { HyIcon(MiuixIcons.Link) },
      title = t("局域网访问", "LAN access"),
      summary = t("在同一 Wi-Fi 的电脑上打开工作台，仅在可信网络使用", "Open the workspace from another device on trusted Wi-Fi"),
      checked = lan,
      onCheckedChange = {
        lan = it
        config.setLanMode(it)
        ConfigFragment.applyLanMode(context, it)
        if (it) (context as? MainActivity)?.requestLocalNetwork()
      },
    )
    var phone by remember { mutableStateOf(PhoneAccess.allowed(context)) }
    SwitchPreference(
      startAction = { HyIcon(R.drawable.ic_ui_shield) },
      title = t("允许 AI 访问手机", "Allow AI to access the phone"),
      summary =
        if (phone) t("Linux 能读写手机存储，AI 可使用设备控制", "Linux can use phone storage; AI can use device control")
        else t("Linux 碰不到手机存储，AI 不能操作手机；文件用「文件」页传", "Linux cannot touch phone storage or control the phone; use Files to move files"),
      checked = phone,
      onCheckedChange = { on ->
        val apply = {
          phone = on
          PhoneAccess.set(context, on)
          android.widget.Toast.makeText(
            context,
            t("重启 DSH 和终端后完全生效", "Fully applies after restarting DSH and terminals"),
            android.widget.Toast.LENGTH_LONG,
          ).show()
        }
        if (!on) apply()
        else
          DshaDialogBuilder(context)
            .setTitle(t("允许 AI 访问手机？", "Allow AI to access the phone?"))
            .setMessage(
              t(
                "打开后，手机存储会挂进 Linux（Download、Documents 可写），AI 还能通过设备桥启动应用、打开网页、改剪贴板，以及使用你在「设备控制」里授予的能力。\n\nLinux 里如果混进了恶意脚本，它也能做同样的事。只在需要时打开，用完关掉。",
                "Phone storage is mounted into Linux (Download and Documents are writable), and the AI can launch apps, open links, change the clipboard and use whatever you grant in Device control.\n\nA malicious script in Linux could do the same. Turn this on only when needed.",
              )
            )
            .setPositiveButton(t("打开", "Turn on")) { _, _ -> apply() }
            .setNegativeButton(t("取消", "Cancel"), null)
            .show()
      },
    )
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.SearchDevice) },
      title = t("设备控制", "Device control"),
      summary = t("让 AI 操作手机：Root、Shizuku、ADB 与系统权限", "Let AI operate the phone: Root, Shizuku, ADB and permissions"),
      onClick = { nav.openFragment(DeviceGrantsFragment()) },
    )
  }
}

@Composable
private fun DataSection(nav: SettingsNavigation) {
  Section(t("数据", "Data")) {
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.Backup) },
      title = t("备份与恢复", "Backup & restore"),
      summary = t("导出、导入对话和设置", "Export and import conversations and settings"),
      onClick = { nav.openFragment(WorkspaceFragment()) },
    )
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.Folder) },
      title = t("存储空间", "Storage"),
      summary = t("空间占用、文件导出与缓存清理", "Usage, file export and cache cleanup"),
      onClick = { nav.openActivity(StorageActivity::class.java) },
    )
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.Notes) },
      title = t("技能", "Skills"),
      summary = t("查看和导入 SKILL.md", "View and import SKILL.md"),
      onClick = { nav.openActivity(SkillsActivity::class.java) },
    )
  }
}

@Composable
private fun EnvironmentSection(nav: SettingsNavigation) {
  Section(t("运行环境", "Environment")) {
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.Reset) },
      title = t("环境状态与修复", "Status & repair"),
      summary = t("检查组件，出问题时一键修复", "Check components and repair problems"),
      onClick = { nav.openFragment(InstallFragment()) },
    )
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.Tune) },
      title = t("高级运行选项", "Advanced options"),
      summary = t("运行方式、端口、DNS 等，一般不用改", "Runtime, port, DNS; usually no need to change"),
      onClick = { nav.openFragment(ConfigFragment()) },
    )
  }
}

@Composable
private fun OtherSection(nav: SettingsNavigation) {
  Section(t("其他", "Other")) {
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.Report) },
      title = t("诊断与日志", "Diagnostics & logs"),
      onClick = { nav.openActivity(DiagnosticActivity::class.java) },
    )
    ArrowPreference(
      startAction = { HyIcon(MiuixIcons.Info) },
      title = t("关于", "About"),
      onClick = { nav.openFragment(AboutFragment()) },
    )
  }
}
