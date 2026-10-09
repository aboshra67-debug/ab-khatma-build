"""P11B Safe Patch: Islamic leader navigation and existing actions in new layout."""
from pathlib import Path
import os,shutil
root=Path("app/src/main/java/com/ab/khatma/secure")
def edit(file, old, new):
    p=Path(file); s=p.read_text(encoding="utf-8")
    assert s.count(old)==1, "P11B unsafe anchor: "+str(p)+" "+old[:30]
    p.write_text(s.replace(old,new,1),encoding="utf-8")

copy_from=Path(os.environ["GITHUB_WORKSPACE"])/"v0_19/KhatmaIslamicLeaderDashboard.kt"
copy_to=root/"KhatmaIslamicLeaderDashboard.kt"
assert copy_from.is_file() and not copy_to.exists()
shutil.copyfile(copy_from,copy_to)

edit("app/build.gradle.kts",
    'versionCode = 26\n        versionName = "0.19.2-google-live-oauth"',
    'versionCode = 27\n        versionName = "0.19.3-islamic-leader-profile"')
p=root/"KhatmaPolishedPanels.kt"
edit(p,'''            val tabs = if (canManage) listOf("read" to "القراءة", "chat" to "الشات", "leader" to "الإدارة")
                       else listOf("read" to "القراءة", "chat" to "الشات")''',
'''            val tabs = if (canManage) listOf("leader" to "لوحة القائد", "read" to "القراءة", "chat" to "الشات")
                       else listOf("read" to "القراءة", "chat" to "الشات")''')
edit(p,'''        Text("إدارة المجموعة وقراءتك",''','''        Text(if (canManage) "مجلس إدارة الختمة" else "وردك ومحادثة مجموعتك",''')
edit(p,'''        Text("السلام عليكم، " + userName, style = MaterialTheme.typography.headlineSmall,''',
'''        Text("السلام عليكم، " + userName, style = MaterialTheme.typography.titleLarge,''')
edit(p,'''        ElevatedCard(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EB)),''',
'''        ElevatedCard(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2E9)),''')
edit(p,'''        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp)''',
'''        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFEF9)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp)''')

u=root/"SecureV2Screen.kt"
edit(u,"import androidx.compose.foundation.layout.*",
'''import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color''')
edit(u,'''                            section = if (section == "plans") "group" else "home"
                            groupTab = "read"
                            chatGroupId = null''',
'''                            section = if (section == "plans") "group" else "home"
                            if (section != "group") groupTab = "read"
                            chatGroupId = null''')
edit(u,'''                            section = "group"
                            groupTab = "read"
                            groupInfo = null
                            chatGroupId = null
                            message = ""''',
'''                            section = "group"
                            groupTab = if (item.role == "leader") "leader" else "read"
                            groupInfo = null
                            pending = emptyList()
                            chatGroupId = null
                            message = ""
                            if (item.role == "leader") {
                                runTask {
                                    val info = withContext(Dispatchers.IO) {
                                        SecureV2Api.groupStatus(active.token, item.groupId)
                                    }
                                    val requests = withContext(Dispatchers.IO) {
                                        SecureV2Api.pending(active.token, item.groupId)
                                    }
                                    groupInfo = info
                                    pending = requests
                                }
                            }''')
edit(u,'''                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {''',
'''                    Card(modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEF8))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {''')
text=u.read_text(encoding="utf-8")
start='''                            if (groupTab == "leader" && item.status == "active" &&
                                (item.role == "leader" || item.role == "assistant")) {'''
end='''                            } // detail-only actions'''
assert text.count(start)==1 and text.count(end)==1
i=text.index(start); j=text.index(end,i)
old=text[i:j]
assert "SecureV2Api.approve" in old and "SecureV2Api.groupStatus" in old and "shareCode" in old
new='''                            if (groupTab == "leader" && item.status == "active" &&
                                (item.role == "leader" || item.role == "assistant")) {
                                KhatmaIslamicLeaderDashboard(
                                    item = item,
                                    details = groupInfo?.takeIf { it.id == item.groupId },
                                    pending = pending,
                                    busy = busy,
                                    onRefresh = {
                                        runTask {
                                            val fresh = withContext(Dispatchers.IO) {
                                                SecureV2Api.groupStatus(active.token, item.groupId)
                                            }
                                            val waiting = withContext(Dispatchers.IO) {
                                                SecureV2Api.pending(active.token, item.groupId)
                                            }
                                            groupInfo = fresh
                                            pending = waiting
                                        }
                                    },
                                    onCopy = { copyCode(it) },
                                    onShare = { shareCode(item.groupName, it) },
                                    onApprove = { memberId, accept ->
                                        runTask {
                                            withContext(Dispatchers.IO) {
                                                SecureV2Api.approve(active.token, item.groupId, memberId, accept)
                                            }
                                            val fresh = withContext(Dispatchers.IO) {
                                                SecureV2Api.groupStatus(active.token, item.groupId)
                                            }
                                            val waiting = withContext(Dispatchers.IO) {
                                                SecureV2Api.pending(active.token, item.groupId)
                                            }
                                            groupInfo = fresh
                                            pending = waiting
                                            message = if (accept) "تم قبول العضو" else "تم رفض طلب الانضمام"
                                        }
                                    },
                                    onOpenPlans = { section = "plans"; chatGroupId = null },
                                    onOpenChat = { groupTab = "chat"; chatGroupId = item.groupId },
                                    onOpenRead = { groupTab = "read"; chatGroupId = null }
                                )
                            }
'''
text=text[:i]+new+text[j:].replace(end,"                            } // All existing read/chat handlers remain on their tabs.",1)
u.write_text(text,encoding="utf-8")
v=Path("tools/verify_project.py")
s=v.read_text(encoding="utf-8")
assert "versionCode 26" in s and "0.19.2-google-live-oauth" in s
s=s.replace("versionCode 26","versionCode 27").replace("versionCode = 26","versionCode = 27")
s=s.replace("0.19.2-google-live-oauth","0.19.3-islamic-leader-profile")
v.write_text(s,encoding="utf-8")
assert 'KhatmaIslamicLeaderDashboard(' in u.read_text()
assert 'SecureV2Api.approve(active.token' in u.read_text()
print("P11B PASS: leader display and member actions preserved.")
