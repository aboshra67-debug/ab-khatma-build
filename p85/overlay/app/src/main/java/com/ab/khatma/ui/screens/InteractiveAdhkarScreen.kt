package com.ab.khatma.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/** P38.1: additive presentation over the original P38 offline Arabic Hisn dataset.
 * The P38 AdhkarScreen is kept intact as a compatibility fallback. */
private data class DhRecord(val id: String, val text: String, val required: Int, val source: String)
private data class DhChapter(val title: String, val records: List<DhRecord>, val group: String)
private data class DhCategory(val title: String, val icon: String, val first: Color, val second: Color)

private val dhInk = Color(0xFF18312D)
private val dhGreen = Color(0xFF006458)
private val dhBeige = Color(0xFFF8F7F2)
private val dhGray = Color(0xFF687C74)

private val dhCategories = listOf(
    DhCategory("أذكار الصباح", "☀", Color(0xFF258AC5), Color(0xFF14517F)),
    DhCategory("أذكار المساء", "☾", Color(0xFF6746AC), Color(0xFF35216B)),
    DhCategory("بعد الصلاة", "✦", Color(0xFF138B71), Color(0xFF006454)),
    DhCategory("النوم والاستيقاظ", "☽", Color(0xFFA54D9B), Color(0xFF683E9D)),
    DhCategory("الصلاة والمسجد", "۞", Color(0xFFAE712E), Color(0xFF885020)),
    DhCategory("السفر والتنقل", "✈", Color(0xFF168FA2), Color(0xFF086779)),
    DhCategory("البيت واليوميات", "⌂", Color(0xFF7DA824), Color(0xFF49791C)),
    DhCategory("الصحة والأهل", "♡", Color(0xFFC45A72), Color(0xFF893B62)),
    DhCategory("الحج والعمرة", "◈", Color(0xFF9E8739), Color(0xFF756123)),
    DhCategory("أدعية وأذكار أخرى", "❀", Color(0xFF687FAD), Color(0xFF42517D))
)

private fun cleanDh(value: String): String = value
    .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
    .lowercase(Locale.ROOT)

private fun dhGroup(title: String): String {
    val n = cleanDh(title)
    if (n.contains("اذكار الصباح")) return "أذكار الصباح"
    if (n.contains("اذكار المساء")) return "أذكار المساء"
    if (n.contains("بعد السلام من الصلاه") || n.contains("بعد السلام من الصلاة")) return "بعد الصلاة"
    if (listOf("النوم", "الاستيقاظ", "ليلا", "الرؤيا", "الحلم").any { n.contains(it) }) return "النوم والاستيقاظ"
    if (listOf("المسجد", "الوضوء", "الصلاه", "الصلاة", "الاذان", "الركوع", "السجود", "التشهد", "الوتر", "الاستفتاح", "الاستخاره", "القنوت").any { n.contains(it) }) return "الصلاة والمسجد"
    if (listOf("السفر", "المسافر", "للمقيم", "الركوب", "المركوب", "القريه", "البلده", "السوق").any { n.contains(it) }) return "السفر والتنقل"
    if (listOf("الحج", "العمره", "المحرم", "الصفا", "المروه", "عرفه", "الركن", "الجمار", "المشعر").any { n.contains(it) }) return "الحج والعمرة"
    if (listOf("المرض", "المريض", "الميت", "القبر", "المصيبه", "المولود", "الاولاد", "الفرط", "التعزيه", "المحتضر", "الدَّفن", "الدفن", "وجعا", "جسده").any { n.contains(it) }) return "الصحة والأهل"
    if (listOf("الثوب", "الخلاء", "المنزل", "الطعام", "الصائم", "الافطار", "الافطار", "العطاس", "المجلس", "الضيف", "المتزوج", "الزوجه", "البيت", "اللباس", "اللبس", "السَّلام", "السلام", "المطر", "الريح", "الرعد", "الهلال").any { n.contains(it) }) return "البيت واليوميات"
    return "أدعية وأذكار أخرى"
}

private object DhOfflineBook {
    @Volatile private var saved: List<DhChapter>? = null
    fun load(context: Context): List<DhChapter> {
        saved?.let { return it }
        val raw = context.assets.open("adhkar/hisn.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        val result = mutableListOf<DhChapter>()
        val names = root.keys()
        while (names.hasNext()) {
            val title = names.next()
            val source = root.optJSONObject(title)?.optJSONArray("Adhkar") ?: continue
            val records = (0 until source.length()).mapNotNull { index ->
                val obj = source.optJSONObject(index) ?: return@mapNotNull null
                val text = obj.optString("Text").trim()
                if (text.isEmpty()) null else DhRecord(
                    // Exact same stable key as P38, so favorites and counters are preserved.
                    "${title.hashCode()}_$index", text,
                    obj.optInt("Count", 1).coerceAtLeast(1), obj.optString("Reference"))
            }
            if (records.isNotEmpty()) result.add(DhChapter(title, records, dhGroup(title)))
        }
        return result.sortedBy { cleanDh(it.title) }.also { saved = it }
    }
}

@Composable
private fun DhCategoryCard(category: DhCategory, chapters: List<DhChapter>, modifier: Modifier, onClick: () -> Unit) {
    val total = chapters.sumOf { it.records.size }
    Card(onClick = onClick, modifier = modifier.height(143.dp), shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = category.first)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(category.first, category.second)))) {
            Text(category.icon, fontSize = 48.sp, color = Color.White.copy(alpha = .62f),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                Text(category.title, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2)
                Text("${chapters.size} باب • $total ذكر", color = Color.White.copy(alpha = .88f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DhChapterRow(chapter: DhChapter, completed: Int, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Color(0xFFE6EAE4)),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(chapter.title, color = dhInk, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                Text("${chapter.records.size} أذكار" + if (completed >= chapter.records.size) " • ✓ مكتمل" else if (completed > 0) " • $completed مكتمل" else "", color = dhGray, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { completed.toFloat() / chapter.records.size.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(8.dp)),
                    color = dhGreen, trackColor = Color(0xFFE4EDE9))
            }
            Spacer(Modifier.width(10.dp))
            Text("‹", color = dhGreen, fontSize = 28.sp)
        }
    }
}

/** Friendly RTL journey: visual categories -> chapters -> one recitation at a time. */
@Composable
internal fun P56AdhkarMain(onBack: () -> Unit, onRuqya: () -> Unit, onQuranDuas: () -> Unit = {}, onTasbih: () -> Unit = {}, onKhatmDua: () -> Unit = {}, onVoice: () -> Unit = {}) {
    val context = LocalContext.current
    DisposableEffect(Unit) { onDispose { P56AdhkarAudio.stop(); P57Tts.stop() } }
    val pref = remember(context) { context.getSharedPreferences("ab_khatma_adhkar_v1", Context.MODE_PRIVATE) }
    val today = LocalDate.now().toString()
    var loadError by remember { mutableStateOf<String?>(null) }
    val chapters by produceState(initialValue = emptyList<DhChapter>(), context) {
        value = runCatching { withContext(Dispatchers.IO) { DhOfflineBook.load(context.applicationContext) } }
            .getOrElse { loadError = "تعذر قراءة الأذكار المحلية"; emptyList() }
    }
    var currentGroup by remember { mutableStateOf<String?>(null) }
    var currentChapter by remember { mutableStateOf<DhChapter?>(null) }
    var readerList by remember { mutableStateOf<List<DhRecord>?>(null) }
    var readerLabel by remember { mutableStateOf("") }
    var at by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var chapterQuery by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("الكل") }
    var showReference by remember { mutableStateOf(true) }
    var favourites by remember { mutableStateOf(pref.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var progressChange by remember { mutableIntStateOf(0) }
    val counterCache = remember { mutableStateMapOf<String, Int>() }
    fun done(record: DhRecord): Int {
        val key = "$today:${record.id}"
        return (counterCache[key] ?: pref.getInt(key, 0)).coerceIn(0, record.required)
    }
    fun openReader(title: String, items: List<DhRecord>, index: Int = 0, chapter: DhChapter? = null) {
        if (items.isEmpty()) return
        readerLabel = title
        readerList = items
        currentChapter = chapter
        at = index.coerceIn(0, items.lastIndex)
        if (chapter != null) pref.edit().putString("last_chapter", chapter.title)
            .putInt("last_index_${chapter.title.hashCode()}", at).apply()
    }
    fun changePage(index: Int) {
        val items = readerList ?: return
        at = index.coerceIn(0, items.lastIndex)
        currentChapter?.let { pref.edit().putInt("last_index_${it.title.hashCode()}", at).apply() }
    }
    fun navigateBack() {
        when {
            readerList != null -> { readerList = null; currentChapter = null }
            currentGroup != null -> currentGroup = null
            tab != "الكل" -> tab = "الكل"
            query.isNotBlank() -> query = ""
            else -> onBack()
        }
    }
    BackHandler { navigateBack() }
    LaunchedEffect(readerLabel) { P56AdhkarAudio.stop(); P57Tts.stop() }
    val active = readerList
    val totalToday = remember(chapters, progressChange, today) {
        chapters.sumOf { c -> c.records.count { done(it) >= it.required } }
    }
    val allRecords = remember(chapters) { chapters.flatMap { c -> c.records.map { c.title to it } } }
    val normalizedQuery = remember(query) { cleanDh(query.trim()) }
    val searchChapters = remember(chapters, normalizedQuery) {
        if (normalizedQuery.isEmpty()) emptyList() else chapters.filter { c ->
            cleanDh(c.title).contains(normalizedQuery) || c.records.any { cleanDh(it.text).contains(normalizedQuery) }
        }
    }
    val preferred = remember(favourites, allRecords) { allRecords.filter { it.second.id in favourites } }
    val mostUsed = remember(progressChange, chapters) {
        allRecords.filter { pref.getInt("total:${it.second.id}", 0) > 0 }
            .sortedByDescending { pref.getInt("total:${it.second.id}", 0) }.take(30)
    }
    val startedToday = allRecords.filter { done(it.second) > 0 }
    val listState = rememberLazyListState()
    val routeKey = listOf(currentGroup ?: "", currentChapter?.title ?: "", readerLabel,
        if (readerList == null) "list" else "reader", tab, at.toString(),
        if (normalizedQuery.isBlank()) "all" else "search").joinToString("|")
    LaunchedEffect(routeKey) { listState.scrollToItem(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(dhBeige)
            .windowInsetsPadding(WindowInsets.safeDrawing)) {
            com.ab.khatma.guide.P85Guide.HelpButton("adhkar")
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = dhGreen)) {
                        Column(Modifier.fillMaxWidth().padding(if (active != null) 8.dp else if (currentGroup != null) 12.dp else 19.dp)) {
                            Text(when {
                                active != null -> readerLabel
                                currentGroup != null -> currentGroup!!
                                else -> "الأدعية والأذكار"
                            }, fontSize = if (active != null) 17.sp else 24.sp,
                                color = Color.White, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(5.dp))
                            if (active == null) Text(when {
                                active != null -> "اقرأ الذكر وسبّح بالعدد الوارد ثم انتقل للتالي"
                                currentGroup != null -> "اختر بابًا لتبدأ الأذكار بالتتابع"
                                else -> "حصن المسلم • أذكار يومية بتجربة تفاعلية"
                            }, fontSize = 13.sp, color = Color(0xFFDCEDE9))
                            if (active == null) {
                                val p68St = remember(progressChange, today) { p68Streak(pref, today) }
                                if (p68St > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("🔥 مواظب على أذكارك $p68St يوم متتالي", fontSize = 12.sp, color = Color(0xFFFFE6A9))
                                }
                            }
                            if (active == null && chapters.isNotEmpty()) {
                                Spacer(Modifier.height(9.dp))
                                Text("${chapters.size} باب • ${allRecords.size} ذكر ودعاء • أتممت اليوم $totalToday",
                                    fontSize = 12.sp, color = Color(0xFFFFE6A9))
                            }
                        }
                    }
                }
                if (loadError != null) item { Text(loadError!!, color = MaterialTheme.colorScheme.error) }
                if (chapters.isEmpty() && loadError == null) item { CircularProgressIndicator(color = dhGreen) }
                if (active != null && active.isNotEmpty()) {
                    val rec = active[at.coerceIn(0, active.lastIndex)]
                    val completed = done(rec)
                    val cat = dhCategories.firstOrNull { it.title == currentChapter?.group }
                    item { P57ChapterAudioRow(currentChapter?.title, active.map { it.text }) }
                    item {
                        P57DhikrCard(
                            recId = rec.id,
                            text = rec.text,
                            source = rec.source,
                            required = rec.required,
                            completed = completed,
                            first = cat?.first ?: dhGreen,
                            second = cat?.second ?: Color(0xFF0B3B33),
                            favourite = rec.id in favourites,
                            hasNext = at < active.lastIndex,
                            onCount = {
                                if (completed < rec.required) {
                                    val nextCount = completed + 1
                                    val key = "$today:${rec.id}"
                                    counterCache[key] = nextCount
                                    pref.edit().putInt(key, nextCount)
                                        .putInt("total:${rec.id}", pref.getInt("total:${rec.id}", 0) + 1).apply()
                                    p68MarkAdhkarDay(pref, today)
                                    progressChange++
                                    nextCount >= rec.required
                                } else false
                            },
                            onReset = {
                                val key = "$today:${rec.id}"; counterCache[key] = 0
                                pref.edit().putInt(key, 0).apply(); progressChange++
                            },
                            onUndo = {
                                val key = "$today:${rec.id}"
                                val current = done(rec)
                                if (current > 0) {
                                    counterCache[key] = current - 1
                                    val lifetime = pref.getInt("total:${rec.id}", 0)
                                    pref.edit().putInt(key, current - 1)
                                        .putInt("total:${rec.id}", (lifetime - 1).coerceAtLeast(0)).apply()
                                    progressChange++
                                }
                            },
                            onFavourite = {
                                favourites = if (rec.id in favourites) favourites - rec.id else favourites + rec.id
                                pref.edit().putStringSet("favorites", favourites).apply()
                            },
                            onAdvance = { changePage(at + 1) },
                        )
                    }
                    if (at == active.lastIndex && active.all { done(it) >= it.required }) {
                        item { Text("ما شاء الله، أكملت أذكار هذا الباب اليوم ✨", color = dhGreen,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)) }
                    }
                } else if (currentGroup != null) {
                    val groupChapters = chapters.filter {
                        it.group == currentGroup && (chapterQuery.isBlank() || cleanDh(it.title).contains(cleanDh(chapterQuery)))
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = chapterQuery, onValueChange = { chapterQuery = it },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                placeholder = { Text("ابحث عن باب") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp))
                            val lastName = pref.getString("last_chapter", null)
                            val last = chapters.firstOrNull { it.title == lastName && it.group == currentGroup }
                            if (last != null) OutlinedButton(
                                onClick = { openReader(last.title, last.records,
                                    pref.getInt("last_index_${last.title.hashCode()}", 0), last) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)) { Text("▶ كمّل من حيث وقفت") }
                            Text("الأبواب (${groupChapters.size})", color = dhInk,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(groupChapters, key = { it.title }) { chapter ->
                        DhChapterRow(chapter, chapter.records.count { done(it) >= it.required }) {
                            openReader(chapter.title, chapter.records,
                                pref.getInt("last_index_${chapter.title.hashCode()}", 0), chapter)
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(value = query, onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text("ابحث عن ذكر أو باب") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp))
                    }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("الكل", "اليوم", "المفضلة", "الأكثر استخدامًا").forEach { option ->
                                FilterChip(selected = tab == option, onClick = { tab = option; query = "" },
                                    label = { Text(option, fontSize = 11.sp) })
                            }
                        }
                    }
                    if (normalizedQuery.isNotBlank()) {
                        item { Text("نتائج البحث (${searchChapters.size} باب)", fontWeight = FontWeight.Bold, color = dhInk) }
                        items(searchChapters, key = { it.title }) { chapter ->
                            DhChapterRow(chapter, chapter.records.count { done(it) >= it.required }) {
                                openReader(chapter.title, chapter.records, chapter.records.indexOfFirst {
                                    cleanDh(it.text).contains(normalizedQuery)
                                }.coerceAtLeast(0), chapter)
                            }
                        }
                        if (searchChapters.isEmpty()) item { Text("لا توجد نتائج مطابقة", color = dhGray) }
                    } else if (tab == "الكل") {
                        val last = pref.getString("last_chapter", null)
                        val lastChapter = chapters.firstOrNull { it.title == last }
                        if (lastChapter != null) item {
                            OutlinedCard(onClick = { openReader(lastChapter.title, lastChapter.records,
                                pref.getInt("last_index_${lastChapter.title.hashCode()}", 0), lastChapter) },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFE8F2ED))) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text("متابعة من آخر ذكر", color = dhGreen, fontWeight = FontWeight.Bold)
                                    Text(lastChapter.title, color = dhInk, fontSize = 13.sp, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        item { P56RuqyaCard(onRuqya) }
                        item { P57QuranDuasCard(onQuranDuas) }
                        item { P60VoiceCard(onVoice) }
                        item { P58ToolsRow(onTasbih, onKhatmDua) }
                        item { Text("أذكار اليوم", color = dhInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        val priority = dhCategories.take(4)
                        items(priority.chunked(2)) { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { cat ->
                                    val group = chapters.filter { it.group == cat.title }
                                    DhCategoryCard(cat, group, Modifier.weight(1f)) { currentGroup = cat.title }
                                }
                                if (pair.size < 2) Spacer(Modifier.weight(1f))
                            }
                        }
                        item { Text("بقية الأقسام", color = dhInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        items(dhCategories.drop(4).chunked(2)) { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { cat ->
                                    val group = chapters.filter { it.group == cat.title }
                                    DhCategoryCard(cat, group, Modifier.weight(1f)) { currentGroup = cat.title }
                                }
                                if (pair.size < 2) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        val data = when (tab) {
                            "اليوم" -> startedToday
                            "المفضلة" -> preferred
                            else -> mostUsed
                        }
                        item { Text("$tab (${data.size})", color = dhInk, fontWeight = FontWeight.Bold) }
                        if (data.isEmpty()) item {
                            Text(when (tab) {
                                "المفضلة" -> "لم تحفظ أذكارًا في المفضلة بعد"
                                "اليوم" -> "ابدأ ذكرًا من الأقسام ليظهر إنجازك هنا"
                                else -> "سجّل تكرار أذكارك لتظهر هنا"
                            }, color = dhGray, modifier = Modifier.padding(12.dp))
                        }
                        items(data, key = { it.second.id }) { (title, rec) ->
                            Card(onClick = { openReader(title, listOf(rec)) },
                                border = BorderStroke(1.dp, Color(0xFFE3EAE5)),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(title, color = dhGreen, fontSize = 12.sp)
                                    Text(rec.text, color = dhInk, fontSize = 15.sp,
                                        maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                                    Text("\u202A${done(rec)} / ${rec.required}\u202C", color = dhGray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                item { Text("المصدر: حصن المسلم (HisnElMuslim، ترخيص MIT). التكرارات والمراجع كما وردت في المصدر.",
                    color = dhGray, fontSize = 11.sp, lineHeight = 16.sp) }
            }
            if (active != null && active.isNotEmpty()) {
                val rec = active[at.coerceIn(0, active.lastIndex)]
                P57ReaderBar(
                    at = at.coerceIn(0, active.lastIndex),
                    total = active.size,
                    isDone = { i -> done(active[i]) >= active[i].required },
                    onPrev = { changePage(at - 1) },
                    onNext = { changePage(at + 1) },
                    completed = done(rec),
                    required = rec.required,
                    onCount = {
                        val current = done(rec)
                        if (current < rec.required) {
                            val key = "$today:${rec.id}"
                            counterCache[key] = current + 1
                            pref.edit().putInt(key, current + 1)
                                .putInt("total:${rec.id}", pref.getInt("total:${rec.id}", 0) + 1).apply()
                            p68MarkAdhkarDay(pref, today)
                            progressChange++
                        }
                    },
                    onUndo = {
                        val current = done(rec)
                        if (current > 0) {
                            val key = "$today:${rec.id}"
                            counterCache[key] = current - 1
                            pref.edit().putInt(key, current - 1)
                                .putInt("total:${rec.id}", (pref.getInt("total:${rec.id}", 0) - 1).coerceAtLeast(0)).apply()
                            progressChange++
                        }
                    },
                )
            }
            P53BottomNav(onBack = { navigateBack() }, onHome = onBack)
        }
    }
}
