package com.deepseekharness.app.runtime;

import static org.junit.Assert.*;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** 就绪判断的缓存键：文件不变就复用，描述文件或健康记录一变就失效。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, application = android.app.Application.class)
public final class RuntimeReadinessCacheTest {
  @Test
  public void keyChangesOnlyWhenDescriptorOrHealthChanges() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    File files = context.getFilesDir();
    File linux = new File(files, "linux");
    File rootfs = new File(linux, "ubuntu");
    assertTrue(rootfs.mkdirs() || rootfs.isDirectory());
    RuntimeInstallationState state = new RuntimeInstallationState(context, linux, rootfs);

    String empty = state.versionMatchKey();
    assertEquals("无文件变化时键必须稳定", empty, state.versionMatchKey());

    File descriptor = new File(linux, ".runtime-descriptor.json");
    Files.write(descriptor.toPath(), "{\"id\":\"a\"}".getBytes(StandardCharsets.UTF_8));
    String written = state.versionMatchKey();
    assertNotEquals("出现描述文件必须换键", empty, written);
    assertEquals(written, state.versionMatchKey());

    Files.write(descriptor.toPath(), "{\"id\":\"abc\"}".getBytes(StandardCharsets.UTF_8));
    String rewritten = state.versionMatchKey();
    assertNotEquals("改写描述文件必须换键", written, rewritten);

    File health = new File(files, "runtime-health");
    assertTrue(health.mkdirs() || health.isDirectory());
    File record = new File(health, "abc.json");
    Files.write(record.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
    String healthy = state.versionMatchKey();
    assertNotEquals("新增健康记录必须换键", rewritten, healthy);

    Files.write(record.toPath(), "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
    assertNotEquals("改写健康记录必须换键", healthy, state.versionMatchKey());
  }
}
