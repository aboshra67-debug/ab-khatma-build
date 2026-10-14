package com.ab.khatma.secure

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** P58: counts the distinct Mushaf pages opened each day, on this phone only. Nothing is sent anywhere. */
object P58Stats {
    private const val PREFS = "p58_stats"

    fun recordPage(context: Context, page: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "d_" + LocalDate.now().toEpochDay()
        val set = prefs.getStringSet(key, emptySet())!!.toMutableSet()
        if (set.add(page.toString())) prefs.edit().putStringSet(key, set).apply()
    }

    fun pagesOn(context: Context, epochDay: Long): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("d_$epochDay", emptySet())!!.size
}

@Composable
internal fun P58WeeklyReport() {
    val context = LocalContext.current
    val today = LocalDate.now()
    val days = remember { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val counts = remember { days.map { P58Stats.pagesOn(context, it.toEpochDay()) } }
    val total = counts.sum()
    val active = counts.count { it > 0 }
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📊 تقرير آخر 7 أيام", color = Color(0xFF064D3B), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(if (total == 0) "افتح المصحف واقرأ، وهتلاقي تقدمك هنا."
                else "قرأت $total صفحة في $active من 7 أيام.", color = Color(0xFF746D62), fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.width(18.dp).height((6 + 40 * counts[i] / maxCount).dp)
                            .background(if (counts[i] > 0) Color(0xFF064D3B) else Color(0xFFE9DDC6), RoundedCornerShape(5.dp)))
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ar")),
                            fontSize = 9.sp, color = Color(0xFF746D62))
                    }
                }
            }
        }
    }
}
