"""V0.19 P14 safe visual-only patch; no changes to auth, memberships or backend."""
from pathlib import Path
import os
import shutil

root=Path("app/src/main/java/com/ab/khatma/secure")
source=Path(os.environ["GITHUB_WORKSPACE"])/"v0_19"
for name in ("P14AtlasArt.kt","KhatmaVisualHome.kt"):
    assert (source/name).is_file()
    assert not (root/name).exists(), "Unexpected existing new component"
    shutil.copyfile(source/name, root/name)

drawable=Path("app/src/main/res/drawable-nodpi")
asset=source/"p14_art_atlas.webp"
assert asset.is_file() and asset.stat().st_size > 1000
assert not (drawable/"p14_art_atlas.webp").exists()
shutil.copyfile(asset, drawable/"p14_art_atlas.webp")

def edit(path,old,new):
    p=Path(path)
    s=p.read_text(encoding="utf-8")
    assert s.count(old)==1, "Unsafe P14 anchor: "+str(p)+" count="+str(s.count(old))
    p.write_text(s.replace(old,new,1),encoding="utf-8")

edit("app/build.gradle.kts",
    'versionCode = 29\n        versionName = "0.19.5-stable-preview-signing"',
    'versionCode = 30\n        versionName = "0.19.6-approved-visual-home"')
edit(root/"SecureV2Screen.kt",
    '                        KhatmaCalmHome(',
    '                        KhatmaVisualHome(')

v=Path("tools/verify_project.py")
t=v.read_text(encoding="utf-8")
assert "versionCode 29" in t and "0.19.5-stable-preview-signing" in t
v.write_text(t.replace("versionCode 29","versionCode 30")
   .replace("versionCode = 29","versionCode = 30")
   .replace("0.19.5-stable-preview-signing","0.19.6-approved-visual-home"),encoding="utf-8")
ui=(root/"SecureV2Screen.kt").read_text(encoding="utf-8")
assert "KhatmaVisualHome(" in ui
assert "SecureV2Api.googleLogin(firebaseToken)" in ui
assert "onSaveName = {" in ui and "SecureV2Api.pending(" in ui
print("P14 PASS: approved art + layout installed; existing auth/group functionality retained")
