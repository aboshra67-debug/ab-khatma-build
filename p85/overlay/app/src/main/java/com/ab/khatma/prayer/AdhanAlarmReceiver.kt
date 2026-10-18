package com.ab.khatma.prayer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ab.khatma.MainActivity
import com.ab.khatma.notifications.P80NoticeCopy

/** Fires only for explicit PendingIntents created by our scheduling layer. */
class AdhanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.ab.khatma.PRAYER_ALARM") return
        val prayer = intent.getStringExtra(EXTRA_PRAYER) ?: return
        val at = intent.getLongExtra(EXTRA_EPOCH, 0L)
        if (prayer !in AdhanPreferences.PRAYERS || at <= 0L) return
        // Ignore stale alarms from clock adjustments or data migrations.
        if (kotlin.math.abs(System.currentTimeMillis() - at) > 7 * 60_000L) return
        val prefs = AdhanPreferences(context)
        if (!prefs.enabled || !prefs.enabledFor(prayer)) return
        val played = context.getSharedPreferences("ab_khatma_adhan_delivery", Context.MODE_PRIVATE)
        val key = "delivered_${prayer}"
        if (played.getLong(key, -1L) == at) return
        played.edit().putLong(key, at).apply()

        val notificationAllowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (notificationAllowed) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "دخول وقت الصلاة", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "إشعارات دخول الصلوات الخمس مع الأذان"; setSound(null, null) })
            }
            val open = PendingIntent.getActivity(context, 8221,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stopIntent = PendingIntent.getService(context, 8300 + AdhanPreferences.PRAYERS.indexOf(prayer),
                Intent(context, AdhanPlaybackService::class.java).setAction(AdhanPlaybackService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("صلاة $prayer 🕌")
                .setContentText(P80NoticeCopy.prayer(context, prayer))
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_media_pause, "إيقاف صوت الأذان", stopIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(P80NoticeCopy.publicVersion(context, CHANNEL_ID, android.R.drawable.ic_lock_idle_alarm))
                .build()
            manager.notify(5200 + AdhanPreferences.PRAYERS.indexOf(prayer), notification)
        }
        if (prefs.audioEnabled && prefs.audioEnabledFor(prayer) && notificationAllowed) {
            val playback = Intent(context, AdhanPlaybackService::class.java)
                .setAction(AdhanPlaybackService.ACTION_START)
                .putExtra(AdhanPlaybackService.EXTRA_PRAYER, prayer)
                .putExtra(EXTRA_EPOCH, at)
            try {
                ContextCompat.startForegroundService(context, playback)
            } catch (_: Exception) {
                // Android may refuse background FGS launch when this was an inexact
                // fallback alarm. The posted notification remains available.
            }
        }
        // Roll the window forward even when the user does not open the app.
        AdhanAlarmScheduler.schedule(context)
    }

    companion object {
        const val EXTRA_PRAYER = "prayer_name"
        const val EXTRA_EPOCH = "prayer_epoch"
        const val CHANNEL_ID = "ab_khatma_adhan_events_v1"
    }
}
