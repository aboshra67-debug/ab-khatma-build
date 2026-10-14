"""P58 / VC80 safe patch on top of P57 / VC79.

1. The Mushaf page follows the verse being recited: colour highlight on the verse and automatic page turn.
2. Memorisation mode (range, repeat each verse / the whole range, hide the text).
3. Tasbih counter, khatm dua page and a one-time dua dialog at the last page of the Mushaf.
4. Weekly reading report in «حسابي» (pages opened per day, local only).
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
patch(gradle, "versionCode = 79", "versionCode = 80")
patch(gradle, 'versionName = "0.19.52-p57-tap-listen-adhkar-cards"',
      'versionName = "0.19.53-p58-follow-hifz-tasbih"')

add("P58Extras.kt", screens / "P58Extras.kt")
add("P58Report.kt", java / "secure/P58Report.kt")

# -------------------------------------------------- page highlight follows the recitation ----
page = screens / "P41OfflineMushafPage.kt"
patch(page, "    onPlayVerse: (Int, Int) -> Unit = { _, _ -> },\n) {",
      "    onPlayVerse: (Int, Int) -> Unit = { _, _ -> },\n    playingKey: String? = null,\n) {")
patch(page, """    val selectedImage by produceState<ImageBitmap?>(initialValue = null, hitMap, selectedVerse) {
        value = withContext(Dispatchers.Default) { hitMap?.highlight(selectedVerse) }
    }""", """    // P58: the verse being recited is highlighted, so the eye can follow the reciter.
    val playingVerse: Pair<Int, Int>? = remember(playingKey) {
        val parts = playingKey?.split(":")
        val s = parts?.getOrNull(0)?.toIntOrNull()
        val a = parts?.getOrNull(1)?.toIntOrNull()
        if (parts != null && parts.size == 2 && s != null && a != null) s to a else null
    }
    val selectedImage by produceState<ImageBitmap?>(initialValue = null, hitMap, selectedVerse, playingVerse) {
        value = withContext(Dispatchers.Default) { hitMap?.highlight(playingVerse ?: selectedVerse) }
    }""")

quran = screens / "QuranScreen.kt"
patch(quran, "                            onPlayVerse = onPlayPrintedAyah,\n",
      "                            onPlayVerse = onPlayPrintedAyah,\n                            playingKey = playingKey,\n")
patch(quran, "    // P57: full screen opens at 1.25x so the text fills the width; leaving it returns to normal size.\n",
      """    // P58: the page follows the verse being recited.
    LaunchedEffect(playingKey) {
        val key = playingKey
        if (key != null && displayMode == QuranMode.MADINA_PAGES) {
            val parts = key.split(":")
            val ay = if (parts.size == 2) repo.ayah(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0) else null
            val idx = if (ay != null) pages.indexOf(ay.page) else -1
            if (idx >= 0 && idx != pagerState.currentPage) pagerState.animateScrollToPage(idx)
        }
    }
    LaunchedEffect(currentPage) { runCatching { com.ab.khatma.secure.P58Stats.recordPage(p55Ctx, currentPage) } }
    var p58Khatm by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        if (currentPage == 604 && pages.size > 1 && pagerState.currentPage == pages.lastIndex && displayMode == QuranMode.MADINA_PAGES) p58Khatm = true
    }
    if (p58Khatm) P58KhatmDialog { p58Khatm = false }
    // P57: full screen opens at 1.25x so the text fills the width; leaving it returns to normal size.
""")

# ---------------------------------------------------------------- hifz entry point ----
patch(quran, "    var p55Picker by remember { mutableStateOf(false) }\n",
      "    var p55Picker by remember { mutableStateOf(false) }\n    var p58Hifz by remember { mutableStateOf(false) }\n")
patch(quran, "    val lastPage = prefs.lastPage()\n    val lastJuz = repo.pageJuz(lastPage)",
      """    if (p58Hifz) androidx.compose.ui.window.Dialog(
        onDismissRequest = { p58Hifz = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF8F7F2)) { P58HifzScreen(repo) { p58Hifz = false } }
    }
    val lastPage = prefs.lastPage()
    val lastJuz = repo.pageJuz(lastPage)""")
patch(quran, "            P57ListenHeader { p55Picker = true }\n",
      "            P57ListenHeader(onPickSurahs = { p55Picker = true }, onHifz = { p58Hifz = true })\n")
rd = screens / "P57Reader.kt"
patch(rd, "internal fun P57ListenHeader(onPickSurahs: () -> Unit) {",
      "internal fun P57ListenHeader(onPickSurahs: () -> Unit, onHifz: () -> Unit = {}) {")
patch(rd, """                Text("تغيير ▾", color = Color(0xFFFFE6A9), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
""", """                Text("تغيير ▾", color = Color(0xFFFFE6A9), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(
            onClick = onHifz,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0B624C),
            border = BorderStroke(1.dp, Color(0xFFDFBD78).copy(alpha = 0.6f)),
        ) {
            Text("🧠 وضع الحفظ والتكرار", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
        }
""")

# ------------------------------------------------------------ tasbih + khatm dua ----
adhkar = screens / "InteractiveAdhkarScreen.kt"
patch(adhkar, "onRuqya: () -> Unit, onQuranDuas: () -> Unit = {}) {",
      "onRuqya: () -> Unit, onQuranDuas: () -> Unit = {}, onTasbih: () -> Unit = {}, onKhatmDua: () -> Unit = {}) {")
patch(adhkar, "                        item { P57QuranDuasCard(onQuranDuas) }\n",
      "                        item { P57QuranDuasCard(onQuranDuas) }\n                        item { P58ToolsRow(onTasbih, onKhatmDua) }\n")
ruq = screens / "P56Ruqya.kt"
patch(ruq, """        2 -> P57QuranDuasScreen(onBack = { page = 0 }, onHome = onBack)
        else -> P56AdhkarMain(onBack = onBack, onRuqya = { page = 1 }, onQuranDuas = { page = 2 })""",
      """        2 -> P57QuranDuasScreen(onBack = { page = 0 }, onHome = onBack)
        3 -> P58TasbihScreen(onBack = { page = 0 }, onHome = onBack)
        4 -> P58KhatmDuaScreen(onBack = { page = 0 }, onHome = onBack)
        else -> P56AdhkarMain(onBack = onBack, onRuqya = { page = 1 }, onQuranDuas = { page = 2 },
            onTasbih = { page = 3 }, onKhatmDua = { page = 4 })""")

# ------------------------------------------------------------------ weekly report ----
prof = java / "secure/P53ProfilePage.kt"
patch(prof, """            P53Stat("👥", "${active.map { it.groupId }.distinct().size}", "ختماتي", Modifier.weight(1f))
        }
""", """            P53Stat("👥", "${active.map { it.groupId }.distinct().size}", "ختماتي", Modifier.weight(1f))
        }
        P58WeeklyReport()
""")
print("P58 applied")
