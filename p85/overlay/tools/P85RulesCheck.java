import com.ab.khatma.guide.P85Rules;
public final class P85RulesCheck {
  static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
  public static void main(String[] args) {
    check(P85Rules.showGuide(false, false, true), "new successful sign-in must open guide");
    check(!P85Rules.showGuide(true, false, true), "restored P84 session must not be interrupted");
    check(!P85Rules.showGuide(false, true, true), "completed or skipped guide stays completed");
    check(!P85Rules.showGuide(false, false, false), "opening app without sign-in is not registration");
    check(P85Rules.keepCurrent("الفجر:123:default", "الفجر:123:default", false), "duplicate alarm must not restart adhan");
    check(!P85Rules.keepCurrent("الفجر:123:default", "الفجر:124:default", false), "next alarm must play");
    check(!P85Rules.keepCurrent("الفجر:123:default", "الفجر:123:default", true), "explicit preview may restart");
    check(P85Rules.wakeLeaseMs(197091) >= 227091, "wake lease covers whole file and margin");
    check(P85Rules.wakeLeaseMs(420000) >= 450000, "long adhan must not inherit four minute cap");
    check(P85Rules.wakeLeaseMs(-1) > 0, "unknown duration has finite protection");
    System.out.println("P85 behavior checks: 10 PASS");
  }
}
