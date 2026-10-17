package com.ab.khatma

import android.app.Application
import com.ab.khatma.prayer.PrayerNotificationScheduler
import com.ab.khatma.prayer.AdhanAlarmScheduler

class KhatmaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.ab.khatma.secure.P83Sessions.initialize(this)
        runCatching { com.ab.khatma.secure.P79Push.initialize(this) }
        PrayerNotificationScheduler.ensureChannel(this)
        PrayerNotificationScheduler.scheduleRollingWindow(this)
        PrayerNotificationScheduler.ensurePeriodicRefresh(this)
        AdhanAlarmScheduler.schedule(this)
        runCatching { com.ab.khatma.reminders.P53ReminderScheduler.reschedule(this) }
        runCatching { com.ab.khatma.ui.screens.P53NightMode.load(this) }
        runCatching { com.ab.khatma.ui.screens.P54ColoredPages.init(this) }
        runCatching { com.ab.khatma.ui.screens.P55PlainPages.init(this) }
        runCatching { com.ab.khatma.ui.screens.P55Reader.load(this) }
    }
}
