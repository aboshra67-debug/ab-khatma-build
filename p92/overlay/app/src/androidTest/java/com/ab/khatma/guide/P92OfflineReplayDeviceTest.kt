package com.ab.khatma.guide

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ab.khatma.media.P91OfflineAudio
import com.ab.khatma.prayer.P85NamedAdhan
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Real emulator network tests, not a mock. CI runs the offline phase in airplane mode
 * after explicitly force-stopping the app. All recordings remain within app-private files.
 */
@RunWith(AndroidJUnit4::class)
class P92OfflineReplayDeviceTest {
    private val quran = "https://cdn.mp3quran.net/audio/muhammad-refaat/r1/001.mp3"
    private val ibtehal = "https://archive.org/download/07._20210119/03.%D8%AD%D9%8A%D9%86%20%D9%8A%D9%87%D8%AF%D9%8A%20%D8%A7%D9%84%D8%B5%D8%A8%D8%AD%20%D8%A7%D8%B4%D8%B1%D8%A7%D9%82%20%D8%B3%D9%86%D8%A7%D9%87.mp3"
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = ctx.getSharedPreferences("p92_offline_test", Context.MODE_PRIVATE)
    private fun hash(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val b = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(b)
                if (n == -1) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    private fun verifyPlayable(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            assertTrue("Saved MP3 must decode as audio", duration > 5_000L)
        } finally { retriever.release() }
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            assertTrue("Local playback has real duration", player.duration > 5_000)
            player.start()
            assertTrue("MediaPlayer must start local MP3", player.isPlaying)
            player.pause()
        } finally { player.release() }
    }

    @Test fun downloadQuranIbtehalAndAdhanOnline() {
        for ((id, url) in listOf("quran" to quran, "ibtehal" to ibtehal)) {
            val file = runBlocking { withTimeout(160_000L) { P91OfflineAudio.acquire(ctx, url) } }
            assertTrue("Downloaded audio should not be empty: $id", file.length() > 1_024)
            assertNotNull(P91OfflineAudio.saved(ctx, url))
            verifyPlayable(file)
            assertTrue(prefs.edit().putString(id, hash(file)).commit())
        }
        val adhan = runBlocking { withTimeout(110_000L) { P85NamedAdhan.prepare(ctx, "refaat_radio") } }
        verifyPlayable(adhan)
        assertNotNull(P85NamedAdhan.file(ctx, "refaat_radio"))
        assertTrue(prefs.edit().putString("adhan", hash(adhan)).commit())
    }

    @Test fun invalidHeaderIsNeverAdvertisedAsSaved() {
        val url = "https://cdn.mp3quran.net/p92-corrupted-file-check.mp3"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val testFile = File(ctx.filesDir, "p91_saved_audio/$digest.mp3")
        testFile.parentFile?.mkdirs()
        try {
            testFile.writeBytes(ByteArray(3_072) { 0x3A.toByte() })
            assertNull("Malformed MP3 is not a saved recording", P91OfflineAudio.saved(ctx, url))
        } finally {
            // Delete ONLY the temporary file created by this test, never a user recording.
            assertTrue(testFile.delete())
        }
    }

    @Test fun filesSurviveProcessRestartAndReplayInAirplaneMode() {
        val manager = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val connected = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        assertFalse("CI must disable network before offline replay",
            connected?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        for ((id, url) in listOf("quran" to quran, "ibtehal" to ibtehal)) {
            val before = prefs.getString(id, null)
            assertNotNull("Online phase must run first for $id", before)
            val file = P91OfflineAudio.saved(ctx, url)
            assertNotNull("Saved file must persist without network for $id", file)
            assertEquals(before, hash(file!!))
            val fast = runBlocking { withTimeout(2_000L) { P91OfflineAudio.acquire(ctx, url) } }
            assertEquals(file.absolutePath, fast.absolutePath)
            verifyPlayable(fast)
        }
        val adhan = P85NamedAdhan.file(ctx, "refaat_radio")
        assertNotNull("Named adhan must remain after restart", adhan)
        assertEquals(prefs.getString("adhan", null), hash(adhan!!))
        verifyPlayable(adhan)
    }
}
