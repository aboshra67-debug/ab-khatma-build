package com.ab.khatma.guide
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ab.khatma.ui.screens.P41OfflineMushafPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class P85ReaderDeviceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun originalPageAndReadingModeRenderWithFrames() {
        var page by mutableIntStateOf(187)
        var reading by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            P41OfflineMushafPage(pageNumber=page,colored=true,zoom=1f,onZoomChange={},onToggleControls={},readingMode=reading)
        } }
        fun waitForPage(p:Int) {
            compose.waitUntil(30000) { compose.onAllNodesWithContentDescription("مصحف المدينة المنورة، صفحة $p").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("مصحف المدينة المنورة، صفحة $p").assertIsDisplayed()
        }
        fun shot(name:String) {
            compose.waitForIdle()
            val context=ApplicationProvider.getApplicationContext<Context>()
            val image=compose.onRoot().captureToImage().asAndroidBitmap()
            context.openFileOutput(name,Context.MODE_PRIVATE).use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        waitForPage(187);shot("p85-reader-187.png")
        compose.runOnIdle { reading=true }
        shot("p85-reader-187-reading.png")
        compose.runOnIdle { reading=false;page=604 }
        waitForPage(604);shot("p85-reader-604.png")
    }
}
