package com.ab.khatma.secure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private fun p103StatusLabel(status: String) = when (status) {
    "completed" -> "تمت ✓"
    "overdue" -> "متأخر"
    "today" -> "اليوم"
    "upcoming" -> "قادم"
    "extended" -> "ممدّد"
    "reassigned" -> "أُعيد توزيعه"
    "excused" -> "معفى"
    else -> status
}

private fun p103DayName(iso: String): String = runCatching {
    LocalDate.parse(iso).dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar")) + " " + iso.takeLast(5)
}.getOrDefault(iso)

/** Read-only schedule of the whole group for any accepted member (names and duties only). */
@Composable
fun P103GroupScheduleCard(token: String, groupId: Long) {
    var open by remember(groupId) { mutableStateOf(false) }
    var data by remember(groupId) { mutableStateOf<SecureV2Api.P103Overview?>(null) }
    var error by remember(groupId) { mutableStateOf("") }
    var loading by remember(groupId) { mutableStateOf(false) }
    LaunchedEffect(open, groupId) {
        if (open) {
            loading = true; error = ""
            try {
                data = withContext(Dispatchers.IO) { SecureV2Api.p103ScheduleOverview(token, groupId) }
            } catch (e: Exception) {
                error = e.message ?: "تعذر تحميل جدول المجموعة"
            } finally { loading = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Text(if (open) "إخفاء جدول المجموعة" else "جدول المجموعة (الأسبوع الجاي)")
        }
        if (open) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            data?.let { d ->
                Text("من ${d.start} إلى ${d.end}", style = MaterialTheme.typography.bodySmall)
                d.people.forEach { p ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            if (p.duties.isEmpty()) Text(
                                if (p.eligible) "لا تكليف في هذه الفترة" else "غير مشارك في القراءة",
                                style = MaterialTheme.typography.bodySmall)
                            p.duties.forEach { t ->
                                Text("${p103DayName(t.date)} • جزء ${t.juz} • ${p103StatusLabel(t.status)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Leader/deputy only (server enforces): who is late, remind one or everyone. */
@Composable
fun P103LateReadersCard(token: String, groupId: Long) {
    val scope = rememberCoroutineScope()
    var open by remember(groupId) { mutableStateOf(false) }
    var refresh by remember(groupId) { mutableIntStateOf(0) }
    var readers by remember(groupId) { mutableStateOf<List<SecureV2Api.P103Late>>(emptyList()) }
    var info by remember(groupId) { mutableStateOf("") }
    var busy by remember(groupId) { mutableStateOf(false) }
    LaunchedEffect(open, groupId, refresh) {
        if (open) {
            try {
                readers = withContext(Dispatchers.IO) { SecureV2Api.p103LateReaders(token, groupId) }
            } catch (e: Exception) { info = e.message ?: "تعذر تحميل المتأخرين" }
        }
    }
    fun run(job: () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            info = try { withContext(Dispatchers.IO) { job() } } catch (e: Exception) {
                e.message ?: "تعذر إرسال التذكير"
            } finally { busy = false }
            refresh++
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Text(if (open) "إخفاء المتأخرين" else "المتأخرون عن الورد وتذكيرهم")
        }
        if (open) {
            if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)
            if (readers.isEmpty()) Text("لا يوجد متأخرون الآن 🌿", style = MaterialTheme.typography.bodySmall)
            else Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                run { SecureV2Api.p103NudgeAll(token, groupId) }
            }) { Text("ذكّر الجميع") }
            readers.forEach { r ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Text(buildString {
                            if (r.overdueCount > 0) append("متأخر ${r.overdueCount} (أقدمها ${r.daysLateMax} يوم)")
                            if (r.openToday) { if (isNotEmpty()) append(" • "); append("ورد اليوم لم يكتمل") }
                        }, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = !busy && !r.nudgedToday, onClick = {
                            run { SecureV2Api.p103Nudge(token, groupId, r.membershipId) }
                        }) { Text(if (r.nudgedToday) "تم التذكير اليوم" else "ذكّره") }
                    }
                }
            }
        }
    }
}
