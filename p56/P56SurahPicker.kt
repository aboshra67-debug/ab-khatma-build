package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
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
import com.ab.khatma.quran.P52SurahAudio
import org.json.JSONArray

private val G = Color(0xFF064D3B)
private val Cream = Color(0xFFFCF8EF)
private val Ink = Color(0xFF4A3B2A)
private val Muted = Color(0xFF746D62)

/** The picker remembers what was chosen, so reopening it never starts from an empty list. */
internal object P56PickerState {
    private const val PREFS = "p56_picker"
    var picked by mutableStateOf<List<Int>>(emptyList())
    var mushafOrder by mutableStateOf(false)
    var repeat by mutableIntStateOf(1)
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        picked = runCatching {
            val a = JSONArray(p.getString("picked", "[]"))
            (0 until a.length()).map { a.getInt(it) }.filter { it in 1..114 }.distinct()
        }.getOrDefault(emptyList())
        mushafOrder = p.getBoolean("mushaf_order", false)
        repeat = p.getInt("repeat", 1).takeIf { it in listOf(1, 2, 3, 5) } ?: 1
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("picked", JSONArray(picked).toString())
            .putBoolean("mushaf_order", mushafOrder)
            .putInt("repeat", repeat)
            .apply()
    }

    /** If something is already playing, show exactly what is playing. */
    fun syncWithPlayer() {
        if (!P52SurahAudio.isActive) return
        val playing = P52SurahAudio.queue.distinct()
        if (playing.isNotEmpty() && playing.toSet() != picked.toSet()) picked = playing
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
    LaunchedEffect(Unit) {
        P52SurahAudio.init(context)
        P56PickerState.load(context)
        P56PickerState.syncWithPlayer()
    }
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(P55SavedLists.load(context)) }
    var naming by remember { mutableStateOf(false) }
    var listName by remember { mutableStateOf("") }
    var changeReciter by remember { mutableStateOf(false) }
    val picked = P56PickerState.picked
    val mushafOrder = P56PickerState.mushafOrder
    val repeat = P56PickerState.repeat
    val q = p55Normalize(query)
    val visible = surahs.filter { s ->
        q.isBlank() || p55Normalize(s.name).contains(q) || s.number.toString() == q.trim()
    }
    val byNumber = surahs.associateBy { it.number }
    val ordered = if (mushafOrder) picked.sorted() else picked
    val minutes = (ordered.sumOf { (byNumber[it]?.pages ?: 1) * 1.4 } * repeat).toInt()
        .coerceAtLeast(if (picked.isEmpty()) 0 else 1)
    val playingNow = if (P52SurahAudio.isActive) P52SurahAudio.currentSurah else null
    val listState = rememberLazyListState()
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(picked.isNotEmpty(), visible.size) {
        if (!scrolled && picked.isNotEmpty() && q.isBlank()) {
            scrolled = true
            val first = visible.indexOfFirst { it.number == (playingNow ?: picked.min()) }
            if (first > 0) listState.scrollToItem((first - 1).coerceAtLeast(0))
        }
    }
    fun setPicked(list: List<Int>) { P56PickerState.picked = list.distinct(); P56PickerState.save(context) }

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
    if (changeReciter) P55ReciterPicker(onDismiss = { changeReciter = false }) {
        P52SurahAudio.selectReciter(context, it.id); changeReciter = false
    }

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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("القارئ: ${P52SurahAudio.savedReciter.name}", color = Ink, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { changeReciter = true }) { Text("تغيير القارئ") }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(30) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        placeholder = { Text("ابحث باسم السورة أو رقمها") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        saved.forEach { (name, list) ->
                            AssistChip(onClick = { setPicked(list) }, label = { Text("💾 $name") })
                        }
                        P55Presets.forEach { p -> AssistChip(onClick = { setPicked(p.surahs) }, label = { Text(p.title) }) }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                        items(visible, key = { it.number }) { s ->
                            val pos = picked.indexOf(s.number)
                            val nowPlaying = playingNow == s.number
                            Row(Modifier.fillMaxWidth()
                                .background(if (nowPlaying) Color(0xFFE5EFE9) else Color.Transparent)
                                .clickable { setPicked(if (pos >= 0) picked - s.number else picked + s.number) }
                                .padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).clip(CircleShape).background(if (pos >= 0) G else Color(0xFFF1EBDD)),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (pos >= 0) "${pos + 1}" else "${s.number}",
                                        color = if (pos >= 0) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("سورة ${s.name}", color = Ink, fontSize = 16.sp,
                                        fontWeight = if (pos >= 0) FontWeight.Bold else FontWeight.Normal)
                                    Text(if (nowPlaying) "▶ تُتلى الآن  •  ${s.ayahs} آية" else "${s.number}  •  ${s.ayahs} آية",
                                        color = if (nowPlaying) G else Muted, fontSize = 11.sp)
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
                                if (picked.isNotEmpty()) TextButton(onClick = { setPicked(emptyList()) }) { Text("مسح", color = Color(0xFFB3261E)) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = !mushafOrder, onClick = { P56PickerState.mushafOrder = false; P56PickerState.save(context) }, label = { Text("بترتيب اختياري") })
                                FilterChip(selected = mushafOrder, onClick = { P56PickerState.mushafOrder = true; P56PickerState.save(context) }, label = { Text("بترتيب المصحف") })
                                Text("تكرار:", color = Muted, fontSize = 12.sp)
                                listOf(1, 2, 3, 5).forEach { n ->
                                    FilterChip(selected = repeat == n, onClick = { P56PickerState.repeat = n; P56PickerState.save(context) },
                                        label = { Text(if (n == 1) "مرة" else "×$n") })
                                }
                            }
                            if (P52SurahAudio.isActive) {
                                Button(
                                    onClick = {
                                        P56PickerState.save(context)
                                        P52SurahAudio.updateQueue(context, List(repeat) { ordered }.flatten())
                                        onDismiss()
                                    },
                                    enabled = picked.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = G),
                                ) { Text("تحديث القائمة وإكمال التلاوة", fontWeight = FontWeight.Bold) }
                            }
                            if (P52SurahAudio.isActive) OutlinedButton(
                                onClick = { P56PickerState.save(context); onPlay(List(repeat) { ordered }.flatten()) },
                                enabled = picked.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(14.dp),
                            ) { Text("تشغيل من البداية") }
                            else Button(
                                onClick = { P56PickerState.save(context); onPlay(List(repeat) { ordered }.flatten()) },
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
