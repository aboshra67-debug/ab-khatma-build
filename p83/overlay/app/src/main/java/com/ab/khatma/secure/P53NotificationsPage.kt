package com.ab.khatma.secure

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.ab.khatma.reminders.P53ReminderScheduler
import com.ab.khatma.reminders.P53ReminderSettings
import com.ab.khatma.reminders.P53ReminderStore
import com.ab.khatma.ui.screens.P68Reading
import com.ab.khatma.ui.screens.P71StartupRecitation

private val NGreen = Color(0xFF064D3B)
private val NInk = Color(0xFF263F35)
private val NGray = Color(0xFF746D62)

private fun p53Clock(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    return "%d:%02d %s".format(h12, m, if (h < 12) "ص" else "م")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun P53NotificationsPage() {
    val context = LocalContext.current
    val store = remember { P53ReminderStore(context) }
    var s by remember { mutableStateOf(store.load()) }
    val enabled = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    LaunchedEffect(Unit) {
        P71StartupRecitation.load(context)
        P68Reading.load(context)
    }

    fun update(next: P53ReminderSettings) {
        s = next
        store.save(next)
        P53ReminderScheduler.reschedule(context)
    }
    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, false).show()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("التنبيهات", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NInk)
        Text("كل التنبيهات بتتحسب على موبايلك. اختار اللي يناسبك ووقته.", color = NGray, fontSize = 13.sp)

        if (!enabled) Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF1E6)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("الإشعارات مقفولة للتطبيق", fontWeight = FontWeight.Bold, color = Color(0xFF8A4B12))
                Text("افتح الإعدادات وفعّل الإشعارات عشان التنبيهات توصلك.", fontSize = 13.sp, color = Color(0xFF8A4B12))
                OutlinedButton(onClick = {
                    val i = if (Build.VERSION.SDK_INT >= 26)
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                    runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("فتح إعدادات الإشعارات") }
            }
        }

        P53Card("أصوات التطبيق") {
            P53ToggleRow("صوت الافتتاحية", "تشغيل التلاوة مرة عند فتح التطبيق", P71StartupRecitation.enabled) {
                if (it != P71StartupRecitation.enabled) P71StartupRecitation.toggle(context)
            }
            P53ToggleRow("صوت تقليب الورق", "تشغيل صوت الورق عند تقليب الصفحات في وضع القراءة", P68Reading.soundOn) {
                if (it != P68Reading.soundOn) P68Reading.toggleSound(context)
            }
        }

        P53Card("ورد القرآن") {
            P53ToggleRow("تذكير الورد اليومي", "مش هيظهر لو قرأت وردك خلاص", s.ward,
                timeLabel = p53Clock(s.wardMinutes), onTime = { pickTime(s.wardMinutes) { update(s.copy(wardMinutes = it)) } }
            ) { update(s.copy(ward = it)) }
            P53ToggleRow("سورة الكهف يوم الجمعة", "مع زرار «استمع للكهف» في الإشعار", s.fridayKahf,
                timeLabel = p53Clock(s.kahfMinutes), onTime = { pickTime(s.kahfMinutes) { update(s.copy(kahfMinutes = it)) } }
            ) { update(s.copy(fridayKahf = it)) }
        }

        P53Card("الأذكار") {
            P53ToggleRow("أذكار الصباح", "بعد الفجر بـ ${s.adhkarOffset} دقيقة", s.morningAdhkar) { update(s.copy(morningAdhkar = it)) }
            P53ToggleRow("أذكار المساء", "بعد العصر بـ ${s.adhkarOffset} دقيقة", s.eveningAdhkar) { update(s.copy(eveningAdhkar = it)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("بعد الصلاة بـ", color = NGray, fontSize = 13.sp)
                listOf(15, 30, 45).forEach { m ->
                    FilterChip(selected = s.adhkarOffset == m, onClick = { update(s.copy(adhkarOffset = m)) },
                        label = { Text("$m د") })
                }
            }
        }

        P53Card("الصلاة") {
            P53ToggleRow("تنبيه قبل الأذان", "إضافة للأذان الموجود — بيتحسب من مواقيت مدينتك", s.prePrayer) { update(s.copy(prePrayer = it)) }
            if (s.prePrayer) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("قبلها بـ", color = NGray, fontSize = 13.sp)
                listOf(5, 10, 15).forEach { m ->
                    FilterChip(selected = s.prePrayerMinutes == m, onClick = { update(s.copy(prePrayerMinutes = m)) },
                        label = { Text("$m د") })
                }
            }
        }

        P53Card("الدعاء") {
            P53ToggleRow("دعاء اليوم", "دعاء قصير من حصن المسلم كل يوم", s.dailyDua,
                timeLabel = p53Clock(s.duaMinutes), onTime = { pickTime(s.duaMinutes) { update(s.copy(duaMinutes = it)) } }
            ) { update(s.copy(dailyDua = it)) }
        }
    }
}

@Composable
private fun P53ToggleRow(
    title: String, subtitle: String, checked: Boolean,
    timeLabel: String? = null, onTime: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NInk, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NGray, fontSize = 12.sp)
            if (timeLabel != null && onTime != null && checked) TextButton(
                onClick = onTime, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) { Text("الوقت: $timeLabel  ✎", color = NGreen, fontSize = 13.sp) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
