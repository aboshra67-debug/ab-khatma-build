package com.ab.khatma.guide

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ab.khatma.secure.P93FirstRunCard
import com.ab.khatma.secure.P93FirstRunStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P93FirstRunDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyNewlyRegisteredAccountsAreEnrolledAndStatePersists() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val first = 90000000117L
        val another = 90000000118L
        P93FirstRunStore.finish(c, first)
        P93FirstRunStore.finish(c, another)
        try {
            assertFalse(P93FirstRunStore.pending(c, first))
            assertFalse(P93FirstRunStore.pending(c, another))
            P93FirstRunStore.begin(c, first)
            assertTrue(P93FirstRunStore.pending(c, first))
            assertFalse("Unrelated or returning account must not get wizard", P93FirstRunStore.pending(c, another))
            assertEquals(0, P93FirstRunStore.step(c, first))
            P93FirstRunStore.saveStep(c, first, 3)
            assertEquals(3, P93FirstRunStore.step(c, first))
            P93FirstRunStore.finish(c, first)
            assertFalse(P93FirstRunStore.pending(c, first))
        } finally {
            P93FirstRunStore.finish(c, first)
            P93FirstRunStore.finish(c, another)
        }
    }

    @Test fun guidedSettingsHaveRealActionsNavigationAndSkip() {
        var opened = ""
        compose.setContent {
            var step by remember { mutableIntStateOf(0) }
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (visible) P93FirstRunCard(
                    userId = 90000000117L,
                    step = step,
                    onPrevious = { step-- },
                    onNext = { if (step == 4) visible = false else step++ },
                    onSkip = { visible = false },
                    onOpenReciter = { opened = "reciter" },
                    onOpenNotifications = { opened = "notifications" },
                    onOpenPrayer = { opened = "prayer" },
                    onOpenGuide = { opened = "guide" }
                )
            }
        }
        compose.onNodeWithText("أهلًا بك في ختمة").assertIsDisplayed()
        compose.onNodeWithText("التالي").performClick()
        compose.onNodeWithText("اختيار القارئ").performClick()
        compose.runOnIdle { assertEquals("reciter", opened) }
        compose.onNodeWithText("التالي").performClick()
        compose.onNodeWithText("فتح إعدادات التنبيهات").performClick()
        compose.runOnIdle { assertEquals("notifications", opened) }
        compose.onNodeWithText("التالي").performClick()
        compose.onNodeWithText("فتح إعدادات الصلاة والأذان").performClick()
        compose.runOnIdle { assertEquals("prayer", opened) }
        compose.onNodeWithText("التالي").performClick()
        compose.onNodeWithText("ابدأ الجولة التفاعلية").performClick()
        compose.runOnIdle { assertEquals("guide", opened) }
        compose.onNodeWithText("إنهاء الإعداد").performClick()
        compose.onNodeWithText("خطوات البداية", substring = true).assertDoesNotExist()
    }
}
