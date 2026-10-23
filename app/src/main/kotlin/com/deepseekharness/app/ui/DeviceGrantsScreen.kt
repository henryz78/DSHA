package com.deepseekharness.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import com.deepseekharness.app.R
import com.deepseekharness.app.util.UiText
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun t(zh: String, en: String) = UiText.choose(zh, en)

/** 设备控制页的显示状态；DeviceGrantsFragment 与 FullPermissionControls 负责写入。 */
class DeviceGrantsUi {
  // 全局权限
  var globalVisible by mutableStateOf(true)
  var globalChecked by mutableStateOf(false)
  var globalStatus by mutableStateOf("")
  var recoveryVisible by mutableStateOf(false)
  var rebuildVisible by mutableStateOf(false)

  // 设备通道
  var root by mutableStateOf(false)
  var rootStatus by mutableStateOf("")
  var rootVerifying by mutableStateOf(false)
  var guardStatus by mutableStateOf("")
  var shizukuStatus by mutableStateOf("")
  var shizukuAuthText by mutableStateOf("")
  var shizukuBusy by mutableStateOf(false)
  var adb by mutableStateOf(false)
  var adbStatus by mutableStateOf("")

  // 屏幕操作：a11yState 0 未开启 1 等待连接 2 已连接
  var a11yStatus by mutableStateOf("")
  var a11yState by mutableIntStateOf(0)
  var screenMode by mutableIntStateOf(0)
  var screenPolicyText by mutableStateOf("")
  var screenPauseText by mutableStateOf("")
  var virtualSupported by mutableStateOf(false)

  // 系统权限与敏感能力
  var allFilesStatus by mutableStateOf("")
  var allFilesGranted by mutableStateOf(false)
  var sms by mutableStateOf(false)
  var smsStatus by mutableStateOf("")
  var location by mutableStateOf(false)
  var sensors by mutableStateOf(false)
}

/** 页面上的点击与开关统一按名字回调给 Java，避免为几十个入口各写一个接口方法。 */
interface DeviceGrantsActions {
  fun action(key: String)

  fun toggle(key: String, on: Boolean)

  fun screenMode(index: Int)
}

fun deviceGrantsPage(context: Context, ui: DeviceGrantsUi, actions: DeviceGrantsActions): ComposeView =
  composePage(context) {
    HyPage(title = t("设备控制", "Device control"), back = true) {
      item { HyNote(t("按需开启。普通对话和终端不依赖设备控制授权。", "Turn on only what you need. Chats and terminals work without device control.")) }
      item {
        HySection(t("全局权限", "Global permission")) {
          if (ui.globalVisible)
            SwitchPreference(
              title = t("不再询问 HYDSH 安全确认", "Skip HYDSH safety confirmations"),
              summary = ui.globalStatus,
              checked = ui.globalChecked,
              onCheckedChange = { actions.toggle("global", it) },
            )
          else HyText(ui.globalStatus)
          HyText(
            t(
              "开启后，已获准能力的操作不再询问安全确认；关闭后恢复各项确认规则。此开关仅保存在本机，不随备份迁移。文件访问、Root、ADB、短信、定位、传感器和 Android 系统权限仍按各自授权设置生效，独立拒绝或暂停仍然有效。",
              "When enabled, operations using authorized capabilities skip safety confirmations. Turning it off restores confirmation rules. Stays on this device. File access, Root, ADB, SMS, location, sensors and Android permissions keep their separate grants; denial or pause stays effective.",
            )
          )
          if (ui.recoveryVisible) {
            HyButton(t("重新选择关闭并保存", "Choose off and save")) { actions.action("recover_off") }
            HyButton(t("重新选择开启并保存", "Choose on and save")) { actions.action("recover_on") }
          }
          if (ui.rebuildVisible) HyButton(t("重新设置本机确认", "Set up local confirmation again")) { actions.action("rebuild") }
          ArrowPreference(title = t("诊断详情", "Diagnostic details"), onClick = { actions.action("diagnostics") })
        }
      }
      item {
        HySection(t("Root", "Root")) {
          SwitchPreference(
            title = t("允许 Root Shell", "Allow Root Shell"),
            summary = t("已 Root 的手机：开启开关，在 Magisk / KernelSU 等管理器允许本应用，再点验证。未 Root 可用下方通道。", "Rooted phones: enable, allow this app in Magisk / KernelSU, then verify. Otherwise use a channel below."),
            checked = ui.root,
            onCheckedChange = { actions.toggle("root", it) },
          )
          ArrowPreference(title = t("通道状态", "Channel status"), summary = ui.rootStatus, onClick = { actions.action("root_status") })
          HyText(ui.guardStatus)
          HyButton(t("申请授权并验证 root", "Request and verify root"), enabled = !ui.rootVerifying) { actions.action("root_verify") }
        }
      }
      item {
        HySection("Shizuku") {
          HyText(t("先在 Shizuku 管理器启动服务，再返回授权。手机重启后通常需要重新启动服务。", "Start the service in Shizuku first, then come back to authorize. Usually needed again after a reboot."))
          ArrowPreference(title = t("通道状态", "Channel status"), summary = ui.shizukuStatus, onClick = { actions.action("shizuku_status") })
          HyButton(ui.shizukuAuthText, enabled = !ui.shizukuBusy) { actions.action("shizuku_auth") }
          HyButton(t("打开 Shizuku 管理器", "Open Shizuku")) { actions.action("shizuku_open") }
        }
      }
      item {
        HySection("ADB") {
          SwitchPreference(
            title = t("启用 ADB 设备通道（默认关闭）", "Enable the ADB device channel (off by default)"),
            summary = t("Android 11+：在同一 Wi-Fi 下配对无线调试。无需 Root；网络变化后可验证或重新配对。", "Android 11+: pair wireless debugging on the same Wi-Fi. No root needed; verify or re-pair after network changes."),
            checked = ui.adb,
            onCheckedChange = { actions.toggle("adb", it) },
          )
          ArrowPreference(title = t("通道状态", "Channel status"), summary = ui.adbStatus, onClick = { actions.action("adb_status") })
          ArrowPreference(title = t("ADB 无线配对", "ADB wireless pairing"), onClick = { actions.action("adb_pair") })
        }
      }
      item {
        HySection(t("屏幕操作", "Screen control")) {
          ArrowPreference(
            title = t("设置屏幕操作 · 两步完成", "Set up screen control in two steps"),
            summary = ui.a11yStatus,
            summaryColor =
              top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(
                color =
                  when (ui.a11yState) {
                    2 -> MiuixTheme.colorScheme.primary
                    1 -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else -> colorResource(R.color.err)
                  }
              ),
            onClick = { actions.action("accessibility") },
          )
          HyText(ui.screenPolicyText)
          listOf(t("全部手动", "Confirm every action"), t("按应用自定义", "Customize by app"), t("全部允许", "Allow all"))
            .forEachIndexed { index, label ->
              RadioButtonPreference(title = label, selected = ui.screenMode == index, onClick = { actions.screenMode(index) })
            }
          ArrowPreference(title = t("编辑应用确认规则", "Edit app confirmation rules"), onClick = { actions.action("screen_rules") })
          HyButton(ui.screenPauseText) { actions.action("screen_pause") }
          HyButton(t("暂停屏幕操作并撤销预授权", "Pause screen actions and revoke preauthorizations")) { actions.action("screen_revoke") }
          if (ui.virtualSupported) {
            HyText(t("虚拟屏需要已连接的设备通道。打开虚拟屏后点“创建竖屏”或“创建横屏”；页内有使用说明。", "A virtual screen needs a connected device channel. Open it, then tap Create portrait or Create landscape."))
            ArrowPreference(title = t("打开虚拟屏", "Open virtual screen"), onClick = { actions.action("virtual_screen") })
          }
        }
      }
      item {
        HySection(t("系统权限", "System permissions")) {
          ArrowPreference(
            title = t("管理文件访问权限", "Manage file access"),
            summary = ui.allFilesStatus,
            summaryColor =
              top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(
                color = if (ui.allFilesGranted) MiuixTheme.colorScheme.primary else colorResource(R.color.err)
              ),
            onClick = { actions.action("all_files") },
          )
          ArrowPreference(
            title = t("设置电池优化", "Battery optimization"),
            summary = t("后台断连时，可允许本应用后台运行并减少电池限制。厂商仍可能暂停后台。", "Allow background running if connections drop in the background. Vendors may still pause it."),
            onClick = { actions.action("battery") },
          )
        }
      }
      item {
        HySection(t("独立敏感能力", "Sensitive capabilities")) {
          SwitchPreference(title = t("短信读取", "Read SMS"), summary = ui.smsStatus, checked = ui.sms, onCheckedChange = { actions.toggle("sms", it) })
          SwitchPreference(title = t("允许读取位置", "Allow location"), checked = ui.location, onCheckedChange = { actions.toggle("location", it) })
          SwitchPreference(title = t("传感器与手电", "Sensors and flashlight"), checked = ui.sensors, onCheckedChange = { actions.toggle("sensors", it) })
        }
      }
    }
  }
