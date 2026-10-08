"""V0.18.1 safe styling patch; only new installs get Noor theme by default."""
from pathlib import Path

theme=Path("app/src/main/java/com/ab/khatma/ui/theme/KhatmaTheme.kt")
s=theme.read_text(encoding="utf-8")
old='prefs.getString("theme_style", KhatmaThemeStyle.ROYAL_AB.name)'
new='prefs.getString("theme_style", KhatmaThemeStyle.NOOR.name)'
assert s.count(old)==1, "Theme preference anchor changed"
s=s.replace(old,new,1)
# Explicitly chosen stored theme styles remain unchanged.
assert 'KhatmaThemeStyle.valueOf(prefs.getString' in s
theme.write_text(s,encoding="utf-8")

gradle=Path("app/build.gradle.kts")
s=gradle.read_text(encoding="utf-8")
assert s.count('versionCode = 21')==1 and s.count('versionName = "0.18.0-ui-beta"')==1
s=s.replace('versionCode = 21','versionCode = 22',1).replace('versionName = "0.18.0-ui-beta"','versionName = "0.18.1-noor-ui"',1)
gradle.write_text(s,encoding="utf-8")

checks=Path("tools/verify_project.py")
s=checks.read_text(encoding="utf-8")
assert "versionCode 21" in s and "0.18.0-ui-beta" in s
s=s.replace("versionCode 21","versionCode 22").replace("versionCode = 21","versionCode = 22")
s=s.replace("0.18.0-ui-beta","0.18.1-noor-ui")
checks.write_text(s,encoding="utf-8")
print("PASS: Noor default only, existing theme settings preserved, V0.18.1-noor-ui")
