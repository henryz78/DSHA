package com.deepseekharness.app.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;
import androidx.fragment.app.Fragment;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.DeviceGrantsTest;
import com.deepseekharness.app.util.NativeConsentPolicy;
import com.deepseekharness.app.util.UiText;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Actual MaterialSwitch refresh and resume events must be read-only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, application = android.app.Application.class)
public final class FullPermissionControlsTest {
  private static final String KEY = "native_global_consent_v1";
  private SharedPreferences preferences;
  private Context context;
  private String previousLanguage;

  @Before
  public void reset() throws Exception {
    new DeviceGrantsTest().isolateProcessStateForIndependentPreferenceFixtures();
    previousLanguage = UiText.language();
    UiText.setLanguage("zh");
    context =
        new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_DeepseekHarness);
    preferences = context.getSharedPreferences("dsha_device_grants", Context.MODE_PRIVATE);
    preferences.edit().clear().commit();
  }

  @After
  public void restoreLanguage() {
    UiText.setLanguage(previousLanguage);
  }

  private final DeviceGrantsUi ui = new DeviceGrantsUi();

  private FullPermissionControls controls(AtomicInteger changes) {
    var owner =
        new Fragment() {
          @Override
          public Context getContext() {
            return context;
          }
        };
    return new FullPermissionControls(owner, ui, changes::incrementAndGet);
  }

  @Test
  public void creatingRefreshingAndResumingTheCheckedSwitchNeverPersists() throws Exception {
    String original = new NativeConsentPolicy(true, 17).encode();
    preferences.edit().putString(KEY, original).commit();
    var writes = new AtomicInteger();
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (store, key) -> writes.incrementAndGet();
    preferences.registerOnSharedPreferenceChangeListener(listener);
    var changes = new AtomicInteger();
    try (var controls = controls(changes)) {
      assertTrue(ui.getGlobalChecked());
      for (int i = 0; i < 10; i++) {
        controls.refresh();
        controls.pause();
        controls.resume();
        controls.cancelForLocationChange();
      }
      org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(
              android.os.Looper.getMainLooper())
          .idle();
      assertEquals(original, preferences.getString(KEY, null));
      assertEquals(0, writes.get());
      assertEquals(0, changes.get());
      var status = ui.getGlobalStatus();
      assertTrue(status.startsWith("已开启"));
      assertFalse(status.contains("revision"));
      assertFalse(status.contains("NONE"));
    }
    preferences.unregisterOnSharedPreferenceChangeListener(listener);
  }

  @Test
  public void refreshingToOffDoesNotPublishAnotherPolicyAndDetailsKeepRevision() throws Exception {
    preferences.edit().putString(KEY, new NativeConsentPolicy(true, 17).encode()).commit();
    var changes = new AtomicInteger();
    try (var controls = controls(changes)) {
      String off = new NativeConsentPolicy(false, 18).encode();
      preferences.edit().putString(KEY, off).commit();
      controls.refresh();
      org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(
              android.os.Looper.getMainLooper())
          .idle();
      assertFalse(ui.getGlobalChecked());
      assertEquals(off, preferences.getString(KEY, null));
      assertEquals(0, changes.get());
      var status = ui.getGlobalStatus();
      assertTrue(status.startsWith("已关闭"));
      assertFalse(status.contains("revision"));
      String detail =
          FullPermissionControls.diagnosticText(
              new NativeConsentPolicy.Snapshot(
                  NativeConsentPolicy.ReadState.OFF, NativeConsentPolicy.SaveState.NONE, 18));
      assertTrue(detail.contains("revision=18"));
      assertTrue(detail.contains("OFF / NONE"));
    }
  }

  @Test
  public void unconfiguredSettingIsDisplayedAsOffWithoutCreatingARecord() throws Exception {
    try (var controls = controls(new AtomicInteger())) {
      assertFalse(ui.getGlobalChecked());
      assertTrue(ui.getGlobalStatus().startsWith("已关闭"));
      assertFalse(preferences.contains(KEY));
    }
  }
}
