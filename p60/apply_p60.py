"""P60 / VC82 safe patch on top of P59 / VC81.

1. «اختيار السور»: back / home bar, and «🎧 تشغيل مع التظليل» (ayah by ayah in the background player;
   the reader opens on the first surah, highlights the verse being recited, turns pages and follows into the next surah).
2. Reader: cleaner top (zoom moved to a clear bar at the bottom, drawing tools behind «✏️ أدوات»),
   floating mini player, one zoom range for pinch and buttons.
3. Tasbih with the dhikr text, a choice of adhkar, a custom dhikr and the after-prayer sequence.
4. Adhkar with real reciters (streamed from the Makkah Live archive, for testing).
Each replacement must match exactly once.
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
    dest.write_bytes((here / name).read_bytes())


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 81", "versionCode = 82")
patch(gradle, 'versionName = "0.19.54-p59-tts-queries-fonts"', 'versionName = "0.19.55-p60-follow-clean-reader"')

add("P60Reader.kt", screens / "P60Reader.kt")
add("P60Adhkar.kt", screens / "P60Adhkar.kt")

# ------------------------------------------------------------ player: follow mode ----
audio = java / "quran/P52SurahAudio.kt"
patch(audio, "    val isActive: Boolean get() = currentSurah != null\n", """    val isActive: Boolean get() = currentSurah != null

    /** P60: the Mushaf follows the recitation only when it was started with «تشغيل مع التظليل». */
    var followReader by mutableStateOf(false)
    val playingSurah: Int? get() = currentSurah?.let { if (it >= AYAH_BASE) (it - AYAH_BASE) / 1000 else it }
    val followKey: String? get() {
        if (!followReader) return null
        val item = currentSurah ?: return null
        if (item < AYAH_BASE) return null
        val code = item - AYAH_BASE
        return "${code / 1000}:${code % 1000}"
    }
""")
patch(audio, "    fun play(context: Context, surahs: List<Int>, start: Int = 0, sampleId: String? = null) {\n",
      "    fun play(context: Context, surahs: List<Int>, start: Int = 0, sampleId: String? = null) {\n        followReader = false\n")

patch(screens / "P52SurahListenUi.kt", "    val nowPlaying = P52SurahAudio.currentSurah == number",
      "    val nowPlaying = P52SurahAudio.playingSurah == number")

# ---------------------------------------------------------------- surah picker ----
pick = screens / "P56SurahPicker.kt"
patch(pick, "    onDismiss: () -> Unit,\n    onPlay: (List<Int>) -> Unit,\n) {\n    val context = LocalContext.current\n    LaunchedEffect(Unit) {\n        P52SurahAudio.init(context)",
      "    onDismiss: () -> Unit,\n    onPlay: (List<Int>) -> Unit,\n    onHome: (() -> Unit)? = null,\n    onPlayFollow: ((List<Int>) -> Unit)? = null,\n) {\n    val context = LocalContext.current\n    LaunchedEffect(Unit) {\n        P52SurahAudio.init(context)")
patch(pick, "    val playingNow = if (P52SurahAudio.isActive) P52SurahAudio.currentSurah else null",
      "    val playingNow = if (P52SurahAudio.isActive) P52SurahAudio.playingSurah else null")
patch(pick, """                            if (P52SurahAudio.isActive) {
                                Button(
                                    onClick = {
                                        P56PickerState.save(context)
                                        P52SurahAudio.updateQueue""", """                            if (onPlayFollow != null) Button(
                                onClick = { P56PickerState.save(context); onPlayFollow(List(repeat) { ordered }.flatten()) },
                                enabled = picked.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = G),
                            ) { Text("🎧 تشغيل مع التظليل في المصحف", fontWeight = FontWeight.Bold) }
                            if (P52SurahAudio.isActive) {
                                Button(
                                    onClick = {
                                        P56PickerState.save(context)
                                        P52SurahAudio.updateQueue""")
patch(pick, """                            else Button(
                                onClick = { P56PickerState.save(context); onPlay(List(repeat) { ordered }.flatten()) },
                                enabled = picked.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = G),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp))
                                Text("تشغيل" + if (repeat > 1) " (×$repeat)" else "", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
""", """                            else OutlinedButton(
                                onClick = { P56PickerState.save(context); onPlay(List(repeat) { ordered }.flatten()) },
                                enabled = picked.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp))
                                Text("تشغيل السور كاملة" + if (repeat > 1) " (×$repeat)" else "", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    P53BottomNav(onBack = onDismiss, onHome = onHome ?: onDismiss)
""")

# ---------------------------------------------------------------- reader + index ----
quran = screens / "QuranScreen.kt"
patch(quran, """        onDismiss = { p55Picker = false },
        onPlay = { list -> P52SurahAudio.play(listenContext, list); p55Picker = false },
    )""", """        onDismiss = { p55Picker = false },
        onPlay = { list -> P52SurahAudio.play(listenContext, list); p55Picker = false },
        onHome = { p55Picker = false; onHome() },
        onPlayFollow = { list ->
            val codes = list.flatMap { s ->
                val n = repo.surahs().firstOrNull { it.index == s }?.ayahs ?: 0
                (1..n).map { a -> P52SurahAudio.ayahCode(s, a) }
            }
            if (codes.isNotEmpty()) {
                P52SurahAudio.play(listenContext, codes)
                P52SurahAudio.followReader = true
                p55Picker = false
                list.firstOrNull()?.let(onOpenSurah)
            }
        },
    )""")
patch(quran, "                    playingKey = playingKey,\n                    highlightedAyahKey = highlightedAyahKey,\n                    audioMessage = audioMessage,",
      "                    playingKey = playingKey ?: P52SurahAudio.followKey,\n                    highlightedAyahKey = highlightedAyahKey,\n                    audioMessage = audioMessage,")
patch(quran, "        P52SurahAudio.beforePlay = { stopAudio() }\n        onDispose { P52SurahAudio.beforePlay = null }\n    }\n",
      """        P52SurahAudio.beforePlay = { stopAudio() }
        onDispose { P52SurahAudio.beforePlay = null }
    }
    // P60: when the recitation moves on to the next surah of the list, the Mushaf moves with it.
    val p60FollowSurah = P52SurahAudio.followKey?.substringBefore(":")?.toIntOrNull()
    LaunchedEffect(p60FollowSurah) {
        val current = target
        if (p60FollowSurah != null && current != null && current.surah != null && current.surah != p60FollowSurah) {
            target = ReadingTarget(
                key = "surah_$p60FollowSurah",
                title = "سورة ${repo.surahName(p60FollowSurah)}",
                pages = repo.surahPageRange(p60FollowSurah),
                surah = p60FollowSurah,
            )
        }
    }
""")

# top row: page zoom leaves the top row (font size stays for the flowing text mode)
patch(quran, """                    Text(if (displayMode == QuranMode.MADINA_PAGES) "تكبير الصفحة" else "حجم الخط",""",
      """                    if (displayMode != QuranMode.MADINA_PAGES) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (displayMode == QuranMode.MADINA_PAGES) "تكبير الصفحة" else "حجم الخط",""")
patch(quran, """                    ) { Text("+", fontSize = 20.sp) }
                }
            }
""", """                    ) { Text("+", fontSize = 20.sp) }
                    }
                }
            }
""")
# drawing tools behind a button
patch(quran, "    var p44InkTool by remember(target.key) { mutableStateOf(P44InkTool.MOVE) }\n",
      "    var p44InkTool by remember(target.key) { mutableStateOf(P44InkTool.MOVE) }\n    var p60Tools by remember(target.key) { mutableStateOf(false) }\n")
patch(quran, "                    P53NightToggle(accent)\n                }\n            }\n\n            if(displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible) {",
      """                    P53NightToggle(accent)
                    TextButton(onClick = { p60Tools = !p60Tools; if (!p60Tools) p44InkTool = P44InkTool.MOVE },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text(if (p60Tools || p44InkTool != P44InkTool.MOVE) "✏️ إخفاء الأدوات" else "✏️ أدوات", fontSize = 12.sp)
                    }
                }
            }

            if(displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible && (p60Tools || p44InkTool != P44InkTool.MOVE)) {""")
# clear zoom bar in the bottom bar
patch(quran, """                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                if (displayMode == QuranMode.MADINA_PAGES) {
                                    when {
                                        pagerState.currentPage > 0 -> scope.launch {""", """                    if (displayMode == QuranMode.MADINA_PAGES) P60ZoomBar(
                        zoom = printedZoom,
                        onMinus = { printedZoom = (printedZoom - 0.25f).coerceIn(1f, 3.5f) },
                        onPlus = { printedZoom = (printedZoom + 0.25f).coerceIn(1f, 3.5f) },
                        onReset = { printedZoom = 1f },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                if (displayMode == QuranMode.MADINA_PAGES) {
                                    when {
                                        pagerState.currentPage > 0 -> scope.launch {""")
# floating mini player over the page + zoom toggle that makes room for it
patch(quran, """                    onToggleZoom = { printedZoom = if (printedZoom > 1.01f) 1f else 1.25f },
                )
""", """                    onToggleZoom = { printedZoom = if (printedZoom > 1.01f) 1f else 1.25f },
                    bottomPad = if (P52SurahAudio.isActive) 92 else 18,
                )
                if (P52SurahAudio.isActive) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    P52MiniPlayer()
                }
""")
patch(screens / "P57Reader.kt", "    onToggleZoom: () -> Unit,\n) {\n    if (!visible) return",
      "    onToggleZoom: () -> Unit,\n    bottomPad: Int = 18,\n) {\n    if (!visible) return")
patch(screens / "P57Reader.kt", "modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp),",
      "modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottomPad.dp),")
patch(screens / "P57Reader.kt", "color = Color(0xFF064D3B).copy(alpha = 0.45f),", "color = Color(0xFF064D3B).copy(alpha = 0.72f),")
patch(screens / "P41OfflineMushafPage.kt", "val next = (zoom * factor).coerceIn(1f, 3f)", "val next = (zoom * factor).coerceIn(1f, 3.5f)")

# ------------------------------------------------------- adhkar entry points ----
ruq = screens / "P56Ruqya.kt"
patch(ruq, "        3 -> P58TasbihScreen(onBack = { page = 0 }, onHome = onBack)\n",
      "        3 -> P60TasbihScreen(onBack = { page = 0 }, onHome = onBack)\n        5 -> P60VoiceScreen(onBack = { page = 0 }, onHome = onBack)\n")
patch(ruq, "onTasbih = { page = 3 }, onKhatmDua = { page = 4 })", "onTasbih = { page = 3 }, onKhatmDua = { page = 4 }, onVoice = { page = 5 })")
adh = screens / "InteractiveAdhkarScreen.kt"
patch(adh, "onTasbih: () -> Unit = {}, onKhatmDua: () -> Unit = {}) {", "onTasbih: () -> Unit = {}, onKhatmDua: () -> Unit = {}, onVoice: () -> Unit = {}) {")
patch(adh, "                        item { P58ToolsRow(onTasbih, onKhatmDua) }\n",
      "                        item { P60VoiceCard(onVoice) }\n                        item { P58ToolsRow(onTasbih, onKhatmDua) }\n")
print("P60 applied")
