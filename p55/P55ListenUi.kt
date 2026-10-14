package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ab.khatma.quran.P52Reciter
import com.ab.khatma.quran.P52Reciters
import com.ab.khatma.quran.P52SurahAudio
import org.json.JSONArray
import org.json.JSONObject

private val G = Color(0xFF064D3B)
private val Gold = Color(0xFFB48B38)
private val Line = Color(0xFFE9DDC6)
private val Cream = Color(0xFFFCF8EF)
private val Ink = Color(0xFF4A3B2A)
private val Muted = Color(0xFF746D62)

/** Arabic search that ignores tashkeel, hamza forms, taa marbuta and alef maqsura. */
internal fun p55Normalize(s: String): String = s
    .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
    .replace('ة', 'ه').replace('ى', 'ي').replace("سورة ", "").replace("سوره ", "")
    .trim().lowercase()

// ------------------------------------------------------------------ reciters ----

@Composable
internal fun P55ReciterPicker(onDismiss: () -> Unit, onPick: (P52Reciter) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    var query by remember { mutableStateOf("") }
    val q = p55Normalize(query)
    val matches = P52Reciters.all.filter {
        q.isBlank() || p55Normalize(it.name).contains(q) || p55Normalize(it.style).contains(q)
    }
    val favs = matches.filter { it.id in P52SurahAudio.favorites }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f), shape = RoundedCornerShape(20.dp), color = Cream) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("اختيار القارئ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF263F35),
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "إغلاق", tint = G) }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(40) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        placeholder = { Text("ابحث باسم القارئ") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Text("▶ عيّنة صوت   ★ مفضّل   «آية بآية» = متاح للاستماع للآية وحدها",
                        fontSize = 11.sp, color = Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    HorizontalDivider(color = Line)
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (favs.isNotEmpty()) {
                            item(key = "h_fav") { P55Header("⭐ المفضّلين") }
                            items(favs, key = { "f_" + it.id }) { r -> P55ReciterRow(context, r, onPick) }
                        }
                        P52Reciters.sections.forEach { section ->
                            val list = matches.filter { it.section == section }
                            if (list.isNotEmpty()) {
                                item(key = "h_$section") { P55Header(section) }
                                items(list, key = { it.id }) { r -> P55ReciterRow(context, r, onPick) }
                            }
                        }
                        if (matches.isEmpty()) item { Text("لا يوجد قارئ بهذا الاسم", Modifier.padding(20.dp), color = Muted) }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun P55Header(title: String) {
    Text(title, Modifier.fillMaxWidth().background(Color(0xFFF3EDE0)).padding(horizontal = 16.dp, vertical = 7.dp),
        color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun P55ReciterRow(context: Context, r: P52Reciter, onPick: (P52Reciter) -> Unit) {
    val chosen = r.id == P52SurahAudio.reciterId
    val sampling = P52SurahAudio.sampleReciterId == r.id && P52SurahAudio.isActive
    Row(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.name, fontSize = 16.sp, color = Ink, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.style.isNotBlank()) Text(r.style, fontSize = 12.sp, color = Color(0xFF9A7A4E))
                if (r.ayahFolder != null) Text("آية بآية", fontSize = 10.sp, color = G,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5EFE9)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
        if (chosen) Icon(Icons.Rounded.CheckCircle, "مختار", tint = G, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = {
            if (sampling) P52SurahAudio.stop() else P52SurahAudio.playSample(context, r.id)
        }) {
            Icon(if (sampling) Icons.Rounded.Close else Icons.Rounded.PlayArrow,
                if (sampling) "إيقاف العيّنة" else "سماع عيّنة", tint = G)
        }
        IconButton(onClick = { P52SurahAudio.toggleFavorite(context, r.id) }) {
            Icon(if (r.id in P52SurahAudio.favorites) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                "مفضّل", tint = Gold)
        }
    }
}

/** Row inside the verse-listening dialog: who will recite the single verse. */
@Composable
internal fun P55VerseReciterRow() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    var open by remember { mutableStateOf(false) }
    if (open) P55ReciterPicker(onDismiss = { open = false }) { P52SurahAudio.selectReciter(context, it.id); open = false }
    val r = P52SurahAudio.savedReciter
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.name + (if (r.style.isNotBlank()) " — ${r.style}" else ""), fontWeight = FontWeight.SemiBold)
            if (r.ayahFolder == null) Text("هذا القارئ متاح للسورة كاملة فقط؛ الآيات المنفردة بصوت الحصري.",
                fontSize = 12.sp, color = Muted)
        }
        TextButton(onClick = { open = true }) { Text("تغيير") }
    }
}

// ------------------------------------------------------------------ surah picker ----

data class P55SurahInfo(val number: Int, val name: String, val ayahs: Int, val pages: Int)

private data class P55Preset(val title: String, val surahs: List<Int>)

private val P55Presets = listOf(
    P55Preset("الكهف (الجمعة)", listOf(18)),
    P55Preset("قبل النوم", listOf(67, 32)),
    P55Preset("المعوذات", listOf(112, 113, 114)),
    P55Preset("يس", listOf(36)),
    P55Preset("الرحمن والواقعة والملك", listOf(55, 56, 67)),
    P55Preset("جزء تبارك", (67..77).toList()),
    P55Preset("جزء عمّ", (78..114).toList()),
    P55Preset("البقرة", listOf(2)),
)

private object P55SavedLists {
    private const val PREFS = "p55_listen_lists"
    fun load(ctx: Context): List<Pair<String, List<Int>>> = runCatching {
        val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lists", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val s = o.getJSONArray("s")
            o.getString("n") to (0 until s.length()).map { s.getInt(it) }
        }
    }.getOrDefault(emptyList())
    fun save(ctx: Context, lists: List<Pair<String, List<Int>>>) {
        val arr = JSONArray()
        lists.take(20).forEach { (n, s) -> arr.put(JSONObject().put("n", n).put("s", JSONArray(s))) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lists", arr.toString()).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun P55SurahPicker(
    surahs: List<P55SurahInfo>,
    onDismiss: () -> Unit,
    onPlay: (List<Int>) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<List<Int>>(emptyList()) }
    var mushafOrder by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(1) }
    var saved by remember { mutableStateOf(P55SavedLists.load(context)) }
    var naming by remember { mutableStateOf(false) }
    var listName by remember { mutableStateOf("") }
    val q = p55Normalize(query)
    val visible = surahs.filter { s ->
        q.isBlank() || p55Normalize(s.name).contains(q) || s.number.toString() == q.trim()
    }
    val byNumber = surahs.associateBy { it.number }
    val ordered = if (mushafOrder) picked.sorted() else picked
    val minutes = (ordered.sumOf { (byNumber[it]?.pages ?: 1) * 1.4 } * repeat).toInt().coerceAtLeast(if (picked.isEmpty()) 0 else 1)

    if (naming) AlertDialog(
        onDismissRequest = { naming = false },
        title = { Text("حفظ القائمة") },
        text = { OutlinedTextField(listName, { listName = it.take(30) }, label = { Text("اسم القائمة (مثلًا: ورد الصبح)") }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = listName.isNotBlank(), onClick = {
                saved = (listOf(listName.trim() to ordered) + saved.filter { it.first != listName.trim() }).take(20)
                P55SavedLists.save(context, saved); naming = false; listName = ""
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { naming = false }) { Text("إلغاء") } },
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = Cream) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(G).padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("اختيار السور للاستماع", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "إغلاق", tint = Color.White) }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(30) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        placeholder = { Text("ابحث باسم السورة أو رقمها") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        saved.forEach { (name, list) ->
                            AssistChip(onClick = { picked = list }, label = { Text("💾 $name") })
                        }
                        P55Presets.forEach { p -> AssistChip(onClick = { picked = p.surahs }, label = { Text(p.title) }) }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(visible, key = { it.number }) { s ->
                            val pos = picked.indexOf(s.number)
                            Row(Modifier.fillMaxWidth().clickable {
                                picked = if (pos >= 0) picked - s.number else picked + s.number
                            }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).clip(CircleShape).background(if (pos >= 0) G else Color(0xFFF1EBDD)),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (pos >= 0) "${pos + 1}" else "${s.number}",
                                        color = if (pos >= 0) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("سورة ${s.name}", color = Ink, fontSize = 16.sp,
                                        fontWeight = if (pos >= 0) FontWeight.Bold else FontWeight.Normal)
                                    Text("${s.number}  •  ${s.ayahs} آية", color = Muted, fontSize = 11.sp)
                                }
                                if (pos >= 0) Icon(Icons.Rounded.CheckCircle, null, tint = G)
                            }
                        }
                        if (visible.isEmpty()) item { Text("لا توجد سورة بهذا الاسم", Modifier.padding(20.dp), color = Muted) }
                    }
                    Surface(color = Color.White, shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (picked.isEmpty()) "لم تختر سورًا بعد" else "تم اختيار ${picked.size} سورة  •  حوالي $minutes دقيقة",
                                    color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (picked.isNotEmpty()) TextButton(onClick = { naming = true }) { Text("حفظ") }
                                if (picked.isNotEmpty()) TextButton(onClick = { picked = emptyList() }) { Text("مسح", color = Color(0xFFB3261E)) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = !mushafOrder, onClick = { mushafOrder = false }, label = { Text("بترتيب اختياري") })
                                FilterChip(selected = mushafOrder, onClick = { mushafOrder = true }, label = { Text("بترتيب المصحف") })
                                Text("تكرار:", color = Muted, fontSize = 12.sp)
                                listOf(1, 2, 3, 5).forEach { n ->
                                    FilterChip(selected = repeat == n, onClick = { repeat = n }, label = { Text(if (n == 1) "مرة" else "×$n") })
                                }
                            }
                            Button(
                                onClick = { onPlay(List(repeat) { ordered }.flatten()) },
                                enabled = picked.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = G),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp))
                                Text("تشغيل" + if (repeat > 1) " (×$repeat)" else "", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
