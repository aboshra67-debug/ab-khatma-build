#!/usr/bin/env python3
"""P80 VC104 notification text-only Safe Patch; exact guards before every replacement.
All reminder schedules, API calls, FCM IDs, Quran files and database data stay intact.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
java = "app/src/main/java/com/ab/khatma/"

def modify(path, old, new):
    p = root / path
    original = p.read_text(encoding="utf8")
    assert original.count(old) == 1, f"P80 guard failed: {path}: {old[:100]!r}"
    p.write_text(original.replace(old, new, 1), encoding="utf8")

def kotlin(name, old, new):
    modify(java + name, old, new)

kotlin("prayer/AdhanAlarmReceiver.kt", "import com.ab.khatma.MainActivity",
       "import com.ab.khatma.MainActivity\nimport com.ab.khatma.notifications.P80NoticeCopy")
kotlin("prayer/AdhanAlarmReceiver.kt",
       '.setContentTitle("حان الآن موعد صلاة $prayer")\n                .setContentText("حي على الصلاة • وقت صلاة $prayer")',
       '.setContentTitle("صلاة $prayer 🕌")\n                .setContentText(P80NoticeCopy.prayer(context, prayer))')
kotlin("prayer/AdhanAlarmReceiver.kt",
       '                .setPriority(NotificationCompat.PRIORITY_HIGH)\n                .build()',
       '                .setPriority(NotificationCompat.PRIORITY_HIGH)\n'
       '                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)\n'
       '                .setPublicVersion(P80NoticeCopy.publicVersion(context, CHANNEL_ID, android.R.drawable.ic_lock_idle_alarm))\n'
       '                .build()')

kotlin("prayer/PrayerReminderWorker.kt",
       "import com.ab.khatma.MainActivity",
       "import com.ab.khatma.MainActivity\nimport com.ab.khatma.notifications.P80NoticeCopy")
kotlin("prayer/PrayerReminderWorker.kt",
       '.setContentTitle("بعد $prayer — لم يكتمل جزء اليوم")\n            .setContentText(text)\n            .setStyle(NotificationCompat.BigTextStyle().bigText(text))',
       '.setContentTitle("وردك بعد صلاة $prayer 🌿")\n'
       '            .setContentText(P80NoticeCopy.afterPrayer(applicationContext, prayer, text))\n'
       '            .setStyle(NotificationCompat.BigTextStyle().bigText(P80NoticeCopy.afterPrayer(applicationContext, prayer, text)))')
kotlin("prayer/PrayerReminderWorker.kt",
       '            .setAutoCancel(true)\n            .build()',
       '            .setAutoCancel(true)\n'
       '            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)\n'
       '            .setPublicVersion(P80NoticeCopy.publicVersion(applicationContext, PrayerNotificationScheduler.CHANNEL_ID, android.R.drawable.ic_dialog_info))\n'
       '            .build()')

kotlin("reminders/P53Reminders.kt",
       "import com.ab.khatma.MainActivity",
       "import com.ab.khatma.MainActivity\nimport com.ab.khatma.notifications.P80NoticeCopy")
kotlin("reminders/P53Reminders.kt",
       '        val b = NotificationCompat.Builder(ctx, P53ReminderScheduler.CHANNEL)',
       '        val friendly = P80NoticeCopy.localReminder(ctx, title, text)\n'
       '        val b = NotificationCompat.Builder(ctx, P53ReminderScheduler.CHANNEL)')
kotlin("reminders/P53Reminders.kt",
       '            .setContentTitle(title)\n            .setContentText(text)\n            .setStyle(NotificationCompat.BigTextStyle().bigText(text))',
       '            .setContentTitle(friendly.first)\n'
       '            .setContentText(friendly.second)\n'
       '            .setStyle(NotificationCompat.BigTextStyle().bigText(friendly.second))')
kotlin("reminders/P53Reminders.kt",
       '            .setContentIntent(open)\n            .setAutoCancel(true)',
       '            .setContentIntent(open)\n'
       '            .setAutoCancel(true)\n'
       '            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)\n'
       '            .setPublicVersion(P80NoticeCopy.publicVersion(ctx, P53ReminderScheduler.CHANNEL, android.R.drawable.ic_dialog_info))')

kotlin("secure/SecureReminderWorker.kt",
       "import com.ab.khatma.MainActivity",
       "import com.ab.khatma.MainActivity\nimport com.ab.khatma.notifications.P80NoticeCopy")
kotlin("secure/SecureReminderWorker.kt",
       '                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)',
       '                val friendly = P80NoticeCopy.secureWard(applicationContext, reminder.stage, reminder.message)\n'
       '                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)')
kotlin("secure/SecureReminderWorker.kt",
       '                    .setContentTitle(reminder.title).setContentText(reminder.message)',
       '                    .setContentTitle(friendly.first).setContentText(friendly.second)\n'
       '                    .setStyle(NotificationCompat.BigTextStyle().bigText(friendly.second))')
kotlin("secure/SecureReminderWorker.kt",
       '                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)\n                    .build()',
       '                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)\n'
       '                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)\n'
       '                    .setPublicVersion(P80NoticeCopy.publicVersion(applicationContext, CHANNEL_ID, android.R.drawable.ic_dialog_info))\n'
       '                    .build()')

kotlin("reminders/ReadingReminderWorker.kt",
       "import com.ab.khatma.MainActivity",
       "import com.ab.khatma.MainActivity\nimport com.ab.khatma.notifications.P80NoticeCopy")
kotlin("reminders/ReadingReminderWorker.kt",
       '            .setContentTitle(title)\n            .setContentText(message)\n            .setStyle(NotificationCompat.BigTextStyle().bigText(message))',
       '            .setContentTitle("وردك مستنيك 🌿")\n'
       '            .setContentText(P80NoticeCopy.say(applicationContext, message))\n'
       '            .setStyle(NotificationCompat.BigTextStyle().bigText(P80NoticeCopy.say(applicationContext, message)))')
kotlin("reminders/ReadingReminderWorker.kt",
       '            .setPriority(NotificationCompat.PRIORITY_DEFAULT)\n            .build()',
       '            .setPriority(NotificationCompat.PRIORITY_DEFAULT)\n'
       '            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)\n'
       '            .setPublicVersion(P80NoticeCopy.publicVersion(applicationContext, CHANNEL_ID, android.R.drawable.ic_dialog_info))\n'
       '            .build()')

kotlin("secure/P79Push.kt",
       "import com.ab.khatma.R",
       "import com.ab.khatma.R\nimport com.ab.khatma.notifications.P80NoticeCopy")
kotlin("secure/P79Push.kt",
       '            .setContentTitle(name)\n            .setContentText("أرسل إليك رسالة خاصة")',
       '            .setContentTitle("رسالة خاصة على ختمة 💌")\n'
       '            .setContentText(P80NoticeCopy.chat(context, name))')

source = Path(__file__).with_name("P80NoticeCopy.kt")
target = root / (java + "notifications/P80NoticeCopy.kt")
assert source.is_file() and not target.exists(), "Refuse to overwrite existing notifier"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(source.read_bytes())

modify("app/build.gradle.kts", "versionCode = 103", "versionCode = 104")
modify("app/build.gradle.kts",
       'versionName = "0.19.74-p79-private-fcm-push-safe-qa"',
       'versionName = "0.19.75-p80-personal-egyptian-notices-safe-qa"')
print("P80 VC104 Safe Patch applied: original schedules, audio, verse UI and database untouched")
