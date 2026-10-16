package com.ab.khatma.secure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** P68: every member of a khatma can see who else is in it (names, juz and whether the juz is done),
 * and can message a member in the group chat (the message starts with «@name»). Names only: no phone, no e-mail. */
@Composable
fun P68MemberList(
    details: SecureV2Api.GroupInfo?,
    onRetry: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("أعضاء الختمة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF064D3B))
        if (details == null) {
            Text("تعذّر تحميل الأعضاء. تأكد من الإنترنت وحاول مرة أخرى.", color = Color(0xFFB3261E), fontSize = 13.sp)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF064D3B))) { Text("إعادة المحاولة") }
            return@Column
        }
        Text("${details.members.size} عضو", color = Color.Gray, fontSize = 12.sp)
        OutlinedTextField(
            value = search, onValueChange = { search = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث عن عضو", fontSize = 12.sp) }, shape = RoundedCornerShape(12.dp),
        )
        details.members.filter { it.name.contains(search.trim(), ignoreCase = true) }.forEach { m ->
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE6DCC2)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (m.juz != null) "الجزء ${m.juz}" else "بدون جزء") + "  •  " + (if (m.completed) "أتمّ ✓" else "قيد القراءة"),
                            color = Color(0xFF587D68), fontSize = 12.sp,
                        )
                    }
                    OutlinedButton(onClick = { onMessage(m.name) }, shape = RoundedCornerShape(12.dp)) { Text("💬 راسله", fontSize = 12.sp) }
                }
            }
        }
        Text(
            "«راسله» يفتح محادثة الختمة ويبدأ الرسالة باسمه، فيراها كل الأعضاء.",
            color = Color.Gray, fontSize = 11.sp,
        )
    }
}
