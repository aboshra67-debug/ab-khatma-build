package com.ab.khatma.secure

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MGreen = Color(0xFF075541)
private val MBorder = Color(0xFFE8E0CE)
private val MRed = Color(0xFFB3261E)

/** Archived khatmas are only hidden on this phone. Nothing changes on the server. */
internal object P56Archive {
    private const val PREFS = "p56_archive"
    fun load(context: Context, userId: Long): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("ids_$userId", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun save(context: Context, userId: Long, ids: Set<Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("ids_$userId", ids.map { it.toString() }.toSet()).apply()
    }
}

@Composable
internal fun P56GroupMenu(item: SecureV2Api.TodayItem, onArchive: () -> Unit, onLeaveOrClose: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val leader = item.role == "leader" && item.status == "active"
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, "خيارات الختمة", tint = MGreen)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("📦 أرشفة (إخفاء من القائمة)") }, onClick = { open = false; onArchive() })
            DropdownMenuItem(
                text = { Text(if (leader) "🗑 حذف الختمة" else "🚪 خروج من الختمة", color = MRed) },
                onClick = { open = false; onLeaveOrClose() },
            )
        }
    }
}

/** Confirms leaving (members) or closing (leader). [onConfirm] gets the khatma and whether it is a close. */
@Composable
internal fun P56ManageDialog(
    target: Pair<SecureV2Api.TodayItem, Boolean>?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (SecureV2Api.TodayItem, Boolean) -> Unit,
) {
    val (item, close) = target ?: return
    var word by remember(item.groupId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (close) "حذف الختمة «${item.groupName}»" else "الخروج من «${item.groupName}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (close) {
                    Text("هتتقفل الختمة لكل الأعضاء، ومش هيقدر حد يدخلها تاني بكود الدعوة.")
                    Text("سجل القراءة بيفضل محفوظ على السيرفر، لكن الختمة بتختفي من كل القوايم.",
                        color = Color(0xFF746D62), fontSize = 13.sp)
                    OutlinedTextField(value = word, onValueChange = { word = it.take(10) },
                        label = { Text("اكتب «حذف» للتأكيد") }, singleLine = true)
                } else {
                    Text("متأكد إنك عايز تخرج من الختمة؟ الجزء اللي لسه ماقريتوش هيتشال من جدولك.")
                    Text("تقدر تنضم تاني بكود الدعوة لو القائد وافق.", color = Color(0xFF746D62), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (!close || word.trim() == "حذف"), onClick = { onConfirm(item, close) }) {
                Text(if (close) "حذف الختمة" else "خروج", color = MRed)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
internal fun P56ArchivePage(archived: List<SecureV2Api.TodayItem>, onRestore: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الختمات المؤرشفة", color = MGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("مخفية من القوايم على هذا الموبايل فقط. بياناتها محفوظة وتقدر ترجّعها في أي وقت.",
            color = Color(0xFF746D62), fontSize = 13.sp)
        if (archived.isEmpty()) {
            P25Panel { Text("مفيش ختمات مؤرشفة. من قايمة الختمات اضغط ⋮ ثم «أرشفة».") }
        }
        archived.forEach { item ->
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, MBorder)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.groupName, fontWeight = FontWeight.Bold, color = Color(0xFF063D30),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (item.role == "leader") "قائد المجموعة" else "عضو", color = Color(0xFF746D62), fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { onRestore(item.groupId) }, shape = RoundedCornerShape(12.dp)) { Text("استرجاع") }
                }
            }
        }
    }
}
