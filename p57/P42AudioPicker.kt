package com.ab.khatma.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ab.khatma.quran.P52SurahAudio

private val LsGreen = Color(0xFF064D3B)
private val LsLine = Color(0xFFE9DDC6)
private val LsMuted = Color(0xFF746D62)

/** P57: bottom sheet for listening to the surah currently open in the reader. */
@Composable
internal fun P42AudioPicker(
    surah: Int,
    title: String,
    maxAyah: Int,
    onDismiss: () -> Unit,
    onPlay: (firstAyah: Int, untilEnd: Boolean) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P52SurahAudio.init(context) }
    var ayah by remember(surah) { mutableIntStateOf(1) }
    var pickReciter by remember { mutableStateOf(false) }
    if (pickReciter) P55ReciterPicker(onDismiss = { pickReciter = false }) {
        P52SurahAudio.selectReciter(context, it.id)
        pickReciter = false
    }
    val reciter = P52SurahAudio.savedReciter
    val safeMax = maxAyah.coerceAtLeast(1)
    val shutter = remember { MutableInteractionSource() }
    val swallow = remember { MutableInteractionSource() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = shutter, indication = null) { onDismiss() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(interactionSource = swallow, indication = null) { },
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    color = Color(0xFFFFFBF0),
                    shadowElevation = 12.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔊 استماع إلى $title", color = LsGreen, fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismiss) { Text("✕", color = LsMuted, fontSize = 20.sp) }
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp), color = Color.White,
                            border = BorderStroke(1.dp, LsLine), modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("القارئ", color = LsMuted, fontSize = 12.sp)
                                        Text(reciter.name + (if (reciter.style.isNotBlank()) " — ${reciter.style}" else ""),
                                            color = LsGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    OutlinedButton(onClick = { pickReciter = true }, shape = RoundedCornerShape(12.dp)) {
                                        Text("تغيير")
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("يحتاج إنترنت. المصحف نفسه بيشتغل بدونه.", color = LsMuted, fontSize = 12.sp)
                                if (reciter.ayahFolder == null) Text(
                                    "هذا القارئ متاح للسورة كاملة فقط؛ الآيات المنفردة بصوت الحصري.",
                                    color = LsMuted, fontSize = 12.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center) {
                            OutlinedButton(
                                onClick = { if (ayah > 1) ayah-- },
                                enabled = ayah > 1,
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(48.dp),
                            ) { Text("−", fontSize = 24.sp) }
                            Text("الآية $ayah من $safeMax", color = LsGreen, fontSize = 18.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = { if (ayah < safeMax) ayah++ },
                                enabled = ayah < safeMax,
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(48.dp),
                            ) { Text("+", fontSize = 24.sp) }
                        }
                        Button(
                            onClick = { onPlay(1, true) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LsGreen),
                        ) { Text("▶ تشغيل السورة كاملة", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { onPlay(ayah, true) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) { Text("آية بآية من هنا", fontSize = 14.sp) }
                            OutlinedButton(
                                onClick = { onPlay(ayah, false) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) { Text("هذه الآية فقط", fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}
