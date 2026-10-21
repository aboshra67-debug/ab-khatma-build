package com.ab.khatma.secure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** P102 QA-only administration; all actual permission checks remain server-side. */
@Composable
fun P102ManagementPanel(
    token: String, groupId: Long, members: List<SecureV2Api.MemberInfo>,
    leader: Boolean, onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var working by remember(groupId) { mutableLongStateOf(-1L) }
    var result by remember(groupId) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("إدارة القيادة والتوزيع", style = MaterialTheme.typography.titleMedium)
        Text("تعيين النائب من صلاحيات القائد. قبول العضو لا يوزّع جزءًا تلقائيًا.",
            style = MaterialTheme.typography.bodySmall)
        if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall)
        members.forEach { member ->
            var juz by remember(groupId, member.id) { mutableStateOf("") }
            fun act(command: () -> Unit) {
                if (working != -1L) return
                scope.launch {
                    working = member.id
                    result = ""
                    try {
                        withContext(Dispatchers.IO) { command() }
                        result = "تم حفظ التغيير"
                        onChanged()
                    } catch (e: Exception) {
                        result = e.message ?: "تعذر تنفيذ الإجراء. تحقق من الاتصال والصلاحيات."
                    } finally {
                        working = -1L
                    }
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val role = when (member.role) {
                        "leader" -> "القائد"
                        "assistant" -> "النائب"
                        else -> "عضو"
                    }
                    Text("${member.name} — $role", style = MaterialTheme.typography.titleSmall)
                    Text(when {
                        member.awaitingDistribution -> "بانتظار توزيع الجزء"
                        !member.readingEligible -> "غير مشارك في القراءة"
                        member.juz != null -> "الجزء الحالي: ${member.juz}"
                        else -> "المشاركة مفعلة؛ لا تكليف لهذا اليوم"
                    }, style = MaterialTheme.typography.bodySmall)
                    if (leader && member.role != "leader") {
                        OutlinedButton(enabled = working == -1L, onClick = {
                            act { SecureV2Api.p102SetDeputy(token, groupId, member.id,
                                member.role != "assistant") }
                        }) { Text(if (member.role == "assistant") "إلغاء تكليف النائب" else "تعيين نائب") }
                    }
                    if (member.role != "leader" || leader) {
                        OutlinedButton(enabled = working == -1L, onClick = {
                            act { SecureV2Api.p102SetReader(token, groupId, member.id,
                                !member.readingEligible) }
                        }) { Text(if (member.readingEligible) "إيقاف المشاركة" else "تفعيل المشاركة") }
                    }
                    if (member.readingEligible) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(juz, { value ->
                                if (value.length <= 2 && value.all(Char::isDigit)) juz = value
                            }, modifier = Modifier.weight(1f), singleLine = true,
                                label = { Text("الجزء 1–30") })
                            Button(enabled = working == -1L &&
                                    (juz.toIntOrNull()?.let { it in 1..30 } == true),
                                onClick = {
                                    val n = juz.toIntOrNull() ?: return@Button
                                    act { SecureV2Api.p102AssignToday(token, groupId, member.id, n) }
                                }) { Text("توزيع") }
                        }
                    }
                }
            }
        }
    }
}
