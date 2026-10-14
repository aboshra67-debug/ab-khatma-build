package com.ab.khatma.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ab.khatma.MainActivity
import com.ab.khatma.prayer.PrayerCalculator
import com.ab.khatma.prayer.PrayerSettingsStore
import com.ab.khatma.quran.P52ListenService
import com.ab.khatma.secure.SecureSessionStore
import com.ab.khatma.secure.SecureV2Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** P53: user-chosen local reminders. Everything is computed on the phone; nothing new is
 * sent to the server. The existing leader reminders and adhan alarms are left untouched. */
data class P53ReminderSettings(
    val ward: Boolean = true,
    val wardMinutes: Int = 20 * 60,          // 8:00 PM
    val morningAdhkar: Boolean = true,       // after Fajr
    val eveningAdhkar: Boolean = true,       // after Asr
    val adhkarOffset: Int = 30,              // minutes after the prayer
    val prePrayer: Boolean = false,
    val prePrayerMinutes: Int = 10,
    val dailyDua: Boolean = true,
    val duaMinutes: Int = 9 * 60,            // 9:00 AM
    val fridayKahf: Boolean = true,
    val kahfMinutes: Int = 10 * 60,          // Friday 10:00 AM
)

class P53ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("p53_reminders", Context.MODE_PRIVATE)
    fun load() = P53ReminderSettings(
        ward = prefs.getBoolean("ward", true),
        wardMinutes = prefs.getInt("ward_m", 20 * 60),
        morningAdhkar = prefs.getBoolean("morning", true),
        eveningAdhkar = prefs.getBoolean("evening", true),
        adhkarOffset = prefs.getInt("adhkar_off", 30),
        prePrayer = prefs.getBoolean("pre", false),
        prePrayerMinutes = prefs.getInt("pre_m", 10),
        dailyDua = prefs.getBoolean("dua", true),
        duaMinutes = prefs.getInt("dua_m", 9 * 60),
        fridayKahf = prefs.getBoolean("kahf", true),
        kahfMinutes = prefs.getInt("kahf_m", 10 * 60),
    )
    fun save(s: P53ReminderSettings) {
        prefs.edit()
            .putBoolean("ward", s.ward).putInt("ward_m", s.wardMinutes)
            .putBoolean("morning", s.morningAdhkar).putBoolean("evening", s.eveningAdhkar)
            .putInt("adhkar_off", s.adhkarOffset)
            .putBoolean("pre", s.prePrayer).putInt("pre_m", s.prePrayerMinutes)
            .putBoolean("dua", s.dailyDua).putInt("dua_m", s.duaMinutes)
            .putBoolean("kahf", s.fridayKahf).putInt("kahf_m", s.kahfMinutes)
            .apply()
    }
}

object P53ReminderScheduler {
    const val CHANNEL = "ab_khatma_daily_reminders"
    private const val TAG = "p53_reminder"
    private const val REFRESH = "p53_reminder_refresh"
    const val KIND_WARD = "ward"
    const val KIND_MORNING = "morning"
    const val KIND_EVENING = "evening"
    const val KIND_PRE = "pre"
    const val KIND_DUA = "dua"
    const val KIND_KAHF = "kahf"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "تذكيرات الورد والأذكار والدعاء", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "ورد اليوم، أذكار الصباح والمساء، قبل الصلاة، دعاء اليوم، الكهف يوم الجمعة" }
            )
        }
    }

    /** Re-plans the next 48 hours. Safe to call often (app start, settings change, every 12 h). */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        ensureChannel(app)
        val wm = WorkManager.getInstance(app)
        wm.cancelAllWorkByTag(TAG)
        val s = P53ReminderStore(app).load()
        val prayer = PrayerSettingsStore(app).load()
        val zone = runCatching { ZoneId.of(prayer.location.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
        val now = System.currentTimeMillis()
        fun at(date: LocalDate, minutes: Int): Long =
            LocalDateTime.of(date, java.time.LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant().toEpochMilli()
        fun plan(kind: String, whenMs: Long, extra: String = "") {
            if (whenMs <= now + 30_000L) return
            val work = OneTimeWorkRequestBuilder<P53ReminderWorker>()
                .setInitialDelay(whenMs - now, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString("kind", kind).putString("extra", extra).build())
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork("p53_${kind}_${extra}_$whenMs", ExistingWorkPolicy.REPLACE, work)
        }
        for (plusDays in 0L..1L) {
            val date = LocalDate.now(zone).plusDays(plusDays)
            if (s.ward) plan(KIND_WARD, at(date, s.wardMinutes))
            if (s.dailyDua) plan(KIND_DUA, at(date, s.duaMinutes))
            if (s.fridayKahf && date.dayOfWeek == DayOfWeek.FRIDAY) plan(KIND_KAHF, at(date, s.kahfMinutes))
            val times = runCatching { PrayerCalculator.calculate(prayer.location, date) }.getOrNull() ?: continue
            if (s.morningAdhkar) plan(KIND_MORNING, times.fajr + s.adhkarOffset * 60_000L)
            if (s.eveningAdhkar) plan(KIND_EVENING, times.asr + s.adhkarOffset * 60_000L)
            if (s.prePrayer) times.entries().forEach { (name, t) ->
                plan(KIND_PRE, t - s.prePrayerMinutes * 60_000L, name)
            }
        }
        wm.enqueueUniquePeriodicWork(
            REFRESH, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<P53ReminderRefreshWorker>(12, TimeUnit.HOURS).build()
        )
    }
}

class P53ReminderRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        P53ReminderScheduler.reschedule(applicationContext)
        return Result.success()
    }
}

class P53ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val s = P53ReminderStore(ctx).load()
        val kind = inputData.getString("kind") ?: return Result.success()
        val extra = inputData.getString("extra").orEmpty()
        val day = LocalDate.now().toEpochDay().toInt()
        when (kind) {
            P53ReminderScheduler.KIND_WARD -> {
                if (!s.ward) return Result.success()
                val session = SecureSessionStore(ctx).load() ?: return Result.success()
                val remaining = withContext(Dispatchers.IO) {
                    runCatching { SecureV2Api.today(session.token) }.getOrNull()
                }?.filter { it.status == "active" && it.assignmentId != null && !it.completed }
                // Already read everything today → stay silent.
                if (remaining != null && remaining.isEmpty()) return Result.success()
                val text = remaining?.take(3)?.joinToString(" • ") { w ->
                    w.groupName + ": " + (w.juz?.let { "الجزء $it" } ?: "ورد اليوم")
                } ?: "لا تنسَ ورد اليوم من القرآن الكريم."
                notify(ctx, 7300 + day % 1000, "لم يكتمل ورد اليوم", text)
            }
            P53ReminderScheduler.KIND_MORNING -> if (s.morningAdhkar)
                notify(ctx, 7310, "أذكار الصباح ☀", "حان وقت أذكار الصباح — دقائق تحفظ يومك.")
            P53ReminderScheduler.KIND_EVENING -> if (s.eveningAdhkar)
                notify(ctx, 7311, "أذكار المساء 🌙", "حان وقت أذكار المساء.")
            P53ReminderScheduler.KIND_PRE -> if (s.prePrayer)
                notify(ctx, 7320 + Math.floorMod(extra.hashCode(), 50), "اقترب وقت $extra",
                    "باقٍ ${s.prePrayerMinutes} دقائق على أذان $extra — استعد للصلاة.")
            P53ReminderScheduler.KIND_DUA -> if (s.dailyDua) {
                val dua = P53DailyDua.forToday(ctx)
                if (dua != null) notify(ctx, 7330, "دعاء اليوم", dua)
            }
            P53ReminderScheduler.KIND_KAHF -> if (s.fridayKahf) {
                val listen = PendingIntent.getForegroundService(
                    ctx, 7341, Intent(ctx, P52ListenService::class.java)
                        .setAction(P52ListenService.ACTION_PLAY_SURAH).putExtra(P52ListenService.EXTRA_SURAH, 18),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notify(ctx, 7340, "يوم الجمعة — سورة الكهف",
                    "«مَن قرأ سورةَ الكهفِ في يومِ الجمعةِ أضاءَ له من النورِ ما بين الجمعتَين».",
                    action = "استمع للكهف" to listen)
            }
        }
        return Result.success()
    }

    private fun notify(
        ctx: Context, id: Int, title: String, text: String,
        action: Pair<String, PendingIntent>? = null,
    ) {
        P53ReminderScheduler.ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(ctx, P53ReminderScheduler.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        action?.let { (label, intent) -> b.addAction(android.R.drawable.ic_media_play, label, intent) }
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(id, b.build()) }
    }
}

/** A short supplication from the bundled Hisn al-Muslim data (MIT), changing every day. */
object P53DailyDua {
    @Volatile private var pool: List<String>? = null

    private fun load(ctx: Context): List<String> {
        pool?.let { return it }
        val list = mutableListOf<String>()
        runCatching {
            val raw = ctx.assets.open("adhkar/hisn.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val title = keys.next()
                val plain = title.replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
                if (!plain.contains("دعاء")) continue
                val arr = root.optJSONObject(title)?.optJSONArray("Adhkar") ?: continue
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i)?.optString("Text")?.trim().orEmpty()
                    if (t.length in 20..230) list.add(t)
                }
            }
        }
        return list.also { pool = it }
    }

    fun forToday(ctx: Context): String? {
        val all = load(ctx)
        if (all.isEmpty()) return null
        val day = LocalDate.now().toEpochDay()
        return all[(day % all.size).toInt()]
    }
}
