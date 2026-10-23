package com.deepseekharness.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.deepseekharness.app.R
import com.deepseekharness.app.ui.liquid.IosLiquidGlassNavigationBar
import com.deepseekharness.app.util.Constants
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 液态玻璃底栏的开关与布局状态。模糊依赖 RuntimeShader，Android 13 起才有。 */
object GlassBar {
  private const val KEY = "bottom_bar_glass"

  @JvmField val supported = Build.VERSION.SDK_INT >= 33

  /** 当前是否使用玻璃底栏；设置页切换后主界面立即重绘。 */
  var enabled by mutableStateOf(false)
    private set

  /** 内容铺到玻璃底栏后面时，HyPage 列表底部要额外留出的高度（像素）。 */
  @JvmStatic var contentBottomPx by mutableIntStateOf(0)

  /** 底栏后面的页面容器；玻璃从这里取样。 */
  @JvmStatic var source: BackdropSourceLayout? = null

  /** 取样节点重建时加一，底栏在绘制时读取它，从而重新录一次背景。 */
  internal var nodeVersion by mutableIntStateOf(0)

  /** 终端页不铺到玻璃底栏后面：底部留出原有间距再加底栏高度。 */
  @JvmStatic
  fun padTerminal(view: View) {
    val base = view.resources.getDimensionPixelSize(R.dimen.gap_control)
    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, base + contentBottomPx)
  }

  @JvmStatic
  fun load(context: Context) {
    enabled =
      supported &&
        context.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
  }

  @JvmStatic
  fun set(context: Context, on: Boolean) {
    context.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    enabled = supported && on
  }
}

/**
 * 页面容器：开启取样后，把子页面录进一个 RenderNode 再画出来，玻璃底栏引用同一个节点做模糊，
 * 页面滚动时节点内容由系统实时更新，不需要逐帧重录。
 */
class BackdropSourceLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
  FrameLayout(context, attrs) {
  var capture = false
    set(value) {
      if (field == value) return
      field = value
      if (!value) node = null
      invalidate()
    }

  var node: RenderNode? = null
    private set

  override fun dispatchDraw(canvas: Canvas) {
    if (!capture || Build.VERSION.SDK_INT < 29 || !canvas.isHardwareAccelerated) {
      super.dispatchDraw(canvas)
      return
    }
    val target =
      node ?: RenderNode("glass-source").also {
        node = it
        GlassBar.nodeVersion++
      }
    target.setPosition(0, 0, width, height)
    val recording = target.beginRecording(width, height)
    try {
      super.dispatchDraw(recording)
    } finally {
      target.endRecording()
    }
    canvas.drawRenderNode(target)
  }
}

private val location = IntArray(2)
private val sourceLocation = IntArray(2)

/** 液态玻璃底栏：官方示例的 IosLiquidGlassNavigationBar，背景取样换成主界面的页面容器。 */
@Composable
internal fun GlassNavigationBar(tabs: List<Triple<Int, Int, Int>>, selected: Int, onSelect: (Int) -> Unit) {
  val colors = MiuixTheme.colorScheme
  val view = LocalView.current
  val surface = colors.surface
  val backdrop = rememberLayerBackdrop {
    GlassBar.nodeVersion
    drawRect(surface)
    val source = GlassBar.source
    val node = source?.node
    if (source != null && node != null && Build.VERSION.SDK_INT >= 29) {
      view.getLocationInWindow(location)
      source.getLocationInWindow(sourceLocation)
      drawIntoCanvas {
        val canvas = it.nativeCanvas
        canvas.save()
        canvas.translate(
          (sourceLocation[0] - location[0]).toFloat(),
          (sourceLocation[1] - location[1]).toFloat(),
        )
        canvas.drawRenderNode(node)
        canvas.restore()
      }
    }
  }
  val index = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)
  val items = tabs.map { (_, icon, label) -> NavigationItem(stringResource(label), ImageVector.vectorResource(icon)) }
  Box(Modifier.fillMaxWidth()) {
    // 只负责录下后面的页面，本身不显示。必须和整个底栏视图对齐（从顶端开始），
    // 否则取样内容会和实际页面错开。
    Box(Modifier.matchParentSize().alpha(0f).layerBackdrop(backdrop))
    // 上方多留一段透明区域，让透镜折射和按压放大时取得到底栏上方的页面。
    Box(Modifier.padding(top = 24.dp)) {
      IosLiquidGlassNavigationBar(
        items = items,
        selectedIndex = index,
        onItemClick = { onSelect(tabs[it].first) },
        backdrop = backdrop,
        isBlurActive = true,
      )
    }
  }
}

