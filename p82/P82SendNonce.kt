package com.ab.khatma.secure

import java.security.MessageDigest
import java.util.UUID

/**
 * VC106: remember ONE random request ID for a pending chat POST.
 * - A failed HTTP request MAY already have committed on the server. Retrying the
 *   same account/conversation/body must reuse the same ID.
 * - A confirmed response removes the pending ID so a new identical message
 *   is a legitimately new send.
 * - The map contains only digests + UUIDs; never message text or auth tokens.
 * - Memory-only by design: safe for foreground retries, not a guarantee after
 *   process death / explicit force-stop. No background mutation retries.
 */
internal object P82SendNonce {
    private data class Pending(val payloadDigest: String, val requestId: String)
    private val pending = LinkedHashMap<String, Pending>()
    private const val MAX_PENDING = 32

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Synchronized
    fun begin(token: String, conversation: String, body: String): String {
        require(token.isNotBlank() && conversation.isNotBlank())
        val scope = digest(token + "\n" + conversation)
        val bodyHash = digest(body)
        val previous = pending[scope]
        if (previous != null && previous.payloadDigest == bodyHash) return previous.requestId
        val requestId = UUID.randomUUID().toString()
        pending.remove(scope)
        pending[scope] = Pending(bodyHash, requestId)
        if (pending.size > MAX_PENDING) {
            pending.remove(pending.keys.first())
        }
        return requestId
    }

    @Synchronized
    fun confirmed(token: String, conversation: String, requestId: String) {
        val scope = digest(token + "\n" + conversation)
        if (pending[scope]?.requestId == requestId) pending.remove(scope)
    }
}
