package com.deepseekharness.app.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.content.Intent;
import androidx.fragment.app.Fragment;
import com.deepseekharness.app.*;
import com.deepseekharness.app.util.UiText;

public final class AboutFragment extends Fragment implements OwnTopBar {
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
    return AboutScreenKt.aboutPage(
        requireContext(),
        new AboutActions(
            () -> AboutDialog.openBrowser(requireContext(), AboutDialog.GITHUB_URL),
            () ->
                new DshaDialogBuilder(requireContext())
                    .setTitle(UiText.choose("交流与反馈", "Community and feedback"))
                    .setItems(
                        new String[] {
                          UiText.choose("打开 QQ 群", "Open QQ group"),
                          UiText.choose("打开问题反馈", "Open issue tracker")
                        },
                        (d, i) -> {
                          if (i == 0) AboutDialog.openQQGroup(requireContext());
                          else
                            AboutDialog.openBrowser(
                                requireContext(), AboutDialog.GITHUB_URL + "/issues");
                        })
                    .show(),
            this::showLicenses));
  }

  private void showLicenses() {
    try {
      String[] listed = requireContext().getAssets().list("licenses");
      if (listed == null || listed.length > 128) throw new java.io.IOException("LICENSE_INDEX");
      String[] names =
          java.util.Arrays.stream(listed)
              .filter(name -> name.matches("[A-Za-z0-9_.-]+\\.txt"))
              .sorted()
              .toArray(String[]::new);
      new DshaDialogBuilder(requireContext())
          .setTitle(UiText.choose("随包许可全文", "Bundled license texts"))
          .setItems(names, (dialog, index) -> showLicense(names[index]))
          .setNegativeButton(android.R.string.cancel, null)
          .show();
    } catch (java.io.IOException failure) {
      Toast.makeText(
              requireContext(),
              UiText.choose("无法读取随包许可清单。", "Unable to read the bundled license list."),
              Toast.LENGTH_LONG)
          .show();
    }
  }

  private void showLicense(String name) {
    final View owner = getView();
    final android.content.res.AssetManager assets = requireContext().getAssets();
    new Thread(
            () -> {
              String content;
              try (var in = assets.open("licenses/" + name);
                  var out = new java.io.ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192];
                int count;
                while ((count = in.read(bytes)) != -1) {
                  if (out.size() + count > 1024 * 1024)
                    throw new java.io.IOException("LICENSE_SIZE");
                  out.write(bytes, 0, count);
                }
                content = out.toString("UTF-8");
              } catch (java.io.IOException failure) {
                content = UiText.choose("无法读取此许可全文。", "Unable to read this license text.");
              }
              final String text = content;
              new android.os.Handler(android.os.Looper.getMainLooper())
                  .post(
                      () -> {
                        if (!isAdded() || getView() != owner) return;
                        CardSheet.show(requireContext(), name, text);
                      });
            },
            "license-read")
        .start();
  }
}
