#!/usr/bin/env python3
"""P93/VC117: SHA-guarded, additive first-run settings patch from exact P92."""
import hashlib
import pathlib
import shutil
import sys

root = pathlib.Path(sys.argv[1]).resolve()
here = pathlib.Path(__file__).resolve().parent
def load(path, expected):
    p = root/path
    raw = p.read_bytes()
    got = hashlib.sha256(raw).hexdigest()
    if got != expected:
        raise RuntimeError(f"Safe patch aborted: unexpected baseline {path}: {got}")
    return p, raw.decode("utf-8")
def rep(s, before, after, path):
    n = s.count(before)
    if n != 1:
        raise RuntimeError(f"Safe patch aborted: anchor count {n}, file {path}")
    return s.replace(before, after, 1)

g="app/build.gradle.kts"
p,s=load(g,"95e47026bb8550ada83570f9d338d0ed665405fe1f933d813a86411f536b5019")
s=rep(s,"versionCode = 116","versionCode = 117",g)
s=rep(s,'versionName = "0.19.87-p92-offline-replay-integrity-safe"', 'versionName = "0.19.88-p93-first-run-settings-safe"',g)
p.write_text(s,encoding="utf-8")

f="app/src/main/java/com/ab/khatma/secure/P53ProfilePage.kt"
p,s=load(f,"04b7d55406f81c3dba756e87045642ff84bc96638cd9931357c03e78c774cb7f")
s=rep(s,"    onOpenNotifications: () -> Unit,\n    onDeleteAccount: () -> Unit = {},",
      "    onOpenNotifications: () -> Unit,\n    onOpenPrayerSettings: () -> Unit = {},\n    onDeleteAccount: () -> Unit = {},",f)
s=rep(s,"    var showReciters by remember { mutableStateOf(false) }\n",
"""    var showReciters by remember { mutableStateOf(false) }
    var showFirstRun by remember(userId) { mutableStateOf(P93FirstRunStore.pending(context, userId)) }
    var firstRunStep by remember(userId) { mutableIntStateOf(P93FirstRunStore.step(context, userId)) }
    fun finishFirstRun() { P93FirstRunStore.finish(context, userId); showFirstRun = false }
""",f)
s=rep(s,"    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {\n        // Identity card",
"""    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showFirstRun) P93FirstRunCard(
            userId = userId, step = firstRunStep,
            onPrevious = { firstRunStep = (firstRunStep - 1).coerceAtLeast(0); P93FirstRunStore.saveStep(context, userId, firstRunStep) },
            onNext = {
                if (firstRunStep == 4) finishFirstRun()
                else { firstRunStep++; P93FirstRunStore.saveStep(context, userId, firstRunStep) }
            },
            onSkip = { finishFirstRun() },
            onOpenReciter = { showReciters = true },
            onOpenNotifications = onOpenNotifications,
            onOpenPrayer = onOpenPrayerSettings,
            onOpenGuide = { finishFirstRun(); com.ab.khatma.guide.P85Guide.openMenu() },
        )
        // Identity card""",f)
s=rep(s,'            P53SettingRow("🔔", "التنبيهات", "الورد، الأذكار، الصلاة، الدعاء، الكهف", onOpenNotifications)\n',
"""            P53SettingRow("🔔", "التنبيهات", "الورد، الأذكار، الصلاة، الدعاء، الكهف", onOpenNotifications)
            P53SettingRow("📖", "شرح التطبيق والجولة التفاعلية", "تعرّف على الأقسام خطوة بخطوة") {
                com.ab.khatma.guide.P85Guide.openMenu()
            }
""",f)
s=rep(s,'            P53SettingRow("❔", "شرح التطبيق", "دليل تفاعلي لكل الأقسام") { com.ab.khatma.guide.P85Guide.openMenu() }\n','',f)
p.write_text(s,encoding="utf-8")

f="app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt"
p,s=load(f,"e0c45fa5928cda1fd14bfe2b4ed7907d2048f335101927a7c5ade0ef187ec84f")
s=rep(s,'    var section by remember { mutableStateOf("home") }\n',
"""    var section by remember { mutableStateOf("home") }
    // Resume an interrupted setup; existing users are never enrolled automatically.
    LaunchedEffect(session?.userId) {
        val current = session
        if (current != null && p79Target == null && P93FirstRunStore.pending(context, current.userId) && section == "home") {
            section = "profile"
        }
    }
""",f)
s=rep(s,'''                                com.ab.khatma.guide.P85Guide.signedIn(context, account.userId)
                                session = account
                                password = ""
                                section = "home"
                                message = if (createAccount)''',
'''                                if (createAccount) {
                                    P93FirstRunStore.begin(context, account.userId)
                                    // The step-by-step settings replace the old immediate home tour.
                                    com.ab.khatma.guide.P85Guide.markRestored(context, account.userId)
                                } else com.ab.khatma.guide.P85Guide.signedIn(context, account.userId)
                                session = account
                                password = ""
                                section = if (createAccount) "profile" else "home"
                                message = if (createAccount)''',f)
s=rep(s,'                            onOpenNotifications = { section = "notifications"; message = "" },\n',
'''                            onOpenNotifications = { section = "notifications"; message = "" },
                            onOpenPrayerSettings = { section = "prayer"; message = "" },
''',f)
p.write_text(s,encoding="utf-8")

source=here/"overlay/app/src/main/java/com/ab/khatma/secure/P93FirstRunSettings.kt"
assert source.is_file()
dst=root/"app/src/main/java/com/ab/khatma/secure/P93FirstRunSettings.kt"
if dst.exists(): raise RuntimeError("P93 wizard already exists; refusing to overwrite")
dst.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(source,dst)
test_source=here/"overlay/app/src/androidTest/java/com/ab/khatma/guide/P93FirstRunDeviceTest.kt"
test_target=root/"app/src/androidTest/java/com/ab/khatma/guide/P93FirstRunDeviceTest.kt"
assert test_source.is_file() and not test_target.exists()
test_target.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(test_source,test_target)
assert "versionCode = 117" in (root/g).read_text()
print("P93 safe patch applied without removing existing settings or modifying Firebase/server.")
