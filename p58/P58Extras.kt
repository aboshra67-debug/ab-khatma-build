package com.ab.khatma.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranRepository

private val PxGreen = Color(0xFF064D3B)
private val PxInk = Color(0xFF18312D)
private val PxGray = Color(0xFF687C74)
private val PxBeige = Color(0xFFF8F7F2)
private val PxGold = Color(0xFFB48B38)

// ------------------------------------------------------------------ hifz mode ----

/** Memorisation helper: pick a range, repeat each verse and the whole range, optionally hide the text. */
@Composable
internal fun P58HifzScreen(repo: QuranRepository, onClose: () -> Unit) {
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
    var menu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    val count = surahs.firstOrNull { it.index == surah }?.ayahs ?: 1
    val verses = remember(surah, from, to) { repo.loadSurah(surah).filter { it.ayah in from..to } }

    fun chooseSurah(n: Int) {
        surah = n
        val c = surahs.firstOrNull { it.index == n }?.ayahs ?: 1
        from = 1
        to = c.coerceAtMost(7)
        revealed.clear()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(PxBeige).windowInsetsPadding(WindowInsets.safeDrawing)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = PxGreen)) {
                        Column(Modifier.fillMaxWidth().padding(19.dp)) {
                            Text("🧠 وضع الحفظ", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(6.dp))
                            Text("اختر المقطع، واسمعه بتكرار كل آية، وجرّب تخفي النص وتسمّع لنفسك.",
                                fontSize = 13.sp, color = Color(0xFFDCEDE9), lineHeight = 21.sp)
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE3E9E3))) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box {
                                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)) {
                                    Text("السورة: ${repo.surahName(surah)} ▾")
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    surahs.forEach { s ->
                                        DropdownMenuItem(text = { Text("${s.index}. ${s.name}") },
                                            onClick = { menu = false; chooseSurah(s.index) })
                                    }
                                }
                            }
                            P58Stepper("من الآية", from, 1, to) { from = it; revealed.clear() }
                            P58Stepper("إلى الآية", to, from, count) { to = it; revealed.clear() }
                            Text("تكرار كل آية:", color = PxGray, fontSize = 12.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 3, 5, 10).forEach { n ->
                                    FilterChip(selected = verseRepeat == n, onClick = { verseRepeat = n },
                                        label = { Text(if (n == 1) "مرة" else "×$n") })
                                }
                            }
                            Text("تكرار المقطع كله:", color = PxGray, fontSize = 12.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 3, 5).forEach { n ->
                                    FilterChip(selected = rangeRepeat == n, onClick = { rangeRepeat = n },
                                        label = { Text(if (n == 1) "مرة" else "×$n") })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("إخفاء النص (اضغط على الآية لإظهارها)", color = PxInk, fontSize = 14.sp,
                                    modifier = Modifier.weight(1f))
                                Switch(checked = hide, onCheckedChange = { hide = it; revealed.clear() })
                            }
                            P55VerseReciterRow()
                            Button(onClick = {
                                val one = (from..to).flatMap { a -> List(verseRepeat) { P52SurahAudio.ayahCode(surah, a) } }
                                val all = List(rangeRepeat) { one }.flatten()
                                if (all.size > 600) {
                                    message = "المقطع مع التكرار طويل (${all.size} مرة). قلّل التكرار أو المقطع."
                                } else {
                                    message = null
                                    all.forEach { P52SurahAudio.customLabels[it] = "حفظ: " + repo.surahName(surah) }
                                    P52SurahAudio.play(context, all)
                                }
                            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PxGreen)) {
                                Text("▶ ابدأ التكرار", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            message?.let { Text(it, color = Color(0xFFB3261E), fontSize = 12.sp) }
                            P52MiniPlayer()
                        }
                    }
                }
                items(verses.size) { i ->
                    val v = verses[i]
                    val shown = !hide || revealed[v.ayah] == true
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE3E9E3)),
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            revealed[v.ayah] = !(revealed[v.ayah] ?: false)
                        }) {
                        Text(if (shown) v.text + " ﴿${v.ayah}﴾" else "﴿${v.ayah}﴾  • • • • •  (اضغط للإظهار)",
                            color = if (shown) PxInk else PxGray, fontSize = 20.sp, lineHeight = 38.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(14.dp))
                    }
                }
            }
            P53BottomNav(onBack = onClose, onHome = onClose)
        }
    }
}

@Composable
private fun P58Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PxInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min,
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp)) {
            Text("−", fontSize = 20.sp)
        }
        Text("$value", color = PxGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp))
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max,
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp)) {
            Text("+", fontSize = 20.sp)
        }
    }
}

// --------------------------------------------------------------------- tasbih ----

@Composable
internal fun P58ToolsRow(onTasbih: () -> Unit, onKhatmDua: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(onClick = onTasbih, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF35216B))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📿", fontSize = 28.sp)
                Text("السبحة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Card(onClick = onKhatmDua, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14517F))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🤲", fontSize = 28.sp)
                Text("دعاء ختم القرآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
internal fun P58TasbihScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    BackHandler { onBack() }
    val prefs = remember { context.getSharedPreferences("p58_tasbih", Context.MODE_PRIVATE) }
    var count by remember { mutableIntStateOf(prefs.getInt("count", 0)) }
    var target by remember { mutableIntStateOf(prefs.getInt("target", 33)) }
    var lifetime by remember { mutableIntStateOf(prefs.getInt("lifetime", 0)) }
    val tap = remember { MutableInteractionSource() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(PxBeige).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📿 السبحة", fontSize = 26.sp, color = PxGreen, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(33, 99, 100, 1000).forEach { n ->
                        FilterChip(selected = target == n, onClick = {
                            target = n; prefs.edit().putInt("target", n).apply()
                        }, label = { Text("$n") })
                    }
                }
                Box(
                    Modifier.size(250.dp).clip(CircleShape).background(if (count >= target) Color(0xFF2E9E5B) else PxGreen)
                        .clickable(interactionSource = tap, indication = null) {
                            count++
                            lifetime++
                            prefs.edit().putInt("count", count).putInt("lifetime", lifetime).apply()
                            haptic.performHapticFeedback(
                                if (count % target == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$count", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                            Text("/ $target", color = Color.White.copy(alpha = 0.8f), fontSize = 22.sp)
                        }
                    }
                }
                Text(if (count >= target) "اكتمل العدد ✓ — تقبّل الله" else "اضغط على الدائرة للتسبيح",
                    color = PxGray, fontSize = 14.sp)
                Text("إجمالي ما سبّحت: $lifetime", color = PxGold, fontSize = 13.sp)
                OutlinedButton(onClick = {
                    count = 0; prefs.edit().putInt("count", 0).apply()
                }, shape = RoundedCornerShape(14.dp)) { Text("تصفير العدّاد") }
            }
            P53BottomNav(onBack = onBack, onHome = onHome)
        }
    }
}

// ------------------------------------------------------- khatm completion dua ----

private data class KdItem(val title: String, val text: String, val source: String)

private val kdItems = listOf(
    KdItem("دعاء مأثور مشهور عند ختم القرآن",
        "اللَّهُمَّ ارْحَمْنِي بِالْقُرْآنِ، وَاجْعَلْهُ لِي إِمَامًا وَنُورًا وَهُدًى وَرَحْمَةً، اللَّهُمَّ ذَكِّرْنِي مِنْهُ مَا نَسِيتُ، وَعَلِّمْنِي مِنْهُ مَا جَهِلْتُ، وَارْزُقْنِي تِلَاوَتَهُ آنَاءَ اللَّيْلِ وَأَطْرَافَ النَّهَارِ، وَاجْعَلْهُ لِي حُجَّةً يَا رَبَّ الْعَالَمِينَ.",
        "من الأدعية المشهورة عند الختم؛ وليس فيها حديث صحيح مرفوع، فهي دعاء حسن لا يُنسب للنبي ﷺ."),
    KdItem("كفّارة المجلس",
        "سُبْحَانَكَ اللَّهُمَّ وَبِحَمْدِكَ، أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا أَنْتَ، أَسْتَغْفِرُكَ وَأَتُوبُ إِلَيْكَ.",
        "رواه الترمذي والنسائي، وصححه الألباني."),
    KdItem("دعاء إبراهيم وإسماعيل عليهما السلام",
        "رَبَّنَا تَقَبَّلْ مِنَّا إِنَّكَ أَنتَ السَّمِيعُ الْعَلِيمُ",
        "سورة البقرة، الآية 127."),
)

@Composable
internal fun P58KhatmDuaScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(PxBeige).windowInsetsPadding(WindowInsets.safeDrawing)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = PxGreen)) {
                        Column(Modifier.fillMaxWidth().padding(19.dp)) {
                            Text("🤲 دعاء ختم القرآن", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(6.dp))
                            Text("تقبّل الله منك. ادعُ بما تيسّر لك بعد الختم.", fontSize = 13.sp, color = Color(0xFFDCEDE9))
                        }
                    }
                }
                items(kdItems.size) { i ->
                    val k = kdItems[i]
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE3E9E3))) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(k.title, color = PxGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(k.text, color = PxInk, fontSize = 20.sp, lineHeight = 36.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(k.source, color = PxGray, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(ClipData.newPlainText("دعاء", k.text))
                                }) { Text("نسخ", color = PxGreen) }
                            }
                        }
                    }
                }
            }
            P53BottomNav(onBack = onBack, onHome = onHome)
        }
    }
}

/** Shown once when the reader reaches the last page of the Mushaf. */
@Composable
internal fun P58KhatmDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🌟 ما شاء الله، وصلت آخر المصحف") },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(kdItems[0].text, fontSize = 17.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
                    Text(kdItems[0].source, fontSize = 11.sp, color = PxGray)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("آمين") } },
    )
}
