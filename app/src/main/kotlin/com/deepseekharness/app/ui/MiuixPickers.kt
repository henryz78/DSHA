package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.util.UiText
import java.util.function.IntConsumer
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text

/** miuix 滚轮选时间（24 小时制）：回调一天中的第几分钟。 */
fun pickTime(context: Context, title: String, minuteOfDay: Int, picked: IntConsumer) {
  val hour = intArrayOf(minuteOfDay / 60 % 24)
  val minute = intArrayOf(minuteOfDay % 60)
  show(context, title, { picked.accept(hour[0] * 60 + minute[0]) }) {
    Wheel(hour, 0..23) { "%02d".format(it) }
    Text(":", fontSize = 22.sp, modifier = Modifier.padding(horizontal = 8.dp))
    Wheel(minute, 0..59) { "%02d".format(it) }
  }
}

/** miuix 滚轮选整数（如间隔小时）。 */
fun pickNumber(context: Context, title: String, min: Int, max: Int, value: Int, unit: String, picked: IntConsumer) {
  val current = intArrayOf(value.coerceIn(min, max))
  show(context, title, { picked.accept(current[0]) }) {
    Wheel(current, min..max) { "$it" }
    if (unit.isNotEmpty()) Text(unit, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
  }
}

@androidx.compose.runtime.Composable
private fun Wheel(holder: IntArray, range: IntRange, label: (Int) -> String) {
  val state = remember { mutableIntStateOf(holder[0]) }
  NumberPicker(
    state.intValue,
    { state.intValue = it; holder[0] = it },
    Modifier.width(72.dp),
    true,
    range,
    label,
  )
}

private fun show(
  context: Context,
  title: String,
  save: () -> Unit,
  wheels: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
  val view =
    composePage(context) {
      Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = wheels,
      )
    }
  // 弹窗关掉就释放，不跟着页面一直留着。
  view.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
  DshaDialogBuilder(context)
    .setTitle(title)
    .setView(view)
    .setPositiveButton(UiText.choose("确定", "OK")) { _, _ -> save() }
    .setNegativeButton(UiText.choose("取消", "Cancel"), null)
    .show()
}
