package com.ab.khatma.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio

private val TsGreen = Color(0xFF064D3B)
private val TsInk = Color(0xFF18312D)
private val TsGray = Color(0xFF687C74)
private val TsBeige = Color(0xFFF8F7F2)
private val TsGold = Color(0xFFD4AF37)

// ------------------------------------------------------------------- tasbih ----

private val tsBuiltIn = listOf(
    "سُبْحَانَ اللهِ",
    "الْحَمْدُ لِلَّهِ",
    "اللهُ أَكْبَرُ",
    "لَا إِلَهَ إِلَّا اللهُ",
    "أَسْتَغْفِرُ اللهَ",
    "سُبْحَانَ اللهِ وَبِحَمْدِهِ",
    "سُبْحَانَ اللهِ الْعَظِيمِ",
    "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللهِ",
    "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
)
private val tsSeqIdx = listOf(0, 1, 2)
private val tsSeqTarget = listOf(33, 33, 34)

@Composable
internal fun P60TasbihScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    BackHandler { onBack() }
    val prefs = remember { context.getSharedPreferences("p58_tasbih", Context.MODE_PRIVATE) }
    var custom by remember { mutableStateOf(prefs.getString("custom", "") ?: "") }
    var customDialog by remember { mutableStateOf(false) }
    var customDraft by remember { mutableStateOf("") }
    val names = remember(custom) { tsBuiltIn + (if (custom.isNotBlank()) listOf(custom) else emptyList()) }
    var sel by remember { mutableIntStateOf(prefs.getInt("sel", 0)) }
    var target by remember { mutableIntStateOf(prefs.getInt("target", 33)) }
    var seqOn by remember { mutableStateOf(prefs.getBoolean("seq", false)) }
    var seqStep by remember { mutableIntStateOf(prefs.getInt("seq_step", 0).coerceIn(0, 2)) }
    var lifetime by remember { mutableIntStateOf(prefs.getInt("lifetime", 0)) }
    val counts = remember { mutableStateMapOf<String, Int>() }
    val tap = remember { MutableInteractionSource() }

    fun countOf(name: String): Int = counts[name] ?: prefs.getInt("cnt_$name", 0)
    fun setCount(name: String, value: Int) {
        counts[name] = value
        prefs.edit().putInt("cnt_$name", value).apply()
    }

    val shownName = if (seqOn) names[tsSeqIdx[seqStep]] else names[sel.coerceIn(0, names.lastIndex)]
    val shownTarget = if (seqOn) tsSeqTarget[seqStep] else target
    val count = countOf(shownName)
    val finished = seqOn && seqStep == 2 && count >= shownTarget

    if (customDialog) AlertDialog(
        onDismissRequest = { customDialog = false },
        title = { Text("ذكر خاص بك") },
        text = {
            OutlinedTextField(value = customDraft, onValueChange = { customDraft = it.take(60) },
                label = { Text("اكتب الذكر") }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = customDraft.isNotBlank(), onClick = {
                custom = customDraft.trim()
                prefs.edit().putString("custom", custom).apply()
                sel = tsBuiltIn.size
                prefs.edit().putInt("sel", sel).apply()
                customDialog = false
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { customDialog = false }) { Text("إلغاء") } },
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(TsBeige).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📿 السبحة", fontSize = 24.sp, color = TsGreen, fontWeight = FontWeight.ExtraBold)
                Box(
                    Modifier.fillMaxWidth()
                        .border(2.dp, TsGold, RoundedCornerShape(20.dp)).padding(5.dp)
                        .border(1.dp, TsGold.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFFBF0), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(shownName, color = TsInk, fontSize = 26.sp, lineHeight = 42.sp, textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEachIndexed { i, n ->
                        FilterChip(selected = !seqOn && sel == i, enabled = !seqOn, onClick = {
                            sel = i; prefs.edit().putInt("sel", i).apply()
                        }, label = { Text(n, maxLines = 1) })
                    }
                    AssistChip(onClick = { customDraft = custom; customDialog = true }, label = { Text("＋ ذكر خاص") })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = seqOn, onClick = {
                        val next = !seqOn
                        seqOn = next
                        seqStep = 0
                        prefs.edit().putBoolean("seq", next).putInt("seq_step", 0).apply()
                        if (next) tsSeqIdx.forEach { setCount(names[it], 0) }
                    }, label = { Text("تسبيح ما بعد الصلاة (33 • 33 • 34)") })
                    if (!seqOn) listOf(33, 99, 100, 1000).forEach { n ->
                        FilterChip(selected = target == n, onClick = {
                            target = n; prefs.edit().putInt("target", n).apply()
                        }, label = { Text("$n") })
                    }
                }
                if (seqOn) Text("الخطوة ${seqStep + 1} من 3", color = TsGray, fontSize = 13.sp)
                Box(
                    Modifier.size(210.dp).clip(CircleShape)
                        .background(if (count >= shownTarget) Color(0xFF2E9E5B) else TsGreen)
                        .clickable(interactionSource = tap, indication = null) {
                            if (finished) return@clickable
                            val next = count + 1
                            setCount(shownName, next)
                            lifetime++
                            prefs.edit().putInt("lifetime", lifetime).apply()
                            val reached = next >= shownTarget
                            haptic.performHapticFeedback(if (reached) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                            if (reached && seqOn && seqStep < 2) {
                                seqStep++
                                prefs.edit().putInt("seq_step", seqStep).apply()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$count", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                            Text("/ $shownTarget", color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp)
                        }
                    }
                }
                Text(
                    when {
                        finished -> "تم التسبيح كاملًا ✓ — تقبّل الله"
                        count >= shownTarget -> "اكتمل العدد ✓"
                        else -> "اضغط على الدائرة للتسبيح"
                    }, color = TsGray, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        if (seqOn) { seqStep = 0; prefs.edit().putInt("seq_step", 0).apply(); tsSeqIdx.forEach { setCount(names[it], 0) } }
                        else setCount(shownName, 0)
                    }, shape = RoundedCornerShape(14.dp)) { Text("تصفير") }
                    Text("إجمالي ما سبّحت: $lifetime", color = Color(0xFFB48B38), fontSize = 13.sp)
                }
            }
            P53BottomNav(onBack = onBack, onHome = onHome)
        }
    }
}

// ----------------------------------------------- adhkar with real reciters (stream) ----

private data class VoiceTrack(val title: String, val url: String)

private fun voiceTrack(n: Int, who: String) = VoiceTrack(
    "تسجيل $n — $who",
    "https://archive.org/download/makkah-live.-net-athkar-01/MakkahLive.Net_athkar_" + n.toString().padStart(2, '0') + ".mp3",
)

private val voiceTracks = listOf(
    voiceTrack(1, "الشيخ مشاري العفاسي"),
    voiceTrack(2, "الشيخ ناصر القطامي"),
    voiceTrack(3, "الشيخ مشاري العفاسي"),
    voiceTrack(4, "الشيخ مشاري العفاسي"),
    voiceTrack(5, "الشيخ فارس عبّاد"),
    voiceTrack(6, "الشيخ فارس عبّاد"),
    voiceTrack(7, "الشيخ محمد جبريل"),
    voiceTrack(8, "الشيخ محمد جبريل"),
    voiceTrack(9, "قارئ غير مسمّى في المصدر"),
    voiceTrack(10, "قارئ غير مسمّى في المصدر"),
    voiceTrack(11, "الشيخ مشاري العفاسي"),
)

/** Streams a recorded adhkar track. Nothing is downloaded or stored. */
internal object P60VoiceAdhkar {
    var playing by mutableStateOf<Int?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var player: MediaPlayer? = null

    fun toggle(index: Int) {
        if (playing == index) { stop(); return }
        stop()
        val track = voiceTracks.getOrNull(index) ?: return
        runCatching { P52SurahAudio.pause() }
        runCatching { P57Tts.stop() }
        runCatching { P56AdhkarAudio.stop() }
        failed = false
        loading = true
        playing = index
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setDataSource(track.url)
            p.setOnPreparedListener { if (player === it) { loading = false; it.start() } }
            p.setOnCompletionListener { if (player === it) stop() }
            p.setOnErrorListener { mp, _, _ -> if (player === mp) { stop(); failed = true }; true }
            player = p
            p.prepareAsync()
        } catch (_: Exception) { stop(); failed = true }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        playing = null
        loading = false
    }
}

@Composable
internal fun P60VoiceCard(onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B5947))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎙", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("أذكار بصوت الشيوخ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("تسجيلات حقيقية: العفاسي، القطامي، فارس عبّاد، محمد جبريل",
                    color = Color(0xFFDCEDE9), fontSize = 12.sp, lineHeight = 18.sp)
            }
            Text("‹", color = Color(0xFFFFE6A9), fontSize = 28.sp)
        }
    }
}

@Composable
internal fun P60VoiceScreen(onBack: () -> Unit, onHome: () -> Unit) {
    BackHandler { onBack() }
    DisposableEffect(Unit) { onDispose { P60VoiceAdhkar.stop() } }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(TsBeige).windowInsetsPadding(WindowInsets.safeDrawing)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TsGreen)) {
                        Column(Modifier.fillMaxWidth().padding(19.dp)) {
                            Text("🎙 أذكار بصوت الشيوخ", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(6.dp))
                            Text("أذكار الصباح والمساء بتسجيلات حقيقية. تحتاج إنترنت، ولا يُحفظ شيء على الموبايل.",
                                fontSize = 13.sp, color = Color(0xFFDCEDE9), lineHeight = 21.sp)
                        }
                    }
                }
                if (P60VoiceAdhkar.failed) item {
                    Text("تعذّر تشغيل التسجيل. تأكد من الإنترنت وجرّب تاني.", color = Color(0xFFB3261E), fontSize = 13.sp)
                }
                items(voiceTracks.size) { i ->
                    val t = voiceTracks[i]
                    val on = P60VoiceAdhkar.playing == i
                    Card(onClick = { P60VoiceAdhkar.toggle(i) }, shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = if (on) Color(0xFFE5EFE9) else Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE3E9E3))) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title, color = TsInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(when {
                                on && P60VoiceAdhkar.loading -> "⏳"
                                on -> "⏹"
                                else -> "▶"
                            }, fontSize = 22.sp)
                        }
                    }
                }
                item {
                    Text("المصدر: موقع Makkah Live (عبر archive.org). التسجيلات لأصحابها، وهي هنا للتجربة والاستماع فقط.",
                        color = TsGray, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
            P53BottomNav(onBack = onBack, onHome = onHome)
        }
    }
}
