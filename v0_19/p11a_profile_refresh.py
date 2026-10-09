"""Safe P11A profile and group refresh changes."""
from pathlib import Path
root=Path("app/src/main/java/com/ab/khatma/secure")
def edit(filename, old, new):
    p=Path(filename); s=p.read_text(encoding="utf-8")
    assert s.count(old)==1, "P11A unsafe anchor: "+str(p)
    p.write_text(s.replace(old,new,1),encoding="utf-8")
api=root/"SecureV2Api.kt"
edit(api,'    fun checkSession(token: String): Boolean =',
'''    fun myDisplayName(token: String): String =
        JSONObject(request("/auth/me", token)).getString("name")
    fun updateDisplayName(token: String, newName: String): String =
        JSONObject(request("/auth/profile", token, "PUT",
            JSONObject().put("name", newName.trim()))).getString("name")

    fun checkSession(token: String): Boolean =''')
p=root/"KhatmaPolishedPanels.kt"
edit(p,'''    groupCount: Int,
    onNewGroup''','''    groupCount: Int,
    groupsLoaded: Boolean,
    onNewGroup''')
edit(p,'''        Text("مجموعاتي (" + groupCount + ")",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (groupCount == 0) {''','''        Text(if (groupsLoaded) "مجموعاتي (" + groupCount + ")" else "مجموعاتي — جاري التحميل",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (groupsLoaded && groupCount == 0) {''')
edit(p,'''fun KhatmaPolishedProfile(userName: String, id: Long,
                          onCopyId: () -> Unit, onLogout: () -> Unit) {
    Column''','''fun KhatmaPolishedProfile(userName: String, id: Long,
                          onCopyId: () -> Unit, onLogout: () -> Unit,
                          busy: Boolean, onSaveName: (String) -> Unit) {
    var editedName by remember(userName) { mutableStateOf(userName) }
    Column''')
edit(p,'''                Text(userName, style = MaterialTheme.typography.titleLarge)
                Text("رقم الحساب: " + id)''','''                Text("الاسم الظاهر في ختمة", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = khatmaGreen)
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it.take(80) },
                    label = { Text("اسمك داخل التطبيق") },
                    supportingText = { Text("يظهر في مجموعاتك والشات دون تغيير حساب Google") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Button(onClick = { onSaveName(editedName.trim()) },
                    enabled = !busy && editedName.trim().length in 2..80 &&
                              editedName.trim() != userName,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = khatmaGreen)) {
                    Text("حفظ الاسم")
                }
                HorizontalDivider()
                Text("رقم الحساب: " + id)''')
u=root/"SecureV2Screen.kt"
edit(u,'''    fun reload() = runTask {
        val s = session ?: return@runTask
        groups = withContext(Dispatchers.IO) { SecureV2Api.today(s.token) }
    }''','''    fun reload() = runTask {
        val s = session ?: return@runTask
        val fresh = withContext(Dispatchers.IO) { SecureV2Api.today(s.token) }
        groups = fresh
        groupsLoaded = true
    }''')
edit(u,'''    var groups by remember { mutableStateOf<List<SecureV2Api.TodayItem>>(emptyList()) }''',
'''    var groups by remember { mutableStateOf<List<SecureV2Api.TodayItem>>(emptyList()) }
    var groupsLoaded by remember { mutableStateOf(false) }''')
edit(u,'''    LaunchedEffect(session?.token) {
        if (session != null) {
            SecureReminderWorker.schedule(context)
            runCatching { withContext(Dispatchers.IO) { SecureV2Api.today(session!!.token) } }
                .onSuccess { groups = it }
                .onFailure { message = "تعذر تحميل المجموعات. تحقق من الاتصال أو صلاحية الجلسة." }
        }
    }''','''    LaunchedEffect(session?.token) {
        val current = session ?: return@LaunchedEffect
        SecureReminderWorker.schedule(context)
        runCatching { withContext(Dispatchers.IO) { SecureV2Api.myDisplayName(current.token) } }
            .onSuccess { freshName ->
                if (session?.token == current.token && freshName.isNotBlank() &&
                    freshName != session?.userName) {
                    val updated = current.copy(userName = freshName)
                    store.save(updated)
                    session = updated
                }
            }
    }
    LaunchedEffect(session?.token, section) {
        val current = session ?: return@LaunchedEffect
        if (section != "home") return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { SecureV2Api.today(current.token) } }
            .onSuccess {
                if (session?.token == current.token) {
                    groups = it
                    groupsLoaded = true
                }
            }
            .onFailure { message = "تعذر تحديث المجموعات. احتفظنا بآخر قائمة؛ اضغط تحديث." }
    }''')
edit(u,'''                            groupCount = groups.size,
                            onNewGroup''','''                            groupCount = groups.size,
                            groupsLoaded = groupsLoaded,
                            onNewGroup''')
edit(u,'''                        lastInvitation?.let { invitation ->''','''                        OutlinedButton(onClick = { reload() }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth()) { Text("تحديث المجموعات") }
                        lastInvitation?.let { invitation ->''')
edit(u,'''                            onLogout = {
                                SecureReminderWorker.cancel''','''                            busy = busy,
                            onSaveName = { proposed ->
                                runTask {
                                    val updated = withContext(Dispatchers.IO) {
                                        SecureV2Api.updateDisplayName(active.token, proposed)
                                    }
                                    val renewed = active.copy(userName = updated)
                                    store.save(renewed)
                                    session = renewed
                                    message = "تم حفظ الاسم الظاهر"
                                }
                            },
                            onLogout = {
                                SecureReminderWorker.cancel''')
edit(u,'''                                groups = emptyList()
                                groupInfo = null''','''                                groups = emptyList()
                                groupsLoaded = false
                                groupInfo = null''')
print("P11A PASS: authenticated profile save and group loading state. Data unchanged.")
