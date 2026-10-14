"""P57 / VC79 safe patch on top of P56 / VC78.

1. Reader full screen: one uniform background, automatic 1.25x zoom with a button to go back,
   two soft side arrows (left = next page, right = previous page).
2. Tapping a verse plays it at once with the chosen reciter (tap again = stop). The small popup is gone.
3. «استماع» dialog redesigned as a bottom sheet.
4. Quran index: big «اختيار السور للاستماع» button and a clear reciter line in the green header.
5. Adhkar: new card design, counter shown as «0 / 1», device voice (TTS) per dhikr and as fallback for a
   chapter, new section «أذكار وأدعية من القرآن» with any reciter.
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


def between(path, start, end, new, inclusive=False):
    """Replaces everything from the start marker up to (not including) the end marker."""
    text = path.read_text(encoding="utf-8")
    assert text.count(start) == 1, (str(path), "start", text.count(start), start[:90])
    s = text.index(start)
    assert text.count(end, s) >= 1, (str(path), "end", end[:90])
    e = text.index(end, s)
    if inclusive:
        e += len(end)
    path.write_text(text[:s] + new + text[e:], encoding="utf-8")


def add(name, dest):
    assert not dest.exists(), dest
    dest.write_bytes((here / name).read_bytes())


# ---------------------------------------------------------------- version ----
gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 78", "versionCode = 79")
patch(gradle, 'versionName = "0.19.51-p56-ruqyah-archive-picker"',
      'versionName = "0.19.52-p57-tap-listen-adhkar-cards"')

# ------------------------------------------------------------- new files ----
add("P57Reader.kt", screens / "P57Reader.kt")
add("P57Adhkar.kt", screens / "P57Adhkar.kt")
picker = screens / "P42AudioPicker.kt"
assert picker.exists()
picker.write_bytes((here / "P42AudioPicker.kt").read_bytes())

# ------------------------------------------- page: tap a verse = play it ----
page = screens / "P41OfflineMushafPage.kt"
patch(page, """        if (verse != null) {
            selectedVerse = verse
            hint = null
        } else {""", """        if (verse != null) {
            // P57: touching a verse plays it right away; touching it again (or another one) is handled by the player.
            selectedVerse = verse
            hint = null
            onPlayVerse(verse.first, verse.second)
        } else {""")
between(page, "        if (selectedVerse != null && inkTool != P44InkTool.PEN",
       "        } else if (hint != null && inkTool == P44InkTool.MOVE) {",
       "        if (hint != null && inkTool == P44InkTool.MOVE) {", inclusive=True)

# ------------------------------------------------------------ reader screen ----
quran = screens / "QuranScreen.kt"
patch(quran, "    LaunchedEffect(currentPage) { printedZoom = 1f; p44InkTool = P44InkTool.MOVE }",
      """    LaunchedEffect(currentPage) { p44InkTool = P44InkTool.MOVE }
    // P57: full screen opens at 1.25x so the text fills the width; leaving it returns to normal size.
    LaunchedEffect(currentPage, p41ControlsVisible) {
        printedZoom = if (!p41ControlsVisible && displayMode == QuranMode.MADINA_PAGES) 1.25f else 1f
    }""")
patch(quran, """    P55ImmersiveEffect(displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible)
    Scaffold(
        containerColor = paperSoft,""", """    P55ImmersiveEffect(displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible)
    val p57Full = displayMode == QuranMode.MADINA_PAGES && !p41ControlsVisible
    val p57Bg = if (p57Full) (if (P53NightMode.enabled) P53NightMode.pageBackground else Color(0xFFFFF9E9)) else paperSoft
    Scaffold(
        containerColor = p57Bg,""")
patch(quran, """                .background(paperSoft)
                .padding(horizontal = 8.dp),""", """                .background(p57Bg)
                .padding(horizontal = if (p57Full) 0.dp else 8.dp),""")
patch(quran, """                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f),""", """                Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),""")
patch(quran, """                        onSelectAyah = onSelectAyah,
                    )
                }
            } else {
                val targetAyahs = remember(target) {""", """                        onSelectAyah = onSelectAyah,
                    )
                }
                P57PageArrows(
                    visible = p57Full && p44InkTool == P44InkTool.MOVE,
                    hasPrev = pagerState.currentPage > 0,
                    hasNext = pagerState.currentPage < pages.lastIndex,
                    zoomed = printedZoom > 1.01f,
                    onPrev = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    onToggleZoom = { printedZoom = if (printedZoom > 1.01f) 1f else 1.25f },
                )
                }
            } else {
                val targetAyahs = remember(target) {""")

# ----------------------------------------------------------- index header ----
patch(quran, """            MushafHeroHeader("المصحف الشريف", onBack, onSearch, null, showBack = false)
""", """            MushafHeroHeader("المصحف الشريف", onBack, onSearch, null, showBack = false)
            P57ListenHeader { p55Picker = true }
""")
between(quran, """                if (tab == 0) {
                    P52ListenToolbar(""", """                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 0.dp, top = 10.dp, end = 0.dp, bottom = 20.dp),""", "")

# ------------------------------------------------------------------ adhkar ----
adhkar = screens / "InteractiveAdhkarScreen.kt"
patch(adhkar, "internal fun P56AdhkarMain(onBack: () -> Unit, onRuqya: () -> Unit) {",
      "internal fun P56AdhkarMain(onBack: () -> Unit, onRuqya: () -> Unit, onQuranDuas: () -> Unit = {}) {")
patch(adhkar, "    DisposableEffect(Unit) { onDispose { P56AdhkarAudio.stop() } }",
      "    DisposableEffect(Unit) { onDispose { P56AdhkarAudio.stop(); P57Tts.stop() } }")
patch(adhkar, "    LaunchedEffect(readerLabel) { P56AdhkarAudio.stop() }",
      "    LaunchedEffect(readerLabel) { P56AdhkarAudio.stop(); P57Tts.stop() }")
patch(adhkar, "                        item { P56RuqyaCard(onRuqya) }\n",
      "                        item { P56RuqyaCard(onRuqya) }\n                        item { P57QuranDuasCard(onQuranDuas) }\n")
patch(adhkar, 'Text("${done(rec)} / ${rec.required}", color = dhGray, fontSize = 12.sp)',
      'Text("\\u202A${done(rec)} / ${rec.required}\\u202C", color = dhGray, fontSize = 12.sp)')
between(adhkar, """                if (active != null && active.isNotEmpty()) {
                    val rec = active[at.coerceIn(0, active.lastIndex)]""",
        "                } else if (currentGroup != null) {", """                if (active != null && active.isNotEmpty()) {
                    val rec = active[at.coerceIn(0, active.lastIndex)]
                    val completed = done(rec)
                    val cat = dhCategories.firstOrNull { it.title == currentChapter?.group }
                    item { P57ChapterAudioRow(currentChapter?.title, active.map { it.text }) }
                    item {
                        P57DhikrCard(
                            recId = rec.id,
                            text = rec.text,
                            source = rec.source,
                            required = rec.required,
                            completed = completed,
                            first = cat?.first ?: dhGreen,
                            second = cat?.second ?: Color(0xFF0B3B33),
                            favourite = rec.id in favourites,
                            hasNext = at < active.lastIndex,
                            onCount = {
                                if (completed < rec.required) {
                                    val nextCount = completed + 1
                                    val key = "$today:${rec.id}"
                                    counterCache[key] = nextCount
                                    pref.edit().putInt(key, nextCount)
                                        .putInt("total:${rec.id}", pref.getInt("total:${rec.id}", 0) + 1).apply()
                                    progressChange++
                                    nextCount >= rec.required
                                } else false
                            },
                            onReset = {
                                val key = "$today:${rec.id}"; counterCache[key] = 0
                                pref.edit().putInt(key, 0).apply(); progressChange++
                            },
                            onFavourite = {
                                favourites = if (rec.id in favourites) favourites - rec.id else favourites + rec.id
                                pref.edit().putStringSet("favorites", favourites).apply()
                            },
                            onAdvance = { changePage(at + 1) },
                        )
                    }
                    if (at == active.lastIndex && active.all { done(it) >= it.required }) {
                        item { Text("ما شاء الله، أكملت أذكار هذا الباب اليوم ✨", color = dhGreen,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)) }
                    }
""")
patch(adhkar, "            P53BottomNav(onBack = { navigateBack() }, onHome = onBack)\n",
      """            if (active != null && active.isNotEmpty()) P57ReaderBar(
                at = at.coerceIn(0, active.lastIndex),
                total = active.size,
                isDone = { i -> done(active[i]) >= active[i].required },
                onPrev = { changePage(at - 1) },
                onNext = { changePage(at + 1) },
            )
            P53BottomNav(onBack = { navigateBack() }, onHome = onBack)
""")

ruq = screens / "P56Ruqya.kt"
patch(ruq, """    var ruqyah by remember { mutableStateOf(false) }
    if (ruqyah) P56RuqyaScreen(onBack = { ruqyah = false }, onHome = onBack)
    else P56AdhkarMain(onBack = onBack, onRuqya = { ruqyah = true })""", """    var page by remember { mutableIntStateOf(0) }
    when (page) {
        1 -> P56RuqyaScreen(onBack = { page = 0 }, onHome = onBack)
        2 -> P57QuranDuasScreen(onBack = { page = 0 }, onHome = onBack)
        else -> P56AdhkarMain(onBack = onBack, onRuqya = { page = 1 }, onQuranDuas = { page = 2 })
    }""")
print("P57 applied")
