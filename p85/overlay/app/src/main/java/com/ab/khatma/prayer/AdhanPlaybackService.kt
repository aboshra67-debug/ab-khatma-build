package com.ab.khatma.prayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioFocusRequest
import com.ab.khatma.guide.P85Rules
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.app.NotificationCompat
import com.ab.khatma.MainActivity
import com.ab.khatma.R

object AdhanPlaybackStatus {
    var text by mutableStateOf("")
    var active by mutableStateOf(false)
    var position by mutableIntStateOf(0)
    var duration by mutableIntStateOf(0)
    var stopSerial by mutableIntStateOf(0)
}

/** Foreground playback with a visible Stop action; no network or background tracking. */
class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var activeKey: String? = null
    private var resumeOnFocus = false
    private var generation=0L
    private var prepared=false
    private var focusAvailable=false
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable {
        override fun run() {
            val current=player?:return
            current.runCatching { AdhanPlaybackStatus.position=currentPosition }
            if (player===current) handler.postDelayed(this,500L)
        }
    }
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private fun focusListener(owner:Long) = AudioManager.OnAudioFocusChangeListener { change ->
        if (owner != generation) return@OnAudioFocusChangeListener
        val p = player
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { AdhanPlaybackStatus.text="تم إيقاف الأذان لاستخدام الصوت"; releasePlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusAvailable=false;resumeOnFocus=true
                if (p?.runCatching { isPlaying }?.getOrDefault(false) == true) {
                    resumeOnFocus = true; p.pause(); AdhanPlaybackStatus.text = "الأذان متوقف مؤقتًا أثناء استخدام الصوت"
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> p?.runCatching { val v=minOf(AdhanPreferences(this@AdhanPlaybackService).volume,.15f);setVolume(v,v) }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusAvailable=true
                val v = AdhanPreferences(this).volume
                if (prepared) p?.runCatching { setVolume(v, v); if (resumeOnFocus) start() }
                    ?.onFailure { if (player===p) failPlayback("focus_resume") }
                if (prepared) resumeOnFocus = false
                if (p != null && prepared) AdhanPlaybackStatus.text = "الأذان يعمل — سيستمر حتى نهاية التسجيل"
            }
        }
    }

    private fun releasePlayback() {
        generation++; prepared=false;focusAvailable=false
        handler.removeCallbacks(tick)
        val old = player; player = null; activeKey = null; resumeOnFocus = false
        old?.runCatching { setOnCompletionListener(null); setOnPreparedListener(null); setOnErrorListener(null); release() }
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        val oldFocus=focus;focus=null
        oldFocus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        AdhanPlaybackStatus.active = false
    }

    private fun failPlayback(code: String) {
        getSharedPreferences("p85_adhan_diagnostics", MODE_PRIVATE).edit()
            .putString("error", code).putLong("at", System.currentTimeMillis()).apply()
        AdhanPlaybackStatus.text = "تعذّر إكمال الأذان. جرّب تسجيلًا آخر وراجع مستوى صوت المنبّه في الهاتف."
        releasePlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AdhanPlaybackStatus.stopSerial++
            releasePlayback()
            AdhanPlaybackStatus.text = "تم إيقاف الأذان"
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_VOLUME) {
            val v = AdhanPreferences(this).volume
            player?.runCatching { setVolume(v, v) }
            if (player == null) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) { stopSelf(); return START_NOT_STICKY }
        val prayer = intent?.getStringExtra(EXTRA_PRAYER)?.takeIf { it in AdhanPreferences.PRAYERS } ?: "تجربة"
        val voice = AdhanPreferences(this).voiceId
        val nextKey = "$prayer:${intent.getLongExtra(AdhanAlarmReceiver.EXTRA_EPOCH, 0L)}:$voice"
        if (P85Rules.keepCurrent(activeKey, nextKey, prayer == "تجربة") && player != null) return START_NOT_STICKY
        createChannel()
        val stop = PendingIntent.getService(this, 852, Intent(this, javaClass).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 853,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, PLAYBACK_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("الأذان الكامل — $prayer")
            .setContentText("صوت الأذان شغّال الآن — إيقاف بضغطة واحدة")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(852, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(852, notification)
        try {
            releasePlayback()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener(generation)).build()
            focus = request
            val granted = getSystemService(AudioManager::class.java).requestAudioFocus(request)
            if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                failPlayback("audio_focus_denied"); return START_NOT_STICKY
            }
            focusAvailable=true
            val p = MediaPlayer()
            player = p; activeKey = nextKey
            AdhanPlaybackStatus.position=0;AdhanPlaybackStatus.duration=0
            AdhanPlaybackStatus.active = true
            AdhanPlaybackStatus.text = "جارٍ تجهيز الأذان الكامل…"
            p.setAudioAttributes(attributes)
            p.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            val chosen = when (voice) {
                "makkah" -> R.raw.adhan_makkah
                "classic" -> R.raw.adhan_classic
                "madinah" -> R.raw.adhan_madinah
                "shalqar" -> R.raw.adhan_shalqar
                else -> R.raw.adhan_full
            }
            val downloaded=P85NamedAdhan.file(this,voice)
            if (P85NamedAdhan.voices.any { it.id==voice } && downloaded==null) {
                failPlayback("named_recording_unavailable");return START_NOT_STICKY
            }
            if (downloaded!=null) p.setDataSource(downloaded.absolutePath)
            else p.setDataSource(this, Uri.parse("android.resource://$packageName/$chosen"))
            p.isLooping = false
            p.setOnPreparedListener { ready ->
                if (player === ready) runCatching {
                    prepared=true
                    val volume = AdhanPreferences(this).volume
                    ready.setVolume(volume, volume)
                    wakeLock?.let { if (it.isHeld) it.release() }
                    wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ABKhatma:AdhanAudio").also {
                            it.acquire(P85Rules.wakeLeaseMs(ready.duration.toLong()))
                        }
                    AdhanPlaybackStatus.duration=ready.duration
                    if (focusAvailable) {
                        ready.start()
                        AdhanPlaybackStatus.text = "الأذان يعمل — سيستمر حتى نهاية التسجيل"
                    } else {
                        resumeOnFocus=true
                        AdhanPlaybackStatus.text = "الأذان متوقف مؤقتًا أثناء استخدام الصوت"
                    }
                    handler.post(tick)
                }.onFailure { if (player===ready) failPlayback("prepared_start") }
            }
            p.setOnCompletionListener { finished ->
                if (player === finished) {
                    AdhanPlaybackStatus.position=AdhanPlaybackStatus.duration
                    AdhanPlaybackStatus.text = "اكتمل الأذان"
                    releasePlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
            p.setOnErrorListener { failed, what, extra ->
                if (player === failed) failPlayback("media_${what}_$extra")
                true
            }
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ABKhatma:AdhanPrepare").also { it.acquire(30_000L) }
            p.prepareAsync()
        } catch (e: Exception) { failPlayback(e.javaClass.simpleName) }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(PLAYBACK_CHANNEL, "التحكم في الأذان", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }

    companion object {
        const val ACTION_VOLUME = "com.ab.khatma.ADHAN_VOLUME"
        const val ACTION_START = "com.ab.khatma.ADHAN_START"
        const val ACTION_STOP = "com.ab.khatma.ADHAN_STOP"
        const val EXTRA_PRAYER = "prayer_name"
        const val PLAYBACK_CHANNEL = "ab_khatma_adhan_playback_controls_v2"
    }
}
