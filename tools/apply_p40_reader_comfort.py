from pathlib import Path
root = Path.cwd()

def patch(rel, subs):
    file = root / rel
    body = file.read_text(encoding="utf-8")
    for old, new in subs:
        n = body.count(old)
        if n != 1:
            raise AssertionError((rel, old[:90], n))
        body = body.replace(old, new, 1)
    file.write_text(body, encoding="utf-8")
    print("PATCH", rel)

patch("app/src/main/java/com/ab/khatma/ui/screens/QuranScreen.kt", [
    ("import androidx.compose.ui.text.font.FontWeight", "import androidx.compose.ui.text.font.FontFamily\nimport androidx.compose.ui.text.font.FontWeight"),
    ("""        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(horizontal = 16.dp, vertical = 10.dp),""",
     """        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(horizontal = 12.dp, vertical = 9.dp),"""),
    ("""                    textAlign = TextAlign.Center,
                    fontSize = (fontSize * 0.92f).sp,
                    lineHeight = (fontSize * 1.58f).sp,""",
     """                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Serif,
                    fontSize = (fontSize * 0.92f).sp,
                    lineHeight = (fontSize * 1.36f).sp,"""),
    ("""                Spacer(Modifier.height(12.dp))
            }

            ClickableText(""",
     """                Spacer(Modifier.height(8.dp))
            }

            ClickableText("""),
    ("""                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.62f).sp,
                    textAlign = TextAlign.Justify,
                    color = ink,""",
     """                    // P40: use native Naskh-friendly serif shaping, not wide
                    // justification that stretches Arabic words on small screens.
                    fontFamily = FontFamily.Serif,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.34f).sp,
                    textAlign = TextAlign.Start,
                    color = ink,"""),
    ("""                    fontSize = 26.sp,
                    lineHeight = 43.sp,
                    textAlign = TextAlign.Right,""",
     """                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Right,""")
])
patch("app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt", [
    ("""    var groupsLoaded by remember { mutableStateOf(false) }
""",
     """    var groupsLoaded by remember { mutableStateOf(false) }
    // P40: a transient auto-refresh failure must not repeatedly interrupt reading.
    var initialGroupLoadWarningShown by remember(session?.token) { mutableStateOf(false) }
"""),
    ("""            .onSuccess {
                if (session?.token == current.token) {
                    groups = it
                    groupsLoaded = true
                }
            }
            .onFailure { message = "تعذر تحديث المجموعات. احتفظنا بآخر قائمة؛ اضغط تحديث." }
""",
     """            .onSuccess {
                if (session?.token == current.token) {
                    groups = it
                    groupsLoaded = true
                    initialGroupLoadWarningShown = false
                    if (message == "تعذر تحديث المجموعات. اضغط تحديث للمحاولة مرة أخرى.") message = ""
                }
            }
            .onFailure {
                // Keep the already-loaded group data visible. A silent background
                // refresh failure is not an error in the offline Qur'an reader.
                if (!groupsLoaded && !initialGroupLoadWarningShown) {
                    initialGroupLoadWarningShown = true
                    message = "تعذر تحديث المجموعات. اضغط تحديث للمحاولة مرة أخرى."
                }
            }
""")
])
patch("app/build.gradle.kts", [
    ("""        versionCode = 60
        versionName = "0.19.36-p39-reader-today-confirm" """.rstrip(),
     """        versionCode = 61
        versionName = "0.19.37-p40-reader-comfort-network" """.rstrip()),
    ("""        release {
            buildConfigField("String", "API_BASE_URL",""",
     """        release {
            // Preserve the installed preview identity for a non-destructive upgrade.
            // A different production package would be a new app with separate data.
            applicationIdSuffix = ".preview"
            buildConfigField("String", "API_BASE_URL",""")
])
patch("app/build.gradle.kts", [
    ('    implementation("androidx.activity:activity-compose:1.12.4")',
     '    implementation("androidx.activity:activity-compose:1.12.4")\n    implementation("androidx.fragment:fragment-ktx:1.8.9")')
])
(root / "P40_READER_COMFORT_NETWORK_PLAY_NOTES_AR.txt").write_text(
    "AB Khatma P40 VC61\n- Quran presentation: serif, compact line heights, no stretched justification.\n"
    "- Background group refresh errors: no repeated toast if cached data exists.\n"
    "- Quran source, Firebase, groups, users, data, signatures unchanged.\n"
    "- Release identity com.ab.khatma.preview retained. Signed AAB needed for Play.\n",
    encoding="utf-8")
