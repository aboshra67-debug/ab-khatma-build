"""P50 additive Android integration over the successfully built P49 VC71 base."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
base = root / "app/src/main/java/com/ab/khatma/secure"
api = base / "SecureV2Api.kt"
ui = base / "SecureV2Screen.kt"
gradle = root / "app/build.gradle.kts"


def safe_replace(path: Path, before: str, after: str):
    source = path.read_text(encoding="utf-8")
    assert source.count(before) == 1, (path.name, source.count(before), before[:72])
    path.write_text(source.replace(before, after, 1), encoding="utf-8")


safe_replace(gradle, "versionCode = 71", "versionCode = 72")
safe_replace(gradle, 'versionName = "0.19.44-p49-thirty-day-schedule"',
             'versionName = "0.19.45-p50-leader-approved-plan"')
safe_replace(api,
    'method: String = "GET", data: JSONObject? = null): String {',
    'method: String = "GET", data: JSONObject? = null, apiVersion: String = "v2"): String {')
safe_replace(api,
    'URL(root + "/v2" + path)',
    'URL(root + "/" + apiVersion + path)')
safe_replace(api,
    '    fun register(name: String, password: String): SecureSessionStore.Session {',
    '''    // P50: shares the existing HTTPS/timeouts/401 rules; the path is always /v4.
    fun p50Request(token: String, path: String, method: String = "GET",
                   data: JSONObject? = null): JSONObject {
        require(path.startsWith("/") && !path.contains(".."))
        return JSONObject(request(path, token, method, data, "v4"))
    }
    fun register(name: String, password: String): SecureSessionStore.Session {''')
safe_replace(ui,
    '''P48ReaderSchedule(token = active.token, groupId = selectedGroupId,
                                isLeader = section == "leaderSchedule" && chosen.role == "leader",
                                groupName = chosen.groupName, todayAssignment = chosen, onRead = { onOpenQuran(it) },
                                onChanged = { reload() },
                                onOpenPagePlans = { section = "plans"; chatGroupId = null })''',
    '''P50LeaderSchedule(token = active.token, groupId = selectedGroupId,
                                isLeader = chosen.role == "leader",
                                groupName = chosen.groupName, onRead = { onOpenQuran(it) },
                                onChanged = { reload() })''')
target = base / "P50LeaderPlans.kt"
assert not target.exists()
target.write_bytes((Path(__file__).parent / "P50LeaderPlans.kt").read_bytes())
assert (root / "app/src/main/res/raw/adhan_madinah.ogg").is_file()
assert (root / "app/src/main/res/raw/adhan_shalqar.ogg").is_file()
assert len(list((root / "app/src/main/assets/quran_pages").glob("*.webp"))) == 604
assert not list(root.rglob("*.jks")) and not list(root.rglob("*.p12"))
print("P50 VC72 safe patch applied: verified opt-in API, UI, adhan, Quran, no signing keys")
