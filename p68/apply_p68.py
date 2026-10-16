"""P68 / VC90 safe patch on top of P67 / VC89.

1. «قراءة» mode in the Mushaf: no buttons, the page fills the screen, swipe only; soft page-turn sound (made in code).
2. «منفردات»: the whole phrase is highlighted; extra tab «ألفاظ جاءت مرة واحدة».
3. Adhkar by voice: sections with clear names, seek bar, back / forward 10 s, speed; recordings 7-10 removed from the list.
4. Adhkar: day streak.
5. Members tab for every member of a khatma + private message button.
Additive only. Each replacement must match exactly once.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"
screens = java / "ui/screens"
quran = java / "quran"
secure = java / "secure"
assets = root / "app/src/main/assets/quran"


def patch(path, old, new, count=1):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    assert found == count, (str(path), found, old[:110])
    path.write_text(text.replace(old, new), encoding="utf-8")


def patch_between(path, start, end, new):
    """Replaces the text from [start] up to (not including) [end]; start must occur exactly once."""
    text = path.read_text(encoding="utf-8")
    assert text.count(start) == 1, (str(path), "start", text.count(start), start[:100])
    a = text.index(start)
    assert text.count(end, a + len(start)) >= 1, (str(path), "end", end[:100])
    b = text.index(end, a + len(start))
    path.write_text(text[:a] + new + text[b:], encoding="utf-8")


def replace_tail(path, start, new):
    text = path.read_text(encoding="utf-8")
    assert text.count(start) == 1, (str(path), "start", text.count(start), start[:100])
    path.write_text(text[: text.index(start)] + new, encoding="utf-8")


def add(name, dest):
    assert not dest.exists(), dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes((here / name).read_bytes())


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 89", "versionCode = 90")
patch(gradle, 'versionName = "0.19.62-p67-munfaradat-mushaf-sheet"', 'versionName = "0.19.63-p68-reading-mode-adhkar-voice"')

# ============================================================ adhkar by voice ====
v = screens / "P60Adhkar.kt"
patch(v, "internal data class VoiceTrack(val title: String, val url: String)", "internal data class VoiceTrack(val title: String, val url: String, val section: String = \"\")")
patch(v, "تسجيلات حقيقية: العفاسي، القطامي، فارس عبّاد، محمد جبريل", "تسجيلات حقيقية: العفاسي، القطامي، فارس عبّاد")
patch_between(v, "private fun voiceTrack(n: Int, who: String) = VoiceTrack(", "/** Streams a recorded adhkar track.", '''private fun voiceTrack(n: Int, section: String, title: String) = VoiceTrack(
    title,
    "https://archive.org/download/makkah-live.-net-athkar-01/MakkahLive.Net_athkar_" + n.toString().padStart(2, '0') + ".mp3",
    section,
)

/** Named by what each recording really contains (recordings 7 to 10 are not offered any more). */
internal val voiceTracks = listOf(
    voiceTrack(1, "الاستيقاظ من النوم", "أذكار الاستيقاظ — مشاري العفاسي"),
    voiceTrack(3, "الصباح", "أذكار الصباح — مشاري العفاسي"),
    voiceTrack(4, "المساء", "أذكار المساء — مشاري العفاسي"),
    voiceTrack(2, "الصباح والمساء", "أذكار الصباح والمساء — ناصر القطامي"),
    voiceTrack(5, "الصباح والمساء", "أذكار الصباح والمساء — فارس عبّاد (١)"),
    voiceTrack(6, "الصباح والمساء", "أذكار الصباح والمساء — فارس عبّاد (٢)"),
    voiceTrack(11, "النوم", "أذكار النوم — مشاري العفاسي"),
)

''')
patch_between(v, "/** Streams a recorded adhkar track.", "@Composable\ninternal fun P60VoiceCard", '''/** Streams a recorded adhkar track. Nothing is downloaded or stored. Pause, seek, ±10 s and speed (saved). */
internal object P60VoiceAdhkar {
    var playing by mutableStateOf<Int?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var position by mutableIntStateOf(0)
        private set
    var duration by mutableIntStateOf(0)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    private var player: MediaPlayer? = null
    private var speedLoaded = false
    private val steps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    fun loadSpeed(context: Context) {
        if (speedLoaded) return
        speedLoaded = true
        speed = runCatching {
            context.applicationContext.getSharedPreferences("p68_adhkar_voice", Context.MODE_PRIVATE).getFloat("speed", 1f)
        }.getOrDefault(1f).coerceIn(0.75f, 2f)
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }

    fun cycleSpeed(context: Context) {
        val i = steps.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }
        speed = steps[(if (i < 0) 1 else i + 1) % steps.size]
        runCatching { context.applicationContext.getSharedPreferences("p68_adhkar_voice", Context.MODE_PRIVATE).edit().putFloat("speed", speed).apply() }
        player?.let { p -> if (!loading && !paused) applySpeed(p) }
    }

    fun speedLabel(): String = when {
        kotlin.math.abs(speed - 0.75f) < 0.01f -> "0.75×"
        kotlin.math.abs(speed - 1.25f) < 0.01f -> "1.25×"
        kotlin.math.abs(speed - 1.5f) < 0.01f -> "1.5×"
        kotlin.math.abs(speed - 2f) < 0.01f -> "2×"
        else -> "1×"
    }

    fun tick() {
        val p = player ?: return
        if (loading) return
        runCatching {
            position = p.currentPosition
            duration = p.duration.coerceAtLeast(0)
        }
    }

    fun togglePause() {
        val p = player ?: return
        if (loading) return
        runCatching {
            if (p.isPlaying) { p.pause(); paused = true } else { p.start(); applySpeed(p); paused = false }
        }
    }

    fun seekBy(ms: Int) {
        val p = player ?: return
        if (loading) return
        runCatching {
            val to = (p.currentPosition + ms).coerceIn(0, p.duration.coerceAtLeast(0))
            p.seekTo(to)
            position = to
        }
    }

    fun seekTo(ms: Int) {
        val p = player ?: return
        if (loading) return
        runCatching { p.seekTo(ms.coerceIn(0, p.duration.coerceAtLeast(0))); position = ms }
    }

    fun toggle(index: Int) {
        if (playing == index) { stop(); return }
        stop()
        val track = voiceTracks.getOrNull(index) ?: return
        runCatching { P52SurahAudio.pause() }
        runCatching { P57Tts.stop() }
        runCatching { P56AdhkarAudio.stop() }
        failed = false
        loading = true
        playing = index
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setDataSource(track.url)
            p.setOnPreparedListener {
                if (player === it) {
                    loading = false
                    paused = false
                    duration = runCatching { it.duration }.getOrDefault(0).coerceAtLeast(0)
                    it.start()
                    applySpeed(it)
                }
            }
            p.setOnCompletionListener { if (player === it) stop() }
            p.setOnErrorListener { mp, _, _ -> if (player === mp) { stop(); failed = true }; true }
            player = p
            p.prepareAsync()
        } catch (_: Exception) { stop(); failed = true }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        playing = null
        loading = false
        paused = false
        position = 0
        duration = 0
    }
}

''')

replace_tail(screens / "P63Adhkar.kt", "@Composable\ninternal fun P63AdhkarVoices(onDismiss: () -> Unit) {", '''private fun p68Time(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
internal fun P63AdhkarVoices(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { P60VoiceAdhkar.loadSpeed(context) }
    androidx.compose.runtime.LaunchedEffect(P60VoiceAdhkar.playing) {
        while (P60VoiceAdhkar.playing != null) {
            P60VoiceAdhkar.tick()
            kotlinx.coroutines.delay(300)
        }
    }
    val sections = listOf("الاستيقاظ من النوم", "الصباح", "المساء", "الصباح والمساء", "النوم")
    Dialog(onDismissRequest = { P60VoiceAdhkar.stop(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 700.dp), shape = RoundedCornerShape(20.dp), color = P61Paper) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎙 أذكار بصوت الشيوخ", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = P61Green)
                    Text(
                        "اختر تسجيلًا واضغط عليه. يحتاج إنترنت، ولا يُحفظ شيء على الموبايل.",
                        fontSize = 12.sp, color = P61Ink.copy(alpha = 0.65f), lineHeight = 18.sp,
                    )
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sections.forEach { sec ->
                            val idx = voiceTracks.indices.filter { voiceTracks[it].section == sec }
                            if (idx.isNotEmpty()) {
                                Text(sec, color = P61Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                                idx.forEach { i ->
                                    val t = voiceTracks[i]
                                    val active = P60VoiceAdhkar.playing == i
                                    Surface(
                                        onClick = { P60VoiceAdhkar.toggle(i) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (active) Color(0xFFE6F0EA) else Color.White,
                                        border = BorderStroke(1.dp, if (active) P61Green else Color(0xFFE6DCC2)),
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                when {
                                                    active && P60VoiceAdhkar.loading -> "⏳"
                                                    active -> "⏹"
                                                    else -> "▶"
                                                },
                                                fontSize = 20.sp, color = P61Green,
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(t.title, color = P61Ink, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (P60VoiceAdhkar.playing != null) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFEDE8D8)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val dur = P60VoiceAdhkar.duration
                                androidx.compose.material3.Slider(
                                    value = if (dur > 0) P60VoiceAdhkar.position.toFloat().coerceIn(0f, dur.toFloat()) else 0f,
                                    onValueChange = { P60VoiceAdhkar.seekTo(it.toInt()) },
                                    valueRange = 0f..(if (dur > 0) dur.toFloat() else 1f),
                                    enabled = dur > 0,
                                )
                                Row(Modifier.fillMaxWidth()) {
                                    Text(p68Time(P60VoiceAdhkar.position), fontSize = 12.sp, color = P61Ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                                    Text(p68Time(dur), fontSize = 12.sp, color = P61Ink.copy(alpha = 0.7f))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { P60VoiceAdhkar.seekBy(-10000) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                                        Text("رجوع ١٠ ث", fontSize = 12.sp, maxLines = 1)
                                    }
                                    Button(
                                        onClick = { P60VoiceAdhkar.togglePause() }, modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                                    ) { Text(if (P60VoiceAdhkar.paused) "▶ تشغيل" else "⏸ إيقاف مؤقت", fontSize = 12.sp, maxLines = 1) }
                                    OutlinedButton(onClick = { P60VoiceAdhkar.seekBy(10000) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                                        Text("تقديم ١٠ ث", fontSize = 12.sp, maxLines = 1)
                                    }
                                    OutlinedButton(onClick = { P60VoiceAdhkar.cycleSpeed(context) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(P60VoiceAdhkar.speedLabel(), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    if (P60VoiceAdhkar.failed) {
                        Text("تعذّر تشغيل التسجيل. تأكد من الإنترنت وجرّب تاني.", color = Color(0xFFB3261E), fontSize = 13.sp)
                    }
                    Text(
                        "المصدر: موقع Makkah Live (عبر archive.org). التسجيلات لأصحابها، وهي هنا للتجربة والاستماع فقط.",
                        fontSize = 11.sp, color = P61Ink.copy(alpha = 0.55f), lineHeight = 16.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { P60VoiceAdhkar.stop() }, modifier = Modifier.weight(1f)) { Text("إيقاف") }
                        Button(
                            onClick = { P60VoiceAdhkar.stop(); onDismiss() }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = P61Green),
                        ) { Text("إغلاق") }
                    }
                }
            }
        }
    }
}
''')

# ============================================================ reading mode ====
add("P68Reading.kt", screens / "P68Reading.kt")
q = screens / "QuranScreen.kt"
patch(q, "    P55ImmersiveEffect(displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible)\n",
      '''    P55ImmersiveEffect(displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible)
    var p68LastPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (p68LastPage != pagerState.currentPage) {
            p68LastPage = pagerState.currentPage
            if (P68Reading.on && !p41ControlsVisible && P68Reading.soundOn) P68FlipSound.play()
        }
    }
    LaunchedEffect(p41ControlsVisible) { if (p41ControlsVisible) { P68Reading.on = false; P68Reading.hint = false } }
    LaunchedEffect(Unit) { P68Reading.load(p55Ctx) }
    DisposableEffect(Unit) { onDispose { P68Reading.on = false; P68Reading.hint = false } }
    LaunchedEffect(P68Reading.hint) { if (P68Reading.hint) { kotlinx.coroutines.delay(3500); P68Reading.hint = false } }
''')
# one touch in reading mode only shows / hides the small exit chip
patch(q, '''                            onToggleControls = {
                                val visible = !p41ControlsVisible
                                p41ControlsVisible = visible
                                P55Reader.set(p55Ctx, !visible)
                            },''', '''                            onToggleControls = {
                                if (P68Reading.on && !p41ControlsVisible) {
                                    P68Reading.hint = !P68Reading.hint
                                } else {
                                    val visible = !p41ControlsVisible
                                    p41ControlsVisible = visible
                                    P55Reader.set(p55Ctx, !visible)
                                }
                            },''')
patch(q, "visible = p57Full && p44InkTool == P44InkTool.MOVE,", "visible = p57Full && !P68Reading.on && p44InkTool == P44InkTool.MOVE,")
patch(q, "if (p57Full && p65Player) Box(Modifier.align(Alignment.BottomCenter)", "if (p57Full && !P68Reading.on && p65Player) Box(Modifier.align(Alignment.BottomCenter)")
patch(q, "                if (p57Full) Row(\n                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),",
      '''                if (p57Full && P68Reading.on && P68Reading.hint) P68ExitReading(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                ) { P68Reading.on = false; P68Reading.hint = false; P55Reader.set(p55Ctx, false); p41ControlsVisible = true; p65Player = false }
                if (p57Full && !P68Reading.on) Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),''')
patch(q, "                    onFull = { P55Reader.set(p55Ctx, true); p41ControlsVisible = false },",
      "                    onFull = { P55Reader.set(p55Ctx, true); p41ControlsVisible = false },\n                    onRead = { P68Reading.on = true; P68Reading.hint = false; P55Reader.set(p55Ctx, true); p41ControlsVisible = false },")

t = screens / "P63Reader.kt"
patch(t, "    onFull: () -> Unit,\n    onSettings: () -> Unit,\n) {", "    onFull: () -> Unit,\n    onRead: () -> Unit,\n    onSettings: () -> Unit,\n) {")
patch(t, '            P63Action(Icons.Rounded.Fullscreen, "تكبير", tint, modifier = Modifier.weight(1f), onClick = onFull)\n',
      '            P63Action(Icons.Rounded.Fullscreen, "تكبير", tint, modifier = Modifier.weight(1f), onClick = onFull)\n'
      '            P63Action(Icons.Rounded.AutoStories, "قراءة", tint, modifier = Modifier.weight(1f), onClick = onRead)\n')
patch(t, "import androidx.compose.material.icons.rounded.Bookmark\n", "import androidx.compose.material.icons.rounded.AutoStories\nimport androidx.compose.material.icons.rounded.Bookmark\n")

patch(screens / "P62Reader.kt", '''                        Switch(checked = P67Look.white, onCheckedChange = { P67Look.toggle(p67Ctx) })
                    }
''', '''                        Switch(checked = P67Look.white, onCheckedChange = { P67Look.toggle(p67Ctx) })
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) { P68Reading.load(p67Ctx) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔊 صوت تقليب الصفحة (وضع القراءة)", Modifier.weight(1f), color = P61Ink)
                        Switch(checked = P68Reading.soundOn, onCheckedChange = { P68Reading.toggleSound(p67Ctx) })
                    }
''')

# ============================================================ munfaradat: whole phrase + once-only words ====
sys.path.insert(0, str(here))
import json
import p68_unique_gen

munf_json = assets / "p67_unique.json"
assert munf_json.exists()
munf_json.write_text(json.dumps(p68_unique_gen.build(str(assets / "quran-uthmani.json")), ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
hap_json = assets / "p68_hapax.json"
assert not hap_json.exists()
hap_json.write_text(json.dumps(p68_unique_gen.hapax(str(assets / "quran-uthmani.json")), separators=(",", ":")), encoding="utf-8")

m = screens / "P67Munfaradat.kt"
patch(m, "internal class P67Word(val index: Int, val level: Int, val variants: List<Pair<String, String>>)",
      "internal class P67Word(val index: Int, val level: Int, val variants: List<Pair<String, String>>, val spanStart: Int = index, val spanEnd: Int = index)")
patch(m, '''internal class P67Item(val surah: Int, val ayah: Int, val words: List<P67Word>) {
    val key: String get() = "$surah:$ayah"''', '''internal class P67Item(val surah: Int, val ayah: Int, val words: List<P67Word>, val hapax: Boolean = false) {
    val key: String get() = "$surah:$ayah" + if (hapax) ":h" else ""''')
patch(m, '''                words.add(P67Word(w.getInt(0), w.getInt(1), (0 until vs.length()).map { j ->
                    val v = vs.getJSONArray(j)
                    v.getString(0) to v.getString(1)
                }))''', '''                words.add(P67Word(w.getInt(0), w.getInt(1), (0 until vs.length()).map { j ->
                    val v = vs.getJSONArray(j)
                    v.getString(0) to v.getString(1)
                }, w.optInt(3, w.getInt(0)), w.optInt(4, w.getInt(0))))''')
patch(m, "    fun status(context: Context, kind: String): Set<String> =", '''    /** Words that occur once in the whole Quran (by their written form). */
    fun loadHapax(context: Context): List<P67Item> {
        val raw = context.applicationContext.assets.open("quran/p68_hapax.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val o = JSONObject(raw)
        val out = ArrayList<P67Item>(o.length())
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val p = k.split(':')
            val arr = o.getJSONArray(k)
            out.add(P67Item(p[0].toInt(), p[1].toInt(), (0 until arr.length()).map { P67Word(arr.getInt(it), 1, emptyList()) }, hapax = true))
        }
        out.sortWith(compareBy({ it.surah }, { it.ayah }))
        return out
    }

    fun status(context: Context, kind: String): Set<String> =''')
patch(m, "    var surah by remember { mutableStateOf(0) }\n", '''    var mode by remember { mutableStateOf(0) } // 0 = distinctive words of similar ayahs, 1 = words that occur once
    var hapax by remember { mutableStateOf<List<P67Item>?>(null) }
    LaunchedEffect(mode) {
        if (mode == 1 && hapax == null) {
            val r = withContext(Dispatchers.Default) { runCatching { P67Munfaradat.loadHapax(context) }.getOrNull() }
            if (r == null) { failed = true; mode = 0 } else hapax = r
        }
    }
    var surah by remember { mutableStateOf(0) }
''')
patch(m, "    val list = all\n", "    val list = if (mode == 0) all else hapax\n")
patch(m, "1 -> l.filter { it.strength >= 3 }", "1 -> l.filter { it.hapax || it.strength >= 3 }")
patch(m, "                        val q = p67MakeQuiz(repo, pool, list)", "                        val q = p67MakeQuiz(repo, pool, all ?: list)")
patch(m, '''        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {''', '''        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            P62ChipRow(listOf("منفردات الآيات" to (mode == 0), "ألفاظ جاءت مرة واحدة" to (mode == 1))) { mode = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {''')
patch(m, "                Text(p67Stars(item.strength), color = P61Gold, fontSize = 14.sp)", "                if (!item.hapax) Text(p67Stars(item.strength), color = P61Gold, fontSize = 14.sp)")
patch(m, "    val marked = remember(item) { item.words.associateBy { it.index } }\n", '''    val marked = remember(item) { item.words.associateBy { it.index } }
    val frame = remember(item) { item.words.flatMap { (it.spanStart..it.spanEnd).toList() }.toSet() }
''')
patch(m, '''            if (w != null) {
                withStyle(SpanStyle(color = P61Green, fontWeight = FontWeight.ExtraBold, background = P61Tan.copy(alpha = 0.55f))) { append(t) }
            } else append(t)
            if (i < tokens.lastIndex) append(" ")''', '''            if (w != null) {
                withStyle(SpanStyle(color = P61Green, fontWeight = FontWeight.ExtraBold, background = P61Tan.copy(alpha = 0.85f))) { append(t) }
            } else if (i in frame) {
                withStyle(SpanStyle(background = P61Tan.copy(alpha = 0.32f))) { append(t) }
            } else append(t)
            if (i < tokens.lastIndex) {
                if (i in frame && (i + 1) in frame) withStyle(SpanStyle(background = P61Tan.copy(alpha = 0.32f))) { append(" ") } else append(" ")
            }''')
patch(m, "            item.words.forEach { w ->\n", '''            if (item.hapax) Text("لفظ ورد مرة واحدة في القرآن كله", fontSize = 12.sp, color = P61Ink.copy(alpha = 0.7f))
            item.words.forEach { w ->
''')
patch(m, '"${q.item.surah}:${q.item.ayah}"', "q.item.key", count=2)

# ============================================================ members tab for every member ====
add("P68MemberList.kt", secure / "P68MemberList.kt")
patch(secure / "P25GroupScreens.kt", 'else listOf("read" to "لوحة المتابعة", "plans" to "جدولي", "chat" to "المحادثة")',
      'else listOf("read" to "لوحة المتابعة", "plans" to "جدولي", "members" to "الأعضاء", "chat" to "المحادثة")')
sv = secure / "SecureV2Screen.kt"
patch(sv, '''                            } // existing full-juz actions remain on reading tab
''', '''                            } // existing full-juz actions remain on reading tab
                            if (groupTab == "members" && item.status == "active" && item.role != "leader") {
                                P68MemberList(
                                    details = groupInfo?.takeIf { it.id == item.groupId },
                                    onRetry = {
                                        runTask {
                                            groupInfo = runCatching {
                                                withContext(Dispatchers.IO) { SecureV2Api.groupStatus(active.token, item.groupId) }
                                            }.getOrNull()
                                        }
                                    },
                                    onMessage = { who ->
                                        groupTab = "chat"
                                        chatGroupId = item.groupId
                                        chatMessages = emptyList()
                                        chatError = ""
                                        chatText = "@" + who + " "
                                    },
                                )
                            }
''')

# ============================================================ adhkar: day streak ====
add("P68Streak.kt", screens / "P68Streak.kt")
ia = screens / "InteractiveAdhkarScreen.kt"
patch(ia, '''                                        .putInt("total:${rec.id}", pref.getInt("total:${rec.id}", 0) + 1).apply()
                                    progressChange++''', '''                                        .putInt("total:${rec.id}", pref.getInt("total:${rec.id}", 0) + 1).apply()
                                    p68MarkAdhkarDay(pref, today)
                                    progressChange++''')
patch(ia, '''                            if (active == null && chapters.isNotEmpty()) {
                                Spacer(Modifier.height(9.dp))''', '''                            if (active == null) {
                                val p68St = remember(progressChange, today) { p68Streak(pref, today) }
                                if (p68St > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("🔥 مواظب على أذكارك $p68St يوم متتالي", fontSize = 12.sp, color = Color(0xFFFFE6A9))
                                }
                            }
                            if (active == null && chapters.isNotEmpty()) {
                                Spacer(Modifier.height(9.dp))''')
print("P68 applied")
