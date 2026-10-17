package com.ab.khatma.secure

import android.content.Context
import android.app.NotificationManager
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Shared by UI, WorkManager and push registration. Passwords never enter this store. */
internal object P83Sessions {
    @Volatile private var coordinator: P83SessionCoordinator? = null
    private val io = Executors.newSingleThreadExecutor()
    fun initialize(context: Context) {
        if (coordinator != null) return
        synchronized(this) {
            if (coordinator != null) return
            val app = context.applicationContext
            val store = SecureSessionStore(app)
            val engine = P83SessionCoordinator(object : P83SessionCoordinator.Port {
                override fun load() = store.load()
                override fun replace(expected: SecureSessionStore.Session, next: SecureSessionStore.Session) =
                    store.replace(expected, next)
                override fun refresh(session: SecureSessionStore.Session, requestId: String) =
                    SecureV2Api.refreshSession(session, requestId)
                override fun enroll(session: SecureSessionStore.Session) = SecureV2Api.enrollSession(session)
                override fun silentGoogle(session: SecureSessionStore.Session) = runBlocking {
                    SecureV2Api.renewLegacyGoogle(session.userId, KhatmaGoogleSignIn.silentFirebaseIdToken(app))
                }
            })
            coordinator = engine
            // Legacy sessions migrate while valid. The main thread never waits for the network.
            io.execute { store.load()?.let { runCatching { engine.enrollLegacy(it.token) } } }
            P83LogoutWorker.schedule(app)
        }
    }
    fun tokenFor(token: String): String = coordinator?.tokenFor(token) ?: throw SecureV2Api.SessionExpiredException()
    fun renew(token: String): String = coordinator?.renew(token) ?: throw SecureV2Api.SessionExpiredException()

    fun logout(context: Context, session: SecureSessionStore.Session) {
        if (!SecureSessionStore(context).logout(session.localId)) return
        KhatmaGoogleSignIn.signOut(context)
        SecureReminderWorker.cancel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.notification.channelId in
            setOf("khatma_v2_reminders", "ab_khatma_private_chat") }.forEach { manager.cancel(it.tag, it.id) }
        P83LogoutWorker.schedule(context)
    }
}

/** Durable revocation retries when the device reconnects, without restoring a logged-out session. */
class P83LogoutWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = SecureSessionStore(applicationContext)
        try {
            val queue = store.pendingLogouts()
            if (queue.isEmpty()) return@withContext Result.success()
            val deviceToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
            for (session in queue) {
                if (session.refreshExpiresAt > System.currentTimeMillis() / 1000L) {
                    try { SecureV2Api.revokeSession(session, deviceToken) }
                    catch (_: SecureV2Api.SessionExpiredException) { /* Account/session already unavailable. */ }
                }
                store.removePendingLogout(session.localId)
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("khatma_v2_session_logout",
                ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<P83LogoutWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
