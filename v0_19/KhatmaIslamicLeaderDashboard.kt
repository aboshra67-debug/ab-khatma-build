package com.ab.khatma.secure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Display-only leader dashboard; secured API still enforces memberships and permissions. */
private val leaderGreen = Color(0xFF145B40)
private val leaderCream = Color(0xFFFFFDF6)
private val leaderGold = Color(0xFFD3B15E)

@Composable
private fun LeaderStat(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = leaderCream),
        border = BorderStroke(1.dp, Color(0xFFE7E0CD))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = leaderGreen,
                fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun KhatmaIslamicLeaderDashboard(
    item: SecureV2Api.TodayItem,
    details: SecureV2Api.GroupInfo?,
    pending: List<SecureV2Api.MemberInfo>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onApprove: (Long, Boolean) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenRead: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Card(shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = leaderGreen),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(leaderGreen, Color(0xFF0B3929))))
                .padding(19.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("۞  لوحة قائد الختمة", color = leaderGold,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.groupName, color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("مجموعة نشطة  •  مساحة خاصة بإدارتك", color = Color(0xFFE4F1E8),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeaderStat("الأعضاء", details?.members?.size?.toString() ?: "—", Modifier.weight(1f))
            LeaderStat("طلبات الانضمام", if (details == null) "—" else pending.size.toString(), Modifier.weight(1f))
            LeaderStat("إنجاز اليوم", if (item.totalCount > 0) item.doneCount.toString() + "/" + item.totalCount else "—", Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("إدارة المجموعة", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("تحديث") }
        }
        if (details == null) {
            Card(colors = CardDefaults.cardColors(containerColor = leaderCream),
                modifier = Modifier.fillMaxWidth()) {
                Text("جاري تحميل تفاصيل المجموعة، أو اضغط تحديث.",
                    modifier = Modifier.padding(15.dp))
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = leaderCream),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFE4DDCA))) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("رمز الدعوة", style = MaterialTheme.typography.labelMedium)
                    Text(details.inviteCode, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = leaderGreen)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onCopy(details.inviteCode) }, modifier = Modifier.weight(1f)) {
                            Text("نسخ الرمز")
                        }
                        Button(onClick = { onShare(details.inviteCode) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = leaderGreen)) {
                            Text("مشاركة الدعوة")
                        }
                    }
                }
            }
        }
        Text("اختصارات القائد", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenRead, modifier = Modifier.weight(1f)) { Text("القراءة") }
            OutlinedButton(onClick = onOpenChat, modifier = Modifier.weight(1f)) { Text("الشات") }
        }
        Button(onClick = onOpenPlans, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = leaderGreen),
            shape = RoundedCornerShape(14.dp)) { Text("الختمات وتوزيع الصفحات") }
        if (pending.isNotEmpty()) {
            Text("طلبات الانضمام", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            pending.forEach { person ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = leaderCream),
                    border = BorderStroke(1.dp, Color(0xFFE4DDCA))) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(person.name, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onApprove(person.id, true) }, enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = leaderGreen)) {
                                Text("قبول")
                            }
                            OutlinedButton(onClick = { onApprove(person.id, false) }, enabled = !busy) {
                                Text("رفض")
                            }
                        }
                    }
                }
            }
        }
        if (details != null) {
            Text("أعضاء المجموعة", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            if (details.members.isEmpty()) {
                Text("لا يوجد أعضاء مقبولون بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            details.members.forEach { member ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = leaderCream),
                    border = BorderStroke(1.dp, Color(0xFFE9E1CF))) {
                    Column(Modifier.fillMaxWidth().padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(member.name, fontWeight = FontWeight.SemiBold)
                        Text("الجزء: " + (member.juz?.toString() ?: "غير محدد") +
                             "   •   " + (if (member.completed) "مكتمل" else "قيد القراءة"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
