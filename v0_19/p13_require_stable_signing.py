"""P13 Safe Patch: disable per-run Android debug signing, use fixed signing only."""
from pathlib import Path

def replace_one(p, before, after):
    text = p.read_text(encoding="utf-8")
    assert text.count(before) == 1, "P13 unexpected signing baseline: " + str(p)
    p.write_text(text.replace(before, after, 1), encoding="utf-8")

gradle=Path("app/build.gradle.kts")
replace_one(gradle,
'''        versionCode = 28
        versionName = "0.19.4-calm-login-home"''',
'''        versionCode = 29
        versionName = "0.19.5-stable-preview-signing"''')
replace_one(gradle,
'''        debug {
            // Preview installs beside the working V0.4 instead of replacing it.''',
'''        debug {
            // CRITICAL: never generate a random per-run debug certificate.
            // CI signs the unsigned preview APK using the owner-controlled stable key.
            signingConfig = null
            // Preview installs beside the working V0.4 instead of replacing it.''')
v=Path("tools/verify_project.py")
t=v.read_text(encoding="utf-8")
assert "versionCode 28" in t and "0.19.4-calm-login-home" in t
t=t.replace("versionCode 28","versionCode 29").replace("versionCode = 28","versionCode = 29")
t=t.replace("0.19.4-calm-login-home","0.19.5-stable-preview-signing")
v.write_text(t,encoding="utf-8")
assert "signingConfig = null" in gradle.read_text(encoding="utf-8")
print("P13 PASS: debug output unsigned; no implicit debug keystore fallback")
