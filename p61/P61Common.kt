package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ab.khatma.R
import com.ab.khatma.quran.QuranAyah
import com.ab.khatma.quran.QuranRepository
import org.json.JSONObject

/** Amiri Quran (SIL Open Font License 1.1) — the font used by the continuous reader, the hifz and the tasmee screens. */
internal val P61Amiri = FontFamily(Font(R.font.amiri_quran))

internal val P61Paper = Color(0xFFFBF7EA)
internal val P61Ink = Color(0xFF1E1A12)
internal val P61Gold = Color(0xFF9A772C)
internal val P61Green = Color(0xFF064D3B)
internal val P61Tan = Color(0xFFE6D3A3)
internal val P61Wrong = Color(0xFFB3261E)

/** Reading style switch: the continuous reader is the default, the printed pages stay available. */
internal object P61Flow {
    private const val PREFS = "p61_prefs"
    var enabled by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("flow_reader", true)
    }

    fun set(context: Context, on: Boolean) {
        loaded = true
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("flow_reader", on).apply()
    }
}

internal fun p61Digits(n: Int): String =
    n.toString().map { '٠' + (it - '0') }.joinToString("")

/** Word helpers. Nothing here changes the Quran text; it only compares words on the phone. */
internal object P61Words {
    private val marks = Regex("[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿـ]")
    private const val BASMALA_END = "ٱلرَّحِيمِ"

    /** Letters only: no diacritics, one shape for every alef, ya and ta marbuta. */
    fun norm(word: String): String {
        var s = marks.replace(word, "")
        s = s.replace('ٱ', 'ا').replace('آ', 'ا')
            .replace('أ', 'ا').replace('إ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه')
            .replace('ؤ', 'و').replace('ئ', 'ي')
        return s.replace("ء", "")
    }

    /** (basmala shown on its own, the verse itself). Ayah 1 of each surah except al-Fatiha and at-Tawba starts with it. */
    fun split(aya: QuranAyah): Pair<String?, String> {
        if (aya.ayah != 1 || aya.surah == 9 || aya.surah == 1) return null to aya.text
        val at = aya.text.indexOf(BASMALA_END)
        if (at < 0 || at > 46 || !aya.text.startsWith("ب")) return null to aya.text
        val end = at + BASMALA_END.length
        return aya.text.substring(0, end) to aya.text.substring(end).trimStart()
    }

    fun body(aya: QuranAyah): String = split(aya).second

    fun words(text: String): List<String> = text.split(' ', '\n').filter { it.isNotBlank() }

    /** For two word lists: which words of each side are not part of the longest common run of equal words. */
    fun diff(a: List<String>, b: List<String>): Pair<BooleanArray, BooleanArray> {
        val an = a.map(::norm)
        val bn = b.map(::norm)
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) for (j in b.size - 1 downTo 0) {
            dp[i][j] = if (an[i] == bn[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val da = BooleanArray(a.size) { true }
        val db = BooleanArray(b.size) { true }
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            if (an[i] == bn[j]) { da[i] = false; db[j] = false; i++; j++ }
            else if (dp[i + 1][j] >= dp[i][j + 1]) i++ else j++
        }
        return da to db
    }
}

/** Similar ayat: ayat that repeat a run of the same words. Built from the bundled Tanzil text by comparing words
 * (assets/quran/p61_similar.json); it is not a complete list from the books of the scholars. */
internal object P61Similar {
    private var map: Map<Int, IntArray>? = null

    @Synchronized
    private fun load(context: Context): Map<Int, IntArray> {
        map?.let { return it }
        val loaded = runCatching {
            val raw = context.applicationContext.assets.open("quran/p61_similar.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(raw)
            val m = HashMap<Int, IntArray>(o.length() * 2)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = o.getJSONArray(k)
                m[k.toInt()] = IntArray(arr.length()) { i -> arr.getInt(i) }
            }
            m as Map<Int, IntArray>
        }.getOrDefault(emptyMap())
        map = loaded
        return loaded
    }

    fun similar(context: Context, surah: Int, ayah: Int): List<Pair<Int, Int>> =
        load(context)[surah * 1000 + ayah]?.map { it / 1000 to it % 1000 }.orEmpty()

    fun has(context: Context, surah: Int, ayah: Int): Boolean = load(context).containsKey(surah * 1000 + ayah)
}

private fun p61Colored(words: List<String>, differs: BooleanArray, ink: Color): AnnotatedString = buildAnnotatedString {
    words.forEachIndexed { i, w ->
        if (differs.getOrElse(i) { false }) {
            pushStyle(SpanStyle(color = P61Wrong, fontWeight = FontWeight.Bold, background = Color(0x22B3261E)))
            append(w)
            pop()
        } else {
            pushStyle(SpanStyle(color = ink))
            append(w)
            pop()
        }
        if (i < words.lastIndex) append(' ')
    }
}

/** «🔁 آيات متشابهة»: the ayat that resemble this one, with the different words marked. */
@Composable
internal fun P61SimilarDialog(
    repo: QuranRepository,
    surah: Int,
    ayah: Int,
    onDismiss: () -> Unit,
    onOpen: ((Int, Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val list = remember(surah, ayah) { P61Similar.similar(context, surah, ayah) }
    val me = remember(surah, ayah) { repo.ayah(surah, ayah) }
    val myWords = remember(me) { me?.let { P61Words.words(P61Words.body(it)) }.orEmpty() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 640.dp),
                shape = RoundedCornerShape(20.dp),
                color = P61Paper,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🔁 آيات متشابهة", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = P61Green)
                    Text(
                        "سورة ${repo.surahName(surah)} — الآية $ayah",
                        fontSize = 13.sp, color = P61Ink.copy(alpha = 0.65f), modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        if (me != null) {
                            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF1E8CC)) {
                                Text(
                                    P61Words.body(me),
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    fontFamily = P61Amiri, fontSize = 22.sp, lineHeight = 42.sp,
                                    textAlign = TextAlign.Center, color = P61Ink,
                                )
                            }
                        }
                        if (list.isEmpty()) {
                            Text(
                                "لم أجد آية تتكرر فيها ألفاظ هذه الآية بنفس الترتيب.",
                                modifier = Modifier.padding(vertical = 14.dp), color = P61Ink.copy(alpha = 0.7f),
                            )
                        }
                        list.forEach { (s, a) ->
                            val other = repo.ayah(s, a) ?: return@forEach
                            val otherWords = P61Words.words(P61Words.body(other))
                            val diff = P61Words.diff(myWords, otherWords).second
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp), color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE6DCC2)),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "سورة ${repo.surahName(s)} — الآية $a",
                                            fontWeight = FontWeight.Bold, color = P61Gold, fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (onOpen != null) TextButton(onClick = { onOpen(s, a) }) { Text("افتح") }
                                    }
                                    Text(
                                        p61Colored(otherWords, diff, P61Ink),
                                        modifier = Modifier.fillMaxWidth(),
                                        fontFamily = P61Amiri, fontSize = 22.sp, lineHeight = 42.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        Text(
                            "الكلمات الحمراء هي الفرق بين الآيتين. المتشابهات هنا مستخرجة تلقائيًا بمقارنة ألفاظ الآيات في نص مصحف تنزيل، وليست حصرًا كاملًا لكل ما ذكره العلماء.",
                            modifier = Modifier.padding(top = 12.dp), fontSize = 11.sp,
                            color = P61Ink.copy(alpha = 0.55f), lineHeight = 17.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                    ) { Text("إغلاق") }
                }
            }
        }
    }
}
