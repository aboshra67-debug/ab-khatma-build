"""P102 VC125 Phase 2 guarded Android patch applied *after* the verified P101 patch.
Only adds API methods and optional waiting status; does not alter signing or user data.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
base = root / "app/src/main/java/com/ab/khatma/secure/SecureV2Api.kt"
assert base.is_file(), "P101 SecureV2Api.kt missing"
s = base.read_text(encoding="utf-8")
anchor = "    // P101: V3 scheduling reuses session renewal, TLS and bounded timeouts."
assert s.count(anchor) == 1, "P101 network anchor mismatch; refusing unsafe patch"
methods = r'''    // P102: leadership and explicit reading duties share the P101 HTTPS/session path.
    // Stage 2 is gated: client methods are not enabled in the published UI until backend QA.
    fun p102Leadership(token: String, groupId: Long): JSONObject {
        require(groupId > 0)
        return JSONObject(request("/groups/$groupId/leadership", token))
    }

    fun p102SetDeputy(token: String, groupId: Long, membershipId: Long, appoint: Boolean): JSONObject {
        require(groupId > 0 && membershipId > 0)
        val action = if (appoint) "deputy" else "revoke-deputy"
        val confirmation = if (appoint) "APPOINT_DEPUTY" else "REVOKE_DEPUTY"
        val body = JSONObject().put("membership_id", membershipId).put("confirm", confirmation)
        return JSONObject(request("/groups/$groupId/leadership/$action", token, "POST", body))
    }

    fun p102SetReader(token: String, groupId: Long, membershipId: Long, enabled: Boolean): JSONObject {
        require(groupId > 0 && membershipId > 0)
        val confirmation = if (enabled) "ENABLE_READING" else "DISABLE_READING"
        val body = JSONObject().put("enabled", enabled).put("confirm", confirmation)
        return JSONObject(request("/groups/$groupId/members/$membershipId/reading-enrollment", token, "POST", body))
    }

    fun p102AssignToday(token: String, groupId: Long, membershipId: Long,
                        juzNumber: Int, khatmaNumber: Int = 1): JSONObject {
        require(groupId > 0 && membershipId > 0)
        require(juzNumber in 1..30 && khatmaNumber in 1..1000)
        val body = JSONObject().put("juz_number", juzNumber)
            .put("khatma_number", khatmaNumber).put("confirm", "ASSIGN_JUZ")
        return JSONObject(request("/groups/$groupId/members/$membershipId/assign-today", token, "POST", body))
    }

'''
s = s.replace(anchor, methods + anchor, 1)
model = """    data class MemberInfo(val id: Long, val name: String, val juz: Int?, val completed: Boolean,
                          val readingEligible: Boolean = true)"""
new_model = """    data class MemberInfo(val id: Long, val name: String, val juz: Int?, val completed: Boolean,
                          val readingEligible: Boolean = true,
                          val awaitingDistribution: Boolean = false)"""
assert s.count(model) == 1, "MemberInfo P101 signature mismatch"
s = s.replace(model, new_model, 1)
old = 'm.optBoolean("completed", false), m.optBoolean("reading_eligible", true))'
new = 'm.optBoolean("completed", false), m.optBoolean("reading_eligible", true),\n                    m.optBoolean("awaiting_distribution", false))'
assert s.count(old) == 2, "P101 bounded reading-directory parser differs; refusing patch"
s = s.replace(old, new)
base.write_text(s, encoding="utf-8")
gradle = root / "app/build.gradle.kts"
v = gradle.read_text(encoding="utf-8")
assert v.count("versionCode = 124") == 1
assert v.count('versionName = "0.19.95-p101-stage1-test"') == 1
v = v.replace("versionCode = 124", "versionCode = 125", 1)
v = v.replace('versionName = "0.19.95-p101-stage1-test"',
              'versionName = "0.19.96-p102-stage2-qa-only"', 1)
gradle.write_text(v, encoding="utf-8")
print("P102 stage2 guarded client methods applied; no data/signing changes; UI remains gated")
