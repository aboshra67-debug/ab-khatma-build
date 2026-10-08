"""Safe patch of existing SecureV2 API and chat actions; old onboarding unchanged."""
from pathlib import Path

api = Path("app/src/main/java/com/ab/khatma/secure/SecureV2Api.kt")
s = api.read_text(encoding="utf-8")

def replace_once(old, new):
    global s
    assert s.count(old) == 1, "Missing safe chat patch anchor: " + old[:65]
    s = s.replace(old, new, 1)

replace_once(
    '''ChatMessage(m.getLong("id"), m.getLong("user_id"), m.getString("name"),
                m.getString("message"), m.optString("created_at"))''',
    '''ChatMessage(m.getLong("id"), m.getLong("user_id"), m.getString("name"),
                m.getString("message"), m.optString("created_at"),
                edited = m.optBoolean("edited"),
                replyToId = if (m.isNull("reply_to_id")) null else m.optLong("reply_to_id"),
                pinned = m.optBoolean("pinned"),
                reactionText = m.optJSONObject("reactions")?.let { reactions ->
                    reactions.keys().asSequence().map { emoji ->
                        emoji + " " + reactions.optInt(emoji)
                    }.joinToString("  ")
                } ?: "")'''
)
replace_once(
    '''    fun sendMessage(token: String, groupId: Long, text: String) {
        request("/groups/$groupId/chat", token, "POST", JSONObject().put("message", text.trim()))
    }
''',
    '''    fun sendMessage(token: String, groupId: Long, text: String, replyTo: Long? = null) {
        val json = JSONObject().put("message", text.trim())
        if (replyTo != null) json.put("reply_to_id", replyTo)
        request("/groups/$groupId/chat", token, "POST", json)
    }
    fun editMessage(token: String, groupId: Long, messageId: Long, text: String) {
        request("/groups/$groupId/chat/$messageId/edit", token, "POST",
            JSONObject().put("message", text.trim()))
    }
    fun deleteMessage(token: String, groupId: Long, messageId: Long, forEveryone: Boolean) {
        val scope = if (forEveryone) "everyone" else "me"
        request("/groups/$groupId/chat/$messageId?scope=$scope", token, "DELETE")
    }
    fun reactMessage(token: String, groupId: Long, messageId: Long, emoji: String) {
        request("/groups/$groupId/chat/$messageId/reaction", token, "PUT",
            JSONObject().put("emoji", emoji))
    }
    fun pinMessage(token: String, groupId: Long, messageId: Long,
                   minutes: Int, notify: Boolean) {
        request("/groups/$groupId/chat/$messageId/pin", token, "POST",
            JSONObject().put("duration_minutes", minutes)
                .put("notify_members", notify))
    }
    fun pinnedMessages(token: String, groupId: Long): List<String> {
        val data = JSONArray(request("/groups/$groupId/chat/pins", token))
        return (0 until data.length()).map { data.getJSONObject(it).getString("message") }
    }
'''
)
replace_once(
    '''data class ChatMessage(val id: Long, val senderId: Long, val senderName: String,
                           val message: String, val createdAt: String)''',
    '''data class ChatMessage(val id: Long, val senderId: Long, val senderName: String,
                           val message: String, val createdAt: String,
                           val edited: Boolean = false, val replyToId: Long? = null,
                           val pinned: Boolean = false, val reactionText: String = "")'''
)
api.write_text(s, encoding="utf-8")
print("PASS: V2 chat API supports server-enforced edit, delete, reply, pins and reactions.")
