package com.deepseekharness.app.ui;

import android.content.Context;
import android.view.View;
import com.deepseekharness.app.util.UiText;

/** 恢复页的 miuix 页面骨架；静态展示可独立验收，不执行 Web 或修复操作。 */
final class StartupRecoveryLayout {
  final View root;
  final HyScreenModel model;
  final HyGroup recovery, tools, attempts, snapshots, plugins;
  final HyLine progress;

  private static String t(String zh, String en) {
    return UiText.choose(zh, en);
  }

  /** back 参数保留兼容；返回键由系统返回分发处理（默认关闭本页）。 */
  StartupRecoveryLayout(Context context, String reason, Runnable back) {
    model = new HyScreenModel(t("启动恢复", "Startup recovery"));
    model.note(t("进入原因：", "Reason: ") + reason);
    model.note(
        t(
            "此页面不依赖 Web。可查看最近启动记录、恢复配置快照，或卸载出错的插件。",
            "This page works independently of Web. Review recent starts, restore configuration snapshots, or remove faulty plugins."));
    progress = model.note("");
    progress.setTone(1);
    progress.setVisible(false);
    recovery = model.section(t("恢复与修复", "Recovery & repair"));
    tools = model.section(t("其他工具", "Other tools"));
    attempts = model.section(t("最近五次启动", "Last five starts"));
    snapshots = model.section(t("配置快照", "Configuration snapshots"));
    snapshots.text(
        t(
            "保留三次健康启动与三次修复前快照；仅保存配置，不复制会话、工作区或依赖目录。",
            "Keeps three healthy-start and three pre-repair snapshots. Saves configuration only, without sessions, workspaces, or dependency directories."));
    plugins = model.section(t("插件卸载", "Remove plugins"));
    root = HyScreenKt.hyScreen(context, model, true);
  }

  /** 分组里的一行操作（箭头行）。chevron 参数保留兼容，miuix 箭头行总带箭头。 */
  HyLine action(HyGroup parent, String label, int icon, Runnable click, boolean chevron) {
    return parent.arrow(label, "", icon, click);
  }

  /** 分组里的一段说明文字；size 与 color 保留兼容，统一按 miuix 说明样式显示。 */
  HyLine label(HyGroup parent, String value, int size, int color) {
    return parent.text(value);
  }

  static void enabled(HyLine action, boolean enabled) {
    action.setEnabled(enabled);
  }
}
