package com.ab.khatma.quran

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ab.khatma.MainActivity

/** A reciter with full-surah files 001.mp3 … 114.mp3 under [baseUrl] and, when available,
 * verse files SSSAAA.mp3 on EveryAyah under [ayahFolder]. */
data class P52Reciter(
    val id: String,
    val name: String,
    val style: String,
    val baseUrl: String,
    val section: String = "مرتّل",
    val ayahFolder: String? = null,
) {
    fun surahUrl(surah: Int): String = baseUrl + surah.toString().padStart(3, '0') + ".mp3"
    fun ayahUrl(surah: Int, ayah: Int): String? = ayahFolder?.let {
        "https://everyayah.com/data/$it/" + surah.toString().padStart(3, '0') + ayah.toString().padStart(3, '0') + ".mp3"
    }
}

/** P55: every entry was checked to contain all 114 surahs (mp3quran.net / quranicaudio.com);
 * verse folders are the EveryAyah recitations list. */
object P52Reciters {
    private const val M = "https://cdn.mp3quran.net/audio/"
    private const val Q = "https://download.quranicaudio.com/quran/"
    private const val MUR = "مرتّل"
    private const val MUJ = "مجوّد"
    private const val MUA = "معلّم (للحفظ)"
    private const val RIW = "روايات أخرى"
    private const val ENG = "معاني بالإنجليزية"
    val all: List<P52Reciter> = listOf(
        // Egypt
        P52Reciter("husary_murattal", "محمود خليل الحصري", "مرتل", M + "mahmoud-husary/r1/", MUR, "Husary_128kbps"),
        P52Reciter("husary_mujawwad", "محمود خليل الحصري", "المصحف المجود", M + "mahmoud-husary/r2/", MUJ, "Husary_128kbps_Mujawwad"),
        P52Reciter("husary_muallim", "محمود خليل الحصري", "المصحف المعلم", Q + "husary_muallim/", MUA, "Husary_Muallim_128kbps"),
        P52Reciter("minshawi_murattal", "محمد صديق المنشاوي", "مرتل", M + "muhammad-minshawi/r1/", MUR, "Minshawy_Murattal_128kbps"),
        P52Reciter("minshawi_mujawwad", "محمد صديق المنشاوي", "المصحف المجود", M + "muhammad-minshawi/r2/", MUJ, "Minshawy_Mujawwad_192kbps"),
        P52Reciter("banna_murattal", "محمود علي البنا", "مرتل", M + "mahmoud-banna/r1/", MUR, "mahmoud_ali_al_banna_32kbps"),
        P52Reciter("abdulbasit_murattal", "عبد الباسط عبد الصمد", "مرتل", Q + "abdul_basit_murattal/", MUR, "Abdul_Basit_Murattal_192kbps"),
        P52Reciter("abdulbasit_mujawwad", "عبد الباسط عبد الصمد", "مجود", Q + "abdulbaset_mujawwad/", MUJ, "Abdul_Basit_Mujawwad_128kbps"),
        P52Reciter("mostafa_ismaeel", "مصطفى إسماعيل", "", Q + "mostafa_ismaeel/", MUJ, "Mustafa_Ismail_48kbps"),
        P52Reciter("tablawi", "محمد محمود الطبلاوي", "", Q + "mohammad_altablawi/", MUR, "Mohammad_al_Tablaway_128kbps"),
        P52Reciter("ayyub", "محمد أيوب", "", M + "muhammad-ayyub/r1/", MUR, "Muhammad_Ayyoub_128kbps"),
        // Haramain & Gulf
        P52Reciter("sudais", "عبد الرحمن السديس", "", Q + "abdurrahmaan_as-sudays/", MUR, "Abdurrahmaan_As-Sudais_192kbps"),
        P52Reciter("shuraym", "سعود الشريم", "", Q + "sa3ood_al-shuraym/", MUR, "Saood_ash-Shuraym_128kbps"),
        P52Reciter("maher", "ماهر المعيقلي", "", M + "maher-muaiqly/r1/", MUR, "MaherAlMuaiqly128kbps"),
        P52Reciter("dossari", "ياسر الدوسري", "", Q + "yasser_ad-dussary/", MUR, "Yasser_Ad-Dussary_128kbps"),
        P52Reciter("juhany", "عبدالله الجهني", "", Q + "abdullaah_3awwaad_al-juhaynee/", MUR, "Abdullaah_3awwaad_Al-Juhaynee_128kbps"),
        P52Reciter("huthaify", "علي الحذيفي", "", Q + "huthayfi/", MUR, "Hudhaify_128kbps"),
        P52Reciter("budair", "صلاح البدير", "", Q + "salahbudair/", MUR, "Salah_Al_Budair_128kbps"),
        P52Reciter("thubaity", "عبد الباري الثبيتي", "", Q + "thubaity/", MUR, null),
        P52Reciter("basfar", "عبدالله بصفر", "", Q + "abdullaah_basfar/", MUR, "Abdullah_Basfar_192kbps"),
        P52Reciter("ali_jaber", "علي جابر", "", Q + "abdullaah_alee_jaabir_studio/", MUR, "Ali_Jaber_64kbps"),
        P52Reciter("khalid_jalil", "خالد الجليل", "", M + "khalid-jalil/r1/", MUR, null),
        P52Reciter("luhaidan", "محمد اللحيدان", "", M + "muhammad-luhaidan/r1/", MUR, null),
        P52Reciter("alafasy", "مشاري العفاسي", "", M + "mishary-alafasy/r1/", MUR, "Alafasy_128kbps"),
        P52Reciter("ghamdi", "سعد الغامدي", "", Q + "sa3d_al-ghaamidi/complete/", MUR, "Ghamadi_40kbps"),
        P52Reciter("ajmy", "أحمد العجمي", "", Q + "ahmed_ibn_3ali_al-3ajamy/", MUR, "ahmed_ibn_ali_al_ajamy_128kbps"),
        P52Reciter("shatri", "أبو بكر الشاطري", "", Q + "abu_bakr_ash-shaatree/", MUR, "Abu_Bakr_Ash-Shaatree_128kbps"),
        P52Reciter("jibreel", "محمد جبريل", "", Q + "muhammad_jibreel/complete/", MUR, "Muhammad_Jibreel_128kbps"),
        P52Reciter("bukhatir", "صلاح بو خاطر", "", Q + "salaah_bukhaatir/", MUR, "Salaah_AbdulRahman_Bukhatir_128kbps"),
        P52Reciter("qatami", "ناصر القطامي", "", Q + "nasser_bin_ali_alqatami/", MUR, "Nasser_Alqatami_128kbps"),
        P52Reciter("yasser_salama", "ياسر سلامة", "", M + "yasser-salama/r1/", MUR, "Yaser_Salamah_128kbps"),
        P52Reciter("hani_rifai", "هاني الرفاعي", "", Q + "rifai/", MUR, "Hani_Rifai_192kbps"),
        P52Reciter("qahtani", "خالد القحطاني", "", Q + "khaalid_al-qahtaanee/", MUR, "Khaalid_Abdullaah_al-Qahtaanee_192kbps"),
        P52Reciter("idrees", "إدريس أبكر", "", M + "idrees-abkar/r1/", MUR, null),
        P52Reciter("akram", "أكرم العلاقمي", "", Q + "akram_al_alaqmi/", MUR, "Akram_AlAlaqimy_128kbps"),
        // Other narrations
        P52Reciter("abdulbasit_warsh", "عبد الباسط عبد الصمد", "رواية ورش", Q + "abdulbaset_warsh/", RIW, "warsh/warsh_Abdul_Basit_128kbps"),
        P52Reciter("husary_doori", "محمود خليل الحصري", "رواية الدوري", Q + "mahmood_khaleel_al-husaree_doori/", RIW, null),
        P52Reciter("huthaify_qaloon", "علي الحذيفي", "رواية قالون", Q + "huthayfi_qaloon/", RIW, null),
        // English meanings
        P52Reciter("english_alafasy_walk", "معاني القرآن بالإنجليزية", "مشاري العفاسي مع إبراهيم ووك",
            Q + "mishaari_w_ibrahim_walk_si/", ENG, null),
    )
    val sections = listOf(MUR, MUJ, MUA, RIW, ENG)
    const val DEFAULT_ID = "husary_murattal"
    fun byId(id: String?): P52Reciter = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }
}

/** Full-surah player: streams, survives the screen turning off (media foreground service),
 * and shows a real media control on the lock screen / notification shade. Headset buttons,
 * calls and «audio becoming noisy» pause it correctly. Separate from the verse player. */
object P52SurahAudio {
    private const val PREFS = "p52_surah_listen"
    private const val KEY_RECITER = "reciter_id"
    private const val KEY_FAVS = "favorite_reciters"
    const val CHANNEL = "ab_khatma_quran_listen"
    const val NOTIFICATION_ID = 8852

    var reciterId by mutableStateOf(P52Reciters.DEFAULT_ID)
        private set
    var favorites by mutableStateOf<Set<String>>(emptySet())
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
    /** A one-off reciter used only for the «sample» button; never saved. */
    var sampleReciterId by mutableStateOf<String?>(null)
        private set

    var sleepMinutes by mutableIntStateOf(0)
        private set
    private val main = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable { stop() }
    private val progressRunnable = object : Runnable {
        override fun run() { publishState(); if (isPlaying) main.postDelayed(this, 1_000) }
    }

    fun cycleSleepTimer() {
        val next = when (sleepMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
        main.removeCallbacks(sleepRunnable)
        sleepMinutes = next
        if (next > 0) main.postDelayed(sleepRunnable, next * 60_000L)
        refreshNotification()
    }

    var beforePlay: (() -> Unit)? = null

    private var app: Context? = null
    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var surahNames: List<String> = emptyList()
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocus = false
    private var noisyRegistered = false

    val reciter: P52Reciter get() = P52Reciters.byId(sampleReciterId ?: reciterId)
    val savedReciter: P52Reciter get() = P52Reciters.byId(reciterId)
    val currentSurah: Int? get() = queue.getOrNull(index)
    val isActive: Boolean get() = currentSurah != null

    fun init(context: Context) {
        if (app != null) return
        val a = context.applicationContext
        app = a
        val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        reciterId = P52Reciters.byId(p.getString(KEY_RECITER, null)).id
        favorites = p.getStringSet(KEY_FAVS, emptySet())!!.toSet()
        surahNames = runCatching { QuranRepository(a).surahs().sortedBy { it.index }.map { it.name } }
            .getOrDefault(emptyList())
    }

    fun surahName(surah: Int): String = surahNames.getOrNull(surah - 1) ?: "سورة $surah"

    fun toggleFavorite(context: Context, id: String) {
        init(context)
        favorites = if (id in favorites) favorites - id else favorites + id
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putStringSet(KEY_FAVS, favorites)?.apply()
    }

    fun selectReciter(context: Context, id: String) {
        init(context)
        val chosen = P52Reciters.byId(id)
        reciterId = chosen.id
        sampleReciterId = null
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY_RECITER, chosen.id)?.apply()
        if (isActive) prepareCurrent()
    }

    /** Plays al-Fatiha with [id] once, without changing the saved reciter. */
    fun playSample(context: Context, id: String) = play(context, listOf(1), sampleId = id)

    fun play(context: Context, surahs: List<Int>, start: Int = 0, sampleId: String? = null) {
        init(context)
        val clean = surahs.filter { it in 1..114 }
        if (clean.isEmpty()) return
        beforePlay?.invoke()
        sampleReciterId = sampleId
        queue = clean
        index = start.coerceIn(0, clean.lastIndex)
        message = null
        app?.let { ctx ->
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, P52ListenService::class.java)) }
        }
        prepareCurrent()
    }

    private fun ensureSession(ctx: Context): MediaSession {
        session?.let { return it }
        val s = MediaSession(ctx, "ABKhatmaQuran")
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { if (!isPlaying) togglePause() }
            override fun onPause() { pause() }
            override fun onSkipToNext() { next() }
            override fun onSkipToPrevious() { previous() }
            override fun onStop() { stop() }
            override fun onSeekTo(pos: Long) { runCatching { player?.seekTo(pos.toInt()) }; publishState() }
        })
        s.setSessionActivity(PendingIntent.getActivity(
            ctx, 8858, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        s.isActive = true
        session = s
        return s
    }

    private fun audioManager(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocus = false; pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (isPlaying) { resumeOnFocus = true; pause() }
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocus) { resumeOnFocus = false; togglePause() }
            }
        }
    }

    private fun requestFocus(ctx: Context): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener, main)
            .build().also { focusRequest = it }
        return audioManager(ctx).requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private fun setNoisy(ctx: Context, on: Boolean) {
        if (on == noisyRegistered) return
        runCatching {
            if (on) ContextCompat.registerReceiver(ctx, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            else ctx.unregisterReceiver(noisyReceiver)
            noisyRegistered = on
        }
    }

    private fun prepareCurrent() {
        val ctx = app ?: return
        val surah = currentSurah ?: return
        releasePlayer()
        isLoading = true
        isPlaying = false
        ensureSession(ctx)
        val r = reciter
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(r.surahUrl(surah))
            p.setOnPreparedListener {
                if (player === it) {
                    isLoading = false
                    if (requestFocus(ctx)) {
                        it.start()
                        isPlaying = true
                        setNoisy(ctx, true)
                    }
                    publishMetadata()
                    publishState()
                    main.removeCallbacks(progressRunnable); main.post(progressRunnable)
                    refreshNotification()
                }
            }
            p.setOnCompletionListener {
                if (player === it) {
                    if (index + 1 < queue.size) { index += 1; prepareCurrent() }
                    else stop()
                }
            }
            p.setOnErrorListener { mp, _, _ ->
                if (player === mp) {
                    isLoading = false
                    isPlaying = false
                    message = "تعذّر تشغيل سورة ${surahName(surah)} بصوت ${r.name}. تأكد من الإنترنت ثم حاول مرة أخرى."
                    publishState()
                    refreshNotification()
                }
                true
            }
            player = p
            p.prepareAsync()
            publishMetadata()
            publishState()
            refreshNotification()
        } catch (_: Exception) {
            isLoading = false
            isPlaying = false
            message = "تعذّر تشغيل التلاوة."
            refreshNotification()
        }
    }

    fun togglePause() {
        val ctx = app ?: return
        val p = player ?: run { if (isActive) prepareCurrent(); return }
        if (isLoading) return
        runCatching {
            if (p.isPlaying) { p.pause(); isPlaying = false; setNoisy(ctx, false) }
            else if (requestFocus(ctx)) {
                beforePlay?.invoke(); p.start(); isPlaying = true; message = null; setNoisy(ctx, true)
                main.removeCallbacks(progressRunnable); main.post(progressRunnable)
            }
        }.onFailure { prepareCurrent() }
        publishState()
        refreshNotification()
    }

    fun pause() {
        val p = player ?: return
        runCatching { if (p.isPlaying) p.pause() }
        isPlaying = false
        app?.let { setNoisy(it, false) }
        publishState()
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
            if (!isPlaying) togglePause() else publishState()
        } else { index -= 1; message = null; prepareCurrent() }
    }

    fun stop() {
        main.removeCallbacks(sleepRunnable)
        main.removeCallbacks(progressRunnable)
        sleepMinutes = 0
        releasePlayer()
        queue = emptyList()
        index = -1
        isPlaying = false
        isLoading = false
        sampleReciterId = null
        app?.let { ctx ->
            setNoisy(ctx, false)
            focusRequest?.let { runCatching { audioManager(ctx).abandonAudioFocusRequest(it) } }
            runCatching { ctx.stopService(Intent(ctx, P52ListenService::class.java)) }
        }
        session?.let { s ->
            runCatching { s.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build()) }
            runCatching { s.isActive = false; s.release() }
        }
        session = null
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

    private fun publishMetadata() {
        val s = session ?: return
        val surah = currentSurah ?: return
        val r = reciter
        val duration = runCatching { if (!isLoading) player?.duration?.toLong() ?: -1L else -1L }.getOrDefault(-1L)
        s.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "سورة " + surahName(surah))
            .putString(MediaMetadata.METADATA_KEY_ARTIST, r.name + (if (r.style.isNotBlank()) " — ${r.style}" else ""))
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "القرآن الكريم")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            .build())
    }

    private fun publishState() {
        val s = session ?: return
        val pos = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
        val state = when {
            isLoading -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            isActive -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO)
            .setState(state, pos, if (isPlaying) 1f else 0f)
            .build())
    }

    internal fun buildNotification(context: Context): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "الاستماع إلى القرآن", NotificationManager.IMPORTANCE_LOW))
        }
        fun pi(code: Int, act: String) = PendingIntent.getService(
            context, code, Intent(context, P52ListenService::class.java).setAction(act),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(icon: Int, label: String, code: Int, act: String) =
            Notification.Action.Builder(Icon.createWithResource(context, icon), label, pi(code, act)).build()
        val open = PendingIntent.getActivity(context, 8853,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val surah = currentSurah
        val r = reciter
        val title = if (surah != null) "سورة ${surahName(surah)}" else "الاستماع إلى القرآن"
        val text = when {
            isLoading -> "جارٍ التحميل… ${r.name}"
            message != null -> message!!
            else -> r.name + (if (r.style.isNotBlank()) " — ${r.style}" else "") +
                (if (sleepMinutes > 0) "  •  ⏲ $sleepMinutes د" else "")
        }
        val s = app?.let { ensureSession(it) } ?: ensureSession(context.applicationContext)
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying || isLoading)
            .setOnlyAlertOnce(true)
            .addAction(action(android.R.drawable.ic_media_previous, "السابق", 8854, P52ListenService.ACTION_PREV))
            .addAction(if (isPlaying) action(android.R.drawable.ic_media_pause, "إيقاف مؤقت", 8855, P52ListenService.ACTION_TOGGLE)
                       else action(android.R.drawable.ic_media_play, "تشغيل", 8855, P52ListenService.ACTION_TOGGLE))
            .addAction(action(android.R.drawable.ic_media_next, "التالي", 8856, P52ListenService.ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "إغلاق", 8857, P52ListenService.ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1, 2))
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

class P52ListenService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        P52SurahAudio.init(this)
        val notification = P52SurahAudio.buildNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(P52SurahAudio.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(P52SurahAudio.NOTIFICATION_ID, notification)
        when (intent?.action) {
            ACTION_STOP -> { P52SurahAudio.stop(); finish(); return START_NOT_STICKY }
            ACTION_PLAY_SURAH -> P52SurahAudio.play(this, listOf(intent?.getIntExtra(EXTRA_SURAH, 18) ?: 18))
            ACTION_TOGGLE -> P52SurahAudio.togglePause()
            ACTION_NEXT -> P52SurahAudio.next()
            ACTION_PREV -> P52SurahAudio.previous()
        }
        if (!P52SurahAudio.isActive) finish()
        return START_NOT_STICKY
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
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
        const val ACTION_PLAY_SURAH = "com.ab.khatma.P53_LISTEN_PLAY_SURAH"
        const val EXTRA_SURAH = "surah"
    }
}
