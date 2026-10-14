package com.ab.khatma.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio

/** P57: two soft arrows on the sides of the page while reading full screen, plus a zoom switch.
 * Left arrow = next page, right arrow = previous page (same as the «التالي» / «السابق» buttons). */
@Composable
internal fun BoxScope.P57PageArrows(
    visible: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    zoomed: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleZoom: () -> Unit,
) {
    if (!visible) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.matchParentSize()) {
            if (hasNext) Surface(
                onClick = onNext,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(width = 38.dp, height = 78.dp),
                shape = RoundedCornerShape(19.dp),
                color = Color(0xFF064D3B).copy(alpha = 0.30f),
            ) { Box(contentAlignment = Alignment.Center) { Text("‹", color = Color.White, fontSize = 34.sp) } }
            if (hasPrev) Surface(
                onClick = onPrev,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(width = 38.dp, height = 78.dp),
                shape = RoundedCornerShape(19.dp),
                color = Color(0xFF064D3B).copy(alpha = 0.30f),
            ) { Box(contentAlignment = Alignment.Center) { Text("›", color = Color.White, fontSize = 34.sp) } }
            Surface(
                onClick = onToggleZoom,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp),
                shape = RoundedCornerShape(50),
                color = Color(0xFF064D3B).copy(alpha = 0.45f),
            ) {
                Text(if (zoomed) "↩ حجم عادي" else "🔍 تكبير", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** P57: Quran index header extension — big listen button and a clear reciter line, in the green area. */
@Composable
internal fun P57ListenHeader(onPickSurahs: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    var pick by remember { mutableStateOf(false) }
    if (pick) P55ReciterPicker(onDismiss = { pick = false }) {
        P52SurahAudio.selectReciter(context, it.id)
        pick = false
    }
    val r = P52SurahAudio.reciter
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF064D3B))
            .padding(start = 13.dp, end = 13.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onPickSurahs,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFE2BE70),
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("🎧 اختيار السور للاستماع", color = Color(0xFF3B2A06), fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp)
            }
        }
        Surface(
            onClick = { pick = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0B624C),
            border = BorderStroke(1.dp, Color(0xFFDFBD78).copy(alpha = 0.6f)),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔊 القارئ: " + r.name + (if (r.style.isNotBlank()) " (${r.style})" else ""),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("تغيير ▾", color = Color(0xFFFFE6A9), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
