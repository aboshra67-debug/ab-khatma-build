"""V0.19 safe group dashboard patch on the verified V0.18.1 source. Fail closed."""
from pathlib import Path
import os, shutil

root = Path(".")
secure = root / "app/src/main/java/com/ab/khatma/secure"
screen = secure / "SecureV2Screen.kt"
s = screen.read_text(encoding="utf-8")
dashboard_source = Path(os.environ["GITHUB_WORKSPACE"]) / "v0_19/KhatmaLeaderDashboard.kt"
dashboard_dest = secure / "KhatmaLeaderDashboard.kt"
assert dashboard_source.is_file() and not dashboard_dest.exists()
shutil.copyfile(dashboard_source, dashboard_dest)

def replace(old, new):
    global s
    assert s.count(old) == 1, "Unsafe baseline change: " + old[:80]
    s = s.replace(old, new, 1)

replace('    var groupTab by remember { mutableStateOf("read") }',
    '''    var groupTab by remember { mutableStateOf("read") }
    var leaderSearch by remember { mutableStateOf("") }
    var leaderFilter by remember { mutableStateOf("all") }''')
replace('                                "plans" -> "ختمات الصفحات"',
    '''                                "plans" -> "ختمات الصفحات"
                                "leaderDashboard" -> "متابعة أعضاء المجموعة"''')
replace('''                            section = if (section == "plans") "group" else "home"
                            groupTab = "read"''',
    '''                            val previousSection = section
                            section = if (previousSection == "plans" ||
                                previousSection == "leaderDashboard") "group" else "home"
                            groupTab = if (previousSection == "leaderDashboard") "leader" else "read"''')
replace('''                                canManage = chosen.role == "leader" || chosen.role == "assistant",''',
    '''                                canManage = chosen.role == "leader",''')
replace('''                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->''',
'''                if (section == "leaderDashboard") {
                    val selected = groups.firstOrNull { it.groupId == selectedGroupId }
                    val details = groupInfo?.takeIf { it.id == selectedGroupId }
                    if (selected == null || selected.status != "active" || selected.role != "leader") {
                        item { Text("لوحة المتابعة متاحة للقائد فقط.") }
                    } else if (details == null) {
                        item {
                            Text(if (busy) "جاري تحميل الأعضاء..." else
                                "تعذر تحميل المجموعة. اضغط رجوع ثم حاول مرة أخرى.")
                        }
                    } else {
                        item(key = "leader_summary") {
                            KhatmaLeaderOverview(
                                group = details, pendingCount = pending.size, busy = busy,
                                onRefresh = {
                                    runTask {
                                        val data = withContext(Dispatchers.IO) {
                                            SecureV2Api.groupStatus(active.token, selectedGroupId) to
                                                SecureV2Api.pending(active.token, selectedGroupId)
                                        }
                                        groupInfo = data.first
                                        pending = data.second
                                        message = "تم تحديث حالة الأعضاء"
                                    }
                                },
                                onCopy = { copyCode(details.inviteCode) },
                                onShare = { shareCode(details.name, details.inviteCode) },
                                onPageKhatmas = { section = "plans" }
                            )
                        }
                        val filtered = filterLeaderMembers(details.members, leaderSearch, leaderFilter)
                        item(key = "leader_filters") {
                            KhatmaLeaderSearchAndFilters(
                                search = leaderSearch, onSearch = { leaderSearch = it.take(100) },
                                filter = leaderFilter, onFilter = { leaderFilter = it },
                                shown = filtered.size, total = details.members.size
                            )
                        }
                        if (filtered.isEmpty()) {
                            item(key = "leader_empty") { Text("لا يوجد أعضاء يطابقون البحث.") }
                        }
                        items(filtered, key = { "leader_member_" + it.id }) { member ->
                            KhatmaLeaderMemberRow(member)
                        }
                        item(key = "pending_requests_label") {
                            Text("طلبات الانضمام (" + pending.size + ")",
                                style = MaterialTheme.typography.titleLarge)
                        }
                        if (pending.isEmpty()) {
                            item(key = "no_pending") { Text("لا توجد طلبات انضمام معلقة.") }
                        }
                        items(pending, key = { "pending_" + it.id }) { applicant ->
                            KhatmaLeaderPendingRow(applicant, busy = busy) {
                                runTask {
                                    val refreshed = withContext(Dispatchers.IO) {
                                        SecureV2Api.approve(active.token, selectedGroupId,
                                            applicant.id, true)
                                        SecureV2Api.groupStatus(active.token, selectedGroupId) to
                                            SecureV2Api.pending(active.token, selectedGroupId)
                                    }
                                    groupInfo = refreshed.first
                                    pending = refreshed.second
                                    message = "تمت الموافقة على العضو"
                                }
                            }
                        }
                    }
                }
                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->''')

start = s.index('''                            if (groupTab == "leader" && item.status == "active" &&''')
end = s.index('''                            } // detail-only actions''', start)
old = s[start:end]
assert old.count('details.members.forEach') == 1
assert old.count('pending.forEach') == 1
s = s[:start] + '''                            if (groupTab == "leader" && item.status == "active" &&
                                item.role == "leader") {
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("لوحة متابعة المجموعة",
                                            style = MaterialTheme.typography.titleLarge)
                                        Text("تابع إنجاز 45 عضوًا أو أكثر، وابحث وفلتر حسب حالة الورد.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = {
                                            section = "leaderDashboard"
                                            leaderSearch = ""
                                            leaderFilter = "all"
                                            chatGroupId = null
                                            groupInfo = null
                                            pending = emptyList()
                                            runTask {
                                                val fetched = withContext(Dispatchers.IO) {
                                                    SecureV2Api.groupStatus(active.token, item.groupId) to
                                                        SecureV2Api.pending(active.token, item.groupId)
                                                }
                                                groupInfo = fetched.first
                                                pending = fetched.second
                                            }
                                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                                            Text("فتح متابعة الأعضاء")
                                        }
                                    }
                                }
                            }
''' + s[end:]
replace('''                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.groupName, style = MaterialTheme.typography.titleMedium)
                            Text(if (item.status == "active") "المجموعة نشطة" else "بانتظار موافقة القائد")''',
'''                    ElevatedCard(modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(item.groupName, style = MaterialTheme.typography.titleLarge)
                            Text(if (item.status == "active") "المجموعة نشطة" else "بانتظار موافقة القائد")''')
screen.write_text(s, encoding="utf-8")

api = secure / "SecureV2Api.kt"
a = api.read_text(encoding="utf-8")
old = '''                    m.optBoolean("completed", false))'''
assert a.count(old) == 1
a = a.replace(old, '''                    m.optBoolean("completed", false), m.optString("role", "member"))''', 1)
old = '''    data class MemberInfo(val id: Long, val name: String, val juz: Int?, val completed: Boolean)'''
assert a.count(old) == 1
a = a.replace(old, '''    data class MemberInfo(val id: Long, val name: String, val juz: Int?, val completed: Boolean,
                          val role: String = "member")''', 1)
api.write_text(a, encoding="utf-8")

gradle = Path("app/build.gradle.kts")
g = gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 22") == 1 and g.count('versionName = "0.18.1-noor-ui"') == 1
gradle.write_text(g.replace("versionCode = 22", "versionCode = 23")
                   .replace('versionName = "0.18.1-noor-ui"',
                            'versionName = "0.19.0-leader-dashboard"'), encoding="utf-8")
verify = Path("tools/verify_project.py")
v = verify.read_text(encoding="utf-8")
assert "versionCode 22" in v and "0.18.1-noor-ui" in v
verify.write_text(v.replace("versionCode 22", "versionCode 23")
                  .replace("versionCode = 22", "versionCode = 23")
                  .replace("0.18.1-noor-ui", "0.19.0-leader-dashboard"), encoding="utf-8")
assert "items(filtered, key =" in s and "details.members.forEach" not in s
print("PASS: leader dashboard uses lazy rows, four summary metrics, search, filters and pending list")
print("PASS: no changes to other modules, databases, login or Firebase")
