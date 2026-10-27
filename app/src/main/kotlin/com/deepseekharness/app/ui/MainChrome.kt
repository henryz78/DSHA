package com.deepseekharness.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.deepseekharness.app.R
import com.deepseekharness.app.ui.compose.HydshTheme
import com.deepseekharness.app.util.UiText
import java.util.function.IntConsumer
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

/**
 * 主界面的顶栏和底部标签（miuix）。页面切换逻辑仍在 MainActivity，这里只负责显示，
 * MainActivity 通过 setter 更新状态。系统栏边距由外层布局处理，所以关掉组件自带的边距。
 */
class MainChrome {
  var title by mutableStateOf("HYDSH")
  var back by mutableStateOf(false)
  var tab by mutableIntStateOf(R.id.nav_launch)

  /** 顶部提示卡：0 不显示；1 维护任务进行中；2 需要处理（受限模式 / 启动未完成）。 */
  var bannerKind by mutableIntStateOf(0)
  var bannerTitle by mutableStateOf("")
  var bannerSummary by mutableStateOf("")

  fun attachBanner(view: ComposeView, onClick: Runnable) {
    view.setContent {
      HydshTheme {
        AnimatedVisibility(
          visible = bannerKind != 0,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut(),
        ) {
          Card(modifier = Modifier.padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 4.dp)) {
            val colors = MiuixTheme.colorScheme
            if (bannerKind == 1)
              BasicComponent(
                title = bannerTitle,
                summary = bannerSummary,
                startAction = { HyIcon(R.drawable.ic_ui_tasks) },
                endActions = { InfiniteProgressIndicator(color = colors.primary) },
                onClick = onClick::run,
              )
            else
              ArrowPreference(
                title = bannerTitle,
                summary = bannerSummary,
                startAction = {
                  Icon(
                    ImageVector.vectorResource(R.drawable.ic_ui_shield),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp).size(26.dp),
                    tint = colors.error,
                  )
                },
                onClick = onClick::run,
              )
          }
        }
      }
    }
  }

  fun attachTopBar(view: ComposeView, onBack: Runnable, onTasks: Runnable) {
    view.setContent {
      HydshTheme {
        SmallTopAppBar(
          title = title,
          // SmallTopAppBar 无论如何都会再加一次状态栏高度；外层布局已经留过，这里先标记为已处理，
          // 否则在系统补发边距（如从网页返回）后顶部会多出一块空白。
          modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars),
          defaultWindowInsetsPadding = false,
          navigationIcon = {
            if (back)
              IconButton(onClick = onBack::run) {
                Icon(
                  ImageVector.vectorResource(R.drawable.ic_ui_back),
                  contentDescription = UiText.choose("返回", "Back"),
                )
              }
          },
          actions = {
            IconButton(onClick = onTasks::run) {
              Icon(
                ImageVector.vectorResource(R.drawable.ic_ui_tasks),
                contentDescription = UiText.choose("后台任务", "Background tasks"),
              )
            }
          },
        )
      }
    }
  }

  /** onStyle：经典/玻璃切换后回调，MainActivity 据此重新安排页面底部留白。 */
  fun attachBottomBar(view: ComposeView, onSelect: IntConsumer, onStyle: Runnable) {
    view.setContent {
      HydshTheme {
        val glass = GlassBar.enabled
        LaunchedEffect(glass) { onStyle.run() }
        if (glass) {
          GlassNavigationBar(TABS, tab) { onSelect.accept(it) }
          return@HydshTheme
        }
        NavigationBar(defaultWindowInsetsPadding = false) {
          for ((id, icon, label) in TABS) {
            NavigationBarItem(
              selected = tab == id,
              onClick = { onSelect.accept(id) },
              icon = ImageVector.vectorResource(icon),
              label = stringResource(label),
            )
          }
        }
      }
    }
  }

  private companion object {
    val TABS =
      listOf(
        Triple(R.id.nav_launch, R.drawable.ic_launch, R.string.ui_m0005),
        Triple(R.id.nav_plugins, R.drawable.ic_plugins, R.string.ui_m0016),
        Triple(R.id.nav_files, R.drawable.ic_files, R.string.ui_tab_files),
        Triple(R.id.nav_terminal, R.drawable.ic_terminal, R.string.ui_m0021),
        Triple(R.id.nav_settings, R.drawable.ic_settings, R.string.ui_m0023),
      )
  }
}
