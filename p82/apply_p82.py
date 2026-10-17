#!/usr/bin/env python3
"""P82/VC106 Safe Patch: opt-in idempotent message IDs in both chat composers.

The source is VC105 after p81 patch. This patch never touches Quran,
Firebase, payments, PostgreSQL, session storage, or any Android permission.
A failed send keeps its UUID; successful send clears it. No automatic POST retry.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
prefix = "app/src/main/java/com/ab/khatma/secure/"

def patch(path, old, new):
    p = root / path
    source = p.read_text(encoding="utf-8")
    count = source.count(old)
    assert count == 1, f"P82 unexpected anchor: {path} {old[:95]!r} count={count}"
    p.write_text(source.replace(old, new, 1), encoding="utf-8")

api = prefix + "SecureV2Api.kt"
screen = prefix + "SecureV2Screen.kt"
private = prefix + "P69PrivateChat.kt"

patch(api,
'''    fun sendPrivate(token: String, groupId: Long, peerId: Long, text: String) {
        request("/groups/$groupId/private/$peerId", token, "POST", JSONObject().put("text", text))
    }''',
'''    fun sendPrivate(token: String, groupId: Long, peerId: Long, text: String,
                    clientMessageId: String? = null) {
        val data = JSONObject().put("text", text)
        if (clientMessageId != null) data.put("client_message_id", clientMessageId)
        request("/groups/$groupId/private/$peerId", token, "POST", data)
    }''')

patch(api,
'''    fun sendMessage(token: String, groupId: Long, text: String, replyTo: Long? = null) {
        val json = JSONObject().put("message", text.trim())
        if (replyTo != null) json.put("reply_to_id", replyTo)
        request("/groups/$groupId/chat", token, "POST", json)
    }''',
'''    fun sendMessage(token: String, groupId: Long, text: String, replyTo: Long? = null,
                    clientMessageId: String? = null) {
        val json = JSONObject().put("message", text.trim())
        if (replyTo != null) json.put("reply_to_id", replyTo)
        if (clientMessageId != null) json.put("client_message_id", clientMessageId)
        request("/groups/$groupId/chat", token, "POST", json)
    }''')

patch(private, 'import androidx.compose.runtime.*',
      'import androidx.compose.runtime.*\nimport androidx.compose.runtime.saveable.rememberSaveable\nimport java.util.UUID')

patch(private,
'''    var draft by remember(groupId, peer.id) { mutableStateOf("") }''',
'''    var draft by remember(groupId, peer.id) { mutableStateOf("") }
    // Preserve the same send ID on explicit retry after a network timeout.
    // UUID is replaced when the draft changes or the send succeeds.
    var p82PendingText by rememberSaveable(groupId, peer.id) { mutableStateOf("") }
    var p82PendingId by rememberSaveable(groupId, peer.id) { mutableStateOf("") }''')

patch(private,
'''                val text = draft.trim()
                act {
                    withContext(Dispatchers.IO) { SecureV2Api.sendPrivate(token, groupId, peer.id, text) }
                    draft = ""
                    messages = withContext(Dispatchers.IO) { SecureV2Api.privateMessages(token, groupId, peer.id) }
                }''',
'''                val text = draft.trim()
                act {
                    // No blind retry here: only reuse this ID if the user presses Send again.
                    val id = if (p82PendingText == text && p82PendingId.isNotBlank())
                        p82PendingId else UUID.randomUUID().toString().also {
                            p82PendingText = text
                            p82PendingId = it
                        }
                    withContext(Dispatchers.IO) {
                        SecureV2Api.sendPrivate(token, groupId, peer.id, text, id)
                    }
                    p82PendingId = ""
                    p82PendingText = ""
                    draft = ""
                    messages = withContext(Dispatchers.IO) {
                        SecureV2Api.privateMessages(token, groupId, peer.id)
                    }
                }''')

patch(screen, 'import androidx.compose.runtime.*',
      'import androidx.compose.runtime.*\nimport androidx.compose.runtime.saveable.rememberSaveable\nimport java.util.UUID')

patch(screen,
'''    var chatText by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<Long?>(null) }''',
'''    var chatText by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<Long?>(null) }
    // User + group scoped; retry after timeout reuses the same request key.
    var p82GroupPendingPayload by rememberSaveable(session?.userId, chatGroupId) {
        mutableStateOf("")
    }
    var p82GroupPendingId by rememberSaveable(session?.userId, chatGroupId) {
        mutableStateOf("")
    }''')

patch(screen,
'''                                    val sendChat: () -> Unit = {
                                        val sending = chatText.trim()
                                        if (sending.isNotBlank()) runTask {
                                            withContext(Dispatchers.IO) {
                                                SecureV2Api.sendMessage(active.token, item.groupId, sending, replyToId)
                                            }
                                            chatText = ""
                                            replyToId = null
                                            chatMessages = withContext(Dispatchers.IO) {
                                                SecureV2Api.messages(active.token, item.groupId)
                                            }
                                        }
                                    }''',
'''                                    val sendChat: () -> Unit = {
                                        val sending = chatText.trim()
                                        val sendingReply = replyToId
                                        if (sending.isNotBlank()) runTask {
                                            val fingerprint = "\${sendingReply ?: 0L}\\u001f$sending"
                                            val id = if (p82GroupPendingPayload == fingerprint &&
                                                p82GroupPendingId.isNotBlank()) p82GroupPendingId
                                                else UUID.randomUUID().toString().also {
                                                    p82GroupPendingPayload = fingerprint
                                                    p82GroupPendingId = it
                                                }
                                            // Only a user click sends POST. Never retry writes automatically.
                                            withContext(Dispatchers.IO) {
                                                SecureV2Api.sendMessage(
                                                    active.token, item.groupId, sending,
                                                    sendingReply, id)
                                            }
                                            p82GroupPendingId = ""
                                            p82GroupPendingPayload = ""
                                            chatText = ""
                                            replyToId = null
                                            chatMessages = withContext(Dispatchers.IO) {
                                                SecureV2Api.messages(active.token, item.groupId)
                                            }
                                        }
                                    }''')

patch("app/build.gradle.kts", "versionCode = 105", "versionCode = 106")
patch("app/build.gradle.kts",
      'versionName = "0.19.76-p81-safe-auto-reconnect-qa"',
      'versionName = "0.19.77-p82-idempotent-private-group-chat-safe-qa"')
print("P82 VC106 Safe Patch PASS: Android private/group chat IDs; manual retry only; no existing functions removed")
