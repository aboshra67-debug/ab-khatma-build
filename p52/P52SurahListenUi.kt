package com.ab.khatma.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ab.khatma.quran.P52Reciter
import com.ab.khatma.quran.P52Reciters
import com.ab.khatma.quran.P52SurahAudio

private val P52Green = Color(0xFF064D3B)
private val P52Gold = Color(0xFFB48B38)
private val P52Line = Color(0xFFE9DDC6)

/** P52: reciter row + "select several surahs" switch shown above the surah list. */
@Composable
internal fun P52ListenToolbar(
    selecting: Boolean,
    selectedCount: Int,
    onToggleSelecting: () -> Unit,
    onPlaySelected: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        P52ReciterPicker(onDismiss = { showPicker = false }) { chosen ->
            P52SurahAudio.selectReciter(context, chosen.id)
            showPicker = false
        }
    }
    val r = P52SurahAudio.reciter
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = { showPicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp),
                color = Color.White,
                border = BorderStroke(1.dp, P52Line),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolumeUp, null, tint = P52Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("القارئ: " + r.name + (if (r.style.isNotBlank()) " (${r.style})" else ""),
                        color = P52Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("تغيير ▾", color = P52Gold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = onToggleSelecting,
                shape = RoundedCornerShape(13.dp),
                color = if (selecting) P52Green else Color.White,
                border = BorderStroke(1.dp, if (selecting) P52Green else P52Line),
            ) {
                Text(if (selecting) "إلغاء" else "اختيار سور",
                    Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    color = if (selecting) Color.White else P52Green, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        if (selecting) {
            Button(
                onClick = onPlaySelected,
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = P52Green),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (selectedCount == 0) "اختر السور ثم اضغط تشغيل" else "تشغيل المحدد ($selectedCount)")
            }
        }
    }
}

/** P52: surah row with a listen button. The emblem is passed in so the existing design is kept. */
@Composable
internal fun P52SurahTile(
    number: Int,
    title: String,
    subtitle: String,
    selecting: Boolean,
    selected: Boolean,
    emblem: @Composable () -> Unit,
    onOpen: () -> Unit,
    onListen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val nowPlaying = P52SurahAudio.currentSurah == number
    Surface(
        onClick = if (selecting) onToggleSelect else onOpen,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected || nowPlaying) Color(0xFFF1F7F2) else Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (selected || nowPlaying) Color(0xFFBAD5C5) else P52Line),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emblem()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = P52Green, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(if (nowPlaying) "▶ يُتلى الآن — ${P52SurahAudio.reciter.name}" else subtitle,
                    color = if (nowPlaying) P52Green else Color(0xFF746D62), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selecting) {
                Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (selected) "محددة" else "غير محددة",
                    tint = if (selected) P52Green else Color(0xFFB9B2A4),
                    modifier = Modifier.padding(6.dp).size(26.dp))
            } else {
                Surface(onClick = onListen, shape = RoundedCornerShape(50), color = Color(0xFFE5EFE9)) {
                    Icon(if (nowPlaying && P52SurahAudio.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "استماع إلى $title", tint = P52Green,
                        modifier = Modifier.padding(7.dp).size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                Surface(onClick = onOpen, shape = RoundedCornerShape(50), color = Color(0xFFFBF3E1)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "قراءة $title", tint = P52Gold,
                        modifier = Modifier.padding(6.dp).size(20.dp))
                }
            }
        }
    }
}

/** P52: small player shown at the bottom of the Mushaf index while a surah is active. */
@Composable
internal fun P52MiniPlayer() {
    val surah = P52SurahAudio.currentSurah ?: return
    val r = P52SurahAudio.reciter
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color = P52Green,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("سورة " + P52SurahAudio.surahName(surah), color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text(
                        if (P52SurahAudio.isLoading) "جارٍ التحميل… " + r.name
                        else r.name + (if (r.style.isNotBlank()) " — ${r.style}" else ""),
                        color = Color(0xFFD9EBDF), fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { P52SurahAudio.previous() }) {
                    Icon(Icons.Rounded.SkipNext, "السابق", tint = Color.White)
                }
                if (P52SurahAudio.isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { P52SurahAudio.togglePause() }) {
                        Icon(if (P52SurahAudio.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (P52SurahAudio.isPlaying) "إيقاف مؤقت" else "تشغيل", tint = Color.White)
                    }
                }
                IconButton(onClick = { P52SurahAudio.next() }) {
                    Icon(Icons.Rounded.SkipPrevious, "التالي", tint = Color.White)
                }
                IconButton(onClick = { P52SurahAudio.stop() }) {
                    Icon(Icons.Rounded.Close, "إغلاق", tint = Color(0xFFFFE7B0))
                }
            }
            P52SurahAudio.message?.let {
                Text(it, color = Color(0xFFFFE7B0), fontSize = 12.sp)
            }
        }
    }
}

/** P52: reciter list in the same calm style as the rest of the Mushaf. */
@Composable
internal fun P52ReciterPicker(onDismiss: () -> Unit, onPick: (P52Reciter) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFCF8EF),
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("اختيار القارئ", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF263F35), modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "إغلاق", tint = P52Green) }
                    }
                    Text("التلاوة تعمل عبر الإنترنت دون تحميل ملفات على الهاتف.",
                        fontSize = 12.sp, color = Color(0xFF746D62),
                        modifier = Modifier.padding(horizontal = 16.dp))
                    HorizontalDivider(Modifier.padding(top = 8.dp), color = P52Line)
                    LazyColumn(Modifier.fillMaxSize()) {
                        val sections = P52Reciters.all.groupBy { it.section }
                        sections.forEach { (section, list) ->
                            if (sections.size > 1) item(key = "s_$section") {
                                Text(section, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = P52Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            items(list, key = { it.id }) { r ->
                                val chosen = r.id == P52SurahAudio.reciterId
                                Row(
                                    Modifier.fillMaxWidth().clickable { onPick(r) }
                                        .padding(horizontal = 16.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.name, fontSize = 16.sp, color = Color(0xFF4A3B2A),
                                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                                        if (r.style.isNotBlank()) Text(r.style, fontSize = 13.sp, color = Color(0xFF9A7A4E))
                                    }
                                    if (chosen) Icon(Icons.Rounded.CheckCircle, "مختار", tint = P52Green)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
