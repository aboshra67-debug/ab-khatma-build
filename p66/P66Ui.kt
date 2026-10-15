package com.ab.khatma.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranAyah
import kotlinx.coroutines.delay
import java.io.File

/** P66: the ONE player look used everywhere (Mushaf, index, tasmee, hifz …): reciter (tap to change),
 * previous / play-pause / next, speed, close, and «⋯» for the sleep timer. */
@Composable
internal fun P66Bar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val item = P52SurahAudio.currentSurah ?: return
    val r = P52SurahAudio.reciter
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    if (pick) P55ReciterPicker(onDismiss = { pick = false }) {
        P52SurahAudio.selectReciter(context, it.id)
        pick = false
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFEDE8D8),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { P52SurahAudio.stop() }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.Close, "إغلاق", tint = P61Ink.copy(alpha = 0.7f))
                }
                TextButton(
                    onClick = { pick = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        r.name + " ▾", color = P61Green, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { P52SurahAudio.previous() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipNext, "السابق", tint = P61Green)
                }
                if (P52SurahAudio.isLoading) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = P61Green, strokeWidth = 2.dp)
                    }
                } else {
                    IconButton(onClick = { P52SurahAudio.togglePause() }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (P52SurahAudio.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (P52SurahAudio.isPlaying) "إيقاف مؤقت" else "تشغيل",
                            tint = P61Green, modifier = Modifier.size(32.dp),
                        )
                    }
                }
                IconButton(onClick = { P52SurahAudio.next() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "التالي", tint = P61Green)
                }
                TextButton(
                    onClick = { P52SurahAudio.setSpeed(context, p61NextSpeed(P52SurahAudio.speed)) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(p61SpeedLabel(P52SurahAudio.speed), color = P61Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.MoreHoriz, "المزيد", tint = P61Green)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (P52SurahAudio.sleepMinutes == 0) "⏲ مؤقت النوم" else "⏲ مؤقت النوم: ${P52SurahAudio.sleepMinutes} د") },
                            onClick = { P52SurahAudio.cycleSleepTimer() },
                        )
                    }
                }
            }
            Text(
                P52SurahAudio.itemTitle(item), fontSize = 11.sp, color = P61Ink.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp), textAlign = TextAlign.Center,
            )
            P52SurahAudio.message?.let {
                Text(it, color = P61Wrong, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Tasmee result: ONE card to compare — the reciter's voice and the student's own recording side by side. */
@Composable
internal fun P66ListenCard(file: File?, wasOn: Boolean, note: String?, verses: List<QuranAyah>) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    val codes = remember(verses) { verses.map { P52SurahAudio.ayahCode(it.surah, it.ayah) } }
    val cur = P52SurahAudio.currentSurah
    val playingHere = cur != null && codes.contains(cur)

    var recPlaying by remember(file?.path) { mutableStateOf(false) }
    var pos by remember(file?.path) { mutableIntStateOf(0) }
    var dur by remember(file?.path) { mutableIntStateOf(0) }
    var failed by remember(file?.path) { mutableStateOf(false) }
    val holder = remember(file?.path) { arrayOfNulls<MediaPlayer>(1) }

    fun stopRec() {
        val m = holder[0]
        holder[0] = null
        if (m != null) {
            runCatching { m.stop() }
            runCatching { m.release() }
        }
        recPlaying = false
        pos = 0
    }
    DisposableEffect(file?.path) {
        P64Bus.stopRecordingPlayback = { stopRec() }
        onDispose {
            stopRec()
            P64Bus.stopRecordingPlayback = null
        }
    }
    LaunchedEffect(recPlaying) {
        while (recPlaying) {
            pos = runCatching { holder[0]?.currentPosition ?: 0 }.getOrDefault(0)
            delay(250)
        }
    }

    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE6DCC2))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎧 قارن بالسماع", fontWeight = FontWeight.Bold, color = P61Green)
            P64ReciterButton()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        stopRec()
                        if (playingHere) P52SurahAudio.stop() else if (codes.isNotEmpty()) P52SurahAudio.play(context, codes)
                    },
                    enabled = codes.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                ) { Text(if (playingHere) "⏹ إيقاف" else "▶ اسمع القارئ", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
                Button(
                    onClick = {
                        val f = file ?: return@Button
                        if (recPlaying) {
                            stopRec()
                        } else {
                            failed = false
                            runCatching { P52SurahAudio.stop() }
                            try {
                                val m = MediaPlayer()
                                m.setDataSource(f.absolutePath)
                                m.setOnPreparedListener { p ->
                                    dur = p.duration
                                    p.start()
                                    recPlaying = true
                                }
                                m.setOnCompletionListener { stopRec() }
                                m.setOnErrorListener { _, _, _ ->
                                    stopRec()
                                    failed = true
                                    true
                                }
                                holder[0] = m
                                m.prepareAsync()
                            } catch (_: Exception) {
                                stopRec()
                                failed = true
                            }
                        }
                    },
                    enabled = file != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = P61Gold),
                ) { Text(if (recPlaying) "⏹ إيقاف" else "▶ اسمع تسجيلي", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
            }
            if (recPlaying && dur > 0) {
                Text(
                    "%d:%02d".format(pos / 60000, (pos / 1000) % 60) + " / " + "%d:%02d".format(dur / 60000, (dur / 1000) % 60),
                    fontSize = 12.sp, color = P61Ink.copy(alpha = 0.6f),
                )
                LinearProgressIndicator(progress = { if (dur > 0) pos.toFloat() / dur else 0f }, modifier = Modifier.fillMaxWidth())
            }
            if (failed) Text("تعذّر تشغيل التسجيل.", color = P61Wrong, fontSize = 12.sp)
            if (file == null) {
                Text(
                    note ?: if (wasOn) "لم يتم تسجيل صوتك هذه المرة." else "تسجيل صوتك كان مغلقًا. فعّله من زر «سجّل صوتي» قبل التسميع لو عايز تسمع نفسك.",
                    fontSize = 12.sp, color = P61Ink.copy(alpha = 0.7f), lineHeight = 19.sp,
                )
            }
            P66Bar()
        }
    }
}
