"""V0.18 presentation-only migration. Fail closed if the baseline changes."""
from pathlib import Path
import os, shutil
root = Path("app/src/main/java/com/ab/khatma/secure")
path = root / "SecureV2Screen.kt"
s = path.read_text(encoding="utf-8")
art = Path(os.environ["GITHUB_WORKSPACE"]) / "v0_18/KhatmaPolishedPanels.kt"
assert art.is_file() and not (root / "KhatmaPolishedPanels.kt").exists()
shutil.copyfile(art, root / "KhatmaPolishedPanels.kt")

def replace(old, new):
    global s
    assert s.count(old) == 1, "V0.18 unexpected anchor: " + old[:75]
    s = s.replace(old, new, 1)

replace('    var selectedGroupId by remember { mutableLongStateOf(0L) }',
        '    var selectedGroupId by remember { mutableLongStateOf(0L) }\n'
        '    var groupTab by remember { mutableStateOf("read") }\n')

a=s.index('    Scaffold(topBar = {')
b=s.index('        LazyColumn(', a)
s=s[:a]+'''    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("خَتْمَة", style = MaterialTheme.typography.titleLarge)
                        if (session != null) {
                            Text(when (section) {
                                "group" -> "تفاصيل المجموعة"
                                "create" -> "مجموعة جديدة"
                                "join" -> "الانضمام لمجموعة"
                                "plans" -> "ختمات الصفحات"
                                "profile" -> "الملف الشخصي"
                                else -> "مجموعاتي"
                            }, style = MaterialTheme.typography.bodySmall,
                               color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    if (session != null && section != "home") {
                        TextButton(onClick = {
                            section = if (section == "plans") "group" else "home"
                            groupTab = "read"
                            chatGroupId = null
                        }) { Text("رجوع") }
                    }
                }
            )
        },
        bottomBar = {
            if (session != null && (section == "home" || section == "profile")) {
                NavigationBar {
                    NavigationBarItem(
                        selected = section == "home",
                        onClick = { section = "home"; chatGroupId = null },
                        icon = { Text("⌂") }, label = { Text("الرئيسية") }
                    )
                    NavigationBarItem(
                        selected = false, onClick = { onOpenQuran(null) },
                        icon = { Text("۞") }, label = { Text("المصحف") }
                    )
                    NavigationBarItem(
                        selected = section == "profile",
                        onClick = { section = "profile"; chatGroupId = null },
                        icon = { Text("●") }, label = { Text("حسابي") }
                    )
                }
            }
        }
    ) { padding ->
'''+s[b:]

a=s.index('            if (session == null) {\n                item {\n                    Row(horizontalArrangement')
b=s.index('            } else {\n                val active = session!!', a)
s=s[:a]+'''            if (session == null) {
                item {
                    KhatmaPolishedLogin(
                        createAccount = createAccount,
                        onModeChange = { createAccount = it; message = "" },
                        name = name, onNameChange = { name = it },
                        accountId = idField, onAccountIdChange = { idField = it },
                        password = password, onPasswordChange = { password = it },
                        busy = busy,
                        onSubmit = {
                            runTask {
                                val account = withContext(Dispatchers.IO) {
                                    if (createAccount) SecureV2Api.register(name, password)
                                    else SecureV2Api.login(idField.toLong(), password)
                                }
                                store.save(account)
                                SecureReminderWorker.schedule(context)
                                session = account
                                password = ""
                                section = "home"
                                message = if (createAccount)
                                    "تم إنشاء حسابك. رقم الحساب: " + account.userId + " — احتفظ به."
                                else "مرحبًا بعودتك"
                            }
                        }
                    )
                }
'''+s[b:]

a=s.index('                    if (section == "home") {\n                        Text("السلام عليكم')
b=s.index('                    } else if (section == "create") {',a)
s=s[:a]+'''                    if (section == "home") {
                        KhatmaPolishedHomeHeader(
                            userName = active.userName,
                            groupCount = groups.size,
                            onNewGroup = { section = "create"; message = "" },
                            onJoinGroup = { section = "join"; message = "" },
                            onOpenMushaf = { onOpenQuran(null) }
                        )
                        lastInvitation?.let { invitation ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("تم إنشاء مجموعة " + invitation.first,
                                        style = MaterialTheme.typography.titleMedium)
                                    Text("كود الدعوة: " + invitation.second)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { copyCode(invitation.second) }) {
                                            Text("نسخ")
                                        }
                                        Button(onClick = {
                                            shareCode(invitation.first, invitation.second)
                                        }) { Text("مشاركة الدعوة") }
                                    }
                                }
                            }
                        }
'''+s[b:]

replace('''                    }, enabled = !busy && inviteCode.trim().length >= 4) { Text("انضمام") }
                    }
                }''',
'''                    }, enabled = !busy && inviteCode.trim().length >= 4) { Text("انضمام") }
                    } else if (section == "profile") {
                        KhatmaPolishedProfile(
                            userName = active.userName,
                            id = active.userId,
                            onCopyId = {
                                copyCode(active.userId.toString())
                                message = "تم نسخ رقم الحساب"
                            },
                            onLogout = {
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
                                message = ""
                            }
                        )
                    }
                }''')

replace('''                item {
                    if (section == "plans") {''',
'''                item {
                    if (section == "group") {
                        val chosen = groups.firstOrNull { it.groupId == selectedGroupId }
                        if (chosen != null && chosen.status == "active") {
                            KhatmaPolishedGroupNavigation(
                                selected = groupTab,
                                canManage = chosen.role == "leader" || chosen.role == "assistant",
                                onSelect = { tab ->
                                    groupTab = tab
                                    chatGroupId = if (tab == "chat") chosen.groupId else null
                                    if (tab == "chat") {
                                        chatMessages = emptyList()
                                        chatError = ""
                                    }
                                }
                            )
                        }
                    }
                    if (section == "plans") {''')

replace('''                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {''',
'''                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->
                    if (section == "home") {
                        KhatmaPolishedGroupCard(item) {
                            selectedGroupId = item.groupId
                            section = "group"
                            groupTab = "read"
                            groupInfo = null
                            chatGroupId = null
                            message = ""
                        }
                    } else {
                    Card(modifier = Modifier.fillMaxWidth()) {''')

replace('''                            } // detail-only actions
                        }
                    }
                }''',
'''                            } // detail-only actions
                        }
                    }
                    } // grouped details, not dashboard
                }''')

replace('''                            if (item.status == "active") {
                                Button(onClick = { section = "plans"; chatGroupId = null })''',
'''                            if (groupTab == "read" && item.status == "active") {
                                Button(onClick = { section = "plans"; chatGroupId = null })''')

replace('''                            OutlinedButton(onClick = { onOpenQuran(item.juz) }) { Text("قراءة الجزء في المصحف") }''',
'''                            if (groupTab == "read") {
                            OutlinedButton(onClick = { onOpenQuran(item.juz) }) { Text("قراءة الجزء في المصحف") }''')

replace('''                            } else if (item.completed) Text("✓ تمت القراءة")
                            if (item.status == "active") {''',
'''                            } else if (item.completed) Text("✓ تمت القراءة")
                            } // existing full-juz actions remain on reading tab
                            if (item.status == "active" && groupTab == "chat") {''')

# Existing group chat section keeps its state + all actions. Open automatically from tab.
start=s.index('''                                OutlinedButton(onClick = {
                                    if (chatGroupId == item.groupId) {''')
end=s.index('''                                if (chatGroupId == item.groupId) {\n                                    Text(''',start)
s=s[:start]+'''                                Text("محادثة المجموعة",
                                    style = MaterialTheme.typography.titleMedium)
'''+s[end:]

replace('''                            if (item.status == "active" && (item.role == "leader" || item.role == "assistant")) {''',
'''                            if (groupTab == "leader" && item.status == "active" &&
                                (item.role == "leader" || item.role == "assistant")) {''')

# Translate raw API labels in group summary, without changing service values.
lines=s.splitlines(keepends=True)
for idx,line in enumerate(lines):
    if 'Text("الحالة:' in line and 'قراءات الأعضاء اليوم' in line:
        lines[idx]='                            Text(if (item.status == "active") "المجموعة نشطة" else "بانتظار موافقة القائد")\n'
    if 'Text("الجزء:' in line and 'لم يُحدد' in line:
        lines[idx]='                            if (groupTab == "read") Text("الجزء القديم: " + (item.juz?.toString() ?: "غير محدد"))\n'
s=''.join(lines)
assert 'KhatmaPolishedLogin(' in s and 'KhatmaPolishedGroupCard(' in s
assert 'KhatmaPolishedGroupNavigation(' in s
path.write_text(s, encoding="utf-8")

gradle=Path("app/build.gradle.kts")
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 20")==1 and g.count('"0.17.1-beta"')==1
gradle.write_text(g.replace("versionCode = 20","versionCode = 21")
                    .replace('"0.17.1-beta"','"0.18.0-ui-beta"'),encoding="utf-8")
verify=Path("tools/verify_project.py")
v=verify.read_text(encoding="utf-8")
assert "versionCode 20" in v and "0.17.1-beta" in v
verify.write_text(v.replace("versionCode 20", "versionCode 21")
                   .replace("versionCode = 20", "versionCode = 21")
                   .replace("0.17.1-beta", "0.18.0-ui-beta"),encoding="utf-8")
print("PASS: polished UI, separated sign in/home/group/account with no V2 API change")
