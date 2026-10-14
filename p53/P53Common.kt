package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val P53Green = Color(0xFF064D3B)
private val P53Ink = Color(0xFF263F35)

/** P53: the one and only Back / Home bar. Same order everywhere:
 * «رجوع» on the right (first in RTL), «الرئيسية» filled green on the left. */
@Composable
fun P53BottomNav(onBack: () -> Unit, onHome: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = Color.White, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(19.dp), tint = P53Ink)
                Spacer(Modifier.width(6.dp))
                Text("رجوع", color = P53Ink, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onHome,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = P53Green),
            ) {
                Icon(Icons.Default.Home, null, Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text("الرئيسية", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** P53: night reading for the printed Mushaf pages. The Quran artwork files are never
 * changed — only a display colour filter is applied while drawing. */
object P53NightMode {
    private const val PREFS = "p53_reading"
    var enabled by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("night", false)
    }

    fun toggle(context: Context) {
        load(context)
        enabled = !enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("night", enabled).apply()
    }

    /** White page → near black, black script → warm cream; colours stay distinguishable. */
    val pageFilter: ColorFilter = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.93f, 0f, 0f, 0f, 237f,
                0f, -0.88f, 0f, 0f, 226f,
                0f, 0f, -0.80f, 0f, 204f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    val pageBackground = Color(0xFF12110E)
}

/** Small «ليلي / نهاري» switch for the Mushaf reader tool row. */
@Composable
fun P53NightToggle(accent: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { P53NightMode.load(context) }
    TextButton(
        onClick = { P53NightMode.toggle(context) },
        colors = ButtonDefaults.textButtonColors(contentColor = accent),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) { Text(if (P53NightMode.enabled) "☀ نهاري" else "🌙 ليلي", fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)) }
}
