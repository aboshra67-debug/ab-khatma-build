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

# P102: expose roles and awaiting-distribution from both authenticated member models.
s = base.read_text(encoding="utf-8")
role_model = '                          val awaitingDistribution: Boolean = false)'
assert s.count(role_model) == 1, "P102 member model anchor mismatch"
s = s.replace(role_model, '                          val awaitingDistribution: Boolean = false,\\n                          val role: String = "member")', 1)
parser = 'm.optBoolean("awaiting_distribution", false))'
assert s.count(parser) == 2, "P102 directory role-parser anchor mismatch"
s = s.replace(parser, 'm.optBoolean("awaiting_distribution", false), m.optString("role", "member"))')
base.write_text(s, encoding="utf-8")

screen = root / "app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt"
u = screen.read_text(encoding="utf-8")
changes = [
    ('it.groupId == selectedGroupId && it.role == "leader" && it.status == "active"',
     'it.groupId == selectedGroupId && it.role in listOf("leader", "assistant") && it.status == "active"', 1),
    ('chosen != null && chosen.status == "active" && chosen.role != "leader"',
     'chosen != null && chosen.status == "active" && chosen.role !in listOf("leader", "assistant")', 1),
    ('groupTab = if (item.role == "leader") "leader" else "read"',
     'groupTab = if (item.role in listOf("leader", "assistant")) "leader" else "read"', 1),
    ('if (item.role == "leader") SecureV2Api.groupStatus(active.token, item.groupId)',
     'if (item.role in listOf("leader", "assistant")) SecureV2Api.groupStatus(active.token, item.groupId)', 2),
    ('if (item.role == "leader") {\\n                                        pending =',
     'if (item.role in listOf("leader", "assistant")) {\\n                                        pending =', 1),
    ('if (item.role == "leader" && groupTab == "leader" && item.status == "active")',
     'if (item.role in listOf("leader", "assistant") && groupTab == "leader" && item.status == "active")', 1),
    ('if (item.role == "leader" && item.status == "active") {',
     'if (item.role in listOf("leader", "assistant") && item.status == "active") {', 1),
    ('P29GroupFrame(leader = item.role == "leader")',
     'P29GroupFrame(leader = item.role in listOf("leader", "assistant"))', 1),
    ('if (chosen.role == "leader") {\\n                                P28LeaderHero(',
     'if (chosen.role in listOf("leader", "assistant")) {\\n                                P28LeaderHero(', 1),
    ('isLeader = chosen.role == "leader"',
     'isLeader = chosen.role in listOf("leader", "assistant")', 1),
    ('leader = selectedGroup.role == "leader"',
     'leader = selectedGroup.role in listOf("leader", "assistant")', 1),
]
for old, new, expected in changes:
    assert u.count(old) == expected, "Unsafe P102 UI anchor: " + old[:70]
    u = u.replace(old, new)
old_case = '''                                    "leader_members" -> P28LeaderMembers(
                                        liveGroup, pending, busy,
                                        token = active.token, groupId = item.groupId,
                                        onApprove = respond,
                                        onEdit = { section = "plans"; chatGroupId = null },
                                        onInvite = {
                                            liveGroup?.let { shareCode(item.groupName, it.inviteCode) }
                                        }
                                    )'''
new_case = '''                                    "leader_members" -> {
                                        P28LeaderMembers(
                                            liveGroup, pending, busy,
                                            token = active.token, groupId = item.groupId,
                                            onApprove = respond,
                                            onEdit = { section = "plans"; chatGroupId = null },
                                            onInvite = {
                                                liveGroup?.let { shareCode(item.groupName, it.inviteCode) }
                                            }
                                        )
                                        P102ManagementPanel(
                                            token = active.token,
                                            groupId = item.groupId,
                                            members = liveGroup?.members.orEmpty(),
                                            leader = item.role == "leader",
                                            onChanged = {
                                                runTask {
                                                    groupInfo = withContext(Dispatchers.IO) {
                                                        SecureV2Api.groupStatus(active.token, item.groupId)
                                                    }
                                                    pending = withContext(Dispatchers.IO) {
                                                        SecureV2Api.pending(active.token, item.groupId)
                                                    }
                                                }
                                            }
                                        )
                                    }'''
assert u.count(old_case) == 1, "P102 leader-members screen anchor mismatch"
u = u.replace(old_case, new_case, 1)
screen.write_text(u, encoding="utf-8")

print("P102 stage2 guarded UI and client patch applied; production release remains blocked")
