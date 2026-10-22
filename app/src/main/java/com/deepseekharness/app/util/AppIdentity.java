package com.deepseekharness.app.util;

/** Product identity for the production application. */
public final class AppIdentity {
  private AppIdentity() {}

  public static String applicationId() {
    return "com.hydsh.app";
  }

  public static String displayName() {
    return "HYDSH";
  }

  public static String publicDirectoryName() {
    return "HYDSH";
  }

  public static String defaultUpdateFeed() {
    return "";
  }

  public static boolean isExperimental() {
    return false;
  }
}
