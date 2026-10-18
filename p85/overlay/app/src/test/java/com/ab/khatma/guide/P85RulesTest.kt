package com.ab.khatma.guide
import org.junit.Assert.*
import org.junit.Test
class P85RulesTest {
    @Test fun newSignInShowsGuideButRestorationDoesNot() {
        assertTrue(P85Rules.showGuide(false,false,true))
        assertFalse(P85Rules.showGuide(true,false,true))
        assertFalse(P85Rules.showGuide(false,true,true))
        assertFalse(P85Rules.showGuide(false,false,false))
    }
    @Test fun duplicateAlarmPreservesCurrentPlayback() {
        assertTrue(P85Rules.keepCurrent("fajr:12:default","fajr:12:default",false))
        assertFalse(P85Rules.keepCurrent("fajr:12:default","fajr:13:default",false))
        assertFalse(P85Rules.keepCurrent("fajr:12:default","fajr:12:default",true))
        assertFalse(P85Rules.keepCurrent(null,"fajr:12:default",false))
    }
    @Test fun longRecordingsHaveDurationBasedWakeLease() {
        assertTrue(P85Rules.wakeLeaseMs(251632)>251632)
        assertTrue(P85Rules.wakeLeaseMs(420000)>=450000)
        assertTrue(P85Rules.wakeLeaseMs(-1)>0)
        assertTrue(P85Rules.wakeLeaseMs(Long.MAX_VALUE)>0)
    }
}
