package com.ab.khatma.guide

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ab.khatma.ui.screens.P85IbtehalScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P88IbtehalUiTest {
    @get:Rule val compose = createComposeRule()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context=ApplicationProvider.getApplicationContext<Context>()
        val image=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        context.openFileOutput(name,Context.MODE_PRIVATE).use {
            image.compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun showsSectionsAndDirectListenInsideChosenSection() {
        compose.setContent { MaterialTheme { P85IbtehalScreen(onBack={}) } }
        compose.onNodeWithText("الأقسام").assertExists()
        screenshot("p88-ibtehal-categories.png")
        compose.onNodeWithText("نصر الدين طوبار").performClick()
        compose.onNodeWithText("هو الله رب الوجود").assertExists()
        compose.onNodeWithText("▶ استماع داخل التطبيق").assertExists()
        screenshot("p88-ibtehal-tobar.png")
        compose.onNodeWithText("‹ الرجوع إلى أقسام الابتهالات").performClick()
        compose.onNodeWithText("الأقسام").assertExists()
    }
}
