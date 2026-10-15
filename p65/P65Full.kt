package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.quran.QuranRepository
import org.json.JSONArray

/** The one-line reader title: surah • juz • hizb (with the ۞ quarter sign when the page has one) • page. */
internal object P65Info {
    private var quarters: IntArray? = null

    @Synchronized
    private fun load(context: Context): IntArray {
        quarters?.let { return it }
        val loaded = runCatching {
            val raw = context.applicationContext.assets.open("quran/p63_hizb.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(raw)
            IntArray(arr.length()) { arr.getInt(it) }
        }.getOrDefault(IntArray(0))
        quarters = loaded
        return loaded
    }

    fun line(context: Context, repo: QuranRepository, surahName: String, juz: Int, page: Int): String {
        val hizb = P63Hizb.ofPage(context, repo, page)
        var text = "سورة $surahName • الجزء ${p61Digits(juz)} • الحزب ${p61Digits(hizb)}"
        val q = load(context)
        if (q.size >= 240) {
            val codes = repo.loadPage(page).map { it.surah * 1000 + it.ayah }.toHashSet()
            for (i in q.indices) {
                if (q[i] in codes) {
                    val n = i / 4 + 1
                    text += when (i % 4) {
                        0 -> " ۞ بداية الحزب ${p61Digits(n)}"
                        1 -> " ۞ ربع"
                        2 -> " ۞ نصف"
                        else -> " ۞ ٣ أرباع"
                    }
                    break
                }
            }
        }
        return text + " • صفحة ${p61Digits(page)}"
    }
}

private val P65Green = Color(0xFF064D3B)

/** Full-screen page: clear previous / next buttons, the zoom pill, and a big speaker button that shows or hides the player. */
@Composable
internal fun BoxScope.P65FullControls(
    visible: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    zoomed: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleZoom: () -> Unit,
    playerOpen: Boolean,
    onTogglePlayer: () -> Unit,
    bottomPad: Int,
) {
    if (!visible) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.matchParentSize()) {
            if (hasNext) Surface(
                onClick = onNext,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(width = 54.dp, height = 96.dp),
                shape = RoundedCornerShape(22.dp),
                color = P65Green.copy(alpha = 0.55f),
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("‹", color = Color.White, fontSize = 34.sp)
                    Text("التالي", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (hasPrev) Surface(
                onClick = onPrev,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(width = 54.dp, height = 96.dp),
                shape = RoundedCornerShape(22.dp),
                color = P65Green.copy(alpha = 0.55f),
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("›", color = Color.White, fontSize = 34.sp)
                    Text("السابق", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                onClick = onToggleZoom,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottomPad.dp),
                shape = RoundedCornerShape(50),
                color = P65Green.copy(alpha = 0.72f),
            ) {
                Text(if (zoomed) "↩ حجم عادي" else "🔍 تكبير", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Surface(
                onClick = onTogglePlayer,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = bottomPad.dp).size(64.dp),
                shape = CircleShape,
                color = if (playerOpen || P52SurahAudio.isPlaying) P65Green.copy(alpha = 0.95f) else P65Green.copy(alpha = 0.78f),
                border = BorderStroke(2.dp, Color(0xFFDFBD78)),
                shadowElevation = 4.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (P52SurahAudio.isPlaying) "🔊" else "🔈", fontSize = 30.sp)
                }
            }
        }
    }
}

/** The player that opens with the speaker button: controls, reciter and speed. */
@Composable
internal fun P65FullPlayer(onPlayHere: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFFBF7EA).copy(alpha = 0.98f),
            border = BorderStroke(1.dp, Color(0xFFE6DCC2)),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (P52SurahAudio.isActive) {
                    P52MiniPlayer()
                } else {
                    Button(
                        onClick = onPlayHere,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = P65Green),
                    ) { Text("▶ ابدأ التلاوة من هذه الصفحة", fontWeight = FontWeight.Bold) }
                }
                P64ReciterButton()
                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                P62ChipRow(
                    speeds.map { "‎" + p61SpeedLabel(it) + "‎" to (kotlin.math.abs(P52SurahAudio.speed - it) < 0.01f) },
                ) { P52SurahAudio.setSpeed(context, speeds[it]) }
            }
        }
    }
}

/** Slim line shown at the top of the full-screen page, next to the «تصغير» pill. */
@Composable
internal fun P65FullInfo(info: String, night: Boolean, modifier: Modifier = Modifier) {
    Text(
        info,
        modifier = modifier,
        color = if (night) Color(0xFFE9DDB8) else P65Green,
        fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2,
    )
}
