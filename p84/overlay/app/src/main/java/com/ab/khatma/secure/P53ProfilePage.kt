package com.ab.khatma.secure

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Edit
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
    onDeleteAccount: () -> Unit = {},
    archivedCount: Int = 0,
    onOpenArchive: () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { P53NightMode.load(context); P52SurahAudio.init(context) }
    var name by remember(userName) { mutableStateOf(userName) }
    var editName by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteWord by remember { mutableStateOf("") }
    var showReciters by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val active = groups.filter { it.status == "active" }
    val todayWards = active.filter { it.assignmentId != null }
    val doneToday = todayWards.count { it.completed }
    val total = remember(doneToday) { P53LocalStats.total(context) }
    val streak = remember(doneToday) { P53LocalStats.streak(context) }
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("")
    }

    if (showReciters) com.ab.khatma.ui.screens.P52ReciterPicker(onDismiss = { showReciters = false }) {
        P52SurahAudio.selectReciter(context, it.id); showReciters = false
    }
    if (editName) AlertDialog(
        onDismissRequest = { editName = false; name = userName },
        title = { Text("تعديل الاسم") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { if (it.length <= 40) name = it },
                label = { Text("الاسم الظاهر في الختمات") }, singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.trim().length >= 2 && name.trim() != userName,
                onClick = { editName = false; onSaveName(name.trim()) },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { editName = false; name = userName }) { Text("إلغاء") } },
    )
    if (showPrivacy) P84PrivacyPage(userId = userId, onClose = { showPrivacy = false })
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        title = { Text("عن التطبيق") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ختمة — AB Khatma", fontWeight = FontWeight.Bold)
                if (version.isNotBlank()) Text("النسخة: $version")
                Text("بياناتك على هذا الموبايل ومجموعاتك على السيرفر. شكرًا لاستخدامك ختمة.",
                    color = Color(0xFF746D62), fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("تمام") } },
    )
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("تسجيل الخروج") },
        text = { Text("هتحتاج رقم الحساب وكلمة المرور (أو Google) عشان ترجع تاني. مجموعاتك محفوظة على السيرفر.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("خروج", color = Color(0xFFB3261E)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("إلغاء") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false; deleteWord = "" },
        title = { Text("حذف الحساب نهائيًا") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("هيتمسح اسمك وبيانات الدخول وربط Google ورسائلك ومنشوراتك، ومش هتقدر ترجع للحساب ده تاني.")
                Text("لو أنت قائد ختمة فيها أعضاء، لازم تخرّجهم أو تقفل الختمة الأول.", color = Color(0xFF746D62), fontSize = 13.sp)
                OutlinedTextField(value = deleteWord, onValueChange = { deleteWord = it.take(10) },
                    label = { Text("اكتب «حذف» للتأكيد") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = deleteWord.trim() == "حذف" && !busy,
                onClick = { confirmDelete = false; deleteWord = ""; onDeleteAccount() }) {
                Text("حذف نهائي", color = Color(0xFFB3261E))
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false; deleteWord = "" }) { Text("إلغاء") } },
    )

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Identity card: avatar, name with a pencil, account number with copy.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(PDeep, PGreen))),
        ) {
            Text("✦", color = Color(0x22FFFFFF), fontSize = 120.sp, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-14).dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(PGold), contentAlignment = Alignment.Center) {
                    Text(userName.trim().take(1).ifBlank { "؟" }, color = PDeep, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(userName, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = { name = userName; editName = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Edit, "تعديل الاسم", tint = PGold, modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCopyId() }) {
                        Text("رقم الحساب  $userId", color = Color(0xFFF7EBCB), fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ContentCopy, "نسخ رقم الحساب", tint = Color(0xFFF7EBCB), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        Text("احتفظ برقم الحساب — بيه تقدر تدخل من أي موبايل.", color = Color(0xFF746D62), fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp))

        // Statistics
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            P53Stat("📖", "$doneToday/${todayWards.size}", "أوراد اليوم", Modifier.weight(1f))
            P53Stat("🔥", "$streak", "أيام متتالية", Modifier.weight(1f))
            P53Stat("✅", "$total", "وِرد مكتمل", Modifier.weight(1f))
            P53Stat("👥", "${active.map { it.groupId }.distinct().size}", "ختماتي", Modifier.weight(1f))
        }
        P58WeeklyReport()

        // Google
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, PLine)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1EBDD)), contentAlignment = Alignment.Center) {
                    Text("G", color = PGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("حساب Google", color = PInk, fontWeight = FontWeight.SemiBold)
                    Text("اربطه عشان تدخل بضغطة من غير كلمة مرور، ومجموعاتك زي ما هي.",
                        color = Color(0xFF746D62), fontSize = 12.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onLinkGoogle, enabled = !busy && googleConfigured,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("اربط الآن") }
            }
        }

        // Settings
        P53Card("الإعدادات") {
            P53SettingRow("🔔", "التنبيهات", "الورد، الأذكار، الصلاة، الدعاء، الكهف", onOpenNotifications)
            P53SettingRow("🔊", "القارئ المفضّل",
                P52SurahAudio.reciter.name + (if (P52SurahAudio.reciter.style.isNotBlank()) " — ${P52SurahAudio.reciter.style}" else "")) {
                showReciters = true
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                P53IconBox("🌙")
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("الوضع الليلي للمصحف", color = PInk, fontWeight = FontWeight.SemiBold)
                    Text("خلفية داكنة لصفحات المصحف عند القراءة ليلًا", color = Color(0xFF746D62), fontSize = 12.sp)
                }
                Switch(checked = P53NightMode.enabled, onCheckedChange = { P53NightMode.toggle(context) })
            }
            P53SettingRow("📦", "الختمات المؤرشفة",
                if (archivedCount == 0) "لا توجد ختمات مؤرشفة" else "$archivedCount ختمة مخفية على هذا الموبايل", onOpenArchive)
            P53SettingRow("🔒", "الخصوصية وبياناتك", "البيانات، الصلاحيات، وحذف الحساب") { showPrivacy = true }
            P53SettingRow("ℹ️", "عن التطبيق", if (version.isBlank()) "ختمة" else "النسخة $version") { showAbout = true }
        }

        // Account actions, deliberately at the very end and away from the other buttons.
        Spacer(Modifier.height(6.dp))
        Text("الحساب", color = Color(0xFF746D62), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp))
        OutlinedButton(
            onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFFE3B9B5)),
        ) { Text("تسجيل الخروج", color = Color(0xFFB3261E)) }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("حذف حسابي نهائيًا", color = Color(0xFF9A5A55), fontSize = 13.sp)
        }
    }
}

@Composable
private fun P53Stat(emoji: String, value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, PLine)) {
        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 15.sp)
            Text(value, color = PGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
private fun P53IconBox(emoji: String) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFF1EBDD)), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = 18.sp)
    }
}

@Composable
private fun P53SettingRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            P53IconBox(emoji)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = PInk, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF746D62), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronLeft, null, tint = PGold)
        }
    }
}
