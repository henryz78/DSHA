package com.deepseekharness.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.R
import com.deepseekharness.app.ui.compose.HydshTheme
import com.deepseekharness.app.util.UiText
import kotlinx.coroutines.flow.drop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private fun t(zh: String, en: String): String = UiText.choose(zh, en)

/** 插件列表的一行。tone：0 普通，1 已启用，2 异常。 */
class PluginRow(
  @JvmField val name: String,
  @JvmField val status: String,
  @JvmField val tone: Int,
  @JvmField val description: String,
  @JvmField val showSwitch: Boolean,
  @JvmField val checked: Boolean,
  @JvmField val switchEnabled: Boolean,
)

/** 插件页显示状态；PluginFragment（Java）负责写入。 */
class PluginUiState {
  var market by mutableStateOf(true)
  var linkHint by mutableStateOf("")
  var installEnabled by mutableStateOf(false)
  var downloadSource by mutableIntStateOf(0)
  var downloadSources by mutableStateOf(listOf<String>())
  var busy by mutableStateOf(false)
  var percent by mutableIntStateOf(-1)
  var message by mutableStateOf("")
  var cancellable by mutableStateOf(false)
  var safeMode by mutableStateOf(false)
  var sortIndex by mutableIntStateOf(0)
  var sortLabels by mutableStateOf(listOf<String>())
  var hideBuiltin by mutableStateOf(false)
  var countText by mutableStateOf("")
  var emptyText by mutableStateOf("")
  var rows by mutableStateOf(listOf<PluginRow>())

  val linkField = TextFieldState()
  val searchField = TextFieldState()

  fun link(): String = linkField.text.toString()

  fun setLink(value: String) = linkField.setTextAndPlaceCursorAtEnd(value)

  fun search(): String = searchField.text.toString()
}

/** 插件页的用户操作，由 PluginFragment 实现。 */
interface PluginActions {
  fun selectTab(market: Boolean)

  fun openWebsite()

  fun paste()

  fun install()

  fun importArchive()

  fun setDownloadSource(index: Int)

  fun refresh()

  fun checkUpdates()

  fun exportPlugins()

  fun restore()

  fun sort(index: Int)

  fun cancelTask()

  fun showStatus()

  fun toggle(name: String, enabled: Boolean)

  fun itemActions(name: String)

  fun filtersChanged()

  fun linkChanged()
}

fun bindPluginScreen(view: ComposeView, state: PluginUiState, actions: PluginActions) {
  view.setContent { HydshTheme { PluginScreen(state, actions) } }
}

@Composable
private fun PluginScreen(state: PluginUiState, actions: PluginActions) {
  LaunchedEffect(state) {
    snapshotFlow { state.link() }.drop(1).collect { actions.linkChanged() }
  }
  LaunchedEffect(state) {
    snapshotFlow { state.search() }.drop(1).collect { actions.filtersChanged() }
  }
  HyPage(title = stringResource(R.string.ui_m0016), tasks = true) {
      item {
        // miuix 标签：安装插件 / 已安装。
        TabRowWithContour(
          tabs = listOf(t("安装插件", "Install"), t("已安装", "Installed")),
          selectedTabIndex = if (state.market) 0 else 1,
          onTabSelected = { actions.selectTab(it == 0) },
          modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
        )
      }
      item { StatusCard(state, actions) }
      if (state.market) {
        item { LinkSection(state, actions) }
        item { OtherSources(state, actions) }
      } else {
        item { ManageSection(state, actions) }
        item { SmallTitle(text = state.countText) }
        if (state.rows.isEmpty())
          item {
            Text(
              text = state.emptyText,
              modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
              color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
          }
        items(state.rows, key = { it.name }) { row -> PluginItem(row, actions) }
      }
  }
}

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
  if (title != null) SmallTitle(text = title)
  Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { content() }
}

@Composable
private fun StatusCard(state: PluginUiState, actions: PluginActions) {
  if (state.message.isEmpty() && !state.busy) return
  Section(null) {
    BasicComponent(
      title = if (state.busy) t("正在处理", "Working") else t("插件操作结果", "Result"),
      summary = state.message,
      onClick = actions::showStatus,
    )
    if (state.busy) {
      LinearProgressIndicator(
        progress = if (state.percent < 0) null else state.percent / 100f,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )
      TextButton(
        text = t("取消当前操作", "Cancel"),
        onClick = actions::cancelTask,
        enabled = state.cancellable,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
      )
    }
  }
}

@Composable
private fun LinkSection(state: PluginUiState, actions: PluginActions) {
  Section(t("从链接安装", "Install from link")) {
    Column(modifier = Modifier.padding(16.dp)) {
      TextField(
        state = state.linkField,
        label = t("GitHub 仓库或压缩包链接", "GitHub repo or archive URL"),
        useLabelAsPlaceholder = true,
        lineLimits = TextFieldLineLimits.SingleLine,
      )
      Text(
        text = state.linkHint,
        modifier = Modifier.padding(top = 8.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp,
      )
      Spacer(Modifier.height(12.dp))
      Row {
        TextButton(text = t("粘贴", "Paste"), onClick = actions::paste, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        TextButton(
          text = t("安装", "Install"),
          onClick = actions::install,
          modifier = Modifier.weight(1f),
          enabled = state.installEnabled,
          colors = ButtonDefaults.textButtonColorsPrimary(),
        )
      }
    }
  }
}

@Composable
private fun OtherSources(state: PluginUiState, actions: PluginActions) {
  Section(t("其他方式", "Other ways")) {
    ArrowPreference(
      title = t("浏览插件网站", "Browse plugin site"),
      summary = t("找到喜欢的插件后复制链接回来安装", "Copy a plugin link and install it here"),
      onClick = actions::openWebsite,
    )
    ArrowPreference(
      title = t("导入插件包", "Import package"),
      summary = t("从手机里选择 ZIP 或 TAR.GZ 文件", "Choose a ZIP or TAR.GZ file"),
      onClick = actions::importArchive,
      enabled = !state.busy,
    )
    OverlayDropdownPreference(
      title = t("下载源", "Download source"),
      summary = t("镜像失败时会自动回退官方源", "Falls back to the official source"),
      items = state.downloadSources,
      selectedIndex = state.downloadSource,
      onSelectedIndexChange = actions::setDownloadSource,
      enabled = !state.busy,
    )
  }
}

@Composable
private fun ManageSection(state: PluginUiState, actions: PluginActions) {
  Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
    TextField(
      state = state.searchField,
      label = t("搜索插件", "Search plugins"),
      useLabelAsPlaceholder = true,
      lineLimits = TextFieldLineLimits.SingleLine,
    )
  }
  Section(null) {
    SwitchPreference(
      title = t("隐藏内置插件", "Hide built-in plugins"),
      checked = state.hideBuiltin,
      onCheckedChange = {
        state.hideBuiltin = it
        actions.filtersChanged()
      },
    )
    OverlayDropdownPreference(
      title = t("排序", "Sort"),
      items = state.sortLabels,
      selectedIndex = state.sortIndex,
      onSelectedIndexChange = actions::sort,
    )
    ArrowPreference(title = t("刷新列表", "Refresh"), onClick = actions::refresh, enabled = !state.busy)
    ArrowPreference(
      title = t("检查更新", "Check for updates"),
      onClick = actions::checkUpdates,
      enabled = !state.busy,
    )
    ArrowPreference(
      title = t("导出插件包", "Export plugins"),
      onClick = actions::exportPlugins,
      enabled = !state.busy,
    )
    if (state.safeMode)
      ArrowPreference(
        title = t("恢复第三方插件", "Restore third-party plugins"),
        summary = t("恢复安全启动前已启用的插件", "Re-enable plugins disabled by safe start"),
        onClick = actions::restore,
        enabled = !state.busy,
      )
  }
}

@Composable
private fun PluginItem(row: PluginRow, actions: PluginActions) {
  val toneColor =
    when (row.tone) {
      1 -> MiuixTheme.colorScheme.primary
      2 -> MiuixTheme.colorScheme.error
      else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
  Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
    BasicComponent(
      onClick = { actions.itemActions(row.name) },
      endActions = {
        if (row.showSwitch)
          Switch(
            checked = row.checked,
            onCheckedChange = { actions.toggle(row.name, it) },
            enabled = row.switchEnabled,
          )
      },
    ) {
      Text(text = row.name, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
      Text(text = row.status, modifier = Modifier.padding(top = 2.dp), fontSize = 13.sp, color = toneColor)
      if (row.description.isNotEmpty())
        Text(
          text = row.description,
          modifier = Modifier.padding(top = 2.dp),
          fontSize = 13.sp,
          color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
          maxLines = 2,
        )
    }
  }
}
