package com.ab.khatma.prayer

import android.content.Context

/** Adhan preferences are deliberately independent of existing reading reminders. */
class AdhanPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ab_khatma_adhan_v1", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var audioEnabled: Boolean
        get() = prefs.getBoolean("audio_enabled", true)
        set(value) { prefs.edit().putBoolean("audio_enabled", value).apply() }

    var volume: Float
        get() = prefs.getFloat("volume", 0.85f).coerceIn(0f, 1f)
        set(value) { prefs.edit().putFloat("volume", value.coerceIn(0f, 1f)).apply() }

    // The selected audio source is saved locally without changing notification schedules.
    var voiceId: String
        get() = prefs.getString("voice_id", "default")?.takeIf { it in (listOf("default", "makkah", "classic", "madinah", "shalqar") + P85NamedAdhan.voices.map { it.id }) } ?: "default"
        set(value) { prefs.edit().putString("voice_id", if (value in (listOf("default", "makkah", "classic", "madinah", "shalqar") + P85NamedAdhan.voices.map { it.id })) value else "default").apply() }

    // Unlike the existing alarm toggle, this mutes just the recording;
    // the arrival notification stays enabled and scheduled as before.
    fun audioEnabledFor(prayer: String): Boolean =
        prayer in PRAYERS && prefs.getBoolean("audio_for_$prayer", true)

    fun setAudioEnabledFor(prayer: String, value: Boolean) {
        if (prayer in PRAYERS) prefs.edit().putBoolean("audio_for_$prayer", value).apply()
    }

    fun enabledFor(prayer: String): Boolean =
        prayer in PRAYERS && prefs.getBoolean("enabled_$prayer", true)

    fun setEnabledFor(prayer: String, value: Boolean) {
        if (prayer in PRAYERS) prefs.edit().putBoolean("enabled_$prayer", value).apply()
    }

    companion object { val PRAYERS = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء") }
}
