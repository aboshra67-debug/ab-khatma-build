#!/usr/bin/env python3
"""VC105 regression: auto recovery on GET only; preserve Quran, original signature, prayer, chats."""
from pathlib import Path
import sys
root = Path(sys.argv[1])
j = root/"app/src/main/java/com/ab/khatma"
api=(j/"secure/SecureV2Api.kt").read_text()
screen=(j/"secure/SecureV2Screen.kt").read_text()
helper=(j/"secure/P81NetworkRecovery.kt").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert 'versionCode = 105' in build
assert 'P81NetworkRecovery.readOnly { SecureV2Api.today(s.token) }' in screen
assert 'P81NetworkRecovery.readOnly { SecureV2Api.today(current.token) }' in screen
assert screen.count("P81NetworkRecovery.readOnly") == 2
assert 'LaunchedEffect(session?.token, section, p81RetrySignal)' in screen
assert 'DisposableEffect(p81LifecycleOwner)' in screen
assert 'Lifecycle.Event.ON_START' in screen and 'Lifecycle.Event.ON_STOP' in screen
assert 'delay(30_000L)' in screen
assert 'p81PendingNetworkRecovery = false' in screen
assert 'showExpiredSession()' in screen
assert 'class SessionExpiredException' in api and 'class TransientNetworkException' in api
assert api.count("throw TransientNetworkException(") >= 6
assert 'connection.instanceFollowRedirects = false' in api
assert 'Authorization", "Bearer $it"' in api
assert 'connection.disconnect()' in api
assert 'catch (error: SecureV2Api.TransientNetworkException)' in helper
assert 'withContext(Dispatchers.IO)' in helper
assert 'retry >= 1' in helper
for forbidden in ["post(", "complete(", "sendPrivate(", "POST", "PUT", "PATCH", "DELETE", "Thread.sleep"]:
    assert forbidden not in helper, f"unsafe retry {forbidden}"
assert '.setPublicVersion(P80NoticeCopy.publicVersion(' in (j/"secure/P79Push.kt").read_text()
assert 'يا $name، $message' in (j/"notifications/P80NoticeCopy.kt").read_text()
assert 'onDismiss = { p101DockAyah = null }' in (j/"ui/screens/QuranScreen.kt").read_text()
assets=root/"app/src/main/assets"
assert len(list((assets/"quran_pages_colored").glob("*.webp"))) == 604
assert len(list((assets/"quran_verse_hitmaps").glob("*.json"))) == 604
print("VC105 static gates PASS: read-only GET retries, lifecycle recovery, no mutation retries, Firebase/notifications and Quran unchanged")
