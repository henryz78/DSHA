package com.deepseekharness.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import com.deepseekharness.app.BuildConfig;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.UpdateRepository;
import com.deepseekharness.app.util.UpdatePolicy;

/** 更新由用户选择通道和确认安装；下载任务不依赖页面生命周期。 */
public final class UpdateActivity extends AppCompatActivity {
  private UpdateRepository repository;
  private boolean resumeInstall;
  private boolean installDispatchReady;
  private HyLine status, progress, release, check, download, install, cancel, stable, preview;
  private String notes = "";

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    repository = new ViewModelProvider(this).get(UpdateRepository.class);
    resumeInstall = saved != null && saved.getBoolean("resumeInstall");
    repository.restoreInterruptedInstall(saved != null && saved.getBoolean("installPending"));
    var model = new HyScreenModel(getString(R.string.ui2_updates));
    model.note(com.deepseekharness.app.util.UiText.choose("查看应用与运行组件的版本信息。", "Version information for the app and runtime components."));
    var versions = model.section("");
    versions.kv(com.deepseekharness.app.util.UiText.choose("当前应用", "Current app"), BuildConfig.VERSION_NAME);
    versions.kv(com.deepseekharness.app.util.UiText.choose("版本码", "Version code"), String.valueOf(BuildConfig.VERSION_CODE));
    versions.kv(com.deepseekharness.app.util.UiText.choose("当前版本类型", "Edition"), com.deepseekharness.app.util.UiText.choose("标准版", "Standard"));
    versions.kv("DSH", com.deepseekharness.app.util.Constants.DSH_VERSION);
    var channel = model.section(getString(R.string.ui2_update_channel));
    boolean previewChannel = UpdatePolicy.PREVIEW.equals(repository.channel());
    stable = channel.radio(getString(R.string.ui_m0173), !previewChannel, () -> chooseChannel(false));
    preview = channel.radio(getString(R.string.ui_m0215), previewChannel, () -> chooseChannel(true));
    var update = model.section(com.deepseekharness.app.util.UiText.choose("检查与下载", "Check and download"));
    status = update.selectable("");
    progress = update.text("");
    release =
        update.arrow(
            com.deepseekharness.app.util.UiText.choose("查看候选版本", "View candidate"),
            com.deepseekharness.app.util.UiText.choose("更新记录、大小与安装方式", "Notes, size and installation"),
            () -> {
              UpdateRepository.State state = repository.state().getValue();
              if (state != null && state.release != null)
                CardSheet.show(this, getString(R.string.ui134_candidate), notes);
            });
    check = update.button(com.deepseekharness.app.util.UiText.text("检查更新"), true, repository::check);
    download =
        update.button(
            com.deepseekharness.app.util.UiText.text("下载更新"),
            false,
            () -> {
              if (android.os.Build.VERSION.SDK_INT >= 33
                  && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                      != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 104);
              else repository.download();
            });
    install = update.button(com.deepseekharness.app.util.UiText.text("安装更新"), true, this::install);
    cancel = update.button(com.deepseekharness.app.util.UiText.text("取消当前任务"), false, repository::cancel);
    var manage = model.section(com.deepseekharness.app.util.UiText.choose("管理", "Manage"));
    manage.arrow(
        com.deepseekharness.app.util.UiText.choose("更新受管运行时", "Update managed runtime"),
        com.deepseekharness.app.util.UiText.choose("只更新随包组件，保留个人数据", "Update bundled components only; keep personal data"),
        R.drawable.ic_recovery_refresh,
        this::showRuntimePlan);
    manage.arrow(
        com.deepseekharness.app.util.UiText.choose("兼容运行时回退", "Compatible runtime rollback"),
        com.deepseekharness.app.util.UiText.choose("通过隔离试运行后再切换", "Switch only after an isolated trial"),
        R.drawable.ic_recovery_wrench,
        () -> RuntimeRecoveryUi.show(this));
    manage.arrow(
        getString(R.string.ui134_changelog),
        BuildConfig.VERSION_NAME,
        R.drawable.ic_recovery_document,
        () -> CardSheet.show(this, getString(R.string.ui134_changelog), bundledChangelog()));
    manage.arrow(
        com.deepseekharness.app.util.UiText.choose("在浏览器查看发布页", "Open the release page"),
        "",
        R.drawable.ic_ui_link,
        () -> {
          UpdateRepository.State state = repository.state().getValue();
          AboutDialog.openBrowser(
              this,
              state != null && state.release != null
                  ? state.release.pageUrl
                  : com.deepseekharness.app.util.Constants.REPOSITORY_URL + "/releases");
        });
    setContentView(HyScreenKt.hyScreen(this, model, true));
    repository.state().observe(this, this::renderState);
    repository
        .installation()
        .observe(
            this,
            state -> {
              renderState(repository.state().getValue());
              dispatchInstall();
            });
    if (saved == null && !repository.hasTask()) repository.check();
  }

  private void showRuntimePlan() {
    CardPage page =
        new CardPage(
            this,
            getString(R.string.ui2_managed_update),
            com.deepseekharness.app.util.UiText.choose(
                "查看当前环境与包内运行组件。", "Review the current environment and bundled components."));
    android.widget.LinearLayout metadata = page.card();
    page.kv(metadata, "Ubuntu", bundledBase());
    page.kv(metadata, "DSH", com.deepseekharness.app.util.Constants.DSH_VERSION);
    page.kv(
        metadata,
        com.deepseekharness.app.util.UiText.choose("个人数据", "Personal data"),
        com.deepseekharness.app.util.UiText.choose("保持原位", "Kept in place"));
    metadata.addView(
        page.text(
            com.deepseekharness.app.util.UiText.choose(
                "检查后展示实际差异；确认更新时停止 Web 与终端，准备候选并完成隔离试运行。",
                "Review actual differences first. Updating stops Web and terminals, prepares a candidate and verifies it in an isolated trial."),
            12,
            R.color.text_secondary));
    var dialog = CardSheet.create(this, page);
    page.button(
        page.footer,
        com.deepseekharness.app.util.UiText.choose("检查更新计划", "Review update plan"),
        true,
        () -> {
          dialog.dismiss();
          startActivity(new Intent(this, ExtractActivity.class).putExtra("review_only", true));
        });
    page.button(
        page.footer,
        com.deepseekharness.app.util.UiText.choose("取消", "Cancel"),
        false,
        dialog::dismiss);
    CardSheet.show(dialog, this);
  }

  private String bundledBase() {
    try (java.io.InputStream input = getAssets().open("offline-rootfs.version")) {
      byte[] bytes = new byte[64];
      int count = input.read(bytes);
      return count > 0
          ? new String(bytes, 0, count, java.nio.charset.StandardCharsets.UTF_8).trim()
          : "—";
    } catch (java.io.IOException unavailable) {
      return "—";
    }
  }

  private String bundledChangelog() {
    try (java.io.InputStream input = getResources().openRawResource(R.raw.changelog);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
      byte[] block = new byte[2048];
      int count;
      while ((count = input.read(block)) != -1) {
        if (output.size() + count > 16384) throw new java.io.IOException("CHANGELOG_LIMIT");
        output.write(block, 0, count);
      }
      return "HYDSH " + BuildConfig.VERSION_NAME + "\n\n" + output.toString("UTF-8");
    } catch (java.io.IOException | android.content.res.Resources.NotFoundException failure) {
      com.deepseekharness.app.core.DiagnosticLog.record(this, "changelog", failure.toString());
      return "HYDSH " + BuildConfig.VERSION_NAME;
    }
  }

  private void chooseChannel(boolean previewChannel) {
    UpdateRepository.State state = repository.state().getValue();
    if ((state != null && state.busy) || repository.installationPending()) return;
    repository.setChannel(previewChannel ? UpdatePolicy.PREVIEW : UpdatePolicy.STABLE);
    stable.setChecked(!previewChannel);
    preview.setChecked(previewChannel);
  }

  private void renderState(UpdateRepository.State state) {
    if (state == null) return;
    UpdateRepository.InstallState installState = repository.installation().getValue();
    boolean installing = installState.pending();
    boolean verifying = installState.verifying;
    boolean busy = state.busy || installing;
    stable.setEnabled(!busy);
    preview.setEnabled(!busy);
    notes =
        state.release == null
            ? ""
            : state.release.version
                + " · "
                + String.format(java.util.Locale.ROOT, "%.2f MiB", state.release.bytes / 1048576.0)
                + "\n\n"
                + state.release.notes;
    String message = com.deepseekharness.app.util.UiStateText.render(state.message);
    if (installing)
      message =
          verifying
              ? com.deepseekharness.app.util.UiText.text("正在重新校验安装包…")
              : com.deepseekharness.app.util.UiText.text("校验完成，返回此页面后继续安装");
    else if (installState.error != null)
      message =
          com.deepseekharness.app.util.UiText.format(
              "%s\n%s", message, com.deepseekharness.app.util.UiStateText.render(installState.error));
    status.setText(message);
    boolean showBytes =
        state.total > 0
            && !verifying
            && (state.stage == UpdateRepository.Stage.IDLE
                || state.stage == UpdateRepository.Stage.DOWNLOADING);
    progress.setText(
        showBytes
            ? String.format(
                java.util.Locale.ROOT,
                "%.1f / %.1f MiB",
                state.downloaded / 1048576.0,
                state.total / 1048576.0)
            : "");
    progress.setVisible(showBytes);
    release.setVisible(state.release != null);
    check.setEnabled(!busy);
    check.setText(
        state.release == null
            ? com.deepseekharness.app.util.UiText.text("检查更新")
            : com.deepseekharness.app.util.UiText.text("重新检查"));
    check.setPrimary(state.release == null);
    download.setVisible(state.release != null && state.apk == null);
    download.setEnabled(!busy && state.release != null);
    download.setText(
        state.downloaded > 0 && state.apk == null
            ? com.deepseekharness.app.util.UiText.text("继续下载")
            : com.deepseekharness.app.util.UiText.text("下载更新"));
    install.setVisible(state.apk != null);
    install.setEnabled(!busy && state.apk != null);
    cancel.setVisible(state.busy && !installing);
  }

  private void install() {
    if (repository.installationPending()) return;
    try {
      if (android.os.Build.VERSION.SDK_INT >= 26
          && !getPackageManager().canRequestPackageInstalls()) {
        resumeInstall = true;
        startActivity(
            new Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())));
        return;
      }
      repository.requestInstall();
    } catch (Exception error) {
      resumeInstall = false;
      showInstallError(error);
    }
  }

  private void dispatchInstall() {
    if (!installDispatchReady
        || isFinishing()
        || isDestroyed()
        || getSupportFragmentManager().isStateSaved()) return;
    java.io.File apk = repository.takeInstallReady();
    if (apk == null) return;
    try {
      Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", apk);
      startActivity(
          new Intent(Intent.ACTION_VIEW)
              .setDataAndType(uri, "application/vnd.android.package-archive")
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    } catch (Exception error) {
      showInstallError(error);
    }
  }

  private void showInstallError(Exception error) {
    repository.installFailed(
        com.deepseekharness.app.util.UiText.format("无法安装：%s；可重试", error.getMessage()));
    Toast.makeText(this, repository.installation().getValue().error, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 104) repository.download();
  }

  @Override
  protected void onSaveInstanceState(Bundle saved) {
    saved.putBoolean("resumeInstall", resumeInstall);
    saved.putBoolean("installPending", repository.installationPending());
    super.onSaveInstanceState(saved);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (resumeInstall) {
      resumeInstall = false;
      if (android.os.Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls())
        install();
      else
        Toast.makeText(
                this, com.deepseekharness.app.util.UiText.text("未允许安装更新，可稍后重试"), Toast.LENGTH_SHORT)
            .show();
    }
  }

  @Override
  protected void onPostResume() {
    super.onPostResume();
    installDispatchReady = true;
    dispatchInstall();
  }

  @Override
  protected void onPause() {
    installDispatchReady = false;
    super.onPause();
  }
}
