package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One distinctive word of an ayah. [level] 3 = strongest (a long phrase that differs by this one word only). */
internal class P67Word(val index: Int, val level: Int, val variants: List<Pair<String, String>>)

internal class P67Item(val surah: Int, val ayah: Int, val words: List<P67Word>) {
    val key: String get() = "$surah:$ayah"
    val strength: Int get() = words.maxOfOrNull { it.level } ?: 1
}

/** «المنفردات» data, computed from the app's own Quran text (assets/quran/p67_unique.json) and saved progress. */
internal object P67Munfaradat {
    private const val PREFS = "p67_munf"

    fun load(context: Context): List<P67Item> {
        val raw = context.applicationContext.assets.open("quran/p67_unique.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val o = JSONObject(raw)
        val out = ArrayList<P67Item>(o.length())
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val p = k.split(':')
            val arr = o.getJSONArray(k)
            val words = ArrayList<P67Word>(arr.length())
            for (i in 0 until arr.length()) {
                val w = arr.getJSONArray(i)
                val vs = w.getJSONArray(2)
                words.add(P67Word(w.getInt(0), w.getInt(1), (0 until vs.length()).map { j ->
                    val v = vs.getJSONArray(j)
                    v.getString(0) to v.getString(1)
                }))
            }
            out.add(P67Item(p[0].toInt(), p[1].toInt(), words))
        }
        out.sortWith(compareBy({ it.surah }, { it.ayah }))
        return out
    }

    fun status(context: Context, kind: String): Set<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(kind, emptySet()) ?: emptySet()

    fun setStatus(context: Context, kind: String, value: Set<String>) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(kind, HashSet(value)).apply()
    }
}

private fun p67Stars(level: Int) = "★".repeat(level.coerceIn(1, 3))

private class P67Question(val item: P67Item, val word: P67Word, val text: String, val options: List<String>, val correct: String)

@Composable
internal fun P67MunfaradatScreen(repo: QuranRepository, onClose: () -> Unit, onOpenPage: (Int) -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<P67Item>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        P52SurahAudio.init(context)
        val r = withContext(Dispatchers.Default) { runCatching { P67Munfaradat.load(context) }.getOrNull() }
        if (r == null) failed = true else all = r
    }
    var surah by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) } // 0 all, 1 strong, 2 review, 3 learned
    var strongFirst by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var learned by remember { mutableStateOf(P67Munfaradat.status(context, "learned")) }
    var review by remember { mutableStateOf(P67Munfaradat.status(context, "review")) }
    var quiz by remember { mutableStateOf<List<P67Question>?>(null) }
    var menu by remember { mutableStateOf(false) }

    val list = all
    val shown = remember(list, surah, filter, strongFirst, query, learned, review) {
        val q = query.trim()
        var l = (list ?: emptyList()).asSequence()
        if (surah > 0) l = l.filter { it.surah == surah }
        l = when (filter) {
            1 -> l.filter { it.strength >= 3 }
            2 -> l.filter { it.key in review }
            3 -> l.filter { it.key in learned }
            else -> l
        }
        if (q.isNotEmpty()) l = l.filter { it.key.contains(q) || repo.surahName(it.surah).contains(q) || (repo.ayah(it.surah, it.ayah)?.text?.contains(q) == true) }
        val out = l.toList()
        if (strongFirst) out.sortedByDescending { it.strength } else out
    }

    Column(Modifier.fillMaxSize().background(P61Paper)) {
        P62Header("🔎 المنفردات", "الكلمة المميّزة في كل آية، وما يشبهها في غيرها", onClose)
        val qz = quiz
        if (qz != null) {
            P67Quiz(qz, onExit = { quiz = null }, onLearned = { key ->
                learned = learned + key
                P67Munfaradat.setStatus(context, "learned", learned)
            }, onReview = { key ->
                review = review + key
                P67Munfaradat.setStatus(context, "review", review)
            })
            return@Column
        }
        if (failed) {
            Text("تعذّر تحميل بيانات المنفردات.", color = P61Wrong, modifier = Modifier.padding(20.dp))
            return@Column
        }
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = P61Green) }
            return@Column
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(if (surah == 0) "كل السور ▾" else "سورة ${repo.surahName(surah)} ▾", color = P61Green, maxLines = 1)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("كل السور") }, onClick = { surah = 0; menu = false })
                        repo.surahs().forEach { s ->
                            DropdownMenuItem(text = { Text("${p61Digits(s.index)}. ${s.name}") }, onClick = { surah = s.index; menu = false })
                        }
                    }
                }
                Button(
                    onClick = {
                        val src = shown.filter { it.key !in learned }.ifEmpty { shown }
                        val pool = src.flatMap { it.words.map { w -> it to w } }
                        val q = p67MakeQuiz(repo, pool, list)
                        if (q.isNotEmpty()) quiz = q
                    },
                    enabled = shown.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = P61Gold),
                ) { Text("🎯 اختبر نفسك", fontWeight = FontWeight.Bold) }
            }
            P62ChipRow(
                listOf("الكل" to (filter == 0), "قوية ★★★" to (filter == 1), "أراجعها" to (filter == 2), "حفظتها" to (filter == 3)),
            ) { filter = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("بحث: سورة أو كلمة", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                )
                TextButton(onClick = { strongFirst = !strongFirst }) {
                    Text(if (strongFirst) "ترتيب: الأقوى ✓" else "ترتيب: المصحف", color = P61Green, fontSize = 12.sp)
                }
            }
            Text(
                "${p61Digits(shown.size)} آية • حفظتها ${p61Digits(learned.size)} • أراجعها ${p61Digits(review.size)}",
                color = P61Ink.copy(alpha = 0.6f), fontSize = 12.sp,
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.key }) { item ->
                P67Card(
                    repo = repo, item = item, isLearned = item.key in learned, inReview = item.key in review,
                    onPlay = {
                        P52SurahAudio.play(context, listOf(P52SurahAudio.ayahCode(item.surah, item.ayah)))
                    },
                    onOpen = { repo.ayah(item.surah, item.ayah)?.let { onOpenPage(it.page) } },
                    onLearned = {
                        learned = if (item.key in learned) learned - item.key else learned + item.key
                        review = review - item.key
                        P67Munfaradat.setStatus(context, "learned", learned)
                        P67Munfaradat.setStatus(context, "review", review)
                    },
                    onReview = {
                        review = if (item.key in review) review - item.key else review + item.key
                        learned = learned - item.key
                        P67Munfaradat.setStatus(context, "learned", learned)
                        P67Munfaradat.setStatus(context, "review", review)
                    },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        if (P52SurahAudio.isActive) Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { P66Bar() }
    }
}

@Composable
private fun P67Card(
    repo: QuranRepository,
    item: P67Item,
    isLearned: Boolean,
    inReview: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onLearned: () -> Unit,
    onReview: () -> Unit,
) {
    val aya = repo.ayah(item.surah, item.ayah) ?: return
    val tokens = remember(aya.text) { aya.text.split(' ').filter { it.isNotEmpty() } }
    val marked = remember(item) { item.words.associateBy { it.index } }
    val styled = buildAnnotatedString {
        tokens.forEachIndexed { i, t ->
            val w = marked[i]
            if (w != null) {
                withStyle(SpanStyle(color = P61Green, fontWeight = FontWeight.ExtraBold, background = P61Tan.copy(alpha = 0.55f))) { append(t) }
            } else append(t)
            if (i < tokens.lastIndex) append(" ")
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp), color = Color.White,
        border = BorderStroke(1.dp, if (isLearned) P61Green.copy(alpha = 0.5f) else if (inReview) P61Gold else Color(0xFFE6DCC2)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "سورة ${repo.surahName(item.surah)} : ${p61Digits(item.ayah)}", color = P61Green, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                Text(p67Stars(item.strength), color = P61Gold, fontSize = 14.sp)
            }
            Text(styled, fontFamily = P61Amiri, fontSize = 22.sp, lineHeight = 40.sp, color = P61Ink, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            item.words.forEach { w ->
                if (w.variants.isNotEmpty()) {
                    val word = tokens.getOrNull(w.index) ?: ""
                    Text(
                        "«$word» وفي غيره: " + w.variants.joinToString(" ، ") { (v, ref) ->
                            val p = ref.split(':')
                            "$v (${repo.surahName(p[0].toInt())} ${p61Digits(p[1].toInt())})"
                        },
                        fontSize = 12.sp, color = P61Ink.copy(alpha = 0.7f), lineHeight = 18.sp,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onPlay, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("▶", color = P61Green) }
                OutlinedButton(onClick = onOpen, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1.5f)) { Text("📖 المصحف", color = P61Green, fontSize = 12.sp, maxLines = 1) }
                OutlinedButton(onClick = onReview, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1.5f)) {
                    Text(if (inReview) "🔁 أراجع ✓" else "🔁 أراجع", color = P61Gold, fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(onClick = onLearned, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1.5f)) {
                    Text(if (isLearned) "✓ حفظتها" else "حفظتها", color = P61Green, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun p67MakeQuiz(repo: QuranRepository, pool: List<Pair<P67Item, P67Word>>, all: List<P67Item>): List<P67Question> {
    val extras = all.flatMap { it.words.flatMap { w -> w.variants.map { v -> v.first } } }.distinct()
    return pool.shuffled().take(10).mapNotNull { (item, w) ->
        val aya = repo.ayah(item.surah, item.ayah) ?: return@mapNotNull null
        val tokens = aya.text.split(' ').filter { it.isNotEmpty() }
        val correct = tokens.getOrNull(w.index) ?: return@mapNotNull null
        val wrong = LinkedHashSet<String>()
        w.variants.map { it.first }.shuffled().forEach { if (it != correct && wrong.size < 3) wrong.add(it) }
        var guard = 0
        while (wrong.size < 3 && extras.isNotEmpty() && guard++ < 60) {
            val c = extras.random()
            if (c != correct) wrong.add(c)
        }
        if (wrong.isEmpty()) return@mapNotNull null
        val blank = tokens.mapIndexed { i, t -> if (i == w.index) "＿＿＿＿" else t }.joinToString(" ")
        P67Question(item, w, blank, (wrong + correct).shuffled(), correct)
    }
}

@Composable
private fun P67Quiz(questions: List<P67Question>, onExit: () -> Unit, onLearned: (String) -> Unit, onReview: (String) -> Unit) {
    var i by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var score by remember { mutableStateOf(0) }
    if (i >= questions.size) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("النتيجة: ${p61Digits(score)} من ${p61Digits(questions.size)}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = P61Green)
            Text("الآيات التي أخطأت فيها أُضيفت إلى «أراجعها».", color = P61Ink.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
            Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = P61Green), shape = RoundedCornerShape(14.dp)) { Text("رجوع للقائمة") }
        }
        return
    }
    val q = questions[i]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("سؤال ${p61Digits(i + 1)} من ${p61Digits(questions.size)}", color = P61Ink.copy(alpha = 0.6f), fontSize = 13.sp)
        Text("أكمل الكلمة الناقصة (سورة ${q.item.surah} : ${p61Digits(q.item.ayah)})", color = P61Green, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Text(q.text, fontFamily = P61Amiri, fontSize = 23.sp, lineHeight = 42.sp, color = P61Ink, textAlign = TextAlign.Right, modifier = Modifier.padding(14.dp).fillMaxWidth())
        }
        q.options.forEach { o ->
            val done = picked != null
            val color = when {
                !done -> Color.White
                o == q.correct -> Color(0xFFD7EBDD)
                o == picked -> Color(0xFFF6D4D1)
                else -> Color.White
            }
            Surface(
                onClick = {
                    if (picked == null) {
                        picked = o
                        if (o == q.correct) score++ else onReview("${q.item.surah}:${q.item.ayah}")
                    }
                },
                shape = RoundedCornerShape(14.dp), color = color, border = BorderStroke(1.dp, Color(0xFFE6DCC2)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(o, fontFamily = P61Amiri, fontSize = 22.sp, color = P61Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp).fillMaxWidth())
            }
        }
        val p = picked
        if (p != null) {
            Text(if (p == q.correct) "أحسنت ✓" else "الصحيح: ${q.correct}", color = if (p == q.correct) P61Green else P61Wrong, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    if (p == q.correct) onLearned("${q.item.surah}:${q.item.ayah}")
                    picked = null
                    i++
                },
                colors = ButtonDefaults.buttonColors(containerColor = P61Green), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            ) { Text(if (i == questions.lastIndex) "النتيجة" else "التالي", fontWeight = FontWeight.Bold) }
        }
        TextButton(onClick = onExit) { Text("إنهاء الاختبار", color = P61Ink.copy(alpha = 0.6f)) }
    }
}
