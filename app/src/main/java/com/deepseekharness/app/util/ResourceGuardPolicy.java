package com.deepseekharness.app.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源保护的判断规则（纯逻辑）：Linux 运行时每秒取一次读数，任一条件成立就要停掉全部 Linux 进程，
 * 防止恶意或失控的脚本写满手机存储、吃光内存或无限开进程。
 */
public final class ResourceGuardPolicy {
  /** 手机剩余存储低于此值就停（按每秒写入约 1 GB 留足余量）。 */
  public static final long MIN_FREE_BYTES = 2L << 30;

  /** 手机可用内存低于此值、且连续 {@link #MEMORY_STRIKES} 次，才停；短暂波动不算。 */
  public static final long MIN_MEMORY_BYTES = 256L << 20;

  public static final int MEMORY_STRIKES = 2;

  /** 轮询兜底：Linux 进程数超过此值就停。 */
  public static final int MAX_PROCESSES = 2048;

  /**
   * 内核硬上限（RLIMIT_NPROC，软硬相同）：统计的是本应用名下全部线程，含 App 自身。
   * 达到后内核直接拒绝再开新进程，Linux 里的程序自己调不高。
   */
  public static final int TASK_LIMIT = 4096;

  public enum Reason {
    STORAGE,
    MEMORY,
    PROCESSES
  }

  private int lowMemory;

  /** 输入一次读数；返回应停止的原因，正常返回 null。 */
  public Reason check(long freeBytes, long availableMemoryBytes, int processes) {
    lowMemory = availableMemoryBytes < MIN_MEMORY_BYTES ? lowMemory + 1 : 0;
    if (freeBytes < MIN_FREE_BYTES) return Reason.STORAGE;
    if (processes > MAX_PROCESSES) return Reason.PROCESSES;
    if (lowMemory >= MEMORY_STRIKES) return Reason.MEMORY;
    return null;
  }

  /**
   * 在 Linux 里的第一条命令前设好进程硬上限，再原样 exec 原命令（进程号与身份不变）。
   * 设置失败只提示不阻止启动，轮询兜底仍然生效。
   */
  public static List<String> limited(List<String> guestCommand) {
    if (guestCommand.isEmpty()) throw new IllegalArgumentException("GUEST_COMMAND_EMPTY");
    List<String> argv = new ArrayList<>(guestCommand.size() + 4);
    argv.add("/bin/bash");
    argv.add("-c");
    argv.add(
        "ulimit -u "
            + TASK_LIMIT
            + " 2>/dev/null || echo 'DSHA: process limit unavailable' >&2; exec \"$@\"");
    argv.add("dsha-guard");
    argv.addAll(guestCommand);
    return argv;
  }
}
