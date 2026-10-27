package com.deepseekharness.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.animate
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.deepseekharness.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec

/**
 * 主界面五个标签页放在 ViewPager2 里：切换时像 miuix 官方示例一样左右平移，途经中间的页面，
 * 用的也是官方翻页的弹簧参数。手指左右滑不翻页（终端、插件页里有横向手势）。
 *
 * 插件页和终端页在环境需要修复时换成修复页，终端页还分 PTY 和简易版；
 * 这些“页面种类”编进条目 id，种类一变 [refresh] 就会换掉对应页面。
 */
class MainPages(
  activity: FragmentActivity,
  private val kindOf: (Int) -> Int,
  private val create: (Int) -> Fragment,
) : FragmentStateAdapter(activity) {
  private val kinds = IntArray(COUNT) { kindOf(it) }

  override fun getItemCount() = COUNT

  override fun getItemId(position: Int) = position * 10L + kinds[position]

  override fun containsItem(itemId: Long): Boolean {
    val position = (itemId / 10).toInt()
    return position in 0 until COUNT && kinds[position] == (itemId % 10).toInt()
  }

  override fun createFragment(position: Int) = create(position)

  /** FragmentStateAdapter 给页面加的标签，用来找到某一页当前的 Fragment。 */
  fun tag(position: Int) = "f" + getItemId(position)

  /** 重新判断每页的种类，变了的页面重建。 */
  fun refresh() {
    for (i in 0 until COUNT) {
      val kind = kindOf(i)
      if (kind != kinds[i]) {
        kinds[i] = kind
        notifyItemChanged(i)
      }
    }
  }

  private var position = 0f
  private var job: Job? = null

  /** 接到 ViewPager2 上：禁用手指翻页，并记录当前滚动位置（动画中途改目标时从这里接着走）。 */
  fun attach(pager: ViewPager2) {
    pager.isUserInputEnabled = false
    // 所有页一开始就全部建好：否则滑动途中才去建插件页、设置页，那一帧卡住，动画就直接跳到终点。
    pager.offscreenPageLimit = COUNT - 1
    pager.adapter = this
    position = pager.currentItem.toFloat()
    pager.registerOnPageChangeCallback(
      object : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrolled(index: Int, offset: Float, offsetPixels: Int) {
          position = index + offset
        }
      }
    )
  }

  /** 用官方翻页弹簧平移到目标页；系统关闭了动画、或页面还没排版时直接跳过去。 */
  fun slide(pager: ViewPager2, index: Int) {
    job?.cancel()
    if (pager.width == 0 || !ValueAnimator.areAnimatorsEnabled()) {
      if (pager.isFakeDragging) pager.endFakeDrag()
      pager.setCurrentItem(index, false)
      position = index.toFloat()
      return
    }
    // 没在滑动时以翻页器实际所在页为起点（系统回收后恢复，翻页器会自己回到原来那页）。
    if (!pager.isFakeDragging) position = pager.currentItem.toFloat()
    val distance = (index - position) * pager.width
    if (distance == 0f) return
    if (!pager.isFakeDragging && !pager.beginFakeDrag()) {
      pager.setCurrentItem(index, false)
      return
    }
    lateinit var self: Job
    self =
      scope.launch {
        val start = position * pager.width
        val end = (COUNT - 1) * pager.width.toFloat()
        var applied = start
        try {
          animate(0f, distance, animationSpec = PagerNavigationSpringSpec) { value, _ ->
            // 弹簧会稍微冲过目标再回来；首尾两页冲不出去，所以夹在边界内，到边就停住，不会被往回拉。
            val want = (start + value).coerceIn(0f, end)
            // fakeDragBy 正数是往前一页拖，所以取反。
            pager.fakeDragBy(-(want - applied))
            applied = want
          }
        } finally {
          // 被新的切换打断时保持拖动状态，由新的动画接着走。
          if (job === self) {
            if (pager.isFakeDragging) {
              // 结束前补齐取整误差，正好停在目标页，系统就不会再做一段吸附滚动
              // （那段滚动没结束时开始下一次拖动会被取消，页面直接跳过去，停下的页面也不会进入运行状态）。
              val rest = (index - position) * pager.width
              if (rest != 0f) pager.fakeDragBy(-rest)
              pager.endFakeDrag()
            }
            if (pager.currentItem != index) pager.setCurrentItem(index, false)
          }
        }
      }
    job = self
  }

  companion object {
    const val COUNT = 5
    private val scope = CoroutineScope(AndroidUiDispatcher.Main)

    @JvmStatic
    fun index(tab: Int) =
      when (tab) {
        R.id.nav_plugins -> 1
        R.id.nav_files -> 2
        R.id.nav_terminal -> 3
        R.id.nav_settings -> 4
        else -> 0
      }

    @JvmStatic
    fun tab(index: Int) =
      when (index) {
        1 -> R.id.nav_plugins
        2 -> R.id.nav_files
        3 -> R.id.nav_terminal
        4 -> R.id.nav_settings
        else -> R.id.nav_launch
      }
  }
}
