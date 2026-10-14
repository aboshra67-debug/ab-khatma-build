package com.ab.khatma.secure

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ab.khatma.quran.P52SurahAudio
import com.ab.khatma.ui.screens.P53NightMode
import java.time.LocalDate

private val PGreen = Color(0xFF064D3B)
private val PDeep = Color(0xFF063D30)
private val PGold = Color(0xFFD6B25B)
private val PCream = Color(0xFFFFFAEF)
private val PLine = Color(0xFFE8E0CE)
private val PInk = Color(0xFF263F35)

/** P53: completed-reading days kept on this phone only, for the profile statistics. */
object P53LocalStats {
    private const val PREFS = "p53_stats"
    fun recordCompletion(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay().toString()
        val days = p.getStringSet("days", emptySet())!!.toMutableSet().apply { add(today) }
        p.edit().putStringSet("days", days).putInt("total", p.getInt("total", 0) + 1).apply()
    }
    fun total(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("total", 0)
    fun streak(context: Context): Int {
        val days = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("days", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        var d = LocalDate.now().toEpochDay()
        if (d !in days) d -= 1 // today not read yet: the streak still counts up to yesterday
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}

@Composable
internal fun P53ProfilePage(
    userName: String,
    userId: Long,
    groups: List<SecureV2Api.TodayItem>,
    busy: Boolean,
    googleConfigured: Boolean,
    onSaveName: (String) -> Unit,
    onCopyId: () -> Unit,
    onLinkGoogle: () -> Unit,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P53NightMode.load(context); P52SurahAudio.init(context) }
    var name by remember(userName) { mutableStateOf(userName) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showReciters by remember { mutableStateOf(false) }
    val active = groups.filter { it.status == "active" }
    val todayWards = active.filter { it.assignmentId != null }
    val doneToday = todayWards.count { it.completed }
    val total = remember(doneToday) { P53LocalStats.total(context) }
    val streak = remember(doneToday) { P53LocalStats.streak(context) }

    if (showReciters) com.ab.khatma.ui.screens.P52ReciterPicker(onDismiss = { showReciters = false }) {
        P52SurahAudio.selectReciter(context, it.id); showReciters = false
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("تسجيل الخروج") },
        text = { Text("هتحتاج رقم الحساب وكلمة المرور (أو Google) عشان ترجع تاني. مجموعاتك محفوظة على السيرفر.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("خروج", color = Color(0xFFB3261E)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("إلغاء") } },
    )

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(PDeep, PGreen))).padding(vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(78.dp).clip(CircleShape).background(PGold), contentAlignment = Alignment.Center) {
                    Text(userName.trim().take(1).ifBlank { "؟" }, color = PDeep, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text(userName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("حسابي", color = Color(0xFFF7EBCB), fontSize = 13.sp)
            }
        }

        // Statistics
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            P53Stat("$doneToday/${todayWards.size}", "أوراد اليوم", Modifier.weight(1f))
            P53Stat("$streak", "أيام متتالية", Modifier.weight(1f))
            P53Stat("$total", "وِرد مكتمل", Modifier.weight(1f))
            P53Stat("${active.map { it.groupId }.distinct().size}", "ختماتي", Modifier.weight(1f))
        }

        // My details
        P53Card("بياناتي") {
            OutlinedTextField(
                value = name, onValueChange = { if (it.length <= 40) name = it },
                label = { Text("الاسم الظاهر في الختمات") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSaveName(name.trim()) },
                enabled = !busy && name.trim().length >= 2 && name.trim() != userName,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PGreen),
            ) { Text("حفظ الاسم") }
            HorizontalDivider(color = PLine)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("رقم الحساب", color = Color(0xFF746D62), fontSize = 12.sp)
                    Text("$userId", color = PInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onCopyId, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("نسخ")
                }
            }
            Text("احتفظ برقم الحساب — بيه تقدر تدخل من أي موبايل.", color = Color(0xFF746D62), fontSize = 12.sp)
            HorizontalDivider(color = PLine)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("حساب Google", color = PInk, fontWeight = FontWeight.SemiBold)
                    Text("اربطه عشان تدخل بضغطة من غير كلمة مرور، ومجموعاتك زي ما هي.",
                        color = Color(0xFF746D62), fontSize = 12.sp)
                }
                TextButton(onClick = onLinkGoogle, enabled = !busy && googleConfigured) { Text("اربط الآن") }
            }
        }

        // Settings
        P53Card("الإعدادات") {
            P53SettingRow(Icons.Default.Notifications, "التنبيهات", "الورد، الأذكار، الصلاة، الدعاء، الكهف", onOpenNotifications)
            P53SettingRow(Icons.Default.RecordVoiceOver, "القارئ المفضّل",
                P52SurahAudio.reciter.name + (if (P52SurahAudio.reciter.style.isNotBlank()) " — ${P52SurahAudio.reciter.style}" else "")) {
                showReciters = true
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DarkMode, null, tint = PGreen)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("الوضع الليلي للمصحف", color = PInk, fontWeight = FontWeight.SemiBold)
                    Text("خلفية داكنة لصفحات المصحف عند القراءة ليلًا", color = Color(0xFF746D62), fontSize = 12.sp)
                }
                Switch(checked = P53NightMode.enabled, onCheckedChange = { P53NightMode.toggle(context) })
            }
        }

        OutlinedButton(
            onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFFE3B9B5)),
        ) { Text("تسجيل الخروج", color = Color(0xFFB3261E)) }
    }
}

@Composable
private fun P53Stat(value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, PLine)) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = PGreen, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFF746D62), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
internal fun P53Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = PCream, border = BorderStroke(1.dp, PLine)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = PGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun P53SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = PInk, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF746D62), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronLeft, null, tint = PGold)
        }
    }
}
