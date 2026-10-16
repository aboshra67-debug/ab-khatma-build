package com.ab.khatma.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranAyah
import com.ab.khatma.quran.QuranRepository
import kotlin.math.roundToInt

/** P67: ayahs the reader marked with the star (saved on the phone only). */
internal object P67Favs {
    private const val PREFS = "p67_favs"
    fun has(context: Context, key: String): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("set", emptySet())!!.contains(key)

    fun toggle(context: Context, key: String): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = p.getStringSet("set", emptySet())!!.toMutableSet()
        val on = if (key in now) { now.remove(key); false } else { now.add(key); true }
        p.edit().putStringSet("set", now).apply()
        return on
    }
}

@Composable
private fun P67SheetRow(icon: ImageVector, label: String, tint: Color = P61Green, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = P61Ink, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 12.sp, color = P61Ink.copy(alpha = 0.6f))
        }
    }
}

/** P67: the sheet that opens when an ayah of the printed page is touched: recite from here, recite up to …,
 * bookmark, star, copy, share, similar verses, meanings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun P67AyahSheet(
    aya: QuranAyah,
    repo: QuranRepository,
    bookmark: Pair<Int, Int>?,
    onBookmark: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val key = "${aya.surah}:${aya.ayah}"
    val surahName = repo.surahName(aya.surah)
    val lastAyah = remember(aya.surah) { repo.loadSurah(aya.surah).maxOfOrNull { it.ayah } ?: aya.ayah }
    var fav by remember(key) { mutableStateOf(P67Favs.has(context, key)) }
    var pickEnd by remember(key) { mutableStateOf(false) }
    var endAt by remember(key) { mutableFloatStateOf(minOf(lastAyah, aya.ayah + 4).toFloat()) }
    var showSimilar by remember { mutableStateOf(false) }
    if (showSimilar) P61SimilarDialog(repo, aya.surah, aya.ayah, onDismiss = { showSimilar = false })
    val label = "﴿ ${aya.text} ﴾ [سورة $surahName : ${aya.ayah}]"

    fun playRun(first: Int, last: Int) {
        val rows = repo.loadSurah(aya.surah).filter { it.ayah in first..last }
        if (rows.isEmpty()) return
        P52SurahAudio.init(context)
        P52SurahAudio.play(context, rows.map { P52SurahAudio.ayahCode(it.surah, it.ayah) })
        P52SurahAudio.followReader = true
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFFFCFAF4), contentColor = P61Ink) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$surahName : ${p61Digits(aya.ayah)}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = P61Green, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { fav = P67Favs.toggle(context, key) }) {
                    Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "مفضلة", tint = P61Gold)
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, label)
                    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Icon(Icons.Rounded.Share, "مشاركة", tint = P61Green) }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF5F0E2), modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                Text(
                    aya.text, fontFamily = P61Amiri, fontSize = 24.sp, lineHeight = 42.sp, color = P61Ink,
                    textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            HorizontalDivider(color = P61Ink.copy(alpha = 0.08f))
            P67SheetRow(Icons.Rounded.PlayArrow, "تلاوة", sub = "من هذه الآية إلى نهاية السورة") { playRun(aya.ayah, lastAyah) }
            P67SheetRow(Icons.Rounded.Repeat, "هذه الآية فقط") { playRun(aya.ayah, aya.ayah) }
            P67SheetRow(Icons.Rounded.SkipNext, "تشغيل إلى…", sub = "اختر آخر آية تقف عندها") { pickEnd = !pickEnd }
            if (pickEnd) {
                Column(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
                    Text("من الآية ${p61Digits(aya.ayah)} إلى الآية ${p61Digits(endAt.roundToInt())}", fontSize = 14.sp, color = P61Ink)
                    if (lastAyah > aya.ayah) Slider(
                        value = endAt, onValueChange = { endAt = it },
                        valueRange = aya.ayah.toFloat()..lastAyah.toFloat(),
                        steps = (lastAyah - aya.ayah - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(thumbColor = P61Green, activeTrackColor = P61Green),
                    )
                    Button(
                        onClick = { playRun(aya.ayah, endAt.roundToInt().coerceIn(aya.ayah, lastAyah)) },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                    ) { Text("▶ ابدأ", fontWeight = FontWeight.Bold) }
                }
            }
            HorizontalDivider(color = P61Ink.copy(alpha = 0.08f))
            val marked = bookmark == (aya.surah to aya.ayah)
            P67SheetRow(
                if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "الفاصل الأحمر",
                tint = P61Wrong, sub = if (marked) "هذه الآية هي موضع الفاصل الآن" else "ضع الفاصل هنا لتكمل منه لاحقًا",
            ) { onBookmark() }
            P67SheetRow(Icons.Rounded.ContentCopy, "نسخ الآية") {
                runCatching {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("ayah", label))
                }
                onDismiss()
            }
            if (P61Similar.has(context, aya.surah, aya.ayah)) P67SheetRow(Icons.Rounded.Repeat, "🔁 آيات متشابهة") { showSimilar = true }
            P67SheetRow(Icons.Rounded.MenuBook, "معاني الكلمات والتفسير") { onMore() }
            Spacer(Modifier.height(6.dp))
            P64ReciterButton()
            if (P52SurahAudio.isActive) P66Bar()
        }
    }
}
