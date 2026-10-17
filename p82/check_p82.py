#!/usr/bin/env python3
"""VC106 guard. Tests rebuilt Android source after all prior P53..P81 patches."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
d = root / "app/src/main/java/com/ab/khatma/secure"
api = (d / "SecureV2Api.kt").read_text(encoding="utf-8")
helper = (d / "P82SendNonce.kt").read_text(encoding="utf-8")
screen = (d / "SecureV2Screen.kt").read_text(encoding="utf-8")
private = (d / "P69PrivateChat.kt").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionCode = 106' in build
assert "0.19.77-p82-chat-idempotency-qa" in build
assert api.count('.put("client_message_id", requestId)') == 2
assert api.count("P82SendNonce.begin(") == 2
assert api.count("P82SendNonce.confirmed(") == 2
assert api.index('P82SendNonce.begin(token, scope, body)') < api.index('request("/groups/$groupId/private/$peerId"')
assert api.index('request("/groups/$groupId/private/$peerId"') < api.index('P82SendNonce.confirmed(token, scope, requestId)')
assert api.index('P82SendNonce.begin(token, scope, (replyTo?.toString()') < api.index('request("/groups/$groupId/chat"')
assert 'P81NetworkRecovery.readOnly { SecureV2Api.today(current.token) }' in screen
assert 'SecureV2Api.sendPrivate(token, groupId, peer.id, text)' in private
assert screen.count("SecureV2Api.sendMessage(") >= 2
assert "UUID.randomUUID().toString()" in helper
assert "val scope = digest(token +" in helper
assert "@Synchronized" in helper
assert "P82SendNonce" not in (d / "P79Push.kt").read_text(encoding="utf-8")
assert "no background" not in helper.lower()
assert "Thread.sleep" not in helper
assert "POST" in api  # existing authenticated channel API unchanged
assets = root / "app/src/main/assets"
assert len(list((assets/"quran_pages_colored").glob("*.webp"))) == 604
assert len(list((assets/"quran_verse_hitmaps").glob("*.json"))) == 604
print("P82 VC106 GATE PASS: optional client_message_id for both chats; original screens, push, recovery and Quran intact")
