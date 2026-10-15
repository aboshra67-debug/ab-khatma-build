package com.ab.khatma.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ab.khatma.quran.P52SurahAudio

private val P64Green = Color(0xFF064D3B)
private val P64Chip = Color(0xFF0B624C)
private val P64Gold = Color(0xFFDFBD78)

/** Slim green top line of the Mushaf index: the title on one side and the search button on the other. */
@Composable
internal fun P64IndexTop(onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(P64Green).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("المصحف الشريف", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
            Text("القرآن الكريم", color = P64Gold, fontSize = 11.sp)
        }
        Surface(
            onClick = onSearch,
            shape = RoundedCornerShape(50),
            color = P64Chip,
            border = BorderStroke(1.dp, P64Gold.copy(alpha = 0.75f)),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = "بحث", tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("بحث", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun P64HeaderButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = P64Chip,
        border = BorderStroke(1.dp, P64Gold.copy(alpha = 0.6f)),
    ) {
        Text(
            label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
        )
    }
}

/** The tools of the index, packed tight: listen, play panel, reciter, and the four study tools in one row. */
@Composable
internal fun P64ListenHeader(
    onPickSurahs: () -> Unit,
    onPlay: () -> Unit,
    onHifz: () -> Unit,
    onTasmee: () -> Unit,
    onSimilar: () -> Unit,
    onMeanings: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    Column(
        Modifier.fillMaxWidth().background(P64Green).padding(start = 13.dp, end = 13.dp, top = 2.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onPickSurahs,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFE2BE70),
                shadowElevation = 2.dp,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("🎧 اختيار السور للاستماع", color = Color(0xFF3B2A06), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                }
            }
            Surface(
                onClick = onPlay,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = P64Chip,
                border = BorderStroke(1.dp, P64Gold.copy(alpha = 0.8f)),
            ) {
                Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (P52SurahAudio.isActive) (if (P52SurahAudio.isPlaying) "⏸ المشغّل" else "▶ المشغّل") else "▶ تشغيل",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                    )
                }
            }
        }
        P64HeaderReciter()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            P64HeaderButton("🧠 الحفظ", Modifier.weight(1f), onHifz)
            P64HeaderButton("🎙 تسميع", Modifier.weight(1f), onTasmee)
            P64HeaderButton("🔁 متشابهات", Modifier.weight(1f), onSimilar)
            P64HeaderButton("📖 مفردات", Modifier.weight(1f), onMeanings)
        }
    }
}

@Composable
private fun P64HeaderReciter() {
    val context = LocalContext.current
    var pick by remember { mutableStateOf(false) }
    if (pick) P55ReciterPicker(onDismiss = { pick = false }) {
        P52SurahAudio.selectReciter(context, it.id)
        pick = false
    }
    val r = P52SurahAudio.reciter
    Surface(
        onClick = { pick = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = P64Chip,
        border = BorderStroke(1.dp, P64Gold.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🔊 القارئ: " + r.name + (if (r.style.isNotBlank()) " (${r.style})" else ""),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("تغيير ▾", color = Color(0xFFFFE6A9), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val P64Speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** «▶ تشغيل»: the player, the reciter and the speed in one small window. */
@Composable
internal fun P64PlayPanel(startSurah: Int, startName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(22.dp), color = P61Paper) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎧 المشغّل", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = P61Green, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("إغلاق") }
                    }
                    if (P52SurahAudio.isActive) {
                        P52MiniPlayer()
                    } else {
                        Text("لا يوجد تشغيل الآن.", color = P61Ink.copy(alpha = 0.7f), fontSize = 14.sp)
                        Button(
                            onClick = { P52SurahAudio.play(context, (startSurah..114).toList()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                        ) { Text("▶ ابدأ من سورة $startName", fontWeight = FontWeight.Bold) }
                    }
                    P64ReciterButton()
                    Text("سرعة التلاوة", color = P61Ink.copy(alpha = 0.65f), fontSize = 12.sp)
                    P62ChipRow(
                        P64Speeds.map { "‎" + p61SpeedLabel(it) + "‎" to (kotlin.math.abs(P52SurahAudio.speed - it) < 0.01f) },
                    ) { P52SurahAudio.setSpeed(context, P64Speeds[it]) }
                }
            }
        }
    }
}
