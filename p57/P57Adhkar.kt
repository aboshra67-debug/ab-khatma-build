package com.ab.khatma.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import com.ab.khatma.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private val AdGreen = Color(0xFF006458)
private val AdInk = Color(0xFF18312D)
private val AdGray = Color(0xFF687C74)
private val AdBeige = Color(0xFFF8F7F2)
private val AdGold = Color(0xFFD4AF37)

// ------------------------------------------------------------- device voice (TTS) ----

/** Reads text aloud with the phone's own Arabic voice. Works offline when the voice is installed. */
internal object P57Tts {
    var speakingId by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingId: String? = null
    private var pendingTexts: List<String> = emptyList()
    private val main = Handler(Looper.getMainLooper())

    private const val HELP = "الصوت العربي مش متاح على الموبايل. افتح: الإعدادات ← اللغة والإدخال ← " +
        "تحويل النص إلى كلام، واختار محرك Google وحمّل صوت اللغة العربية، وبعدها جرّب تاني."

    private fun fail() {
        ready = false
        message = HELP
        speakingId = null
        pendingId = null
        runCatching { tts?.shutdown() }
        tts = null
    }

    private fun finished(utteranceId: String?, error: Boolean) {
        val parts = (utteranceId ?: return).split("|")
        if (parts.size < 3) return
        if (error || parts[2] == "1") main.post { if (speakingId == parts[0]) speakingId = null }
    }

    private fun ensure(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            main.post {
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    fail()
                } else {
                    val result = engine.setLanguage(Locale.forLanguageTag("ar"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        fail()
                    } else {
                        engine.setSpeechRate(0.85f)
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) { finished(utteranceId, false) }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) { finished(utteranceId, true) }
                        })
                        ready = true
                        val id = pendingId
                        if (id != null) { pendingId = null; speakNow(id, pendingTexts) }
                    }
                }
            }
        }
    }

    private fun speakNow(id: String, texts: List<String>) {
        val engine = tts ?: return
        engine.stop()
        val clean = texts.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) { speakingId = null; return }
        speakingId = id
        clean.forEachIndexed { i, t ->
            val last = if (i == clean.lastIndex) 1 else 0
            engine.speak(t.take(3500), if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$id|$i|$last")
        }
    }

    /** Starts reading [texts] under [id]; calling it again with the same id stops the reading. */
    fun speakList(ctx: Context, id: String, texts: List<String>) {
        if (speakingId == id) { stop(); return }
        runCatching { P52SurahAudio.pause() }
        runCatching { P56AdhkarAudio.stop() }
        message = null
        ensure(ctx)
        if (ready) speakNow(id, texts) else { pendingId = id; pendingTexts = texts; speakingId = id }
    }

    fun stop() {
        runCatching { tts?.stop() }
        pendingId = null
        speakingId = null
    }
}

// ------------------------------------------------------------ chapter listen row ----

@Composable
internal fun P57ChapterAudioRow(chapterTitle: String?, texts: List<String>) {
    val title = chapterTitle ?: return
    val context = LocalContext.current
    val ttsId = "chapter:$title"
    var tapped by remember(title) { mutableStateOf(false) }
    var usedDevice by remember(title) { mutableStateOf(false) }
    LaunchedEffect(P56AdhkarAudio.failed, tapped) {
        if (tapped && P56AdhkarAudio.failed) {
            tapped = false
            usedDevice = true
            P57Tts.speakList(context, ttsId, texts)
        }
    }
    val streaming = P56AdhkarAudio.playingTitle == title
    val speaking = P57Tts.speakingId == ttsId
    Column(Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = {
            when {
                streaming -> P56AdhkarAudio.stop()
                speaking -> P57Tts.stop()
                P56AdhkarAudio.hasAudio(context, title) -> {
                    P57Tts.stop(); tapped = true; usedDevice = false
                    P56AdhkarAudio.toggle(context, title)
                }
                else -> { usedDevice = true; P57Tts.speakList(context, ttsId, texts) }
            }
        }) {
            Text(
                when {
                    streaming -> if (P56AdhkarAudio.loading) "⏳ جارٍ التحميل…" else "⏹ إيقاف الصوت"
                    speaking -> "⏹ إيقاف القراءة"
                    else -> "🔊 استمع للباب"
                }
            )
        }
        if (usedDevice && speaking) Text("بصوت الموبايل (القراءة الصوتية)", color = AdGray, fontSize = 12.sp)
        P57Tts.message?.let { Text(it, color = Color(0xFFB3261E), fontSize = 12.sp, lineHeight = 18.sp) }
    }
}

// ------------------------------------------------------------------- dhikr card ----

@Composable
internal fun P57DhikrCard(
    recId: String,
    text: String,
    source: String,
    required: Int,
    completed: Int,
    first: Color,
    second: Color,
    favourite: Boolean,
    hasNext: Boolean,
    onCount: () -> Boolean,
    onReset: () -> Unit,
    onFavourite: () -> Unit,
    onAdvance: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("ab_khatma_adhkar_v1", Context.MODE_PRIVATE) }
    var auto by remember { mutableStateOf(prefs.getBoolean("p57_auto", true)) }
    var showSource by remember(recId) { mutableStateOf(false) }
    var advanceToken by remember(recId) { mutableIntStateOf(0) }
    LaunchedEffect(recId) { if (P57Tts.speakingId?.startsWith("d:") == true) P57Tts.stop() }
    LaunchedEffect(advanceToken) {
        if (advanceToken > 0 && auto && hasNext) {
            delay(1000)
            onAdvance()
        }
    }
    val night = P53NightMode.enabled
    val c1 = if (night) lerp(first, Color.Black, 0.55f) else first
    val c2 = if (night) lerp(second, Color.Black, 0.55f) else second
    val panel = if (night) Color(0xFF1C1B17) else Color(0xFFFFFBF0)
    val inkColor = if (night) Color(0xFFEDE6D3) else AdInk
    val finished = completed >= required
    val speaking = P57Tts.speakingId == "d:$recId"
    val tap = remember { MutableInteractionSource() }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(c1, c2)))
            .clickable(interactionSource = tap, indication = null) {
                if (!finished) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (onCount()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        advanceToken++
                    }
                }
            }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .border(2.dp, AdGold, RoundedCornerShape(22.dp))
                .padding(5.dp)
                .border(1.dp, AdGold.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .background(panel, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("✦", color = AdGold, fontSize = 18.sp)
                Text(text, color = inkColor, fontSize = 24.sp, lineHeight = 44.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
                if (source.isNotBlank() && showSource) Text("المرجع: $source", color = if (night) Color(0xFFB9B29F) else AdGray,
                    fontSize = 12.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Text("✦", color = AdGold, fontSize = 18.sp)
            }
        }
        if (source.isNotBlank()) TextButton(onClick = { showSource = !showSource }) {
            Text(if (showSource) "ⓘ إخفاء المصدر" else "ⓘ مصدر", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(116.dp)) {
            CircularProgressIndicator(
                progress = { if (required > 0) (completed.toFloat() / required).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxSize(),
                color = if (finished) Color(0xFF7CE0A3) else Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeWidth = 7.dp,
            )
            Box(
                Modifier.size(92.dp).clip(CircleShape)
                    .background(if (finished) Color(0xFF2E9E5B) else Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(if (finished) "✓" else "$completed / $required", color = Color.White,
                        fontSize = if (finished) 42.sp else 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            when {
                finished -> "اكتمل الذكر ✓"
                required > 1 -> "اضغط في أي مكان للعدّ"
                else -> "اضغط في أي مكان للإتمام"
            },
            color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp,
        )
        if (finished) TextButton(onClick = onReset) { Text("إعادة التكرار", color = Color.White.copy(alpha = 0.85f)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { P57Tts.speakList(context, "d:$recId", listOf(text)) }) {
                Text(if (speaking) "⏹" else "🔊", fontSize = 22.sp)
            }
            IconButton(onClick = onFavourite) {
                Icon(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "المفضلة",
                    tint = if (favourite) Color(0xFFFFB3C1) else Color.White)
            }
            IconButton(onClick = {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("ذكر", text + "\n" + source))
            }) { Icon(Icons.Default.ContentCopy, "نسخ", tint = Color.White) }
            IconButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text + "\n" + source)
                }, "مشاركة الذكر"))
            }) { Icon(Icons.Default.Share, "مشاركة", tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("الانتقال التلقائي للذكر التالي", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp,
                modifier = Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = {
                auto = it
                prefs.edit().putBoolean("p57_auto", it).apply()
            })
        }
        P57Tts.message?.let { Text(it, color = Color(0xFFFFD9D4), fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center) }
    }
}

/** Fixed bar under the card: previous / next and progress dots. */
@Composable
internal fun P57ReaderBar(
    at: Int,
    total: Int,
    isDone: (Int) -> Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val night = P53NightMode.enabled
    Surface(
        color = if (night) Color(0xFF1C1B17) else Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrev, enabled = at > 0, shape = RoundedCornerShape(14.dp)) { Text("السابق") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${at + 1} من $total", color = if (night) Color(0xFFEDE6D3) else AdGreen,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val from = (at - 3).coerceAtLeast(0)
                    val to = (at + 3).coerceAtMost(total - 1)
                    for (i in from..to) {
                        Box(
                            Modifier.size(if (i == at) 11.dp else 8.dp).clip(CircleShape).background(
                                when {
                                    i == at -> AdGold
                                    isDone(i) -> Color(0xFF2E9E5B)
                                    else -> Color(0xFFC9D1CC)
                                }
                            )
                        )
                    }
                }
            }
            Button(onClick = onNext, enabled = at < total - 1, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdGreen)) {
                Text(if (at >= total - 1) "نهاية الباب" else "التالي")
            }
        }
    }
}

// --------------------------------------------------------- Quran duas by reciter ----

private data class QdItem(val title: String, val note: String, val ranges: List<Triple<Int, Int, Int>>)

private val qdFixed = listOf(
    QdItem("آية الكرسي", "من قرأها عند النوم لم يزل عليه من الله حافظ — رواه البخاري.", listOf(Triple(2, 255, 255))),
    QdItem("خواتيم سورة البقرة", "«من قرأ بالآيتين من آخر سورة البقرة في ليلة كفتاه» — متفق عليه.", listOf(Triple(2, 285, 286))),
    QdItem("الإخلاص والمعوّذتان", "كان النبي ﷺ يقرؤها عند النوم ويمسح بها جسده — رواه البخاري.",
        listOf(Triple(112, 1, 4), Triple(113, 1, 5), Triple(114, 1, 6))),
    QdItem("سورة الفاتحة", "أم القرآن، وهي من أعظم ما يُقرأ ويُدعى به.", listOf(Triple(1, 1, 7))),
)

private val qdDuas = listOf(
    QdItem("ربنا آتنا في الدنيا حسنة", "من أدعية القرآن الكريم.", listOf(Triple(2, 201, 201))),
    QdItem("ربنا لا تزغ قلوبنا", "من أدعية القرآن الكريم.", listOf(Triple(3, 8, 8))),
    QdItem("ربنا اغفر لنا ذنوبنا وإسرافنا", "من أدعية القرآن الكريم.", listOf(Triple(3, 147, 147))),
    QdItem("ربنا ما خلقت هذا باطلًا", "من أدعية القرآن الكريم.", listOf(Triple(3, 191, 194))),
    QdItem("ربنا ظلمنا أنفسنا", "دعاء آدم وزوجه عليهما السلام.", listOf(Triple(7, 23, 23))),
    QdItem("حسبي الله لا إله إلا هو", "من أدعية القرآن الكريم.", listOf(Triple(9, 129, 129))),
    QdItem("ربنا هب لنا رحمة من لدنك", "دعاء أهل الكهف.", listOf(Triple(18, 10, 10))),
    QdItem("رب اشرح لي صدري", "دعاء موسى عليه السلام.", listOf(Triple(20, 25, 28))),
    QdItem("رب زدني علمًا", "من أدعية القرآن الكريم.", listOf(Triple(20, 114, 114))),
    QdItem("لا إله إلا أنت سبحانك", "دعاء ذي النون يونس عليه السلام.", listOf(Triple(21, 87, 87))),
    QdItem("رب اغفر وارحم", "من أدعية القرآن الكريم.", listOf(Triple(23, 118, 118))),
    QdItem("ربنا اصرف عنا عذاب جهنم", "من صفات عباد الرحمن.", listOf(Triple(25, 65, 66))),
    QdItem("ربنا هب لنا من أزواجنا وذرياتنا", "من صفات عباد الرحمن.", listOf(Triple(25, 74, 74))),
    QdItem("رب اجعلني مقيم الصلاة", "دعاء إبراهيم عليه السلام.", listOf(Triple(14, 40, 41))),
    QdItem("رب ارحمهما كما ربياني صغيرًا", "دعاء للوالدين.", listOf(Triple(17, 24, 24))),
    QdItem("ربنا اغفر لنا ولإخواننا", "من أدعية القرآن الكريم.", listOf(Triple(59, 10, 10))),
)

private fun qdCodes(item: QdItem): List<Int> =
    item.ranges.flatMap { (s, a, b) -> (a..b).map { P52SurahAudio.ayahCode(s, it) } }

@Composable
internal fun P57QuranDuasCard(onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF8A6A1F))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📖", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("أذكار وأدعية من القرآن", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("آية الكرسي • خواتيم البقرة • المعوّذات • ربنا… • بصوت القارئ الذي تختاره",
                    color = Color(0xFFFFF0CF), fontSize = 12.sp, lineHeight = 18.sp)
            }
            Text("‹", color = Color(0xFFFFE6A9), fontSize = 28.sp)
        }
    }
}

@Composable
internal fun P57QuranDuasScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    BackHandler { onBack() }
    val repo by produceState<QuranRepository?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { QuranRepository(context.applicationContext).also { it.surahs() } }.getOrNull()
        }
    }
    var repeat by remember { mutableIntStateOf(1) }

    fun play(items: List<QdItem>) {
        val sequence = items.flatMap { item ->
            val codes = qdCodes(item)
            codes.forEach { P52SurahAudio.customLabels[it] = item.title }
            codes
        }
        if (sequence.isNotEmpty()) P52SurahAudio.play(context, List(repeat) { sequence }.flatten())
    }

    fun verseText(item: QdItem): String {
        val r = repo ?: return ""
        return item.ranges.joinToString("\n") { (s, a, b) ->
            (a..b).joinToString(" ") { n -> (r.ayah(s, n)?.text ?: "") + " ﴿$n﴾" }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(AdBeige).windowInsetsPadding(WindowInsets.safeDrawing)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = AdGreen)) {
                        Column(Modifier.fillMaxWidth().padding(19.dp)) {
                            Text("أذكار وأدعية من القرآن", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(6.dp))
                            Text("آيات تُقرأ في الأذكار والأدعية، تسمعها بأي قارئ من القرّاء، ويمكنك تكرارها.",
                                fontSize = 13.sp, color = Color(0xFFDCEDE9), lineHeight = 21.sp)
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE3E9E3))) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("القارئ", color = AdInk, fontWeight = FontWeight.Bold)
                            P55VerseReciterRow()
                            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("التكرار:", color = AdGray, fontSize = 12.sp)
                                listOf(1, 3, 7).forEach { n ->
                                    FilterChip(selected = repeat == n, onClick = { repeat = n },
                                        label = { Text(if (n == 1) "مرة" else "×$n") })
                                }
                            }
                            Button(onClick = { play(qdFixed) }, modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = AdGreen)) {
                                Text("▶ تشغيل الأذكار القرآنية كاملة", fontWeight = FontWeight.Bold)
                            }
                            P52MiniPlayer()
                        }
                    }
                }
                item { Text("آيات الأذكار", color = AdInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                items(qdFixed.size) { i -> QdCard(qdFixed[i], verseText(qdFixed[i])) { play(listOf(qdFixed[i])) } }
                item { Text("أدعية من القرآن", color = AdInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                item {
                    OutlinedButton(onClick = { play(qdDuas) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("▶ تشغيل كل الأدعية")
                    }
                }
                items(qdDuas.size) { i -> QdCard(qdDuas[i], verseText(qdDuas[i])) { play(listOf(qdDuas[i])) } }
                item {
                    Text("نصوص الآيات من المصحف المدمج في التطبيق، والصوت يحتاج إنترنت.",
                        color = AdGray, fontSize = 10.sp, lineHeight = 16.sp)
                }
            }
            P53BottomNav(onBack = onBack, onHome = onHome)
        }
    }
}

@Composable
private fun QdCard(item: QdItem, text: String, onPlay: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE3E9E3))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, color = AdGreen, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onPlay, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("▶ استمع") }
            }
            if (text.isNotBlank()) Text(text, color = AdInk, fontSize = 20.sp, lineHeight = 38.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            Text(item.note, color = AdGray, fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
}
