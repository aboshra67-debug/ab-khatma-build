package com.ab.khatma.guide;
/** Small platform-free decisions used by the guide and the adhan service. */
public final class P85Rules {
  private P85Rules() {}
  public static boolean showGuide(boolean restored, boolean seen, boolean signedIn) { return !restored && !seen && signedIn; }
  public static boolean keepCurrent(String active, String next, boolean preview) {
    return !preview && active != null && active.equals(next);
  }
  public static long wakeLeaseMs(long durationMs) {
    return Math.max(240000L, Math.min(900000L, Math.min(840000L, Math.max(0L, durationMs)) + 60000L));
  }
}
