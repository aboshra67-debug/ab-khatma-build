"""P66 / VC88 safe patch on top of P65 / VC87.

1. Fix: searching in the reciter list closed the app (two reciters shared the id «yasser_salama»).
2. One compact player look everywhere (reciter, previous / play / next, speed, close, «⋯»); the big reader panel is gone.
3. Index: no duplicate «تشغيل» button; the surah picker has one big «تشغيل» and a «مع التظليل في المصحف» option.
4. Tasmee: ONE comparison card (reciter / my recording side by side); recording is OFF by default with a warning.
5. Each ▶ keeps its own place: a surah started from the index resumes where it stopped (saved on the phone).
6. Continuous recitation: for reciters with verse timings (mp3quran) the surah file plays without gaps while the
   verse being read is still tracked; other reciters keep the per-verse files and are labelled in the picker.
Additive only. Each replacement must match exactly once.
"""
from pathlib import Path
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


def patch_between(path, start, end, new, keep_end=True):
    """Replaces the text from [start] up to (not including) [end]; both markers must occur exactly once."""
    text = path.read_text(encoding="utf-8")
    assert text.count(start) == 1, (str(path), "start", text.count(start), start[:100])
    a = text.index(start)
    assert text.count(end, a) >= 1, (str(path), "end", end[:100])
    b = text.index(end, a + len(start))
    path.write_text(text[:a] + new + text[b:], encoding="utf-8")


def add(name, dest):
    assert not dest.exists(), dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes((here / name).read_bytes())


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 87", "versionCode = 88")
patch(gradle, 'versionName = "0.19.60-p65-full-controls-adhkar-card"', 'versionName = "0.19.61-p66-continuous-listen-one-player"')
add("P66Timing.kt", quran / "P66Timing.kt")
add("P66Ui.kt", screens / "P66Ui.kt")

# ------------------------------------------------------------------------------ audio engine ----
a = quran / "P52SurahAudio.kt"

# 1. the duplicated id that crashed the reciter search
patch(a, 'P52Reciter("yasser_salama", "ياسر سلامة", "مصحف الحدر", M + "yasser-salama/r1/", HADR, null),',
      'P52Reciter("yasser_salama_hadr", "ياسر سلامة", "مصحف الحدر", M + "yasser-salama/r1/", HADR, null),')

patch(a, "import android.os.PowerManager\n", "import android.os.PowerManager\nimport android.os.SystemClock\n")
patch(a, "        P64AyahCache.init(a)\n", "        P64AyahCache.init(a)\n        P66Timing.init(a)\n")

# state
patch(a, "    private var noisyRegistered = false\n", """    private var noisyRegistered = false

    // P66: a run of verses plays inside ONE full-surah file; the timings say where each verse starts and ends.
    private var segSurah = -1
    private var segReciter: String? = null
    private var segTimes: P66Timing.Surah? = null
    private var segEnd = 0
    private var segHoldUntil = 0L
    private var prepToken = 0
    private var resumeFirst = false
    private val segRunnable = object : Runnable {
        override fun run() {
            segTick()
            if (segSurah > 0) main.postDelayed(this, 60)
        }
    }

    private fun seekExact(p: MediaPlayer, ms: Int) {
        if (Build.VERSION.SDK_INT >= 26) p.seekTo(ms.toLong(), MediaPlayer.SEEK_CLOSEST) else p.seekTo(ms)
    }

    private fun segSeek(p: MediaPlayer, ms: Int) {
        segHoldUntil = SystemClock.uptimeMillis() + 350
        runCatching { seekExact(p, ms) }
    }

    /** Starts [p] at [ms] (after the seek has finished, so nothing from the start of the file is heard). */
    private fun startAt(p: MediaPlayer, ms: Int) {
        if (ms <= 0) { p.start(); return }
        segHoldUntil = SystemClock.uptimeMillis() + 500
        p.setOnSeekCompleteListener { m ->
            m.setOnSeekCompleteListener(null)
            runCatching { m.start() }
        }
        seekExact(p, ms)
    }

    private fun savedPos(item: Int): Int =
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getInt("pos_${reciterId}_$item", 0) ?: 0

    private fun clearPos(item: Int) {
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove("pos_${reciterId}_$item")?.apply()
    }

    /** Remembers where a whole surah stopped, so its ▶ button continues from there next time. */
    private fun savePos() {
        val item = currentSurah ?: return
        if (item >= AYAH_BASE || sampleReciterId != null || isLoading) return
        val p = player ?: return
        val pos = runCatching { p.currentPosition }.getOrDefault(0)
        if (pos < 2000) return
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putInt("pos_${reciterId}_$item", pos)?.apply()
    }

    private fun segTick() {
        val p = player ?: return
        if (segSurah < 0 || isLoading || !isPlaying) return
        if (SystemClock.uptimeMillis() < segHoldUntil) return
        val pos = runCatching { p.currentPosition }.getOrDefault(0)
        if (pos < segEnd - 20) return
        segAdvance(p)
    }

    /** The current verse has reached its end inside the surah file: move on to the next queued verse. */
    private fun segAdvance(p: MediaPlayer) {
        val cur = queue.getOrNull(index) ?: return
        val nxt = queue.getOrNull(index + 1)
        if (nxt == null) { stop(); return }
        val ns = (nxt - AYAH_BASE) / 1000
        val na = (nxt - AYAH_BASE) % 1000
        val t = if (nxt >= AYAH_BASE && ns == segSurah) segTimes?.times?.get(na) else null
        index += 1
        message = null
        if (t == null) { prepareCurrent(); return }
        segEnd = t[1]
        if (nxt != cur + 1) segSeek(p, t[0])
        publishMetadata()
        publishState()
        refreshNotification()
    }

    /** True when the verse is handled by the continuous mode (reused file, timings being fetched, or file preparing). */
    private fun trySegment(ctx: Context, item: Int): Boolean {
        val r = reciter
        if (item < AYAH_BASE || queue.size < 2 || !P66Timing.supported(r)) return false
        val s = (item - AYAH_BASE) / 1000
        val a = (item - AYAH_BASE) % 1000
        val tm = P66Timing.get(r, s)
        if (tm == null) {
            if (!P66Timing.needsFetch(r, s)) return false
            releasePlayer()
            isLoading = true
            isPlaying = false
            ensureSession(ctx)
            publishState()
            refreshNotification()
            val my = prepToken
            P66Timing.fetch(r, s) { main.post { if (my == prepToken && currentSurah == item) prepareCurrent() } }
            return true
        }
        val t = tm.times[a] ?: return false
        val live = player
        if (live != null && segSurah == s && segReciter == r.id && !isLoading) {
            segTimes = tm
            segEnd = t[1]
            segSeek(live, t[0])
            if (!isPlaying && requestFocus(ctx)) {
                beforePlay?.invoke()
                applySpeed(live)
                runCatching { live.start() }
                isPlaying = true
                setNoisy(ctx, true)
            }
            message = null
            publishMetadata()
            publishState()
            refreshNotification()
            return true
        }
        releasePlayer()
        isLoading = true
        isPlaying = false
        ensureSession(ctx)
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(r.surahUrl(s))
            p.setOnPreparedListener {
                if (player === it) {
                    val d = runCatching { it.duration }.getOrDefault(0)
                    if (d > 0 && tm.lastEnd > d + 1500) {
                        // the timings do not belong to this recording: use the verse files instead
                        P66Timing.markBad(r, s)
                        prepareCurrent()
                    } else {
                        isLoading = false
                        segSurah = s
                        segReciter = r.id
                        segTimes = tm
                        segEnd = t[1]
                        if (requestFocus(ctx)) {
                            applySpeed(it)
                            startAt(it, t[0])
                            isPlaying = true
                            setNoisy(ctx, true)
                        } else runCatching { seekExact(it, t[0]) }
                        publishMetadata()
                        publishState()
                        main.removeCallbacks(progressRunnable); main.post(progressRunnable)
                        main.removeCallbacks(segRunnable); main.post(segRunnable)
                        refreshNotification()
                    }
                }
            }
            p.setOnCompletionListener {
                if (player === it) {
                    segSurah = -1
                    main.removeCallbacks(segRunnable)
                    if (index + 1 < queue.size) { index += 1; prepareCurrent() } else stop()
                }
            }
            p.setOnErrorListener { mp, _, _ ->
                if (player === mp) {
                    P66Timing.markBad(r, s)
                    prepareCurrent()
                }
                true
            }
            player = p
            p.prepareAsync()
            publishMetadata()
            publishState()
            refreshNotification()
        } catch (_: Exception) {
            P66Timing.markBad(r, s)
            prepareCurrent()
        }
        return true
    }
""")

# play(): resume flag
patch(a, """    fun play(context: Context, surahs: List<Int>, start: Int = 0, sampleId: String? = null) {
        followReader = false
        init(context)""", """    fun play(context: Context, surahs: List<Int>, start: Int = 0, sampleId: String? = null, resume: Boolean = false) {
        followReader = false
        init(context)
        resumeFirst = resume && sampleId == null""")

# prepareCurrent: continuous mode first, resume position for whole surahs
patch(a, """        val surah = currentSurah ?: return
        prefetchAhead()
        releasePlayer()
        isLoading = true""", """        val surah = currentSurah ?: return
        prepToken += 1
        if (trySegment(ctx, surah)) return
        prefetchAhead()
        releasePlayer()
        isLoading = true""")
patch(a, """                        applySpeed(it)
                        it.start()
                        isPlaying = true
                        setNoisy(ctx, true)
                    }
                    publishMetadata()
                    publishState()
                    main.removeCallbacks(progressRunnable); main.post(progressRunnable)
                    refreshNotification()
                    preloadNext()""", """                        applySpeed(it)
                        val resumeAt = if (resumeFirst && surah < AYAH_BASE) savedPos(surah) else 0
                        resumeFirst = false
                        val total = runCatching { it.duration }.getOrDefault(0)
                        if (resumeAt in 3000 until (total - 8000)) startAt(it, resumeAt) else it.start()
                        isPlaying = true
                        setNoisy(ctx, true)
                    }
                    publishMetadata()
                    publishState()
                    main.removeCallbacks(progressRunnable); main.post(progressRunnable)
                    refreshNotification()
                    preloadNext()""")
patch(a, """            p.setOnCompletionListener {
                if (player === it) {
                    if (index + 1 < queue.size) { if (!switchToNext()) { index += 1; prepareCurrent() } }""", """            p.setOnCompletionListener {
                if (player === it) {
                    if (surah < AYAH_BASE) clearPos(surah)
                    if (index + 1 < queue.size) { if (!switchToNext()) { index += 1; prepareCurrent() } }""")

# previous(): inside the continuous file, "previous" restarts the verse first
patch(a, """    fun previous() {
        val p = player
""", """    fun previous() {
        val sp = player
        val cur = currentSurah
        if (segSurah > 0 && sp != null && !isLoading && cur != null && cur >= AYAH_BASE) {
            val st = segTimes?.times?.get((cur - AYAH_BASE) % 1000)?.get(0)
            val here = runCatching { sp.currentPosition }.getOrDefault(0)
            if (st != null && (here - st > 2500 || index == 0)) {
                segSeek(sp, st)
                if (!isPlaying) togglePause() else publishState()
                return
            }
        }
        val p = player
""")

# stop(): remember the place of a whole surah
patch(a, """        sleepMinutes = 0
        releasePlayer()
        queue = emptyList()""", """        sleepMinutes = 0
        savePos()
        releasePlayer()
        queue = emptyList()""")

# releasePlayer(): forget the continuous file
patch(a, """    private fun releasePlayer() {
        releaseNext()
""", """    private fun releasePlayer() {
        releaseNext()
        segSurah = -1
        segTimes = null
        main.removeCallbacks(segRunnable)
""")

# preloadNext(): nothing to prepare inside the continuous file
patch(a, """    private fun preloadNext() {
        val ctx = app ?: return
""", """    private fun preloadNext() {
        val ctx = app ?: return
        if (segSurah > 0) { releaseNext(); return }
""")

# publishState(): remember the place of a whole surah while it plays
patch(a, """    private fun publishState() {
        val s = session ?: return
""", """    private fun publishState() {
        savePos()
        val s = session ?: return
""")

# --------------------------------------------------------------------------------- the player ----
f = screens / "P52SurahListenUi.kt"
patch_between(f, "internal fun P52MiniPlayer() {", "/** P55: the reciter list now lives in P55ReciterPicker",
              "internal fun P52MiniPlayer() {\n    P66Bar()\n}\n\n")
f = screens / "P61Flow.kt"
patch_between(f, "internal fun P61FloatingPlayer(onPickReciter: () -> Unit) {", "internal fun p61SpeedLabel(speed: Float)",
              "internal fun P61FloatingPlayer(onPickReciter: () -> Unit) {\n    P66Bar()\n}\n\n")
f = screens / "P65Full.kt"
patch_between(f, "                P64ReciterButton()\n                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)",
              "            }\n        }\n    }\n}\n\n/** Slim line", "")

# reader: when something else is playing, one tap starts the recitation from THIS page
patch(screens / "P63Reader.kt", """        if (P52SurahAudio.isActive) {
            P61FloatingPlayer(onPickReciter = { showReciters = true })
        } else {""", """        if (P52SurahAudio.isActive) {
            P66Bar()
            if (!P52SurahAudio.followReader) TextButton(onClick = onPlayHere, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("▶ ابدأ التلاوة من هذه الصفحة", color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        } else {""")

# ------------------------------------------------------------------------------------- index ----
patch_between(screens / "P64Index.kt", "            Surface(\n                onClick = onPlay,", "        }\n        P64HeaderReciter()", "")
patch(screens / "QuranScreen.kt", "else P52SurahAudio.play(listenContext, (surah.index..114).toList())",
      "else P52SurahAudio.play(listenContext, (surah.index..114).toList(), resume = true)")

# surah picker: one big «تشغيل» + a side option for the Mushaf highlighting
patch_between(screens / "P56SurahPicker.kt", "                            if (onPlayFollow != null) Button(", "                        }\n                    }\n                    P53BottomNav(onBack = onDismiss",
"""                            var withMushaf by remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        P56PickerState.save(context)
                                        val all = List(repeat) { ordered }.flatten()
                                        if (withMushaf && onPlayFollow != null) onPlayFollow(all) else onPlay(all)
                                    },
                                    enabled = picked.isNotEmpty(),
                                    modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = G),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp))
                                    Text("تشغيل" + if (repeat > 1) " (×$repeat)" else "", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                                }
                                if (onPlayFollow != null) FilterChip(
                                    selected = withMushaf, onClick = { withMushaf = !withMushaf },
                                    label = { Text("مع التظليل في المصحف", fontSize = 12.sp) },
                                )
                            }
                            if (P52SurahAudio.isActive) TextButton(
                                onClick = {
                                    P56PickerState.save(context)
                                    P52SurahAudio.updateQueue(context, List(repeat) { ordered }.flatten())
                                    onDismiss()
                                },
                                enabled = picked.isNotEmpty(),
                            ) { Text("تحديث القائمة وإكمال التلاوة الحالية") }
""")

# reciter picker: say which reciters play without pauses
f = screens / "P55ListenUi.kt"
patch(f, """                if (r.ayahFolder != null) Text("آية بآية", fontSize = 11.sp, color = G,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5EFE9)).padding(horizontal = 5.dp, vertical = 1.dp))
                else Text("(للسورة كاملة فقط)", fontSize = 11.sp, color = Muted)""",
      """                if (com.ab.khatma.quran.P66Timing.supported(r)) Text("آية بآية — متصل بلا وقفات", fontSize = 11.sp, color = G,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5EFE9)).padding(horizontal = 5.dp, vertical = 1.dp))
                else if (r.ayahFolder != null) Text("(بوقفات بين الآيات)", fontSize = 11.sp, color = Muted)
                else Text("(للسورة كاملة فقط)", fontSize = 11.sp, color = Muted)""")
patch(f, "«آية بآية» = متاح للآية وحدها   (للسورة كاملة فقط) = الآيات المنفردة بصوت الحصري",
      "«متصل بلا وقفات» = التلاوة مستمرة بين الآيات   (بوقفات بين الآيات) = كل آية ملف منفصل   (للسورة كاملة فقط) = الآيات بصوت الحصري")

# ------------------------------------------------------------------------------------ tasmee ----
t = screens / "P61Tasmee.kt"
patch(t, "    var keepAudio by remember { mutableStateOf(true) }", "    var keepAudio by remember { mutableStateOf(false) }")
patch(t, "                        audioNote?.let { Text(it, color = P61Ink.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 19.sp) }\n",
      "                        audioNote?.let { Text(it, color = P61Ink.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 19.sp) }\n"
      "                        if (keepAudio) Text(\"⚠ تسجيل الصوت قد يوقف الكتابة على بعض الموبايلات. لو الكتابة وقفت اقفله.\", color = P61Wrong, fontSize = 12.sp, lineHeight = 19.sp)\n")
patch(t, "                            P64RecordingCard(audioFile, keepAudio, audioNote)\n                            P64ReciterListenCard(verses)\n",
      "                            P66ListenCard(audioFile, keepAudio, audioNote, verses)\n")
print("P66 applied")
