package com.ab.khatma.guide
import android.content.Context
import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ab.khatma.prayer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P85AudioDeviceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun downloadedMinshawiCompletesWithScreenOffAndDuplicateAlarm() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        compose.setContent { Text("P85 isolated audio device test") }
        val old=AdhanPreferences(context).voiceId
        runBlocking { P85NamedAdhan.prepare(context,"minshawi") }
        AdhanPreferences(context).voiceId="minshawi"
        fun start() { ContextCompat.startForegroundService(context,Intent(context,AdhanPlaybackService::class.java)
            .setAction(AdhanPlaybackService.ACTION_START).putExtra(AdhanPlaybackService.EXTRA_PRAYER,"الفجر")
            .putExtra(AdhanAlarmReceiver.EXTRA_EPOCH,85001L)) }
        try {
            compose.runOnIdle { start() }
            compose.waitUntil(20000) { AdhanPlaybackStatus.position>3000 }
            val before=AdhanPlaybackStatus.position
            compose.runOnIdle { start() }
            Thread.sleep(700)
            assertTrue("duplicate start reset position",AdhanPlaybackStatus.position>=before)
            assertTrue("full named recording",AdhanPlaybackStatus.duration>250000)
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 223").close()
            val deadline=System.currentTimeMillis()+300000
            while(AdhanPlaybackStatus.active&&System.currentTimeMillis()<deadline) Thread.sleep(1000)
            assertFalse("foreground audio did not finish",AdhanPlaybackStatus.active)
            assertEquals("اكتمل الأذان",AdhanPlaybackStatus.text)
            assertTrue(AdhanPlaybackStatus.position>=AdhanPlaybackStatus.duration-1000)
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 224").close()
            context.startService(Intent(context,AdhanPlaybackService::class.java).setAction(AdhanPlaybackService.ACTION_STOP))
            AdhanPreferences(context).voiceId=old
        }
    }
}
