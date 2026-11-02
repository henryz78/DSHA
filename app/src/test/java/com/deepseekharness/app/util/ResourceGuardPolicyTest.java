package com.deepseekharness.app.util;

import static com.deepseekharness.app.util.ResourceGuardPolicy.*;
import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public final class ResourceGuardPolicyTest {
  private static final long PLENTY = 50L << 30, MEMORY = 4L << 30;

  @Test
  public void normalReadingsKeepRunning() {
    ResourceGuardPolicy policy = new ResourceGuardPolicy();
    for (int i = 0; i < 10; i++) assertNull(policy.check(PLENTY, MEMORY, 40));
    assertNull(policy.check(MIN_FREE_BYTES, MIN_MEMORY_BYTES, MAX_PROCESSES));
  }

  @Test
  public void lowStorageStopsImmediately() {
    assertEquals(Reason.STORAGE, new ResourceGuardPolicy().check(MIN_FREE_BYTES - 1, MEMORY, 40));
  }

  @Test
  public void tooManyProcessesStopsImmediately() {
    assertEquals(Reason.PROCESSES, new ResourceGuardPolicy().check(PLENTY, MEMORY, MAX_PROCESSES + 1));
  }

  @Test
  public void lowMemoryNeedsConsecutiveReadings() {
    ResourceGuardPolicy policy = new ResourceGuardPolicy();
    long low = MIN_MEMORY_BYTES - 1;
    assertNull(policy.check(PLENTY, low, 40));
    assertNull(policy.check(PLENTY, MEMORY, 40)); // 波动恢复，计数清零
    assertNull(policy.check(PLENTY, low, 40));
    assertEquals(Reason.MEMORY, policy.check(PLENTY, low, 40));
  }

  @Test
  public void storageOutranksMemory() {
    ResourceGuardPolicy policy = new ResourceGuardPolicy();
    policy.check(PLENTY, 0, 40);
    assertEquals(Reason.STORAGE, policy.check(0, 0, MAX_PROCESSES + 1));
  }

  @Test
  public void limitWrapsCommandWithoutChangingIt() {
    List<String> argv = limited(List.of("/bin/bash", "-c", "echo $$ > /root/x; exec node"));
    assertEquals("/bin/bash", argv.get(0));
    assertTrue(argv.get(2).startsWith("ulimit -u " + TASK_LIMIT + " "));
    assertTrue(argv.get(2).endsWith("exec \"$@\""));
    assertEquals(List.of("/bin/bash", "-c", "echo $$ > /root/x; exec node"), argv.subList(4, 7));
    assertTrue(TASK_LIMIT > MAX_PROCESSES);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyCommandRejected() {
    limited(List.of());
  }
}
