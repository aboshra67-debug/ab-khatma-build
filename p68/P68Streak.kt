package com.ab.khatma.ui.screens

import android.content.SharedPreferences
import java.time.LocalDate

/** P68: how many days in a row the reader completed at least one dhikr (kept on the phone only). */
internal fun p68MarkAdhkarDay(pref: SharedPreferences, today: String) {
    val last = pref.getString("p68_last_day", null)
    if (last == today) return
    val yesterday = runCatching { LocalDate.parse(today).minusDays(1).toString() }.getOrDefault("")
    val streak = if (last == yesterday) pref.getInt("p68_streak", 0) + 1 else 1
    pref.edit().putString("p68_last_day", today).putInt("p68_streak", streak).apply()
}

internal fun p68Streak(pref: SharedPreferences, today: String): Int {
    val last = pref.getString("p68_last_day", null) ?: return 0
    val yesterday = runCatching { LocalDate.parse(today).minusDays(1).toString() }.getOrDefault("")
    return if (last == today || last == yesterday) pref.getInt("p68_streak", 0) else 0
}
