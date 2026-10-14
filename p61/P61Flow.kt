package com.ab.khatma.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranAyah
import com.ab.khatma.quran.QuranPrefsStore
import com.ab.khatma.quran.QuranRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val P61JuzNames = listOf(
    "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
    "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر",
    "الثامن عشر", "التاسع عشر", "العشرون", "الحادي والعشرون", "الثاني والعشرون", "الثالث والعشرون",
    "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون", "السابع والعشرون", "الثامن والعشرون",
    "التاسع والعشرون", "الثلاثون",
)

/** Where each verse and each surah banner sits inside its page item, so the list can scroll to it. */
private class P61Registry {
    val verseY = HashMap<String, Float>()
    val surahY = HashMap<Int, Float>()
}

private class P61Seg(val surahStart: Int?, val verses: List<QuranAyah>) {
    val keys: Set<String> = verses.map { "${it.surah}:${it.ayah}" }.toSet()
}

private fun p61Segments(ayahs: List<QuranAyah>): List<P61Seg> {
    val out = ArrayList<P61Seg>()
    var cur = ArrayList<QuranAyah>()
    var start: Int? = null
    for (a in ayahs) {
        if (a.ayah == 1) {
            if (cur.isNotEmpty()) out.add(P61Seg(start, cur))
            cur = ArrayList()
            start = a.surah
        }
        cur.add(a)
    }
    if (cur.isNotEmpty()) out.add(P61Seg(start, cur))
    return out
}

/**
 * P61: continuous Mushaf reading (scroll down through the whole Quran, surah banners, ayah circles, the recited
 * ayah highlighted and followed). The printed-page reader is untouched and is one switch away.
 */
@Composable
internal fun P61FlowReader(
    repo: QuranRepository,
    prefs: QuranPrefsStore,
    pages: IntRange,
    startPage: Int?,
    juz: Int?,
    surah: Int?,
    assigned: Boolean,
    isContinue: Boolean,
    focusAyah: Pair<Int, Int>?,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    bookmark: Pair<Int, Int>?,
    playingKey: String?,
    highlightedAyahKey: String?,
    audioMessage: String?,
    assignmentCompleted: Boolean,
    onCompleteAssignment: (() -> Unit)?,
    isAudioPlaying: Boolean,
    onStopAudio: () -> Unit,
    onSelectAyah: (QuranAyah) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenSavedPage: (Int) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        P52SurahAudio.init(context)
        P53NightMode.load(context)
    }
    val night = P53NightMode.enabled
    val bg = if (night) Color(0xFF14110C) else P61Paper
    val ink = if (night) Color(0xFFEDE3CC) else P61Ink
    val gold = if (night) Color(0xFFC9A95B) else P61Gold
    val muted = ink.copy(alpha = 0.55f)

    // A juz or an assigned ward stays inside its own pages; everything else is the whole Quran.
    val flowPages = remember(pages, juz, assigned) {
        if (juz != null || assigned) pages.toList().ifEmpty { listOf(1) } else (1..604).toList()
    }
    val initialPage = remember {
        when {
            startPage != null && startPage in flowPages -> startPage
            assigned && juz != null -> prefs.lastPageForJuz(juz).takeIf { it in flowPages } ?: flowPages.first()
            isContinue -> prefs.lastPage().takeIf { it in flowPages } ?: flowPages.first()
            surah != null -> repo.firstPageOfSurah(surah).takeIf { it in flowPages } ?: flowPages.first()
            else -> flowPages.first()
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = flowPages.indexOf(initialPage).coerceAtLeast(0))
    val registry = remember(fontSize) { P61Registry() }
    val scope = rememberCoroutineScope()

    val currentPage by remember {
        derivedStateOf { flowPages[listState.firstVisibleItemIndex.coerceIn(0, flowPages.lastIndex)] }
    }

    var controls by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showMarks by remember { mutableStateOf(false) }
    var showReciters by remember { mutableStateOf(false) }
    var khatm by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var savedPages by remember { mutableStateOf(prefs.savedPages()) }

    // ---- scrolling helpers -------------------------------------------------------------------------
    suspend fun scrollToVerse(key: String, follow: Boolean) {
        val parts = key.split(":")
        val ay = repo.ayah(parts.getOrNull(0)?.toIntOrNull() ?: return, parts.getOrNull(1)?.toIntOrNull() ?: return) ?: return
        val idx = flowPages.indexOf(ay.page)
        if (idx < 0) return
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) listState.scrollToItem(idx)
        var tries = 0
        while (registry.verseY[key] == null && tries < 30) { delay(40); tries++ }
        val y = registry.verseY[key] ?: return
        if (follow) {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
            val vh = listState.layoutInfo.viewportSize.height
            val verseTop = (info?.offset ?: 0) + y
            if (verseTop < vh * 0.14f || verseTop > vh * 0.60f) listState.animateScrollBy(verseTop - vh * 0.24f)
        } else {
            listState.scrollToItem(idx, (y - 24f).toInt().coerceAtLeast(0))
        }
    }

    LaunchedEffect(Unit) {
        when {
            focusAyah != null -> scrollToVerse("${focusAyah.first}:${focusAyah.second}", false)
            surah != null && startPage == null && !assigned && juz == null && !isContinue -> {
                val first = repo.ayah(surah, 1)
                val idx = if (first != null) flowPages.indexOf(first.page) else -1
                if (idx >= 0) {
                    var tries = 0
                    while (registry.surahY[surah] == null && tries < 30) { delay(40); tries++ }
                    registry.surahY[surah]?.let { listState.scrollToItem(idx, (it - 8f).toInt().coerceAtLeast(0)) }
                }
            }
        }
    }

    // The recited verse is followed unless the reader is dragging the page.
    LaunchedEffect(playingKey) {
        val key = playingKey ?: return@LaunchedEffect
        if (!listState.isScrollInProgress) scrollToVerse(key, true)
    }

    // Top icons hide while reading forward and come back when scrolling up.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1200); settled = true }
    LaunchedEffect(listState) {
        var prev = listState.firstVisibleItemIndex * 100000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 100000 + listState.firstVisibleItemScrollOffset }
            .collect { now ->
                if (settled && scrub == null) {
                    if (now - prev > 30) controls = false else if (prev - now > 30) controls = true
                }
                prev = now
            }
    }

    // Progress: the last page is saved at once; the daily counter waits until the page has been on screen a moment.
    LaunchedEffect(currentPage) {
        prefs.saveLastPage(currentPage)
        prefs.saveLastJuz(repo.pageJuz(currentPage))
        if (assigned && juz != null) prefs.saveLastPageForJuz(juz, currentPage)
        delay(1500)
        runCatching { com.ab.khatma.secure.P58Stats.recordPage(context, currentPage) }
    }
    LaunchedEffect(currentPage, listState.canScrollForward) {
        if (currentPage == 604 && !listState.canScrollForward && flowPages.size > 1) khatm = true
    }
    if (khatm) P58KhatmDialog { khatm = false }

    fun firstVisibleVerse(): QuranAyah? {
        val idx = listState.firstVisibleItemIndex.coerceIn(0, flowPages.lastIndex)
        val off = listState.firstVisibleItemScrollOffset
        val verses = repo.loadPage(flowPages[idx])
        verses.firstOrNull { (registry.verseY["${it.surah}:${it.ayah}"] ?: Float.MAX_VALUE) >= off - 12f }?.let { return it }
        return flowPages.getOrNull(idx + 1)?.let { repo.loadPage(it).firstOrNull() } ?: verses.firstOrNull()
    }

    fun playFromHere() {
        val first = firstVisibleVerse() ?: return
        val startIdx = flowPages.indexOf(first.page).coerceAtLeast(0)
        val codes = ArrayList<Int>()
        for (i in startIdx..flowPages.lastIndex) {
            for (v in repo.loadPage(flowPages[i])) {
                if (i == startIdx && (v.surah < first.surah || (v.surah == first.surah && v.ayah < first.ayah))) continue
                codes.add(P52SurahAudio.ayahCode(v.surah, v.ayah))
            }
        }
        if (codes.isEmpty()) return
        P52SurahAudio.play(context, codes)
        P52SurahAudio.followReader = true
    }

    if (showReciters) P55ReciterPicker(onDismiss = { showReciters = false }) {
        P52SurahAudio.selectReciter(context, it.id)
        showReciters = false
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize().background(bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 54.dp, bottom = 210.dp),
                ) {
                    items(count = flowPages.size, key = { flowPages[it] }) { idx ->
                        P61PageBlock(
                            page = flowPages[idx],
                            repo = repo,
                            fontSize = fontSize,
                            ink = ink, gold = gold, muted = muted, night = night,
                            playingKey = playingKey,
                            selectedKey = highlightedAyahKey,
                            bookmark = bookmark,
                            registry = registry,
                            onSelectAyah = onSelectAyah,
                        )
                    }
                    item(key = "p61_end") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (flowPages.last() == 604) {
                                Text("صَدَقَ ٱللَّهُ ٱلْعَظِيمُ", fontFamily = P61Amiri, fontSize = 24.sp, color = gold)
                                Spacer(Modifier.height(12.dp))
                            }
                            if (assigned && onCompleteAssignment != null) {
                                if (assignmentCompleted) Text("تمت القراءة ✓", color = gold, fontWeight = FontWeight.Bold)
                                else Button(
                                    onClick = onCompleteAssignment,
                                    colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                                ) { Text("أتممت القراءة ✓") }
                            }
                        }
                    }
                }
            }

            // ---- top icons ---------------------------------------------------------------------------
            AnimatedVisibility(
                visible = controls,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth().background(bg.copy(alpha = 0.94f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenIndex) { Icon(Icons.Rounded.Menu, "الفهرس", tint = P61Green) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "بحث", tint = P61Green) }
                    IconButton(onClick = { savedPages = prefs.savedPages(); showMarks = true }) {
                        Icon(Icons.Rounded.BookmarkBorder, "علاماتي", tint = P61Green)
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "إعدادات القراءة", tint = P61Green) }
                }
            }

            // ---- bottom: message, player, seek bar --------------------------------------------------
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                audioMessage?.let {
                    Text(it, color = P61Wrong, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                if (P52SurahAudio.isActive) {
                    P61FloatingPlayer(onPickReciter = { showReciters = true })
                } else if (controls || isAudioPlaying) {
                    P61AudioStrip(
                        playing = isAudioPlaying,
                        night = night,
                        onPlay = { playFromHere() },
                        onStop = onStopAudio,
                        onPickReciter = { showReciters = true },
                    )
                }
                AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut()) {
                    Column {
                        val s = scrub
                        if (s != null) {
                            val p = flowPages[s.roundToInt().coerceIn(0, flowPages.lastIndex)]
                            Surface(
                                shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 4.dp,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(
                                    "${repo.surahName(repo.pageSurah(p))} — صفحة ${p61Digits(p)}",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = P61Green, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().background(bg.copy(alpha = 0.92f)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val index = flowPages.indexOf(currentPage).coerceAtLeast(0)
                            Slider(
                                value = s ?: index.toFloat(),
                                onValueChange = { scrub = it },
                                onValueChangeFinished = { scrub = null },
                                valueRange = 0f..flowPages.lastIndex.coerceAtLeast(1).toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = P61Green, activeTrackColor = P61Green.copy(alpha = 0.55f),
                                    inactiveTrackColor = gold.copy(alpha = 0.3f),
                                ),
                            )
                            Text(p61Digits(currentPage), color = muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }
    }

    // Dragging the bar jumps straight to that page.
    LaunchedEffect(scrub) { scrub?.let { listState.scrollToItem(it.roundToInt().coerceIn(0, flowPages.lastIndex)) } }

    if (showSettings) {
        P61SettingsDialog(
            fontSize = fontSize,
            onFontSizeChange = onFontSizeChange,
            night = night,
            onNight = { P53NightMode.toggle(context) },
            onPages = { showSettings = false; P61Flow.set(context, false) },
            onHome = { showSettings = false; onHome() },
            onBack = { showSettings = false; onBack() },
            onDismiss = { showSettings = false },
        )
    }
    if (showMarks) {
        P61MarksDialog(
            repo = repo,
            currentPage = currentPage,
            savedPages = savedPages,
            bookmark = bookmark,
            onToggle = { prefs.toggleSavedPage(currentPage); savedPages = prefs.savedPages() },
            onOpenPage = { showMarks = false; onOpenSavedPage(it) },
            onOpenBookmark = { s, a -> showMarks = false; scope.launch { scrollToVerse("$s:$a", false) } },
            onDismiss = { showMarks = false },
        )
    }
}

// ------------------------------------------------------------------------------------ one page ----

@Composable
private fun P61PageBlock(
    page: Int,
    repo: QuranRepository,
    fontSize: Float,
    ink: Color,
    gold: Color,
    muted: Color,
    night: Boolean,
    playingKey: String?,
    selectedKey: String?,
    bookmark: Pair<Int, Int>?,
    registry: P61Registry,
    onSelectAyah: (QuranAyah) -> Unit,
) {
    val ayahs = remember(page) { repo.loadPage(page) }
    val segments = remember(page) { p61Segments(ayahs) }
    val first = ayahs.firstOrNull()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("الجزء " + P61JuzNames.getOrElse((first?.juz ?: 1) - 1) { "" }, fontSize = 11.sp, color = muted)
            Text(if (first != null) repo.surahName(first.surah) else "", fontSize = 11.sp, color = muted)
        }
        segments.forEach { seg ->
            seg.surahStart?.let { s ->
                val banner = repo.ayah(s, 1)
                P61SurahBanner(
                    name = repo.surahName(s), ink = ink, gold = gold, night = night,
                    modifier = Modifier.onGloballyPositioned { registry.surahY[s] = it.positionInParent().y },
                )
                val basmala = banner?.let { P61Words.split(it).first }
                if (basmala != null) {
                    Text(
                        basmala,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        fontFamily = P61Amiri, fontSize = (fontSize * 0.95f).sp, lineHeight = (fontSize * 1.8f).sp,
                        textAlign = TextAlign.Center, color = ink,
                    )
                }
            }
            P61Segment(
                seg = seg, fontSize = fontSize, ink = ink, gold = gold, night = night,
                playingKey = playingKey, selectedKey = selectedKey, bookmark = bookmark,
                registry = registry, onSelectAyah = onSelectAyah, repo = repo,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, gold.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 2.dp),
            ) { Text(p61Digits(page), fontFamily = P61Amiri, fontSize = 14.sp, color = gold) }
        }
    }
}

@Composable
private fun P61Segment(
    seg: P61Seg,
    repo: QuranRepository,
    fontSize: Float,
    ink: Color,
    gold: Color,
    night: Boolean,
    playingKey: String?,
    selectedKey: String?,
    bookmark: Pair<Int, Int>?,
    registry: P61Registry,
    onSelectAyah: (QuranAyah) -> Unit,
) {
    // Only the page that owns the highlighted verse is rebuilt when the recitation moves on.
    val pk = playingKey?.takeIf { it in seg.keys }
    val sk = selectedKey?.takeIf { it in seg.keys }
    val bk = bookmark?.let { "${it.first}:${it.second}" }?.takeIf { it in seg.keys }
    val playBg = if (night) Color(0xFF52441F) else P61Tan
    val selBg = if (night) Color(0xFF3A3017) else Color(0xFFF1E6C6)

    val built = remember(seg, pk, sk, bk, night) {
        val starts = ArrayList<Pair<String, Int>>()
        val text = buildAnnotatedString {
            seg.verses.forEachIndexed { i, a ->
                val key = "${a.surah}:${a.ayah}"
                val body = if (a.ayah == 1) P61Words.body(a) else a.text
                val start = length
                starts.add(key to start)
                append(body)
                append(' ')
                appendInlineContent("n${a.ayah}", p61Digits(a.ayah))
                val end = length
                addStringAnnotation("AYA", key, start, end)
                when (key) {
                    pk -> addStyle(SpanStyle(background = playBg), start, end)
                    sk -> addStyle(SpanStyle(background = selBg), start, end)
                    bk -> addStyle(SpanStyle(background = gold.copy(alpha = 0.16f)), start, end)
                }
                if (i < seg.verses.lastIndex) append(' ')
            }
        }
        text to starts
    }
    val annotated: AnnotatedString = built.first
    val inline = remember(seg, fontSize, ink, gold) {
        seg.verses.associate { a ->
            "n${a.ayah}" to InlineTextContent(
                Placeholder(width = 1.5.em, height = 1.5.em, placeholderVerticalAlign = PlaceholderVerticalAlign.Center),
            ) { label ->
                Box(Modifier.fillMaxSize().padding(2.dp).border(1.dp, gold, CircleShape), contentAlignment = Alignment.Center) {
                    Text(label, fontFamily = P61Amiri, fontSize = (fontSize * 0.42f).sp, color = ink)
                }
            }
        }
    }

    var segY by remember { mutableFloatStateOf(-1f) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(layout, segY, built) {
        val l = layout ?: return@LaunchedEffect
        if (segY < 0f) return@LaunchedEffect
        built.second.forEach { (key, start) ->
            if (start < l.layoutInput.text.length) registry.verseY[key] = segY + l.getLineTop(l.getLineForOffset(start))
        }
    }

    Text(
        text = annotated,
        inlineContent = inline,
        style = TextStyle(
            fontFamily = P61Amiri, fontSize = fontSize.sp, lineHeight = (fontSize * 1.95f).sp,
            textAlign = TextAlign.Justify, color = ink,
        ),
        onTextLayout = { layout = it },
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { segY = it.positionInParent().y }
            .padding(horizontal = 16.dp)
            .pointerInput(annotated) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val offset = l.getOffsetForPosition(pos)
                    annotated.getStringAnnotations("AYA", offset, offset).firstOrNull()?.let { hit ->
                        val parts = hit.item.split(":")
                        val s = parts.getOrNull(0)?.toIntOrNull()
                        val a = parts.getOrNull(1)?.toIntOrNull()
                        if (s != null && a != null) repo.ayah(s, a)?.let(onSelectAyah)
                    }
                }
            },
    )
}

@Composable
private fun P61SurahBanner(name: String, ink: Color, gold: Color, night: Boolean, modifier: Modifier = Modifier) {
    val fill = if (night) Color(0xFF2B2415) else Color(0xFFEFE3C2)
    Box(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp)).background(fill)
            .border(1.5.dp, gold, RoundedCornerShape(10.dp)).padding(3.dp)
            .border(0.8.dp, gold.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("۞", fontFamily = P61Amiri, color = gold, fontSize = 24.sp)
            Text("سُورَةُ $name", fontFamily = P61Amiri, fontSize = 26.sp, color = ink, textAlign = TextAlign.Center)
            Text("۞", fontFamily = P61Amiri, color = gold, fontSize = 24.sp)
        }
    }
}

// ------------------------------------------------------------------------------------- player ----

@Composable
private fun P61AudioStrip(playing: Boolean, night: Boolean, onPlay: () -> Unit, onStop: () -> Unit, onPickReciter: () -> Unit) {
    val r = P52SurahAudio.savedReciter
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = if (night) Color(0xFF2A2417) else Color(0xFFEFE9D8),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (playing) onStop else onPlay) {
                Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (playing) "إيقاف" else "تشغيل من هنا", tint = P61Green)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onPickReciter) {
                Text(r.name + " ▾", color = P61Green, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The card from the reference app: close, reciter, more, then previous / pause / next. */
@Composable
private fun P61FloatingPlayer(onPickReciter: () -> Unit) {
    val context = LocalContext.current
    val item = P52SurahAudio.currentSurah ?: return
    val r = P52SurahAudio.reciter
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(22.dp), color = Color(0xFFEDE8D8), shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { P52SurahAudio.stop() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, "إغلاق", tint = P61Ink.copy(alpha = 0.7f))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onPickReciter) {
                    Text(r.name + " ▾", color = P61Green, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                P52SurahAudio.itemTitle(item), fontSize = 11.sp, color = P61Ink.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "المزيد", tint = P61Green) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                            DropdownMenuItem(
                                text = { Text((if (kotlin.math.abs(P52SurahAudio.speed - v) < 0.01f) "✓ " else "") + "السرعة ${p61SpeedLabel(v)}") },
                                onClick = { P52SurahAudio.setSpeed(context, v); menu = false },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (P52SurahAudio.sleepMinutes == 0) "⏲ مؤقت النوم" else "⏲ مؤقت النوم: ${P52SurahAudio.sleepMinutes} د") },
                            onClick = { P52SurahAudio.cycleSleepTimer() },
                        )
                    }
                }
                IconButton(onClick = { P52SurahAudio.previous() }) { Icon(Icons.Rounded.SkipNext, "السابق", tint = P61Green) }
                if (P52SurahAudio.isLoading) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = P61Green, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { P52SurahAudio.togglePause() }) {
                        Icon(
                            if (P52SurahAudio.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (P52SurahAudio.isPlaying) "إيقاف مؤقت" else "تشغيل", tint = P61Green, modifier = Modifier.size(34.dp),
                        )
                    }
                }
                IconButton(onClick = { P52SurahAudio.next() }) { Icon(Icons.Rounded.SkipPrevious, "التالي", tint = P61Green) }
                TextButton(onClick = { P52SurahAudio.setSpeed(context, p61NextSpeed(P52SurahAudio.speed)) }) {
                    Text(p61SpeedLabel(P52SurahAudio.speed), color = P61Green, fontWeight = FontWeight.Bold)
                }
            }
            P52SurahAudio.message?.let { Text(it, color = P61Wrong, fontSize = 11.sp) }
        }
    }
}

internal fun p61SpeedLabel(speed: Float): String = when {
    kotlin.math.abs(speed - 0.75f) < 0.01f -> "0.75×"
    kotlin.math.abs(speed - 1.25f) < 0.01f -> "1.25×"
    kotlin.math.abs(speed - 1.5f) < 0.01f -> "1.5×"
    kotlin.math.abs(speed - 2f) < 0.01f -> "2×"
    else -> "1×"
}

internal fun p61NextSpeed(speed: Float): Float {
    val steps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    val i = steps.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }
    return steps[(if (i < 0) 1 else i + 1) % steps.size]
}

// ------------------------------------------------------------------------------------ dialogs ----

@Composable
private fun P61SettingsDialog(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    night: Boolean,
    onNight: () -> Unit,
    onPages: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(20.dp), color = P61Paper) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إعدادات القراءة", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = P61Green)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("حجم الخط", Modifier.weight(1f), color = P61Ink)
                        OutlinedButton(onClick = { onFontSizeChange(fontSize - 2f) }) { Text("−", fontSize = 20.sp) }
                        Text(fontSize.toInt().toString(), Modifier.width(48.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { onFontSizeChange(fontSize + 2f) }) { Text("+", fontSize = 20.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌙 القراءة الليلية", Modifier.weight(1f), color = P61Ink)
                        Switch(checked = night, onCheckedChange = { onNight() })
                    }
                    OutlinedButton(onClick = onPages, modifier = Modifier.fillMaxWidth()) { Text("📖 عرض صفحات المصحف (الشكل القديم)") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("رجوع") }
                        Button(
                            onClick = onHome, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                        ) { Text("الرئيسية") }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
                }
            }
        }
    }
}

@Composable
private fun P61MarksDialog(
    repo: QuranRepository,
    currentPage: Int,
    savedPages: Set<Int>,
    bookmark: Pair<Int, Int>?,
    onToggle: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onOpenBookmark: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(20.dp), color = P61Paper) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("علاماتي", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = P61Green)
                    Button(
                        onClick = onToggle, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                    ) { Text(if (currentPage in savedPages) "إزالة علامة صفحة $currentPage" else "حفظ علامة عند صفحة $currentPage") }
                    bookmark?.let { (s, a) ->
                        OutlinedButton(onClick = { onOpenBookmark(s, a) }, modifier = Modifier.fillMaxWidth()) {
                            Text("آخر علامة آية: ${repo.surahName(s)} — $a")
                        }
                    }
                    if (savedPages.isEmpty()) Text("لم تحفظ علامات صفحات بعد.", color = P61Ink.copy(alpha = 0.6f), fontSize = 13.sp)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        savedPages.sorted().forEach { p ->
                            TextButton(onClick = { onOpenPage(p) }, modifier = Modifier.fillMaxWidth()) {
                                Text("صفحة $p — ${repo.surahName(repo.pageSurah(p))}")
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
                }
            }
        }
    }
}
