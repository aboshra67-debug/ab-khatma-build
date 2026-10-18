package com.ab.khatma.guide

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ab.khatma.guide.P85Guide.target
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P85GuideUiTest {
    @get:Rule val compose=createComposeRule()
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val context=ApplicationProvider.getApplicationContext<Context>()
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        context.openFileOutput(name,Context.MODE_PRIVATE).use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun host() {
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                listOf("mushaf","khatmas","meetup","prayer","adhkar","profile").forEach {
                    Text("target-$it",Modifier.fillMaxWidth().height(160.dp).target("home.$it"))
                }
            }
            P85Guide.Host()
        } }
    }
    @Test fun nextPreviousSkipAndManualRestartWork() {
        host()
        compose.runOnIdle { P85Guide.start("home") }
        compose.onNodeWithText("المصحف",useUnmergedTree=true).assertIsDisplayed()
        screenshot("p85-guide-first.png")
        compose.onNodeWithText("التالي").performClick()
        compose.onNodeWithText("الختمات ووردك",useUnmergedTree=true).assertIsDisplayed()
        screenshot("p85-guide-next.png")
        compose.onNodeWithText("السابق").performClick()
        compose.onNodeWithText("المصحف",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("تخطي").performClick()
        compose.onNodeWithText("التالي").assertDoesNotExist()
        compose.runOnIdle { P85Guide.openMenu() }
        compose.onNodeWithText("دليل استخدام ختمة").assertIsDisplayed()
        compose.onNodeWithText("المصحف والاستماع").performClick()
        compose.onNodeWithText("الفهرس").assertIsDisplayed()
        compose.onNodeWithText("تخطي").performClick()
    }
    @Test fun returningAccountNeverGetsAutomaticGuideAgain() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val id=90000000085L
        context.getSharedPreferences("p85_guide",Context.MODE_PRIVATE).edit().remove("seen_$id").commit()
        host()
        compose.runOnIdle { P85Guide.signedIn(context,id) }
        compose.onNodeWithText("المصحف",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("تخطي").performClick()
        compose.runOnIdle { P85Guide.signedIn(context,id) }
        compose.onNodeWithText("التالي").assertDoesNotExist()
        context.getSharedPreferences("p85_guide",Context.MODE_PRIVATE).edit().remove("seen_$id").commit()
    }
}
