package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.BuildConfig
import com.deepseekharness.app.R
import com.deepseekharness.app.ui.compose.HydshTheme
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 关于页的点击动作，由 AboutFragment 提供。 */
class AboutActions(
  @JvmField val repository: Runnable,
  @JvmField val community: Runnable,
  @JvmField val licenses: Runnable,
)

/** Fragment / Activity 用：创建一个随视图树生命周期释放的 Compose 页面。 */
fun composePage(context: Context, content: @Composable () -> Unit): ComposeView =
  ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { HydshTheme(content) }
  }

fun aboutPage(context: Context, actions: AboutActions): ComposeView =
  composePage(context) { AboutScreen(actions) }

@Composable
private fun AboutScreen(actions: AboutActions) {
  val repo = android.net.Uri.parse(AboutDialog.GITHUB_URL)
  HyPage(title = t("关于", "About"), back = true) {
    item {
      Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        androidx.compose.foundation.Image(
          androidx.compose.ui.res.painterResource(R.drawable.dsha_brand),
          contentDescription = null,
          modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)),
        )
        Text("HYDSH", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
          text = "DeepSeek Harness for Android\n${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
          color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
          fontSize = 13.sp,
          textAlign = TextAlign.Center,
        )
      }
    }
    item {
      HySection {
        HyKv(t("包名", "Package"), BuildConfig.APPLICATION_ID)
        HyKv(t("当前版本", "Edition"), t("标准版 · Android 11+", "Standard · Android 11+"))
        HyKv("DSH", Constants.DSH_VERSION)
        HyKv(t("架构", "Architecture"), "arm64-v8a")
        HyKv(t("开源许可", "License"), "MIT")
      }
    }
    item {
      HySection(t("了解更多", "Learn more")) {
        ArrowPreference(
          title = t("项目仓库", "Repository"),
          summary = (repo.host ?: "") + (repo.path ?: ""),
          startAction = { HyIcon(R.drawable.ic_ui_link) },
          onClick = { actions.repository.run() },
        )
        ArrowPreference(
          title = t("交流与反馈", "Community and feedback"),
          summary = UiText.format("QQ群 %s · 问题反馈", AboutDialog.QQ_GROUP),
          startAction = { HyIcon(R.drawable.ic_ui_chat) },
          onClick = { actions.community.run() },
        )
        ArrowPreference(
          title = t("开源许可", "Open-source licenses"),
          summary = t("HYDSH 与随包第三方组件", "HYDSH and bundled components"),
          startAction = { HyIcon(R.drawable.ic_recovery_document) },
          onClick = { actions.licenses.run() },
        )
      }
    }
  }
}
