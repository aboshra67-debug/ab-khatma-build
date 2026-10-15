package com.ab.khatma.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

// ================================================================================ tasmee voice capture ====

/**
 * P67: the student's voice is captured ONCE by the app and the same sound is handed to the speech engine
 * (Android 13+ «audio source» of the recognizer). The microphone is no longer shared between two users,
 * which is why the old recording stayed silent while the text was being written.
 */
internal class P67VoiceCapture(private val context: Context) {
    companion object {
        const val RATE = 16000
    }

    /** The speech engine can take its sound from the app (Android 13 and newer). */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var sink: ParcelFileDescriptor.AutoCloseOutputStream? = null
    private var readEnd: ParcelFileDescriptor? = null
    private var pcm: File? = null
    private var out: FileOutputStream? = null

    @Volatile var peak: Int = 0
        private set
    @Volatile var bytes: Long = 0
        private set
    val active: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        cancel()
        if (!supported) return false
        peak = 0
        bytes = 0
        return try {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return false
            val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 4, RATE))
            if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
            val dir = File(context.cacheDir, "p67_tasmee").also { it.mkdirs() }
            val f = File(dir, "last.pcm")
            if (f.exists()) f.delete()
            pcm = f
            out = FileOutputStream(f)
            record = r
            r.startRecording()
            running = true
            thread = Thread {
                val buf = ByteArray(3200) // 100 ms
                while (running) {
                    val n = r.read(buf, 0, buf.size)
                    if (n <= 0) { if (n < 0) break else continue }
                    var p = peak
                    var i = 0
                    while (i + 1 < n) {
                        val v = kotlin.math.abs(((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt())
                        if (v > p) p = v
                        i += 2
                    }
                    peak = p
                    runCatching { out?.write(buf, 0, n) }
                    bytes += n
                    val s = sink
                    if (s != null) {
                        try { s.write(buf, 0, n) } catch (_: IOException) { if (sink === s) sink = null }
                    }
                }
            }.also { it.start() }
            true
        } catch (_: Exception) {
            cancel()
            false
        }
    }

    /** A fresh pipe for one listening session of the speech engine (the old one is closed). */
    fun newSource(): ParcelFileDescriptor? {
        if (!running) return null
        closeSource()
        return runCatching {
            val p = ParcelFileDescriptor.createPipe()
            sink = ParcelFileDescriptor.AutoCloseOutputStream(p[1])
            readEnd = p[0]
            p[0]
        }.getOrNull()
    }

    private fun closeSource() {
        val s = sink
        sink = null
        runCatching { s?.close() }
        runCatching { readEnd?.close() }
        readEnd = null
    }

    /** The recognizer intent that reads the app's own capture instead of opening the microphone. */
    fun intentFor(base: Intent, source: ParcelFileDescriptor): Intent = Intent(base).apply {
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
    }

    private fun halt() {
        running = false
        closeSource()
        runCatching { thread?.join(600) }
        thread = null
        val r = record
        record = null
        runCatching { r?.stop() }
        runCatching { r?.release() }
        runCatching { out?.close() }
        out = null
    }

    /** Stops and returns the recording as a WAV file, or null when nothing audible was captured. */
    fun stop(): File? {
        if (record == null && !running) return null
        halt()
        val f = pcm ?: return null
        if (bytes < RATE || peak < 600) return null // under half a second, or silence
        return runCatching {
            val wav = File(f.parentFile, "last.wav")
            if (wav.exists()) wav.delete()
            RandomAccessFile(wav, "rw").use { w ->
                val len = f.length()
                fun i32(v: Long) { w.write((v and 0xFF).toInt()); w.write(((v shr 8) and 0xFF).toInt()); w.write(((v shr 16) and 0xFF).toInt()); w.write(((v shr 24) and 0xFF).toInt()) }
                fun i16(v: Int) { w.write(v and 0xFF); w.write((v shr 8) and 0xFF) }
                w.writeBytes("RIFF"); i32(36 + len); w.writeBytes("WAVEfmt "); i32(16); i16(1); i16(1)
                i32(RATE.toLong()); i32(RATE * 2L); i16(2); i16(16); w.writeBytes("data"); i32(len)
                f.inputStream().use { input ->
                    val b = ByteArray(64 * 1024)
                    while (true) { val n = input.read(b); if (n <= 0) break; w.write(b, 0, n) }
                }
            }
            f.delete()
            wav
        }.getOrNull()
    }

    fun cancel() {
        halt()
        runCatching { pcm?.delete() }
    }
}

// ======================================================================== full-screen Mushaf controls ====

private val P67Green = Color(0xFF064D3B)
private val P67Gold = Color(0xFFDFBD78)

/**
 * P67: the full-screen controls sit in their own strip UNDER the page (and the player above that strip),
 * so nothing covers the Quran text any more: next ‹ · zoom · speaker · › previous.
 */
@Composable
internal fun P67FullBottom(
    night: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    zoomed: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleZoom: () -> Unit,
    playerOpen: Boolean,
    onTogglePlayer: () -> Unit,
    player: @Composable () -> Unit,
) {
    val pill = if (night) Color(0xFF12332A) else Color(0xFFEDE8D8)
    val ink = if (night) Color(0xFFE9DDB8) else P67Green
    Column(Modifier.fillMaxWidth()) {
        if (playerOpen) Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) { player() }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                P67Pill("‹  التالي", ink, pill, hasNext, onNext)
                P67Pill(if (zoomed) "↩ حجم عادي" else "🔍 تكبير", ink, pill, true, onToggleZoom)
                Surface(
                    onClick = onTogglePlayer,
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = if (playerOpen || P52SurahAudio.isPlaying) P67Green else P67Green.copy(alpha = 0.85f),
                    border = BorderStroke(2.dp, P67Gold),
                    shadowElevation = 3.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (P52SurahAudio.isPlaying) "🔊" else "🔈", fontSize = 24.sp)
                    }
                }
                P67Pill("السابق  ›", ink, pill, hasPrev, onPrev)
            }
        }
    }
}

@Composable
private fun P67Pill(text: String, ink: Color, bg: Color, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, ink.copy(alpha = 0.25f)),
        modifier = Modifier.height(40.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (enabled) ink else ink.copy(alpha = 0.35f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
