"""P54 / VC76 safe patch on top of P53 / VC75.

1. Size: the coloured Mushaf pages (~112 MB) leave the APK and are downloaded once, on
   demand, with SHA-256 verification. Plain pages, verse touch and audio stay bundled.
   (The CI workflow moves the folder out of the build only; the Safe Source keeps it.)
2. «حذف حسابي نهائيًا» in My account (Google Play requirement), using the new server route.
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


gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 75", "versionCode = 76")
patch(gradle, 'versionName = "0.19.48-p53-profile-nav-reminders"',
      'versionName = "0.19.49-p54-small-apk-account-delete"')

dest = java / "ui/screens/P54ColoredPages.kt"
assert not dest.exists()
dest.write_bytes((here / "P54ColoredPages.kt").read_bytes())

app = java / "KhatmaApplication.kt"
patch(app, "        runCatching { com.ab.khatma.ui.screens.P53NightMode.load(this) }\n",
      "        runCatching { com.ab.khatma.ui.screens.P53NightMode.load(this) }\n"
      "        runCatching { com.ab.khatma.ui.screens.P54ColoredPages.init(this) }\n")

# ---- coloured pages: downloaded file first, then bundled assets, then plain page --------
page = java / "ui/screens/P41OfflineMushafPage.kt"
patch(page, '''                val bitmap = runCatching {
                    context.assets.open("$source/$name").use { BitmapFactory.decodeStream(it, null, opts) }
                }.getOrNull() ?: context.assets.open("quran_pages/$name").use {''',
      '''                val downloaded = if (colored) P54ColoredPages.pageFile(context, name) else null
                val bitmap = downloaded?.let { f ->
                    runCatching { f.inputStream().use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
                } ?: runCatching {
                    context.assets.open("$source/$name").use { BitmapFactory.decodeStream(it, null, opts) }
                }.getOrNull() ?: context.assets.open("quran_pages/$name").use {''')

quran = java / "ui/screens/QuranScreen.kt"
patch(quran, "    var coloredMushaf by remember(target.key) { mutableStateOf(prefs.coloredMushaf()) }",
      "    var coloredMushaf by remember(target.key) { mutableStateOf(prefs.coloredMushaf() && P54ColoredPages.ready) }")
patch(quran, '''                        onClick = {
                            coloredMushaf = !coloredMushaf
                            prefs.saveColoredMushaf(coloredMushaf)
                            printedZoom = 1f
                        },''', '''                        onClick = {
                            if (!coloredMushaf && !P54ColoredPages.ready) {
                                P54ColoredPages.askVisible = true
                            } else {
                                coloredMushaf = !coloredMushaf
                                prefs.saveColoredMushaf(coloredMushaf)
                                printedZoom = 1f
                            }
                        },''')
patch(quran, "                    if (displayMode == QuranMode.MADINA_PAGES) P53NightToggle(accent)",
      "                    if (displayMode == QuranMode.MADINA_PAGES) P53NightToggle(accent)\n"
      "                    P54ColoredGate(onReady = {\n"
      "                        coloredMushaf = true\n"
      "                        prefs.saveColoredMushaf(true)\n"
      "                        printedZoom = 1f\n"
      "                    })")

# ---- account deletion -------------------------------------------------------------------
api = java / "secure/SecureV2Api.kt"
patch(api, '''    fun checkSession(token: String): Boolean =''',
      '''    /** P54: permanent deletion (server erases name, login and personal content). */
    fun deleteAccount(token: String) {
        request("/auth/account/delete", token, "POST", JSONObject().put("confirm", "DELETE"))
    }

    fun checkSession(token: String): Boolean =''')

profile = java / "secure/P53ProfilePage.kt"
patch(profile, '''    onOpenNotifications: () -> Unit,
) {''', '''    onOpenNotifications: () -> Unit,
    onDeleteAccount: () -> Unit = {},
) {''')
patch(profile, '''    var showReciters by remember { mutableStateOf(false) }''',
      '''    var showReciters by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteWord by remember { mutableStateOf("") }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false; deleteWord = "" },
        title = { Text("حذف الحساب نهائيًا") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("هيتمسح اسمك وبيانات الدخول وربط Google ورسائلك ومنشوراتك، ومش هتقدر ترجع للحساب ده تاني.")
                Text("لو أنت قائد ختمة فيها أعضاء، لازم تخرّجهم أو تقفل الختمة الأول.", color = Color(0xFF746D62), fontSize = 13.sp)
                OutlinedTextField(value = deleteWord, onValueChange = { deleteWord = it.take(10) },
                    label = { Text("اكتب «حذف» للتأكيد") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = deleteWord.trim() == "حذف" && !busy,
                onClick = { confirmDelete = false; deleteWord = ""; onDeleteAccount() }) {
                Text("حذف نهائي", color = Color(0xFFB3261E))
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false; deleteWord = "" }) { Text("إلغاء") } },
    )''')
patch(profile, '''        ) { Text("تسجيل الخروج", color = Color(0xFFB3261E)) }
    }
}''', '''        ) { Text("تسجيل الخروج", color = Color(0xFFB3261E)) }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("حذف حسابي نهائيًا", color = Color(0xFF9A5A55), fontSize = 13.sp)
        }
    }
}''')

ui = java / "secure/SecureV2Screen.kt"
patch(ui, '''                            onOpenNotifications = { section = "notifications"; message = "" },''',
      '''                            onOpenNotifications = { section = "notifications"; message = "" },
                            onDeleteAccount = {
                                runTask {
                                    withContext(Dispatchers.IO) { SecureV2Api.deleteAccount(active.token) }
                                    SecureReminderWorker.cancel(context)
                                    store.clear()
                                    session = null
                                    groups = emptyList()
                                    groupsLoaded = false
                                    groupInfo = null
                                    pending = emptyList()
                                    chatGroupId = null
                                    chatMessages = emptyList()
                                    lastInvitation = null
                                    section = "home"
                                    message = "تم حذف حسابك نهائيًا"
                                }
                            },''')

assert len(list((root / "app/src/main/assets/quran_pages").glob("*.webp"))) == 604
assert not list(root.rglob("*.p12")) and not list(root.rglob("*.jks")) and not list(root.rglob("*.keystore"))
print("P54: coloured pages on demand (smaller APK), in-app account deletion, VC76")
