package com.ab.khatma.secure

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Pure session coordination; no Activity, Firebase chooser, or lifecycle dependency. */
internal class P83SessionCoordinator(private val port: Port) {
    interface Port {
        fun load(): SecureSessionStore.Session?
        fun replace(expected: SecureSessionStore.Session, next: SecureSessionStore.Session): Boolean
        fun refresh(session: SecureSessionStore.Session, requestId: String): SecureSessionStore.Session
        fun enroll(session: SecureSessionStore.Session): SecureSessionStore.Session
        fun silentGoogle(session: SecureSessionStore.Session): SecureSessionStore.Session
    }
    private val renewalLock = Any()
    private val aliases = ConcurrentHashMap<String, String>()

    private fun current(requestedToken: String): SecureSessionStore.Session {
        val saved = port.load() ?: throw SecureV2Api.SessionExpiredException()
        if (requestedToken != saved.token && aliases[requestedToken] != saved.localId)
            throw SecureV2Api.SessionExpiredException()
        aliases[saved.token] = saved.localId
        return saved
    }
    fun tokenFor(requestedToken: String): String = current(requestedToken).token

    fun enrollLegacy(requestedToken: String): String = synchronized(renewalLock) {
        val active = current(requestedToken)
        if (active.refreshToken.isNotBlank()) active.token else renewLocked(active)
    }
    /** Concurrent 401s share one persisted replacement. */
    fun renew(requestedToken: String): String = synchronized(renewalLock) {
        val active = current(requestedToken)
        if (active.token != requestedToken) active.token else renewLocked(active)
    }
    private fun renewLocked(active: SecureSessionStore.Session): String {
        var expected = active
        val result = if (active.refreshToken.isNotBlank()) {
            // Persist before sending: process death/lost response retries the SAME rotation.
            if (active.refreshRequestId.isBlank()) {
                val pending = active.copy(refreshRequestId = UUID.randomUUID().toString())
                if (!port.replace(active, pending)) throw SecureV2Api.SessionExpiredException()
                expected = pending
            }
            port.refresh(expected, expected.refreshRequestId)
        } else {
            try { port.enroll(active) }
            catch (_: SecureV2Api.SessionExpiredException) {
                // Legacy migration only: verified SAME existing server identity, no chooser.
                port.silentGoogle(active)
            }
        }
        if (result.userId != active.userId || result.refreshToken.isBlank() || result.sessionId.isBlank())
            throw SecureV2Api.SessionExpiredException()
        if (active.refreshToken.isNotBlank() &&
            (result.sessionId != active.sessionId || result.refreshExpiresAt != active.refreshExpiresAt))
            throw SecureV2Api.SessionExpiredException()
        val next = result.copy(localId = active.localId, refreshRequestId = "",
            revokeToken = result.revokeToken.ifBlank { active.revokeToken })
        if (next.revokeToken.isBlank()) throw SecureV2Api.SessionExpiredException()
        // Logout/account switch may run while the network is in flight. Never restore it.
        if (!port.replace(expected, next)) throw SecureV2Api.SessionExpiredException()
        aliases[active.token] = active.localId
        aliases[next.token] = next.localId
        if (aliases.size > 128) aliases.entries.removeIf { it.value != next.localId }
        return next.token
    }
}
