"""P55 / VC77 safe patch on top of P54 / VC76.

1. Verse touch restored: coloured pages (with the verse map) are bundled again and are the
   default; the plain «أصلي» pages become the optional one-time download.
2. Verse-by-verse and single-verse listening use the chosen reciter (EveryAyah), Husary fallback.
3. 38 verified reciters, reciter picker with search, sections, favourites and a voice sample.
4. Full-screen Mushaf (frame hidden only; swipe, verse touch, pen, zoom untouched).
5. Tools row fixed (zoom % no longer squeezed); night + full-screen moved to the second row.
6. New «اختيار السور» page: search, presets, order, repeat, saved lists.
7. Real media controls: lock screen, notification, headset buttons, pause on calls/unplug.
Each replacement must match exactly once.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"


def patch(path, old, new):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    assert found == 1, (str(path), found, old[:100])
    path.write_text(text.replace(old, new), encoding="utf-8")


def add(name, dest):
    assert not dest.exists(), dest
    dest.write_bytes((here / name).read_bytes())


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 76", "versionCode = 77")
patch(gradle, 'versionName = "0.19.49-p54-small-apk-account-delete"',
      'versionName = "0.19.50-p55-verse-touch-reciters-fullscreen"')

# ---- player core: same public API, now with MediaSession / focus / 38 reciters -------------
audio = java / "quran/P52SurahAudio.kt"
old_audio = audio.read_text(encoding="utf-8")
assert "fun cycleSleepTimer()" in old_audio and "ACTION_PLAY_SURAH" in old_audio
audio.write_bytes((here / "P52SurahAudio.kt").read_bytes())

add("P55ListenUi.kt", java / "ui/screens/P55ListenUi.kt")
add("P55PlainPages.kt", java / "ui/screens/P55PlainPages.kt")
add("P55ReaderUi.kt", java / "ui/screens/P55ReaderUi.kt")

listen = java / "ui/screens/P52SurahListenUi.kt"
text = listen.read_text(encoding="utf-8")
marker = "/** P52: reciter list in the same calm style as the rest of the Mushaf. */"
assert text.count(marker) == 1
text = text[:text.index(marker)] + '''/** P55: the reciter list now lives in P55ReciterPicker (search, favourites, sample). */
@Composable
internal fun P52ReciterPicker(onDismiss: () -> Unit, onPick: (P52Reciter) -> Unit) =
    P55ReciterPicker(onDismiss, onPick)
'''
listen.write_text(text, encoding="utf-8")

app = java / "KhatmaApplication.kt"
patch(app, "        runCatching { com.ab.khatma.ui.screens.P54ColoredPages.init(this) }\n",
      "        runCatching { com.ab.khatma.ui.screens.P54ColoredPages.init(this) }\n"
      "        runCatching { com.ab.khatma.ui.screens.P55PlainPages.init(this) }\n"
      "        runCatching { com.ab.khatma.ui.screens.P55Reader.load(this) }\n")

# ---- page loading: coloured bundled; plain = downloaded file, else coloured -----------------
page = java / "ui/screens/P41OfflineMushafPage.kt"
patch(page, "                val downloaded = if (colored) P54ColoredPages.pageFile(context, name) else null",
      "                val downloaded = if (!colored) P55PlainPages.pageFile(context, name) else null")
patch(page, '''context.assets.open("quran_pages/$name").use {''',
      '''context.assets.open("quran_pages_colored/$name").use {''')

quran = java / "ui/screens/QuranScreen.kt"
patch(quran, "import androidx.compose.foundation.verticalScroll\n",
      "import androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.horizontalScroll\n")
patch(quran, "mutableStateOf(prefs.coloredMushaf() && P54ColoredPages.ready)",
      "mutableStateOf(prefs.coloredMushaf() || !P55PlainPages.ready)")
patch(quran, '''                            if (!coloredMushaf && !P54ColoredPages.ready) {
                                P54ColoredPages.askVisible = true''', '''                            if (coloredMushaf && !P55PlainPages.ready) {
                                P55PlainPages.askVisible = true''')
patch(quran, '''                    if (displayMode == QuranMode.MADINA_PAGES) P53NightToggle(accent)
                    P54ColoredGate(onReady = {
                        coloredMushaf = true
                        prefs.saveColoredMushaf(true)
                        printedZoom = 1f
                    })''', '''                    P55PlainGate(onReady = {
                        coloredMushaf = false
                        prefs.saveColoredMushaf(false)
                        printedZoom = 1f
                    })''')
patch(quran, '''            if (displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,''', '''            if (displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),''')
patch(quran, '''                    TextButton(onClick = { showMarks = true }) { Text("علاماتي", fontSize = 12.sp) }''',
      '''                    TextButton(onClick = { showMarks = true }) { Text("علاماتي", fontSize = 12.sp) }
                    P53NightToggle(accent)
                    P55FullscreenButton(accent) { p41ControlsVisible = false }''')
patch(quran, "    var p41ControlsVisible by remember(target.key) { mutableStateOf(true) }",
      "    var p41ControlsVisible by remember(target.key) { mutableStateOf(!P55Reader.full) }")
patch(quran, "    BackHandler(enabled = displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible) { p41ControlsVisible = true }",
      "    val p55Ctx = LocalContext.current\n"
      "    BackHandler(enabled = displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible) {\n"
      "        p41ControlsVisible = true\n        P55Reader.set(p55Ctx, false)\n    }")
patch(quran, '''    Scaffold(
        containerColor = paperSoft,
        topBar = {
            // Home and Back stay accessible even when reading tools are hidden.
            TopAppBar(''', '''    P55ImmersiveEffect(displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible)
    Scaffold(
        containerColor = paperSoft,
        floatingActionButton = {
            if (displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible)
                P55ExitFullscreenFab { p41ControlsVisible = true }
        },
        topBar = {
            // Hidden only in full-screen; one tap on the corner button brings everything back.
            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible) TopAppBar(''')
# verse audio follows the chosen reciter
patch(quran, '''            audioMessage = null
            audioLoadingKey = key
            player.setDataSource(cfg.ayahUrl(aya.surah, aya.ayah))''', '''            audioMessage = null
            audioLoadingKey = key
            val p55Url = P52SurahAudio.savedReciter.ayahUrl(aya.surah, aya.ayah)
            if (p55Url == null) audioMessage = P52SurahAudio.savedReciter.name + " متاح للسورة كاملة فقط — الآية بصوت الحصري."
            player.setDataSource(p55Url ?: cfg.ayahUrl(aya.surah, aya.ayah))''')
# «اختيار سور» opens the new page
patch(quran, "                        onToggleSelecting = { selecting = !selecting; selectedSurahs = emptyList() },",
      "                        onToggleSelecting = { p55Picker = true },")
patch(quran, "    var selectedSurahs by remember { mutableStateOf<List<Int>>(emptyList()) }\n",
      '''    var selectedSurahs by remember { mutableStateOf<List<Int>>(emptyList()) }
    var p55Picker by remember { mutableStateOf(false) }
    if (p55Picker) P55SurahPicker(
        surahs = repo.surahs().map { s ->
            P55SurahInfo(s.index, s.name, s.ayahs, repo.surahPageRange(s.index).let { it.last - it.first + 1 })
        },
        onDismiss = { p55Picker = false },
        onPlay = { list -> P52SurahAudio.play(listenContext, list); p55Picker = false },
    )
''')

picker = java / "ui/screens/P42AudioPicker.kt"
patch(picker, '''                Text("الشيخ محمود خليل الحصري — المصحف المعلم")''', '''                P55VerseReciterRow()''')

assert len(list((root / "app/src/main/assets/quran_pages_colored").glob("*.webp"))) == 604
assert len(list((root / "app/src/main/assets/quran_verse_hitmaps").glob("*.json"))) == 604
assert not list(root.rglob("*.p12")) and not list(root.rglob("*.jks")) and not list(root.rglob("*.keystore"))
print("P55: verse touch restored, 38 reciters, verse reciter, full screen, surah picker, media controls, VC77")
