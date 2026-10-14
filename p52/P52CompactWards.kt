package com.ab.khatma.secure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** P52: compact one-row ward cards. Same data, same callbacks and same confirmation
 * dialog as P39 (no write happens here); only the layout is smaller so several
 * khatmas fit on the home screen. The leader line appears only when a real name exists. */
@Composable
internal fun P52CompactWards(
    assignments: List<SecureV2Api.TodayItem>,
    busy: Boolean,
    onRead: (SecureV2Api.TodayItem) -> Unit,
    onConfirm: (SecureV2Api.TodayItem) -> Unit,
    maxPreview: Int = Int.MAX_VALUE,
    viewerName: String = "",
    onShowAll: () -> Unit = {},
) {
    val readerWards = assignments.filter {
        it.status == "active" && it.assignmentId != null
    }.sortedWith(compareBy<SecureV2Api.TodayItem> { it.completed }.thenBy { it.groupName })
    if (readerWards.isEmpty()) return
    val remaining = readerWards.count { !it.completed }
    val emerald = Color(0xFF075740)
    val olive = Color(0xFF263F35)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (remaining == 0) "أتممت أوراد اليوم ✓" else "المطلوب قراءته اليوم",
                    color = olive, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFE5EFE9)) {
                    Text("$remaining متبقٍ", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = emerald, style = MaterialTheme.typography.labelMedium)
                }
            }
            readerWards.take(maxPreview).forEach { ward ->
                Surface(
                    onClick = { onRead(ward) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE3DDCD)),
                    color = if (ward.completed) Color(0xFFF0F5F0) else Color.White,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null,
                            tint = emerald, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ward.groupName, color = olive, fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val leader = if (ward.role == "leader" && viewerName.isNotBlank()) viewerName
                                else ward.leaderName?.takeIf { it.isNotBlank() }
                            val juzText = ward.juz?.let { "الجزء $it" } ?: "افتح جدول الختمة"
                            Text(
                                if (leader != null) "$juzText  •  القائد: $leader" else juzText,
                                color = Color(0xFF525C55), fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        if (ward.completed) {
                            Icon(Icons.Default.CheckCircle, "تمت القراءة", tint = emerald,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تمت", color = emerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            OutlinedButton(
                                onClick = { onRead(ward) },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) { Text(if (ward.juz == null) "جدولي" else "اقرأ", fontSize = 13.sp, maxLines = 1) }
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = { onConfirm(ward) }, enabled = !busy,
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = emerald),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) { Text("تم ✓", fontSize = 13.sp, maxLines = 1) }
                        }
                    }
                }
            }
            if (remaining > 0) {
                Text("قرأت من المصحف الورقي؟ اضغط «تم ✓» ثم أكّد.",
                    fontSize = 12.sp, color = Color(0xFF626E66))
            }
            if (readerWards.size > maxPreview) {
                TextButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth()) {
                    Text("عرض كل ختماتي (" + readerWards.size + ")", color = emerald,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
