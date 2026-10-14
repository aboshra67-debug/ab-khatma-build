package com.ab.khatma.quran

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ab.khatma.MainActivity

/** P52: a reciter whose full-surah recordings are 001.mp3 … 114.mp3 under [baseUrl]. */
data class P52Reciter(
    val id: String,
    val name: String,
    val style: String,
    val baseUrl: String,
    val section: String = "العربية",
) {
    fun surahUrl(surah: Int): String = baseUrl + surah.toString().padStart(3, '0') + ".mp3"
}

/** Every entry below was checked to have all 114 surahs (mp3quran.net / quranicaudio.com). */
object P52Reciters {
    private const val MP3Q = "https://cdn.mp3quran.net/audio/"
    private const val QA = "https://download.quranicaudio.com/quran/"
    val all: List<P52Reciter> = listOf(
        P52Reciter("husary_murattal", "محمود خليل الحصري", "مرتل", MP3Q + "mahmoud-husary/r1/"),
        P52Reciter("husary_mujawwad", "محمود خليل الحصري", "المصحف المجود", MP3Q + "mahmoud-husary/r2/"),
        P52Reciter("minshawi_murattal", "محمد صديق المنشاوي", "مرتل", MP3Q + "muhammad-minshawi/r1/"),
        P52Reciter("minshawi_mujawwad", "محمد صديق المنشاوي", "المصحف المجود", MP3Q + "muhammad-minshawi/r2/"),
        P52Reciter("banna_murattal", "محمود علي البنا", "مرتل", MP3Q + "mahmoud-banna/r1/"),
        P52Reciter("abdulbasit_murattal", "عبد الباسط عبد الصمد", "مرتل", QA + "abdul_basit_murattal/"),
        P52Reciter("alafasy", "مشاري العفاسي", "", MP3Q + "mishary-alafasy/r1/"),
        P52Reciter("maher", "ماهر المعيقلي", "", MP3Q + "maher-muaiqly/r1/"),
        P52Reciter("dossari", "ياسر الدوسري", "", QA + "yasser_ad-dussary/"),
        P52Reciter("qatami", "ناصر القطامي", "", QA + "nasser_bin_ali_alqatami/"),
        P52Reciter("juhany", "عبدالله الجهني", "", QA + "abdullaah_3awwaad_al-juhaynee/"),
        P52Reciter("basfar", "عبدالله بصفر", "", QA + "abdullaah_basfar/"),
        P52Reciter("huthaify", "علي الحذيفي", "", QA + "huthayfi/"),
        P52Reciter("ali_jaber", "علي جابر", "", QA + "abdullaah_alee_jaabir_studio/"),
        P52Reciter("ayyub", "محمد أيوب", "", MP3Q + "muhammad-ayyub/r1/"),
        P52Reciter("luhaidan", "محمد اللحيدان", "", MP3Q + "muhammad-luhaidan/r1/"),
        P52Reciter("yasser_salama", "ياسر سلامة", "", MP3Q + "yasser-salama/r1/"),
        P52Reciter("english_alafasy_walk", "معاني القرآن بالإنجليزية", "مشاري العفاسي مع إبراهيم ووك",
            QA + "mishaari_w_ibrahim_walk_si/", section = "الإنجليزية"),
    )
    const val DEFAULT_ID = "husary_murattal"
    fun byId(id: String?): P52Reciter = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }
}

/** P52: one shared full-surah player. It streams (no download, APK size unchanged),
 * keeps playing with the screen off through a media foreground service, and is
 * completely separate from the P42 verse-by-verse player. */
object P52SurahAudio {
    private const val PREFS = "p52_surah_listen"
    private const val KEY_RECITER = "reciter_id"
    const val CHANNEL = "ab_khatma_quran_listen"
    const val NOTIFICATION_ID = 8852

    var reciterId by mutableStateOf(P52Reciters.DEFAULT_ID)
        private set
    var queue by mutableStateOf<List<Int>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** Lets the Mushaf screen stop the verse player before a surah starts. */
    var beforePlay: (() -> Unit)? = null

    private var app: Context? = null
    private var player: MediaPlayer? = null
    private var surahNames: List<String> = emptyList()

    val reciter: P52Reciter get() = P52Reciters.byId(reciterId)
    val currentSurah: Int? get() = queue.getOrNull(index)
    val isActive: Boolean get() = currentSurah != null

    fun init(context: Context) {
        if (app != null) return
        val a = context.applicationContext
        app = a
        reciterId = P52Reciters.byId(
            a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECITER, null)
        ).id
        surahNames = runCatching { QuranRepository(a).surahs().sortedBy { it.index }.map { it.name } }
            .getOrDefault(emptyList())
    }

    fun surahName(surah: Int): String = surahNames.getOrNull(surah - 1) ?: "سورة $surah"

    fun selectReciter(context: Context, id: String) {
        init(context)
        val chosen = P52Reciters.byId(id)
        reciterId = chosen.id
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY_RECITER, chosen.id)?.apply()
        if (isActive) prepareCurrent()
    }

    /** Plays [surahs] in order starting at [start]. */
    fun play(context: Context, surahs: List<Int>, start: Int = 0) {
        init(context)
        val clean = surahs.filter { it in 1..114 }
        if (clean.isEmpty()) return
        beforePlay?.invoke()
        queue = clean
        index = start.coerceIn(0, clean.lastIndex)
        message = null
        app?.let { ctx ->
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, P52ListenService::class.java))
            }
        }
        prepareCurrent()
    }

    private fun prepareCurrent() {
        val ctx = app ?: return
        val surah = currentSurah ?: return
        releasePlayer()
        isLoading = true
        isPlaying = false
        val r = reciter
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(r.surahUrl(surah))
            p.setOnPreparedListener {
                if (player === it) {
                    isLoading = false
                    it.start()
                    isPlaying = true
                    refreshNotification()
                }
            }
            p.setOnCompletionListener {
                if (player === it) {
                    if (index + 1 < queue.size) {
                        index += 1
                        prepareCurrent()
                    } else stop()
                }
            }
            p.setOnErrorListener { mp, _, _ ->
                if (player === mp) {
                    isLoading = false
                    isPlaying = false
                    message = "تعذّر تشغيل سورة ${surahName(surah)} بصوت ${r.name}. تأكد من الإنترنت ثم حاول مرة أخرى."
                    refreshNotification()
                }
                true
            }
            player = p
            p.prepareAsync()
            refreshNotification()
        } catch (_: Exception) {
            isLoading = false
            isPlaying = false
            message = "تعذّر تشغيل التلاوة."
            refreshNotification()
        }
    }

    fun togglePause() {
        val p = player ?: run { if (isActive) prepareCurrent(); return }
        if (isLoading) return
        runCatching {
            if (p.isPlaying) { p.pause(); isPlaying = false }
            else { beforePlay?.invoke(); p.start(); isPlaying = true; message = null }
        }.onFailure { prepareCurrent() }
        refreshNotification()
    }

    /** Called when the verse player starts, so two recitations never overlap. */
    fun pause() {
        val p = player ?: return
        runCatching { if (p.isPlaying) p.pause() }
        isPlaying = false
        refreshNotification()
    }

    fun next() {
        if (index + 1 < queue.size) { index += 1; message = null; prepareCurrent() }
    }

    fun previous() {
        val p = player
        val position = runCatching { p?.currentPosition ?: 0 }.getOrDefault(0)
        if (position > 5_000 || index == 0) {
            runCatching { p?.seekTo(0) }
            if (!isPlaying) togglePause()
        } else { index -= 1; message = null; prepareCurrent() }
    }

    fun stop() {
        releasePlayer()
        queue = emptyList()
        index = -1
        isPlaying = false
        isLoading = false
        app?.let { ctx -> runCatching { ctx.stopService(Intent(ctx, P52ListenService::class.java)) } }
    }

    private fun releasePlayer() {
        player?.let { p ->
            runCatching { p.setOnPreparedListener(null) }
            runCatching { p.setOnCompletionListener(null) }
            runCatching { p.setOnErrorListener(null) }
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        player = null
    }

    internal fun buildNotification(context: Context): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "الاستماع إلى القرآن", NotificationManager.IMPORTANCE_LOW)
            )
        }
        fun action(code: Int, act: String) = PendingIntent.getService(
            context, code, Intent(context, P52ListenService::class.java).setAction(act),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            context, 8853,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val surah = currentSurah
        val title = if (surah != null) "سورة ${surahName(surah)}" else "الاستماع إلى القرآن"
        val text = when {
            isLoading -> "جارٍ التحميل… ${reciter.name}"
            message != null -> message!!
            else -> reciter.name + (if (reciter.style.isNotBlank()) " — ${reciter.style}" else "")
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_previous, "السابق", action(8854, P52ListenService.ACTION_PREV))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "إيقاف مؤقت" else "تشغيل", action(8855, P52ListenService.ACTION_TOGGLE)
            )
            .addAction(android.R.drawable.ic_media_next, "التالي", action(8856, P52ListenService.ACTION_NEXT))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إغلاق", action(8857, P52ListenService.ACTION_STOP))
            .setOngoing(isPlaying || isLoading)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun refreshNotification() {
        val ctx = app ?: return
        if (!isActive) return
        runCatching {
            val nm = NotificationManagerCompat.from(ctx)
            if (nm.areNotificationsEnabled()) nm.notify(NOTIFICATION_ID, buildNotification(ctx))
        }
    }
}

/** Keeps the full-surah player alive with the screen off; controls live in the notification. */
class P52ListenService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        P52SurahAudio.init(this)
        val notification = P52SurahAudio.buildNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(P52SurahAudio.NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(P52SurahAudio.NOTIFICATION_ID, notification)
        when (intent?.action) {
            ACTION_STOP -> {
                P52SurahAudio.stop()
                finish()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> P52SurahAudio.togglePause()
            ACTION_NEXT -> P52SurahAudio.next()
            ACTION_PREV -> P52SurahAudio.previous()
        }
        if (!P52SurahAudio.isActive) finish()
        return START_NOT_STICKY
    }

    private fun finish() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { NotificationManagerCompat.from(this).cancel(P52SurahAudio.NOTIFICATION_ID) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "com.ab.khatma.P52_LISTEN_TOGGLE"
        const val ACTION_NEXT = "com.ab.khatma.P52_LISTEN_NEXT"
        const val ACTION_PREV = "com.ab.khatma.P52_LISTEN_PREV"
        const val ACTION_STOP = "com.ab.khatma.P52_LISTEN_STOP"
    }
}
