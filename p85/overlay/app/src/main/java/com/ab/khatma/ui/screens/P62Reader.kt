package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranRepository
import kotlin.math.roundToInt

private val P62Bar = Color(0xFFF6F0DD)
private val P62BarNight = Color(0xFF1F1B12)

@Composable
private fun P62Icon(
    icon: ImageVector,
    desc: String,
    tint: Color,
    active: Boolean = false,
    flip: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).then(if (active) Modifier.background(P61Tan.copy(alpha = 0.7f), CircleShape) else Modifier),
    ) {
        Icon(icon, desc, tint = tint, modifier = if (flip) Modifier.scale(-1f, 1f) else Modifier)
    }
}

/** The thin bar of icons above the Mushaf page: back, index, search, bookmarks, tools, full page, settings. */
@Composable
internal fun P62TopBar(
    night: Boolean,
    saved: Boolean,
    toolsOn: Boolean,
    onBack: () -> Unit,
    onIndex: () -> Unit,
    onSearch: () -> Unit,
    onMarks: () -> Unit,
    onTools: () -> Unit,
    onFull: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P53NightMode.load(context) }
    val tint = if (night) Color(0xFFE9DDB8) else P61Green
    Row(
        Modifier.fillMaxWidth().background(if (night) P62BarNight else P62Bar).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        P62Icon(Icons.Rounded.ArrowBack, "رجوع", tint, flip = true, onClick = onBack)
        P62Icon(Icons.Rounded.Menu, "الفهرس", tint, onClick = onIndex)
        Spacer(Modifier.weight(1f))
        P62Icon(Icons.Rounded.Search, "بحث", tint, onClick = onSearch)
        P62Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "علاماتي", tint, onClick = onMarks)
        P62Icon(Icons.Rounded.Edit, "أدوات", tint, active = toolsOn, onClick = onTools)
        P62Icon(Icons.Rounded.Fullscreen, "تكبير", tint, onClick = onFull)
        P62Icon(Icons.Rounded.Settings, "الإعدادات", tint, onClick = onSettings)
    }
}

/** Shown while the page fills the whole screen: one tap gives the bars back. */
@Composable
internal fun P62ExitPill(onExit: () -> Unit) {
    Surface(
        onClick = onExit,
        shape = RoundedCornerShape(50),
        color = P61Green.copy(alpha = 0.82f),
    ) {
        Text(
            "↩ تصغير", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Recite the Quran from the first verse of this page onwards; the page and the highlight follow the voice. */
internal fun p62PlayFromPage(context: Context, repo: QuranRepository, pages: List<Int>, page: Int, surah: Int?) {
    val last = if (surah != null) 604 else (pages.lastOrNull() ?: page)
    val codes = ArrayList<Int>()
    for (p in page..last) {
        for (v in repo.loadPage(p)) {
            if (p == page && surah != null && v.surah != surah) continue
            codes.add(P52SurahAudio.ayahCode(v.surah, v.ayah))
        }
    }
    if (codes.isEmpty()) return
    P52SurahAudio.play(context, codes)
    P52SurahAudio.followReader = true
    if (P52SurahAudio.savedReciter.ayahFolder == null) {
        val note = P52SurahAudio.savedReciter.name + " متاح للسورة كاملة فقط — الآيات بصوت الحصري."
        P52SurahAudio.message = note
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (P52SurahAudio.message == note) P52SurahAudio.message = null
        }, 6000)
    }
}

/** Under the page: the player (a thin strip, or the floating card while reciting) and the page navigation bar. */
@Composable
internal fun P62Bottom(
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
) {
    val context = LocalContext.current
    var showReciters by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    if (showReciters) P55ReciterPicker(onDismiss = { showReciters = false }) {
        P52SurahAudio.selectReciter(context, it.id)
        showReciters = false
    }
    val bg = if (night) P62BarNight else P62Bar
    val muted = if (night) Color(0xFFE9DDB8) else P61Ink.copy(alpha = 0.65f)
    Column(Modifier.fillMaxWidth().background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
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
            P61FloatingPlayer(onPickReciter = { showReciters = true })
        } else {
            P61AudioStrip(
                playing = singleAyahPlaying,
                night = night,
                onPlay = onPlayHere,
                onStop = onStopSingle,
                onPickReciter = { showReciters = true },
            )
        }
        if (pages.size > 1) {
            val s = scrub
            if (s != null) {
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
                Text(p61Digits(pages.getOrElse(index) { pages.first() }), color = muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** ⚙ in the reader: everything that used to crowd the bars. */
@Composable
internal fun P62SettingsDialog(
    night: Boolean,
    onNight: () -> Unit,
    colored: Boolean,
    onColored: () -> Unit,
    zoom: Float,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: () -> Unit,
    onFlow: () -> Unit,
    onFlex: () -> Unit,
    onListen: () -> Unit,
    onHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(20.dp), color = P61Paper) {
                Column(
                    Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(onClick = { onDismiss(); com.ab.khatma.guide.P85Guide.start("reader") }) { Text("❔ شرح المصحف") }
                    Text("إعدادات المصحف", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = P61Green)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌙 القراءة الليلية", Modifier.weight(1f), color = P61Ink)
                        Switch(checked = night, onCheckedChange = { onNight() })
                    }
                    val p67Ctx = androidx.compose.ui.platform.LocalContext.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⬜ خلفية بيضاء ثلجية", Modifier.weight(1f), color = P61Ink)
                        Switch(checked = P67Look.white, onCheckedChange = { P67Look.toggle(p67Ctx) })
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) { P68Reading.load(p67Ctx) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔊 صوت تقليب الصفحة (وضع القراءة)", Modifier.weight(1f), color = P61Ink)
                        Switch(checked = P68Reading.soundOn, onCheckedChange = { P68Reading.toggleSound(p67Ctx) })
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) { P71StartupRecitation.load(p67Ctx) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تلاوة عند فتح التطبيق — عبد الباسط", Modifier.weight(1f), color = P61Ink)
                        Switch(checked = P71StartupRecitation.enabled, onCheckedChange = { P71StartupRecitation.toggle(p67Ctx) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("المصحف: " + (if (colored) "ملون" else "أصلي"), Modifier.weight(1f), color = P61Ink)
                        OutlinedButton(onClick = onColored) { Text(if (colored) "تحويل إلى أصلي" else "تحويل إلى ملون") }
                    }
                    P60ZoomBar(zoom = zoom, onMinus = onMinus, onPlus = onPlus, onReset = onReset)
                    OutlinedButton(onClick = onListen, modifier = Modifier.fillMaxWidth()) { Text("🎧 استمع للسورة كاملة") }
                    OutlinedButton(onClick = onFlow, modifier = Modifier.fillMaxWidth()) { Text("📜 قراءة متصلة بالنص") }
                    OutlinedButton(onClick = onFlex, modifier = Modifier.fillMaxWidth()) { Text("نص مرن (خط قابل للتكبير)") }
                    Button(
                        onClick = onHome, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                    ) { Text("الرئيسية") }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------ shared pieces ----

@Composable
internal fun P62Header(title: String, subtitle: String?, onClose: () -> Unit) {
    Surface(color = P61Green, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.ArrowBack, "رجوع", tint = Color.White, modifier = Modifier.scale(-1f, 1f))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun P62Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) P61Green else Color.White,
        border = BorderStroke(1.dp, if (selected) P61Green else Color(0xFFD9CDAF)),
    ) {
        Box(Modifier.padding(vertical = 9.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
            Text(
                label, color = if (selected) Color.White else P61Ink, fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Equal-width chips in one row, so nothing is cut at the screen edge. */
@Composable
internal fun P62ChipRow(options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, (label, selected) ->
            P62Chip(label, selected, Modifier.weight(1f)) { onPick(i) }
        }
    }
}

@Composable
internal fun P62Stepper(label: String, value: Int, min: Int, max: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = P61Ink.copy(alpha = 0.65f), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { if (value > min) onChange(value - 1) }, enabled = value > min,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp),
            ) { Text("−", fontSize = 20.sp) }
            Text(
                p61Digits(value), color = P61Green, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                textAlign = TextAlign.Center, modifier = Modifier.width(52.dp),
            )
            OutlinedButton(
                onClick = { if (value < max) onChange(value + 1) }, enabled = value < max,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp),
            ) { Text("+", fontSize = 20.sp) }
        }
    }
}

@Composable
internal fun P62SurahPicker(repo: QuranRepository, surah: Int, onPick: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("السورة: ${repo.surahName(surah)} ▾")
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            repo.surahs().forEach { s ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("${s.index}. ${s.name}") },
                    onClick = { menu = false; onPick(s.index) },
                )
            }
        }
    }
}
