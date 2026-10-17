package com.ab.khatma.notifications

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ab.khatma.BuildConfig
import com.ab.khatma.secure.SecureSessionStore
import com.ab.khatma.storage.SessionStore

/** P80/VC104: notification copy only. No new permissions, polls, alarms or backend calls.
 * Display name is resolved from the currently signed-in account on the phone.
 * Never put names, groups, prayer status or private chat text on the lock screen.
 */
object P80NoticeCopy {
    fun displayName(context: Context): String? {
        val secure = SecureSessionStore(context).load()
        // Prevent showing another account's stale legacy name when V2 is enabled.
        val raw = when {
            secure != null -> secure.userName
            !BuildConfig.V2_PREVIEW_ENABLED -> SessionStore(context).userName()
            else -> ""
        }
        val safe = raw.replace(Regex("[\\p{Cntrl}\\u007f]+"), " ")
            .replace(Regex("\\s+"), " ").trim().take(40)
        return safe.takeIf { it.isNotBlank() }
    }

    fun say(context: Context, message: String): String {
        val name = displayName(context) ?: return message
        return "يا $name، $message"
    }

    fun prayer(context: Context, prayer: String) =
        say(context, "أذان $prayer دلوقتي. يلا نلبّي نداء الصلاة 🌿")

    fun afterPrayer(context: Context, prayer: String, details: String) =
        say(context, "بعد صلاة $prayer، لو لسه ما خلّصتش وردك، افتح جدولك وشوف اللي باقي. $details")

    fun ward(context: Context, details: String?, knownIncomplete: Boolean): String {
        val message = if (knownIncomplete && !details.isNullOrBlank())
            "وردك لسه محتاج تأكيد النهارده 🌿 $details. افتح جدولك وكمّل براحتك."
        else
            "تيجي نقرأ شوية قرآن؟ افتح جدولك وشوف ورد النهارده 🌿"
        return say(context, message)
    }

    fun secureWard(context: Context, stage: String, serverMessage: String): Pair<String, String> {
        val title = if (stage == "final") "لسه في وقت لوردك 🌙" else "وردك مستنيك 🌿"
        // Keep the official assigned Juz number from the server, without claiming
        // the user did not read: only confirmation is missing in the database.
        val detail = serverMessage
            .replace("جزؤك اليوم هو الجزء ", "النهارده عليك الجزء ")
            .replace("لم يتم تسجيل القراءة حتى الآن.", "لسه ما اتسجلش إنك خلّصته.")
        return title to say(context, detail)
    }

    fun chat(context: Context, senderName: String): String =
        say(context, "$senderName بعتلك رسالة خاصة على ختمة 💬")

    fun publicVersion(context: Context, channel: String, icon: Int): Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle("ختمة")
            .setContentText("عندك تنبيه جديد")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
}
