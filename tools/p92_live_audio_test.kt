package com.ab.khatma.guide

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ab.khatma.media.P91OfflineAudio
import com.ab.khatma.prayer.P85NamedAdhan
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real network-source integration gates: failures must block final approval. */
@RunWith(AndroidJUnit4::class)
class P92LiveAudioDeviceTest {
    private val archiveUrl = "https://archive.org/download/07._20210119/03.%D8%AD%D9%8A%D9%86%20%D9%8A%D9%87%D8%AF%D9%8A%20%D8%A7%D9%84%D8%B5%D8%A8%D8%AD%20%D8%A7%D8%B4%D8%B1%D8%A7%D9%82%20%D8%B3%D9%86%D8%A7%D9%87.mp3"

    private fun assertMp3Decodes(path: String) {
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            assertTrue("Downloaded audio is not playable", (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)>10_000L)
        } finally { retriever.release() }
    }

    @Test fun actualIbtehalDownloadsAndDecodes() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val file=runBlocking { withTimeout(120_000L) { P91OfflineAudio.acquire(ctx,archiveUrl) } }
        assertTrue(file.isFile && file.length()>1_024)
        assertMp3Decodes(file.absolutePath)
    }

    @Test fun refaatAdhanDownloadsAndDecodes() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val file=runBlocking { withTimeout(120_000L) { P85NamedAdhan.prepare(ctx,"refaat_radio") } }
        assertTrue(file.isFile && file.length()==407680L)
        assertMp3Decodes(file.absolutePath)
    }
}
