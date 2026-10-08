"""Safe, additive V2 chat action UI; no changes outside secure chat screen."""
from pathlib import Path
p = Path("app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt")
s = p.read_text(encoding="utf-8")

def replace(old, new):
    global s
    assert s.count(old) == 1, "Unsafe UI patch: " + old[:70]
    s = s.replace(old, new, 1)

replace('    var chatText by remember { mutableStateOf("") }',
'''    var chatText by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<Long?>(null) }
    var actionMenu by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<SecureV2Api.ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var pinTarget by remember { mutableStateOf<SecureV2Api.ChatMessage?>(null) }
    var pinMinutes by remember { mutableStateOf("60") }
    var notifyPin by remember { mutableStateOf(false) }
    var pinnedMessages by remember { mutableStateOf<List<String>>(emptyList()) }''')
replace('.onSuccess { chatMessages = it; chatError = "" }',
''' .onSuccess {
                    chatMessages = it
                    chatError = ""
                    pinnedMessages = runCatching {
                        withContext(Dispatchers.IO) { SecureV2Api.pinnedMessages(token, id) }
                    }.getOrDefault(emptyList())
                }''')
replace('    Scaffold(topBar = {',
'''    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("تعديل الرسالة") },
            text = { OutlinedTextField(editText, { editText = it.take(1000) },
                modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = {
                val target = editTarget ?: return@TextButton
                runTask {
                    withContext(Dispatchers.IO) {
                        SecureV2Api.editMessage(session!!.token, chatGroupId!!,
                            target.id, editText)
                        chatMessages = SecureV2Api.messages(session!!.token, chatGroupId!!)
                    }
                    editTarget = null
                }
            }, enabled = !busy && editText.isNotBlank()) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("إلغاء") } }
        )
    }
    if (pinTarget != null) {
        AlertDialog(
            onDismissRequest = { pinTarget = null },
            title = { Text("مدة تثبيت الرسالة") },
            text = {
                Column {
                    OutlinedTextField(pinMinutes, { pinMinutes = it.filter(Char::isDigit).take(6) },
                        label = { Text("عدد الدقائق") })
                    Row {
                        Checkbox(notifyPin, { notifyPin = it })
                        Text("إشعار الجميع")
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val target = pinTarget ?: return@TextButton
                runTask {
                    val duration = pinMinutes.toIntOrNull() ?: 0
                    check(duration in 1..525600) { "مدة تثبيت غير صحيحة" }
                    withContext(Dispatchers.IO) {
                        SecureV2Api.pinMessage(session!!.token, chatGroupId!!,
                            target.id, duration, notifyPin)
                        pinnedMessages = SecureV2Api.pinnedMessages(session!!.token, chatGroupId!!)
                    }
                    pinTarget = null
                }
            }, enabled = !busy) { Text("تثبيت") } },
            dismissButton = { TextButton(onClick = { pinTarget = null }) { Text("إلغاء") } }
        )
    }
    Scaffold(topBar = {''')
start = s.index('                                    if (chatMessages.isEmpty()) Text("لا توجد رسائل بعد")')
end = s.index('                                    OutlinedTextField(chatText,', start)
replacement = '''                                    if (pinnedMessages.isNotEmpty()) {
                                        Text("الرسائل المثبّتة", style = MaterialTheme.typography.titleSmall)
                                        pinnedMessages.forEach { pin -> Text("📌 " + pin) }
                                    }
                                    if (chatMessages.isEmpty()) Text("لا توجد رسائل بعد")
                                    chatMessages.forEach { chat ->
                                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(9.dp)) {
                                                Text(chat.senderName, style = MaterialTheme.typography.labelLarge)
                                                if (chat.replyToId != null) {
                                                    Text("رد على رسالة " + chat.replyToId)
                                                }
                                                Text(chat.message)
                                                if (chat.edited) Text("تم التعديل",
                                                    style = MaterialTheme.typography.labelSmall)
                                                if (chat.reactionText.isNotEmpty()) Text(chat.reactionText)
                                                Row {
                                                    TextButton(onClick = { replyToId = chat.id }) { Text("رد") }
                                                    Box {
                                                        TextButton(onClick = { actionMenu = chat.id }) { Text("•••") }
                                                        DropdownMenu(expanded = actionMenu == chat.id,
                                                            onDismissRequest = { actionMenu = null }) {
                                                            DropdownMenuItem(text = { Text("❤️ تفاعل") },
                                                                onClick = {
                                                                    actionMenu = null
                                                                    runTask {
                                                                        withContext(Dispatchers.IO) {
                                                                            SecureV2Api.reactMessage(active.token,
                                                                                item.groupId, chat.id, "❤️")
                                                                            chatMessages = SecureV2Api.messages(active.token, item.groupId)
                                                                        }
                                                                    }
                                                                })
                                                            if (chat.senderId == active.userId) {
                                                                DropdownMenuItem(text = { Text("تعديل") },
                                                                    onClick = {
                                                                        editText = chat.message
                                                                        editTarget = chat
                                                                        actionMenu = null
                                                                    })
                                                            }
                                                            DropdownMenuItem(text = { Text("حذف عندي") },
                                                                onClick = {
                                                                    actionMenu = null
                                                                    runTask {
                                                                        withContext(Dispatchers.IO) {
                                                                            SecureV2Api.deleteMessage(active.token,
                                                                                item.groupId, chat.id, false)
                                                                            chatMessages = SecureV2Api.messages(active.token, item.groupId)
                                                                        }
                                                                    }
                                                                })
                                                            if (chat.senderId == active.userId || item.role == "leader") {
                                                                DropdownMenuItem(text = { Text("حذف عند الجميع") },
                                                                    onClick = {
                                                                        actionMenu = null
                                                                        runTask {
                                                                            withContext(Dispatchers.IO) {
                                                                                SecureV2Api.deleteMessage(active.token,
                                                                                    item.groupId, chat.id, true)
                                                                                chatMessages = SecureV2Api.messages(active.token, item.groupId)
                                                                            }
                                                                        }
                                                                    })
                                                            }
                                                            if (item.role == "leader") {
                                                                DropdownMenuItem(text = { Text("تثبيت") },
                                                                    onClick = {
                                                                        actionMenu = null
                                                                        pinTarget = chat
                                                                    })
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (replyToId != null) TextButton(onClick = { replyToId = null }) {
                                        Text("الرد على الرسالة " + replyToId + " (إلغاء)")
                                    }
'''
s = s[:start] + replacement + s[end:]
replace('SecureV2Api.sendMessage(active.token, item.groupId, sending)',
        'SecureV2Api.sendMessage(active.token, item.groupId, sending, replyToId)')
replace('                                            chatText = ""\n                                            chatMessages = withContext(Dispatchers.IO) {',
        '                                            chatText = ""\n                                            replyToId = null\n                                            chatMessages = withContext(Dispatchers.IO) {')
p.write_text(s, encoding="utf-8")
print("PASS: Android chat actions, reactions, reply, delete, edit and time-limited pins.")
