package com.ab.khatma.secure

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Uses authorized group status; no made-up attendance or late-reading data. */
private val deepGreen = Color(0xFF176A4D)
private val softGold = Color(0xFF956B19)
private val subtleRed = Color(0xFFA45638)
private val cream = Color(0xFFF8F2E5)

internal fun normalizeLeaderSearch(input: String): String = input.trim().lowercase()
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
    .replace('ى', 'ي').replace('ة', 'ه')

internal fun filterLeaderMembers(
    members: List<SecureV2Api.MemberInfo>, search: String, status: String
): List<SecureV2Api.MemberInfo> {
    val query = normalizeLeaderSearch(search)
    return members.asSequence().filter { member ->
        (query.isEmpty() || normalizeLeaderSearch(member.name).contains(query) ||
            member.juz?.toString() == query) && when (status) {
            "done" -> member.juz != null && member.completed
            "reading" -> member.juz != null && !member.completed
            "unassigned" -> member.juz == null
            else -> true
        }
    }.sortedWith(compareBy<SecureV2Api.MemberInfo>(
        { if (it.juz == null) 2 else if (it.completed) 1 else 0 },
        { normalizeLeaderSearch(it.name) }
    )).toList()
}

@Composable
private fun LeaderNumber(number: Int, title: String, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(17.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(number.toString(), style = MaterialTheme.typography.headlineMedium,
                color = accent, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun KhatmaLeaderOverview(
    group: SecureV2Api.GroupInfo, pendingCount: Int, busy: Boolean,
    onRefresh: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit,
    onPageKhatmas: () -> Unit
) {
    val assigned = group.members.count { it.juz != null }
    val completed = group.members.count { it.juz != null && it.completed }
    val remaining = assigned - completed
    val unassigned = group.members.size - assigned
    val ratio = if (assigned > 0) completed.toFloat() / assigned else 0f
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Surface(color = deepGreen, shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("متابعة القراءة اليوم", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(group.name, color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.titleMedium)
                Text((ratio * 100).toInt().toString() + "%   •   " + completed + " من " + assigned + " مكلفًا",
                    color = Color.White, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { ratio },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Color(0xFFD9B560), trackColor = Color.White.copy(alpha = 0.23f))
                Text("ملخص تكليفات الأجزاء اليوم. ختمات الصفحات لها شاشة متابعة منفصلة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.83f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LeaderNumber(group.members.size, "إجمالي الأعضاء", deepGreen, Modifier.weight(1f))
            LeaderNumber(completed, "أتموا الورد", deepGreen, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            LeaderNumber(remaining, "لم يُكملوا بعد", softGold, Modifier.weight(1f))
            LeaderNumber(unassigned, "بلا تكليف اليوم", subtleRed, Modifier.weight(1f))
        }
        if (pendingCount > 0) {
            Text("طلبات انضمام معلّقة: " + pendingCount,
                style = MaterialTheme.typography.titleSmall, color = softGold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = deepGreen)) {
                Text("تحديث الحالة")
            }
            OutlinedButton(onClick = onPageKhatmas, modifier = Modifier.weight(1f)) {
                Text("ختمات الصفحات")
            }
        }
        Surface(color = cream, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("دعوة أعضاء", color = deepGreen,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("الكود: " + group.inviteCode, color = Color(0xFF373B32))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopy) { Text("نسخ الكود") }
                    OutlinedButton(onClick = onShare) { Text("مشاركة الدعوة") }
                }
            }
        }
    }
}

@Composable
fun KhatmaLeaderSearchAndFilters(
    search: String, onSearch: (String) -> Unit, filter: String,
    onFilter: (String) -> Unit, shown: Int, total: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("حالة أعضاء المجموعة", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        OutlinedTextField(value = search, onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("بحث بالاسم أو رقم الجزء") },
            shape = RoundedCornerShape(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "الكل", "reading" to "لم يُكمل", "done" to "مكتمل",
                "unassigned" to "بدون تكليف").forEach { choice ->
                FilterChip(selected = filter == choice.first,
                    onClick = { onFilter(choice.first) }, label = { Text(choice.second) })
            }
        }
        Text("عرض " + shown + " من " + total + " عضو",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun KhatmaLeaderMemberRow(member: SecureV2Api.MemberInfo) {
    val status = when {
        member.juz == null -> "بلا تكليف"
        member.completed -> "مكتمل"
        else -> "لم يُكمل"
    }
    val accent = when {
        member.juz == null -> subtleRed
        member.completed -> deepGreen
        else -> softGold
    }
    val role = when (member.role) {
        "leader" -> "قائد"
        "assistant" -> "مشرف"
        else -> "عضو"
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(cream, CircleShape),
                contentAlignment = Alignment.Center) {
                Text(member.name.trim().take(1).ifEmpty { "•" }, color = deepGreen,
                    style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(member.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(role + " · " + if (member.juz != null) "الجزء " + member.juz else "غير مكلف اليوم",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = accent.copy(alpha = 0.10f), shape = RoundedCornerShape(11.dp)) {
                Text(status, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    color = accent, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun KhatmaLeaderPendingRow(
    member: SecureV2Api.MemberInfo, busy: Boolean, onApprove: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(member.name, modifier = Modifier.weight(1f))
            Button(onClick = onApprove, enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = deepGreen)) {
                Text("موافقة")
            }
        }
    }
}
