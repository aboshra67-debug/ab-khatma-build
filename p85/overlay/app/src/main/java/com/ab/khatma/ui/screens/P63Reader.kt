package com.ab.khatma.ui.screens

import com.ab.khatma.guide.P85Guide.target

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranRepository
import org.json.JSONArray
import kotlin.math.roundToInt

private val P63Bar = Color(0xFFF3F3F0)
private val P63BarNight = Color(0xFF1F1B12)

/** Hizb numbers (1..60) from the 240 quarter starts of the Mushaf (Tanzil / Madinah Mushaf data). */
internal object P63Hizb {
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

    /** The hizb that is running at the first verse of the page. */
    fun ofPage(context: Context, repo: QuranRepository, page: Int): Int {
        val first = repo.loadPage(page).firstOrNull() ?: return 1
        val code = first.surah * 1000 + first.ayah
        val q = load(context)
        var at = 0
        for (i in q.indices) { if (q[i] <= code) at = i else break }
        return at / 4 + 1
    }
}

@Composable
private fun P63Action(icon: ImageVector, label: String, tint: Color, active: Boolean = false, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .then(if (active) Modifier.background(P61Tan.copy(alpha = 0.7f), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** One clean line (surah • juz • hizb • page) and, under it, six named icons. */
@Composable
internal fun P63TopBar(
    night: Boolean,
    info: String,
    saved: Boolean,
    toolsOn: Boolean,
    onIndex: () -> Unit,
    onSearch: () -> Unit,
    onMarks: () -> Unit,
    onTools: () -> Unit,
    onFull: () -> Unit,
    onRead: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P53NightMode.load(context) }
    val tint = if (night) Color(0xFFE9DDB8) else P61Green
    Column(Modifier.fillMaxWidth().background(if (night) P63BarNight else P63Bar)) {
        Text(
            info, color = tint, fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 1.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            P63Action(Icons.Rounded.Menu, "الفهرس", tint, modifier = Modifier.weight(1f).target("reader.index"), onClick = onIndex)
            P63Action(Icons.Rounded.Search, "بحث", tint, modifier = Modifier.weight(1f).target("reader.search"), onClick = onSearch)
            P63Action(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "علاماتي", tint, modifier = Modifier.weight(1f).target("reader.marks"), onClick = onMarks)
            P63Action(Icons.Rounded.Edit, "أدوات", tint, active = toolsOn, modifier = Modifier.weight(1f).target("reader.tools"), onClick = onTools)
            P63Action(Icons.Rounded.Fullscreen, "تكبير", tint, modifier = Modifier.weight(1f).target("reader.full"), onClick = onFull)
            P63Action(Icons.Rounded.AutoStories, "قراءة", tint, modifier = Modifier.weight(1f).target("reader.read"), onClick = onRead)
            P63Action(Icons.Rounded.Settings, "إعدادات", tint, modifier = Modifier.weight(1f).target("reader.settings"), onClick = onSettings)
        }
    }
}

/** Player (strip or floating card) and, under it, previous page — page bar — next page. */
@Composable
internal fun P63Bottom(
    repo: QuranRepository,
    pages: List<Int>,
    index: Int,
    night: Boolean,
    assigned: Boolean,
    progress: Float,
    atEnd: Boolean,
    completed: Boolean,
    onComplete: (() -> Unit)?,
    singleAyahPlaying: Boolean,
    onStopSingle: () -> Unit,
    onPlayHere: () -> Unit,
    onSeek: (Int) -> Unit,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRead: () -> Unit,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    var showReciters by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    if (showReciters) P55ReciterPicker(onDismiss = { showReciters = false }) {
        P52SurahAudio.selectReciter(context, it.id)
        showReciters = false
    }
    val bg = if (night) P63BarNight else P63Bar
    val muted = if (night) Color(0xFFE9DDB8) else P61Ink.copy(alpha = 0.65f)
    val tint = if (night) Color(0xFFE9DDB8) else P61Green
    if (compact) {
        Surface(
            color = bg,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onPrev, enabled = hasPrev, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)) { Text("السابق") }
                Button(
                    onClick = onRead,
                    modifier = Modifier.padding(horizontal = 8.dp).height(54.dp).weight(1.2f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) { Text("📖 قراءة", fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1) }
                Text(
                    "صفحة " + p61Digits(pages.getOrElse(index) { pages.firstOrNull() ?: 1 }),
                    color = tint,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(0.8f),
                )
                OutlinedButton(onClick = onNext, enabled = hasNext, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)) { Text("التالي") }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        if (assigned) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${(progress * 100).toInt()}% من الجزء", color = muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                when {
                    completed -> Text("تمت القراءة ✓", color = P61Gold, fontWeight = FontWeight.Bold)
                    atEnd && onComplete != null -> Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = P61Gold),
                    ) { Text("أتممت الجزء ✓") }
                }
            }
        }
        if (P52SurahAudio.isActive) {
            P66Bar()
            if (!P52SurahAudio.followReader) TextButton(onClick = onPlayHere, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("▶ ابدأ التلاوة من هذه الصفحة", color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        val s = scrub
        if (s != null && pages.size > 1) {
            val p = pages[s.roundToInt().coerceIn(0, pages.lastIndex)]
            Surface(
                shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    "${repo.surahName(repo.pageSurah(p))} — صفحة ${p61Digits(p)}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = P61Green, fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!P52SurahAudio.isActive) {
                IconButton(onClick = if (singleAyahPlaying) onStopSingle else onPlayHere, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (singleAyahPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        if (singleAyahPlaying) "إيقاف" else "تشغيل من هنا", tint = tint,
                    )
                }
                TextButton(onClick = { showReciters = true }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
                    val nm = P52SurahAudio.savedReciter.name
                    Text((if (nm.length > 11) nm.take(11) + "…" else nm) + " ▾", color = tint, fontSize = 11.sp, maxLines = 1)
                }
            }
            TextButton(onClick = onPrev, enabled = hasPrev, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("›", color = if (hasPrev) tint else muted.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
            if (pages.size > 1) {
                Slider(
                    value = s ?: index.toFloat(),
                    onValueChange = { scrub = it; onSeek(it.roundToInt().coerceIn(0, pages.lastIndex)) },
                    onValueChangeFinished = { scrub = null },
                    valueRange = 0f..pages.lastIndex.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = P61Green, activeTrackColor = P61Green.copy(alpha = 0.55f),
                        inactiveTrackColor = P61Gold.copy(alpha = 0.3f),
                    ),
                )
            } else {
                Text(
                    "صفحة " + p61Digits(pages.firstOrNull() ?: 1), color = muted, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = onNext, enabled = hasNext, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("‹", color = if (hasNext) tint else muted.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
        }
        // A prominent entry beneath the page controls, visible without opening any menu.
        Button(
            onClick = onRead,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 3.dp).height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = P61Green),
        ) { Text("📖 وضع القراءة", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }
}

/** P71: persistent controls in reading mode; no transient hidden exit button. */
@Composable
internal fun P71ReadingBottom(
    page: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    Surface(color = P63Bar, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TextButton(onClick = onPrev, enabled = hasPrev, modifier = Modifier.weight(1f)) { Text("السابق", maxLines = 1) }
            Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                modifier = Modifier.weight(1.55f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("إنهاء القراءة", maxLines = 1, fontSize = 12.sp)
            }
            Text("${p61Digits(page)}", color = P61Green, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(.55f), textAlign = TextAlign.Center)
            TextButton(onClick = onNext, enabled = hasNext, modifier = Modifier.weight(1f)) { Text("التالي", maxLines = 1) }
        }
    }
}
