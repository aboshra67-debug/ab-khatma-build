package com.ab.khatma.secure

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class P83SessionCoordinatorTest {
    private fun session() = SecureSessionStore.Session(7L, "name", "access-old", "server-session",
        "refresh-old", "revoke-only", 1900000000L, "installation-A")
    private class MemoryPort(initial: SecureSessionStore.Session) : P83SessionCoordinator.Port {
        @Volatile var saved: SecureSessionStore.Session? = initial
        val calls = AtomicInteger()
        val requestIds = mutableListOf<String>()
        var action: (SecureSessionStore.Session) -> SecureSessionStore.Session = {
            it.copy(token = "access-new", refreshToken = "refresh-new", revokeToken = "")
        }
        var enrollment: (SecureSessionStore.Session) -> SecureSessionStore.Session = { action(it) }
        var google: (SecureSessionStore.Session) -> SecureSessionStore.Session = { throw SecureV2Api.SessionExpiredException() }
        override fun load() = saved
        @Synchronized override fun replace(expected: SecureSessionStore.Session, next: SecureSessionStore.Session): Boolean {
            val current = saved ?: return false
            if (current.localId != expected.localId || current.token != expected.token ||
                current.refreshRequestId != expected.refreshRequestId) return false
            saved = next.copy(userName = current.userName)
            return true
        }
        override fun refresh(session: SecureSessionStore.Session, requestId: String): SecureSessionStore.Session {
            calls.incrementAndGet()
            synchronized(requestIds) { requestIds.add(requestId) }
            return action(session)
        }
        override fun enroll(session: SecureSessionStore.Session) = enrollment(session)
        override fun silentGoogle(session: SecureSessionStore.Session) = google(session)
    }
    @Test fun simultaneous401sRenewOnceAndOldCallersUseCurrentToken() {
        val port = MemoryPort(session()); val engine = P83SessionCoordinator(port)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..8).map { pool.submit<String> { engine.renew("access-old") } }
            assertEquals(setOf("access-new"), futures.map { it.get(5, TimeUnit.SECONDS) }.toSet())
            assertEquals(1, port.calls.get())
            assertEquals("access-new", engine.tokenFor("access-old"))
            assertEquals("revoke-only", port.saved!!.revokeToken)
        } finally { pool.shutdownNow() }
    }
    @Test fun lostResponseAndProcessRestartReusePersistedRequestId() {
        val port = MemoryPort(session()); port.action = { throw IOException("synthetic offline") }
        assertThrows(IOException::class.java) { P83SessionCoordinator(port).renew("access-old") }
        val pending = port.saved!!.refreshRequestId
        assertTrue(pending.isNotBlank()); assertEquals("refresh-old", port.saved!!.refreshToken)
        port.action = { it.copy(token = "access-new", refreshToken = "refresh-new") }
        assertEquals("access-new", P83SessionCoordinator(port).renew("access-old"))
        assertEquals(listOf(pending, pending), port.requestIds)
        assertEquals("", port.saved!!.refreshRequestId)
    }
    @Test fun logoutDuringRenewalCannotRestoreAccount() {
        val port = MemoryPort(session()); val started = CountDownLatch(1); val release = CountDownLatch(1)
        port.action = {
            started.countDown(); check(release.await(5, TimeUnit.SECONDS))
            it.copy(token = "access-new", refreshToken = "refresh-new")
        }
        val engine = P83SessionCoordinator(port); val pool = Executors.newSingleThreadExecutor()
        try {
            val result = pool.submit<Boolean> {
                try { engine.renew("access-old"); false } catch (_: SecureV2Api.SessionExpiredException) { true }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS)); port.saved = null; release.countDown()
            assertTrue(result.get(5, TimeUnit.SECONDS)); assertNull(port.saved)
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun accountSwitchDuringRenewalPreservesNewLoginEvenForSameUser() {
        val port = MemoryPort(session())
        port.action = {
            port.saved = session().copy(localId = "installation-B", token = "new-login")
            it.copy(token = "access-new", refreshToken = "refresh-new")
        }
        val engine = P83SessionCoordinator(port)
        assertThrows(SecureV2Api.SessionExpiredException::class.java) { engine.renew("access-old") }
        assertEquals("new-login", port.saved!!.token)
        assertThrows(SecureV2Api.SessionExpiredException::class.java) { engine.tokenFor("access-old") }
    }
    @Test fun refreshCannotChangeAccountOrExtendThirtyDayWindow() {
        for (wrong in listOf(session().copy(userId = 8), session().copy(refreshExpiresAt = 2000000000L),
            session().copy(sessionId = "different-server-session"))) {
            val port = MemoryPort(session()); port.action = { wrong }
            assertThrows(SecureV2Api.SessionExpiredException::class.java) { P83SessionCoordinator(port).renew("access-old") }
            assertEquals("access-old", port.saved!!.token)
        }
    }
    @Test fun legacyGoogleMigrationCannotChangeAccount() {
        val legacy = session().copy(sessionId = "", refreshToken = "", revokeToken = "")
        val port = MemoryPort(legacy)
        port.enrollment = { throw SecureV2Api.SessionExpiredException() }
        port.google = { session().copy(userId = 99, token = "wrong-account") }
        assertThrows(SecureV2Api.SessionExpiredException::class.java) { P83SessionCoordinator(port).renew("access-old") }
        assertEquals(7L, port.saved!!.userId)
    }
    @Test fun networkFailureKeepsLegacySessionAndNeverUsesGoogle() {
        val legacy = session().copy(sessionId = "", refreshToken = "", revokeToken = "")
        val port = MemoryPort(legacy); port.enrollment = { throw IOException("offline") }
        port.google = { error("Google must not run after transport failure") }
        assertThrows(IOException::class.java) { P83SessionCoordinator(port).enrollLegacy("access-old") }
        assertEquals(legacy, port.saved)
    }
    @Test fun displayNameUpdateWhileRenewingIsPreserved() {
        val port = MemoryPort(session())
        port.action = {
            port.saved = port.saved!!.copy(userName = "new name")
            it.copy(token = "access-new", refreshToken = "refresh-new")
        }
        P83SessionCoordinator(port).renew("access-old")
        assertEquals("new name", port.saved!!.userName)
    }
}
