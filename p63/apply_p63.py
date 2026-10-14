"""P63 / VC85 safe patch on top of P62 / VC84.

1. Verse-by-verse recitation without gaps: the next verse is downloaded and prepared while the current one is heard, and
   takes over the instant it ends. The «listen from this verse to the end of the surah» player now uses the same engine.
2. Mushaf reader: one line «surah • juz • hizb • page», six named icons, previous/next page row, back and home at the
   bottom as everywhere else, shared pages (e.g. al-Falaq / an-Nas) shown as the real coloured page, the red notice hides.
3. Hizb numbers for every page (240 quarter starts of the Madinah Mushaf, bundled).
4. Reciter Yasser Salama (hadr mushaf, mp3quran).
5. Morning / evening adhkar chapters are heard in a real reciter's voice instead of the phone's synthetic voice.
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
patch(gradle, "versionCode = 84", "versionCode = 85")
patch(gradle, 'versionName = "0.19.57-p62-colored-reader-tools"', 'versionName = "0.19.58-p63-gapless-hizb-layout"')

add("P63Reader.kt", screens / "P63Reader.kt")
add("P63Adhkar.kt", screens / "P63Adhkar.kt")
add("p63_hizb.json", assets / "quran/p63_hizb.json")

# ------------------------------------------------------- 1. recitation without gaps ----
audio = java / "quran/P52SurahAudio.kt"
patch(audio, "    private var player: MediaPlayer? = null\n    private var session: MediaSession? = null\n",
      "    private var player: MediaPlayer? = null\n"
      "    // P63: the next verse is prepared while the current one is playing, so the recitation never waits for the network.\n"
      "    private var nextPlayer: MediaPlayer? = null\n"
      "    private var nextReady = false\n"
      "    private var nextItem = -1\n"
      "    private var switchWhenReady = false\n"
      "    private var session: MediaSession? = null\n")
patch(audio, """                    publishMetadata()
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
""", """                    publishMetadata()
                    publishState()
                    main.removeCallbacks(progressRunnable); main.post(progressRunnable)
                    refreshNotification()
                    preloadNext()
                }
            }
            p.setOnCompletionListener {
                if (player === it) {
                    if (index + 1 < queue.size) { if (!switchToNext()) { index += 1; prepareCurrent() } }
                    else stop()
                }
            }
""")
patch(audio, "    private fun releasePlayer() {\n        player?.let { p ->\n", """    private fun releaseNext() {
        nextPlayer?.let { n ->
            runCatching { n.setOnPreparedListener(null) }
            runCatching { n.setOnCompletionListener(null) }
            runCatching { n.setOnErrorListener(null) }
            runCatching { n.release() }
        }
        nextPlayer = null
        nextReady = false
        nextItem = -1
        switchWhenReady = false
    }

    /** Only verse recitations are prepared ahead (they are small); whole surahs are not. */
    private fun preloadNext() {
        val ctx = app ?: return
        releaseNext()
        val item = queue.getOrNull(index + 1) ?: return
        if (item < AYAH_BASE) return
        try {
            val n = MediaPlayer()
            n.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            n.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            n.setDataSource(urlFor(reciter, item))
            n.setOnPreparedListener {
                if (nextPlayer === it) {
                    nextReady = true
                    if (switchWhenReady) { switchWhenReady = false; switchToNext() }
                }
            }
            n.setOnErrorListener { mp, _, _ ->
                if (nextPlayer === mp) {
                    val waiting = switchWhenReady
                    releaseNext()
                    if (waiting) { index += 1; prepareCurrent() }
                }
                true
            }
            nextPlayer = n
            nextItem = item
            n.prepareAsync()
        } catch (_: Exception) { releaseNext() }
    }

    /** The current verse has ended: the prepared next verse takes over at once. False when there is nothing prepared. */
    private fun switchToNext(): Boolean {
        val n = nextPlayer ?: return false
        if (nextItem != queue.getOrNull(index + 1)) { releaseNext(); return false }
        if (!nextReady) {
            switchWhenReady = true
            isLoading = true
            publishState()
            return true
        }
        val old = player
        index += 1
        message = null
        player = n
        nextPlayer = null
        nextReady = false
        nextItem = -1
        switchWhenReady = false
        val item = queue[index]
        n.setOnPreparedListener(null)
        n.setOnCompletionListener {
            if (player === it) {
                if (index + 1 < queue.size) { if (!switchToNext()) { index += 1; prepareCurrent() } }
                else stop()
            }
        }
        n.setOnErrorListener { mp, _, _ ->
            if (player === mp) {
                isLoading = false
                isPlaying = false
                message = "تعذّر تشغيل ${itemTitle(item)} بصوت ${reciter.name}. تأكد من الإنترنت ثم حاول مرة أخرى."
                publishState()
                refreshNotification()
            }
            true
        }
        isLoading = false
        applySpeed(n)
        runCatching { n.start() }
        isPlaying = true
        old?.let { o ->
            runCatching { o.setOnPreparedListener(null) }
            runCatching { o.setOnCompletionListener(null) }
            runCatching { o.setOnErrorListener(null) }
            runCatching { o.release() }
        }
        publishMetadata()
        publishState()
        main.removeCallbacks(progressRunnable); main.post(progressRunnable)
        refreshNotification()
        preloadNext()
        return true
    }

    private fun releasePlayer() {
        releaseNext()
        player?.let { p ->
""")
patch(audio, "        queue = clean\n        index = at\n        publishMetadata()\n",
      "        queue = clean\n        index = at\n        if (player != null && !isLoading) preloadNext()\n        publishMetadata()\n")

# ----------------------------------------------------------------- 4. reciter: Yasser Salama ----
patch(audio, '    private const val RIW = "روايات أخرى"\n',
      '    private const val RIW = "روايات أخرى"\n    private const val HADR = "مصحف الحدر (سريع)"\n')
patch(audio, "    val sections = listOf(MUR, MUJ, MUA, RIW, ENG)", "    val sections = listOf(MUR, MUJ, MUA, HADR, RIW, ENG)")
patch(audio, "        // English meanings\n",
      '        P52Reciter("yasser_salama", "ياسر سلامة", "مصحف الحدر", M + "yasser-salama/r1/", HADR, null),\n'
      "        // English meanings\n")

# ------------------------------------------------------------------------------ QuranScreen ----
q = screens / "QuranScreen.kt"

# «listen from this verse to the end»: same gapless queue player
patch(q, """        stopAudio()
        audioQueue = sequence
        audioQueueIndex = 0
        playAyah(sequence.first(), fromSequence = true)
    }""", """        stopAudio()
        if (toEnd && sequence.size > 1) {
            // P63: a whole run of verses is played by the queue player, which prepares the next verse while this one is heard.
            P52SurahAudio.play(context, sequence.map { P52SurahAudio.ayahCode(it.surah, it.ayah) })
            P52SurahAudio.followReader = true
            audioMessage = if (P52SurahAudio.savedReciter.ayahUrl(surah, firstAyah) == null)
                P52SurahAudio.savedReciter.name + " متاح للسورة كاملة فقط — الآيات بصوت الحصري." else null
            return
        }
        audioQueue = sequence
        audioQueueIndex = 0
        playAyah(sequence.first(), fromSequence = true)
    }""")

# reader: red notice hides by itself
patch(q, "    var p62Settings by remember { mutableStateOf(false) }\n",
      "    var p62Settings by remember { mutableStateOf(false) }\n"
      "    var p63MsgShown by remember { mutableStateOf(true) }\n"
      "    LaunchedEffect(audioMessage) {\n"
      "        p63MsgShown = true\n"
      "        if (audioMessage != null) { kotlinx.coroutines.delay(5000); p63MsgShown = false }\n"
      "    }\n")
patch(q, """            audioMessage?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),""",
      """            audioMessage?.takeIf { p63MsgShown }?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),""")

# reader: top bar (info line + named icons) — back / home go to the bottom
patch(q, """                P62TopBar(
                    night = P53NightMode.enabled,
                    saved = currentPage in savedPages,
                    toolsOn = p60Tools || p44InkTool != P44InkTool.MOVE,
                    onBack = onBack,
                    onIndex = onOpenIndex,""", """                P63TopBar(
                    night = P53NightMode.enabled,
                    info = "سورة ${repo.surahName(currentSurah)} • الجزء ${p61Digits(currentJuz)} • الحزب ${p61Digits(P63Hizb.ofPage(p55Ctx, repo, currentPage))} • صفحة ${p61Digits(currentPage)}",
                    saved = currentPage in savedPages,
                    toolsOn = p60Tools || p44InkTool != P44InkTool.MOVE,
                    onIndex = onOpenIndex,""")
# reader: bottom player + page row; back / home in their natural place
patch(q, """                if (p41ControlsVisible) P62Bottom(
                    repo = repo,""", """                if (p41ControlsVisible) P63Bottom(
                    repo = repo,""")
patch(q, """                    onSeek = { i -> scope.launch { pagerState.scrollToPage(i) } },
                )
            } else {""", """                    onSeek = { i -> scope.launch { pagerState.scrollToPage(i) } },
                    hasPrev = pagerState.currentPage > 0 || (target.surah != null && target.surah > 1),
                    hasNext = pagerState.currentPage < pages.lastIndex || (target.surah != null && target.surah < 114),
                    onPrev = {
                        when {
                            pagerState.currentPage > 0 -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            target.surah != null && target.surah > 1 -> onNavigateSurah(target.surah - 1, true)
                            else -> Unit
                        }
                    },
                    onNext = {
                        when {
                            pagerState.currentPage < pages.lastIndex -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            target.surah != null && target.surah < 114 -> onNavigateSurah(target.surah + 1, false)
                            else -> Unit
                        }
                    },
                )
            } else {""")
patch(q, "            if (displayMode != QuranMode.MADINA_PAGES)\n                P53BottomNav(onBack = onBack, onHome = onHome)",
      "            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible)\n                P53BottomNav(onBack = onBack, onHome = onHome)")
# reader: a surah that shares its page with another one (al-Falaq / an-Nas ...) is shown as the real coloured page too
patch(q, """                    val hasNeighborSurah = target.surah != null &&
                        repo.loadPage(pageNumber).any { it.surah != target.surah }""",
      """                    val hasNeighborSurah = false""")

# the strip's ▶ tells when a reciter has no verse-by-verse files, then clears the notice
reader62 = screens / "P62Reader.kt"
patch(reader62, "    P52SurahAudio.play(context, codes)\n    P52SurahAudio.followReader = true\n}",
      "    P52SurahAudio.play(context, codes)\n    P52SurahAudio.followReader = true\n"
      "    if (P52SurahAudio.savedReciter.ayahFolder == null) {\n"
      "        val note = P52SurahAudio.savedReciter.name + \" متاح للسورة كاملة فقط — الآيات بصوت الحصري.\"\n"
      "        P52SurahAudio.message = note\n"
      "        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({\n"
      "            if (P52SurahAudio.message == note) P52SurahAudio.message = null\n"
      "        }, 6000)\n"
      "    }\n}")

# ------------------------------------------------------------------------------ adhkar ----
p60 = screens / "P60Adhkar.kt"
patch(p60, "private data class VoiceTrack(", "internal data class VoiceTrack(")
patch(p60, "private val voiceTracks = listOf(", "internal val voiceTracks = listOf(")
p57a = screens / "P57Adhkar.kt"
patch(p57a, "    val streaming = P56AdhkarAudio.playingTitle == title\n    val speaking = P57Tts.speakingId == ttsId\n    Column(Modifier.fillMaxWidth()) {",
      "    val streaming = P56AdhkarAudio.playingTitle == title\n    val speaking = P57Tts.speakingId == ttsId\n"
      "    var p63Voices by remember(title) { mutableStateOf(false) }\n"
      "    if (p63Voices) P63AdhkarVoices { p63Voices = false }\n"
      "    Column(Modifier.fillMaxWidth()) {")
patch(p57a, "            when {\n                streaming -> P56AdhkarAudio.stop()",
      "            when {\n                P60VoiceAdhkar.playing != null -> P60VoiceAdhkar.stop()\n                streaming -> P56AdhkarAudio.stop()")
patch(p57a, "                else -> { usedDevice = true; P57Tts.speakList(context, ttsId, texts) }",
      "                p63IsMorningEvening(title) -> { p63Voices = true; Unit }\n"
      "                else -> { usedDevice = true; P57Tts.speakList(context, ttsId, texts) }")
patch(p57a, "                when {\n                    streaming -> if (P56AdhkarAudio.loading)",
      "                when {\n                    P60VoiceAdhkar.playing != null -> \"⏹ إيقاف الصوت\"\n                    streaming -> if (P56AdhkarAudio.loading)")

# ------------------------------------------------------------------------------------ notices ----
notice = assets / "quran/CONTENT_NOTICE.txt"
notice.write_text(notice.read_text(encoding="utf-8") + """
P63: hizb numbers use the 240 quarter starts of the Madinah Mushaf (Tanzil metadata, as served by api.alquran.cloud).
Reciter Yasser Salama: audio served by mp3quran.net (cdn.mp3quran.net/audio/yasser-salama/r1/).
""", encoding="utf-8")
(root / "P63_NOTES_AR.txt").write_text("""P63 / VC85
1) تشغيل الآيات بدون قطع: الآية التالية تُحمَّل وتُجهَّز أثناء سماع الحالية وتبدأ فور انتهائها. «استمع من آية إلى نهاية السورة» يستخدم نفس المشغّل.
2) شاشة المصحف: سطر واحد «سورة • جزء • حزب • صفحة»، ست أيقونات بأسمائها، صف «السابق / التالي»، الرجوع والرئيسية تحت كباقي الشاشات،
   السور المشتركة في صفحة (الفلق/الناس..) تظهر بصورة الصفحة الملونة، والسطر الأحمر يختفي لوحده.
3) أرقام الأحزاب لكل صفحة.
4) القارئ ياسر سلامة (مصحف الحدر).
5) أذكار الصباح والمساء: «استمع للباب» يفتح تسجيلات بصوت الشيوخ بدل صوت الموبايل.
""", encoding="utf-8")
print("P63 applied")
