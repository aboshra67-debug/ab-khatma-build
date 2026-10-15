"""P64 / VC86 safe patch on top of P63 / VC85.

1. Verse-by-verse recitation: the next verses are downloaded ahead into a small, self-cleaning cache and play from the
   phone, plus a watchdog for a verse that does not arrive (fixes the 3-4 second stop between verses).
2. Hifz page: a clear «🎧 القارئ: … ▾» button; reciters without verse files are marked «(للسورة كاملة فقط)».
3. Tasmee: «حزب» (whole hizb or one quarter); the result page gets «اسمع القارئ» and the student's own recording.
4. Mushaf index: slim title + search line, compact tool buttons, «▶ تشغيل» player panel (reciter, speed, controls).
5. Adhkar: the phone's synthetic voice is no longer used anywhere; only real recordings are offered.
Additive only. Each replacement must match exactly once.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"
screens = java / "ui/screens"


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
patch(gradle, "versionCode = 85", "versionCode = 86")
patch(gradle, 'versionName = "0.19.58-p63-gapless-hizb-layout"', 'versionName = "0.19.59-p64-prefetch-tasmee-index"')

add("P64AyahCache.kt", java / "quran/P64AyahCache.kt")
add("P64Ui.kt", screens / "P64Ui.kt")
add("P64Index.kt", screens / "P64Index.kt")

# ------------------------------------------------------------ 1. verses play from the phone ----
audio = java / "quran/P52SurahAudio.kt"
patch(audio, "        app = a\n        val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n",
      "        app = a\n        P64AyahCache.init(a)\n        val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n")
patch(audio, "            p.setDataSource(urlFor(r, surah))\n", "            p.setDataSource(sourceFor(r, surah))\n")
patch(audio, "            n.setDataSource(urlFor(reciter, item))\n", "            n.setDataSource(sourceFor(reciter, item))\n")
patch(audio, "    /** Replaces the queue without interrupting the current surah when it is still in the new list. */\n",
      """    /** P64: the downloaded copy of a verse when there is one, otherwise the address. */
    private fun sourceFor(r: P52Reciter, item: Int): String {
        val url = urlFor(r, item)
        if (item < AYAH_BASE) return url
        return P64AyahCache.cached(url)?.absolutePath ?: url
    }

    /** P64: downloads the next verses in the background so they start without waiting for the network. */
    private fun prefetchAhead() {
        val r = reciter
        for (k in 1..6) {
            val item = queue.getOrNull(index + k) ?: break
            if (item < AYAH_BASE) break
            P64AyahCache.prefetch(urlFor(r, item))
        }
    }

    /** Replaces the queue without interrupting the current surah when it is still in the new list. */
""")
patch(audio, "        val surah = currentSurah ?: return\n        releasePlayer()\n        isLoading = true\n",
      "        val surah = currentSurah ?: return\n        prefetchAhead()\n        releasePlayer()\n        isLoading = true\n")
patch(audio, "    private fun preloadNext() {\n        val ctx = app ?: return\n        releaseNext()\n",
      "    private fun preloadNext() {\n        val ctx = app ?: return\n        prefetchAhead()\n        releaseNext()\n")
patch(audio, """        if (!nextReady) {
            switchWhenReady = true
            isLoading = true
            publishState()
            return true
        }
""", """        if (!nextReady) {
            switchWhenReady = true
            isLoading = true
            publishState()
            // P64: when the next verse has not arrived after a few seconds, start it again instead of waiting for ever.
            main.postDelayed({
                if (switchWhenReady && nextPlayer === n && index + 1 < queue.size) {
                    releaseNext()
                    index += 1
                    prepareCurrent()
                }
            }, 6000L)
            return true
        }
""")

# ------------------------------------------------------------------- 2. reciter list + hifz ----
listen = screens / "P55ListenUi.kt"
patch(listen, """                if (r.ayahFolder != null) Text("آية بآية", fontSize = 11.sp, color = G,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5EFE9)).padding(horizontal = 5.dp, vertical = 1.dp))
""", """                if (r.ayahFolder != null) Text("آية بآية", fontSize = 11.sp, color = G,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5EFE9)).padding(horizontal = 5.dp, vertical = 1.dp))
                else Text("(للسورة كاملة فقط)", fontSize = 11.sp, color = Muted)
""")
patch(listen, "«آية بآية» = متاح للاستماع للآية وحدها\"", "«آية بآية» = متاح للآية وحدها   (للسورة كاملة فقط) = الآيات المنفردة بصوت الحصري\"")

hifz = screens / "P62Hifz.kt"
patch(hifz, """                                P62Stepper("إلى الآية", to, from, count, Modifier.weight(1f)) { to = it; revealed.clear() }
                            }
""", """                                P62Stepper("إلى الآية", to, from, count, Modifier.weight(1f)) { to = it; revealed.clear() }
                            }
                            P64ReciterButton()
""")

# ------------------------------------------------------------------------------ 3. tasmee ----
t = screens / "P61Tasmee.kt"
patch(t, "private enum class P61Scope { AYAT, PAGES, JUZ }", "private enum class P61Scope { AYAT, PAGES, JUZ, HIZB }")
patch(t, "    var juz by remember { mutableIntStateOf(30) }\n    var menu by remember { mutableStateOf(false) }\n",
      "    var juz by remember { mutableIntStateOf(30) }\n    var hizb by remember { mutableIntStateOf(60) }\n    var quarter by remember { mutableIntStateOf(0) }\n    var menu by remember { mutableStateOf(false) }\n")
patch(t, """    val verses: List<QuranAyah> = remember(scopeMode, surah, from, to, pageFrom, pageTo, juz) {
        when (scopeMode) {
            P61Scope.AYAT -> repo.loadSurah(surah).filter { it.ayah in from..to }
            P61Scope.PAGES -> (pageFrom..maxOf(pageFrom, pageTo)).flatMap { repo.loadPage(it) }
            P61Scope.JUZ -> repo.loadJuz(juz)
        }
    }
""", """    val verses: List<QuranAyah> = remember(scopeMode, surah, from, to, pageFrom, pageTo, juz, hizb, quarter) {
        when (scopeMode) {
            P61Scope.AYAT -> repo.loadSurah(surah).filter { it.ayah in from..to }
            P61Scope.PAGES -> (pageFrom..maxOf(pageFrom, pageTo)).flatMap { repo.loadPage(it) }
            P61Scope.JUZ -> repo.loadJuz(juz)
            P61Scope.HIZB -> P64Hizb.verses(context, repo, hizb, quarter)
        }
    }
""")
patch(t, "    val overrides = remember { mutableStateListOf<Int>() }\n",
      """    val overrides = remember { mutableStateListOf<Int>() }
    val recorder = remember { P64TasmeeRecorder(context) }
    var keepAudio by remember { mutableStateOf(true) }
    var audioFile by remember { mutableStateOf<java.io.File?>(null) }
    var audioNote by remember { mutableStateOf<String?>(null) }
    var maxRms by remember { mutableFloatStateOf(0f) }
""")
patch(t, "override fun onRmsChanged(rmsdB: Float) { level = rmsdB }", "override fun onRmsChanged(rmsdB: Float) { level = rmsdB; if (rmsdB > maxRms) maxRms = rmsdB }")
patch(t, "        onDispose {\n            wantListening = false\n            runCatching { recognizer?.cancel() }\n",
      "        onDispose {\n            wantListening = false\n            recorder.cancel()\n            runCatching { recognizer?.cancel() }\n")
patch(t, """        wantListening = true
        runCatching { recognizer.startListening(intent) }
    }

    fun stopRecording() {
        wantListening = false
        runCatching { recognizer?.stopListening() }
""", """        wantListening = true
        runCatching { recognizer.startListening(intent) }
        maxRms = 0f
        audioFile = null
        audioNote = null
        if (keepAudio && !recorder.start()) audioNote = "تعذّر تشغيل تسجيل الصوت؛ التسميع والمراجعة يعملان كالمعتاد."
    }

    fun stopRecording() {
        wantListening = false
        runCatching { recognizer?.stopListening() }
        val savedAudio = recorder.stop()
        if (savedAudio != null) audioFile = savedAudio
""")
patch(t, "        while (wantListening) { delay(1000); seconds++ }\n", """        while (wantListening) {
            delay(1000); seconds++
            recorder.sample()
            // The voice is heard by the recorder but not by the speech engine: the phone shares the microphone badly,
            // so the recording is dropped and the recognition goes on as before.
            if (recorder.active && seconds >= 10 && committed.isBlank() && partial.isBlank() && maxRms < 2f && recorder.peak > 1500) {
                recorder.cancel()
                keepAudio = false
                audioNote = "هذا الموبايل لا يسمح بتسجيل الصوت أثناء التعرف على الكلام، فتم إيقاف التسجيل وأكمل التسميع كالمعتاد."
                runCatching { recognizer?.cancel() }
                runCatching { recognizer?.startListening(intent) }
            }
        }
""")
patch(t, 'P61Scope.JUZ to "جزء كامل")', 'P61Scope.JUZ to "جزء كامل", P61Scope.HIZB to "حزب")')
patch(t, "            P61Scope.JUZ -> P61Stepper(\"الجزء\", juz, 1, 30) { juz = it }\n", """            P61Scope.JUZ -> P61Stepper("الجزء", juz, 1, 30) { juz = it }
                            P61Scope.HIZB -> {
                                P61Stepper("الحزب", hizb, 1, 60) { hizb = it }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(0 to "الحزب كامل", 1 to "الربع الأول", 2 to "الربع الثاني", 3 to "الربع الثالث", 4 to "الربع الرابع").forEach { (q, name) ->
                                        FilterChip(selected = quarter == q, onClick = { quarter = q }, label = { Text(name) })
                                    }
                                }
                            }
""")
patch(t, "                        recMessage?.let { Text(it, color = P61Wrong, fontSize = 13.sp, lineHeight = 20.sp) }\n", """                        recMessage?.let { Text(it, color = P61Wrong, fontSize = 13.sp, lineHeight = 20.sp) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎙 سجّل صوتي لأسمعه بعد المراجعة", color = P61Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(checked = keepAudio, onCheckedChange = { keepAudio = it }, enabled = !wantListening)
                        }
                        audioNote?.let { Text(it, color = P61Ink.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 19.sp) }
""")
patch(t, '                            Text("ما سمعه التطبيق (عدّله لو أخطأ في السمع):"', """                            P64RecordingCard(audioFile, keepAudio, audioNote)
                            P64ReciterListenCard(verses)
                            Text("ما سمعه التطبيق (عدّله لو أخطأ في السمع):\"""")

# ----------------------------------------------------------------------- 4. Mushaf index ----
q = screens / "QuranScreen.kt"
patch(q, "    var p62Meanings by remember { mutableStateOf(false) }\n    if (p55Picker) P55SurahPicker(\n",
      "    var p62Meanings by remember { mutableStateOf(false) }\n    var p64Panel by remember { mutableStateOf(false) }\n    if (p55Picker) P55SurahPicker(\n")
patch(q, "    val lastSurah = repo.pageSurah(lastPage)\n",
      "    val lastSurah = repo.pageSurah(lastPage)\n    if (p64Panel) P64PlayPanel(startSurah = lastSurah, startName = repo.surahName(lastSurah), onDismiss = { p64Panel = false })\n")
patch(q, """            MushafHeroHeader("المصحف الشريف", onBack, onSearch, null, showBack = false)
            P57ListenHeader(onPickSurahs = { p55Picker = true }, onHifz = { p58Hifz = true },
                onTasmee = { p61TasmeeArgs = null; p61Tasmee = true },
                onSimilar = { p62Similar = true }, onMeanings = { p62Meanings = true })
""", """            P64IndexTop(onSearch)
            P64ListenHeader(onPickSurahs = { p55Picker = true }, onPlay = { p64Panel = true }, onHifz = { p58Hifz = true },
                onTasmee = { p61TasmeeArgs = null; p61Tasmee = true },
                onSimilar = { p62Similar = true }, onMeanings = { p62Meanings = true })
""")

# ------------------------------------------------------------ 5. adhkar: no synthetic voice ----
ad = screens / "P57Adhkar.kt"
patch(ad, """            IconButton(onClick = { P57Tts.speakList(context, "d:$recId", listOf(text)) }) {
                Text(if (speaking) "⏹" else "🔊", fontSize = 22.sp)
            }
""", "")
patch(ad, "    val title = chapterTitle ?: return\n    val context = LocalContext.current\n",
      "    val title = chapterTitle ?: return\n    val context = LocalContext.current\n    if (!P56AdhkarAudio.hasAudio(context, title) && !p63IsMorningEvening(title)) return\n")
patch(ad, "    var usedDevice by remember(title) { mutableStateOf(false) }\n",
      "    var usedDevice by remember(title) { mutableStateOf(false) }\n    var noVoiceMsg by remember(title) { mutableStateOf(false) }\n")
patch(ad, "            tapped = false\n            usedDevice = true\n            P57Tts.speakList(context, ttsId, texts)\n",
      "            tapped = false\n            noVoiceMsg = true\n")
patch(ad, "                else -> { usedDevice = true; P57Tts.speakList(context, ttsId, texts) }\n", "                else -> Unit\n")
patch(ad, '        if (usedDevice && speaking) Text("بصوت الموبايل (القراءة الصوتية)", color = AdGray, fontSize = 12.sp)\n',
      '        if (noVoiceMsg) Text("تعذّر تشغيل التسجيل. تأكد من الإنترنت وحاول مرة أخرى.", color = Color(0xFFB3261E), fontSize = 12.sp)\n')
