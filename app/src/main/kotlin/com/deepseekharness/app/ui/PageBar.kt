package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.vectorResource
import com.deepseekharness.app.R
import com.deepseekharness.app.ui.compose.HydshTheme
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 独立子页面的 miuix 顶栏：返回键 + 居中标题，和主界面顶栏一致。系统栏边距由 ModernAndroidUi 处理。 */
@JvmOverloads
fun pageBar(
  context: Context,
  title: String,
  onBack: Runnable,
  actionLabel: String? = null,
  onAction: Runnable? = null,
  action2Label: String? = null,
  onAction2: Runnable? = null,
): ComposeView =
  ComposeView(context).apply { bindPageBar(this, title, onBack, actionLabel, onAction, action2Label, onAction2) }

@JvmOverloads
fun bindPageBar(
  view: ComposeView,
  title: String,
  onBack: Runnable,
  actionLabel: String? = null,
  onAction: Runnable? = null,
  action2Label: String? = null,
  onAction2: Runnable? = null,
) {
  view.setContent {
    HydshTheme {
      SmallTopAppBar(
        title = title,
        modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars),
        defaultWindowInsetsPadding = false,
        navigationIcon = {
          IconButton(onClick = onBack::run) {
            Icon(
              ImageVector.vectorResource(R.drawable.ic_ui_back),
              contentDescription = UiText.choose("返回", "Back"),
            )
          }
        },
        actions = {
          for ((label, action) in listOf(actionLabel to onAction, action2Label to onAction2))
            if (label != null && action != null)
              Text(
                text = label,
                modifier =
                  Modifier.clip(RoundedCornerShape(12.dp))
                    .clickable { action.run() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = MiuixTheme.colorScheme.primary,
                fontSize = 16.sp,
              )
        },
      )
    }
  }
}
