package com.ab.khatma.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio

/** P68: «قراءة» mode of the Mushaf: nothing but the page. Swipe to turn pages; one touch shows a small «إنهاء» chip. */
internal object P68Reading {
    private const val PREFS = "p68_reading"
    var on by mutableStateOf(false)
    var hint by mutableStateOf(false)
    var soundOn by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        soundOn = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("flip_sound", true)
        }.getOrDefault(true)
    }

    fun toggleSound(context: Context) {
        load(context)
        soundOn = !soundOn
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("flip_sound", soundOn).apply() }
    }
}

@Composable
internal fun P68ExitReading(modifier: Modifier = Modifier, onExit: () -> Unit) {
    Surface(onClick = onExit, shape = RoundedCornerShape(50), color = P61Green.copy(alpha = 0.85f), modifier = modifier) {
        Text(
            "↩ إنهاء وضع القراءة", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** A soft paper rustle made in code (no recording, no download): short filtered noise with two small swells. */
internal object P68FlipSound {
    private const val RATE = 22050
    private var pcm: ShortArray? = null
    @Volatile private var last = 0L

    private fun build(): ShortArray {
        val n = (RATE * 0.24).toInt()
        val out = ShortArray(n)
        val rnd = java.util.Random(11)
        var lp = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val a = (1f - Math.exp(-t * 260.0).toFloat()) * Math.exp(-t * 19.0).toFloat()
            val t2 = t - 0.085f
            val b = if (t2 > 0f) 0.45f * (1f - Math.exp(-t2 * 300.0).toFloat()) * Math.exp(-t2 * 30.0).toFloat() else 0f
            val noise = rnd.nextFloat() * 2f - 1f
            lp += 0.18f * (noise - lp)
            val hp = noise - lp
            out[i] = (hp * (a + b) * 0.55f * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    fun play() {
        val now = System.currentTimeMillis()
        if (now - last < 150) return
        last = now
        Thread {
            try {
                val data = pcm ?: build().also { pcm = it }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, data.size)
                track.setVolume(if (P52SurahAudio.isPlaying) 0.25f else 0.6f)
                track.play()
                Thread.sleep(450)
                track.release()
            } catch (_: Exception) {
            }
        }.start()
    }
}
