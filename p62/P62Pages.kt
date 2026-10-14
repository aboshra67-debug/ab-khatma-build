package com.ab.khatma.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ab.khatma.quran.QuranAyah
import com.ab.khatma.quran.QuranRepository

private fun p62Plain(a: QuranAyah): String =
    P61Words.words(P61Words.body(a)).joinToString(" ") { P61Words.norm(it) }

/** «🔁 المتشابهات»: its own page. Pick a surah, see its ayat that have look-alikes, tap one to compare. */
@Composable
internal fun P62SimilarScreen(repo: QuranRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onClose() }
    var surah by remember { mutableIntStateOf(2) }
    var query by remember { mutableStateOf("") }
    var openAyah by remember { mutableIntStateOf(0) }
    val withMatches = remember(surah) {
        repo.loadSurah(surah).filter { P61Similar.has(context, it.surah, it.ayah) }
    }
    val plain = remember(withMatches) { withMatches.associate { it.ayah to p62Plain(it) } }
    val q = remember(query) { P61Words.words(query.trim()).joinToString(" ") { P61Words.norm(it) } }
    val shown = remember(withMatches, q) {
        if (q.isBlank()) withMatches else withMatches.filter { plain[it.ayah]?.contains(q) == true }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(surah, q) { listState.scrollToItem(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(P61Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
            P62Header("🔁 المتشابهات", "آيات تتكرر فيها ألفاظ بنفس الترتيب", onClose)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                P62SurahPicker(repo, surah) { surah = it; openAyah = 0; query = "" }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث بكلمة من الآية داخل هذه السورة") },
                )
                Text(
                    "في سورة ${repo.surahName(surah)}: ${p61Digits(withMatches.size)} آية لها متشابهات" +
                        (if (q.isNotBlank()) " — المعروض ${p61Digits(shown.size)}" else ""),
                    fontSize = 12.sp, color = P61Ink.copy(alpha = 0.65f),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) item {
                    Text(
                        if (withMatches.isEmpty()) "لا توجد في هذه السورة آيات لها متشابهات بالمعيار المستخدم."
                        else "لا توجد آية بهذه الكلمات.",
                        color = P61Ink.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 18.dp),
                    )
                }
                items(shown, key = { it.ayah }) { aya ->
                    val isOpen = openAyah == aya.ayah
                    val others = if (isOpen) P61Similar.similar(context, aya.surah, aya.ayah) else emptyList()
                    Surface(
                        onClick = { openAyah = if (isOpen) 0 else aya.ayah },
                        shape = RoundedCornerShape(16.dp), color = Color.White,
                        border = BorderStroke(1.dp, if (isOpen) P61Gold else Color(0xFFE6DCC2)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "الآية ${p61Digits(aya.ayah)}", fontWeight = FontWeight.Bold, color = P61Gold,
                                    fontSize = 14.sp, modifier = Modifier.weight(1f),
                                )
                                Text(if (isOpen) "إخفاء ▴" else "عرض المتشابهات ▾", color = P61Green, fontSize = 12.sp)
                            }
                            Text(
                                P61Words.body(aya), fontFamily = P61Amiri, fontSize = 22.sp, lineHeight = 40.sp,
                                color = P61Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                            if (isOpen) {
                                val mine = P61Words.words(P61Words.body(aya))
                                others.forEach { (s, a) ->
                                    val other = repo.ayah(s, a) ?: return@forEach
                                    val words = P61Words.words(P61Words.body(other))
                                    val diff = P61Words.diff(mine, words).second
                                    Spacer(Modifier.height(8.dp))
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8F1DC)) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text(
                                                "سورة ${repo.surahName(s)} — الآية ${p61Digits(a)}",
                                                fontWeight = FontWeight.Bold, color = P61Green, fontSize = 13.sp,
                                            )
                                            Text(
                                                p61Colored(words, diff, P61Ink),
                                                fontFamily = P61Amiri, fontSize = 22.sp, lineHeight = 40.sp,
                                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "الكلمات الحمراء هي الفرق عن هذه الآية.",
                                    fontSize = 11.sp, color = P61Ink.copy(alpha = 0.55f), modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "المتشابهات مستخرجة تلقائيًا بمقارنة ألفاظ الآيات في نص مصحف تنزيل، وليست حصرًا كاملًا لكل ما ذكره العلماء.",
                        fontSize = 11.sp, color = P61Ink.copy(alpha = 0.55f), lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            P53BottomNav(onBack = onClose, onHome = onClose)
        }
    }
}

/** «📖 المفردات»: its own page. The ayat of a surah, each with the meaning of its words (QuranEnc, unchanged). */
@Composable
internal fun P62MeaningsScreen(repo: QuranRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    BackHandler { onClose() }
    var surah by remember { mutableIntStateOf(1) }
    var query by remember { mutableStateOf("") }
    val verses = remember(surah) { repo.loadSurah(surah) }
    val plain = remember(verses) {
        verses.associate { a ->
            a.ayah to (p62Plain(a) + " " + P61Words.words(repo.meaning(a.surah, a.ayah)?.text.orEmpty()).joinToString(" ") { P61Words.norm(it) })
        }
    }
    val q = remember(query) { P61Words.words(query.trim()).joinToString(" ") { P61Words.norm(it) } }
    val shown = remember(verses, q) {
        if (q.isBlank()) verses else verses.filter { plain[it.ayah]?.contains(q) == true }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(surah, q) { listState.scrollToItem(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(P61Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
            P62Header("📖 المفردات", "معاني كلمات كل آية", onClose)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                P62SurahPicker(repo, surah) { surah = it; query = "" }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث بكلمة أو بمعنى داخل هذه السورة") },
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) item {
                    Text("لا توجد آية بهذه الكلمات.", color = P61Ink.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 18.dp))
                }
                items(shown, key = { it.ayah }) { aya ->
                    val info = repo.meaning(aya.surah, aya.ayah)
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE6DCC2)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "الآية ${p61Digits(aya.ayah)}", fontWeight = FontWeight.Bold, color = P61Gold,
                                    fontSize = 14.sp, modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    val code = P52SurahAudio.ayahCode(aya.surah, aya.ayah)
                                    P52SurahAudio.play(context, listOf(code))
                                }) { Text("▶ استمع", color = P61Green) }
                            }
                            Text(
                                P61Words.body(aya), fontFamily = P61Amiri, fontSize = 22.sp, lineHeight = 40.sp,
                                color = P61Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8F1DC)) {
                                Text(
                                    if (info == null || info.text.isBlank()) "المحتوى غير متاح لهذه الآية." else info.text,
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    fontSize = 15.sp, lineHeight = 26.sp, color = P61Ink,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "المصدر: QuranEnc.com — المحتوى معروض دون تعديل.",
                        fontSize = 11.sp, color = P61Ink.copy(alpha = 0.55f), modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            P53BottomNav(onBack = onClose, onHome = onClose)
        }
    }
}
