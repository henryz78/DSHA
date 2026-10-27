package com.deepseekharness.app.backup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class QuarantinedPluginReviewTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void existingReviewsDoNotPreventTheNextCandidate() throws Exception {
    var fs = new JvmBackupFileSystem();
    File parent = new File(temp.getRoot(), "reviews");
    Set<String> names = new HashSet<>();
    for (int i = 0; i < 65; i++) {
      File slot = QuarantinedPluginReview.createReviewSlot(fs, parent);
      assertTrue(slot.isDirectory());
      assertTrue(names.add(slot.getName()));
    }
    assertEquals(65, fs.list(parent).size());
    for (String name : names) assertTrue(new File(parent, name).isDirectory());
  }

  @Test
  public void profileHoistedDependencyBecomesThePluginsOwnEdge() throws Exception {
    var fs = new JvmBackupFileSystem();
    File store = temp.getRoot();
    File plugin = new File(store, "packages/package-aaaaaaaaaaaaaaaaaaaa");
    assertTrue(plugin.mkdirs());
    Files.writeString(
        new File(plugin, "package.json").toPath(),
        "{\"name\":\"p\",\"dependencies\":{\"ignore\":\"^7\",\"gone\":\"1\"}}");
    var nodes = new java.util.LinkedHashMap<String, Object>();
    nodes.put(
        "package-aaaaaaaaaaaaaaaaaaaa",
        new java.util.LinkedHashMap<>(java.util.Map.of("managed", false, "edges", java.util.Map.of())));
    nodes.put("package-bbbbbbbbbbbbbbbbbbbb", java.util.Map.of("managed", false, "edges", java.util.Map.of()));
    var graph =
        java.util.Map.<String, Object>of(
            "nodes",
            nodes,
            "profiles",
            java.util.Map.of("web", java.util.Map.of("ignore", "package-bbbbbbbbbbbbbbbbbbbb")));
    var result = QuarantinedPluginReview.withHoisted(fs, store, graph);
    @SuppressWarnings("unchecked")
    var node = (java.util.Map<String, Object>) ((java.util.Map<?, ?>) result.get("nodes")).get("package-aaaaaaaaaaaaaaaaaaaa");
    // 只补 profile 层确实绑定了的；没有来源的依赖不编造。
    assertEquals(java.util.Map.of("ignore", "package-bbbbbbbbbbbbbbbbbbbb"), node.get("edges"));
    assertEquals(java.util.Map.of(), ((java.util.Map<?, ?>) nodes.get("package-aaaaaaaaaaaaaaaaaaaa")).get("edges"));
  }

  @Test
  public void nonDirectoryReviewParentIsRejectedWithoutChangingIt() throws Exception {
    var fs = new JvmBackupFileSystem();
    File parent = new File(temp.getRoot(), "reviews");
    Files.writeString(parent.toPath(), "keep this file");
    assertThrows(IOException.class, () -> QuarantinedPluginReview.createReviewSlot(fs, parent));
    assertEquals("keep this file", Files.readString(parent.toPath()));
  }
}
