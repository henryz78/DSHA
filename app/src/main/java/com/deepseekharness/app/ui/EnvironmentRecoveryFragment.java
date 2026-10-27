package com.deepseekharness.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import com.deepseekharness.app.R;
import com.deepseekharness.app.util.UiText;

/** 环境未完成时仍可进入主界面，明确列出可用的原生恢复入口。 */
public final class EnvironmentRecoveryFragment extends Fragment implements OwnTopBar {
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
    var model = new HyScreenModel(UiText.text("运行环境需要恢复"));
    model.note(
        UiText.text(
            "可以在设置中修改配置、查看数据维护记录和下载日志。环境恢复完成后，再使用插件和终端。个人数据与原环境继续保留。"));
    var actions = model.section("");
    actions.arrow(
        UiText.text("查看维护进度与恢复选项"),
        "",
        R.drawable.ic_recovery_wrench,
        () ->
            startActivity(
                new Intent(requireContext(), ExtractActivity.class).putExtra("review_only", true)));
    actions.arrow(
        UiText.text("查看并下载诊断日志"),
        "",
        R.drawable.ic_recovery_document,
        () -> startActivity(DiagnosticActivity.downloadLogs(requireContext())));
    return HyScreenKt.hyScreen(requireContext(), model, false);
  }
}
