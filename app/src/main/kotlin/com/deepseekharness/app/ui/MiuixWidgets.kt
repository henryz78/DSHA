package com.deepseekharness.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.R
import com.deepseekharness.app.ui.compose.HydshTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 旧页面的按钮、开关、滑动条的替身：对外保留原控件的常用方法（文字、点击、开关状态、进度），
 * 里面画的是 miuix 组件。页面逻辑不用改，只把控件类型换掉。
 */

/** miuix TextButton。XML 里 app:miuixPrimary="true" 为主色按钮；点击走 View 自己的 OnClickListener。 */
class MiuixButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
  AbstractComposeView(context, attrs) {
  private var label by mutableStateOf<CharSequence>("")
  private var enabledState by mutableStateOf(true)
  var primary by mutableStateOf(false)

  init {
    val a = context.obtainStyledAttributes(attrs, R.styleable.MiuixButton)
    label = a.getText(R.styleable.MiuixButton_android_text) ?: ""
    primary = a.getBoolean(R.styleable.MiuixButton_miuixPrimary, false)
    enabledState = a.getBoolean(R.styleable.MiuixButton_android_enabled, true)
    a.recycle()
    super.setEnabled(enabledState)
    isClickable = true
  }

  fun setText(value: CharSequence?) {
    label = value ?: ""
    contentDescription = label
  }

  fun setText(resId: Int) = setText(context.getText(resId))

  fun getText(): CharSequence = label

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    enabledState = enabled
  }

  @Composable
  override fun Content() {
    HydshTheme {
      val fill = layoutParams?.width.let { it == ViewGroup.LayoutParams.MATCH_PARENT || it == 0 }
      TextButton(
        text = label.toString(),
        onClick = { performClick() },
        // 上下各留 4dp，相邻按钮之间自然有 8dp 间距，不会贴在一起。
        modifier = (if (fill) Modifier.fillMaxWidth() else Modifier).padding(vertical = 4.dp),
        enabled = enabledState,
        colors =
          if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
      )
    }
  }
}

/** 带文字的 miuix 开关行：左边文字，右边 Switch，点整行也能切换。 */
class MiuixSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
  AbstractComposeView(context, attrs) {
  fun interface OnCheckedChangeListener {
    fun onCheckedChanged(view: MiuixSwitch, checked: Boolean)
  }

  private var label by mutableStateOf<CharSequence>("")
  private var checkedState by mutableStateOf(false)
  private var enabledState by mutableStateOf(true)
  private var listener: OnCheckedChangeListener? = null

  init {
    val a = context.obtainStyledAttributes(attrs, R.styleable.MiuixButton)
    label = a.getText(R.styleable.MiuixButton_android_text) ?: ""
    enabledState = a.getBoolean(R.styleable.MiuixButton_android_enabled, true)
    a.recycle()
    super.setEnabled(enabledState)
  }

  fun setText(value: CharSequence?) {
    label = value ?: ""
  }

  fun setText(resId: Int) = setText(context.getText(resId))

  fun getText(): CharSequence = label

  fun isChecked(): Boolean = checkedState

  /** 和 CompoundButton 一样：状态真的变了才通知监听者（代码里改也会通知）。 */
  fun setChecked(checked: Boolean) {
    if (checked == checkedState) return
    checkedState = checked
    listener?.onCheckedChanged(this, checked)
  }

  fun toggle() = setChecked(!checkedState)

  fun setOnCheckedChangeListener(value: OnCheckedChangeListener?) {
    listener = value
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    enabledState = enabled
  }

  @Composable
  override fun Content() {
    HydshTheme {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(
              enabled = enabledState,
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {
              toggle()
            }
            .padding(vertical = 10.dp)
            .alpha(if (enabledState) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = label.toString(),
          modifier = Modifier.weight(1f).padding(end = 12.dp),
          color = MiuixTheme.colorScheme.onSurface,
          fontSize = 16.sp,
        )
        Switch(checked = checkedState, onCheckedChange = { setChecked(it) }, enabled = enabledState)
      }
    }
  }
}

/** miuix Slider，按整数刻度 0..max 工作，接口仿 SeekBar。 */
class MiuixSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
  AbstractComposeView(context, attrs) {
  fun interface OnChangeListener {
    fun onProgressChanged(slider: MiuixSlider, progress: Int, fromUser: Boolean)
  }

  private var maxState by mutableStateOf(100)
  private var value by mutableFloatStateOf(0f)
  private var listener: OnChangeListener? = null

  fun getMax(): Int = maxState

  fun setMax(max: Int) {
    maxState = max.coerceAtLeast(1)
  }

  fun getProgress(): Int = value.toInt()

  fun setProgress(progress: Int) = update(progress, false)

  fun setOnChangeListener(value: OnChangeListener?) {
    listener = value
  }

  private fun update(progress: Int, fromUser: Boolean) {
    val next = progress.coerceIn(0, maxState)
    if (next == value.toInt() && value == next.toFloat()) return
    value = next.toFloat()
    listener?.onProgressChanged(this, next, fromUser)
  }

  @Composable
  override fun Content() {
    HydshTheme {
      Slider(
        value = value,
        onValueChange = { update(Math.round(it), true) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        valueRange = 0f..maxState.toFloat(),
      )
    }
  }
}
