"""P62 / VC84 safe patch on top of P61 / VC83.

1. The coloured Mushaf pages (images, verse highlight, ink tools, zoom) are the main reader again, dressed like the
   reference app: a thin icon bar, a player strip / floating card, a page seek bar with a surah bubble. The page is
   turned left/right and fills the screen; one «أدوات» button holds the pen; «تكبير» opens the page full screen and
   «تصغير» comes back. The continuous text reader stays as an option (⚙ settings), no longer the default.
2. «🔁 المتشابهات» and «📖 المفردات» are separate pages with their own buttons on the Mushaf index.
3. «وضع الحفظ» re-arranged: range on top, verses in the middle, controls pinned at the bottom.
4. Small VC83 fixes: the ayah circle never sits alone on a line, evenly spaced option chips, tighter line spacing.
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


def cut(path, start, end, new):
    """Replace everything from `start` up to (not including) `end`; both must be unique."""
    text = path.read_text(encoding="utf-8")
    assert text.count(start) == 1, (str(path), "start", text.count(start), start[:100])
    assert text.count(end) == 1, (str(path), "end", text.count(end), end[:100])
    a = text.index(start)
    b = text.index(end)
    assert a < b
    path.write_text(text[:a] + new + text[b:], encoding="utf-8")


def add(name, dest):
    assert not dest.exists(), dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes((here / name).read_bytes())


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 83", "versionCode = 84")
patch(gradle, 'versionName = "0.19.56-p61-flow-tasmee-similar"', 'versionName = "0.19.57-p62-colored-reader-tools"')

for kt in ("P62Reader.kt", "P62Pages.kt", "P62Hifz.kt"):
    add(kt, screens / kt)

# ------------------------------------------------------- P61 pieces reused by the new screens ----
common = screens / "P61Common.kt"
patch(common, "    var enabled by mutableStateOf(true)\n", "    var enabled by mutableStateOf(false)\n")
patch(common, 'getBoolean("flow_reader", true)', 'getBoolean("flow_reader_v62", false)')
patch(common, 'putBoolean("flow_reader", on)', 'putBoolean("flow_reader_v62", on)')
patch(common, "private fun p61Colored(", "internal fun p61Colored(")

flow = screens / "P61Flow.kt"
patch(flow, "private fun P61AudioStrip(", "internal fun P61AudioStrip(")
patch(flow, "private fun P61FloatingPlayer(", "internal fun P61FloatingPlayer(")
patch(flow, "                append(body)\n                append(' ')\n                appendInlineContent(",
      "                append(body)\n                append('\\u00A0')\n                appendInlineContent(")
patch(flow, "lineHeight = (fontSize * 1.95f).sp", "lineHeight = (fontSize * 1.75f).sp")

# ------------------------------------------------------------ index header: two more buttons ----
p57 = screens / "P57Reader.kt"
patch(p57, "internal fun P57ListenHeader(onPickSurahs: () -> Unit, onHifz: () -> Unit = {}, onTasmee: () -> Unit = {}) {",
      "internal fun P57ListenHeader(\n    onPickSurahs: () -> Unit,\n    onHifz: () -> Unit = {},\n    onTasmee: () -> Unit = {},\n"
      "    onSimilar: () -> Unit = {},\n    onMeanings: () -> Unit = {},\n) {")
patch(p57, """                Text("🎙 تسميع", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
    }
}
""", """                Text("🎙 تسميع", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = onSimilar,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B624C),
                border = BorderStroke(1.dp, Color(0xFFDFBD78).copy(alpha = 0.6f)),
            ) {
                Text("🔁 المتشابهات", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
            Surface(
                onClick = onMeanings,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B624C),
                border = BorderStroke(1.dp, Color(0xFFDFBD78).copy(alpha = 0.6f)),
            ) {
                Text("📖 المفردات", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
    }
}
""")

# ------------------------------------------------------------------------------ QuranScreen ----
q = screens / "QuranScreen.kt"

# index screen: new pages
patch(q, "    var p61TasmeeArgs by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }\n",
      "    var p61TasmeeArgs by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }\n"
      "    var p62Similar by remember { mutableStateOf(false) }\n"
      "    var p62Meanings by remember { mutableStateOf(false) }\n")
patch(q, "            P61HifzScreen(\n                repo = repo,\n", "            P62HifzScreen(\n                repo = repo,\n")
patch(q, """                onClose = { p61Tasmee = false },
            )
        }
    }
""", """                onClose = { p61Tasmee = false },
            )
        }
    }
    if (p62Similar) androidx.compose.ui.window.Dialog(
        onDismissRequest = { p62Similar = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = P61Paper) { P62SimilarScreen(repo = repo, onClose = { p62Similar = false }) }
    }
    if (p62Meanings) androidx.compose.ui.window.Dialog(
        onDismissRequest = { p62Meanings = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = P61Paper) { P62MeaningsScreen(repo = repo, onClose = { p62Meanings = false }) }
    }
""")
patch(q, "            P57ListenHeader(onPickSurahs = { p55Picker = true }, onHifz = { p58Hifz = true },\n"
         "                onTasmee = { p61TasmeeArgs = null; p61Tasmee = true })",
      "            P57ListenHeader(onPickSurahs = { p55Picker = true }, onHifz = { p58Hifz = true },\n"
      "                onTasmee = { p61TasmeeArgs = null; p61Tasmee = true },\n"
      "                onSimilar = { p62Similar = true }, onMeanings = { p62Meanings = true })")

# reader: state
patch(q, "    var showMarks by remember { mutableStateOf(false) }\n    val p55Ctx = LocalContext.current\n",
      "    var showMarks by remember { mutableStateOf(false) }\n    var p62Settings by remember { mutableStateOf(false) }\n    val p55Ctx = LocalContext.current\n")
# reader: «علاماتي» dialog can also save / remove the current page
patch(q, """            confirmButton = { TextButton(onClick = { showMarks = false }) { Text("إغلاق") } },
""", """            dismissButton = {
                TextButton(onClick = { prefs.toggleSavedPage(currentPage); savedPages = prefs.savedPages() }) {
                    Text(if (currentPage in savedPages) "إزالة علامة صفحة $currentPage" else "حفظ علامة صفحة $currentPage")
                }
            },
            confirmButton = { TextButton(onClick = { showMarks = false }) { Text("إغلاق") } },
""")
# reader: no green app bar / bottom bars around the page any more
patch(q, "            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible) TopAppBar(",
      "            if (displayMode != QuranMode.MADINA_PAGES) TopAppBar(")
patch(q, "            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible) Surface(\n                color = paper,",
      "            if (displayMode != QuranMode.MADINA_PAGES) Surface(\n                color = paper,")
patch(q, "            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible)\n                P53BottomNav(onBack = onBack, onHome = onHome)",
      "            if (displayMode != QuranMode.MADINA_PAGES)\n                P53BottomNav(onBack = onBack, onHome = onHome)")
patch(q, "            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible) Surface(\n                modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 4.dp),",
      "            if (displayMode != QuranMode.MADINA_PAGES) Surface(\n                modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 4.dp),")
patch(q, "                P55ExitFullscreenFab { p41ControlsVisible = true }", "                Unit")
patch(q, "                .padding(horizontal = if (p57Full) 0.dp else 8.dp),",
      "                .padding(horizontal = if (displayMode == QuranMode.MADINA_PAGES) 0.dp else 8.dp),")

# reader: the icon bar replaces the old row of text buttons
cut(q,
    "            if (displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible) {\n                Row(\n                    Modifier.fillMaxWidth().padding(bottom = 4.dp),\n                    horizontalArrangement = Arrangement.SpaceEvenly,",
    "            if(displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible && (p60Tools || p44InkTool != P44InkTool.MOVE)) {",
    """            if (displayMode == QuranMode.MADINA_PAGES && p41ControlsVisible) {
                P62TopBar(
                    night = P53NightMode.enabled,
                    saved = currentPage in savedPages,
                    toolsOn = p60Tools || p44InkTool != P44InkTool.MOVE,
                    onBack = onBack,
                    onIndex = onOpenIndex,
                    onSearch = onSearch,
                    onMarks = { showMarks = true },
                    onTools = { p60Tools = !p60Tools; if (!p60Tools) p44InkTool = P44InkTool.MOVE },
                    onFull = { P55Reader.set(p55Ctx, true); p41ControlsVisible = false },
                    onSettings = { p62Settings = true },
                )
            }
            if (displayMode == QuranMode.MADINA_PAGES) {
                P55PlainGate(onReady = {
                    coloredMushaf = false
                    prefs.saveColoredMushaf(false)
                    printedZoom = 1f
                })
            }
            if (p62Settings) P62SettingsDialog(
                night = P53NightMode.enabled,
                onNight = { P53NightMode.toggle(p55Ctx) },
                colored = coloredMushaf,
                onColored = {
                    p62Settings = false
                    if (coloredMushaf && !P55PlainPages.ready) {
                        P55PlainPages.askVisible = true
                    } else {
                        coloredMushaf = !coloredMushaf
                        prefs.saveColoredMushaf(coloredMushaf)
                        printedZoom = 1f
                    }
                },
                zoom = printedZoom,
                onMinus = { printedZoom = (printedZoom - 0.25f).coerceIn(1f, 3.5f) },
                onPlus = { printedZoom = (printedZoom + 0.25f).coerceIn(1f, 3.5f) },
                onReset = { printedZoom = 1f },
                onFlow = { p62Settings = false; P61Flow.set(p55Ctx, true) },
                onFlex = {
                    p62Settings = false
                    if (target.surah != null) surahReadingMode = QuranMode.FLEX_TEXT else onModeChange(QuranMode.FLEX_TEXT)
                },
                onListen = { p62Settings = false; onListen(currentSurah) },
                onHome = { p62Settings = false; onHome() },
                onDismiss = { p62Settings = false },
            )

""")

# reader: player strip / card + seek bar under the page
patch(q, """                if (P52SurahAudio.isActive) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    P52MiniPlayer()
                }
                }
            } else {
                val targetAyahs = remember(target) {""", """                if (p57Full && P52SurahAudio.isActive) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    P52MiniPlayer()
                }
                if (p57Full) Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp)) {
                    P62ExitPill { P55Reader.set(p55Ctx, false); p41ControlsVisible = true }
                }
                }
                if (p41ControlsVisible) P62Bottom(
                    repo = repo,
                    pages = pages,
                    index = pagerState.currentPage,
                    night = P53NightMode.enabled,
                    assigned = target.assigned,
                    progress = progress,
                    atEnd = atEnd,
                    completed = assignmentCompleted,
                    onComplete = onCompleteAssignment,
                    singleAyahPlaying = isAudioPlaying,
                    onStopSingle = onStopAudio,
                    onPlayHere = { p62PlayFromPage(p55Ctx, repo, pages, currentPage, target.surah) },
                    onSeek = { i -> scope.launch { pagerState.scrollToPage(i) } },
                )
            } else {
                val targetAyahs = remember(target) {""")

# ------------------------------------------------------------------------------------ notices ----
notice = assets / "quran/CONTENT_NOTICE.txt"
notice.write_text(notice.read_text(encoding="utf-8") + """
P62: the coloured Mushaf pages are the default reader again; the continuous text reader is an option in the reader settings.
""", encoding="utf-8")
(root / "P62_NOTES_AR.txt").write_text("""P62 / VC84
1) المصحف الملون (صور الصفحات) هو الأساس من جديد: تقلب يمين وشمال والصفحة تملأ الشاشة.
   شريط أيقونات رفيع فوق (رجوع، فهرس، بحث، علاماتي، أدوات، تكبير، إعدادات)، وتحت: شريط تشغيل وشريط تنقل بين الصفحات.
   عند التلاوة يظهر كارت التشغيل العائم، والآية تتظلل على الصفحة والصفحة تتبع الصوت.
   «تكبير» يفتح الصفحة كاملة، و«↩ تصغير» يرجّع الشريطين. «أدوات» فيها القلم والتظليل والممحاة.
   القراءة المتصلة بالنص خيار داخل ⚙ الإعدادات.
2) «🔁 المتشابهات» و«📖 المفردات»: صفحتان مستقلتان بزرّين في فهرس المصحف.
3) وضع الحفظ: ترتيب جديد (المقطع فوق، الآيات في الوسط، أزرار التحكم مثبتة تحت).
4) إصلاحات: دائرة رقم الآية لا تنفرد بسطر، أزرار السرعة والتكرار متساوية العرض، تقليل تباعد الأسطر.
""", encoding="utf-8")
print("P62 applied")
