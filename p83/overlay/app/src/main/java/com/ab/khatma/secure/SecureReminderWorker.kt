package com.ab.khatma.secure

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.ab.khatma.BuildConfig
import com.ab.khatma.MainActivity
import com.ab.khatma.notifications.P80NoticeCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** V2 reminders are acknowledged only after the OS accepts a notification. */
class SecureReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.V2_PREVIEW_ENABLED) return@withContext Result.success()
        val session = SecureSessionStore(applicationContext).load() ?: return@withContext Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return@withContext Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled())
            return@withContext Result.success()
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "تذكيرات الختمة الآمنة", NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
        try {
            val reminders = SecureV2Api.due(session.token)
            for (reminder in reminders) {
                val id = (reminder.assignmentId.toInt() * 31 + if (reminder.stage == "final") 2 else 1)
                val intent = Intent(applicationContext, MainActivity::class.java)
                val pending = PendingIntent.getActivity(applicationContext, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val friendly = P80NoticeCopy.secureWard(applicationContext, reminder.stage, reminder.message)
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(friendly.first).setContentText(friendly.second)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(friendly.second))
                    .setContentIntent(pending).setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(P80NoticeCopy.publicVersion(applicationContext, CHANNEL_ID, android.R.drawable.ic_dialog_info))
                    .build()
                // notify() may throw SecurityException; no ACK in that case.
                synchronized(SecureSessionStore.lock) {
                    if (SecureSessionStore(applicationContext).load()?.localId != session.localId)
                        return@withContext Result.success()
                    notificationManager.notify(id, notification)
                }
                // A failed acknowledgment retries using the same stable notification id.
                SecureV2Api.ack(session.token, reminder)
            }
            Result.success()
        } catch (_: SecureV2Api.SessionExpiredException) {
            if (SecureSessionStore(applicationContext).clearIfCurrent(session.localId))
                cancel(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val CHANNEL_ID = "khatma_v2_reminders"
        private const val WORK_NAME = "khatma_v2_reminder_periodic"
        fun schedule(context: Context) {
            if (!BuildConfig.V2_PREVIEW_ENABLED) return
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SecureReminderWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints).build())
        }
        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
