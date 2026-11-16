package com.ab.khatma.ui.screens

import android.content.Context
import android.media.MediaPlayer
import com.ab.khatma.R

/** Genuine QuranHive reciter word audio (CC BY 4.0), not TTS or a teacher lesson. */
internal data class P125WordSample(val key: String, val label: String, val rawId: Int)
internal object P125WordAudio {
    val bismillah = listOf(
        P125WordSample("001_001_001", "بِسْمِ", R.raw.p125_001_001_001),
        P125WordSample("001_001_002", "اللَّهِ", R.raw.p125_001_001_002),
        P125WordSample("001_001_003", "الرَّحْمَٰنِ", R.raw.p125_001_001_003),
        P125WordSample("001_001_004", "الرَّحِيمِ", R.raw.p125_001_001_004)
    )
}
internal class P125DirectAudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    fun stop() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }
    fun play(sample: P125WordSample, onStatus: (String) -> Unit) {
        stop()
        val fresh = MediaPlayer.create(context, sample.rawId)
            ?: error("تعذّر فتح التسجيل الصوتي")
        try {
            player = fresh
            fresh.setOnCompletionListener {
                if (player === fresh) {
                    player = null
                    runCatching { fresh.release() }
                }
            }
            fresh.start()
            onStatus("يُشغَّل الآن: ${sample.label} — تسجيل قارئ أصلي")
        } catch (error: Exception) {
            if (player === fresh) player = null
            runCatching { fresh.release() }
            throw error
        }
    }
}
