package com.ab.khatma.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ab.khatma.quran.QuranAyah
import com.ab.khatma.quran.QuranRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ============================================================================================ compare ====

internal enum class P61WordState { OK, CLOSE, WRONG, MISSING }

internal class P61Compare(
    val words: List<String>,                 // the Quran words, as written in the Mushaf
    val state: List<P61WordState>,
    val said: List<String?>,                 // what was said in place of a wrong word
    val extras: List<String>,                // words said that are not in the Quran text of the chosen range
) {
    val good: Int get() = state.count { it == P61WordState.OK || it == P61WordState.CLOSE }
}

private fun p61Levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val t = prev; prev = cur; cur = t
    }
    return prev[b.length]
}

/** 1.0 = same letters; 0.7+ = the speech engine probably heard the same word with a small slip. */
private fun p61Similarity(a: String, b: String): Float {
    if (a == b) return 1f
    val longest = maxOf(a.length, b.length)
    if (longest <= 3) return 0f
    return 1f - p61Levenshtein(a, b).toFloat() / longest
}

private val P61BasmalaWords = listOf("بسم", "الله", "الرحمن", "الرحيم")

/**
 * Lines up what was said with the Quran words of the chosen range (edit-distance alignment) and
 * marks every Quran word: correct, almost correct, wrong or missing.
 */
internal fun p61Compare(expectedDisplay: List<String>, spokenText: String): P61Compare {
    val e = expectedDisplay.map(P61Words::norm)
    var s = P61Words.words(spokenText.replace('؟', ' ').replace(',', ' ')).map(P61Words::norm).filter { it.isNotEmpty() }
    // Reciting the basmala first is normal; it is not counted as a mistake.
    if (s.size >= 4 && s.take(4) == P61BasmalaWords && !(e.size >= 4 && e.take(4) == P61BasmalaWords)) s = s.drop(4)
    val n = e.size
    val m = s.size
    val dp = Array(n + 1) { FloatArray(m + 1) }
    for (i in 1..n) dp[i][0] = i.toFloat()
    for (j in 1..m) dp[0][j] = j.toFloat()
    for (i in 1..n) for (j in 1..m) {
        val sim = p61Similarity(e[i - 1], s[j - 1])
        val sub = dp[i - 1][j - 1] + when {
            sim >= 1f -> 0f
            sim >= 0.7f -> 0.25f
            else -> 1f
        }
        dp[i][j] = minOf(sub, dp[i - 1][j] + 1f, dp[i][j - 1] + 1f)
    }
    val state = Array(n) { P61WordState.MISSING }
    val said = arrayOfNulls<String>(n)
    val extras = ArrayList<String>()
    var i = n
    var j = m
    while (i > 0 || j > 0) {
        if (i > 0 && j > 0) {
            val sim = p61Similarity(e[i - 1], s[j - 1])
            val sub = dp[i - 1][j - 1] + when {
                sim >= 1f -> 0f
                sim >= 0.7f -> 0.25f
                else -> 1f
            }
            if (kotlin.math.abs(dp[i][j] - sub) < 1e-4f) {
                when {
                    sim >= 1f -> state[i - 1] = P61WordState.OK
                    sim >= 0.7f -> state[i - 1] = P61WordState.CLOSE
                    else -> { state[i - 1] = P61WordState.WRONG; said[i - 1] = s[j - 1] }
                }
                i--; j--
                continue
            }
        }
        if (i > 0 && kotlin.math.abs(dp[i][j] - (dp[i - 1][j] + 1f)) < 1e-4f) {
            state[i - 1] = P61WordState.MISSING
            i--
        } else {
            extras.add(0, s[j - 1])
            j--
        }
    }
    return P61Compare(expectedDisplay, state.toList(), said.toList(), extras)
}

// ============================================================================================ history ====

private object P61TasmeeLog {
    private const val PREFS = "p61_tasmee"

    fun add(context: Context, label: String, percent: Int) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(p.getString("log", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject().put("t", System.currentTimeMillis()).put("l", label).put("p", percent))
        for (i in 0 until minOf(arr.length(), 19)) next.put(arr.getJSONObject(i))
        p.edit().putString("log", next.toString()).apply()
    }

    fun recent(context: Context): List<Triple<Long, String, Int>> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "[]"))
        (0 until minOf(arr.length(), 5)).map { i ->
            val o = arr.getJSONObject(i)
            Triple(o.getLong("t"), o.getString("l"), o.getInt("p"))
        }
    }.getOrDefault(emptyList())
}

// =============================================================================================== screen ====

private enum class P61Stage { SETUP, RECORD, CHECK }
private enum class P61Scope { AYAT, PAGES, JUZ }

/**
 * «🎙 تسميع»: choose what to recite, recite from memory while the phone writes what it hears, edit the text if the
 * phone misheard, then every word of the Mushaf is marked ✓ / ✗ / missing.
 */
@Composable
internal fun P61TasmeeScreen(
    repo: QuranRepository,
    initialSurah: Int = 1,
    initialFrom: Int = 1,
    initialTo: Int? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onClose() }

    val surahs = remember { repo.surahs() }
    var stage by remember { mutableStateOf(P61Stage.SETUP) }
    var scopeMode by remember { mutableStateOf(P61Scope.AYAT) }
    var surah by remember { mutableIntStateOf(initialSurah.coerceIn(1, 114)) }
    val count = surahs.firstOrNull { it.index == surah }?.ayahs ?: 1
    var from by remember { mutableIntStateOf(initialFrom.coerceIn(1, count)) }
    var to by remember { mutableIntStateOf((initialTo ?: count.coerceAtMost(7)).coerceIn(1, count)) }
    var pageFrom by remember { mutableIntStateOf(repo.firstPageOfSurah(initialSurah.coerceIn(1, 114))) }
    var pageTo by remember { mutableIntStateOf(pageFrom) }
    var juz by remember { mutableIntStateOf(30) }
    var menu by remember { mutableStateOf(false) }

    val verses: List<QuranAyah> = remember(scopeMode, surah, from, to, pageFrom, pageTo, juz) {
        when (scopeMode) {
            P61Scope.AYAT -> repo.loadSurah(surah).filter { it.ayah in from..to }
            P61Scope.PAGES -> (pageFrom..maxOf(pageFrom, pageTo)).flatMap { repo.loadPage(it) }
            P61Scope.JUZ -> repo.loadJuz(juz)
        }
    }
    val expected: List<String> = remember(verses) { verses.flatMap { P61Words.words(P61Words.body(it)) } }
    val label = remember(verses, scopeMode) {
        val a = verses.firstOrNull()
        val b = verses.lastOrNull()
        if (a == null || b == null) "" else if (a.surah == b.surah)
            "سورة ${repo.surahName(a.surah)} من الآية ${a.ayah} إلى ${b.ayah}"
        else "من سورة ${repo.surahName(a.surah)} الآية ${a.ayah} إلى سورة ${repo.surahName(b.surah)} الآية ${b.ayah}"
    }
    val pagesLabel = remember(verses) {
        val ps = verses.map { it.page }
        if (ps.isEmpty()) "" else if (ps.min() == ps.max()) "صفحة ${ps.min()}" else "من صفحة ${ps.min()} إلى ${ps.max()}"
    }

    // ---- recording -----------------------------------------------------------------------------------
    var wantListening by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableIntStateOf(0) }
    var recMessage by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    val overrides = remember { mutableStateListOf<Int>() }

    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    fun listenAgain() {
        scope.launch {
            delay(250)
            if (wantListening) runCatching { recognizer?.startListening(intent) }
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = rmsdB }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) committed = (committed + " " + t).trim()
                partial = ""
                failures = 0
                if (wantListening) listenAgain()
            }
            override fun onError(error: Int) {
                partial = ""
                if (!wantListening) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listenAgain()
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        wantListening = false
                        recMessage = "لازم تسمح للتطبيق باستخدام الميكروفون."
                    }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                        wantListening = false
                        recMessage = "التعرف على الصوت يحتاج إنترنت. اتصل بالشبكة وحاول تاني، أو اكتب ما حفظته بالكتابة."
                    }
                    else -> {
                        failures++
                        if (failures >= 4) {
                            wantListening = false
                            recMessage = "خدمة التعرف على الصوت لا تعمل الآن على هذا الموبايل. اكتب ما حفظته بالكتابة وسنقارنه."
                        } else listenAgain()
                    }
                }
            }
        })
        onDispose {
            wantListening = false
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
        }
    }

    fun startRecording() {
        if (recognizer == null) {
            recMessage = "خدمة التعرف على الصوت غير موجودة على هذا الموبايل. اكتب ما حفظته بالكتابة وسنقارنه."
            return
        }
        recMessage = null
        failures = 0
        wantListening = true
        runCatching { recognizer.startListening(intent) }
    }

    fun stopRecording() {
        wantListening = false
        runCatching { recognizer?.stopListening() }
        if (partial.isNotBlank()) { committed = (committed + " " + partial).trim(); partial = "" }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else recMessage = "بدون إذن الميكروفون اكتب ما حفظته بالكتابة وسنقارنه."
    }

    fun begin() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startRecording()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(wantListening) {
        while (wantListening) { delay(1000); seconds++ }
    }

    var transcript by remember { mutableStateOf("") }
    val result: P61Compare? = remember(stage, transcript, expected) {
        if (stage == P61Stage.CHECK) p61Compare(expected, transcript) else null
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(P61Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("🎙 التسميع", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = P61Green)

                when (stage) {
                    P61Stage.SETUP -> {
                        Text(
                            "اختر المقطع، وسمّع من حفظك. التطبيق يكتب ما يسمعه، وبعدها يراجع كل كلمة: صح أو خطأ.",
                            fontSize = 13.sp, color = P61Ink.copy(alpha = 0.65f), lineHeight = 21.sp,
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(P61Scope.AYAT to "آيات من سورة", P61Scope.PAGES to "صفحات", P61Scope.JUZ to "جزء كامل").forEach { (m, name) ->
                                FilterChip(selected = scopeMode == m, onClick = { scopeMode = m }, label = { Text(name) })
                            }
                        }
                        when (scopeMode) {
                            P61Scope.AYAT -> {
                                Box {
                                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                        Text("السورة: ${repo.surahName(surah)} ▾")
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        surahs.forEach { s ->
                                            DropdownMenuItem(text = { Text("${s.index}. ${s.name}") }, onClick = {
                                                menu = false
                                                surah = s.index
                                                from = 1
                                                to = s.ayahs.coerceAtMost(7)
                                            })
                                        }
                                    }
                                }
                                P61Stepper("من الآية", from, 1, to) { from = it }
                                P61Stepper("إلى الآية", to, from, count) { to = it }
                            }
                            P61Scope.PAGES -> {
                                P61Stepper("من صفحة", pageFrom, 1, pageTo) { pageFrom = it }
                                P61Stepper("إلى صفحة", pageTo, pageFrom, 604) { pageTo = it }
                            }
                            P61Scope.JUZ -> P61Stepper("الجزء", juz, 1, 30) { juz = it }
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF1E8CC)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(label, fontWeight = FontWeight.Bold, color = P61Ink, fontSize = 14.sp)
                                Text(
                                    "$pagesLabel • ${verses.size} آية • ${expected.size} كلمة",
                                    fontSize = 12.sp, color = P61Ink.copy(alpha = 0.65f),
                                )
                            }
                        }
                        Button(
                            onClick = {
                                committed = ""; partial = ""; typed = ""; seconds = 0; recMessage = null; overrides.clear()
                                stage = P61Stage.RECORD
                            },
                            enabled = expected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                        ) { Text("🎙 ابدأ التسميع", fontWeight = FontWeight.Bold, fontSize = 17.sp) }

                        val recent = remember { P61TasmeeLog.recent(context) }
                        if (recent.isNotEmpty()) {
                            Text("آخر تسميعاتك", fontWeight = FontWeight.Bold, color = P61Green)
                            recent.forEach { (t, l, p) ->
                                val day = remember(t) { java.text.SimpleDateFormat("d/M", Locale.US).format(java.util.Date(t)) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(l, Modifier.weight(1f), fontSize = 12.sp, color = P61Ink.copy(alpha = 0.8f), maxLines = 2)
                                    Text("$day  •  ${p61Digits(p)}٪", fontWeight = FontWeight.Bold, color = if (p >= 90) P61Green else P61Gold)
                                }
                            }
                        }
                    }

                    P61Stage.RECORD -> {
                        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF1E8CC)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(label, fontWeight = FontWeight.Bold, color = P61Ink, fontSize = 14.sp)
                                Text(pagesLabel, fontSize = 12.sp, color = P61Ink.copy(alpha = 0.65f))
                            }
                        }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            val pulse = if (wantListening) 1f + (level.coerceIn(0f, 10f) / 10f) * 0.22f else 1f
                            Box(
                                Modifier.size(104.dp).scale(pulse).clip(CircleShape)
                                    .background(if (wantListening) Color(0xFFC62828) else P61Green)
                                    .clickable { if (wantListening) stopRecording() else begin() },
                                contentAlignment = Alignment.Center,
                            ) { Text(if (wantListening) "⏹" else "🎙", fontSize = 40.sp, color = Color.White) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (wantListening) "جارٍ الاستماع… %02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
                                else "اضغط الميكروفون وابدأ التسميع من حفظك",
                                color = P61Ink.copy(alpha = 0.75f), fontSize = 14.sp,
                            )
                        }
                        recMessage?.let { Text(it, color = P61Wrong, fontSize = 13.sp, lineHeight = 20.sp) }
                        Text("ما سمعه التطبيق:", fontWeight = FontWeight.Bold, color = P61Green, fontSize = 14.sp)
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE6DCC2))) {
                            Text(
                                (committed + " " + partial).trim().ifEmpty { "…" },
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                fontFamily = P61Amiri, fontSize = 22.sp, lineHeight = 40.sp, color = P61Ink,
                            )
                        }
                        Text("أو اكتب ما حفظته بنفسك:", fontSize = 12.sp, color = P61Ink.copy(alpha = 0.6f))
                        OutlinedTextField(
                            value = typed, onValueChange = { typed = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                            placeholder = { Text("اكتب أو استخدم الميكروفون في لوحة المفاتيح") },
                        )
                        Button(
                            onClick = {
                                stopRecording()
                                transcript = (committed + " " + typed).trim()
                                stage = P61Stage.CHECK
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                        ) { Text("✓ انتهيت، راجع تسميعي", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { stopRecording(); stage = P61Stage.SETUP }, modifier = Modifier.fillMaxWidth()) {
                            Text("تغيير المقطع")
                        }
                    }

                    P61Stage.CHECK -> {
                        val r = result
                        if (r != null) {
                            val extraOk = overrides.count { it in r.state.indices && r.state[it] !in listOf(P61WordState.OK, P61WordState.CLOSE) }
                            val good = r.good + extraOk
                            val percent = if (r.words.isEmpty()) 0 else (good * 100f / r.words.size).toInt()
                            Surface(shape = RoundedCornerShape(16.dp), color = if (percent >= 90) Color(0xFFE3F1E7) else Color(0xFFF7ECCB)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${p61Digits(percent)}٪", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                                        color = if (percent >= 90) P61Green else P61Gold)
                                    Text("$good صح من ${r.words.size} كلمة", fontSize = 14.sp, color = P61Ink)
                                    Text(label, fontSize = 12.sp, color = P61Ink.copy(alpha = 0.65f))
                                }
                            }
                            Text(
                                "الأخضر صح، الأحمر خطأ، البرتقالي ناقص. اضغط على أي كلمة لو التطبيق ظلمك فيها.",
                                fontSize = 12.sp, color = P61Ink.copy(alpha = 0.65f), lineHeight = 19.sp,
                            )
                            P61ResultText(r, overrides) { i ->
                                if (i in overrides) overrides.remove(i) else overrides.add(i)
                            }
                            val mistakes = r.state.indices.filter { r.state[it] == P61WordState.WRONG && it !in overrides }
                            val missing = r.state.indices.filter { r.state[it] == P61WordState.MISSING && it !in overrides }
                            if (mistakes.isNotEmpty() || missing.isNotEmpty() || r.extras.isNotEmpty()) {
                                Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE6DCC2))) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("المراجعة", fontWeight = FontWeight.Bold, color = P61Green)
                                        mistakes.take(12).forEach { i ->
                                            Text("✗ قلت «${r.said[i]}» والصواب «${r.words[i]}»", fontSize = 14.sp, color = P61Wrong)
                                        }
                                        missing.take(12).forEach { i ->
                                            Text("… نسيت «${r.words[i]}»", fontSize = 14.sp, color = Color(0xFFB26A00))
                                        }
                                        if (r.extras.isNotEmpty()) {
                                            Text("زيادة لم ترد في المقطع: " + r.extras.take(8).joinToString(" "), fontSize = 13.sp,
                                                color = P61Ink.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                            Text("ما سمعه التطبيق (عدّله لو أخطأ في السمع):", fontSize = 12.sp, color = P61Ink.copy(alpha = 0.6f))
                            OutlinedTextField(
                                value = transcript, onValueChange = { transcript = it; overrides.clear() },
                                modifier = Modifier.fillMaxWidth(), minLines = 3,
                            )
                            Button(
                                onClick = {
                                    P61TasmeeLog.add(context, label, percent)
                                    onClose()
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                            ) { Text("حفظ النتيجة والخروج", fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = {
                                    committed = ""; partial = ""; typed = ""; seconds = 0; recMessage = null; overrides.clear()
                                    stage = P61Stage.RECORD
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            ) { Text("🔁 أعد التسميع") }
                        }
                    }
                }
            }
            P53BottomNav(onBack = onClose, onHome = onClose)
        }
    }
}

@Composable
private fun P61ResultText(r: P61Compare, overrides: List<Int>, onToggle: (Int) -> Unit) {
    val okColor = Color(0xFF1B7A43)
    val annotated: AnnotatedString = remember(r, overrides.toList()) {
        buildAnnotatedString {
            r.words.forEachIndexed { i, w ->
                val st = if (i in overrides) P61WordState.OK else r.state[i]
                val start = length
                pushStyle(
                    when (st) {
                        P61WordState.OK -> SpanStyle(color = okColor)
                        P61WordState.CLOSE -> SpanStyle(color = okColor, textDecoration = TextDecoration.Underline)
                        P61WordState.WRONG -> SpanStyle(color = P61Wrong, fontWeight = FontWeight.Bold, background = Color(0x22B3261E))
                        P61WordState.MISSING -> SpanStyle(color = Color(0xFFB26A00), background = Color(0x33E8A33D))
                    },
                )
                append(w)
                pop()
                addStringAnnotation("W", i.toString(), start, length)
                if (i < r.words.lastIndex) append(' ')
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE6DCC2))) {
        Text(
            annotated,
            modifier = Modifier.fillMaxWidth().padding(14.dp).pointerInput(annotated) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val off = l.getOffsetForPosition(pos)
                    annotated.getStringAnnotations("W", off, off).firstOrNull()?.item?.toIntOrNull()?.let(onToggle)
                }
            },
            onTextLayout = { layout = it },
            fontFamily = P61Amiri, fontSize = 24.sp, lineHeight = 46.sp, textAlign = TextAlign.Justify,
        )
    }
}

@Composable
internal fun P61Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = P61Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { if (value > min) onChange(value - 1) }, enabled = value > min,
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp),
        ) { Text("−", fontSize = 20.sp) }
        Text(
            "$value", color = P61Green, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(56.dp),
        )
        OutlinedButton(
            onClick = { if (value < max) onChange(value + 1) }, enabled = value < max,
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp),
        ) { Text("+", fontSize = 20.sp) }
    }
}
