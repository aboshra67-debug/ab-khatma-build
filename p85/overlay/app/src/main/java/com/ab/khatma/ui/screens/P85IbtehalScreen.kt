package com.ab.khatma.ui.screens

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ab.khatma.guide.P85Guide
import com.ab.khatma.quran.P52SurahAudio

/** User-started live listening. No recordings copied, downloaded, or auto-played. */
@Composable
internal fun P85IbtehalScreen(onBack: () -> Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val handler=remember { Handler(Looper.getMainLooper()) }
    val manager=remember { context.getSystemService(AudioManager::class.java) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var focus by remember { mutableStateOf<AudioFocusRequest?>(null) }
    var state by remember { mutableStateOf("اضغط استماع لتشغيل البث") }
    var loading by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    fun stop() {
        val old=player;player=null
        old?.runCatching { setOnPreparedListener(null);setOnErrorListener(null);release() }
        focus?.let { manager.abandonAudioFocusRequest(it) };focus=null
        loading=false;playing=false
    }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
            .onFailure { state="تعذّر فتح المصدر؛ جرّب من متصفح الهاتف." }
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            if (event==Lifecycle.Event.ON_STOP) { stop();state="البث متوقف؛ اضغط استماع للمتابعة" }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer);stop();handler.removeCallbacksAndMessages(null) }
    }
    BackHandler { stop();onBack() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(18.dp)
        .verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("الابتهالات والتواشيح",fontSize=25.sp,fontWeight=FontWeight.Bold)
        Text("بث مباشر • يحتاج إنترنت",fontSize=14.sp)
        P85Guide.HelpButton("ibtehal")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("إذاعة الابتهالات",fontSize=21.sp,fontWeight=FontWeight.Bold)
                Text("ابتهالات وتواشيح من محطة EBTEHALAT على Zeno.FM. المحتوى والبرنامج يحددهما مصدر البث.",fontSize=14.sp,lineHeight=23.sp)
                Text(state,fontSize=14.sp)
                if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Button(enabled=!loading&&!playing,onClick={
                        stop();loading=true;state="جارٍ الاتصال بالبث…"
                        val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                        val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(attrs).setOnAudioFocusChangeListener { change ->
                                if(change<0) { stop();state="البث متوقف أثناء استخدام الصوت" }
                            }.build()
                        focus=request
                        if(manager.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            stop();state="الصوت مستخدم الآن؛ حاول بعد انتهاء التشغيل الحالي."
                        } else try {
                            P52SurahAudio.pause()
                            val p=MediaPlayer();player=p
                            p.setAudioAttributes(attrs)
                            p.setDataSource("https://stream.zeno.fm/hntmdjmxhihtv")
                            p.setOnPreparedListener { ready ->
                                if(player===ready) runCatching { ready.start();loading=false;playing=true;state="البث شغّال" }
                                    .onFailure { stop();state="تعذّر تشغيل البث؛ حاول تاني." }
                            }
                            p.setOnErrorListener { failed,_,_ ->
                                if(player===failed) { stop();state="البث غير متاح حاليًا؛ حاول تاني أو افتح المصدر." };true
                            }
                            p.setOnCompletionListener { done -> if(player===done) { stop();state="انتهى البث؛ اضغط استماع للمحاولة." } }
                            p.prepareAsync()
                            handler.postDelayed({ if(player===p&&loading) { stop();state="الاتصال اتأخر؛ راجع الإنترنت وحاول تاني." } },30_000L)
                        } catch(_:Exception) { stop();state="تعذّر الاتصال بالبث؛ راجع الإنترنت وحاول تاني." }
                    }) { Text("▶ استماع") }
                    OutlinedButton(onClick={stop();state="تم إيقاف البث"},enabled=loading||playing) { Text("إيقاف") }
                }
                Text("التشغيل أثناء بقاء القسم مفتوحًا. قد يعرض المصدر محتوى أو إعلانات من اختياره.",fontSize=12.sp,lineHeight=19.sp)
                TextButton(onClick={stop();open("https://zeno.fm/radio/EBTEHALAT1/")}) { Text("فتح صفحة مصدر البث") }
            }
        }
        Text("من أرشيف إذاعة القرآن الكريم",fontWeight=FontWeight.Bold,fontSize=19.sp)
        OutlinedButton(onClick={stop();open("https://misrquran.gov.eg/audio-details/01dbeb13-8952-47c6-85ed-02f64cbc6ab6")},modifier=Modifier.fillMaxWidth()) { Text("ابتهالات وأذان الفجر ↗") }
        OutlinedButton(onClick={stop();open("https://misrquran.gov.eg/audio-details/f1674eab-5c86-4cf2-bdb0-36de8bc3d3fa")},modifier=Modifier.fillMaxWidth()) { Text("ابتهالات من مسجد السيدة زينب ↗") }
        P53BottomNav(onBack={stop();onBack()},onHome={stop();onBack()})
    }
}
