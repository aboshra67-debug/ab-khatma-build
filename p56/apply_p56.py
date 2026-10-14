"""P56 / VC78 safe patch on top of P55 / VC77.

1. Surah picker remembers the selection, shows what is playing, lets you add / remove surahs while
   listening ("تحديث القائمة") and change the reciter from inside the picker.
2. Full screen: icon in the top bar, double tap on the page, remembered, zoom hint disappears by itself,
   tools row fits the screen without scrolling.
3. Adhkar: chapter audio (streamed from the data set's own link) and a Ruqyah section (Quran + sunnah
   with sources, listening with any reciter).
4. Khatmas: archive (this phone only), leave, and close (leader) through new server endpoints.
5. «حسابي» page redesigned.
Each replacement must match exactly once.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"


def patch(path, old, new, count=1):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    assert found == count, (str(path), found, old[:110])
    path.write_text(text.replace(old, new), encoding="utf-8")


def add(name, dest):
    assert not dest.exists(), dest
    dest.write_bytes((here / name).read_bytes())


# ---------------------------------------------------------------- version ----
gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 77", "versionCode = 78")
patch(gradle, 'versionName = "0.19.50-p55-verse-touch-reciters-fullscreen"',
      'versionName = "0.19.51-p56-ruqyah-archive-picker"')

# ------------------------------------------------- player: ayah items + queue update ----
audio = java / "quran/P52SurahAudio.kt"
patch(audio, "    fun playSample(context: Context, id: String) = play(context, listOf(1), sampleId = id)\n",
      '''    fun playSample(context: Context, id: String) = play(context, listOf(1), sampleId = id)

    // P56: a queue item is either a surah number (1..114) or an encoded single verse.
    const val AYAH_BASE = 1_000_000
    fun ayahCode(surah: Int, ayah: Int) = AYAH_BASE + surah * 1000 + ayah
    val customLabels = HashMap<Int, String>()

    fun itemTitle(item: Int): String = customLabels[item]
        ?: if (item >= AYAH_BASE) "الآية ${(item - AYAH_BASE) % 1000} من سورة ${surahName((item - AYAH_BASE) / 1000)}"
        else "سورة " + surahName(item)

    private fun urlFor(r: P52Reciter, item: Int): String {
        if (item < AYAH_BASE) return r.surahUrl(item)
        val s = (item - AYAH_BASE) / 1000
        val a = (item - AYAH_BASE) % 1000
        return r.ayahUrl(s, a) ?: P52Reciters.byId(P52Reciters.DEFAULT_ID).ayahUrl(s, a) ?: r.surahUrl(s)
    }

    /** Replaces the queue without interrupting the current surah when it is still in the new list. */
    fun updateQueue(context: Context, surahs: List<Int>) {
        init(context)
        val clean = surahs.filter { it in 1..114 || it >= AYAH_BASE }
        if (clean.isEmpty()) return
        val current = currentSurah
        val at = if (current == null) -1 else clean.indexOf(current)
        if (at < 0) { play(context, clean); return }
        queue = clean
        index = at
        publishMetadata()
        publishState()
        refreshNotification()
    }
''')
patch(audio, "        val clean = surahs.filter { it in 1..114 }\n",
      "        val clean = surahs.filter { it in 1..114 || it >= AYAH_BASE }\n")
patch(audio, "            p.setDataSource(r.surahUrl(surah))\n", "            p.setDataSource(urlFor(r, surah))\n")
patch(audio, '.putString(MediaMetadata.METADATA_KEY_TITLE, "سورة " + surahName(surah))',
      ".putString(MediaMetadata.METADATA_KEY_TITLE, itemTitle(surah))")
patch(audio, 'val title = if (surah != null) "سورة ${surahName(surah)}" else "الاستماع إلى القرآن"',
      'val title = if (surah != null) itemTitle(surah) else "الاستماع إلى القرآن"')
patch(audio, "تعذّر تشغيل سورة ${surahName(surah)} بصوت", "تعذّر تشغيل ${itemTitle(surah)} بصوت")

listen_ui = java / "ui/screens/P52SurahListenUi.kt"
patch(listen_ui, 'Text("سورة " + P52SurahAudio.surahName(surah), color = Color.White,',
      "Text(P52SurahAudio.itemTitle(surah), color = Color.White,")

# ------------------------------------------------------------------ surah picker ----
p55 = java / "ui/screens/P55ListenUi.kt"
text = p55.read_text(encoding="utf-8")
marker = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\ninternal fun P55SurahPicker("
assert text.count(marker) == 1
text = text[:text.index(marker)]
for old, new in [("private data class P55Preset(", "internal data class P55Preset("),
                 ("private val P55Presets = ", "internal val P55Presets = "),
                 ("private object P55SavedLists {", "internal object P55SavedLists {")]:
    assert text.count(old) == 1, old
    text = text.replace(old, new)
p55.write_text(text, encoding="utf-8")
add("P56SurahPicker.kt", java / "ui/screens/P56SurahPicker.kt")

# ------------------------------------------------------------------ full screen / tools ----
quran = java / "ui/screens/QuranScreen.kt"
patch(quran, "import androidx.compose.material.icons.rounded.ZoomOut\n",
      "import androidx.compose.material.icons.rounded.ZoomOut\nimport androidx.compose.material.icons.rounded.Fullscreen\n")
patch(quran, "                actions = {\n                    MushafSearchButton(onClick = onSearch)\n",
      '''                actions = {
                    if (displayMode == QuranMode.MADINA_PAGES) IconButton(onClick = {
                        P55Reader.set(p55Ctx, true)
                        p41ControlsVisible = false
                    }) { Icon(Icons.Rounded.Fullscreen, "ملء الشاشة", tint = Color.White) }
                    MushafSearchButton(onClick = onSearch)
''')
patch(quran, "                            onToggleControls = { p41ControlsVisible = !p41ControlsVisible },",
      "                            onToggleControls = {\n"
      "                                val visible = !p41ControlsVisible\n"
      "                                p41ControlsVisible = visible\n"
      "                                P55Reader.set(p55Ctx, !visible)\n"
      "                            },")
patch(quran, '''                    TextButton(onClick = { onListen(currentSurah) }) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null)
                        Text("استمع للسورة", fontSize = 12.sp)
                    }
                    if (isAudioPlaying) TextButton(onClick = onStopAudio) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Text("إيقاف", fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        prefs.toggleSavedPage(currentPage)
                        savedPages = prefs.savedPages()
                    }) {
                        Icon(if (currentPage in savedPages) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = null)
                        Text(if (currentPage in savedPages) "محفوظة" else "حفظ الصفحة", fontSize = 12.sp)
                    }
                    TextButton(onClick = { showMarks = true }) { Text("علاماتي", fontSize = 12.sp) }
                    P53NightToggle(accent)
                    P55FullscreenButton(accent) { p41ControlsVisible = false }
''', '''                    TextButton(onClick = { onListen(currentSurah) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("استمع", fontSize = 12.sp)
                    }
                    if (isAudioPlaying) TextButton(onClick = onStopAudio,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("إيقاف", fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        prefs.toggleSavedPage(currentPage)
                        savedPages = prefs.savedPages()
                    }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Icon(if (currentPage in savedPages) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (currentPage in savedPages) "محفوظة" else "حفظ", fontSize = 12.sp)
                    }
                    TextButton(onClick = { showMarks = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("علاماتي", fontSize = 12.sp) }
                    P53NightToggle(accent)
''')
patch(quran, "                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),\n"
             "                    horizontalArrangement = Arrangement.spacedBy(2.dp),",
      "                    Modifier.fillMaxWidth().padding(bottom = 4.dp),\n"
      "                    horizontalArrangement = Arrangement.SpaceEvenly,")

page = java / "ui/screens/P41OfflineMushafPage.kt"
patch(page, '''                onDoubleTap = {
                    val next = if (zoom > 1.01f) 1f else 1.7f
                    onZoomChange(next)
                    if (next <= 1.01f) { x = 0f; y = 0f }
                },''', '''                onDoubleTap = {
                    // Zoomed in: back to normal. Normal size: toggle full screen.
                    if (zoom > 1.01f) { onZoomChange(1f); x = 0f; y = 0f } else onToggleControls()
                },''')
patch(page, "    var x by remember(pageNumber) { mutableFloatStateOf(0f) }\n",
      "    var x by remember(pageNumber) { mutableFloatStateOf(0f) }\n"
      "    var zoomHintVisible by remember(pageNumber) { mutableStateOf(false) }\n"
      "    LaunchedEffect(zoom) {\n"
      "        if (zoom > 1.01f) { zoomHintVisible = true; kotlinx.coroutines.delay(2200); zoomHintVisible = false }\n"
      "        else zoomHintVisible = false\n"
      "    }\n")
patch(page, "if (page != null && zoom > 1.01f && inkTool == P44InkTool.MOVE && selectedVerse == null && hint == null) {",
      "if (page != null && zoomHintVisible && zoom > 1.01f && inkTool == P44InkTool.MOVE && selectedVerse == null && hint == null) {")

# ------------------------------------------------------------ adhkar + ruqyah ----
add("P56Ruqya.kt", java / "ui/screens/P56Ruqya.kt")
adhkar = java / "ui/screens/InteractiveAdhkarScreen.kt"
patch(adhkar, "fun InteractiveAdhkarScreen(onBack: () -> Unit) {\n    val context = LocalContext.current\n",
      "internal fun P56AdhkarMain(onBack: () -> Unit, onRuqya: () -> Unit) {\n"
      "    val context = LocalContext.current\n"
      "    DisposableEffect(Unit) { onDispose { P56AdhkarAudio.stop() } }\n")
patch(adhkar, "    BackHandler { navigateBack() }\n",
      "    BackHandler { navigateBack() }\n    LaunchedEffect(readerLabel) { P56AdhkarAudio.stop() }\n")
patch(adhkar, '                        item { Text("أذكار اليوم", color = dhInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }',
      '                        item { P56RuqyaCard(onRuqya) }\n'
      '                        item { Text("أذكار اليوم", color = dhInk, fontWeight = FontWeight.Bold, fontSize = 18.sp) }')
patch(adhkar, "                    val completed = done(rec)\n                    item {\n",
      '''                    val completed = done(rec)
                    val audioChapter = currentChapter
                    if (audioChapter != null && P56AdhkarAudio.hasAudio(context, audioChapter.title)) item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { P56AdhkarAudio.toggle(context, audioChapter.title) }) {
                                Text(
                                    if (P56AdhkarAudio.playingTitle == audioChapter.title)
                                        (if (P56AdhkarAudio.loading) "⏳ جارٍ التحميل…" else "⏹ إيقاف الصوت")
                                    else "🔊 استمع للباب"
                                )
                            }
                            if (P56AdhkarAudio.failed) Text("  الصوت غير متاح الآن — تأكد من الإنترنت", color = dhGray, fontSize = 12.sp)
                        }
                    }
                    item {
''')

# --------------------------------------------------------- khatmas: archive / leave / close ----
secure = java / "secure"
add("P56GroupManage.kt", secure / "P56GroupManage.kt")
api = secure / "SecureV2Api.kt"
patch(api, "    fun checkSession(token: String): Boolean =",
      '''    /** P56: a member leaves a khatma (server keeps the history, the khatma just disappears for them). */
    fun leaveGroup(token: String, groupId: Long) {
        try { request("/groups/$groupId/leave", token, "POST", JSONObject()) }
        catch (e: SessionExpiredException) { throw e }
        catch (e: IllegalStateException) {
            val m = e.message.orEmpty()
            if (m.startsWith("لا يمكن تنفيذ")) throw IllegalStateException("أنت قائد الختمة وفيها أعضاء. احذف الختمة بدل الخروج منها.", e)
            if (m.startsWith("الخدمة غير متاحة")) throw IllegalStateException("الخروج من الختمة هيشتغل بعد تحديث السيرفر.", e)
            throw e
        }
    }

    /** P56: the leader closes the khatma for everyone. */
    fun closeGroup(token: String, groupId: Long) {
        try { request("/groups/$groupId/close", token, "POST", JSONObject().put("confirm", "CLOSE")) }
        catch (e: SessionExpiredException) { throw e }
        catch (e: IllegalStateException) {
            if (e.message.orEmpty().startsWith("الخدمة غير متاحة"))
                throw IllegalStateException("حذف الختمة هيشتغل بعد تحديث السيرفر.", e)
            throw e
        }
    }

    fun checkSession(token: String): Boolean =''')

tile = secure / "P25GroupScreens.kt"
patch(tile, "fun P25GroupTile(item: SecureV2Api.TodayItem, onOpen: () -> Unit) {",
      "fun P25GroupTile(item: SecureV2Api.TodayItem, menu: (@Composable () -> Unit)? = null, onOpen: () -> Unit) {")
patch(tile, '''            Text("‹", color = p25Green, fontSize = 31.sp)
        }
    }
}

@Composable
fun P25CreateGroup(''', '''            menu?.invoke()
            Text("‹", color = p25Green, fontSize = 31.sp)
        }
    }
}

@Composable
fun P25CreateGroup(''')

screen = secure / "SecureV2Screen.kt"
patch(screen, "    var confirmTodayAssignment by remember { mutableStateOf<SecureV2Api.TodayItem?>(null) }\n",
      '''    var confirmTodayAssignment by remember { mutableStateOf<SecureV2Api.TodayItem?>(null) }
    var archivedIds by remember(session?.userId) { mutableStateOf(P56Archive.load(context, session?.userId ?: 0L)) }
    var p56Manage by remember { mutableStateOf<Pair<SecureV2Api.TodayItem, Boolean>?>(null) }
''')
patch(screen, "confirmTodayAssignment?.let { requested ->\n",
      '''P56ManageDialog(p56Manage, busy, onDismiss = { p56Manage = null }) { item, close ->
    runTask {
        val current = session ?: return@runTask
        try {
            withContext(Dispatchers.IO) {
                if (close) SecureV2Api.closeGroup(current.token, item.groupId)
                else SecureV2Api.leaveGroup(current.token, item.groupId)
            }
        } catch (e: Exception) { p56Manage = null; throw e }
        p56Manage = null
        archivedIds = archivedIds - item.groupId
        P56Archive.save(context, current.userId, archivedIds)
        groups = withContext(Dispatchers.IO) { SecureV2Api.today(current.token) }
        groupsLoaded = true
        message = if (close) "تم حذف الختمة «${item.groupName}»" else "خرجت من ختمة «${item.groupName}»"
    }
}
confirmTodayAssignment?.let { requested ->
''')
patch(screen, '                                "allwards" -> "كل ختماتي اليوم"\n',
      '                                "allwards" -> "كل ختماتي اليوم"\n                                "archive" -> "الختمات المؤرشفة"\n')
patch(screen, '                                "notifications" -> "profile"\n',
      '                                "notifications", "archive" -> "profile"\n')
patch(screen, "                            groupCount = groups.size,\n",
      "                            groupCount = groups.count { it.groupId !in archivedIds },\n")
patch(screen, "                                    assignments = groups,\n                                    maxPreview = 4,",
      "                                    assignments = groups.filter { it.groupId !in archivedIds },\n                                    maxPreview = 4,")
patch(screen, "                        P52CompactWards(assignments = groups, busy = busy,",
      "                        P52CompactWards(assignments = groups.filter { it.groupId !in archivedIds }, busy = busy,")
patch(screen, '                    } else if (section == "khatmas") {\n                        P25GroupsHub(',
      '''                    } else if (section == "archive") {
                        P56ArchivePage(groups.filter { it.groupId in archivedIds }) { id ->
                            archivedIds = archivedIds - id
                            P56Archive.save(context, active.userId, archivedIds)
                            message = "تم استرجاع الختمة"
                        }
                    } else if (section == "khatmas") {
                        P25GroupsHub(''')
patch(screen, '(section == "khatmas" && it.groupName.contains(groupQuery.trim(), ignoreCase = true)) ||',
      '(section == "khatmas" && it.groupId !in archivedIds && it.groupName.contains(groupQuery.trim(), ignoreCase = true)) ||')
patch(screen, "                        P25GroupTile(item) {\n                            selectedGroupId = item.groupId",
      '''                        P25GroupTile(item, menu = {
                            P56GroupMenu(item,
                                onArchive = {
                                    archivedIds = archivedIds + item.groupId
                                    P56Archive.save(context, active.userId, archivedIds)
                                    message = "تمت أرشفة «${item.groupName}». هتلاقيها في حسابي ← الختمات المؤرشفة"
                                },
                                onLeaveOrClose = { p56Manage = item to (item.role == "leader" && item.status == "active") })
                        }) {
                            selectedGroupId = item.groupId''')
patch(screen, '''                            onOpenNotifications = { section = "notifications"; message = "" },
                            onDeleteAccount = {''', '''                            onOpenNotifications = { section = "notifications"; message = "" },
                            archivedCount = archivedIds.size,
                            onOpenArchive = { section = "archive"; message = "" },
                            onDeleteAccount = {''')

# ------------------------------------------------------------------ profile page ----
profile = secure / "P53ProfilePage.kt"
old_profile = profile.read_text(encoding="utf-8")
assert "onDeleteAccount" in old_profile and "object P53LocalStats" in old_profile
profile.write_bytes((here / "P53ProfilePage.kt").read_bytes())

assert len(list((root / "app/src/main/assets/quran_pages_colored").glob("*.webp"))) == 604
assert len(list((root / "app/src/main/assets/quran_verse_hitmaps").glob("*.json"))) == 604
assert not list(root.rglob("*.p12")) and not list(root.rglob("*.jks")) and not list(root.rglob("*.keystore"))
print("P56: picker memory, full screen, ruqyah + adhkar audio, archive/leave/close, profile page, VC78")
