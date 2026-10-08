"""V0.16 P1 safe routing fix: V2 staging must never enter legacy unauthenticated registration.
Apply only to the verified V0.16 GitHub source during CI. Fail closed on changed anchors.
"""
from pathlib import Path

def replace_exact(path, before, after):
    p = Path(path)
    content = p.read_text(encoding="utf-8")
    assert content.count(before) == 1, f"Unexpected patch anchor in {path}: {before[:80]!r}"
    p.write_text(content.replace(before, after, 1), encoding="utf-8")

root = "app/src/main/java/com/ab/khatma/"
replace_exact(
    root + "ui/screens/AppRoot.kt",
    "var screen by remember { mutableStateOf<Screen>(if (vm.userId > 0) Screen.Home else Screen.Welcome) }",
    """var screen by remember { mutableStateOf<Screen>(
        if (BuildConfig.V2_PREVIEW_ENABLED) Screen.SecureV2
        else if (vm.userId > 0) Screen.Home else Screen.Welcome
    ) }"""
)
replace_exact(
    root + "ui/screens/AppRoot.kt",
    "Screen.SecureV2 -> SecureV2Screen(onBack = { screen = if (vm.userId > 0) Screen.Home else Screen.Welcome },",
    """Screen.SecureV2 -> SecureV2Screen(onBack = {
            screen = if (BuildConfig.V2_PREVIEW_ENABLED) Screen.SecureQuran(null)
            else if (vm.userId > 0) Screen.Home else Screen.Welcome
        },"""
)
replace_exact(
    root + "AppViewModel.kt",
    """        ReadingReminderScheduler.schedule(application)
        if (userId > 0) refreshToday()""",
    """        // The V2 staging backend disallows unauthenticated V1 synchronization.
        if (!BuildConfig.V2_PREVIEW_ENABLED) {
            ReadingReminderScheduler.schedule(application)
            if (userId > 0) refreshToday()
        }"""
)
replace_exact("app/build.gradle.kts", "versionCode = 16\n        versionName = \"0.16.0\"",
              "versionCode = 17\n        versionName = \"0.16.1\"")
assert "Screen.SecureV2" in Path(root + "ui/screens/AppRoot.kt").read_text()
assert "if (!BuildConfig.V2_PREVIEW_ENABLED)" in Path(root + "AppViewModel.kt").read_text()
print("PASS: Staging routes directly to authenticated V2; V1 startup sync disabled; version 0.16.1.")
