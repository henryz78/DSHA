package com.deepseekharness.app.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.deepseekharness.app.HarnessService
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.util.UiLanguagePreference
import com.deepseekharness.app.util.UiText
import com.deepseekharness.app.util.UiThemePreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
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

  // Scaffold 提供弹出层宿主，下拉框必须在它里面才能展开；系统栏由外层 Activity 处理。
  Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().overScrollVertical(),
      contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
    ) {
      item { CommonSection(context, config, nav) }
      item { ConnectionSection(context, config, nav) }
      item { DataSection(nav) }
      item { EnvironmentSection(nav) }
      item { OtherSection(nav) }
    }
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

  Section(t("常用", "General")) {
    OverlayDropdownPreference(
      title = t("外观", "Appearance"),
      items = listOf(t("跟随系统", "Follow system"), t("浅色", "Light"), t("深色", "Dark")),
      selectedIndex = theme,
      onSelectedIndexChange = {
        theme = it
        ThemeController.select(context, THEMES[it])
      },
    )
    OverlayDropdownPreference(
      title = t("语言", "Language"),
      items = listOf(t("跟随系统", "Follow system"), "简体中文", "English"),
      selectedIndex = language,
      onSelectedIndexChange = { LanguageController.select(context, LANGUAGES[it]) },
    )
    SwitchPreference(
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
    SwitchPreference(
      title = t("画中画", "Picture-in-picture"),
      summary = t("切到后台时以小窗显示对话", "Show the conversation in a small window in background"),
      checked = pip,
      onCheckedChange = {
        pip = it
        config.setPictureInPictureEnabled(it)
      },
    )
    ArrowPreference(
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
    ArrowPreference(
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
      title = t("备份与恢复", "Backup & restore"),
      summary = t("导出、导入对话和设置", "Export and import conversations and settings"),
      onClick = { nav.openFragment(WorkspaceFragment()) },
    )
    ArrowPreference(
      title = t("存储空间", "Storage"),
      summary = t("空间占用与缓存清理", "Usage and cache cleanup"),
      onClick = { nav.openActivity(StorageActivity::class.java) },
    )
    ArrowPreference(
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
      title = t("环境状态与修复", "Status & repair"),
      summary = t("检查组件，出问题时一键修复", "Check components and repair problems"),
      onClick = { nav.openFragment(InstallFragment()) },
    )
    ArrowPreference(
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
      title = t("诊断与日志", "Diagnostics & logs"),
      onClick = { nav.openActivity(DiagnosticActivity::class.java) },
    )
    ArrowPreference(
      title = t("关于", "About"),
      onClick = { nav.openFragment(AboutFragment()) },
    )
  }
}
