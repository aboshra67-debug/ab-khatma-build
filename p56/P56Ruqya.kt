package com.ab.khatma.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val RqGreen = Color(0xFF006458)
private val RqInk = Color(0xFF18312D)
private val RqGray = Color(0xFF687C74)
private val RqBeige = Color(0xFFF8F7F2)

/** The Adhkar entry point: the original screen, plus the Ruqyah section. */
@Composable
fun InteractiveAdhkarScreen(onBack: () -> Unit) {
    var ruqyah by remember { mutableStateOf(false) }
    if (ruqyah) P56RuqyaScreen(onBack = { ruqyah = false }, onHome = onBack)
    else P56AdhkarMain(onBack = onBack, onRuqya = { ruqyah = true })
}

@Composable
internal fun P56RuqyaCard(onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B5947))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🛡", fontSize = 34.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("الرقية الشرعية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("آيات وأدعية من الكتاب والسنة • استماع بالقارئ الذي تختاره",
                    color = Color(0xFFDCEDE9), fontSize = 12.sp, lineHeight = 18.sp)
            }
            Text("‹", color = Color(0xFFFFE6A9), fontSize = 28.sp)
        }
    }
}

// ----------------------------------------------------------- adhkar chapter audio ----

/** Streams the chapter recording that ships with the Hisn data set. Never downloads or stores anything. */
internal object P56AdhkarAudio {
    var playingTitle by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var player: MediaPlayer? = null
    private var urls: Map<String, String>? = null

    private fun urlFor(ctx: Context, title: String): String? {
        val map = urls ?: runCatching {
            val root = JSONObject(ctx.assets.open("adhkar/hisn.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    root.optJSONObject(k)?.optString("Audio")?.takeIf { it.startsWith("https://") }?.let { put(k, it) }
                }
            }
        }.getOrDefault(emptyMap()).also { urls = it }
        return map[title]
    }

    fun hasAudio(ctx: Context, title: String) = urlFor(ctx, title) != null

    fun toggle(ctx: Context, title: String) {
        if (playingTitle == title) { stop(); return }
        stop()
        val url = urlFor(ctx, title) ?: return
        runCatching { P52SurahAudio.pause() }
        failed = false
        loading = true
        playingTitle = title
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setDataSource(url)
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
        playingTitle = null
        loading = false
    }
}

// ------------------------------------------------------------------------ ruqyah ----

private data class RqItem(val title: String, val note: String, val ranges: List<Triple<Int, Int, Int>>)
private data class RqDua(val title: String, val text: String, val howTo: String, val source: String)

private val rqSunnah = listOf(
    RqItem("سورة الفاتحة", "ثبت في الصحيحين أن صحابيًّا رقى بها لديغًا فشُفي، وأقرّه النبي ﷺ.", listOf(Triple(1, 1, 7))),
    RqItem("آية الكرسي", "ثبت في صحيح البخاري أن من قرأها عند النوم لم يزل عليه من الله حافظ، ولا يقربه شيطان حتى يصبح.", listOf(Triple(2, 255, 255))),
    RqItem("خواتيم سورة البقرة", "ثبت في الصحيحين: «من قرأ بالآيتين من آخر سورة البقرة في ليلة كفتاه».", listOf(Triple(2, 285, 286))),
    RqItem("الإخلاص والمعوّذتان", "كان النبي ﷺ إذا أوى إلى فراشه جمع كفّيه ونفث فيهما وقرأ هذه السور، ثم مسح بهما ما استطاع من جسده (البخاري).",
        listOf(Triple(112, 1, 4), Triple(113, 1, 5), Triple(114, 1, 6))),
)

private val rqOthers = listOf(
    RqItem("الأعراف ١١٧–١٢٢", "آيات يقرؤها كثير من أهل العلم في الرقية؛ وليس فيها حديث صحيح بعينه، والأصل ما ثبت في السنة.", listOf(Triple(7, 117, 122))),
    RqItem("يونس ٧٩–٨٢", "كذلك من اجتهاد أهل العلم، وليس فيها حديث صحيح بعينه.", listOf(Triple(10, 79, 82))),
    RqItem("طه ٦٥–٦٩", "كذلك من اجتهاد أهل العلم، وليس فيها حديث صحيح بعينه.", listOf(Triple(20, 65, 69))),
)

private val rqDuas = listOf(
    RqDua("رقية جبريل للنبي ﷺ",
        "بِسْمِ اللهِ أَرْقِيكَ، مِنْ كُلِّ شَيْءٍ يُؤْذِيكَ، مِنْ شَرِّ كُلِّ نَفْسٍ أَوْ عَيْنِ حَاسِدٍ، اللهُ يَشْفِيكَ، بِسْمِ اللهِ أَرْقِيكَ",
        "", "رواه مسلم"),
    RqDua("دعاء الشفاء",
        "أَذْهِبِ الْبَأْسَ رَبَّ النَّاسِ، وَاشْفِ أَنْتَ الشَّافِي، لَا شِفَاءَ إِلَّا شِفَاؤُكَ، شِفَاءً لَا يُغَادِرُ سَقَمًا",
        "", "رواه البخاري ومسلم"),
    RqDua("عند الألم في الجسد",
        "أَعُوذُ بِاللهِ وَقُدْرَتِهِ مِنْ شَرِّ مَا أَجِدُ وَأُحَاذِرُ",
        "ضع يدك على موضع الألم وقل: «بسم الله» ثلاثًا، ثم قل هذا الدعاء سبع مرات.", "رواه مسلم"),
    RqDua("لعيادة المريض",
        "أَسْأَلُ اللهَ الْعَظِيمَ، رَبَّ الْعَرْشِ الْعَظِيمِ، أَنْ يَشْفِيَكَ",
        "يُقال سبع مرات.", "رواه أبو داود والترمذي، وصححه الألباني"),
    RqDua("تعويذ الأولاد",
        "أُعِيذُكُمَا بِكَلِمَاتِ اللهِ التَّامَّةِ، مِنْ كُلِّ شَيْطَانٍ وَهَامَّةٍ، وَمِنْ كُلِّ عَيْنٍ لَامَّةٍ",
        "كان النبي ﷺ يعوّذ بها الحسن والحسين.", "رواه البخاري"),
    RqDua("التعوّذ من شر ما خلق",
        "أَعُوذُ بِكَلِمَاتِ اللهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
        "", "رواه مسلم"),
    RqDua("لا يضر مع اسمه شيء",
        "بِسْمِ اللهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
        "يُقال ثلاث مرات صباحًا ومساءً.", "رواه أبو داود والترمذي، وصححه الألباني"),
)

private fun ayahCodes(item: RqItem): List<Int> =
    item.ranges.flatMap { (s, a, b) -> (a..b).map { P52SurahAudio.ayahCode(s, it) } }

@Composable
internal fun P56RuqyaScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    BackHandler { onBack() }
    val repo by produceState<QuranRepository?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { QuranRepository(context.applicationContext).also { it.surahs() } }.getOrNull()
        }
    }
    var repeat by remember { mutableIntStateOf(1) }

    fun play(items: List<RqItem>) {
        val sequence = items.flatMap { item ->
            val codes = ayahCodes(item)
            codes.forEach { P52SurahAudio.customLabels[it] = item.title }
            codes
        }
        if (sequence.isNotEmpty()) P52SurahAudio.play(context, List(repeat) { sequence }.flatten())
    }

    fun verseText(item: RqItem): String {
        val r = repo ?: return ""
        return item.ranges.joinToString("\n") { (s, a, b) ->
            (a..b).joinToString(" ") { n -> (r.ayah(s, n)?.text ?: "") + " ﴿$n﴾" }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(RqBeige).windowInsetsPadding(WindowInsets.safeDrawing)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = RqGreen)) {
                        Column(Modifier.fillMaxWidth().padding(19.dp)) {
                            Text("الرقية الشرعية", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(6.dp))
                            Text("الرقية دعاء وتوكّل على الله، والشافي هو الله وحده. نقتصر هنا على ما ثبت في الكتاب والسنة، مع ذكر المصدر.",
                                fontSize = 13.sp, color = Color(0xFFDCEDE9), lineHeight = 21.sp)
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE3E9E3))) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("القارئ", color = RqInk, fontWeight = FontWeight.Bold)
                            P55VerseReciterRow()
                            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("التكرار:", color = RqGray, fontSize = 12.sp)
                                listOf(1, 3, 7).forEach { n ->
                                    FilterChip(selected = repeat == n, onClick = { repeat = n },
                                        label = { Text(if (n == 1) "مرة" else "×$n") })
                                }
                            }
                            Button(onClick = { play(rqSunnah) }, modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = RqGreen)) {
                                Text("▶ تشغيل الرقية كاملة", fontWeight = FontWeight.Bold)
                            }
                            P52MiniPlayer()
                        }
                    }
                }
                item { Text("آيات الرقية الثابتة في السنة", color = RqInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                items(rqSunnah.size) { i -> RqVerseCard(rqSunnah[i], verseText(rqSunnah[i])) { play(listOf(rqSunnah[i])) } }
                item { Text("أدعية الرقية من السنة", color = RqInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                items(rqDuas.size) { i -> RqDuaCard(context, rqDuas[i]) }
                item { Text("آيات أخرى يقرؤها الرقاة", color = RqInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                items(rqOthers.size) { i -> RqVerseCard(rqOthers[i], verseText(rqOthers[i])) { play(listOf(rqOthers[i])) } }
                item {
                    Text("نصوص الآيات من المصحف المدمج في التطبيق. الأدعية من الصحيح من الأحاديث كما في كتب السنة، وانظر أيضًا «حصن المسلم».",
                        color = RqGray, fontSize = 10.sp, lineHeight = 16.sp)
                }
            }
            P53BottomNav(onBack = onBack, onHome = onHome)
        }
    }
}

@Composable
private fun RqVerseCard(item: RqItem, text: String, onPlay: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE3E9E3))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, color = RqGreen, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onPlay, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("▶ استمع") }
            }
            if (text.isNotBlank()) Text(text, color = RqInk, fontSize = 20.sp, lineHeight = 38.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            Text(item.note, color = RqGray, fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun RqDuaCard(context: Context, dua: RqDua) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE3E9E3))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dua.title, color = RqGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(dua.text, color = RqInk, fontSize = 20.sp, lineHeight = 36.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            if (dua.howTo.isNotBlank()) Text(dua.howTo, color = RqGray, fontSize = 12.sp, lineHeight = 19.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dua.source, color = RqGray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("دعاء", dua.text + "\n" + dua.source))
                }) { Text("نسخ", color = RqGreen) }
            }
        }
    }
}
