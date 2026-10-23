package com.deepseekharness.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.deepseekharness.app.R
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 自带顶栏的页面：主界面看到这个标记就隐藏公共顶栏。 */
interface OwnTopBar

/**
 * 所有 Compose 页面共用的 miuix 页面骨架：
 * 顶部大标题在左上，往下滑时收起成居中的小标题；列表到头有 miuix 回弹。
 * 系统栏边距由 ModernAndroidUi 统一留好，这里不再重复加。
 */
@Composable
fun HyPage(
  title: String,
  back: Boolean = false,
  tasks: Boolean = false,
  actions: @Composable RowScope.() -> Unit = {},
  content: LazyListScope.() -> Unit,
) {
  val scroll = MiuixScrollBehavior()
  val context = LocalContext.current
  val activity = context.activity()
  val dispatcher = activity?.onBackPressedDispatcher
  // 主界面开了玻璃底栏时，列表铺到底栏后面，末尾多留出底栏高度。
  val barInset =
    if (activity is MainActivity) with(LocalDensity.current) { GlassBar.contentBottomPx.toDp() } else 0.dp
  Scaffold(
    topBar = {
      TopAppBar(
        title = title,
        modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars),
        scrollBehavior = scroll,
        defaultWindowInsetsPadding = false,
        navigationIcon = {
          if (back)
            IconButton(onClick = { dispatcher?.onBackPressed() }, modifier = Modifier.padding(start = 12.dp)) {
              Icon(
                ImageVector.vectorResource(R.drawable.ic_ui_back),
                contentDescription = UiText.choose("返回", "Back"),
              )
            }
        },
        actions = {
          actions()
          if (tasks)
            IconButton(
              onClick = { BackgroundTasksActivity.open(context) },
              modifier = Modifier.padding(end = 12.dp),
            ) {
              Icon(
                ImageVector.vectorResource(R.drawable.ic_ui_tasks),
                contentDescription = UiText.choose("后台任务", "Background tasks"),
              )
            }
        },
      )
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxHeight().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
      contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp + barInset),
      overscrollEffect = null,
    ) {
      content()
    }
  }
}

/** 分组：可选的小标题 + miuix 卡片。 */
@Composable
fun HySection(title: String? = null, content: @Composable () -> Unit) {
  if (!title.isNullOrEmpty()) SmallTitle(text = title)
  Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { content() }
}

private tailrec fun Context.activity(): ComponentActivity? =
  when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
  }

/** 卡片内的“名称 — 值”行。 */
@Composable
fun HyKv(label: String, value: String) {
  BasicComponent(
    title = label,
    endActions = {
      Text(
        text = value,
        modifier = Modifier.widthIn(max = 220.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 14.sp,
        textAlign = TextAlign.End,
      )
    },
  )
}

/** 卡片内的说明文字（与行标题左对齐）。 */
@Composable
fun HyText(text: String, color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary, selectable: Boolean = false) {
  if (text.isEmpty()) return
  val body = @Composable {
    Text(
      text = text,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      color = color,
      fontSize = 14.sp,
    )
  }
  if (selectable) SelectionContainer { body() } else body()
}

/** 卡片外、分组之间的说明文字（与小标题左对齐）。 */
@Composable
fun HyNote(text: String, color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
  if (text.isEmpty()) return
  Text(
    text = text,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 4.dp, bottom = 12.dp),
    color = color,
    fontSize = 14.sp,
  )
}

/** miuix 按钮；inCard 时左右各留 16dp，卡片外留 12dp，与卡片对齐。 */
@Composable
fun HyButton(
  text: String,
  primary: Boolean = false,
  enabled: Boolean = true,
  inCard: Boolean = true,
  onClick: () -> Unit,
) {
  TextButton(
    text = text,
    onClick = onClick,
    modifier =
      Modifier.fillMaxWidth().padding(horizontal = if (inCard) 16.dp else 12.dp, vertical = 6.dp),
    enabled = enabled,
    colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
  )
}

/** 行首图标（来自 drawable 资源），主色。 */
@Composable
fun HyIcon(res: Int) = HyIcon(ImageVector.vectorResource(res))

/** 行首图标（miuix 图标库），主色。 */
@Composable
fun HyIcon(icon: ImageVector) {
  Icon(
    icon,
    contentDescription = null,
    modifier = Modifier.padding(end = 16.dp).size(26.dp),
    tint = MiuixTheme.colorScheme.primary,
  )
}
