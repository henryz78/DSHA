package com.deepseekharness.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.deepseekharness.app.OverlayController
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

private fun t(zh: String, en: String) = UiText.choose(zh, en)

private val DURATIONS = intArrayOf(2, 5, 6, 10, 15, 30, 60)

/** 悬浮条样式编辑页：所有控件只改草稿，点“保存样式”才写入原有偏好键。 */
class OverlayFragment : Fragment(), OwnTopBar {
  private var enabled by mutableStateOf(false)
  private var alpha by mutableIntStateOf(OverlayController.DEF_ALPHA)
  private var lines by mutableIntStateOf(OverlayController.DEF_LINES)
  private var hold by mutableIntStateOf(OverlayController.DEF_HOLD)
  private var background by mutableIntStateOf(0)
  private var font by mutableIntStateOf(OverlayController.DEF_TEXT_SP)
  private var reasoning by mutableStateOf(false)
  private var commands by mutableStateOf(true)
  private var confirmation by mutableStateOf(true)
  private var permission by mutableStateOf("")
  private var livePreview = false

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
    val prefs = requireContext().getSharedPreferences(Constants.PREFS, 0)
    enabled = saved?.getBoolean("enabled") ?: prefs.getBoolean(OverlayController.K_ENABLED, false)
    alpha = saved?.getInt("alpha") ?: prefs.getInt(OverlayController.K_ALPHA, OverlayController.DEF_ALPHA)
    lines = saved?.getInt("lines") ?: prefs.getInt(OverlayController.K_LINES, OverlayController.DEF_LINES)
    hold = saved?.getInt("hold") ?: prefs.getInt(OverlayController.K_HOLD, OverlayController.DEF_HOLD)
    background = saved?.getInt("background") ?: prefs.getInt(OverlayController.K_BG, 0)
    font = saved?.getInt("font") ?: prefs.getInt(OverlayController.K_TEXT_SP, OverlayController.DEF_TEXT_SP)
    reasoning = saved?.getBoolean("reasoning") ?: prefs.getBoolean(OverlayController.K_REASONING, false)
    commands = saved?.getBoolean("commands") ?: prefs.getBoolean(OverlayController.K_COMMAND, true)
    confirmation = saved?.getBoolean("confirmation") ?: prefs.getBoolean(OverlayController.K_CONFIRM, true)
    renderPermission()
    return composePage(requireContext()) { Screen() }
  }

  @Composable
  private fun Screen() {
    val durations = DURATIONS.toList().let { if (hold in it) it else it + hold }
    HyPage(title = t("悬浮条", "Floating bar"), back = true) {
      item { HyNote(t("在其他应用中，轻量查看任务进展。", "Follow progress while using other apps.")) }
      item {
        HySection {
          SwitchPreference(
            title = t("显示悬浮条", "Show floating status"),
            summary = permission,
            checked = enabled,
            onCheckedChange = {
              enabled = it
              changed()
            },
          )
          Sample()
          HyButton(t("预览悬浮条", "Preview floating status")) { preview() }
        }
      }
      item {
        HySection(t("外观", "Appearance")) {
          SliderPreference(
            title = t("不透明度", "Opacity"),
            value = alpha.toFloat(),
            onValueChange = {
              alpha = Math.round(it)
              changed()
            },
            valueRange = 20f..100f,
            valueText = "$alpha%",
          )
          SliderPreference(
            title = t("文字大小", "Text size"),
            value = font.toFloat(),
            onValueChange = {
              font = Math.round(it)
              changed()
            },
            valueRange = 6f..20f,
            valueText = "${font}sp",
          )
          OverlayDropdownPreference(
            title = t("最多显示行数", "Maximum lines"),
            items = (1..6).map { UiText.format("%s 行", it) },
            selectedIndex = (lines - 1).coerceIn(0, 5),
            onSelectedIndexChange = {
              lines = it + 1
              changed()
            },
          )
          OverlayDropdownPreference(
            title = t("停留时间", "Display duration"),
            items = durations.map { UiText.format("%s 秒", it) },
            selectedIndex = durations.indexOf(hold).coerceAtLeast(0),
            onSelectedIndexChange = { hold = durations[it] },
          )
          OverlayDropdownPreference(
            title = t("背景样式", "Background style"),
            items = OverlayController.BG_NAMES.map { UiText.text(it) },
            selectedIndex = background.coerceIn(0, OverlayController.BG_NAMES.size - 1),
            onSelectedIndexChange = {
              background = it
              changed()
            },
          )
        }
      }
      item {
        HySection(t("显示内容", "Content")) {
          SwitchPreference(
            title = t("显示思考过程", "Show reasoning"),
            checked = reasoning,
            onCheckedChange = {
              reasoning = it
              changed()
            },
          )
          SwitchPreference(
            title = t("显示执行命令", "Show executed commands"),
            checked = commands,
            onCheckedChange = {
              commands = it
              changed()
            },
          )
          SwitchPreference(
            title = t("在悬浮条上确认命令", "Confirm commands in the overlay"),
            checked = confirmation,
            onCheckedChange = { confirmation = it },
          )
        }
      }
      item { HyButton(t("保存样式", "Save style"), primary = true, inCard = false) { save() } }
    }
  }

  /** 页面内的样式小样，和真实悬浮条用同一组背景色、透明度、行数、字号。 */
  @Composable
  private fun Sample() {
    val index = background.coerceIn(0, OverlayController.BG_PRESETS.size - 1)
    val argb = ((alpha * 255 / 100) shl 24) or (OverlayController.BG_PRESETS[index] and 0xFFFFFF)
    val text = buildString {
      append(t("HYDSH · 样式预览\n", "HYDSH · Style preview\n"))
      if (reasoning) append(t("正在思考：先检查文件内容。\n", "Thinking: checking the files first.\n"))
      if (commands) append(t("正在执行命令：ls -la\n", "Running command: ls -la\n"))
      append(t("文件已整理完成，可以继续下一步。", "Files are ready; continue to the next step."))
    }
    Text(
      text = text,
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(argb))
          .padding(14.dp),
      color = Color.White,
      fontSize = font.sp,
      maxLines = lines,
    )
  }

  private fun changed() {
    if (livePreview)
      OverlayController.showStylePreview(
        requireContext(),
        background.coerceIn(0, OverlayController.BG_PRESETS.size - 1),
        alpha,
        lines,
        font,
        reasoning,
        commands,
      )
  }

  private fun preview() {
    livePreview = true
    changed()
    permission =
      if (!OverlayController.permitted(requireContext())) {
        openOverlayPermission()
        t("需要悬浮窗权限，正在打开系统设置…", "Overlay permission is required; opening system settings…")
      } else t("系统悬浮条预览已显示，调整下面的选项会立即刷新。", "System overlay preview is shown; changes below update it immediately.")
  }

  private fun renderPermission() {
    permission =
      if (OverlayController.permitted(requireContext())) t("悬浮窗权限已开启", "Overlay permission is enabled")
      else t("需要悬浮窗权限，点击“预览悬浮条”即可授权。", "Overlay permission is required; tap “Preview floating status” to grant it.")
  }

  private fun openOverlayPermission() {
    val uri = Uri.parse("package:" + requireContext().packageName)
    try {
      startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri))
    } catch (unavailable: RuntimeException) {
      try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
      } catch (ignored: RuntimeException) {
        Toast.makeText(
            requireContext(),
            t("请在系统设置中允许 HYDSH 显示在其他应用上层。", "Allow HYDSH to display over other apps in system settings."),
            Toast.LENGTH_LONG,
          )
          .show()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    renderPermission()
    if (livePreview && OverlayController.permitted(requireContext())) changed()
  }

  private fun save() {
    val context = requireContext()
    context
      .getSharedPreferences(Constants.PREFS, 0)
      .edit()
      .putBoolean(OverlayController.K_ENABLED, enabled)
      .putInt(OverlayController.K_ALPHA, alpha)
      .putInt(OverlayController.K_LINES, lines)
      .putInt(OverlayController.K_HOLD, hold)
      .putInt(OverlayController.K_BG, background)
      .putInt(OverlayController.K_TEXT_SP, font)
      .putBoolean(OverlayController.K_REASONING, reasoning)
      .putBoolean(OverlayController.K_COMMAND, commands)
      .putBoolean(OverlayController.K_CONFIRM, confirmation)
      .apply()
    if (!enabled) OverlayController.teardown(context.applicationContext)
    else OverlayController.applyStyleNow(context.applicationContext)
    livePreview = false
    OverlayController.hideStylePreview(context.applicationContext)
    Toast.makeText(context, t("悬浮条设置已保存", "Overlay settings saved"), Toast.LENGTH_SHORT).show()
    if (enabled && !OverlayController.permitted(context)) openOverlayPermission()
  }

  override fun onDestroyView() {
    livePreview = false
    context?.let { OverlayController.hideStylePreview(it.applicationContext) }
    super.onDestroyView()
  }

  override fun onSaveInstanceState(out: Bundle) {
    super.onSaveInstanceState(out)
    out.putBoolean("enabled", enabled)
    out.putInt("alpha", alpha)
    out.putInt("lines", lines)
    out.putInt("hold", hold)
    out.putInt("background", background)
    out.putInt("font", font)
    out.putBoolean("reasoning", reasoning)
    out.putBoolean("commands", commands)
    out.putBoolean("confirmation", confirmation)
  }
}
