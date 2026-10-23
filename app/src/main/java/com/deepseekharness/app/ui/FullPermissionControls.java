package com.deepseekharness.app.ui;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.deepseekharness.app.core.DeviceGrants;
import com.deepseekharness.app.util.NativeConsentPolicy;
import com.deepseekharness.app.util.UiText;

/**
 * Local ASK confirmation toggle. It never requests or changes a device/system capability.
 * 只往 DeviceGrantsUi 写显示状态；刷新、暂停、恢复都是只读的，只有用户操作才会保存。
 */
final class FullPermissionControls implements AutoCloseable {
  private final Context app;
  private final Context ctx;
  private final Runnable policyChanged;
  private final DeviceGrantsUi ui;
  private boolean closed;

  FullPermissionControls(Fragment owner, DeviceGrantsUi ui, Runnable policyChanged) {
    ctx = owner.requireContext();
    app = ctx.getApplicationContext();
    this.ui = ui;
    this.policyChanged = policyChanged;
    refresh();
  }

  /** 用户拨动开关或在异常状态下重新选择。 */
  void choose(boolean enabled) {
    if (!closed) save(enabled);
  }

  /** 记录损坏时：确认后保留原件，并把本机设置重建为关闭。 */
  void rebuild() {
    if (closed) return;
    var candidate = new DeviceGrants(app).prepareConsentRecovery();
    if (candidate == null) {
      refresh();
      return;
    }
    // This is a user choice about local authority and must never skip its native confirmation.
    new DshaDialogBuilder(ctx, false)
        .setTitle(UiText.choose("重新设置本机确认", "Set up local confirmation again"))
        .setMessage(
            UiText.choose(
                "保留当前异常记录，并将本机全局确认设置重建为关闭。旧的排队确认会失效；不会恢复旧授权。完成后如需免除安全确认，请自行开启开关。其他能力、数据和文件不受影响。",
                "Keep the current invalid record and rebuild local global confirmation as off. Old queued confirmations become invalid; no old grant is restored. You can then enable the switch yourself. Other capability settings, data and files are unaffected."))
        .setNegativeButton(UiText.choose("取消", "Cancel"), null)
        .setPositiveButton(
            UiText.choose("保留原件并重建为关闭", "Keep original and rebuild as off"),
            (dialog, which) -> {
              if (closed) return;
              new DeviceGrants(app).rebuildConsentDisabled(candidate);
              policyChanged.run();
              refresh();
            })
        .show();
  }

  void showDiagnostics() {
    if (closed) return;
    CardSheet.show(
        ctx,
        UiText.choose("全局权限诊断", "Global permission diagnostics"),
        diagnosticText(new DeviceGrants(app).consentSnapshot()));
  }

  private void save(boolean enabled) {
    new DeviceGrants(app).setGlobalAllowed(enabled);
    policyChanged.run();
    refresh();
  }

  void refresh() {
    if (closed) return;
    var snapshot = new DeviceGrants(app).consentSnapshot();
    ui.setGlobalVisible(snapshot.hasSwitchValue());
    if (snapshot.hasSwitchValue())
      ui.setGlobalChecked(snapshot.readState() == NativeConsentPolicy.ReadState.ON);
    ui.setRecoveryVisible(
        snapshot.readState() == NativeConsentPolicy.ReadState.SAVE_FAILED
            || snapshot.readState() == NativeConsentPolicy.ReadState.RECONFIRM_REQUIRED);
    ui.setRebuildVisible(snapshot.canRebuild());
    String description =
        switch (snapshot.readState()) {
          case ON ->
              UiText.choose(
                  "已开启：免安全确认；各项能力授权保持独立",
                  "On: skip confirmation; capability grants remain separate");
          case OFF ->
              UiText.choose("已关闭：按各项确认规则", "Off: follow individual confirmation rules");
          case UNCONFIGURED ->
              UiText.choose(
                  "已关闭（默认）：按各项确认规则",
                  "Off (default): follow individual confirmation rules");
          case MALFORMED ->
              UiText.choose(
                  "本机确认设置记录损坏：未重置记录或恢复旧授权",
                  "Local confirmation record is malformed; no reset or grant restored");
          case READ_UNAVAILABLE ->
              UiText.choose(
                  "暂时无法读取本机确认设置；这不代表你已关闭",
                  "Cannot read local confirmation settings; this does not mean you turned them off");
          case CHANGING ->
              UiText.choose(
                  "正在更新本机确认设置；旧请求已失效",
                  "Updating local confirmation settings; old requests are invalid");
          case SAVE_FAILED ->
              UiText.choose(
                  "本次选择保存或核对失败：确认豁免未生效；不会自动恢复旧授权",
                  "Saving or checking this selection failed; confirmation bypass is unavailable; no old grant restored");
          case RECONFIRM_REQUIRED ->
              UiText.choose(
                  "本机设置现已可读；旧确认授权仍已失效，请重新选择并保存",
                  "Local settings are readable again; old consent remains invalid. Choose and save again");
        };
    ui.setGlobalStatus(description);
  }

  static String diagnosticText(NativeConsentPolicy.Snapshot snapshot) {
    return UiText.format(
        "%s / %s / revision=%s\n%s%s",
        snapshot.readState(),
        snapshot.saveState(),
        snapshot.revision(),
        UiText.choose(
            "revision 用于使旧请求失效，不代表开关状态。普通启动、停止和界面刷新不会修改本机确认设置。",
            "The revision invalidates old requests; it is not the switch state. Normal startup, stopping and UI refresh do not change this local setting."),
        snapshot.saveState() == NativeConsentPolicy.SaveState.CACHE_VERIFIED
            ? UiText.choose(
                "\n仅同进程缓存核对，未验证重开后的落盘值",
                "\nSame-process cache checked; persistence after reopening is unverified")
            : "");
  }

  void pause() {}

  void resume() {
    refresh();
  }

  void cancelForLocationChange() {
    refresh();
  }

  @Override
  public void close() {
    closed = true;
  }
}
