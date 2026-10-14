package com.ab.khatma.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** P60: one clear page-zoom control, shown at the bottom of the reader (next to previous / next). */
@Composable
internal fun P60ZoomBar(zoom: Float, onMinus: () -> Unit, onPlus: () -> Unit, onReset: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(50), color = Color(0xFFE7F0EA)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔍 تكبير الصفحة", color = Color(0xFF064D3B), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp))
                FilledTonalButton(onClick = onMinus, enabled = zoom > 1.01f, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(38.dp), shape = RoundedCornerShape(50)) { Text("−", fontSize = 22.sp) }
                TextButton(onClick = onReset, enabled = zoom > 1.01f, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("${(zoom * 100).toInt()}%", color = Color(0xFF064D3B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                FilledTonalButton(onClick = onPlus, enabled = zoom < 3.49f, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(38.dp), shape = RoundedCornerShape(50)) { Text("+", fontSize = 22.sp) }
            }
        }
    }
}
