package com.ab.khatma.ui.screens

import kotlinx.coroutines.launch

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.LocationListener
import android.location.Location
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ab.khatma.R
import com.ab.khatma.prayer.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val Emerald = Color(0xFF00695F)
private val DeepGreen = Color(0xFF004C46)
private val Ink = Color(0xFF12283C)
private val SoftText = Color(0xFF65747D)
private val Ivory = Color(0xFFF8F6F0)
private val Lavender = Color(0xFFF2EAF7)

/** P37.1: compact prayer UI + independent full adhan / entry notifications. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PrayerPremiumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prayerStore = remember { PrayerSettingsStore(context) }
    val correctionStore = remember { PrayerTimeCorrectionsStore(context) }
    val adhanPrefs = remember { AdhanPreferences(context) }
    var adhanEnabled by remember { mutableStateOf(adhanPrefs.enabled) }
    var audioEnabled by remember { mutableStateOf(adhanPrefs.audioEnabled) }
    var adhanVolume by remember { mutableFloatStateOf(adhanPrefs.volume) }
    var selectedAdhanVoice by remember { mutableStateOf(adhanPrefs.voiceId) }
    var silentPrayers by remember { mutableStateOf(AdhanPreferences.PRAYERS.associateWith { !adhanPrefs.audioEnabledFor(it) }) }
    var prayerAlertStates by remember {
        mutableStateOf(AdhanPreferences.PRAYERS.associateWith { adhanPrefs.enabledFor(it) })
    }
    var exactAlarmGranted by remember { mutableStateOf(AdhanAlarmScheduler.exactAlarmAvailable(context)) }
    var notificationsAllowed by remember { mutableStateOf(
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val activity = context as? Activity
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val previousStatusIcons = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = AdhanAlarmScheduler.exactAlarmAvailable(context)
                notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                AdhanAlarmScheduler.schedule(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (previousStatusIcons != null) controller?.isAppearanceLightStatusBars = previousStatusIcons
        }
    }

    val uiPrefs = remember { context.getSharedPreferences("khatma_prayer_p36_ui", Context.MODE_PRIVATE) }
    var settings by remember { mutableStateOf(prayerStore.load()) }
    var corrections by remember { mutableStateOf(correctionStore.all()) }
    var gpsEnabled by remember { mutableStateOf(uiPrefs.getBoolean("auto_location", false)) }
    var showCityPicker by remember { mutableStateOf(false) }
    var cityQuery by remember { mutableStateOf("") }
    var showSoundInfo by remember { mutableStateOf(false) }
    val p85AudioScope = rememberCoroutineScope()
    var p85Downloading by remember { mutableStateOf<String?>(null) }
    var p85VoiceError by remember { mutableStateOf("") }
    var p85ChoiceGeneration by remember { mutableIntStateOf(0) }
    var locationInfo by remember { mutableStateOf<String?>(null) }
    var locatingNow by remember { mutableStateOf(false) }
    var locationRequestNumber by remember { mutableIntStateOf(0) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    fun persist(next: PrayerSettings) {
        if (next == settings) return
        settings = next
        prayerStore.save(next)
        PrayerNotificationScheduler.scheduleRollingWindow(context)
        AdhanAlarmScheduler.schedule(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            gpsEnabled = true
            uiPrefs.edit().putBoolean("auto_location", true).apply()
            locationRequestNumber++
        } else {
            gpsEnabled = false
            uiPrefs.edit().putBoolean("auto_location", false).apply()
            locationInfo = "إذن الموقع غير متاح. يمكنك اختيار المدينة يدويًا."
        }
    }

    fun startGps() {
        if (hasPrayerLocationPermission(context)) {
            gpsEnabled = true
            uiPrefs.edit().putBoolean("auto_location", true).apply()
            locationRequestNumber++
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Only when the user opens/enables this page: no background tracking or
    // cross-module permissions; drop old cached GPS values to avoid wrong times.
    LaunchedEffect(gpsEnabled, locationRequestNumber) {
        if (!gpsEnabled) {
            locatingNow = false
            return@LaunchedEffect
        }
        if (!hasPrayerLocationPermission(context)) {
            gpsEnabled = false
            uiPrefs.edit().putBoolean("auto_location", false).apply()
            locationInfo = "امنح التطبيق إذن الموقع، أو اختر مدينة يدويًا."
            return@LaunchedEffect
        }
        locatingNow = true
        locationInfo = "جارٍ تحديد موقع حديث…"
        try {
            val fresh = currentPrayerLocation(context)
            if (fresh != null) {
                // The Android zone follows the phone setting. It can differ from
                // geographic timezone when roaming; show that explicitly.
                val zone = TimeZone.getDefault().id
                persist(settings.copy(location = PrayerLocation("موقعي الحالي", fresh.first, fresh.second, zone)))
                locationInfo = "تم تحديث الموقع. المنطقة الزمنية حسب إعداد الهاتف ($zone)."
            } else {
                locationInfo = "تعذر تحديد موقع حديث. فعّل GPS أو اختر المدينة يدويًا؛ لن نستخدم إحداثيات قديمة."
            }
        } finally {
            locatingNow = false
        }
    }

    fun selectLocation(location: PrayerLocation) {
        gpsEnabled = false
        uiPrefs.edit().putBoolean("auto_location", false).apply()
        locationInfo = null
        persist(settings.copy(location = location))
    }

    fun changeCorrection(name: String, delta: Int) {
        val updated = ((corrections[name] ?: 0) + delta).coerceIn(-30, 30)
        correctionStore.set(name, updated)
        corrections = corrections.toMutableMap().also { it[name] = updated }
    }

    // Coalesce repeated +/- taps so WorkManager is not enqueued for each tap.
    LaunchedEffect(corrections) {
        delay(350L)
        PrayerNotificationScheduler.scheduleRollingWindow(context)
        AdhanAlarmScheduler.schedule(context)
    }

    val zone = remember(settings.location.timeZoneId) {
        runCatching { ZoneId.of(settings.location.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
    }
    val currentDate = remember(nowMillis / 60_000L, zone) { LocalDate.now(zone) }
    val today = remember(settings.location, currentDate) {
        runCatching { PrayerCalculator.calculate(settings.location, currentDate) }.getOrNull()
    }
    val tomorrow = remember(settings.location, currentDate) {
        runCatching { PrayerCalculator.calculate(settings.location, currentDate.plusDays(1)) }.getOrNull()
    }
    val next = remember(today, tomorrow, corrections, nowMillis / 1000L) {
        (today?.entries().orEmpty() + tomorrow?.entries().orEmpty())
            .map { (name, epoch) -> name to epoch + (corrections[name] ?: 0) * 60_000L }
            .filter { it.second > nowMillis }
            .minByOrNull { it.second }
    }

    val featuredCities = remember {
        listOf(
            EgyptCities.all[0], EgyptCities.all[2], EgyptCities.all[1],
            PrayerLocation("الرياض", 24.7136, 46.6753, "Asia/Riyadh"),
            PrayerLocation("مكة", 21.3891, 39.8579, "Asia/Riyadh"),
            PrayerLocation("دبي", 25.2048, 55.2708, "Asia/Dubai"),
        )
    }

    // The compact city picker is kept out of the scrolling page until requested.
    val allAvailableCities = remember(featuredCities) { (featuredCities + EgyptCities.all).distinctBy { it.name } }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().background(Ivory)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 7.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    com.ab.khatma.guide.P85Guide.HelpButton("prayer")
                    Text("مواقيت الصلاة والأذان", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(1.dp))
                    Text("مواقيت دقيقة • أذان كامل • إشعارات الصلاة", color = SoftText, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
            item {
                PrayerHeroCard(
                    prayer = next?.first ?: "المواقيت",
                    date = if (next != null) SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone(settings.location.timeZoneId) }.format(Date(next.second)) else currentDate.toString(),
                    countdown = next?.let { formatPrayerCountdown(it.second - nowMillis) } ?: "--:--:--",
                    city = settings.location.name,
                    onUseLocation = ::startGps,
                    isLocating = locatingNow,
                )
            }
            item {
                PrayerWhiteCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Emerald, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text("المدينة الحالية", color = SoftText, fontSize = 11.sp)
                            Text(settings.location.name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { cityQuery = ""; showCityPicker = true },
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text("تغيير", color = Emerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.width(3.dp))
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = Emerald, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F1EF))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("GPS تلقائي حسب مكانك", color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = gpsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) startGps() else {
                                    gpsEnabled = false
                                    uiPrefs.edit().putBoolean("auto_location", false).apply()
                                }
                            },
                            modifier = Modifier.scale(.70f),
                            colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                        )
                    }
                    if (locatingNow) {
                        Text("جارٍ تحديد موقعك…", color = Emerald, fontSize = 11.sp)
                    } else {
                        locationInfo?.let { Text(it, color = SoftText, fontSize = 11.sp, lineHeight = 16.sp) }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(21.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECF5F1)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, null, tint = DeepGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إشعارات الصلاة والأذان", color = Ink, fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Switch(
                                checked = adhanEnabled,
                                onCheckedChange = { enabled ->
                                    adhanPrefs.enabled = enabled
                                    adhanEnabled = enabled
                                    AdhanAlarmScheduler.schedule(context)
                                },
                                modifier = Modifier.scale(.73f),
                                colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("تشغيل صوت الأذان مع دخول الصلاة", color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = audioEnabled,
                                onCheckedChange = { enabled -> adhanPrefs.audioEnabled = enabled; audioEnabled = enabled },
                                enabled = adhanEnabled,
                                modifier = Modifier.scale(.68f),
                                colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedButton(
                                onClick = {
                                    ContextCompat.startForegroundService(context,
                                        Intent(context, AdhanPlaybackService::class.java)
                                            .setAction(AdhanPlaybackService.ACTION_START)
                                            .putExtra(AdhanPlaybackService.EXTRA_PRAYER, "تجربة"))
                                },
                                modifier = Modifier.height(37.dp),
                                shape = RoundedCornerShape(13.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                            ) { Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("تجربة الأذان", fontSize = 11.sp) }
                            OutlinedButton(
                                onClick = { p85ChoiceGeneration++; context.startService(Intent(context, AdhanPlaybackService::class.java)
                                    .setAction(AdhanPlaybackService.ACTION_STOP)) },
                                modifier = Modifier.height(37.dp),
                                shape = RoundedCornerShape(13.dp),
                                contentPadding = PaddingValues(horizontal = 9.dp),
                            ) { Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("إيقاف الآن", fontSize = 11.sp) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showSoundInfo = true }, contentPadding = PaddingValues(horizontal = 3.dp)) {
                                Text("الأصوات والضبط", color = Emerald, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        HorizontalDivider(color = Color(0xFFD2E5DD))
                        Text("صوت الأذان لكل صلاة", color = DeepGreen, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        Text("يمكنك كتم الصوت في صلاة معيّنة مع استمرار إشعار دخول الوقت.",
                            color = SoftText, fontSize = 11.sp, lineHeight = 16.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AdhanPreferences.PRAYERS.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { prayer ->
                                        val silenced = silentPrayers[prayer] == true
                                        val selected = !silenced
                                        Surface(
                                            modifier = Modifier.weight(1f).height(38.dp).clickable(enabled = adhanEnabled) {
                                                adhanPrefs.setAudioEnabledFor(prayer, !selected)
                                                silentPrayers = silentPrayers.toMutableMap().also { it[prayer] = selected }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (selected && adhanEnabled) Color(0xFFD8EDE4) else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp,
                                                if (selected && adhanEnabled) Emerald else Color(0xFFE2E8E5)),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("$prayer ${if (selected) "🔊" else "صامت"}",
                                                    color = if (selected && adhanEnabled) DeepGreen else SoftText,
                                                    fontSize = 11.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        if (!notificationsAllowed) {
                            Text("اسمح بالإشعارات من إعدادات الهاتف حتى تظهر تنبيهات الصلاة.",
                                color = Color(0xFF9A4B20), fontSize = 11.sp)
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                })
                            }) { Text("السماح بالإشعارات", fontSize = 12.sp) }
                        }
                        if (!exactAlarmGranted && Build.VERSION.SDK_INT >= 31) {
                            Text("للدقة في المواعيد، اسمح بالمنبّهات الدقيقة. دون الإذن قد يتأخر الأذان.",
                                color = Color(0xFF9A4B20), fontSize = 11.sp, lineHeight = 16.sp)
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${context.packageName}"))) }
                            }) { Text("السماح بالمنبّهات الدقيقة", fontSize = 12.sp) }
                        }
                    }
                }
            }
            item {
                PrayerWhiteCard {
                    PrayerCardTitle("مواقيت اليوم", "☪")
                    Spacer(Modifier.height(7.dp))
                    if (today == null) {
                        Text("تعذّر حساب مواقيت هذا الموقع. اختر مدينة أخرى.", color = SoftText)
                    } else {
                        today.entries().forEach { (name, epoch) ->
                            val enabled = prayerAlertStates[name] ?: true
                            PrayerTimeEntry(
                                name = name,
                                time = formatPrayerTime(epoch + (corrections[name] ?: 0) * 60_000L, settings.location.timeZoneId),
                                correction = corrections[name] ?: 0,
                                enabled = enabled,
                                onToggle = { value ->
                                    adhanPrefs.setEnabledFor(name, value)
                                    prayerAlertStates = prayerAlertStates.toMutableMap().also { it[name] = value }
                                    AdhanAlarmScheduler.schedule(context)
                                },
                                onAdjust = { delta -> changeCorrection(name, delta) },
                            )
                            Spacer(Modifier.height(7.dp))
                        }
                    }
                    Text("+/− لتصحيح دقائق الصلاة. مفتاح كل صلاة يتحكم في إشعارها وأذانها.", color = SoftText, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Lavender),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, null, tint = DeepGreen, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("تذكيرات القراءة", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.AccessTime, null, tint = Emerald, modifier = Modifier.size(18.dp))
                        }
                        Text("إشعار قراءة القرآن بعد كل صلاة", color = SoftText, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("تشغيل التذكيرات الخمسة", color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.fajr && settings.dhuhr && settings.asr && settings.maghrib && settings.isha,
                                onCheckedChange = { enabled -> persist(settings.copy(fajr = enabled, dhuhr = enabled, asr = enabled, maghrib = enabled, isha = enabled)) },
                                modifier = Modifier.scale(.82f),
                                colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                            )
                        }
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Text("تنبيهات القراءة متوقفة لأن إشعارات التطبيق غير مسموح بها على الجهاز.", color = Color(0xFF9A4B20), fontSize = 12.sp)
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                })
                            }) { Text("فتح إعدادات الإشعارات", color = Emerald) }
                        }
                        Text("تذكير القراءة منفصل عن إشعارات الصلاة والأذان أعلى الصفحة.", color = SoftText, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
            item {
                PrayerWhiteCard {
                    PrayerCardTitle("موعد تذكير القراءة بعد الصلاة", "◷")
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 15, 30, 60).forEach { delayValue ->
                            val selected = settings.delayMinutes == delayValue
                            Box(
                                Modifier.weight(1f).height(41.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(if (selected) Emerald else Color(0xFFF1F3F2))
                                    .clickable { persist(settings.copy(delayMinutes = delayValue)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (delayValue == 0) "مباشرة" else "$delayValue د", color = if (selected) Color.White else Ink, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text("قد تتأخر تذكيرات القراءة قليلًا بسبب توفير الطاقة؛ وليست أذانًا دقيقًا.", color = SoftText, fontSize = 11.sp, lineHeight = 18.sp)
                }
            }
        }
        P53BottomNav(onBack = onBack, onHome = onBack)
        }
    }

    if (showCityPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCityPicker = false },
            containerColor = Color.White,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text("اختر المدينة", color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cityQuery,
                    onValueChange = { cityQuery = it },
                    label = { Text("ابحث عن مدينة") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(8.dp))
                val results = allAvailableCities.filter { it.name.contains(cityQuery.trim(), ignoreCase = true) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(results, key = { it.name }) { city ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { selectLocation(city); showCityPicker = false }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(city.name, modifier = Modifier.weight(1f), color = Ink, fontSize = 16.sp)
                            if (settings.location.name == city.name) Text("✓", color = Emerald, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Color(0xFFF2F2F0))
                    }
                    if (results.isEmpty()) item { Text("لا توجد مدينة بهذا الاسم", color = SoftText, modifier = Modifier.padding(18.dp)) }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    if (showSoundInfo) {
        ModalBottomSheet(
            onDismissRequest = { showSoundInfo = false; p85ChoiceGeneration++ },
            containerColor = Ivory,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (AdhanPlaybackStatus.text.isNotBlank()) Text(AdhanPlaybackStatus.text, color = DeepGreen, fontSize = 12.sp)
                if (AdhanPlaybackStatus.active && AdhanPlaybackStatus.duration>0) {
                    LinearProgressIndicator(progress={AdhanPlaybackStatus.position.toFloat()/AdhanPlaybackStatus.duration},modifier=Modifier.fillMaxWidth(),color=Emerald)
                    Text("${AdhanPlaybackStatus.position/1000} / ${AdhanPlaybackStatus.duration/1000} ثانية",fontSize=11.sp,color=SoftText)
                }
                Text("صوت الأذان", color = DeepGreen, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("الأصوات الأصلية تعمل دون إنترنت. أصوات الشيوخ الجديدة تُحمّل مرة واحدة عند اختيارها.",
                    color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
                Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("default", "الأذان الهادئ", "التسجيل الأصلي • 02:34 • CC0"),
                    Triple("makkah", "أذان الحرم المكي", "تسجيل ميداني • CC BY 3.0"),
                    Triple("classic", "أذان عربي كلاسيكي", "تسجيل مختلف • 03:03 • CC BY-SA 4.0"),
                    Triple("madinah", "أذان المسجد النبوي", "تسجيل ميداني • 03:07 • CC BY 3.0"),
                    Triple("shalqar", "أذان مسجد شالقار", "تسجيل مسجد بكازاخستان • 02:35 • CC BY-SA 4.0"),
                ).forEach { (id, title, detail) ->
                    val chosen = selectedAdhanVoice == id
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            p85ChoiceGeneration++
                            selectedAdhanVoice = id
                            adhanPrefs.voiceId = id
                        },
                        shape = RoundedCornerShape(17.dp),
                        color = if (chosen) Color(0xFFE2F1E8) else Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            if (chosen) 1.5.dp else 1.dp,
                            if (chosen) Emerald else Color(0xFFE7E9E4)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(if (chosen) Icons.Default.CheckCircle else Icons.Default.VolumeUp,
                                contentDescription = null, tint = Emerald, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(detail, color = SoftText, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                p85ChoiceGeneration++
                                selectedAdhanVoice = id
                                adhanPrefs.voiceId = id
                                ContextCompat.startForegroundService(context,
                                    Intent(context, AdhanPlaybackService::class.java)
                                        .setAction(AdhanPlaybackService.ACTION_START)
                                        .putExtra(AdhanPlaybackService.EXTRA_PRAYER, "تجربة"))
                            }) { Text("استماع ▶", fontSize = 11.sp, color = Emerald) }
                        }
                    }
                }
                Text("أذان الشيوخ — إسلام ويب",color=DeepGreen,fontSize=16.sp,fontWeight=FontWeight.Bold)
                P85NamedAdhan.voices.forEach { voice ->
                    Surface(shape=RoundedCornerShape(17.dp),color=if(selectedAdhanVoice==voice.id) Color(0xFFE2F1E8) else Color.White,
                        border=androidx.compose.foundation.BorderStroke(1.dp,if(selectedAdhanVoice==voice.id) Emerald else Color(0xFFE7E9E4))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(voice.name,color=Ink,fontWeight=FontWeight.Bold,fontSize=15.sp)
                                Text("${voice.seconds/60}:${(voice.seconds%60).toString().padStart(2,'0')} • تحميل لمرة واحدة",color=SoftText,fontSize=11.sp)
                            }
                            TextButton(enabled=p85Downloading==null,onClick={
                                p85Downloading=voice.id;p85VoiceError=""
                                val choice=++p85ChoiceGeneration
                                val stopSerial=AdhanPlaybackStatus.stopSerial
                                p85AudioScope.launch {
                                    try {
                                        P85NamedAdhan.prepare(context.applicationContext,voice.id)
                                        if (choice==p85ChoiceGeneration && stopSerial==AdhanPlaybackStatus.stopSerial && showSoundInfo) {
                                        selectedAdhanVoice=voice.id;adhanPrefs.voiceId=voice.id
                                        ContextCompat.startForegroundService(context,Intent(context,AdhanPlaybackService::class.java)
                                            .setAction(AdhanPlaybackService.ACTION_START).putExtra(AdhanPlaybackService.EXTRA_PRAYER,"تجربة"))
                                        }
                                    } catch (_:Exception) { p85VoiceError="تعذّر تحميل التسجيل؛ الصوت المختار سابقًا محفوظ. راجع الإنترنت وحاول تاني." }
                                    finally { p85Downloading=null }
                                }
                            }) { Text(if(p85Downloading==voice.id) "جارٍ التحميل…" else if(selectedAdhanVoice==voice.id) "مختار ▶" else "اختيار ▶",fontSize=11.sp) }
                        }
                    }
                }
                if(p85VoiceError.isNotBlank()) Text(p85VoiceError,color=Color(0xFF9A4B20),fontSize=12.sp)
                Surface(shape = RoundedCornerShape(17.dp), color = Color.White) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("مستوى صوت الأذان", color = Ink, fontSize = 14.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${(adhanVolume * 100).toInt()}%", color = Emerald, fontWeight = FontWeight.Bold)
                        }
                        Slider(value = adhanVolume,
                            onValueChange = { adhanVolume = it; adhanPrefs.volume = it
                                if (AdhanPlaybackStatus.active) context.startService(Intent(context, AdhanPlaybackService::class.java).setAction(AdhanPlaybackService.ACTION_VOLUME))
                            },
                            valueRange = 0f..1f)
                        Text("هذا المستوى داخل التطبيق؛ راجع أيضًا مستوى صوت المنبّه في الهاتف. يمكنك إيقاف الأذان من الإشعار أو زر الإيقاف.",
                            color = SoftText, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
                OutlinedButton(
                    onClick = {
                        p85ChoiceGeneration++
                        context.startService(Intent(context, AdhanPlaybackService::class.java)
                            .setAction(AdhanPlaybackService.ACTION_STOP))
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إيقاف الصوت الآن")
                }
                Text("المصادر: Adam-synagda (CC0)، وSeyfula Islam (CC BY 3.0)، " +
                        "وAndrewler (CC BY-SA 4.0)، وejaz215 (CC BY 3.0)، وEsetok (CC BY-SA 4.0). تفاصيل التراخيص محفوظة في ملف المصادر.",
                    color = SoftText, fontSize = 11.sp, lineHeight = 15.sp)
                Button(
                    onClick = { showSoundInfo = false; p85ChoiceGeneration++ }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                ) { Text("حفظ وإغلاق") }
                Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PrayerBottomNavigation(onHome: () -> Unit, onBack: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 9.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onHome,
                modifier = Modifier.weight(1f).height(41.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
            ) {
                Icon(Icons.Default.Home, null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text("الرئيسية", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(41.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
            ) {
                Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(19.dp), tint = Ink)
                Spacer(Modifier.width(6.dp))
                Text("رجوع", color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PrayerHeroCard(prayer: String, date: String, countdown: String, city: String, onUseLocation: () -> Unit, isLocating: Boolean) {
    Box(Modifier.fillMaxWidth().height(189.dp).clip(RoundedCornerShape(22.dp)).background(DeepGreen)) {
        Image(
            painter = painterResource(R.drawable.prayer_hero_mosque),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        // Visual gradient preserves readable contrast without hard-coded dates or prayers.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(DeepGreen, DeepGreen.copy(alpha = .94f), DeepGreen.copy(alpha = .16f)))))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier.align(Alignment.CenterEnd).widthIn(max = 227.dp).padding(horizontal = 15.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text("☾  الصلاة القادمة", fontSize = 13.sp, color = Color.White.copy(alpha = .92f))
                Text(prayer, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 39.sp)
                Text("$date  •  $city", color = Color.White.copy(alpha = .90f), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("متبقي: $countdown", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(3.dp))
                Button(
                    onClick = onUseLocation,
                    enabled = !isLocating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFCF9F2), contentColor = DeepGreen),
                    shape = RoundedCornerShape(35.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (isLocating) "جارٍ تحديد الموقع" else "استخدم موقعي", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrayerWhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), content = content)
    }
}

@Composable
private fun PrayerCardTitle(title: String, symbol: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = Emerald, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(9.dp))
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrayerTimeEntry(
    name: String,
    time: String,
    correction: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onAdjust: (Int) -> Unit,
) {
    val tint = when (name) {
        "الفجر" -> Color(0xFFE9F4F3)
        "الظهر" -> Color(0xFFEFF7FD)
        "العصر" -> Color(0xFFFFF3E0)
        "المغرب" -> Color(0xFFFFE8DC)
        else -> Color(0xFFEDE9F6)
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(tint).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(52.dp), maxLines = 1)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            PrayerStepControl("+", correction < 30) { onAdjust(1) }
            Text(if (correction > 0) "+$correction" else "$correction", color = Ink, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(29.dp))
            PrayerStepControl("−", correction > -30) { onAdjust(-1) }
        }
        Text(time, color = DeepGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.scale(.75f).width(49.dp),
            colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
        )
    }
}

@Composable
private fun PrayerStepControl(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(25.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) .88f else .4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Emerald.copy(alpha = if (enabled) 1f else .35f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatPrayerTime(epoch: Long, zoneId: String): String =
    SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(zoneId) }.format(Date(epoch))

private fun formatPrayerCountdown(remainingMs: Long): String {
    val seconds = (remainingMs / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L)
}

private fun hasPrayerLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Returns a recent fix (within 15 minutes), or null. Never reuses stale GPS data. */
private suspend fun currentPrayerLocation(context: Context): Pair<Double, Double>? {
    if (!hasPrayerLocationPermission(context)) return null
    val fix = withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val candidates = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            val provider = candidates.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancellation = CancellationSignal()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { value ->
                        if (continuation.isActive) continuation.resume(value)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(value: Location) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(value)
                        }
                        override fun onProviderDisabled(provider: String) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    } ?: return null
    val age = System.currentTimeMillis() - fix.time
    if (age < -60_000L || age > 15 * 60_000L) return null
    if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return null
    if (fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) return null
    return fix.latitude to fix.longitude
}
