"""Safe UI organization: preserve V2 API, no changes to server or personal data."""
from pathlib import Path

p = Path("app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt")
s = p.read_text(encoding="utf-8")

def exact(old, new):
    global s
    if s.count(old) != 1:
        raise SystemExit("FAIL: unexpected V2 UI anchor: " + old[:100])
    s = s.replace(old, new, 1)

exact("import androidx.compose.ui.Modifier\n",
      "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalLayoutDirection\nimport androidx.compose.ui.unit.LayoutDirection\n")
exact("    var chatError by remember { mutableStateOf(\"\") }\n",
      "    var chatError by remember { mutableStateOf(\"\") }\n"
      "    var section by remember { mutableStateOf(\"home\") }\n"
      "    var selectedGroupId by remember { mutableLongStateOf(0L) }\n")
exact("Text(\"ختمة — الحساب الآمن التجريبي\")",
      "Text(when (section) { \"group\" -> \"تفاصيل المجموعة\"; \"create\" -> \"مجموعة جديدة\"; \"join\" -> \"انضمام لمجموعة\"; else -> \"ختمة\" })")
exact("TextButton(onClick = onBack) { Text(\"رجوع\") }",
      "if (session != null && section != \"home\") TextButton(onClick = { section = \"home\"; chatGroupId = null }) { Text(\"رجوع\") }")
exact("    Scaffold(topBar = {", "    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {\n    Scaffold(topBar = {")
assert s.endswith("    }\n}\n")
s = s[:-2] + "    }\n}\n" # close CompositionLocalProvider, then SecureV2Screen
exact("                Text(\"هذه تجربة حساب جديد مستقلة. حسابك وختماتك القديمة لن يتم نقلها أو حذفها.\",\n                    color = MaterialTheme.colorScheme.onSurfaceVariant)",
      "                if (session == null) {\n"
      "                    Text(\"أهلًا بك في ختمة — قراءتك ومجموعاتك في مكان واحد.\",\n"
      "                        style = MaterialTheme.typography.titleLarge)\n"
      "                    Text(\"سجّل حسابًا آمنًا لتبدأ رحلتك مع القرآن.\",\n"
      "                        color = MaterialTheme.colorScheme.onSurfaceVariant)\n"
      "                }")
start = s.index('                item {\n                    Text("مرحبًا')
stop = s.index('                    Text("إنشاء مجموعة جديدة"', start)
replacement = """                item {
                    if (section == "home") {
                        Text("السلام عليكم، " + active.userName,
                            style = MaterialTheme.typography.headlineSmall)
                        Text("مجموعاتي (" + groups.size + ")",
                            style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { section = "create" }, modifier = Modifier.weight(1f)) { Text("إنشاء مجموعة") }
                            OutlinedButton(onClick = { section = "join" }, modifier = Modifier.weight(1f)) { Text("انضمام بكود") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { onOpenQuran(null) }) { Text("المصحف") }
                            OutlinedButton(onClick = { reload() }, enabled = !busy) { Text("تحديث") }
                            TextButton(onClick = {
                                SecureReminderWorker.cancel(context)
                                store.clear()
                                session = null
                                groups = emptyList()
                                groupInfo = null
                                pending = emptyList()
                                chatGroupId = null
                                chatMessages = emptyList()
                                lastInvitation = null
                                section = "home"
                            }) { Text("خروج") }
                        }
                        lastInvitation?.let { (title, code) ->
                            Text("كود المجموعة الجديدة: " + code)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { copyCode(code) }) { Text("نسخ الكود") }
                                Button(onClick = { shareCode(title, code) }) { Text("مشاركة الدعوة") }
                            }
                        }
                        if (groups.isEmpty()) Text("لا توجد مجموعات بعد. أنشئ مجموعة أو انضم بكود دعوة.")
                    } else if (section == "create") {
"""
s = s[:start] + replacement + s[stop:]
exact('                            groupName = ""\n                            message = "تم إنشاء المجموعة. كود الدعوة: $code"',
      '                            groupName = ""\n                            section = "home"\n                            message = "تم إنشاء المجموعة. كود الدعوة: $code"')
exact('                    OutlinedTextField(inviteCode, { inviteCode = it.uppercase() },',
      '                    } else if (section == "join") {\n'
      '                    Text("أدخل كود الدعوة؛ يتطلب الانضمام موافقة القائد.", style = MaterialTheme.typography.titleMedium)\n'
      '                    OutlinedTextField(inviteCode, { inviteCode = it.uppercase() },')
exact('                            groups = withContext(Dispatchers.IO) { SecureV2Api.today(active.token) }\n'
      '                            message = if (status == "pending")',
      '                            groups = withContext(Dispatchers.IO) { SecureV2Api.today(active.token) }\n'
      '                            section = "home"\n'
      '                            message = if (status == "pending")')
exact('                    OutlinedButton(onClick = { onOpenQuran(null) }) { Text("تصفح المصحف") }\n'
      '                    Text("ختماتي اليومية", style = MaterialTheme.typography.titleLarge)',
      '                    }')
exact('                items(groups, key = { it.groupId }) { item ->',
      '                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->')
exact('                            OutlinedButton(onClick = { onOpenQuran(item.juz) }) { Text("قراءة الجزء في المصحف") }',
      '                            if (section == "home") {\n'
      '                                Button(onClick = { selectedGroupId = item.groupId; section = "group"; groupInfo = null; chatGroupId = null; planGroupId = null }) {\n'
      '                                    Text(if (item.status == "active") "فتح المجموعة" else "متابعة طلب الانضمام")\n'
      '                                }\n'
      '                            } else {\n'
      '                            OutlinedButton(onClick = { onOpenQuran(item.juz) }) { Text("قراءة الجزء في المصحف") }')
suffix = '                            }\n                        }\n                    }\n                }\n            }\n        }\n    }\n    }\n}\n'
if not s.endswith(suffix):
    raise SystemExit("FAIL: unexpected group-card ending after applying UI changes")
s = s[:-len(suffix)] + '                            }\n                            } // detail-only actions\n                        }\n                    }\n                }\n            }\n        }\n    }\n    }\n}\n'
exact('if (item.role == "leader" || item.role == "assistant") {',
      'if (item.status == "active" && (item.role == "leader" || item.role == "assistant")) {')
exact('if (item.status == "active") {\n                                OutlinedButton(onClick = {\n                                    if (chatGroupId',
      'if (item.status == "active") {\n                                OutlinedButton(onClick = {\n                                    if (chatGroupId')
# Keep screen navigation explicit and stable across recreation and sessions.
p.write_text(s, encoding="utf-8")
assert "الحساب الآمن التجريبي" not in s
assert "section == \"group\"" in s
assert "section = \"create\"" in s
assert "CompositionLocalProvider" in s
print("PASS: V2 integrated home/group/create/join navigation, RTL, group privacy gates.")
