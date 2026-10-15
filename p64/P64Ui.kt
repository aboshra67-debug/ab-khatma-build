package com.ab.khatma.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranAyah
import com.ab.khatma.quran.QuranRepository
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.io.File

// ------------------------------------------------------------------------------------ hizb ----

/** Verses of a hizb (1..60) or of one of its four quarters, from the 240 quarter starts bundled in P63. */
internal object P64Hizb {
    private var quarters: IntArray? = null

    @Synchronized
    private fun load(context: Context): IntArray {
        quarters?.let { return it }
        val loaded = runCatching {
            val raw = context.applicationContext.assets.open("quran/p63_hizb.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(raw)
            IntArray(arr.length()) { arr.getInt(it) }
        }.getOrDefault(IntArray(0))
        quarters = loaded
        return loaded
    }

    /** [quarter] = 0 for the whole hizb, 1..4 for one quarter. */
    fun verses(context: Context, repo: QuranRepository, hizb: Int, quarter: Int): List<QuranAyah> {
        val q = load(context)
        if (q.size < 240) return emptyList()
        val h = hizb.coerceIn(1, 60)
        val oneQuarter = quarter in 1..4
        val startIdx = (h - 1) * 4 + (if (oneQuarter) quarter - 1 else 0)
        val endIdx = if (oneQuarter) startIdx + 1 else (h - 1) * 4 + 4
        val a = q[startIdx]
        val b = if (endIdx >= q.size) Int.MAX_VALUE else q[endIdx]
        val lastSurah = if (b == Int.MAX_VALUE) 114 else b / 1000
        return ((a / 1000)..lastSurah).flatMap { s -> repo.loadSurah(s) }.filter { v ->
            val c = v.surah * 1000 + v.ayah
            c >= a && c < b
        }
    }
}

// ------------------------------------------------------------------------------- reciter button ----

/** One clear line «🎧 القارئ: … ▾» that opens the reciter list. */
@Composable
internal fun P64ReciterButton(modifier: Modifier = Modifier.fillMaxWidth()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    var open by remember { mutableStateOf(false) }
    if (open) P55ReciterPicker(onDismiss = { open = false }) {
        P52SurahAudio.selectReciter(context, it.id)
        open = false
    }
    val r = P52SurahAudio.savedReciter
    Surface(
        onClick = { open = true },
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEAF2EB),
        border = BorderStroke(1.dp, Color(0xFFBAD5C5)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🎧 القارئ: " + r.name + (if (r.style.isNotBlank()) " (${r.style})" else ""),
                    color = P61Green, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text("تغيير ▾", color = P61Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (r.ayahFolder == null) {
                Text(
                    "هذا القارئ للسورة كاملة فقط؛ الآيات المنفردة بصوت الحصري.",
                    fontSize = 11.sp, color = P61Ink.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ------------------------------------------------------------------------ tasmee: voice recorder ----

/**
 * Records the voice while the phone writes what it hears. Some phones do not let two apps listen to the microphone at
 * once; then nothing is stored (the recording stays silent) and the tasmee keeps working as before.
 */
internal class P64TasmeeRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    var peak: Int = 0
        private set
    val active: Boolean get() = recorder != null

    @Suppress("DEPRECATION")
    private fun create(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()

    fun start(): Boolean {
        cancel()
        peak = 0
        return try {
            val dir = File(context.cacheDir, "p64_tasmee")
            dir.mkdirs()
            val f = File(dir, "last.m4a")
            if (f.exists()) f.delete()
            val r = create()
            recorder = r
            file = f
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(16000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            true
        } catch (_: Exception) {
            val r = recorder
            recorder = null
            file = null
            runCatching { r?.release() }
            false
        }
    }

    fun sample() {
        val r = recorder ?: return
        val a = runCatching { r.maxAmplitude }.getOrDefault(0)
        if (a > peak) peak = a
    }

    /** The finished recording, or null when nothing audible was captured. */
    fun stop(): File? {
        val r = recorder ?: return null
        sample()
        recorder = null
        val f = file
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        return if (ok && f != null && f.exists() && f.length() > 2048L && peak > 200) f else null
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
    }
}

internal object P64Bus {
    var stopRecordingPlayback: (() -> Unit)? = null
}

/** The recording of the student's own voice, played next to the coloured result text. */
@Composable
internal fun P64RecordingCard(file: File?, wasOn: Boolean, note: String?) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE6DCC2))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎙 تسجيل صوتك", fontWeight = FontWeight.Bold, color = P61Green)
            if (file != null) {
                P64RecordingPlayer(file)
                Text(
                    "التسجيل محفوظ على موبايلك فقط، ويُستبدل عند التسميع التالي.",
                    fontSize = 11.sp, color = P61Ink.copy(alpha = 0.55f),
                )
            } else if (note != null) {
                Text(note, fontSize = 12.sp, color = P61Ink.copy(alpha = 0.7f), lineHeight = 19.sp)
            } else if (wasOn) {
                Text(
                    "لم يتم تسجيل صوت: هذا الموبايل لا يسمح بالتسجيل أثناء التعرف على الكلام. التسميع والمراجعة يعملان كالمعتاد.",
                    fontSize = 12.sp, color = P61Ink.copy(alpha = 0.7f), lineHeight = 19.sp,
                )
            } else {
                Text("تسجيل الصوت كان مغلقًا في هذه المرة.", fontSize = 12.sp, color = P61Ink.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun P64RecordingPlayer(file: File) {
    var playing by remember(file.path) { mutableStateOf(false) }
    var pos by remember(file.path) { mutableIntStateOf(0) }
    var dur by remember(file.path) { mutableIntStateOf(0) }
    var failed by remember(file.path) { mutableStateOf(false) }
    val holder = remember(file.path) { arrayOfNulls<MediaPlayer>(1) }

    fun stopNow() {
        val m = holder[0]
        holder[0] = null
        if (m != null) {
            runCatching { m.stop() }
            runCatching { m.release() }
        }
        playing = false
        pos = 0
    }

    DisposableEffect(file.path) {
        P64Bus.stopRecordingPlayback = { stopNow() }
        onDispose {
            stopNow()
            P64Bus.stopRecordingPlayback = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            pos = runCatching { holder[0]?.currentPosition ?: 0 }.getOrDefault(0)
            delay(250)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                if (playing) {
                    stopNow()
                } else {
                    failed = false
                    runCatching { P52SurahAudio.stop() }
                    try {
                        val m = MediaPlayer()
                        m.setDataSource(file.absolutePath)
                        m.setOnPreparedListener { p ->
                            dur = p.duration
                            p.start()
                            playing = true
                        }
                        m.setOnCompletionListener { stopNow() }
                        m.setOnErrorListener { _, _, _ ->
                            stopNow()
                            failed = true
                            true
                        }
                        holder[0] = m
                        m.prepareAsync()
                    } catch (_: Exception) {
                        stopNow()
                        failed = true
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = P61Green),
        ) { Text(if (playing) "⏹ إيقاف" else "▶ اسمع تسجيلي", fontWeight = FontWeight.Bold) }
        if (dur > 0) {
            Text(
                "%d:%02d".format(pos / 60000, (pos / 1000) % 60) + " / " + "%d:%02d".format(dur / 60000, (dur / 1000) % 60),
                fontSize = 12.sp, color = P61Ink.copy(alpha = 0.6f),
            )
        }
    }
    if (dur > 0) LinearProgressIndicator(progress = { if (dur > 0) pos.toFloat() / dur else 0f }, modifier = Modifier.fillMaxWidth())
    if (failed) Text("تعذّر تشغيل التسجيل.", color = P61Wrong, fontSize = 12.sp)
}

// --------------------------------------------------------------------- tasmee: listen to the reciter ----

/** Plays the very passage that was recited, in the chosen reciter's voice, for comparison. */
@Composable
internal fun P64ReciterListenCard(verses: List<QuranAyah>) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    val codes = remember(verses) { verses.map { P52SurahAudio.ayahCode(it.surah, it.ayah) } }
    val cur = P52SurahAudio.currentSurah
    val playingHere = cur != null && codes.contains(cur)
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE6DCC2))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎧 اسمع المقطع بصوت القارئ", fontWeight = FontWeight.Bold, color = P61Green)
            P64ReciterButton()
            Button(
                onClick = {
                    P64Bus.stopRecordingPlayback?.invoke()
                    if (playingHere) P52SurahAudio.stop() else if (codes.isNotEmpty()) P52SurahAudio.play(context, codes)
                },
                enabled = codes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = P61Green),
            ) { Text(if (playingHere) "⏹ إيقاف" else "▶ اسمع القارئ", fontWeight = FontWeight.Bold) }
            P52MiniPlayer()
        }
    }
}
