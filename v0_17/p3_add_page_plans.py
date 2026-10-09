"""Safe, fail-closed opt-in page khatma screen in V2 account and group."""
from pathlib import Path
import shutil

root = Path("app/src/main/java/com/ab/khatma")
dest = root / "secure/PagePlanner.kt"
source = Path(__file__).resolve().parents[1] / "v0_17" / "PagePlanner.kt"
# __file__ is in repo root/v0_17; a workflow can run this script from the
# extracted Android project, so use its GitHub workspace location instead.
import os
source = Path(os.environ["GITHUB_WORKSPACE"]) / "v0_17/PagePlanner.kt"
assert source.is_file()
assert not dest.exists(), "Refusing to replace existing V3 planner screen"
shutil.copyfile(source, dest)

def change(path: Path, old: str, new: str):
    data = path.read_text(encoding="utf-8")
    assert data.count(old) == 1, f"Unsafe patch anchor in {path}: {old[:60]}"
    path.write_text(data.replace(old, new, 1), encoding="utf-8")

p = root / "secure/SecureV2Screen.kt"
change(p,
    'if (session != null && section != "home") TextButton(onClick = { section = "home"; chatGroupId = null }) { Text("رجوع") }',
    'if (session != null && section != "home") TextButton(onClick = { section = if (section == "plans") "group" else "home"; chatGroupId = null }) { Text("رجوع") }'
)
change(p,
    '                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->',
    '''                item {
                    if (section == "plans") {
                        val selectedGroup = groups.firstOrNull { it.groupId == selectedGroupId }
                        if (selectedGroup != null && selectedGroup.status == "active") {
                            PagePlanner(
                                token = active.token, groupId = selectedGroupId,
                                leader = selectedGroup.role == "leader",
                                onRead = { onOpenQuran(null) }
                            )
                        }
                    }
                }
                items(groups.filter { section == "home" || (section == "group" && it.groupId == selectedGroupId) }, key = { it.groupId }) { item ->'''
)
change(p,
    '                            OutlinedButton(onClick = { onOpenQuran(item.juz) }) { Text("قراءة الجزء في المصحف") }',
    '''                            if (item.status == "active") {
                                Button(onClick = { section = "plans"; chatGroupId = null }) {
                                    Text("ختمات الصفحات وتسجيل القراءة")
                                }
                            }
                            OutlinedButton(onClick = { onOpenQuran(item.juz) }) { Text("قراءة الجزء في المصحف") }'''
)
gradle = Path("app/build.gradle.kts")
change(gradle, 'versionCode = 18\n        versionName = "0.16.2"',
       'versionCode = 19\n        versionName = "0.17.0-beta"')
verifier = Path("tools/verify_project.py")
s = verifier.read_text(encoding="utf-8")
assert "'versionCode 18'" in s and "'versionName 0.16.2'" in s
s = s.replace("versionCode 18", "versionCode 19").replace("versionCode = 18", "versionCode = 19")
s = s.replace("0.16.2", "0.17.0-beta")
verifier.write_text(s, encoding="utf-8")
assert "PagePlanner(" in p.read_text()
assert "versionCode = 19" in gradle.read_text()
print("PASS: Android authenticated PagePlanner installed, V0.17 navigation connected, no V1 change.")
