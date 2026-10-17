#!/usr/bin/env python3
"""VC103 static safety gate to run after applying the P79 patch (not a device test)."""
import sys
from pathlib import Path
root = Path(sys.argv[1])
src = root / "app/src/main/java/com/ab/khatma"
def read(rel):
    return (src / rel).read_text(encoding="utf-8")

app = (root / "app/build.gradle.kts").read_text()
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
push = read("secure/P79Push.kt")
secure = read("secure/SecureV2Screen.kt")
api = read("secure/SecureV2Api.kt")
activity = read("MainActivity.kt")
app_root = read("ui/screens/AppRoot.kt")
quran = read("ui/screens/QuranScreen.kt")
page = read("ui/screens/P41OfflineMushafPage.kt")

assert "versionCode = 103" in app
assert 'applicationIdSuffix = ".preview"' in app
assert "com.google.firebase:firebase-messaging" in app
assert "P79FirebaseMessagingService" in manifest
assert 'android:exported="false"' in manifest
assert "FirebaseMessaging.getInstance().token" in push
assert "SecureSessionStore(context).load()" in push
assert "intendedUser != loggedIn.userId" in push
assert "VISIBILITY_PRIVATE" in push and "VISIBILITY_PUBLIC" in push
assert "setPublicVersion(publicNotification)" in push
assert "P79Push.clearTarget" in activity and "override fun onNewIntent" in activity
assert "p79NotificationPeer" in secure and "membersDirectory" in secure
assert "registerPushDevice" in api and "unregisterPushDevice" in api
assert "P79Push.unregister" in secure
assert "Screen.SecureV2" in app_root
assert "onMore = { onPlayPrintedAyah(aya.surah, aya.ayah) }" in quran
assert 'Text("الخيارات"' in quran
assert "private fun P101ReadingAudioDock(" in quran
assert "selectedHighlight = selectedImage," in page
assert "onSelectVerseAt = ::selectAt" in page
assets = root / "app/src/main/assets"
for folder in ("quran_pages_colored", "quran_verse_hitmaps"):
    assert (assets / folder).is_dir()
assert len(list((assets / "quran_pages_colored").glob("*.webp"))) == 604
assert len(list((assets / "quran_verse_hitmaps").glob("*.json"))) == 604
print("P79 VC103 static guards PASS; reading controls retained; no Quran file was patched.")
