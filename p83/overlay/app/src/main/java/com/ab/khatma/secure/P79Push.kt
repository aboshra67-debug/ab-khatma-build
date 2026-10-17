package com.ab.khatma.secure

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ab.khatma.BuildConfig
import com.ab.khatma.MainActivity
import com.ab.khatma.R
import com.ab.khatma.notifications.P80NoticeCopy
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.Executors

/** VC103: no background polling; no message content delivered through FCM. */
data class P79PrivateTarget(
    val groupId: Long, val senderMembershipId: Long,
    val recipientUserId: Long, val messageId: Long
)

object P79Push {
    private const val CHANNEL = "ab_khatma_private_chat"
    private const val EXTRA_GROUP = "p79_group"
    private const val EXTRA_SENDER = "p79_sender"
    private const val EXTRA_USER = "p79_user"
    private const val EXTRA_MESSAGE = "p79_message"
    private val io = Executors.newSingleThreadExecutor()

    fun initialize(context: Context) {
        if (BuildConfig.KHATMA_FIREBASE_APP_ID.isBlank()
            || BuildConfig.KHATMA_FIREBASE_API_KEY.isBlank()) return
        // FirebaseAuth's KHATMA_AUTH secondary instance is preserved unchanged.
        if (FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
            FirebaseApp.initializeApp(context.applicationContext,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.KHATMA_FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.KHATMA_FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.KHATMA_FIREBASE_PROJECT_ID)
                    .build())
        }
        ensureChannel(context)
    }

    fun sync(context: Context, session: SecureSessionStore.Session) {
        if (!BuildConfig.V2_PREVIEW_ENABLED) return
        runCatching {
            initialize(context)
            if (FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { deviceToken ->
                register(session, deviceToken)
            }
        }
    }

    fun register(session: SecureSessionStore.Session, deviceToken: String) {
        io.execute {
            runCatching { SecureV2Api.registerPushDevice(session.token, deviceToken) }
        }
    }

    fun unregister(context: Context, session: SecureSessionStore.Session) {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { deviceToken ->
                io.execute {
                    runCatching { SecureV2Api.unregisterPushDevice(session.token, deviceToken) }
                }
            }
        }
    }

    fun targetFromIntent(intent: Intent?): P79PrivateTarget? {
        if (intent == null || !intent.hasExtra(EXTRA_MESSAGE)) return null
        val t = P79PrivateTarget(
            intent.getLongExtra(EXTRA_GROUP, 0L),
            intent.getLongExtra(EXTRA_SENDER, 0L),
            intent.getLongExtra(EXTRA_USER, 0L),
            intent.getLongExtra(EXTRA_MESSAGE, 0L)
        )
        return t.takeIf {
            it.groupId > 0 && it.senderMembershipId > 0 &&
                it.recipientUserId > 0 && it.messageId > 0
        }
    }

    fun clearTarget(intent: Intent?) {
        intent?.removeExtra(EXTRA_MESSAGE)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "الرسائل الخاصة", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "تنبيهات المحادثات الخاصة بالختمة" })
        }
    }

    fun display(context: Context, data: Map<String, String>) {
        if (data["kind"] != "private_chat") return
        val loggedIn = SecureSessionStore(context).load() ?: return
        val intendedUser = data["recipient_user_id"]?.toLongOrNull() ?: return
        if (intendedUser != loggedIn.userId) return // protects account switching/logouts
        val group = data["group_id"]?.toLongOrNull() ?: return
        val sender = data["sender_membership_id"]?.toLongOrNull() ?: return
        val message = data["message_id"]?.toLongOrNull() ?: return
        if (group <= 0 || sender <= 0 || message <= 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        initialize(context)
        val name = data["sender_name"].orEmpty().filter { it.code >= 32 }.take(80)
            .ifBlank { "أحد أعضاء الختمة" }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_GROUP, group)
            putExtra(EXTRA_SENDER, sender)
            putExtra(EXTRA_USER, intendedUser)
            putExtra(EXTRA_MESSAGE, message)
        }
        val pending = PendingIntent.getActivity(context, message.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicNotification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ختمة")
            .setContentText("رسالة خاصة جديدة")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("رسالة خاصة على ختمة 💌")
            .setContentText(P80NoticeCopy.chat(context, name))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        synchronized(SecureSessionStore.lock) {
            if (SecureSessionStore(context).load()?.localId == loggedIn.localId)
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(message.toInt(), notification)
        }
    }
}

/** Data-only FCM makes notifications available while the process is not running. */
class P79FirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        P79Push.display(applicationContext, message.data)
    }
    override fun onNewToken(token: String) {
        SecureSessionStore(applicationContext).load()?.let { P79Push.register(it, token) }
    }
}
