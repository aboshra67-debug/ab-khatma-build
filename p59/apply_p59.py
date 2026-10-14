"""P59 / VC81 safe patch on top of P58 / VC80.

1. Manifest: declares the text-to-speech service so the phone's Arabic voice can be found on Android 11+.
2. Readability: every font size of 10sp or smaller becomes 11sp (no layout is removed).
Each replacement must match exactly once (font pass reports its count).
"""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
java = root / "app/src/main/java/com/ab/khatma"


def patch(path, old, new, count=1):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    assert found == count, (str(path), found, old[:110])
    path.write_text(text.replace(old, new), encoding="utf-8")


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 80", "versionCode = 81")
patch(gradle, 'versionName = "0.19.53-p58-follow-hifz-tasbih"', 'versionName = "0.19.54-p59-tts-queries-fonts"')

manifest = root / "app/src/main/AndroidManifest.xml"
patch(manifest, "\n    <application\n", """
    <queries>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>

    <application
""")

pattern = re.compile(r"fontSize\s*=\s*(\d|10)(\.\d+)?\.sp")
changed = 0
for f in sorted(java.rglob("*.kt")):
    text = f.read_text(encoding="utf-8")
    new, n = pattern.subn("fontSize = 11.sp", text)
    if n:
        f.write_text(new, encoding="utf-8")
        changed += n
assert changed >= 15, changed
print("P59 applied; small fonts raised:", changed)
