package com.ab.khatma.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.R
import com.ab.khatma.quran.HusaryAudioConfig
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranAyah
import com.ab.khatma.quran.QuranExplanation
import com.ab.khatma.quran.QuranPrefsStore
import com.ab.khatma.quran.QuranRepository
import com.ab.khatma.quran.QuranSearchEngine
import com.ab.khatma.quran.QuranSearchHit
import com.ab.khatma.quran.QuranSearchType
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow

private enum class QuranMode { MADINA_PAGES, FLEX_TEXT }
private enum class ExplanationMode { WORDS, TAFSIR }

private data class ReadingTarget(
    val key: String,
    val title: String,
    val pages: IntRange,
    val juz: Int? = null,
    val surah: Int? = null,
    val assigned: Boolean = false,
    val startPage: Int? = null,
    val focusAyah: Pair<Int, Int>? = null,
)

@Composable
fun QuranScreen(
    initialJuz: Int?,
    initialPages: IntRange? = null,
    assignmentCompleted: Boolean = false,
    onCompleteAssignment: (() -> Unit)? = null,
    onBack: () -> Unit,
    onHome: () -> Unit = onBack,
) {
    val context = LocalContext.current
    com.ab.khatma.guide.P85Guide.Host()
    val repo = remember { QuranRepository(context) }
    // P21: prepare the offline index only when a search starts, on a background thread.
    // Keep it cached when moving between the search, index and reader screens.
    val searchEngine = remember(repo) { lazy(LazyThreadSafetyMode.SYNCHRONIZED) { QuranSearchEngine(repo) } }
    val prefs = remember { QuranPrefsStore(context) }
    val player = remember { MediaPlayer() }

    var target by remember(initialJuz, initialPages) {
        mutableStateOf(
            initialPages?.takeIf { it.first in 1..604 && it.last in it.first..604 }?.let { range ->
                ReadingTarget(key = "assigned_pages_${range.first}_${range.last}",
                    title = "ورد اليوم — الصفحات ${range.first} إلى ${range.last}",
                    pages = range, assigned = true, startPage = range.first)
            } ?: initialJuz?.coerceIn(1, 30)?.let { juz ->
                ReadingTarget(
                    key = "assigned_juz_$juz",
                    title = "قراءتك اليوم — الجزء $juz",
                    pages = repo.juzPageRange(juz),
                    juz = juz,
                    assigned = true,
                )
            }
        )
    }
    var showSearch by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(prefs.fontSize()) }
    var mode by remember {
        mutableStateOf(runCatching { QuranMode.valueOf(prefs.mode()) }.getOrDefault(QuranMode.MADINA_PAGES))
    }
    var bookmark by remember { mutableStateOf(prefs.bookmark()) }
    var selectedAyah by remember { mutableStateOf<QuranAyah?>(null) }
    var p67SheetAyah by remember { mutableStateOf<QuranAyah?>(null) }
    var highlightedAyahKey by remember {
        mutableStateOf(prefs.lastReadAyah()?.let { "${it.first}:${it.second}" })
    }
    var explanationMode by remember { mutableStateOf<ExplanationMode?>(null) }
    var playingKey by remember { mutableStateOf<String?>(null) }
    var audioLoadingKey by remember { mutableStateOf<String?>(null) }
    var audioMessage by remember { mutableStateOf<String?>(null) }
    var audioQueue by remember { mutableStateOf<List<QuranAyah>>(emptyList()) }
    var audioQueueIndex by remember { mutableIntStateOf(-1) }
    var pendingAudioIndex by remember { mutableIntStateOf(-1) }
    var showAudioPicker by remember { mutableIntStateOf(0) }
    val audioConfig = remember { repo.husaryAudio() }

    LaunchedEffect(fontSize) { prefs.saveFontSize(fontSize) }
    LaunchedEffect(mode) { prefs.saveMode(mode.name) }

    DisposableEffect(Unit) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        onDispose { runCatching { player.release() } }
    }

    fun stopAudio() {
        audioQueue = emptyList()
        audioQueueIndex = -1
        pendingAudioIndex = -1
        runCatching {
            if (player.isPlaying) player.stop()
            player.reset()
        }
        playingKey = null
        audioLoadingKey = null
    }

    fun playAyah(aya: QuranAyah, fromSequence: Boolean = false) {
        P52SurahAudio.pause()
        if (!fromSequence) {
            audioQueue = emptyList()
            audioQueueIndex = -1
            pendingAudioIndex = -1
        }
        val cfg = audioConfig
        if (cfg == null) {
            audioMessage = "مصدر صوت الحصري غير متاح في هذه النسخة."
            return
        }
        val key = "${aya.surah}:${aya.ayah}"
        if (playingKey == key || audioLoadingKey == key) {
            stopAudio()
            return
        }
        runCatching {
            player.reset()
            audioMessage = null
            audioLoadingKey = key
            val p55Url = P52SurahAudio.savedReciter.ayahUrl(aya.surah, aya.ayah)
            if (p55Url == null) audioMessage = P52SurahAudio.savedReciter.name + " متاح للسورة كاملة فقط — الآية بصوت الحصري."
            player.setDataSource(p55Url ?: cfg.ayahUrl(aya.surah, aya.ayah))
            player.setOnPreparedListener {
                audioLoadingKey = null
                playingKey = key
                highlightedAyahKey = key
                prefs.saveLastReadAyah(aya.surah, aya.ayah)
                P52SurahAudio.applySpeed(it)
                it.start()
            }
            player.setOnCompletionListener {
                playingKey = null
                audioLoadingKey = null
                if (audioQueueIndex >= 0 && audioQueueIndex + 1 < audioQueue.size) {
                    pendingAudioIndex = audioQueueIndex + 1
                } else {
                    audioQueue = emptyList()
                    audioQueueIndex = -1
                }
            }
            player.setOnErrorListener { _, _, _ ->
                playingKey = null
                audioLoadingKey = null
                audioQueue = emptyList()
                audioQueueIndex = -1
                audioMessage = "تعذر تشغيل التلاوة. تأكد من الاتصال بالإنترنت."
                true
            }
            player.prepareAsync()
        }.onFailure {
            playingKey = null
            audioLoadingKey = null
            audioQueue = emptyList()
            audioQueueIndex = -1
            audioMessage = "تعذر تشغيل التلاوة."
        }
    }

    // Sequencing happens on the UI state loop; a MediaPlayer callback never blocks
    // waiting for hundreds of network MP3 files, and a failure stops the queue.
    LaunchedEffect(pendingAudioIndex) {
        val index = pendingAudioIndex
        if (index >= 0) {
            pendingAudioIndex = -1
            audioQueue.getOrNull(index)?.let { aya ->
                audioQueueIndex = index
                playAyah(aya, fromSequence = true)
            }
        }
    }
    fun playSequence(surah: Int, firstAyah: Int, toEnd: Boolean) {
        val rows = repo.loadSurah(surah).filter { it.ayah >= firstAyah }
        val sequence = if (toEnd) rows else rows.take(1)
        if (sequence.isEmpty()) { audioMessage = "رقم الآية غير صحيح"; return }
        stopAudio()
        if (toEnd && sequence.size > 1) {
            // P63: a whole run of verses is played by the queue player, which prepares the next verse while this one is heard.
            P52SurahAudio.play(context, sequence.map { P52SurahAudio.ayahCode(it.surah, it.ayah) })
            P52SurahAudio.followReader = true
            audioMessage = if (P52SurahAudio.savedReciter.ayahUrl(surah, firstAyah) == null)
                P52SurahAudio.savedReciter.name + " متاح للسورة كاملة فقط — الآيات بصوت الحصري." else null
            return
        }
        audioQueue = sequence
        audioQueueIndex = 0
        playAyah(sequence.first(), fromSequence = true)
    }

    DisposableEffect(Unit) {
        P52SurahAudio.init(context)
        P52SurahAudio.beforePlay = { stopAudio() }
        onDispose { P52SurahAudio.beforePlay = null }
    }
    // P60: when the recitation moves on to the next surah of the list, the Mushaf moves with it.
    val p60FollowSurah = P52SurahAudio.followKey?.substringBefore(":")?.toIntOrNull()
    LaunchedEffect(p60FollowSurah) {
        val current = target
        if (!P61Flow.enabled && p60FollowSurah != null && current != null && current.surah != null && current.surah != p60FollowSurah) {
            target = ReadingTarget(
                key = "surah_$p60FollowSurah",
                title = "سورة ${repo.surahName(p60FollowSurah)}",
                pages = repo.surahPageRange(p60FollowSurah),
                surah = p60FollowSurah,
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (!repo.isBundled()) {
            MissingQuranScreen(onBack)
            return@CompositionLocalProvider
        }

        if (showAudioPicker in 1..114) {
            P42AudioPicker(
                surah = showAudioPicker,
                title = repo.surahName(showAudioPicker),
                maxAyah = repo.loadSurah(showAudioPicker).size,
                onDismiss = { showAudioPicker = 0 },
                onPlay = { first, toEnd ->
                    playSequence(showAudioPicker, first, toEnd)
                    showAudioPicker = 0
                },
            )
        }
        p67SheetAyah?.let { aya ->
            P67AyahSheet(
                aya = aya,
                repo = repo,
                bookmark = bookmark,
                onBookmark = {
                    prefs.saveBookmark(aya.surah, aya.ayah)
                    bookmark = aya.surah to aya.ayah
                },
                onMore = {
                    p67SheetAyah = null
                    explanationMode = null
                    selectedAyah = aya
                },
                onDismiss = { p67SheetAyah = null },
            )
        }
        selectedAyah?.let { aya ->
            AyahActionsSheet(
                aya = aya,
                repo = repo,
                audioConfig = audioConfig,
                playingKey = playingKey,
                audioLoadingKey = audioLoadingKey,
                bookmark = bookmark,
                explanationMode = explanationMode,
                onExplanationMode = { explanationMode = it },
                onPlay = { playAyah(aya) },
                onBookmark = {
                    prefs.saveBookmark(aya.surah, aya.ayah)
                    bookmark = aya.surah to aya.ayah
                },
                onDismiss = {
                    selectedAyah = null
                    explanationMode = null
                },
            )
        }

        val currentTarget = target
        BackHandler(enabled = showSearch) { showSearch = false }
        if (showSearch) {
            QuranSearchScreen(
                repo = repo,
                searchEngine = searchEngine,
                onBack = { showSearch = false },
                onHome = onHome,
                onOpenSurah = { surah ->
                    showSearch = false
                    target = ReadingTarget(
                        key = "search_surah_$surah",
                        title = "سورة ${repo.surahName(surah)}",
                        pages = repo.surahPageRange(surah),
                        surah = surah,
                    )
                },
                onOpenAyah = { aya ->
                    showSearch = false
                    highlightedAyahKey = "${aya.surah}:${aya.ayah}"
                    target = ReadingTarget(
                        key = "search_ayah_${aya.surah}_${aya.ayah}",
                        title = "سورة ${repo.surahName(aya.surah)}",
                        pages = repo.surahPageRange(aya.surah),
                        surah = aya.surah,
                        startPage = aya.page,
                        focusAyah = aya.surah to aya.ayah,
                    )
                },
            )
        } else if (currentTarget == null) {
            QuranIndexScreen(
                repo = repo,
                prefs = prefs,
                onBack = onBack,
                onHome = onHome,
                onSearch = { showSearch = true },
                onContinue = {
                    val page = prefs.lastPage()
                    target = ReadingTarget(
                        key = "continue_$page",
                        title = "المصحف الشريف",
                        pages = 1..604,
                    )
                },
                onOpenJuz = { juz ->
                    target = ReadingTarget(
                        key = "juz_$juz",
                        title = "الجزء $juz",
                        pages = repo.juzPageRange(juz),
                        juz = juz,
                    )
                },
                onOpenSurah = { surah ->
                    target = ReadingTarget(
                        key = "surah_$surah",
                        title = "سورة ${repo.surahName(surah)}",
                        pages = repo.surahPageRange(surah),
                        surah = surah,
                    )
                },
                onOpenPage = { page ->
                    target = ReadingTarget(key = "saved_page_$page", title = "المنفردات — صفحة $page",
                        pages = 1..604, startPage = page)
                },
            )
        } else {
            key(currentTarget.key) {
                LaunchedEffect(Unit) { P61Flow.load(context) }
                if (P61Flow.enabled) P61FlowReader(
                    repo = repo,
                    prefs = prefs,
                    pages = currentTarget.pages,
                    startPage = currentTarget.startPage,
                    juz = currentTarget.juz,
                    surah = currentTarget.surah,
                    assigned = currentTarget.assigned,
                    isContinue = currentTarget.key.startsWith("continue_"),
                    focusAyah = currentTarget.focusAyah,
                    fontSize = fontSize,
                    onFontSizeChange = { fontSize = it.coerceIn(20f, 44f) },
                    bookmark = bookmark,
                    playingKey = playingKey ?: P52SurahAudio.followKey,
                    highlightedAyahKey = highlightedAyahKey,
                    audioMessage = audioMessage,
                    assignmentCompleted = assignmentCompleted,
                    onCompleteAssignment = if (currentTarget.assigned) onCompleteAssignment else null,
                    isAudioPlaying = playingKey != null || audioLoadingKey != null,
                    onStopAudio = ::stopAudio,
                    onSelectAyah = { aya ->
                        explanationMode = null
                        selectedAyah = aya
                        highlightedAyahKey = "${aya.surah}:${aya.ayah}"
                        prefs.saveLastReadAyah(aya.surah, aya.ayah)
                    },
                    onBack = if (currentTarget.assigned) onBack else ({ target = null }),
                    onHome = onHome,
                    onSearch = { showSearch = true },
                    onOpenIndex = { target = null },
                    onOpenSavedPage = { page ->
                        target = ReadingTarget(key = "saved_page_$page", title = "علاماتي — صفحة $page",
                            pages = 1..604, startPage = page)
                    },
                ) else QuranReaderScreen(
                    repo = repo,
                    prefs = prefs,
                    target = currentTarget,
                    mode = mode,
                    onModeChange = { mode = it },
                    fontSize = fontSize,
                    onFontSizeChange = { fontSize = it.coerceIn(20f, 44f) },
                    bookmark = bookmark,
                    playingKey = playingKey ?: P52SurahAudio.followKey,
                    highlightedAyahKey = highlightedAyahKey,
                    audioMessage = audioMessage,
                    assignmentCompleted = assignmentCompleted,
                    onCompleteAssignment = if (currentTarget.assigned) onCompleteAssignment else null,
                    onSelectAyah = { aya ->
                        explanationMode = null
                        selectedAyah = aya
                        highlightedAyahKey = "${aya.surah}:${aya.ayah}"
                        prefs.saveLastReadAyah(aya.surah, aya.ayah)
                    },
                    onPlayPrintedAyah = { surah, ayah ->
                        repo.ayah(surah, ayah)?.let { verse ->
                            highlightedAyahKey = "$surah:$ayah"
                            prefs.saveLastReadAyah(surah, ayah)
                            p67SheetAyah = verse
                        }
                    },
                    onBack = if (currentTarget.assigned) onBack else ({ target = null }),
                    onHome = onHome,
                    onListen = { surah -> showAudioPicker = surah },
                    isAudioPlaying = playingKey != null || audioLoadingKey != null,
                    onStopAudio = ::stopAudio,
                    onOpenSavedPage = { page ->
                        target = ReadingTarget(key = "saved_page_$page", title = "علاماتي — صفحة $page",
                            pages = 1..604, startPage = page)
                    },
                    onOpenIndex = { target = null },
                    onSearch = { showSearch = true },
                    onNavigateSurah = { surah, openAtEnd ->
                        if (surah in 1..114) {
                            // A new selected surah always has its own isolated reader,
                            // including when two surahs share one physical Quran page.
                            val range = repo.surahPageRange(surah)
                            target = ReadingTarget(
                                key = "surah_$surah",
                                title = "سورة ${repo.surahName(surah)}",
                                pages = range,
                                surah = surah,
                                startPage = if (openAtEnd) range.last else range.first,
                            )
                        }
                    },
                )
            }
        }
    }
}

// P20: visual polish only. Reuses the P19 search and Qur'an navigation callbacks.
@Composable
private fun MushafSearchButton(onClick: () -> Unit, onDark: Boolean = true) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (onDark) Color(0xFF0E5C46) else Color(0xFFF5EAD1),
        border = BorderStroke(1.dp, Color(0xFFCEAA62).copy(alpha = 0.85f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = if (onDark) Color.White else Color(0xFF064D3B),
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "بحث",
                color = if (onDark) Color.White else Color(0xFF064D3B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MushafHeroHeader(
    title: String,
    onBack: () -> Unit,
    onSearch: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    showBack: Boolean = true,
) {
    val green = Color(0xFF064D3B)
    val gold = Color(0xFFDFBD78)
    Box(
        modifier = Modifier.fillMaxWidth().height(130.dp).background(green),
    ) {
        // Reuse original, bundled art. No remote fetching or text embedded in image.
        Image(
            painter = painterResource(if (onSearch == null) R.drawable.home_card_mushaf else R.drawable.home_hero_masjid),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(190.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.78f,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.45f to green.copy(alpha = 0.76f),
                    0.78f to green,
                    1f to green,
                )
            )
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showBack) Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0B624C),
                    border = BorderStroke(1.dp, gold.copy(alpha = 0.75f)),
                ) {
                    Text("رجوع", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
                if (onSearch != null) MushafSearchButton(onClick = onSearch)
                if (onHome != null) TextButton(onClick = onHome) {
                    Icon(Icons.Rounded.Home, contentDescription = "الرئيسية", tint = Color.White)
                    Text("الرئيسية", color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 23.sp,
                textAlign = TextAlign.Center, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text("القرآن الكريم", color = gold, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MushafNumberEmblem(number: Int) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.quran_surah_medallion),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Text(number.toString(), color = Color(0xFFFFEFBE), fontSize = 18.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuranIndexScreen(
    repo: QuranRepository,
    prefs: QuranPrefsStore,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onContinue: () -> Unit,
    onOpenJuz: (Int) -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenPage: (Int) -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(0) }
    val listenContext = LocalContext.current
    var selecting by remember { mutableStateOf(false) }
    var selectedSurahs by remember { mutableStateOf<List<Int>>(emptyList()) }
    var p55Picker by remember { mutableStateOf(false) }
    var p58Hifz by remember { mutableStateOf(false) }
    var p61Tasmee by remember { mutableStateOf(false) }
    var p61TasmeeArgs by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var p62Similar by remember { mutableStateOf(false) }
    var p62Meanings by remember { mutableStateOf(false) }
    var p64Panel by remember { mutableStateOf(false) }
    if (p55Picker) P55SurahPicker(
        surahs = repo.surahs().map { s ->
            P55SurahInfo(s.index, s.name, s.ayahs, repo.surahPageRange(s.index).let { it.last - it.first + 1 })
        },
        onDismiss = { p55Picker = false },
        onPlay = { list -> P52SurahAudio.play(listenContext, list); p55Picker = false },
        onHome = { p55Picker = false; onHome() },
        onPlayFollow = { list ->
            val codes = list.flatMap { s ->
                val n = repo.surahs().firstOrNull { it.index == s }?.ayahs ?: 0
                (1..n).map { a -> P52SurahAudio.ayahCode(s, a) }
            }
            if (codes.isNotEmpty()) {
                P52SurahAudio.play(listenContext, codes)
                P52SurahAudio.followReader = true
                p55Picker = false
                list.firstOrNull()?.let(onOpenSurah)
            }
        },
    )
    if (p58Hifz) androidx.compose.ui.window.Dialog(
        onDismissRequest = { p58Hifz = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = P61Paper) {
            P62HifzScreen(
                repo = repo,
                onClose = { p58Hifz = false },
                onTasmee = { s, f, t -> p61TasmeeArgs = Triple(s, f, t); p58Hifz = false; p61Tasmee = true },
            )
        }
    }
    if (p61Tasmee) androidx.compose.ui.window.Dialog(
        onDismissRequest = { p61Tasmee = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = P61Paper) {
            P61TasmeeScreen(
                repo = repo,
                initialSurah = p61TasmeeArgs?.first ?: 1,
                initialFrom = p61TasmeeArgs?.second ?: 1,
                initialTo = p61TasmeeArgs?.third,
                onClose = { p61Tasmee = false },
            )
        }
    }
    if (p62Similar) androidx.compose.ui.window.Dialog(
        onDismissRequest = { p62Similar = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = P61Paper) { P62SimilarScreen(repo = repo, onClose = { p62Similar = false }) }
    }
    if (p62Meanings) androidx.compose.ui.window.Dialog(
        onDismissRequest = { p62Meanings = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = P61Paper) {
            P67MunfaradatScreen(repo = repo, onClose = { p62Meanings = false }, onOpenPage = { page -> p62Meanings = false; onOpenPage(page) })
        }
    }
    val lastPage = prefs.lastPage()
    val lastJuz = repo.pageJuz(lastPage)
    val lastSurah = repo.pageSurah(lastPage)
    if (p64Panel) P64PlayPanel(startSurah = lastSurah, startName = repo.surahName(lastSurah), onDismiss = { p64Panel = false })
    val green = Color(0xFF064D3B)
    val gold = Color(0xFFB48B38)
    val cream = Color(0xFFFCF8EF)
    val line = Color(0xFFE9DDC6)

    Scaffold(containerColor = cream) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            P64IndexTop(onSearch)
            P64ListenHeader(onPickSurahs = { p55Picker = true }, onPlay = { p64Panel = true }, onHifz = { p58Hifz = true },
                onTasmee = { p61TasmeeArgs = null; p61Tasmee = true },
                onSimilar = { p62Similar = true }, onMeanings = { p62Meanings = true })
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 13.dp)) {
                Surface(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 10.dp),
                    color = Color(0xFFEAF2EB),
                    shape = RoundedCornerShape(19.dp),
                    border = BorderStroke(1.dp, Color(0xFFBAD5C5)),
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(color = green, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = Color.White,
                                modifier = Modifier.padding(10.dp).size(24.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("تابع من حيث توقفت", color = green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("صفحة $lastPage  •  الجزء $lastJuz  •  ${repo.surahName(lastSurah)}",
                                color = green.copy(alpha = 0.8f), fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = gold)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.dp, line),
                    shadowElevation = 1.dp,
                ) {
                    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("السور", "الأجزاء").forEachIndexed { i, name ->
                            Surface(
                                onClick = { tab = i },
                                modifier = Modifier.weight(1f),
                                color = if (tab == i) green else Color.White,
                                shape = RoundedCornerShape(13.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(if (i == 0) Icons.Rounded.MenuBook else Icons.Rounded.BookmarkBorder,
                                        contentDescription = null,
                                        tint = if (tab == i) Color.White else green,
                                        modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(name, color = if (tab == i) Color.White else green,
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 0.dp, top = 10.dp, end = 0.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (tab == 0) {
                        items(repo.surahs(), key = { "surah_${it.index}" }) { surah ->
                            P52SurahTile(
                                number = surah.index,
                                title = "سورة ${surah.name}",
                                subtitle = "${surah.ayahs} آية  •  تبدأ من صفحة ${repo.firstPageOfSurah(surah.index)}",
                                selecting = selecting,
                                selected = surah.index in selectedSurahs,
                                emblem = { MushafNumberEmblem(surah.index) },
                                onOpen = { onOpenSurah(surah.index) },
                                onListen = {
                                    if (P52SurahAudio.currentSurah == surah.index) P52SurahAudio.togglePause()
                                    else P52SurahAudio.play(listenContext, (surah.index..114).toList(), resume = true)
                                },
                                onToggleSelect = {
                                    selectedSurahs = if (surah.index in selectedSurahs)
                                        selectedSurahs - surah.index else selectedSurahs + surah.index
                                },
                            )
                        }
                    } else {
                        items((1..30).toList(), key = { "juz_$it" }) { juz ->
                            val range = repo.juzPageRange(juz)
                            QuranIndexTile(
                                number = juz,
                                title = "الجزء $juz",
                                subtitle = "من صفحة ${range.first} إلى ${range.last}",
                                green = green, gold = gold, border = line,
                                onClick = { onOpenJuz(juz) },
                            )
                        }
                    }
                }
                P52MiniPlayer()
            }
            P53BottomNav(onBack = onBack, onHome = onHome)
        }
    }
}

@Composable
private fun QuranIndexTile(
    number: Int,
    title: String,
    subtitle: String,
    green: Color,
    gold: Color,
    border: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, border),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MushafNumberEmblem(number)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = green, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFF746D62), fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(50), color = Color(0xFFFBF3E1)) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = gold,
                    modifier = Modifier.padding(6.dp).size(20.dp))
            }
        }
    }
}

// P21: a compact, continuous-color search header. Its status-bar-safe layout
// deliberately avoids the half-cropped illustration in the previous screen.
@Composable
private fun QuranSearchTopBar(onBack: () -> Unit, onHome: () -> Unit) {
    val gold = Color(0xFFE3C78D)
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF08624B), Color(0xFF064332))))
            .statusBarsPadding(),
    ) {
        Icon(
            Icons.Rounded.MenuBook,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.09f),
            modifier = Modifier.align(Alignment.CenterStart).size(110.dp),
        )
        Column(
            Modifier.fillMaxWidth().height(112.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Spacer(Modifier.height(26.dp))
            Text("البحث في القرآن", modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, color = Color.White,
                fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("ابحث في السور والآيات دون إنترنت", modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, color = gold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun QuranSearchExample(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color(0xFFF8F3E7),
        border = BorderStroke(1.dp, Color(0xFFE4D2AB)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            color = Color(0xFF07503F), textAlign = TextAlign.Center,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranSearchScreen(
    repo: QuranRepository,
    searchEngine: Lazy<QuranSearchEngine>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenAyah: (QuranAyah) -> Unit,
) {
    val green = Color(0xFF064D3B)
    val gold = Color(0xFFB48B38)
    val cream = Color(0xFFFCF8EF)
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // الكل / الآيات / السور
    var results by remember { mutableStateOf<List<QuranSearchHit>>(emptyList()) }
    var shown by remember { mutableIntStateOf(35) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query, filter) {
        shown = 35
        if (query.isBlank()) {
            results = emptyList()
            loading = false
        } else {
            loading = true
            delay(170) // Cancel stale searches while the user is typing.
            results = withContext(Dispatchers.Default) { searchEngine.value.search(query, filter) }
            loading = false
        }
    }

    Scaffold(
        containerColor = cream,
        topBar = { QuranSearchTopBar(onBack, onHome) },
        bottomBar = { P53BottomNav(onBack = onBack, onHome = onHome) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text("اسم السورة أو كلمة أو جزء من آية…", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "بحث", tint = green) },
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) {
                        Text("مسح", color = green, fontSize = 13.sp)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = green,
                    unfocusedBorderColor = Color(0xFFCEC2AC),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                ),
            )
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("الكل", "الآيات", "السور").forEachIndexed { i, label ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        onClick = { filter = i },
                        color = if (filter == i) green else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (filter == i) green else Color(0xFFE4D9C7)),
                    ) {
                        Text(label, modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            color = if (filter == i) Color.White else green,
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            when {
                query.isBlank() -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE8DDC6)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFEAF3ED)) {
                                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = green,
                                        modifier = Modifier.padding(10.dp).size(24.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("ابحث في القرآن الكريم", color = green,
                                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                            Spacer(Modifier.height(9.dp))
                            Text("ابحث عن آية تتذكر جزءًا منها، أو كلمة وردت في القرآن الكريم، أو اسم سورة.",
                                fontSize = 14.sp, lineHeight = 24.sp, color = Color(0xFF685F51))
                            Spacer(Modifier.height(13.dp))
                            Text("جرّب البحث عن", color = gold,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { QuranSearchExample("الرحمن") { query = "الرحمن" } }
                                Box(Modifier.weight(1f)) { QuranSearchExample("الصبر") { query = "الصبر" } }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { QuranSearchExample("إن الله") { query = "إن الله" } }
                                Box(Modifier.weight(1f)) { QuranSearchExample("الفاتحة") { query = "الفاتحة" } }
                            }
                        }
                    }
                    Text("البحث يعمل دون إنترنت في 114 سورة و6236 آية.",
                        modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
                        textAlign = TextAlign.Center, fontSize = 12.sp, color = Color(0xFF6D716B))
                }
                loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = gold)
                    Text("جارٍ البحث في القرآن...", color = green,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        textAlign = TextAlign.Center)
                }
                else -> {
                    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("نتائج البحث: ${results.size}", color = green,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("اضغط لفتح النتيجة", color = Color(0xFF8E784C), fontSize = 12.sp)
                    }
                    if (results.isEmpty()) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE9DDC6)),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Search, contentDescription = null,
                                    tint = gold, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("لا توجد نتائج مطابقة", color = green, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(5.dp))
                                Text("جرّب كتابة جزء من الكلمة أو البحث دون تشكيل.",
                                    color = Color(0xFF706D65), fontSize = 13.sp,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(results.take(shown), key = { hit -> hit.key }) { hit ->
                                Surface(
                                    onClick = {
                                        if (hit.type == QuranSearchType.SURAH) onOpenSurah(hit.surah)
                                        else hit.ayah?.let(onOpenAyah)
                                    },
                                    color = Color.White,
                                    shape = RoundedCornerShape(17.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE8DCC8)),
                                    shadowElevation = 1.dp,
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(50),
                                                color = Color(0xFFF8F0DA)) {
                                                Icon(Icons.Rounded.MenuBook, contentDescription = null,
                                                    tint = gold, modifier = Modifier.padding(7.dp).size(17.dp))
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text("سورة ${repo.surahName(hit.surah)}" +
                                                (hit.ayah?.let { "  •  الآية ${it.ayah}" } ?: ""),
                                                modifier = Modifier.weight(1f),
                                                color = green, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Icon(Icons.Rounded.ChevronLeft, contentDescription = null,
                                                tint = gold, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.height(7.dp))
                                        if (hit.ayah == null) {
                                            Text("اضغط لفتح السورة", color = Color(0xFF786F60), fontSize = 13.sp)
                                        } else {
                                            Text(
                                                searchEngine.value.highlightText(hit.ayah.text, query),
                                                color = Color(0xFF302C23), fontSize = 18.sp,
                                                lineHeight = 29.sp, maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(5.dp))
                                            Text("صفحة ${hit.ayah.page}  •  الجزء ${hit.ayah.juz}",
                                                color = Color(0xFF786F60), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            if (shown < results.size) {
                                item(key = "load_more") {
                                    OutlinedButton(onClick = { shown += 35 },
                                        modifier = Modifier.fillMaxWidth()) {
                                        Text("إظهار المزيد (${results.size - shown} نتيجة)", color = green)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// P23: The supplied Quran source embeds the introductory basmala in ayah 1
// for all surahs except At-Tawbah (9). The Fatihah basmala is itself ayah 1.
// Split only for *display*, keeping the original QuranAyah untouched.
private fun splitIntroBasmala(aya: QuranAyah): Pair<String?, String> {
    if (aya.ayah != 1 || aya.surah == 9) return null to aya.text
    val lastWord = "ٱلرَّحِيمِ"
    val at = aya.text.indexOf(lastWord)
    if (at < 0 || at > 46 || !aya.text.startsWith("ب")) return null to aya.text
    val end = at + lastWord.length
    return aya.text.substring(0, end) to aya.text.substring(end).trimStart()
}

// Conservative, lexical highlighting: this is presentation only, not exegesis.
// The red span never changes letter order, Quran source, verse identifiers or search.
private val p23WordRegex = Regex("\\S+")
private val p23DivineLabels = setOf(
    // Unambiguous lexical forms only. "رب" / "إله" require contextual interpretation
    // and are not colored automatically on an immutable Quran page image.
    "الله", "لله", "اللهم", "الرحمن", "الرحيم", "للرحمن", "للرحيم"
)
private fun p23NormalizeWord(word: String): String = buildString(word.length) {
    for (c in word) {
        if (Character.getType(c) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(c) == Character.ENCLOSING_MARK.toInt() ||
            c == 'ـ'
        ) continue
        if (!c.isLetter()) continue
        append(when(c) {
            'ٱ', 'أ', 'إ', 'آ' -> 'ا'
            'ى' -> 'ي'
            else -> c
        })
    }
}
private fun p23IsDivineWord(word: String): Boolean {
    val normalized = p23NormalizeWord(word)
    if (normalized in p23DivineLabels) return true
    // Conjunctions and prefixes are part of the original Arabic word.
    val withConjunction = if (normalized.startsWith('و') || normalized.startsWith('ف')) normalized.drop(1) else normalized
    return withConjunction in p23DivineLabels ||
        (withConjunction.startsWith('ب') && withConjunction.drop(1) in p23DivineLabels) ||
        (withConjunction.startsWith('ل') && withConjunction.drop(1) in p23DivineLabels)
}
private fun p23ColoredQuranText(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    addStyle(SpanStyle(color = baseColor), 0, length)
    Regex("﴿[0-9٠-٩]+﴾").findAll(text).forEach { marker ->
        addStyle(SpanStyle(color = Color(0xFFA18C62), fontWeight = FontWeight.SemiBold),
            marker.range.first, marker.range.last + 1)
    }
    p23WordRegex.findAll(text).forEach { token ->
        if (p23IsDivineWord(token.value)) {
            addStyle(
                SpanStyle(color = Color(0xFFA64D57), fontWeight = FontWeight.SemiBold),
                token.range.first, token.range.last + 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranReaderScreen(
    repo: QuranRepository,
    prefs: QuranPrefsStore,
    target: ReadingTarget,
    mode: QuranMode,
    onModeChange: (QuranMode) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    bookmark: Pair<Int, Int>?,
    playingKey: String?,
    highlightedAyahKey: String?,
    audioMessage: String?,
    assignmentCompleted: Boolean,
    onCompleteAssignment: (() -> Unit)?,
    onSelectAyah: (QuranAyah) -> Unit,
    onPlayPrintedAyah: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onListen: (Int) -> Unit,
    isAudioPlaying: Boolean,
    onStopAudio: () -> Unit,
    onOpenSavedPage: (Int) -> Unit,
    onOpenIndex: () -> Unit,
    onSearch: () -> Unit,
    onNavigateSurah: (Int, Boolean) -> Unit,
) {
    // P24: open each surah in the original page-by-page reader by default.
    // Flexible continuous reading stays available only by manual toggle.
    var surahReadingMode by remember(target.key) { mutableStateOf(QuranMode.MADINA_PAGES) }
    val displayMode = if (target.surah != null) surahReadingMode else mode
    // P41: tap the printed page to hide/reveal toolbars, leaving more room to read.
    var p41ControlsVisible by remember(target.key) { mutableStateOf(!P55Reader.full) }
    var p65Player by remember { mutableStateOf(false) }
    // VC101: the selected verse stays docked in reading mode instead of opening
    // the old blocking modal sheet. Minimized controls never stop playback.
    var p101DockAyah by remember(target.key) { mutableStateOf<QuranAyah?>(null) }
    var printedZoom by remember(target.key) { mutableFloatStateOf(1f) }
    var coloredMushaf by remember(target.key) { mutableStateOf(prefs.coloredMushaf() || !P55PlainPages.ready) }
    var savedPages by remember(target.key) { mutableStateOf(prefs.savedPages()) }
    var p44InkTool by remember(target.key) { mutableStateOf(P44InkTool.MOVE) }
    var p60Tools by remember(target.key) { mutableStateOf(false) }
    var showMarks by remember { mutableStateOf(false) }
    var p62Settings by remember { mutableStateOf(false) }
    var p63MsgShown by remember { mutableStateOf(true) }
    LaunchedEffect(audioMessage) {
        p63MsgShown = true
        if (audioMessage != null) { kotlinx.coroutines.delay(5000); p63MsgShown = false }
    }
    val p55Ctx = LocalContext.current
    BackHandler(enabled = displayMode == QuranMode.MADINA_PAGES && (P68Reading.on || !p41ControlsVisible)) {
        // In clean reading mode the native Android Back action is the exit: no on-image controls.
        P68Reading.on = false
        P68Reading.hint = false
        p41ControlsVisible = true
        p65Player = false
        P55Reader.set(p55Ctx, false)
    }
    val pages = remember(target) { target.pages.toList().ifEmpty { listOf(1) } }
    val savedPage = remember(target) {
        when {
            target.startPage != null -> target.startPage.takeIf { it in target.pages }
            target.assigned && target.juz != null -> prefs.lastPageForJuz(target.juz).takeIf { it in target.pages }
            target.key.startsWith("continue_") -> prefs.lastPage().takeIf { it in target.pages }
            else -> null
        } ?: target.pages.first
    }
    val initialIndex = pages.indexOf(savedPage).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val currentPage = pages.getOrElse(pagerState.currentPage) { pages.first() }
    LaunchedEffect(currentPage) { p44InkTool = P44InkTool.MOVE }
    // P58: the page follows the verse being recited.
    LaunchedEffect(playingKey) {
        val key = playingKey
        if (key != null && displayMode == QuranMode.MADINA_PAGES) {
            val parts = key.split(":")
            val ay = if (parts.size == 2) repo.ayah(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0) else null
            val idx = if (ay != null) pages.indexOf(ay.page) else -1
            if (idx >= 0 && idx != pagerState.currentPage) pagerState.animateScrollToPage(idx)
        }
    }
    LaunchedEffect(currentPage) { runCatching { com.ab.khatma.secure.P58Stats.recordPage(p55Ctx, currentPage) } }
    var p58Khatm by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        if (currentPage == 604 && pages.size > 1 && pagerState.currentPage == pages.lastIndex && displayMode == QuranMode.MADINA_PAGES) p58Khatm = true
    }
    if (p58Khatm) P58KhatmDialog { p58Khatm = false }
    // P57: full screen opens at 1.25x so the text fills the width; leaving it returns to normal size.
    LaunchedEffect(currentPage, p41ControlsVisible) {
        printedZoom = 1f // P67: full screen keeps the normal size so no text is cropped
    }
    if (showMarks) {
        AlertDialog(
            onDismissRequest = { showMarks = false },
            title = { Text("الصفحات المحفوظة") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (savedPages.isEmpty()) Text("لم تحفظ صفحات بعد")
                    savedPages.sorted().forEach { page ->
                        TextButton(onClick = { showMarks = false; onOpenSavedPage(page) }) {
                            Text("الانتقال إلى صفحة $page")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { prefs.toggleSavedPage(currentPage); savedPages = prefs.savedPages() }) {
                    Text(if (currentPage in savedPages) "إزالة علامة صفحة $currentPage" else "حفظ علامة صفحة $currentPage")
                }
            },
            confirmButton = { TextButton(onClick = { showMarks = false }) { Text("إغلاق") } },
        )
    }
    val currentJuz = repo.pageJuz(currentPage)
    // P22: when the user opens a surah, the page can still have neighboring
    // surahs in Madina pagination; identify the selected surah instead.
    val currentSurah = target.surah ?: repo.pageSurah(currentPage)
    val progress = (pagerState.currentPage + 1).toFloat() / pages.size.coerceAtLeast(1)
    val atEnd = pagerState.currentPage == pages.lastIndex

    val paper = Color(0xFFFFFBF0)
    val paperSoft = Color(0xFFF4ECD9)
    val ink = Color(0xFF262018)
    val accent = Color(0xFF9A772C)
    val frame = Color(0xFFCDBB8B)

    LaunchedEffect(currentPage) {
        prefs.saveLastPage(currentPage)
        prefs.saveLastJuz(currentJuz)
        if (target.assigned && target.juz != null) prefs.saveLastPageForJuz(target.juz, currentPage)
    }

    P55ImmersiveEffect(displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible)
    var p68LastPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (p68LastPage != pagerState.currentPage) {
            p68LastPage = pagerState.currentPage
            if (P68Reading.on && !p41ControlsVisible && P68Reading.soundOn) P68FlipSound.play()
        }
    }
    LaunchedEffect(p41ControlsVisible) { if (p41ControlsVisible) { P68Reading.on = false; P68Reading.hint = false } }
    LaunchedEffect(Unit) { P68Reading.load(p55Ctx) }
    DisposableEffect(Unit) { onDispose { P68Reading.on = false; P68Reading.hint = false } }
    LaunchedEffect(P68Reading.hint) { if (P68Reading.hint) { kotlinx.coroutines.delay(3500); P68Reading.hint = false } }
    val p57Full = displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible
    val p57Bg = if (p57Full) (if (P53NightMode.enabled) P53NightMode.pageBackground else P67Look.pageBackground) else paperSoft
    Scaffold(
        containerColor = p57Bg,
        floatingActionButton = {
            if (displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible)
                Unit
        },
        topBar = {
            // Hidden only in full-screen; one tap on the corner button brings everything back.
            if (displayMode != QuranMode.MADINA_PAGES) TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF064D3B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (target.assigned) "الجزء ${target.juz}" else target.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            "سورة ${repo.surahName(currentSurah)}  •  صفحة $currentPage  •  الجزء $currentJuz",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                },
                navigationIcon = {},
                actions = {
                    if (displayMode == QuranMode.MADINA_PAGES) IconButton(onClick = {
                        P55Reader.set(p55Ctx, true)
                        p41ControlsVisible = false
                    }) { Icon(Icons.Rounded.Fullscreen, "ملء الشاشة", tint = Color.White) }
                    MushafSearchButton(onClick = onSearch)
                    TextButton(
                        onClick = onOpenIndex,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) { Text("الفهرس") }
                },
            )
        },
        bottomBar = {
            Column {
            if (displayMode != QuranMode.MADINA_PAGES) Surface(
                color = paper,
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, frame.copy(alpha = 0.55f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    if (target.assigned) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = accent,
                            trackColor = paperSoft,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${(progress * 100).toInt()}% من الجزء",
                                style = MaterialTheme.typography.labelSmall,
                                color = ink.copy(alpha = 0.65f),
                            )
                            when {
                                assignmentCompleted -> Text("تمت القراءة ✓", color = accent, fontWeight = FontWeight.Bold)
                                atEnd && onCompleteAssignment != null -> Button(
                                    onClick = onCompleteAssignment,
                                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                                ) { Text("أتممت الجزء ✓") }
                            }
                        }
                    }

                    if (displayMode == QuranMode.MADINA_PAGES) P60ZoomBar(
                        zoom = printedZoom,
                        onMinus = { printedZoom = (printedZoom - 0.25f).coerceIn(1f, 3.5f) },
                        onPlus = { printedZoom = (printedZoom + 0.25f).coerceIn(1f, 3.5f) },
                        onReset = { printedZoom = 1f },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                if (displayMode == QuranMode.MADINA_PAGES) {
                                    when {
                                        pagerState.currentPage > 0 -> scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                        target.surah != null && target.surah > 1 ->
                                            onNavigateSurah(target.surah - 1, true)
                                    }
                                }
                            },
                            enabled = displayMode == QuranMode.MADINA_PAGES &&
                                (pagerState.currentPage > 0 || target.surah != null && target.surah > 1),
                            colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        ) { Text("‹ السابق") }

                        Text(
                            if (displayMode == QuranMode.MADINA_PAGES) "— $currentPage —" else if (target.surah != null) "السورة كاملة" else "النص المرن",
                            color = ink,
                            fontWeight = FontWeight.SemiBold,
                        )

                        TextButton(
                            onClick = {
                                if (displayMode == QuranMode.MADINA_PAGES) {
                                    when {
                                        pagerState.currentPage < pages.lastIndex -> scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                        target.surah != null && target.surah < 114 ->
                                            onNavigateSurah(target.surah + 1, false)
                                    }
                                }
                            },
                            enabled = displayMode == QuranMode.MADINA_PAGES &&
                                (pagerState.currentPage < pages.lastIndex || target.surah != null && target.surah < 114),
                            colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        ) { Text("التالي ›") }
                    }
                }
            }
            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible)
                P53BottomNav(onBack = onBack, onHome = onHome, compact = true)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(p57Bg)
                .padding(horizontal = if (displayMode == QuranMode.MADINA_PAGES) 0.dp else 8.dp),
        ) {
            if (displayMode != QuranMode.MADINA_PAGES) Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 4.dp),
                color = paper,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, frame.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            val next = if (displayMode == QuranMode.MADINA_PAGES) QuranMode.FLEX_TEXT else QuranMode.MADINA_PAGES
                            if (target.surah != null) surahReadingMode = next else onModeChange(next)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                    ) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (displayMode == QuranMode.MADINA_PAGES) "نص مرن" else "المصحف")
                    }
                    TextButton(
                        onClick = { P61Flow.set(p55Ctx, true) },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) { Text("📜 قراءة متصلة", fontSize = 12.sp) }
                    if (displayMode == QuranMode.MADINA_PAGES) TextButton(
                        onClick = {
                            if (coloredMushaf && !P55PlainPages.ready) {
                                P55PlainPages.askVisible = true
                            } else {
                                coloredMushaf = !coloredMushaf
                                prefs.saveColoredMushaf(coloredMushaf)
                                printedZoom = 1f
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) { Text(if (coloredMushaf) "ملون" else "أصلي", fontSize = 12.sp) }
                    P55PlainGate(onReady = {
                        coloredMushaf = false
                        prefs.saveColoredMushaf(false)
                        printedZoom = 1f
                    })
                    Spacer(Modifier.weight(1f))
                    if (displayMode != QuranMode.MADINA_PAGES) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (displayMode == QuranMode.MADINA_PAGES) "تكبير الصفحة" else "حجم الخط", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.65f))
                    TextButton(
                        onClick = {
                            if (displayMode == QuranMode.MADINA_PAGES) printedZoom = (printedZoom - 0.25f).coerceIn(1f, 3.5f)
                            else onFontSizeChange(fontSize - 2f)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("−", fontSize = 20.sp) }
                    TextButton(
                        onClick = { if (displayMode == QuranMode.MADINA_PAGES) printedZoom = 1f },
                        enabled = displayMode == QuranMode.MADINA_PAGES && printedZoom > 1.01f,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                    ) {
                        Text(if (displayMode == QuranMode.MADINA_PAGES) "${(printedZoom * 100).toInt()}%" else fontSize.toInt().toString(),
                            style = MaterialTheme.typography.labelMedium, color = ink)
                    }
                    TextButton(
                        onClick = {
                            if (displayMode == QuranMode.MADINA_PAGES) printedZoom = (printedZoom + 0.25f).coerceIn(1f, 3.5f)
                            else onFontSizeChange(fontSize + 2f)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("+", fontSize = 20.sp) }
                    }
                }
            }

            if (displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible) {
                P63TopBar(
                    night = P53NightMode.enabled,
                    info = P65Info.line(p55Ctx, repo, repo.surahName(currentSurah), currentJuz, currentPage),
                    saved = currentPage in savedPages,
                    toolsOn = p60Tools || p44InkTool != P44InkTool.MOVE,
                    onIndex = onOpenIndex,
                    onSearch = onSearch,
                    onMarks = { showMarks = true },
                    onTools = { p60Tools = !p60Tools; if (!p60Tools) p44InkTool = P44InkTool.MOVE },
                    onFull = { P55Reader.set(p55Ctx, true); p41ControlsVisible = false },
                    onRead = { printedZoom = 1f; p44InkTool = P44InkTool.MOVE; p60Tools = false; P68Reading.on = true; P68Reading.hint = false; P55Reader.set(p55Ctx, true); p41ControlsVisible = false },
                    onSettings = { p62Settings = true },
                )
            }
            if (displayMode == QuranMode.MADINA_PAGES) {
                P55PlainGate(onReady = {
                    coloredMushaf = false
                    prefs.saveColoredMushaf(false)
                    printedZoom = 1f
                })
            }
            if (p62Settings) P62SettingsDialog(
                night = P53NightMode.enabled,
                onNight = { P53NightMode.toggle(p55Ctx) },
                colored = coloredMushaf,
                onColored = {
                    p62Settings = false
                    if (coloredMushaf && !P55PlainPages.ready) {
                        P55PlainPages.askVisible = true
                    } else {
                        coloredMushaf = !coloredMushaf
                        prefs.saveColoredMushaf(coloredMushaf)
                        printedZoom = 1f
                    }
                },
                zoom = printedZoom,
                onMinus = { printedZoom = (printedZoom - 0.25f).coerceIn(1f, 3.5f) },
                onPlus = { printedZoom = (printedZoom + 0.25f).coerceIn(1f, 3.5f) },
                onReset = { printedZoom = 1f },
                onFlow = { p62Settings = false; P61Flow.set(p55Ctx, true) },
                onFlex = {
                    p62Settings = false
                    if (target.surah != null) surahReadingMode = QuranMode.FLEX_TEXT else onModeChange(QuranMode.FLEX_TEXT)
                },
                onListen = { p62Settings = false; onListen(currentSurah) },
                onHome = { p62Settings = false; onHome() },
                onDismiss = { p62Settings = false },
            )

            if(displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible && (p60Tools || p44InkTool != P44InkTool.MOVE)) {
                Surface(
                    modifier=Modifier.fillMaxWidth().padding(bottom=4.dp),
                    color=paper, shape=RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier=Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceEvenly,
                        verticalAlignment=Alignment.CenterVertically,
                    ) {
                        listOf(
                            P44InkTool.MOVE to "حركة",
                            P44InkTool.PEN to "✎ قلم",
                            P44InkTool.HIGHLIGHT to "تظليل",
                            P44InkTool.ERASER to "ممحاة",
                            P44InkTool.SELECT to "تحديد",
                        ).forEach { (tool,label) ->
                            TextButton(
                                onClick={p44InkTool=tool},
                                contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),
                                colors=ButtonDefaults.textButtonColors(contentColor=if(p44InkTool==tool) Color(0xFF064D3B) else accent),
                            ) { Text(label,fontSize=11.sp,fontWeight=if(p44InkTool==tool) FontWeight.Bold else FontWeight.Normal) }
                        }
                    }
                }
            }
            audioMessage?.takeIf { p63MsgShown }?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            if (displayMode == QuranMode.MADINA_PAGES) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    // P43: while magnified the single-finger drag pans the page,
                    // instead of accidentally turning to the next page.
                    userScrollEnabled = (P68Reading.on || printedZoom <= 1.01f) && p44InkTool == P44InkTool.MOVE,
                ) { pageIndex ->
                    val pageNumber = pages[pageIndex]
                    // Preserve P22 isolated-surah behavior on shared pages.
                    // A real printed page contains both surahs; retain the former
                    // filtered text view there rather than leaking neighboring verses.
                    val hasNeighborSurah = false
                    if (!hasNeighborSurah) {
                        P41OfflineMushafPage(
                            pageNumber = pageNumber,
                            colored = coloredMushaf,
                            zoom = printedZoom,
                            onZoomChange = { printedZoom = it },
                            onToggleControls = {
                                if (P68Reading.on && !p41ControlsVisible) {
                                    P68Reading.hint = !P68Reading.hint
                                } else {
                                    val visible = !p41ControlsVisible
                                    p41ControlsVisible = visible
                                    P55Reader.set(p55Ctx, !visible)
                                }
                            },
                            inkTool = p44InkTool,
                            onPlayVerse = { surah, ayah ->
                                if (P68Reading.on) {
                                    repo.ayah(surah, ayah)?.let { p101DockAyah = it }
                                } else onPlayPrintedAyah(surah, ayah)
                            },
                            playingKey = playingKey,
                            readingMode = P68Reading.on,
                        )
                    } else MushafPage(
                        pageNumber = pageNumber,
                        isolatedSurah = target.surah,
                        // P22: hide the preceding/following surah on shared
                        // physical pages. Juz and continue retain full pages.
                        ayahs = repo.loadPage(pageNumber).let { pageAyahs ->
                            target.surah?.let { selectedSurah ->
                                pageAyahs.filter { it.surah == selectedSurah }
                            } ?: pageAyahs
                        },
                        repo = repo,
                        fontSize = fontSize,
                        playingKey = playingKey,
                        highlightedAyahKey = highlightedAyahKey,
                        scrollToAyahKey = target.focusAyah?.let { "${it.first}:${it.second}" },
                        bookmark = bookmark,
                        onSelectAyah = onSelectAyah,
                    )
                }
                P65FullControls(
                    visible = p57Full && !P68Reading.on && p44InkTool == P44InkTool.MOVE,
                    hasPrev = pagerState.currentPage > 0 || (target.surah != null && target.surah > 1),
                    hasNext = pagerState.currentPage < pages.lastIndex || (target.surah != null && target.surah < 114),
                    zoomed = printedZoom > 1.01f,
                    onPrev = {
                        when {
                            pagerState.currentPage > 0 -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            target.surah != null && target.surah > 1 -> onNavigateSurah(target.surah - 1, true)
                            else -> Unit
                        }
                    },
                    onNext = {
                        when {
                            pagerState.currentPage < pages.lastIndex -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            target.surah != null && target.surah < 114 -> onNavigateSurah(target.surah + 1, false)
                            else -> Unit
                        }
                    },
                    onToggleZoom = { printedZoom = if (printedZoom > 1.01f) 1f else 1.25f },
                    playerOpen = p65Player,
                    onTogglePlayer = { p65Player = !p65Player },
                    bottomPad = if (p65Player) (if (P52SurahAudio.isActive) 270 else 210) else 18,
                )
                if (p57Full && !P68Reading.on && p65Player) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    P65FullPlayer(onPlayHere = { p62PlayFromPage(p55Ctx, repo, pages, currentPage, target.surah) })
                }
                // VC101: compact overlay anchored to the bottom of the enlarged Mushaf.
                // It is not modal: outside its bounds the page remains swipeable/tappable.
                if (P68Reading.on) p101DockAyah?.let { aya ->
                    P101ReadingAudioDock(
                        aya = aya,
                        surahName = repo.surahName(aya.surah),
                        reciterName = P52SurahAudio.savedReciter.name,
                        selectedPlaying = P52SurahAudio.followKey == "${aya.surah}:${aya.ayah}",
                        isPlaying = P52SurahAudio.isPlaying,
                        isLoading = P52SurahAudio.isLoading,
                        message = P52SurahAudio.message,
                        onPlayPause = {
                            val key = "${aya.surah}:${aya.ayah}"
                            if (P52SurahAudio.followKey == key && P52SurahAudio.isActive) {
                                P52SurahAudio.togglePause()
                            } else {
                                P52SurahAudio.init(p55Ctx)
                                P52SurahAudio.play(p55Ctx, listOf(P52SurahAudio.ayahCode(aya.surah, aya.ayah)))
                                P52SurahAudio.followReader = true
                            }
                        },
                        onRepeat = {
                            P52SurahAudio.init(p55Ctx)
                            P52SurahAudio.play(p55Ctx, listOf(P52SurahAudio.ayahCode(aya.surah, aya.ayah)))
                            P52SurahAudio.followReader = true
                        },
                        // VC102: the original full verse-actions sheet is still here.
                        // Open it explicitly without dismissing the compact reading dock.
                        onMore = { onPlayPrintedAyah(aya.surah, aya.ayah) },
                        onDismiss = { p101DockAyah = null },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                if (p57Full && P68Reading.on && P68Reading.hint) P68ExitReading(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                ) { P68Reading.on = false; P68Reading.hint = false; P55Reader.set(p55Ctx, false); p41ControlsVisible = true; p65Player = false }
                if (p57Full && !P68Reading.on) Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    P62ExitPill { P55Reader.set(p55Ctx, false); p41ControlsVisible = true; p65Player = false }
                    P65FullInfo(
                        info = P65Info.line(p55Ctx, repo, repo.surahName(currentSurah), currentJuz, currentPage),
                        night = P53NightMode.enabled,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
                }
                if (!P68Reading.on) P63Bottom(
                    repo = repo,
                    pages = pages,
                    index = pagerState.currentPage,
                    night = P53NightMode.enabled,
                    assigned = target.assigned,
                    progress = progress,
                    atEnd = atEnd,
                    completed = assignmentCompleted,
                    onComplete = onCompleteAssignment,
                    singleAyahPlaying = isAudioPlaying,
                    onStopSingle = onStopAudio,
                    onPlayHere = { p62PlayFromPage(p55Ctx, repo, pages, currentPage, target.surah) },
                    onSeek = { i -> scope.launch { pagerState.scrollToPage(i) } },
                    hasPrev = pagerState.currentPage > 0 || (target.surah != null && target.surah > 1),
                    hasNext = pagerState.currentPage < pages.lastIndex || (target.surah != null && target.surah < 114),
                    onPrev = {
                        when {
                            pagerState.currentPage > 0 -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            target.surah != null && target.surah > 1 -> onNavigateSurah(target.surah - 1, true)
                            else -> Unit
                        }
                    },
                    onNext = {
                        when {
                            pagerState.currentPage < pages.lastIndex -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            target.surah != null && target.surah < 114 -> onNavigateSurah(target.surah + 1, false)
                            else -> Unit
                        }
                    },
                    onRead = { printedZoom = 1f; p44InkTool = P44InkTool.MOVE; p60Tools = false; P68Reading.on = true; P68Reading.hint = false; P55Reader.set(p55Ctx, true); p41ControlsVisible = false },
                    compact = !p41ControlsVisible,
                )
            } else {
                val targetAyahs = remember(target) {
                    target.surah?.let(repo::loadSurah)
                        ?: pages.flatMap { repo.loadPage(it) }
                            .filter { target.juz == null || it.juz == target.juz }
                            .distinctBy { "${it.surah}:${it.ayah}" }
                }
                val firstAyah = target.surah?.let { n -> targetAyahs.firstOrNull { it.surah == n && it.ayah == 1 } }
                val introBasmala = remember(firstAyah) { firstAyah?.let(::splitIntroBasmala)?.first }
                // The Fatihah basmala is ayah 1, not a repeatable preface.
                val visibleAyahs = remember(target, targetAyahs) {
                    if (target.surah == 1 && firstAyah != null && splitIntroBasmala(firstAyah).second.isEmpty()) {
                        targetAyahs.filterNot { it.surah == 1 && it.ayah == 1 }
                    } else targetAyahs
                }
                val ayahListState = rememberLazyListState()
                LaunchedEffect(target.key, target.focusAyah, visibleAyahs) {
                    target.focusAyah?.let { (surah, ayah) ->
                        val index = visibleAyahs.indexOfFirst { it.surah == surah && it.ayah == ayah }
                        if (index >= 0) ayahListState.scrollToItem(index + if (target.surah != null) 1 else 0)
                        else if (target.surah == 1 && ayah == 1) ayahListState.scrollToItem(0)
                    }
                }
                LazyColumn(
                    state = ayahListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(if (target.surah != null) 2.dp else 7.dp),
                ) {
                    if (target.surah != null) {
                        item(key = "surah_heading_${target.surah}") {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                color = paper,
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(1.dp, frame),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 17.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("سُورَةُ ${repo.surahName(target.surah)}", color = accent,
                                        fontWeight = FontWeight.Bold, fontSize = 21.sp, textAlign = TextAlign.Center)
                                    if (introBasmala != null) {
                                        Spacer(Modifier.height(12.dp))
                                        HorizontalDivider(color = frame.copy(alpha = 0.55f))
                                        Spacer(Modifier.height(14.dp))
                                        val introLabel = if (target.surah == 1) "$introBasmala  ﴿1﴾" else introBasmala
                                        Text(
                                            text = remember(introLabel) { p23ColoredQuranText(introLabel, Color(0xFF947027)) },
                                            modifier = if (target.surah == 1 && firstAyah != null)
                                                Modifier.clickable { onSelectAyah(firstAyah) }
                                                else Modifier,
                                            fontSize = (fontSize * 0.92f).sp,
                                            lineHeight = (fontSize * 1.5f).sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(visibleAyahs, key = { "${it.surah}:${it.ayah}" }) { aya ->
                        val key = "${aya.surah}:${aya.ayah}"
                        val selected = highlightedAyahKey == key
                        val renderedText = remember(aya) {
                            if (target.surah == aya.surah && aya.ayah == 1)
                                splitIntroBasmala(aya).second else aya.text
                        }
                        val verseLine = remember(renderedText, aya.ayah) { "$renderedText  ﴿${aya.ayah}﴾" }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = when {
                                playingKey == key -> Color(0xFFFFE4B1)
                                selected -> Color(0xFFFFF2D1)
                                else -> paper
                            },
                            shape = RoundedCornerShape(if (target.surah != null) 0.dp else 12.dp),
                            border = if (target.surah == null) BorderStroke(1.dp, frame.copy(alpha = 0.5f)) else null,
                        ) {
                            Column(Modifier.fillMaxWidth().clickable { onSelectAyah(aya) }
                                .padding(horizontal = 14.dp, vertical = if (target.surah != null) 5.dp else 13.dp)) {
                                Text(
                                    text = remember(verseLine) { p23ColoredQuranText(verseLine, ink) },
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.66f).sp,
                                    textAlign = TextAlign.Justify,
                                )
                                if (target.surah == null) Text(
                                    "سورة ${repo.surahName(aya.surah)} • صفحة ${aya.page}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ink.copy(alpha = 0.58f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MushafPage(
    pageNumber: Int,
    isolatedSurah: Int?,
    ayahs: List<QuranAyah>,
    repo: QuranRepository,
    fontSize: Float,
    playingKey: String?,
    highlightedAyahKey: String?,
    scrollToAyahKey: String?,
    bookmark: Pair<Int, Int>?,
    onSelectAyah: (QuranAyah) -> Unit,
) {
    val paper = Color(0xFFFFFBF0)
    val ink = Color(0xFF211D17)
    val accent = Color(0xFF98752C)
    val frame = Color(0xFFCDBB8B)
    val selectedBg = Color(0xFFFFF0C2)
    val pageScroll = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    var focusedOnce by remember(pageNumber, scrollToAyahKey) { mutableStateOf(false) }
    val playingBg = Color(0xFFFFD978)

    // P24: decorate the first page of each independently opened surah.
    // Never insert into or alter the bundled Uthmani source.
    val firstAya = ayahs.firstOrNull { it.ayah == 1 && it.surah == isolatedSurah }
    val introBasmala = firstAya?.let(::splitIntroBasmala)?.first
    val hasIndependentBasmala = isolatedSurah != null && introBasmala != null
    val annotation = buildAnnotatedString {
        ayahs.forEachIndexed { index, aya ->
            val key = "${aya.surah}:${aya.ayah}"
            val parts = if (hasIndependentBasmala && aya.surah == isolatedSurah && aya.ayah == 1)
                splitIntroBasmala(aya) else null
            val verseText = parts?.second ?: aya.text
            // In Al-Fatihah the basmala IS ayah 1. Display it once above,
            // with its verse number, and skip the empty body below.
            if (!(isolatedSurah == 1 && aya.ayah == 1 && verseText.isBlank())) {
                val start = length
                append(verseText)
                append("  ﴿${aya.ayah}﴾")
                val end = length
                addStringAnnotation(tag = "AYA", annotation = key, start = start, end = end)
                when {
                    playingKey == key -> addStyle(
                        SpanStyle(background = playingBg, fontWeight = FontWeight.Bold), start, end,
                    )
                    highlightedAyahKey == key -> addStyle(
                        SpanStyle(background = selectedBg, fontWeight = FontWeight.SemiBold), start, end,
                    )
                    bookmark == (aya.surah to aya.ayah) -> addStyle(
                        SpanStyle(fontWeight = FontWeight.SemiBold), start, end,
                    )
                }
                // Red highlighting applies to selected divine references only.
                // It does not affect words, text ordering, verse numbers or data.
                p23WordRegex.findAll(verseText).forEach { token ->
                    if (p23IsDivineWord(token.value)) {
                        addStyle(SpanStyle(color = Color(0xFFA64D57), fontWeight = FontWeight.SemiBold),
                            start + token.range.first, start + token.range.last + 1)
                    }
                }
                if (index < ayahs.lastIndex) append("  ")
            }
        }
    }
    val firstSurah = ayahs.firstOrNull()?.surah ?: 1
    val lastSurah = ayahs.lastOrNull()?.surah ?: firstSurah
    val pageJuz = ayahs.firstOrNull()?.juz ?: repo.pageJuz(pageNumber)

    Surface(
        modifier = Modifier.fillMaxSize().padding(vertical = 5.dp, horizontal = 1.dp),
        color = paper,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, frame),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("الجزء $pageJuz", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.55f))
                Text(
                    if (firstSurah == lastSurah) "سورة ${repo.surahName(firstSurah)}"
                    else "${repo.surahName(firstSurah)} • ${repo.surahName(lastSurah)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text("صفحة $pageNumber", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.55f))
            }
            HorizontalDivider(Modifier.padding(top = 7.dp, bottom = 11.dp), color = frame.copy(alpha = 0.7f))

            if (hasIndependentBasmala && introBasmala != null) {
                val basmalaLine = if (isolatedSurah == 1) "$introBasmala  ﴿1﴾" else introBasmala
                Text(
                    text = remember(basmalaLine) {
                        p23ColoredQuranText(basmalaLine, Color(0xFF9A772C))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isolatedSurah == 1 && firstAya != null)
                            Modifier.clickable { onSelectAyah(firstAya) }
                            else Modifier),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Serif,
                    fontSize = (fontSize * 0.92f).sp,
                    lineHeight = (fontSize * 1.36f).sp,
                )
                Spacer(Modifier.height(8.dp))
            }

            ClickableText(
                text = annotation,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    // P40: use native Naskh-friendly serif shaping, not wide
                    // justification that stretches Arabic words on small screens.
                    fontFamily = FontFamily.Serif,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.34f).sp,
                    textAlign = TextAlign.Start,
                    color = ink,
                ),
                onTextLayout = { layout ->
                    if (!focusedOnce && scrollToAyahKey != null) {
                        val match = annotation.getStringAnnotations("AYA", 0, annotation.length)
                            .firstOrNull { it.item == scrollToAyahKey }
                        if (match != null) {
                            focusedOnce = true
                            val y = layout.getLineTop(layout.getLineForOffset(match.start))
                            scrollScope.launch {
                                // Wait for the page's height to be measured before scrolling.
                                kotlinx.coroutines.yield()
                                pageScroll.animateScrollTo((y + 46f).toInt().coerceAtMost(pageScroll.maxValue))
                            }
                        }
                    }
                },
                onClick = { offset ->
                    annotation.getStringAnnotations("AYA", offset, offset).firstOrNull()?.let { hit ->
                        val parts = hit.item.split(":")
                        val surah = parts.getOrNull(0)?.toIntOrNull()
                        val ayah = parts.getOrNull(1)?.toIntOrNull()
                        if (surah != null && ayah != null) repo.ayah(surah, ayah)?.let(onSelectAyah)
                    }
                },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = frame.copy(alpha = 0.55f))
            Text(
                "﴿  $pageNumber  ﴾",
                modifier = Modifier.padding(top = 6.dp),
                color = accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "المس أي آية لتحديدها وفتح التلاوة والمعاني والتفسير",
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.48f),
                textAlign = TextAlign.Center,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AyahActionsSheet(
    aya: QuranAyah,
    repo: QuranRepository,
    audioConfig: HusaryAudioConfig?,
    playingKey: String?,
    audioLoadingKey: String?,
    bookmark: Pair<Int, Int>?,
    explanationMode: ExplanationMode?,
    onExplanationMode: (ExplanationMode?) -> Unit,
    onPlay: () -> Unit,
    onBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    val key = "${aya.surah}:${aya.ayah}"
    val paper = Color(0xFFFFFBF0)
    val ink = Color(0xFF211D17)
    val accent = Color(0xFF98752C)
    val soft = Color(0xFFF5EACB)
    val p61Ctx = androidx.compose.ui.platform.LocalContext.current
    var p61ShowSimilar by remember { mutableStateOf(false) }
    if (p61ShowSimilar) P61SimilarDialog(repo, aya.surah, aya.ayah, onDismiss = { p61ShowSimilar = false })

    val info: QuranExplanation? = when (explanationMode) {
        ExplanationMode.WORDS -> repo.meaning(aya.surah, aya.ayah)
        ExplanationMode.TAFSIR -> repo.tafsir(aya.surah, aya.ayah)
        null -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = paper,
        contentColor = ink,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "سورة ${repo.surahName(aya.surah)} — الآية ${aya.ayah}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                "تم تحديد هذه الآية على صفحة المصحف ✓",
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(9.dp))

            Surface(
                color = soft.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    aya.text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    fontFamily = P61Amiri,
                    fontSize = 26.sp,
                    lineHeight = 46.sp,
                    textAlign = TextAlign.Right,
                    color = ink,
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Button(
                    onClick = onPlay,
                    enabled = audioConfig != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    if (audioLoadingKey == key) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            if (playingKey == key) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(if (playingKey == key) "إيقاف" else "استماع للحصري")
                }

                OutlinedButton(
                    onClick = onBookmark,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                ) {
                    Icon(
                        if (bookmark == (aya.surah to aya.ayah)) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("علامة")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FilledTonalButton(
                    onClick = { onExplanationMode(ExplanationMode.WORDS) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = soft, contentColor = ink),
                ) { Text("معاني الكلمات") }

                FilledTonalButton(
                    onClick = { onExplanationMode(ExplanationMode.TAFSIR) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = soft, contentColor = ink),
                ) { Text("التفسير الميسر") }
            }
            if (P61Similar.has(p61Ctx, aya.surah, aya.ayah)) {
                FilledTonalButton(
                    onClick = { p61ShowSimilar = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = soft, contentColor = ink),
                ) { Text("🔁 آيات متشابهة") }
            }

            if (explanationMode != null) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = accent.copy(alpha = 0.28f))
                Text(
                    if (explanationMode == ExplanationMode.WORDS) "معاني الكلمات" else "التفسير الميسر",
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Spacer(Modifier.height(6.dp))
                if (info == null || info.text.isBlank()) {
                    Text("المحتوى غير متاح لهذه الآية.", color = ink)
                } else {
                    Text(info.text, fontSize = 18.sp, lineHeight = 30.sp, color = ink)
                    if (info.footnotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info.footnotes, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f))
                    }
                }
                Text(
                    "المصدر: QuranEnc.com — المحتوى معروض دون تعديل.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingQuranScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المصحف الشريف") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.Center) {
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text("محتوى المصحف غير متاح في هذا البناء", fontWeight = FontWeight.Bold)
                    Text("يجب تضمين نص المصحف الموثق قبل إنشاء APK.")
                }
            }
        }
    }
}

/** VC101: unobtrusive, non-modal verse player for the enlarged Mushaf reading mode.
 * Selecting an ayah docks it above the bottom edge; the down arrow collapses it
 * to a single row and NEVER stops audio. The reader remains swipeable outside it.
 */
@Composable
private fun P101ReadingAudioDock(
    aya: QuranAyah,
    surahName: String,
    reciterName: String,
    selectedPlaying: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    message: String?,
    onPlayPause: () -> Unit,
    onRepeat: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var minimized by remember(aya.surah, aya.ayah) { mutableStateOf(false) }
    val green = Color(0xFF07533F)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFFEFA),
        contentColor = green,
        shadowElevation = 9.dp,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFBFA979).copy(alpha = .65f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (minimized) 2.dp else 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { minimized = !minimized }) {
                    Text(if (minimized) "↑ فتح" else "↓ تصغير", fontSize = 14.sp, color = green)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$surahName — الآية ${aya.ayah}", fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, color = green, maxLines = 1)
                    if (!minimized) Text(reciterName, fontSize = 12.sp,
                        color = Color(0xFF755D37), maxLines = 1)
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(42.dp)) {
                    if (isLoading && selectedPlaying) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = green)
                    } else Icon(
                        if (selectedPlaying && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (selectedPlaying && isPlaying) "إيقاف مؤقت" else "استمع للآية",
                        tint = green,
                    )
                }
                // Preserve access to the exact pre-reading options ("تلاوة", "هذه الآية فقط",
                // "تشغيل إلى…", bookmark, copy, similar verses, meanings). It is opt-in;
                // the nonmodal dock remains visible for ordinary reading.
                TextButton(onClick = onMore, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("الخيارات", fontSize = 12.sp, color = green)
                }
                if (!minimized) TextButton(onClick = onDismiss) { Text("×", fontSize = 22.sp, color = green) }
            }
            if (!minimized) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRepeat) {
                        Text("↻ كرر الآية", color = green, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("اسحب الصفحات يمينًا وشمالًا أثناء الاستماع", fontSize = 11.sp,
                        color = Color(0xFF756B5C), maxLines = 1)
                }
                if (!message.isNullOrBlank()) {
                    Text(message, color = Color(0xFF933F33), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        maxLines = 2)
                }
            }
        }
    }
}
