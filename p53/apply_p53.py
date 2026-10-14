"""P53 / VC75 safe patch on top of the P52 / VC74 safe source.

1. Account page redesign (stats, settings, Google link, confirmed logout).
2. One unified bottom bar «رجوع / الرئيسية» on every page; duplicate top buttons removed;
   in the Mushaf reader it follows the existing show/hide-controls tap.
3. Local reminders: ward (silent when already read), morning/evening adhkar,
   before-prayer, daily dua, Friday al-Kahf (with a listen action).
4. Extras: after-prayer adhkar category fix, Mushaf night mode, sleep timer,
   mini player on the home screen, WhatsApp-first invite sharing.
Nothing is deleted from data or server. Each replacement must match exactly once.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"


def patch(path, old, new, count=1):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    assert found == count, (str(path), found, old[:100])
    path.write_text(text.replace(old, new), encoding="utf-8")


def add_file(name, dest):
    assert not dest.exists(), dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes((here / name).read_bytes())


# ---- version ----------------------------------------------------------------------
gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 74", "versionCode = 75")
patch(gradle, 'versionName = "0.19.47-p52-compact-wards-surah-listen"',
      'versionName = "0.19.48-p53-profile-nav-reminders"')

# ---- new files --------------------------------------------------------------------
add_file("P53Common.kt", java / "ui/screens/P53Common.kt")
add_file("P53Reminders.kt", java / "reminders/P53Reminders.kt")
add_file("P53ProfilePage.kt", java / "secure/P53ProfilePage.kt")
add_file("P53NotificationsPage.kt", java / "secure/P53NotificationsPage.kt")

# ---- app start: reminders + night mode -------------------------------------------
app = java / "KhatmaApplication.kt"
patch(app, "        AdhanAlarmScheduler.schedule(this)\n",
      "        AdhanAlarmScheduler.schedule(this)\n"
      "        runCatching { com.ab.khatma.reminders.P53ReminderScheduler.reschedule(this) }\n"
      "        runCatching { com.ab.khatma.ui.screens.P53NightMode.load(this) }\n")

# ---- surah player: Friday al-Kahf action + sleep timer ----------------------------
audio = java / "quran/P52SurahAudio.kt"
patch(audio, "import android.os.PowerManager\n",
      "import android.os.PowerManager\nimport android.os.Handler\nimport android.os.Looper\n")
patch(audio, '''    /** Lets the Mushaf screen stop the verse player before a surah starts. */''',
      '''    /** P53 sleep timer: 0 = off, otherwise minutes until playback stops. */
    var sleepMinutes by mutableIntStateOf(0)
        private set
    private val sleepHandler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable { stop() }

    fun cycleSleepTimer() {
        val next = when (sleepMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
        sleepHandler.removeCallbacks(sleepRunnable)
        sleepMinutes = next
        if (next > 0) sleepHandler.postDelayed(sleepRunnable, next * 60_000L)
    }

    /** Lets the Mushaf screen stop the verse player before a surah starts. */''')
patch(audio, '''    fun stop() {
        releasePlayer()''', '''    fun stop() {
        sleepHandler.removeCallbacks(sleepRunnable)
        sleepMinutes = 0
        releasePlayer()''')
patch(audio, '''            ACTION_TOGGLE -> P52SurahAudio.togglePause()''',
      '''            ACTION_PLAY_SURAH -> P52SurahAudio.play(this, listOf(intent?.getIntExtra(EXTRA_SURAH, 18) ?: 18))
            ACTION_TOGGLE -> P52SurahAudio.togglePause()''')
patch(audio, '''        const val ACTION_STOP = "com.ab.khatma.P52_LISTEN_STOP"''',
      '''        const val ACTION_STOP = "com.ab.khatma.P52_LISTEN_STOP"
        const val ACTION_PLAY_SURAH = "com.ab.khatma.P53_LISTEN_PLAY_SURAH"
        const val EXTRA_SURAH = "surah"''')

listen = java / "ui/screens/P52SurahListenUi.kt"
patch(listen, '''                IconButton(onClick = { P52SurahAudio.stop() }) {''',
      '''                TextButton(onClick = { P52SurahAudio.cycleSleepTimer() },
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (P52SurahAudio.sleepMinutes == 0) "⏲" else "⏲ ${P52SurahAudio.sleepMinutes}د",
                        color = Color(0xFFFFE7B0), fontSize = 13.sp)
                }
                IconButton(onClick = { P52SurahAudio.stop() }) {''')

# ---- secure screen (home, profile, notifications, bottom bar) ---------------------
ui = java / "secure/SecureV2Screen.kt"
patch(ui, "import com.ab.khatma.ui.screens.InteractiveAdhkarScreen\n",
      "import com.ab.khatma.ui.screens.InteractiveAdhkarScreen\n"
      "import com.ab.khatma.ui.screens.P53BottomNav\n"
      "import com.ab.khatma.ui.screens.P52MiniPlayer\n")
# Adhkar: the screen already ends with the unified bar; drop the duplicate top row.
patch(ui, '''        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { section = "home" }) { Text("رجوع") }
                TextButton(onClick = { section = "home" }) {
                    Icon(Icons.Default.Home, contentDescription = "الرئيسية"); Text("الرئيسية")
                }
            }
            Box(Modifier.weight(1f)) { InteractiveAdhkarScreen(onBack = { section = "home" }) }
        }''', '''        InteractiveAdhkarScreen(onBack = { section = "home" })''')
# WhatsApp first, any other app as fallback.
patch(ui, '''        context.startActivity(Intent.createChooser(intent, "مشاركة دعوة الختمة"))''',
      '''        val whatsapp = Intent(intent).setPackage("com.whatsapp")
        try { context.startActivity(whatsapp) }
        catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(intent, "مشاركة دعوة الختمة"))
        }''')
patch(ui, '''            if (session != null && section != "home") TopAppBar(''',
      '''            if (session != null && section != "home" && section != "profile" && section != "notifications") TopAppBar(''')
patch(ui, '''                if (session != null && section != "home" && section != "profile") {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                section = when (section) {
                                    "plans", "schedule", "leaderSchedule" -> "group"
                                    "join" -> if (joinFromMeetup) "meetup" else "khatmas"
                                    "group" -> "khatmas"
                                    else -> "home"
                                }
                                if (section != "group") groupTab = "read"
                                chatGroupId = null
                            }, modifier = Modifier.weight(1f)) { Text("رجوع") }
                            Button(onClick = {
                                section = "home"; groupTab = "read"; chatGroupId = null
                            }, modifier = Modifier.weight(1f)) { Text("الرئيسية") }
                        }
                    }
                }''', '''                if (session != null && section != "home" && section != "profile") {
                    P53BottomNav(
                        onBack = {
                            section = when (section) {
                                "plans", "schedule", "leaderSchedule" -> "group"
                                "join" -> if (joinFromMeetup) "meetup" else "khatmas"
                                "group" -> "khatmas"
                                "notifications" -> "profile"
                                else -> "home"
                            }
                            if (section != "group") groupTab = "read"
                            chatGroupId = null
                        },
                        onHome = { section = "home"; groupTab = "read"; chatGroupId = null },
                    )
                }''')
patch(ui, '''                    if (section == "home") {
                        KhatmaVisualHome(''', '''                    if (section == "home") {
                        P52MiniPlayer()
                        KhatmaVisualHome(''')
patch(ui, '''                    withContext(Dispatchers.IO) {
                        SecureV2Api.complete(current.token, requested.assignmentId)
                    }''', '''                    withContext(Dispatchers.IO) {
                        SecureV2Api.complete(current.token, requested.assignmentId)
                    }
                    runCatching { P53LocalStats.recordCompletion(context) }''')
patch(ui, '''                    } else if (section == "profile") {
                        KhatmaPolishedProfile(
                            userName = active.userName,
                            id = active.userId,
                            onCopyId = {
                                copyCode(active.userId.toString())
                                message = "تم نسخ رقم الحساب"
                            },
                            busy = busy,
                            onSaveName = { proposed ->''', '''                    } else if (section == "notifications") {
                        P53NotificationsPage()
                    } else if (section == "profile") {
                        P53ProfilePage(
                            userName = active.userName,
                            userId = active.userId,
                            groups = groups,
                            googleConfigured = KhatmaGoogleSignIn.isConfigured,
                            onOpenNotifications = { section = "notifications"; message = "" },
                            onLinkGoogle = {
                                runTask {
                                    val firebaseToken = KhatmaGoogleSignIn.firebaseIdToken(context)
                                    withContext(Dispatchers.IO) {
                                        SecureV2Api.linkGoogle(active.token, firebaseToken)
                                    }
                                    message = "تم ربط Google بحسابك الحالي ومجموعاتك"
                                }
                            },
                            onCopyId = {
                                copyCode(active.userId.toString())
                                message = "تم نسخ رقم الحساب"
                            },
                            busy = busy,
                            onSaveName = { proposed ->''')
patch(ui, '''                                section = "home"
                                message = ""
                            }
                        )
                        OutlinedButton(onClick = {
                            runTask {
                                val firebaseToken = KhatmaGoogleSignIn.firebaseIdToken(context)
                                withContext(Dispatchers.IO) {
                                    SecureV2Api.linkGoogle(active.token, firebaseToken)
                                }
                                message = "تم ربط Google بحسابك الحالي ومجموعاتك"
                            }
                        }, enabled = !busy && KhatmaGoogleSignIn.isConfigured,
                           modifier = Modifier.fillMaxWidth()) {
                            Text("ربط Google بهذا الحساب دون فقد مجموعاتي")
                        }
                    }''', '''                                section = "home"
                                message = ""
                            }
                        )
                    }''')

# ---- adhkar: after-prayer category + unified bar ----------------------------------
adhkar = java / "ui/screens/InteractiveAdhkarScreen.kt"
patch(adhkar, '''    if (n.contains("بعد السلام من الصلاه")) return "بعد الصلاة"''',
      '''    if (n.contains("بعد السلام من الصلاه") || n.contains("بعد السلام من الصلاة")) return "بعد الصلاة"''')
patch(adhkar, '''            Surface(color = Color.White, shadowElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { navigateBack() }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Default.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp)); Text("رجوع")
                    }
                    Button(onClick = onBack, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = dhGreen),
                        shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp)); Text("الرئيسية")
                    }
                }
            }''', '''            P53BottomNav(onBack = { navigateBack() }, onHome = onBack)''')

# ---- prayer: same order as everywhere ---------------------------------------------
prayer = java / "ui/screens/PrayerPremiumScreen.kt"
patch(prayer, "        PrayerBottomNavigation(onHome = onBack, onBack = onBack)",
      "        P53BottomNav(onBack = onBack, onHome = onBack)")

# ---- group heroes: back lives in the bottom bar now -------------------------------
p25 = java / "secure/P25GroupScreens.kt"
patch(p25, '''        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 7.dp)) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            Spacer(Modifier.width(5.dp))
            Text("رجوع", color = Color.White, fontWeight = FontWeight.Bold)
        }
''', "")
p28 = java / "secure/P28LeaderScreens.kt"
patch(p28, '''        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)
            .padding(top = 9.dp, start = 12.dp)
            .size(42.dp).background(deep28.copy(alpha = .27f), CircleShape)) {
            Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
        }
''', "")

# ---- Mushaf: index, search, reader, night mode ------------------------------------
quran = java / "ui/screens/QuranScreen.kt"
patch(quran, '''    onSearch: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
) {
    val green = Color(0xFF064D3B)
    val gold = Color(0xFFDFBD78)''', '''    onSearch: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    showBack: Boolean = true,
) {
    val green = Color(0xFF064D3B)
    val gold = Color(0xFFDFBD78)''')
patch(quran, '''                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0B624C),
                    border = BorderStroke(1.dp, gold.copy(alpha = 0.75f)),
                ) {''', '''                if (showBack) Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0B624C),
                    border = BorderStroke(1.dp, gold.copy(alpha = 0.75f)),
                ) {''')
patch(quran, '''            MushafHeroHeader("المصحف الشريف", onBack, onSearch, onHome)
            Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {''',
      '''            MushafHeroHeader("المصحف الشريف", onBack, onSearch, null, showBack = false)
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 13.dp)) {''')
patch(quran, '''                P52MiniPlayer()
            }
        }
    }
}''', '''                P52MiniPlayer()
            }
            P53BottomNav(onBack = onBack, onHome = onHome)
        }
    }
}''')
# search: bottom bar instead of the top pair
patch(quran, '''        topBar = { QuranSearchTopBar(onBack, onHome) },''',
      '''        topBar = { QuranSearchTopBar(onBack, onHome) },
        bottomBar = { P53BottomNav(onBack = onBack, onHome = onHome) },''')
patch(quran, '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0B604B),
                    border = BorderStroke(1.dp, gold.copy(alpha = 0.88f)),
                ) {
                    Text("رجوع", modifier = Modifier.padding(horizontal = 17.dp, vertical = 9.dp),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = onHome) {
                    Icon(Icons.Rounded.Home, contentDescription = "الرئيسية", tint = Color.White)
                    Text("الرئيسية", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))''', '''            Spacer(Modifier.height(26.dp))''')
# reader: top keeps search + index; Back/Home follow the show/hide-controls tap at the bottom
patch(quran, '''                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) { Text("رجوع") }
                },
                actions = {
                    TextButton(onClick = onHome, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Rounded.Home, contentDescription = "الرئيسية", modifier = Modifier.size(19.dp))
                        Text("الرئيسية", fontSize = 12.sp)
                    }
                    MushafSearchButton(onClick = onSearch)''', '''                navigationIcon = {},
                actions = {
                    MushafSearchButton(onClick = onSearch)''')
patch(quran, '''        bottomBar = {
            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible) Surface(''',
      '''        bottomBar = {
            Column {
            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible) Surface(''')
patch(quran, '''                        ) { Text("التالي ›") }
                    }
                }
            }
        },
    ) { padding ->''', '''                        ) { Text("التالي ›") }
                    }
                }
            }
            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible)
                P53BottomNav(onBack = onBack, onHome = onHome)
            }
        },
    ) { padding ->''')
patch(quran, '''                    ) { Text(if (coloredMushaf) "ملون" else "أصلي", fontSize = 12.sp) }''',
      '''                    ) { Text(if (coloredMushaf) "ملون" else "أصلي", fontSize = 12.sp) }
                    if (displayMode == QuranMode.MADINA_PAGES) P53NightToggle(accent)''')
mushaf = java / "ui/screens/P41OfflineMushafPage.kt"
patch(mushaf, '''            .background(Color(0xFFFFF9E9))''',
      '''            .background(if (P53NightMode.enabled) P53NightMode.pageBackground else Color(0xFFFFF9E9))''')
patch(mushaf, '''                contentDescription = "مصحف المدينة المنورة، صفحة $pageNumber",
                contentScale = ContentScale.Fit,''', '''                contentDescription = "مصحف المدينة المنورة، صفحة $pageNumber",
                colorFilter = if (P53NightMode.enabled) P53NightMode.pageFilter else null,
                contentScale = ContentScale.Fit,''')

# ---- safety ------------------------------------------------------------------------
assert len(list((root / "app/src/main/assets/quran_pages").glob("*.webp"))) == 604
assert len(list((root / "app/src/main/res/raw").glob("adhan_*.ogg"))) == 5
assert not list(root.rglob("*.p12")) and not list(root.rglob("*.jks")) and not list(root.rglob("*.keystore"))
print("P53: profile, unified back/home bar, local reminders, adhkar fix, night mode, sleep timer, VC75")
