#!/usr/bin/env python3
"""P82 VC106 Safe Patch on verified VC105 source.
Only secure chat send methods and build version change. No UI, Quran, API base,
sessions, alarm, FCM, other POST operations or existing data modified.
"""
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()

def change(path, old, new):
    p = root / path
    src = p.read_text(encoding="utf-8")
    assert src.count(old) == 1, f"P82 unexpected source at {path}: {old[:80]!r}"
    p.write_text(src.replace(old, new, 1), encoding="utf-8")

api = "app/src/main/java/com/ab/khatma/secure/SecureV2Api.kt"
change(api,
'''    fun sendPrivate(token: String, groupId: Long, peerId: Long, text: String) {
        request("/groups/$groupId/private/$peerId", token, "POST", JSONObject().put("text", text))
    }''',
'''    fun sendPrivate(token: String, groupId: Long, peerId: Long, text: String) {
        val body = text.trim()
        val scope = "private:$groupId:$peerId"
        val requestId = P82SendNonce.begin(token, scope, body)
        // The currently deployed VC103 server ignores the optional field.
        // The staged PrePlay Stage3 server deduplicates it transactionally.
        request("/groups/$groupId/private/$peerId", token, "POST",
            JSONObject().put("text", body).put("client_message_id", requestId))
        P82SendNonce.confirmed(token, scope, requestId)
    }''')

change(api,
'''    fun sendMessage(token: String, groupId: Long, text: String, replyTo: Long? = null) {
        val json = JSONObject().put("message", text.trim())
        if (replyTo != null) json.put("reply_to_id", replyTo)
        request("/groups/$groupId/chat", token, "POST", json)
    }''',
'''    fun sendMessage(token: String, groupId: Long, text: String, replyTo: Long? = null) {
        val body = text.trim()
        val scope = "group:$groupId"
        val requestId = P82SendNonce.begin(token, scope, (replyTo?.toString() ?: "") + "\\n" + body)
        val json = JSONObject().put("message", body)
            .put("client_message_id", requestId)
        if (replyTo != null) json.put("reply_to_id", replyTo)
        request("/groups/$groupId/chat", token, "POST", json)
        P82SendNonce.confirmed(token, scope, requestId)
    }''')

helper = root / "app/src/main/java/com/ab/khatma/secure/P82SendNonce.kt"
assert not helper.exists(), "Never overwrite an existing send nonce helper"
helper.write_bytes(Path(__file__).with_name("P82SendNonce.kt").read_bytes())

change("app/build.gradle.kts", "versionCode = 105", "versionCode = 106")
change("app/build.gradle.kts",
       'versionName = "0.19.76-p81-safe-auto-reconnect-qa"',
       'versionName = "0.19.77-p82-chat-idempotency-qa"')
print("P82 VC106 SAFE PATCH PASS — chat POST nonce only; Quran and notifications preserved")
