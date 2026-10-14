"""P61 / VC83 safe patch on top of P60 / VC82.

1. Continuous Mushaf reader (scroll down through the Quran, surah banners, ayah circles, the recited ayah highlighted
   and followed, floating player, seek bar). The printed-page reader stays available (switch in both directions).
2. «🎙 تسميع»: choose a range, recite while the phone writes what it hears, edit it, then every Quran word is marked.
3. «وضع الحفظ» redesigned in the same calm style (paragraph, highlighted repeated verse, meanings, similar ayat).
4. «🔁 آيات متشابهة» (computed from the bundled Tanzil text) and the existing word meanings reachable from the new screens.
5. Recitation speed 0.75× – 2× (saved) in every player.
Additive only. Each replacement must match exactly once.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"
screens = java / "ui/screens"
assets = root / "app/src/main/assets"


def patch(path, old, new, count=1):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    assert found == count, (str(path), found, old[:110])
    path.write_text(text.replace(old, new), encoding="utf-8")


def add(name, dest):
    assert not dest.exists(), dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes((here / name).read_bytes())


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 82", "versionCode = 83")
patch(gradle, 'versionName = "0.19.55-p60-follow-clean-reader"', 'versionName = "0.19.56-p61-flow-tasmee-similar"')

for kt in ("P61Common.kt", "P61Flow.kt", "P61Tasmee.kt", "P61Hifz.kt"):
    add(kt, screens / kt)
add("AmiriQuran.ttf", root / "app/src/main/res/font/amiri_quran.ttf")
add("p61_similar.json", assets / "quran/p61_similar.json")
add("FONT_AMIRI_OFL.txt", root / "FONT_AMIRI_OFL.txt")
add("gen_similar.py", root / "tools/p61_gen_similar.py")

# ------------------------------------------------------------------------------ manifest ----
manifest = root / "app/src/main/AndroidManifest.xml"
patch(manifest, '    <uses-permission android:name="android.permission.WAKE_LOCK" />\n',
      '    <uses-permission android:name="android.permission.WAKE_LOCK" />\n'
      '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
      '    <uses-feature android:name="android.hardware.microphone" android:required="false" />\n')
patch(manifest, '        </intent>\n    </queries>',
      '        </intent>\n        <intent>\n            <action android:name="android.speech.RecognitionService" />\n        </intent>\n    </queries>')

# ----------------------------------------------------------------- player: recitation speed ----
audio = java / "quran/P52SurahAudio.kt"
patch(audio, "    var sleepMinutes by mutableIntStateOf(0)\n        private set\n",
      "    var sleepMinutes by mutableIntStateOf(0)\n        private set\n\n"
      "    /** P61: recitation speed (0.75x - 2x), saved; the voice keeps its pitch. */\n"
      "    var speed by mutableStateOf(1f)\n        private set\n")
patch(audio, "        reciterId = P52Reciters.byId(p.getString(KEY_RECITER, null)).id\n",
      "        reciterId = P52Reciters.byId(p.getString(KEY_RECITER, null)).id\n"
      "        speed = p.getFloat(\"speed_v61\", 1f).coerceIn(0.5f, 2f)\n")
patch(audio, '    fun surahName(surah: Int): String = surahNames.getOrNull(surah - 1) ?: "سورة $surah"\n',
      '    fun surahName(surah: Int): String = surahNames.getOrNull(surah - 1) ?: "سورة $surah"\n\n'
      "    fun setSpeed(context: Context, value: Float) {\n"
      "        init(context)\n"
      "        speed = value.coerceIn(0.5f, 2f)\n"
      "        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putFloat(\"speed_v61\", speed)?.apply()\n"
      "        val p = player\n"
      "        if (p != null && isPlaying) applySpeed(p)\n"
      "    }\n\n"
      "    /** Also used by the single-verse player of the Mushaf. */\n"
      "    fun applySpeed(p: MediaPlayer) {\n"
      "        if (Build.VERSION.SDK_INT >= 23) runCatching { p.playbackParams = android.media.PlaybackParams().setSpeed(speed).setPitch(1f) }\n"
      "    }\n")
patch(audio, "                    if (requestFocus(ctx)) {\n                        it.start()\n",
      "                    if (requestFocus(ctx)) {\n                        applySpeed(it)\n                        it.start()\n")
patch(audio, "beforePlay?.invoke(); p.start(); isPlaying = true;", "beforePlay?.invoke(); applySpeed(p); p.start(); isPlaying = true;")

# ----------------------------------------------------------- mini player: speed + sleep row ----
mini = screens / "P52SurahListenUi.kt"
patch(mini, """                TextButton(onClick = { P52SurahAudio.cycleSleepTimer() },
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (P52SurahAudio.sleepMinutes == 0) "⏲" else "⏲ ${P52SurahAudio.sleepMinutes}د",
                        color = Color(0xFFFFE7B0), fontSize = 13.sp)
                }
""", "")
patch(mini, """            P52SurahAudio.message?.let {
                Text(it, color = Color(0xFFFFE7B0), fontSize = 12.sp)
            }
""", """            val p61Context = androidx.compose.ui.platform.LocalContext.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { P52SurahAudio.setSpeed(p61Context, p61NextSpeed(P52SurahAudio.speed)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)) {
                    Text("⚡ " + p61SpeedLabel(P52SurahAudio.speed), color = Color(0xFFFFE7B0), fontSize = 13.sp)
                }
                TextButton(onClick = { P52SurahAudio.cycleSleepTimer() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)) {
                    Text(if (P52SurahAudio.sleepMinutes == 0) "⏲ مؤقت" else "⏲ ${P52SurahAudio.sleepMinutes}د",
                        color = Color(0xFFFFE7B0), fontSize = 13.sp)
                }
            }
            P52SurahAudio.message?.let {
                Text(it, color = Color(0xFFFFE7B0), fontSize = 12.sp)
            }
""")

# ------------------------------------------------------- index header: «🎙 تسميع» next to hifz ----
hdr = screens / "P57Reader.kt"
patch(hdr, "internal fun P57ListenHeader(onPickSurahs: () -> Unit, onHifz: () -> Unit = {}) {",
      "internal fun P57ListenHeader(onPickSurahs: () -> Unit, onHifz: () -> Unit = {}, onTasmee: () -> Unit = {}) {")
patch(hdr, """        Surface(
            onClick = onHifz,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0B624C),
            border = BorderStroke(1.dp, Color(0xFFDFBD78).copy(alpha = 0.6f)),
        ) {
            Text("🧠 وضع الحفظ والتكرار", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
        }
""", """        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = onHifz,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B624C),
                border = BorderStroke(1.dp, Color(0xFFDFBD78).copy(alpha = 0.6f)),
            ) {
                Text("🧠 وضع الحفظ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
            Surface(
                onClick = onTasmee,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B624C),
                border = BorderStroke(1.dp, Color(0xFFDFBD78).copy(alpha = 0.6f)),
            ) {
                Text("🎙 تسميع", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
""")

# ------------------------------------------------------------------------------ QuranScreen ----
q = screens / "QuranScreen.kt"

# single-verse player follows the saved speed
patch(q, "                prefs.saveLastReadAyah(aya.surah, aya.ayah)\n                it.start()\n",
      "                prefs.saveLastReadAyah(aya.surah, aya.ayah)\n                P52SurahAudio.applySpeed(it)\n                it.start()\n")

# the continuous reader already follows the recitation into the next surah by itself
patch(q, "if (p60FollowSurah != null && current != null && current.surah != null && current.surah != p60FollowSurah) {",
      "if (!P61Flow.enabled && p60FollowSurah != null && current != null && current.surah != null && current.surah != p60FollowSurah) {")

# the reader: continuous by default, printed pages one tap away
patch(q, """            key(currentTarget.key) {
                QuranReaderScreen(
""", """            key(currentTarget.key) {
                LaunchedEffect(Unit) { P61Flow.load(context) }
                if (P61Flow.enabled) P61FlowReader(
                    repo = repo,
                    prefs = prefs,
                    pages = currentTarget.pages,
                    startPage = currentTarget.startPage,
                    juz = currentTarget.juz,
                    surah = currentTarget.surah,
                    assigned = currentTarget.assigned,
                    isContinue = currentTarget.key.startsWith("continue_"),
                    focusAyah = currentTarget.focusAyah,
                    fontSize = fontSize,
                    onFontSizeChange = { fontSize = it.coerceIn(20f, 44f) },
                    bookmark = bookmark,
                    playingKey = playingKey ?: P52SurahAudio.followKey,
                    highlightedAyahKey = highlightedAyahKey,
                    audioMessage = audioMessage,
                    assignmentCompleted = assignmentCompleted,
                    onCompleteAssignment = if (currentTarget.assigned) onCompleteAssignment else null,
                    isAudioPlaying = playingKey != null || audioLoadingKey != null,
                    onStopAudio = ::stopAudio,
                    onSelectAyah = { aya ->
                        explanationMode = null
                        selectedAyah = aya
                        highlightedAyahKey = "${aya.surah}:${aya.ayah}"
                        prefs.saveLastReadAyah(aya.surah, aya.ayah)
                    },
                    onBack = if (currentTarget.assigned) onBack else ({ target = null }),
                    onHome = onHome,
                    onSearch = { showSearch = true },
                    onOpenIndex = { target = null },
                    onOpenSavedPage = { page ->
                        target = ReadingTarget(key = "saved_page_$page", title = "علاماتي — صفحة $page",
                            pages = 1..604, startPage = page)
                    },
                ) else QuranReaderScreen(
""")

# printed-page reader: a button to go back to the continuous reader
patch(q, """                        Text(if (displayMode == QuranMode.MADINA_PAGES) "نص مرن" else "المصحف")
                    }
""", """                        Text(if (displayMode == QuranMode.MADINA_PAGES) "نص مرن" else "المصحف")
                    }
                    TextButton(
                        onClick = { P61Flow.set(p55Ctx, true) },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) { Text("📜 قراءة متصلة", fontSize = 12.sp) }
""")

# index: new hifz screen + tasmee screen
patch(q, "    var p58Hifz by remember { mutableStateOf(false) }\n",
      "    var p58Hifz by remember { mutableStateOf(false) }\n"
      "    var p61Tasmee by remember { mutableStateOf(false) }\n"
      "    var p61TasmeeArgs by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }\n")
patch(q, "        Surface(Modifier.fillMaxSize(), color = Color(0xFFF8F7F2)) { P58HifzScreen(repo) { p58Hifz = false } }\n    }\n",
      """        Surface(Modifier.fillMaxSize(), color = P61Paper) {
            P61HifzScreen(
                repo = repo,
                onClose = { p58Hifz = false },
                onTasmee = { s, f, t -> p61TasmeeArgs = Triple(s, f, t); p58Hifz = false; p61Tasmee = true },
            )
        }
    }
    if (p61Tasmee) androidx.compose.ui.window.Dialog(
        onDismissRequest = { p61Tasmee = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = P61Paper) {
            P61TasmeeScreen(
                repo = repo,
                initialSurah = p61TasmeeArgs?.first ?: 1,
                initialFrom = p61TasmeeArgs?.second ?: 1,
                initialTo = p61TasmeeArgs?.third,
                onClose = { p61Tasmee = false },
            )
        }
    }
""")
patch(q, "P57ListenHeader(onPickSurahs = { p55Picker = true }, onHifz = { p58Hifz = true })",
      "P57ListenHeader(onPickSurahs = { p55Picker = true }, onHifz = { p58Hifz = true },\n                onTasmee = { p61TasmeeArgs = null; p61Tasmee = true })")

# verse sheet: similar ayat, and the Quran font
patch(q, """    val key = "${aya.surah}:${aya.ayah}"
    val paper = Color(0xFFFFFBF0)
    val ink = Color(0xFF211D17)
    val accent = Color(0xFF98752C)
    val soft = Color(0xFFF5EACB)
""", """    val key = "${aya.surah}:${aya.ayah}"
    val paper = Color(0xFFFFFBF0)
    val ink = Color(0xFF211D17)
    val accent = Color(0xFF98752C)
    val soft = Color(0xFFF5EACB)
    val p61Ctx = androidx.compose.ui.platform.LocalContext.current
    var p61ShowSimilar by remember { mutableStateOf(false) }
    if (p61ShowSimilar) P61SimilarDialog(repo, aya.surah, aya.ayah, onDismiss = { p61ShowSimilar = false })
""")
patch(q, """                ) { Text("التفسير الميسر") }
            }
""", """                ) { Text("التفسير الميسر") }
            }
            if (P61Similar.has(p61Ctx, aya.surah, aya.ayah)) {
                FilledTonalButton(
                    onClick = { p61ShowSimilar = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = soft, contentColor = ink),
                ) { Text("🔁 آيات متشابهة") }
            }
""")
patch(q, """                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    lineHeight = 36.sp,
""", """                    fontFamily = P61Amiri,
                    fontSize = 26.sp,
                    lineHeight = 46.sp,
""")

# ------------------------------------------------------------------------------------ notices ----
notice = assets / "quran/CONTENT_NOTICE.txt"
notice.write_text(notice.read_text(encoding="utf-8") + """
Quran font (continuous reader, hifz, tasmee): Amiri Quran, SIL Open Font License 1.1 (see FONT_AMIRI_OFL.txt).
Similar ayat: computed on the developer's machine by comparing the words of the bundled Tanzil text
(tools/p61_gen_similar.py); the list is a word-matching aid, not a complete list from the books of the scholars.
Tasmee uses the phone's own speech recognition (Android SpeechRecognizer, Arabic). Nothing is recorded or stored by the app.
""", encoding="utf-8")
(root / "P61_NOTES_AR.txt").write_text("""P61 / VC83
1) قراءة متصلة: المصحف كله بالتمرير لأسفل، لافتات السور، دوائر الآيات، تظليل الآية المتلوّة ومتابعتها، مشغّل عائم، شريط تنقل سريع.
   الصفحات المطبوعة (الملونة/الأصلي) ما زالت موجودة من «إعدادات القراءة» أو زر «📖 عرض صفحات المصحف».
2) تسميع: اختيار المقطع (آيات/صفحات/جزء)، التسميع بالصوت (يكتبه الهاتف) أو بالكتابة، تعديل النص، مقارنة كل كلمة بنص المصحف.
3) وضع الحفظ بتنسيق جديد مع تظليل الآية المكررة والمعاني والمتشابهات وزر تسميع.
4) آيات متشابهة (مستخرجة بمقارنة الألفاظ) ومعاني الكلمات (QuranEnc كما هي).
5) سرعة التلاوة 0.75× إلى 2× في كل المشغّلات.
""", encoding="utf-8")
print("P61 applied")
