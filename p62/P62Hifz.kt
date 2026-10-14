package com.ab.khatma.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranRepository

private fun p62PlayingVerse(): String? {
    val item = P52SurahAudio.currentSurah ?: return null
    if (item < P52SurahAudio.AYAH_BASE) return null
    val code = item - P52SurahAudio.AYAH_BASE
    return "${code / 1000}:${code % 1000}"
}

private fun p62Times(n: Int): String = when (n) {
    1 -> "مرة"
    3 -> "٣ مرات"
    5 -> "٥ مرات"
    10 -> "١٠ مرات"
    else -> p61Digits(n) + " مرات"
}

/** «وضع الحفظ»: the range on top, the verses in the middle, the controls pinned at the bottom. */
@Composable
internal fun P62HifzScreen(
    repo: QuranRepository,
    onClose: () -> Unit,
    onTasmee: (surah: Int, from: Int, to: Int) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    BackHandler { onClose() }

    val surahs = remember { repo.surahs() }
    var surah by remember { mutableIntStateOf(1) }
    var from by remember { mutableIntStateOf(1) }
    var to by remember { mutableIntStateOf(7) }
    var verseRepeat by remember { mutableIntStateOf(3) }
    var rangeRepeat by remember { mutableIntStateOf(1) }
    var hide by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var showSimilar by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showMeaning by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    val count = surahs.firstOrNull { it.index == surah }?.ayahs ?: 1
    val verses = remember(surah, from, to) { repo.loadSurah(surah).filter { it.ayah in from..to } }
    val playing = p62PlayingVerse()

    fun chooseSurah(n: Int) {
        surah = n
        val c = surahs.firstOrNull { it.index == n }?.ayahs ?: 1
        from = 1
        to = c.coerceAtMost(7)
        revealed.clear()
        selected = null
    }

    val listState = rememberLazyListState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val starts = remember(verses) { HashMap<String, Int>() }
    val paragraphIndex = 2

    // The verse that is being repeated stays in view.
    LaunchedEffect(playing) {
        val key = playing ?: return@LaunchedEffect
        if (listState.isScrollInProgress) return@LaunchedEffect
        val start = starts[key] ?: return@LaunchedEffect
        val l = layout ?: return@LaunchedEffect
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == paragraphIndex } ?: return@LaunchedEffect
        val y = info.offset + l.getLineTop(l.getLineForOffset(start.coerceAtMost(l.layoutInput.text.length - 1)))
        val vh = listState.layoutInfo.viewportSize.height
        if (y < vh * 0.12f || y > vh * 0.62f) listState.animateScrollBy(y - vh * 0.22f)
    }

    showSimilar?.let { (s, a) ->
        P61SimilarDialog(repo, s, a, onDismiss = { showSimilar = null }, onOpen = null)
    }
    showMeaning?.let { (s, a) ->
        val info = repo.meaning(s, a)
        AlertDialog(
            onDismissRequest = { showMeaning = null },
            title = { Text("معاني الكلمات — ${repo.surahName(s)} ${p61Digits(a)}") },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            if (info == null || info.text.isBlank()) "المحتوى غير متاح لهذه الآية." else info.text,
                            fontSize = 17.sp, lineHeight = 29.sp,
                        )
                        Text(
                            "المصدر: QuranEnc.com — المحتوى معروض دون تعديل.",
                            fontSize = 11.sp, color = P61Ink.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMeaning = null }) { Text("إغلاق") } },
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(P61Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
            P62Header("🧠 وضع الحفظ", "اسمع المقطع بتكرار كل آية، واخفِ النص وسمّع لنفسك", onClose)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 0 — the range
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, Color(0xFFE6DCC2)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            P62SurahPicker(repo, surah) { chooseSurah(it) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                P62Stepper("من الآية", from, 1, to, Modifier.weight(1f)) { from = it; revealed.clear() }
                                P62Stepper("إلى الآية", to, from, count, Modifier.weight(1f)) { to = it; revealed.clear() }
                            }
                        }
                    }
                }
                // 1 — options (collapsed to one line)
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, Color(0xFFE6DCC2)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(onClick = { optionsOpen = !optionsOpen }, color = Color.Transparent) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("⚙ خيارات الحفظ", fontWeight = FontWeight.Bold, color = P61Green)
                                        Text(
                                            "الآية: ${p62Times(verseRepeat)} • المقطع: ${p62Times(rangeRepeat)} • السرعة: ‎${p61SpeedLabel(P52SurahAudio.speed)}‎" +
                                                (if (hide) " • النص مخفي" else ""),
                                            fontSize = 12.sp, color = P61Ink.copy(alpha = 0.65f), lineHeight = 18.sp,
                                        )
                                    }
                                    Text(if (optionsOpen) "▴" else "▾", color = P61Green, fontSize = 18.sp)
                                }
                            }
                            if (optionsOpen) {
                                Text("تكرار كل آية", color = P61Ink.copy(alpha = 0.65f), fontSize = 12.sp)
                                val ones = listOf(1, 3, 5, 10)
                                P62ChipRow(ones.map { p62Times(it) to (verseRepeat == it) }) { verseRepeat = ones[it] }
                                Text("تكرار المقطع كله", color = P61Ink.copy(alpha = 0.65f), fontSize = 12.sp)
                                val twos = listOf(1, 3, 5)
                                P62ChipRow(twos.map { p62Times(it) to (rangeRepeat == it) }) { rangeRepeat = twos[it] }
                                Text("سرعة التلاوة", color = P61Ink.copy(alpha = 0.65f), fontSize = 12.sp)
                                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                                P62ChipRow(
                                    speeds.map { "‎" + p61SpeedLabel(it) + "‎" to (kotlin.math.abs(P52SurahAudio.speed - it) < 0.01f) },
                                ) { P52SurahAudio.setSpeed(context, speeds[it]) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("إخفاء النص (اضغط على الآية لإظهارها)", color = P61Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Switch(checked = hide, onCheckedChange = { hide = it; revealed.clear() })
                                }
                                P55VerseReciterRow()
                            }
                        }
                    }
                }
                // 2 — the verses
                item {
                    val inline = remember(verses) {
                        verses.associate { v ->
                            "n${v.ayah}" to InlineTextContent(
                                Placeholder(width = 1.5.em, height = 1.5.em, placeholderVerticalAlign = PlaceholderVerticalAlign.Center),
                            ) { label ->
                                Box(Modifier.fillMaxSize().padding(2.dp).border(1.dp, P61Gold, CircleShape), contentAlignment = Alignment.Center) {
                                    Text(label, fontFamily = P61Amiri, fontSize = 11.sp, color = P61Ink)
                                }
                            }
                        }
                    }
                    val annotated: AnnotatedString = buildAnnotatedString {
                        starts.clear()
                        verses.forEachIndexed { i, v ->
                            val key = "${v.surah}:${v.ayah}"
                            val shown = !hide || revealed[v.ayah] == true
                            val start = length
                            starts[key] = start
                            if (shown) append(P61Words.body(v)) else {
                                pushStyle(SpanStyle(color = P61Ink.copy(alpha = 0.35f)))
                                append("• • • • •")
                                pop()
                            }
                            append(' ')
                            appendInlineContent("n${v.ayah}", p61Digits(v.ayah))
                            val end = length
                            addStringAnnotation("AYA", key, start, end)
                            when (key) {
                                playing -> addStyle(SpanStyle(background = P61Tan), start, end)
                                selected -> addStyle(SpanStyle(background = Color(0xFFF1E6C6)), start, end)
                            }
                            if (i < verses.lastIndex) append(' ')
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = Color(0xFFFFFDF6),
                        border = BorderStroke(1.dp, Color(0xFFE6DCC2)),
                    ) {
                        Text(
                            text = annotated,
                            inlineContent = inline,
                            style = TextStyle(
                                fontFamily = P61Amiri, fontSize = 26.sp, lineHeight = 46.sp,
                                textAlign = TextAlign.Justify, color = P61Ink,
                            ),
                            onTextLayout = { layout = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).pointerInput(annotated) {
                                detectTapGestures { pos ->
                                    val l = layout ?: return@detectTapGestures
                                    val off = l.getOffsetForPosition(pos)
                                    annotated.getStringAnnotations("AYA", off, off).firstOrNull()?.let { hit ->
                                        val a = hit.item.substringAfter(":").toIntOrNull() ?: return@let
                                        selected = hit.item
                                        if (hide) revealed[a] = !(revealed[a] ?: false)
                                    }
                                }
                            },
                        )
                    }
                }
                // 3 — actions for the tapped verse
                item {
                    val sel = selected
                    if (sel != null) {
                        val s = sel.substringBefore(":").toIntOrNull()
                        val a = sel.substringAfter(":").toIntOrNull()
                        if (s != null && a != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp), color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE6DCC2)),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("الآية ${p61Digits(a)} — ${repo.surahName(s)}", fontWeight = FontWeight.Bold, color = P61Green)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = {
                                            val code = P52SurahAudio.ayahCode(s, a)
                                            P52SurahAudio.customLabels[code] = "حفظ: " + repo.surahName(s)
                                            P52SurahAudio.play(context, List(verseRepeat) { code })
                                        }) { Text("▶ استمع") }
                                        OutlinedButton(onClick = { showMeaning = s to a }) { Text("📖 المفردات") }
                                        if (P61Similar.has(context, s, a)) {
                                            OutlinedButton(onClick = { showSimilar = s to a }) { Text("🔁 المتشابهات") }
                                        }
                                        OutlinedButton(onClick = { onTasmee(s, a, a) }) { Text("🎙 سمّع هذه") }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            // pinned controls
            Column(
                Modifier.fillMaxWidth().background(Color(0xFFF6F0DD)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                P52MiniPlayer()
                message?.let { Text(it, color = P61Wrong, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val one = (from..to).flatMap { a -> List(verseRepeat) { P52SurahAudio.ayahCode(surah, a) } }
                            val all = List(rangeRepeat) { one }.flatten()
                            if (all.size > 600) {
                                message = "المقطع مع التكرار طويل (${p61Digits(all.size)} مرة). قلّل التكرار أو المقطع."
                            } else {
                                message = null
                                all.forEach { P52SurahAudio.customLabels[it] = "حفظ: " + repo.surahName(surah) }
                                P52SurahAudio.play(context, all)
                            }
                        },
                        modifier = Modifier.weight(2f).height(50.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                    ) { Text("▶ ابدأ التكرار", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    OutlinedButton(
                        onClick = { onTasmee(surah, from, to) },
                        modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, P61Gold),
                    ) { Text("🎙 تسميع", fontWeight = FontWeight.Bold, color = P61Gold) }
                }
            }
            P53BottomNav(onBack = onClose, onHome = onClose)
        }
    }
}
