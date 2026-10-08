from pathlib import Path
import os
import shutil

root = Path("app/src/main/java/com/ab/khatma")
p = root / "secure/SecureV2Screen.kt"
s = p.read_text(encoding="utf-8")

def replace_once(a,b):
    global s
    assert s.count(a)==1, "Patch anchor count != 1: " + a[:70]
    s=s.replace(a,b,1)

ui=Path(os.environ["GITHUB_WORKSPACE"])/"v0_18/PremiumKhatmaUi.kt"
target=root/"secure/PremiumKhatmaUi.kt"
assert ui.exists() and not target.exists()
shutil.copyfile(ui,target)
replace_once("import androidx.compose.foundation.layout.*\n",
             "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.ui.text.font.FontWeight\n")
replace_once('    var section by remember { mutableStateOf("home") }',
'''    var section by remember { mutableStateOf("home") }
    var showNewAccountId by remember { mutableStateOf<Long?>(null) }''')
replace_once("    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {\n",
'''    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    PremiumKhatmaTheme {
    if (showNewAccountId != null) AlertDialog(
        onDismissRequest = { showNewAccountId = null },
        title = { Text("تم إنشاء حسابك ✓") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("رقم عضويتك في ختمة:")
                Text(showNewAccountId.toString(), style = MaterialTheme.typography.headlineMedium)
                Text("احتفظ بهذا الرقم للدخول من جهاز آخر إلى أن تتوفر المصادقة بالهاتف وGoogle.")
            }
        },
        confirmButton = {
            TextButton(onClick = { showNewAccountId = null }) { Text("تمام") }
        },
        dismissButton = {
            TextButton(onClick = {
                copyCode(showNewAccountId.toString())
                showNewAccountId = null
            }) { Text("نسخ الرقم") }
        }
    )
''')

a=s.index('    Scaffold(topBar = {')
b=s.index('    }) { padding ->',a)+len('    }) { padding ->')
s=s[:a]+'''    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (session != null) TopAppBar(
                title = {
                    Column {
                        Text(when (section) {
                            "group" -> "تفاصيل المجموعة"
                            "plans" -> "خطة القراءة"
                            "create" -> "مجموعة جديدة"
                            "join" -> "طلب الانضمام"
                            "profile" -> "حسابي"
                            else -> "ختمة"
                        }, fontWeight = FontWeight.Bold)
                        Text("نقرأ معًا.. ونختم معًا",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (section != "home" && section != "profile")
                        TextButton(onClick = {
                            section = if (section == "plans") "group" else "home"
                            chatGroupId = null
                        }) { Text("رجوع") }
                },
                actions = {
                    if (section == "home")
                        TextButton(onClick = { reload() }, enabled = !busy) { Text("تحديث") }
                }
            )
        },
        bottomBar = {
            if (session != null && section in listOf("home","profile")) {
                NavigationBar {
                    NavigationBarItem(
                        selected = section == "home",
                        onClick = { section = "home"; chatGroupId = null },
                        icon = { Text("⌂") }, label = { Text("الرئيسية") })
                    NavigationBarItem(
                        selected = false, onClick = { onOpenQuran(null) },
                        icon = { Text("۞") }, label = { Text("المصحف") })
                    NavigationBarItem(
                        selected = section == "profile",
                        onClick = { section = "profile"; chatGroupId = null },
                        icon = { Text("◉") }, label = { Text("حسابي") })
                }
            }
        }
    ) { padding ->'''+s[b:]
replace_once('''                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)''',
'''                if (message.isNotBlank()) Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(message.take(160), modifier = Modifier.fillMaxWidth().padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }''')

a=s.index('            if (session == null) {\n                item {\n                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp))')
b=s.index('            } else {\n                val active = session!!',a)
s=s[:a]+'''            if (session == null) {
                item {
                    PremiumLoginPanel(
                        isRegister = createAccount,
                        onRegisterMode = { createAccount = it; message = "" },
                        name = name,
                        onName = { name = it },
                        accountId = idField,
                        onAccountId = { idField = it.filter(Char::isDigit) },
                        password = password,
                        onPassword = { password = it },
                        busy = busy,
                        onSubmit = {
                            runTask {
                                val registration = createAccount
                                val account = withContext(Dispatchers.IO) {
                                    if (registration) SecureV2Api.register(name, password)
                                    else SecureV2Api.login(idField.toLong(), password)
                                }
                                store.save(account)
                                SecureReminderWorker.schedule(context)
                                session = account
                                password = ""
                                section = "home"
                                if (registration) showNewAccountId = account.userId
                            }
                        }
                    )
                }
'''+s[b:]

a=s.index('                    if (section == "home") {')
b=s.index('                    } else if (section == "create") {',a)
s=s[:a]+'''                    if (section == "home") {
                        PremiumHomeHeader(active.userName, groups.size)
                        Spacer(Modifier.height(12.dp))
                        PremiumHomeActions(
                            onCreate = { section = "create"; message = "" },
                            onJoin = { section = "join"; message = "" },
                            onQuran = { onOpenQuran(null) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("مجموعاتي", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        lastInvitation?.let { (title, code) ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("دعوة " + title, fontWeight = FontWeight.Bold)
                                    Text("كود الدعوة: " + code)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { copyCode(code) }) { Text("نسخ") }
                                        Button(onClick = { shareCode(title, code) }) { Text("مشاركة") }
                                    }
                                }
                            }
                        }
                        if (groups.isEmpty()) PremiumEmptyGroups()
                    } else if (section == "profile") {
                        ElevatedCard(Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("بيانات حسابك", style = MaterialTheme.typography.titleLarge)
                                Text("الاسم: " + active.userName)
                                Text("رقم العضوية: " + active.userId,
                                    style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { copyCode(active.userId.toString()) }) {
                                    Text("نسخ رقم العضوية")
                                }
                                Text("احتفظ برقم العضوية لتسجيل الدخول من جهاز آخر.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = {
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
                                }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
                            }
                        }
'''+s[b:]

replace_once('''                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {''',
'''                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->
                    if (section == "home") {
                        PremiumGroupTile(
                            groupName = item.groupName, role = item.role,
                            status = item.status, completed = item.doneCount,
                            total = item.totalCount,
                            onOpen = {
                                selectedGroupId = item.groupId
                                section = "group"
                                groupInfo = null
                                chatGroupId = null
                            }
                        )
                    } else {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp)) {''')
# Added one conditional requires one closing brace at end of item;
# new premium MaterialTheme requires a second brace at end of file.
old_tail='''                            } // detail-only actions
                        }
                    }
                }
            }
        }
    }
    }
}'''
s = s.rstrip()
assert s.endswith(old_tail), "Unrecognized layout nesting"
s=s[:-len(old_tail)]+'''                            } // detail-only actions
                        }
                    }
                    }
                }
            }
        }
    }
    }
    }
}'''
assert "PremiumLoginPanel(" in s and "PremiumGroupTile(" in s
assert "SecureV2Api.register(name, password)" in s
assert "SecureV2Api.complete(active.token, item.assignmentId)" in s
p.write_text(s,encoding="utf-8")
g=Path("app/build.gradle.kts")
v=g.read_text(encoding="utf-8")
assert v.count('versionCode = 20')==1 and v.count('versionName = "0.17.1-beta"')==1
g.write_text(v.replace('versionCode = 20','versionCode = 21')
  .replace('versionName = "0.17.1-beta"','versionName = "0.18.0-ui-beta"'),encoding="utf-8")
v=Path("tools/verify_project.py")
t=v.read_text(encoding="utf-8")
assert "versionCode 20" in t and "0.17.1-beta" in t
v.write_text(t.replace("versionCode 20","versionCode 21")
    .replace("versionCode = 20","versionCode = 21")
    .replace("0.17.1-beta","0.18.0-ui-beta"),encoding="utf-8")
print("PASS: V0.18 premium onboarding/home/profile/group navigation.")
