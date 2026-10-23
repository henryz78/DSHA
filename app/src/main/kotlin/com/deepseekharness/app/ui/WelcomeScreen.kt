package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.R
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.UiText
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private class WelcomeSlide(
  val eyebrow: Int,
  val icon: Int,
  val title: Int,
  val lead: Int,
  val rows: List<Triple<Int, Int, Int>> = emptyList(),
  val footer: Int? = null,
)

private val slides =
  listOf(
    WelcomeSlide(R.string.welcome_eyebrow0, R.drawable.ic_ui2_prompt, R.string.ui2_welcome_title0, R.string.ui2_welcome_lead0),
    WelcomeSlide(
      R.string.welcome_eyebrow1,
      R.drawable.ic_plugins,
      R.string.ui2_welcome_title1,
      R.string.ui2_welcome_lead1,
      listOf(
        Triple(R.drawable.ic_ui_chat, R.string.ui2_w1_0, R.string.ui2_ws1_0),
        Triple(R.drawable.ic_terminal, R.string.ui2_w1_1, R.string.ui2_ws1_1),
        Triple(R.drawable.ic_plugins, R.string.ui2_w1_2, R.string.ui2_ws1_2),
      ),
    ),
    WelcomeSlide(
      R.string.welcome_eyebrow2,
      R.drawable.ic_ui_shield,
      R.string.ui2_welcome_title2,
      R.string.ui2_welcome_lead2,
      listOf(
        Triple(R.drawable.ic_recovery_document, R.string.ui2_w2_0, R.string.ui2_ws2_0),
        Triple(R.drawable.ic_ui_shield, R.string.ui2_w2_1, R.string.ui2_ws2_1),
        Triple(R.drawable.ic_settings, R.string.ui2_w2_2, R.string.ui2_ws2_2),
      ),
      R.string.ui2_online_notice,
    ),
  )

/** 欢迎引导三页：左右滑或点主按钮翻页，最后一页进入环境准备。 */
fun welcomePage(context: Context, start: Runnable): ComposeView =
  composePage(context) { WelcomeScreen(start) }

@Composable
private fun WelcomeScreen(start: Runnable) {
  val pager = rememberPagerState { slides.size }
  val scope = rememberCoroutineScope()
  val colors = MiuixTheme.colorScheme
  Column(Modifier.fillMaxSize().background(colors.surface)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.app_name),
        Modifier.weight(1f),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
      )
      Text(
        stringResource(R.string.ui_welcome_counter, pager.currentPage + 1, slides.size),
        color = colors.onSurfaceVariantSummary,
        fontSize = 13.sp,
      )
    }
    HorizontalPager(pager, Modifier.weight(1f)) { index -> Slide(slides[index], index == 0) }
    Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
      Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(slides.size) { i ->
          val selected = i == pager.currentPage
          val width by animateDpAsState(if (selected) 24.dp else 6.dp)
          val color by animateColorAsState(if (selected) colors.primary else colors.dividerLine)
          Box(Modifier.width(width).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
      }
      val last = pager.currentPage == slides.size - 1
      TextButton(
        text =
          when (pager.currentPage) {
            0 -> stringResource(R.string.ui2_meet)
            1 -> UiText.choose("了解数据与权限", "Data and permissions")
            else -> UiText.choose("开始准备环境", "Prepare the environment")
          },
        onClick = {
          if (last) start.run() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = ButtonDefaults.textButtonColorsPrimary(),
      )
      TextButton(
        text = stringResource(R.string.ui2_skip),
        onClick = start::run,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      )
    }
  }
}

@Composable
private fun Slide(slide: WelcomeSlide, first: Boolean) {
  val colors = MiuixTheme.colorScheme
  Column(Modifier.fillMaxSize().overScrollVertical().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
      Text(stringResource(slide.eyebrow), color = colors.primary, fontSize = 12.sp, letterSpacing = 1.8.sp)
      Box(
        Modifier.padding(top = if (first) 40.dp else 20.dp)
          .size(if (first) 96.dp else 56.dp)
          .clip(RoundedCornerShape(if (first) 28.dp else 16.dp))
          .background(colors.primary.copy(alpha = 0.14f))
          .align(if (first) Alignment.CenterHorizontally else Alignment.Start),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          ImageVector.vectorResource(slide.icon),
          contentDescription = null,
          modifier = Modifier.size(if (first) 48.dp else 30.dp),
          tint = colors.primary,
        )
      }
      Text(
        stringResource(slide.title),
        Modifier.padding(top = if (first) 40.dp else 20.dp),
        fontSize = if (first) 30.sp else 26.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = if (first) 38.sp else 34.sp,
      )
      Text(
        stringResource(slide.lead),
        Modifier.padding(top = 12.dp),
        color = colors.onSurfaceVariantSummary,
        fontSize = 14.sp,
        lineHeight = 22.sp,
      )
      if (first)
        Text(
          stringResource(R.string.ui2_offline_badge, Constants.DSH_VERSION),
          Modifier.padding(top = 20.dp),
          color = colors.primary,
          fontSize = 12.sp,
        )
    }
    if (slide.rows.isNotEmpty())
      Card(Modifier.padding(horizontal = 12.dp).padding(top = 20.dp)) {
        slide.rows.forEach { (icon, title, summary) ->
          BasicComponent(
            title = stringResource(title),
            summary = stringResource(summary),
            startAction = { HyIcon(icon) },
          )
        }
      }
    slide.footer?.let {
      Text(
        stringResource(it),
        Modifier.padding(horizontal = 24.dp).padding(top = 12.dp),
        color = colors.onSurfaceVariantSummary,
        fontSize = 13.sp,
      )
    }
  }
}
