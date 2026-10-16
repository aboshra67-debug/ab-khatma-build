"""P67 / VC89 safe patch on top of P66 / VC88.

1. New reciter «ماهر المعيقلي — المصحف المجود» (continuous, verse-timed; the murattal Maher stays).
2. «منفردات»: a new interactive section (replaces the «مفردات» button). Built from the app's own Quran text.
3. Mushaf: snow-white page option (default ON, setting), full screen at normal size (no cropped text),
   previous / next moved off the text, compact top and bottom bars, bigger page.
4. Tapping an ayah of the Mushaf opens one bottom sheet (recite from here / only this ayah / play up to …,
   red bookmark, star, copy, share, similar verses, meanings).
Additive only. Each replacement must match exactly once.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"
screens = java / "ui/screens"
quran = java / "quran"
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


def add(name, dest):
    assert not dest.exists(), dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes((here / name).read_bytes())


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 88", "versionCode = 89")
patch(gradle, 'versionName = "0.19.61-p66-continuous-listen-one-player"', 'versionName = "0.19.62-p67-munfaradat-mushaf-sheet"')
add("P67Look.kt", screens / "P67Look.kt")
add("P67Sheet.kt", screens / "P67Sheet.kt")
add("P67Munfaradat.kt", screens / "P67Munfaradat.kt")

# ------------------------------------------------------------------------------ data («منفردات») ----
sys.path.insert(0, str(here))
import json
import p67_unique_gen

unique = p67_unique_gen.build(str(assets / "quran-uthmani.json"))
assert len(unique) > 3000, len(unique)
dest = assets / "p67_unique.json"
assert not dest.exists()
dest.write_text(json.dumps(unique, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

# ------------------------------------------------------------------------------ reciter ----
a = quran / "P52SurahAudio.kt"
patch(a, '        P52Reciter("maher", "ماهر المعيقلي", "", M + "maher-muaiqly/r1/", MUR, "MaherAlMuaiqly128kbps"),\n',
      '        P52Reciter("maher", "ماهر المعيقلي", "", M + "maher-muaiqly/r1/", MUR, "MaherAlMuaiqly128kbps"),\n'
      '        P52Reciter("maher_mujawwad", "ماهر المعيقلي", "المصحف المجود", M + "maher-muaiqly/r3/", MUJ, null),\n')
# a reciter without verse files can only play through the timed surah file, even for a single ayah
patch(a, "if (item < AYAH_BASE || queue.size < 2 || !P66Timing.supported(r)) return false",
      "if (item < AYAH_BASE || (queue.size < 2 && r.ayahFolder != null) || !P66Timing.supported(r)) return false")
patch(quran / "P66Timing.kt", '        "khalid_jalil" to 20,\n', '        "khalid_jalil" to 20,\n        "maher_mujawwad" to 133,\n')

# ------------------------------------------------------------------------------ white page ----
m = screens / "P41OfflineMushafPage.kt"
patch(m, "    Box(\n        Modifier.fillMaxSize()\n            .background(if (P53NightMode.enabled) P53NightMode.pageBackground else Color(0xFFFFF9E9))",
      "    P67Look.load(androidx.compose.ui.platform.LocalContext.current)\n"
      "    Box(\n        Modifier.fillMaxSize()\n            .background(if (P53NightMode.enabled) P53NightMode.pageBackground else P67Look.pageBackground)")
patch(m, "colorFilter = if (P53NightMode.enabled) P53NightMode.pageFilter else null,",
      "colorFilter = if (P53NightMode.enabled) P53NightMode.pageFilter else if (P67Look.white) P67Look.pageFilter else null,")

r = screens / "P62Reader.kt"
patch(r, '''                        Switch(checked = night, onCheckedChange = { onNight() })
                    }
''', '''                        Switch(checked = night, onCheckedChange = { onNight() })
                    }
                    val p67Ctx = androidx.compose.ui.platform.LocalContext.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⬜ خلفية بيضاء ثلجية", Modifier.weight(1f), color = P61Ink)
                        Switch(checked = P67Look.white, onCheckedChange = { P67Look.toggle(p67Ctx) })
                    }
''')

# ------------------------------------------------------------------------------ bottom navigation ----
c = screens / "P53Common.kt"
patch(c, "fun P53BottomNav(onBack: () -> Unit, onHome: () -> Unit, modifier: Modifier = Modifier) {",
      "fun P53BottomNav(onBack: () -> Unit, onHome: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {")
patch(c, "padding(horizontal = 14.dp, vertical = 6.dp),", "padding(horizontal = 14.dp, vertical = if (compact) 3.dp else 6.dp),")
patch(c, "modifier = Modifier.weight(1f).height(44.dp),", "modifier = Modifier.weight(1f).height(if (compact) 36.dp else 44.dp),", count=2)

# ------------------------------------------------------------------------------ reader bars ----
p = screens / "P63Reader.kt"
patch(p, "private val P63Bar = Color(0xFFF6F0DD)", "private val P63Bar = Color(0xFFF3F3F0)")
patch(p, "import androidx.compose.material.icons.rounded.Settings\n",
      "import androidx.compose.material.icons.rounded.Settings\nimport androidx.compose.material.icons.rounded.PlayArrow\nimport androidx.compose.material.icons.rounded.Stop\nimport androidx.compose.material3.IconButton\nimport androidx.compose.foundation.layout.PaddingValues\n")
patch(p, "        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))\n        Text(label, color = tint, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)",
      "        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))\n        Text(label, color = tint, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)")
patch(p, "            .padding(vertical = 4.dp),\n        horizontalAlignment = Alignment.CenterHorizontally,", "            .padding(vertical = 2.dp),\n        horizontalAlignment = Alignment.CenterHorizontally,")
patch(p, '''            info, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),''',
      '''            info, color = tint, fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 1.dp),''')
# the little player strip becomes part of the page row (play + reciter + previous + slider + next)
patch(p, '''        } else {
            P61AudioStrip(
                playing = singleAyahPlaying,
                night = night,
                onPlay = onPlayHere,
                onStop = onStopSingle,
                onPickReciter = { showReciters = true },
            )
        }
''', "        }\n")
patch(p, '''        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev, enabled = hasPrev) {
                Text("› السابق", color = if (hasPrev) tint else muted.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }''', '''        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!P52SurahAudio.isActive) {
                IconButton(onClick = if (singleAyahPlaying) onStopSingle else onPlayHere, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (singleAyahPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        if (singleAyahPlaying) "إيقاف" else "تشغيل من هنا", tint = tint,
                    )
                }
                TextButton(onClick = { showReciters = true }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
                    val nm = P52SurahAudio.savedReciter.name
                    Text((if (nm.length > 11) nm.take(11) + "…" else nm) + " ▾", color = tint, fontSize = 11.sp, maxLines = 1)
                }
            }
            TextButton(onClick = onPrev, enabled = hasPrev, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("›", color = if (hasPrev) tint else muted.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }''')
patch(p, '''            TextButton(onClick = onNext, enabled = hasNext) {
                Text("التالي ‹", color = if (hasNext) tint else muted.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }''', '''            TextButton(onClick = onNext, enabled = hasNext, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("‹", color = if (hasNext) tint else muted.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }''')

# ------------------------------------------------------------------------------ full screen controls ----
f = screens / "P65Full.kt"
patch(f, "import androidx.compose.foundation.layout.Column\n", "import androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.Row\n")
patch_between(f, "    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {\n",
              "/** The player that opens with the speaker button",
'''    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.matchParentSize()) {
            // previous / next sit in the bottom margin, never on top of the text
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = (bottomPad + 9).dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    onClick = onNext, enabled = hasNext, modifier = Modifier.size(46.dp), shape = CircleShape,
                    color = P65Green.copy(alpha = if (hasNext) 0.62f else 0.16f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("‹", color = Color.White, fontSize = 30.sp) }
                }
                Surface(
                    onClick = onPrev, enabled = hasPrev, modifier = Modifier.size(46.dp), shape = CircleShape,
                    color = P65Green.copy(alpha = if (hasPrev) 0.62f else 0.16f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("›", color = Color.White, fontSize = 30.sp) }
                }
            }
            Surface(
                onClick = onToggleZoom,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = (bottomPad + 12).dp).size(40.dp),
                shape = CircleShape,
                color = P65Green.copy(alpha = 0.72f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (zoomed) "↩" else "🔍", color = Color.White, fontSize = 18.sp)
                }
            }
            Surface(
                onClick = onTogglePlayer,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = bottomPad.dp).size(64.dp),
                shape = CircleShape,
                color = if (playerOpen || P52SurahAudio.isPlaying) P65Green.copy(alpha = 0.95f) else P65Green.copy(alpha = 0.78f),
                border = BorderStroke(2.dp, Color(0xFFDFBD78)),
                shadowElevation = 4.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (P52SurahAudio.isPlaying) "🔊" else "🔈", fontSize = 30.sp)
                }
            }
        }
    }
}

''')

# ------------------------------------------------------------------------------ Quran screen ----
q = screens / "QuranScreen.kt"
patch(q, "printedZoom = if (!p41ControlsVisible && displayMode == QuranMode.MADINA_PAGES) 1.25f else 1f",
      "printedZoom = 1f // P67: full screen keeps the normal size so no text is cropped")
patch(q, "val p57Bg = if (p57Full) (if (P53NightMode.enabled) P53NightMode.pageBackground else Color(0xFFFFF9E9)) else paperSoft",
      "val p57Bg = if (p57Full) (if (P53NightMode.enabled) P53NightMode.pageBackground else P67Look.pageBackground) else paperSoft")
patch(q, "            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible)\n                P53BottomNav(onBack = onBack, onHome = onHome)",
      "            if (displayMode != QuranMode.MADINA_PAGES || p41ControlsVisible)\n                P53BottomNav(onBack = onBack, onHome = onHome, compact = true)")
patch(q, "    var selectedAyah by remember { mutableStateOf<QuranAyah?>(null) }\n",
      "    var selectedAyah by remember { mutableStateOf<QuranAyah?>(null) }\n    var p67SheetAyah by remember { mutableStateOf<QuranAyah?>(null) }\n")
patch(q, "        selectedAyah?.let { aya ->\n            AyahActionsSheet(",
      '''        p67SheetAyah?.let { aya ->
            P67AyahSheet(
                aya = aya,
                repo = repo,
                bookmark = bookmark,
                onBookmark = {
                    prefs.saveBookmark(aya.surah, aya.ayah)
                    bookmark = aya.surah to aya.ayah
                },
                onMore = {
                    p67SheetAyah = null
                    explanationMode = null
                    selectedAyah = aya
                },
                onDismiss = { p67SheetAyah = null },
            )
        }
        selectedAyah?.let { aya ->
            AyahActionsSheet(''')
patch(q, '''                        repo.ayah(surah, ayah)?.let { verse ->
                            highlightedAyahKey = "$surah:$ayah"
                            playAyah(verse)
                        }''', '''                        repo.ayah(surah, ayah)?.let { verse ->
                            highlightedAyahKey = "$surah:$ayah"
                            prefs.saveLastReadAyah(surah, ayah)
                            p67SheetAyah = verse
                        }''')
patch(q, "                onOpenSurah = { surah ->\n                    target = ReadingTarget(\n                        key = \"surah_$surah\",\n                        title = \"سورة ${repo.surahName(surah)}\",\n                        pages = repo.surahPageRange(surah),\n                        surah = surah,\n                    )\n                },\n            )\n        } else {",
      "                onOpenSurah = { surah ->\n                    target = ReadingTarget(\n                        key = \"surah_$surah\",\n                        title = \"سورة ${repo.surahName(surah)}\",\n                        pages = repo.surahPageRange(surah),\n                        surah = surah,\n                    )\n                },\n                onOpenPage = { page ->\n                    target = ReadingTarget(key = \"saved_page_$page\", title = \"المنفردات — صفحة $page\",\n                        pages = 1..604, startPage = page)\n                },\n            )\n        } else {")
patch(q, "    onOpenJuz: (Int) -> Unit,\n    onOpenSurah: (Int) -> Unit,\n) {\n    var tab by remember { mutableIntStateOf(0) }",
      "    onOpenJuz: (Int) -> Unit,\n    onOpenSurah: (Int) -> Unit,\n    onOpenPage: (Int) -> Unit = {},\n) {\n    var tab by remember { mutableIntStateOf(0) }")
patch(q, "Surface(Modifier.fillMaxSize(), color = P61Paper) { P62MeaningsScreen(repo = repo, onClose = { p62Meanings = false }) }",
      "Surface(Modifier.fillMaxSize(), color = P61Paper) {\n            P67MunfaradatScreen(repo = repo, onClose = { p62Meanings = false }, onOpenPage = { page -> p62Meanings = false; onOpenPage(page) })\n        }")
patch(screens / "P64Index.kt", 'P64HeaderButton("📖 مفردات", Modifier.weight(1f), onMeanings)', 'P64HeaderButton("🔎 منفردات", Modifier.weight(1f), onMeanings)')
print("P67 applied")
