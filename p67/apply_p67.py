"""P67 / VC89 safe patch on top of P66 / VC88.

1. Maher Al-Muaiqly, verses he joins (وصل): some verse files were cut a little late and end on the first hiss
   of the next verse («…المستقيم صـ») while the next file starts the word again («صراط»). Those verses (found
   once by p67/p67_overlap.py: abrupt end on a hissing letter + next verse starting with a hissing letter) now
   stop just before that hiss, and the next verse starts at once from its first sound. (His full-surah file on
   mp3quran is a different recording, so verse timings could not be taken from it.)
2. Full-screen Mushaf: the controls (next / zoom / speaker / previous) and the player moved to their own strip
   under the page; nothing covers the Quran text any more. The info line sits above the page.
3. Player bar: the reciter name has its own line (no more «ماهر ال..»).
4. Tasmee: the voice recording works together with the live writing (Android 13+: the app captures the voice
   once and gives the same sound to the speech engine). Clear notes when a phone cannot do it.
5. Tasmee «حزب»: choose the juz first, then its first / second hizb, then the quarter. Choice chips fit the screen.
Additive only. Each replacement must match exactly once.
"""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"
screens = java / "ui/screens"
quran = java / "quran"


def patch(path, old, new, count=1):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    assert found == count, (str(path), found, old[:110])
    path.write_text(text.replace(old, new), encoding="utf-8")


def patch_between(path, start, end, new):
    """Replaces the text from [start] up to (not including) [end]; [start] must occur exactly once."""
    text = path.read_text(encoding="utf-8")
    assert text.count(start) == 1, (str(path), "start", text.count(start), start[:100])
    a = text.index(start)
    b = text.index(end, a + len(start))
    path.write_text(text[:a] + new + text[b:], encoding="utf-8")


def add(name, dest):
    assert not dest.exists(), dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes((here / name).read_bytes())


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 88", "versionCode = 89")
patch(gradle, 'versionName = "0.19.61-p66-continuous-listen-one-player"', 'versionName = "0.19.62-p67-maher-continuous-tasmee-voice"')
add("P67Joins.kt", quran / "P67Joins.kt")
add("P67Ui.kt", screens / "P67Ui.kt")

# ------------------------------------------------------------- 1. Maher: joined verses (وصل) ----
joins = here / "p67_joins_maher.json"
assert joins.is_file() and joins.stat().st_size > 10000, "Maher joins asset missing"
shutil.copyfile(joins, root / "app/src/main/assets/quran/p67_joins_maher.json")

a = quran / "P52SurahAudio.kt"
patch(a, "        P66Timing.init(a)\n", "        P66Timing.init(a)\n        P67Joins.init(a)\n")
patch(a, "    private var resumeFirst = false\n", """    private var resumeFirst = false

    // P67: a verse file that already holds the start of the next verse (وصل) stops there, and the next verse
    // goes on at once from its first sound.
    private var joinCut = 0
    private var pendingHead = 0
    private val joinRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (joinCut <= 0 || p == null) return
            if (isPlaying && !isLoading && segSurah < 0) {
                val pos = runCatching { p.currentPosition }.getOrDefault(0)
                if (pos >= joinCut) { joinCut = 0; joinNow(); return }
            }
            main.postDelayed(this, 30)
        }
    }

    /** Arms the stop point of the verse now playing (only when the queue goes on with the very next verse). */
    private fun armJoin() {
        main.removeCallbacks(joinRunnable)
        joinCut = 0
        if (segSurah > 0) return
        val item = queue.getOrNull(index) ?: return
        val nxt = queue.getOrNull(index + 1) ?: return
        if (item < AYAH_BASE || nxt != item + 1) return
        val c = P67Joins.cut(reciter.id, (item - AYAH_BASE) / 1000, (item - AYAH_BASE) % 1000)
        if (c <= 0) return
        joinCut = c
        main.post(joinRunnable)
    }

    private fun joinNow() {
        if (index + 1 >= queue.size) { stop(); return }
        val nxt = queue[index + 1]
        pendingHead = (P67Joins.head(reciter.id, (nxt - AYAH_BASE) / 1000, (nxt - AYAH_BASE) % 1000) - 40).coerceAtLeast(0)
        if (!switchToNext()) { pendingHead = 0; index += 1; prepareCurrent() }
        else if (switchWhenReady) runCatching { player?.pause() }
    }
""")
patch(a, "        prepToken += 1\n        if (trySegment(ctx, surah)) return\n",
      "        prepToken += 1\n        pendingHead = 0\n        if (trySegment(ctx, surah)) return\n")
patch(a, """                    preloadNext()
                }
            }
            p.setOnCompletionListener {
                if (player === it) {
                    if (surah < AYAH_BASE) clearPos(surah)""", """                    preloadNext()
                    armJoin()
                }
            }
            p.setOnCompletionListener {
                if (player === it) {
                    if (surah < AYAH_BASE) clearPos(surah)""")
patch(a, """        isLoading = false
        applySpeed(n)
        runCatching { n.start() }""", """        isLoading = false
        applySpeed(n)
        val hd = pendingHead
        pendingHead = 0
        if (hd > 0) runCatching { seekExact(n, hd) }
        runCatching { n.start() }""")
patch(a, "        refreshNotification()\n        preloadNext()\n        return true\n",
      "        refreshNotification()\n        preloadNext()\n        armJoin()\n        return true\n")
patch(a, "        if (player != null && !isLoading) preloadNext()\n",
      "        if (player != null && !isLoading) { preloadNext(); armJoin() }\n")
patch(a, """        releaseNext()
        segSurah = -1
        segTimes = null
        main.removeCallbacks(segRunnable)
""", """        releaseNext()
        segSurah = -1
        segTimes = null
        main.removeCallbacks(segRunnable)
        main.removeCallbacks(joinRunnable)
        joinCut = 0
""")

# ------------------------------------------------------------- 2. full-screen Mushaf layout ----
q = screens / "QuranScreen.kt"
patch(q, """            if (displayMode == QuranMode.MADINA_PAGES) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(""", """            if (displayMode == QuranMode.MADINA_PAGES) {
                Column(Modifier.fillMaxWidth().weight(1f)) {
                if (p57Full) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    P62ExitPill { P55Reader.set(p55Ctx, false); p41ControlsVisible = true; p65Player = false }
                    P65FullInfo(
                        info = P65Info.line(p55Ctx, repo, repo.surahName(currentSurah), currentJuz, currentPage),
                        night = P53NightMode.enabled,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(""")
patch_between(q, "                P65FullControls(\n                    visible = p57Full && p44InkTool == P44InkTool.MOVE,",
              "                if (p41ControlsVisible) P63Bottom(", """                }
                if (p57Full && p44InkTool == P44InkTool.MOVE) P67FullBottom(
                    night = P53NightMode.enabled,
                    hasPrev = pagerState.currentPage > 0 || (target.surah != null && target.surah > 1),
                    hasNext = pagerState.currentPage < pages.lastIndex || (target.surah != null && target.surah < 114),
                    zoomed = printedZoom > 1.01f,
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
                    onToggleZoom = { printedZoom = if (printedZoom > 1.01f) 1f else 1.25f },
                    playerOpen = p65Player,
                    onTogglePlayer = { p65Player = !p65Player },
                    player = { P65FullPlayer(onPlayHere = { p62PlayFromPage(p55Ctx, repo, pages, currentPage, target.surah) }) },
                )
                }
""")

# ------------------------------------------------------------------- 3. player bar reciter line ----
b = screens / "P66Ui.kt"
patch(b, "import androidx.compose.foundation.layout.Row\n", "import androidx.compose.foundation.layout.Row\nimport androidx.compose.foundation.layout.Spacer\n")
patch_between(b, "                TextButton(\n                    onClick = { pick = true },",
              "                IconButton(onClick = { P52SurahAudio.previous() }", "                Spacer(Modifier.weight(1f))\n")
patch(b, """            Text(
                P52SurahAudio.itemTitle(item), fontSize = 11.sp, color = P61Ink.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp), textAlign = TextAlign.Center,
            )""", """            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pick = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("🎙 " + r.name + " ▾", color = P61Green, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text(
                    P52SurahAudio.itemTitle(item), fontSize = 11.sp, color = P61Ink.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
            }""")

# ------------------------------------------------------------------------- 4. tasmee recording ----
s = screens / "P61Tasmee.kt"
patch(s, "    val recorder = remember { P64TasmeeRecorder(context) }\n",
      "    val recorder = remember { P64TasmeeRecorder(context) }\n"
      "    val capture = remember { P67VoiceCapture(context) }\n"
      "    var piped by remember { mutableStateOf(false) }\n")
patch(s, """    fun listenAgain() {
        scope.launch {
            delay(250)
            if (wantListening) runCatching { recognizer?.startListening(intent) }
        }
    }
""", """    /** One listening session; with the recording on, the speech engine hears the app's own capture. */
    fun session() {
        val rz = recognizer ?: return
        val src = if (piped) capture.newSource() else null
        if (piped && src == null) {
            piped = false
            capture.cancel()
            audioNote = "تعذّر تسجيل الصوت هذه المرة؛ التسميع والمراجعة يعملان كالمعتاد."
        }
        runCatching { rz.startListening(if (src != null) capture.intentFor(intent, src) else intent) }
    }

    /** The speech engine cannot use the app's capture on this phone: keep the writing, drop the recording. */
    fun dropPiped(note: String) {
        piped = false
        capture.cancel()
        keepAudio = false
        audioNote = note
        runCatching { recognizer?.cancel() }
        if (wantListening) session()
    }

    fun listenAgain() {
        scope.launch {
            delay(250)
            if (wantListening) session()
        }
    }
""")
patch(s, """                    else -> {
                        failures++""", """                    else -> {
                        if (piped && committed.isBlank() && partial.isBlank()) {
                            dropPiped("خدمة الكلام على هذا الموبايل لا تقبل التسجيل مع الكتابة، فتم إيقاف التسجيل وأكمل التسميع كالمعتاد.")
                            return
                        }
                        failures++""")
patch(s, "            recorder.cancel()\n            runCatching { recognizer?.cancel() }\n            runCatching { recognizer?.destroy() }",
      "            recorder.cancel()\n            capture.cancel()\n            runCatching { recognizer?.cancel() }\n            runCatching { recognizer?.destroy() }")
patch(s, """        runCatching { recognizer.startListening(intent) }
        maxRms = 0f
        audioFile = null
        audioNote = null
        if (keepAudio && !recorder.start()) audioNote = "تعذّر تشغيل تسجيل الصوت؛ التسميع والمراجعة يعملان كالمعتاد."
""", """        maxRms = 0f
        audioFile = null
        audioNote = null
        piped = keepAudio && capture.supported && capture.start()
        session()
        if (keepAudio && !piped && !recorder.start()) audioNote = "تعذّر تشغيل تسجيل الصوت؛ التسميع والمراجعة يعملان كالمعتاد."
""")
patch(s, "        val savedAudio = recorder.stop()\n",
      "        val savedAudio = if (piped) capture.stop() else recorder.stop()\n        piped = false\n")
patch(s, """            delay(1000); seconds++
            recorder.sample()
""", """            delay(1000); seconds++
            recorder.sample()
            if (piped && seconds == 8) {
                val heard = committed.isNotBlank() || partial.isNotBlank()
                if (!heard && capture.peak > 1500) {
                    dropPiped("خدمة الكلام على هذا الموبايل لا تسمع الصوت المسجّل، فتم إيقاف التسجيل وأكمل التسميع كالمعتاد.")
                } else if (heard && capture.peak < 300) {
                    piped = false
                    capture.cancel()
                    keepAudio = false
                    audioNote = "هذا الموبايل لا يسمح بتسجيل الصوت أثناء التعرف على الكلام، فتم إيقاف التسجيل وأكمل التسميع كالمعتاد."
                }
            }
""")
patch(s, "                        if (keepAudio) Text(\"⚠ تسجيل الصوت قد يوقف الكتابة",
      "                        if (keepAudio && !capture.supported) Text(\"⚠ تسجيل الصوت قد يوقف الكتابة")

# --------------------------------------------------------------------- 5. tasmee choice: juz -> hizb ----
patch(s, """                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(P61Scope.AYAT to "آيات من سورة", P61Scope.PAGES to "صفحات", P61Scope.JUZ to "جزء كامل", P61Scope.HIZB to "حزب").forEach { (m, name) ->
                                FilterChip(selected = scopeMode == m, onClick = { scopeMode = m }, label = { Text(name) })
                            }
                        }""", """                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(P61Scope.AYAT to "آيات", P61Scope.PAGES to "صفحات", P61Scope.JUZ to "جزء", P61Scope.HIZB to "حزب").forEach { (m, name) ->
                                FilterChip(
                                    selected = scopeMode == m, onClick = { scopeMode = m }, modifier = Modifier.weight(1f),
                                    label = { Text(name, maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                )
                            }
                        }""")
patch(s, """                            P61Scope.HIZB -> {
                                P61Stepper("الحزب", hizb, 1, 60) { hizb = it }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(0 to "الحزب كامل", 1 to "الربع الأول", 2 to "الربع الثاني", 3 to "الربع الثالث", 4 to "الربع الرابع").forEach { (q, name) ->
                                        FilterChip(selected = quarter == q, onClick = { quarter = q }, label = { Text(name) })
                                    }
                                }
                            }""", """                            P61Scope.HIZB -> {
                                // P67: the juz first, then one of its two hizbs, then the quarter.
                                val hj = (hizb + 1) / 2
                                P61Stepper("الجزء", hj, 1, 30) { j -> hizb = j * 2 - (if (hizb % 2 == 1) 1 else 0) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(hj * 2 - 1, hj * 2).forEachIndexed { i, h ->
                                        FilterChip(
                                            selected = hizb == h, onClick = { hizb = h }, modifier = Modifier.weight(1f),
                                            label = {
                                                Text((if (i == 0) "الحزب الأول" else "الحزب الثاني") + " (" + p61Digits(h) + ")",
                                                    maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                            },
                                        )
                                    }
                                }
                                FilterChip(
                                    selected = quarter == 0, onClick = { quarter = 0 }, modifier = Modifier.fillMaxWidth(),
                                    label = { Text("الحزب كامل", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1 to "الربع ١", 2 to "الربع ٢", 3 to "الربع ٣", 4 to "الربع ٤").forEach { (qq, name) ->
                                        FilterChip(
                                            selected = quarter == qq, onClick = { quarter = qq }, modifier = Modifier.weight(1f),
                                            label = { Text(name, maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                        )
                                    }
                                }
                            }""")

print("P67 applied")
