"""P65 / VC87 safe patch on top of P64 / VC86.

1. Full-screen page: clear «السابق / التالي» buttons, a big speaker button that opens / hides the player (reciter, speed,
   controls), and a top line with surah • juz • hizb (and the ۞ quarter sign when the page has one) • page.
2. The reader title line also shows the ۞ quarter sign.
3. Adhkar card: the counter and the actions sit together under a shorter card, so everything fits one screen.
4. Adhkar chapter voice: only the real reciters (Mishary Alafasy first) for morning / evening; no other recordings.
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
patch(gradle, "versionCode = 86", "versionCode = 87")
patch(gradle, 'versionName = "0.19.59-p64-prefetch-tasmee-index"', 'versionName = "0.19.60-p65-full-controls-adhkar-card"')
add("P65Full.kt", screens / "P65Full.kt")

# ------------------------------------------------------------------ 1. full-screen page ----
q = screens / "QuranScreen.kt"
patch(q, "    var p41ControlsVisible by remember(target.key) { mutableStateOf(!P55Reader.full) }\n",
      "    var p41ControlsVisible by remember(target.key) { mutableStateOf(!P55Reader.full) }\n    var p65Player by remember { mutableStateOf(false) }\n")
patch(q, """                P57PageArrows(
                    visible = p57Full && p44InkTool == P44InkTool.MOVE,
                    hasPrev = pagerState.currentPage > 0,
                    hasNext = pagerState.currentPage < pages.lastIndex,
                    zoomed = printedZoom > 1.01f,
                    onPrev = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    onToggleZoom = { printedZoom = if (printedZoom > 1.01f) 1f else 1.25f },
                    bottomPad = if (P52SurahAudio.isActive) 92 else 18,
                )
                if (p57Full && P52SurahAudio.isActive) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    P52MiniPlayer()
                }
                if (p57Full) Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp)) {
                    P62ExitPill { P55Reader.set(p55Ctx, false); p41ControlsVisible = true }
                }
""", """                P65FullControls(
                    visible = p57Full && p44InkTool == P44InkTool.MOVE,
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
                    bottomPad = if (p65Player) (if (P52SurahAudio.isActive) 270 else 210) else 18,
                )
                if (p57Full && p65Player) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    P65FullPlayer(onPlayHere = { p62PlayFromPage(p55Ctx, repo, pages, currentPage, target.surah) })
                }
                if (p57Full) Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    P62ExitPill { P55Reader.set(p55Ctx, false); p41ControlsVisible = true; p65Player = false }
                    P65FullInfo(
                        info = P65Info.line(p55Ctx, repo, repo.surahName(currentSurah), currentJuz, currentPage),
                        night = P53NightMode.enabled,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
""")
patch(q, 'info = "سورة ${repo.surahName(currentSurah)} • الجزء ${p61Digits(currentJuz)} • الحزب ${p61Digits(P63Hizb.ofPage(p55Ctx, repo, currentPage))} • صفحة ${p61Digits(currentPage)}",',
      "info = P65Info.line(p55Ctx, repo, repo.surahName(currentSurah), currentJuz, currentPage),")
patch(screens / "P63Reader.kt", "            maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),",
      "            maxLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),")

# ------------------------------------------------------------------------ 3. adhkar card ----
ad = screens / "P57Adhkar.kt"
patch(ad, "import androidx.compose.foundation.rememberScrollState\n",
      "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.verticalScroll\n")
patch(ad, "                .padding(horizontal = 16.dp, vertical = 22.dp),\n", "                .padding(horizontal = 14.dp, vertical = 14.dp),\n")
patch(ad, """                Text(text, color = inkColor, fontSize = 24.sp, lineHeight = 44.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
""", """                Text(text, color = inkColor, fontSize = 22.sp, lineHeight = 38.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
""")
src = ad.read_text(encoding="utf-8")
start_marker = "        if (source.isNotBlank()) TextButton(onClick = { showSource = !showSource }) {\n"
end_marker = "        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n            Text(\"الانتقال التلقائي للذكر التالي\""
assert src.count(start_marker) == 1 and src.count(end_marker) == 1
a = src.index(start_marker)
b = src.index(end_marker)
assert a < b
new_block = """        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFavourite) {
                Icon(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "المفضلة",
                    tint = if (favourite) Color(0xFFFFB3C1) else Color.White)
            }
            IconButton(onClick = {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("ذكر", text + "\\n" + source))
            }) { Icon(Icons.Default.ContentCopy, "نسخ", tint = Color.White) }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                CircularProgressIndicator(
                    progress = { if (required > 0) (completed.toFloat() / required).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxSize(),
                    color = if (finished) Color(0xFF7CE0A3) else Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 6.dp,
                )
                Box(
                    Modifier.size(78.dp).clip(CircleShape)
                        .background(if (finished) Color(0xFF2E9E5B) else Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(if (finished) "✓" else "$completed / $required", color = Color.White,
                            fontSize = if (finished) 36.sp else 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text + "\\n" + source)
                }, "مشاركة الذكر"))
            }) { Icon(Icons.Default.Share, "مشاركة", tint = Color.White) }
            if (source.isNotBlank()) IconButton(onClick = { showSource = !showSource }) {
                Text("ⓘ", color = Color.White.copy(alpha = 0.95f), fontSize = 22.sp)
            } else Box(Modifier.size(48.dp))
        }
        Text(
            when {
                finished -> "اكتمل الذكر ✓"
                required > 1 -> "اضغط في أي مكان للعدّ"
                else -> "اضغط في أي مكان للإتمام"
            },
            color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp,
        )
        if (finished) TextButton(onClick = onReset) { Text("إعادة التكرار", color = Color.White.copy(alpha = 0.85f)) }
"""
ad.write_text(src[:a] + new_block + src[b:], encoding="utf-8")

inter = screens / "InteractiveAdhkarScreen.kt"
patch(inter, "                        Column(Modifier.fillMaxWidth().padding(19.dp)) {\n",
      "                        Column(Modifier.fillMaxWidth().padding(if (active != null) 12.dp else 19.dp)) {\n")
patch(inter, "                            Text(when {\n                                active != null -> \"اقرأ الذكر",
      "                            if (active == null) Text(when {\n                                active != null -> \"اقرأ الذكر")

# ------------------------------------------------------------------------ 4. adhkar voices ----
patch(ad, "    if (!P56AdhkarAudio.hasAudio(context, title) && !p63IsMorningEvening(title)) return\n",
      "    if (!p63IsMorningEvening(title)) return\n")
patch(ad, "                P56AdhkarAudio.hasAudio(context, title) -> {\n",
      "                P56AdhkarAudio.hasAudio(context, title) && !p63IsMorningEvening(title) -> {\n")
patch(screens / "P63Adhkar.kt", "                        voiceTracks.forEachIndexed { i, t ->\n",
      "                        voiceTracks.indices.sortedBy { if (voiceTracks[it].title.contains(\"العفاسي\")) 0 else 1 }.forEach { i ->\n                            val t = voiceTracks[i]\n")
