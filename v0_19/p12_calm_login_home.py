"""P12 safe additive UI patch; existing Google, users, memberships and server unchanged."""
from pathlib import Path
import os
import shutil

root=Path("app/src/main/java/com/ab/khatma/secure")
def one(path,old,new):
    file=Path(path)
    src=file.read_text(encoding="utf-8")
    assert src.count(old)==1,(str(file),src.count(old),old[:60])
    file.write_text(src.replace(old,new,1),encoding="utf-8")

src=Path(os.environ["GITHUB_WORKSPACE"])/"v0_19/KhatmaCalmScreens.kt"
dest=root/"KhatmaCalmScreens.kt"
assert src.is_file() and not dest.exists()
shutil.copyfile(src,dest)

one("app/build.gradle.kts",
    'versionCode = 27\n        versionName = "0.19.3-islamic-leader-profile"',
    'versionCode = 28\n        versionName = "0.19.4-calm-login-home"')
p=root/"SecureV2Screen.kt"
one(p, 'if (session != null) TopAppBar(',
       'if (session != null && section != "home") TopAppBar(')
one(p, '"profile" -> "الملف الشخصي"',
       '"profile" -> "الملف الشخصي"\n                                "khatmas" -> "الختمات والمجموعات"')
one(p, '''                            section = if (section == "plans") "group" else "home"
                            if (section != "group") groupTab = "read"''',
'''                            section = when (section) {
                                "plans" -> "group"
                                "group" -> "khatmas"
                                else -> "home"
                            }
                            if (section != "group") groupTab = "read"''')
one(p,'                NavigationBar {',
'''                NavigationBar(containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp) {''')
one(p,'KhatmaPolishedLogin(', 'KhatmaCalmLogin(')
one(p,'''                    if (section == "home") {
                        KhatmaPolishedHomeHeader(
                            userName = active.userName,
                            groupCount = groups.size,
                            groupsLoaded = groupsLoaded,
                            onNewGroup = { section = "create"; message = "" },
                            onJoinGroup = { section = "join"; message = "" },
                            onOpenMushaf = { onOpenQuran(null) }
                        )
                        OutlinedButton(onClick = { reload() }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth()) { Text("تحديث المجموعات") }
                        lastInvitation?.let { invitation ->''',
'''                    if (section == "home") {
                        KhatmaCalmHome(
                            userName = active.userName,
                            groupCount = groups.size,
                            groupsLoaded = groupsLoaded,
                            onOpenMushaf = { onOpenQuran(null) },
                            onOpenKhatmas = { section = "khatmas"; message = "" },
                            onJoinGroup = { section = "join"; message = "" },
                            onOpenProfile = { section = "profile"; message = "" }
                        )
                    } else if (section == "khatmas") {
                        Text("ختماتي ومجموعاتي", style = MaterialTheme.typography.titleLarge)
                        Text(if (groupsLoaded) "المجموعات: " + groups.size
                             else "جاري تحميل المجموعات...",
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { section = "create"; message = "" },
                                modifier = Modifier.weight(1f)) { Text("إنشاء مجموعة") }
                            OutlinedButton(onClick = { section = "join"; message = "" },
                                modifier = Modifier.weight(1f)) { Text("الانضمام بكود") }
                        }
                        OutlinedButton(onClick = { reload() }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth()) { Text("تحديث المجموعات") }
                        lastInvitation?.let { invitation ->''')
one(p,
  'items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->',
  'items(groups.filter { section == "khatmas" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->')
one(p,
 '''                    if (section == "home") {
                        KhatmaPolishedGroupCard(item) {''',
 '''                    if (section == "khatmas") {
                        KhatmaPolishedGroupCard(item) {''')

v=Path("tools/verify_project.py")
t=v.read_text(encoding="utf-8")
assert "versionCode 27" in t and "0.19.3-islamic-leader-profile" in t
t=t.replace("versionCode 27","versionCode 28").replace("versionCode = 27","versionCode = 28")
t=t.replace("0.19.3-islamic-leader-profile","0.19.4-calm-login-home")
v.write_text(t,encoding="utf-8")
ui=p.read_text(encoding="utf-8")
assert "KhatmaCalmLogin(" in ui and "KhatmaCalmHome(" in ui
assert "SecureV2Api.googleLogin(firebaseToken)" in ui
assert "onSaveName = {" in ui and 'groupTab = if (item.role == "leader") "leader" else "read"' in ui
print("P12 UI safe checks passed; no model, session, Firebase or endpoint changes")
