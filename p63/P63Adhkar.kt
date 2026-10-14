package com.ab.khatma.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Morning / evening adhkar chapters are heard in a real reciter's voice, not in the phone's synthetic voice. */
internal fun p63IsMorningEvening(title: String): Boolean {
    val t = P61Words.norm(title)
    return t.contains("الصباح") || t.contains("المساء")
}

@Composable
internal fun P63AdhkarVoices(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = { P60VoiceAdhkar.stop(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp), shape = RoundedCornerShape(20.dp), color = P61Paper) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎙 أذكار الصباح والمساء بصوت الشيوخ", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = P61Green)
                    Text(
                        "اختر تسجيلًا واضغط عليه. يحتاج إنترنت، ولا يُحفظ شيء على الموبايل.",
                        fontSize = 12.sp, color = P61Ink.copy(alpha = 0.65f), lineHeight = 18.sp,
                    )
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        voiceTracks.forEachIndexed { i, t ->
                            val active = P60VoiceAdhkar.playing == i
                            Surface(
                                onClick = { P60VoiceAdhkar.toggle(i) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (active) Color(0xFFE6F0EA) else Color.White,
                                border = BorderStroke(1.dp, if (active) P61Green else Color(0xFFE6DCC2)),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when {
                                            active && P60VoiceAdhkar.loading -> "⏳"
                                            active -> "⏹"
                                            else -> "▶"
                                        },
                                        fontSize = 20.sp, color = P61Green,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(t.title, color = P61Ink, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                    if (P60VoiceAdhkar.failed) {
                        Text("تعذّر تشغيل التسجيل. تأكد من الإنترنت وجرّب تاني.", color = Color(0xFFB3261E), fontSize = 13.sp)
                    }
                    Text(
                        "المصدر: موقع Makkah Live (عبر archive.org). التسجيلات لأصحابها، وهي هنا للتجربة والاستماع فقط.",
                        fontSize = 11.sp, color = P61Ink.copy(alpha = 0.55f), lineHeight = 16.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { P60VoiceAdhkar.stop() }, modifier = Modifier.weight(1f)) { Text("إيقاف") }
                        Button(
                            onClick = { P60VoiceAdhkar.stop(); onDismiss() }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                        ) { Text("إغلاق") }
                    }
                }
            }
        }
    }
}
