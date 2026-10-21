"""P101 stage 1: verified, minimal P100/VC123 source patch (no secrets, no data edits)."""
from pathlib import Path
import hashlib
import sys

root = Path(sys.argv[1]).resolve()
base = "app/src/main/java/com/ab/khatma/secure/"
replacements = {
base+"SecureV2Api.kt": [
('    class SessionExpiredException : IllegalStateException("انتهت جلسة الدخول. سجل الدخول مرة أخرى بنفس الحساب؛ مجموعاتك محفوظة.")',
 '    class SessionExpiredException : IllegalStateException("انتهت جلسة الدخول. سجل الدخول مرة أخرى بنفس الحساب؛ مجموعاتك محفوظة.")\n    class PermissionDeniedException(message: String) : IllegalStateException(message)'),
('                    403 -> "ليس لديك صلاحية لإجراء العملية"',
 '''                    403 -> when (detail) {
                        "Only an active reading member can be assigned", "Not a reading member", "Choose an active reader of this group" ->
                            "القارئ المختار ليس مشاركًا نشطًا في القراءة. فعّل مشاركته أولًا أو اختر قارئًا آخر."
                        "Not an active group member" -> "عضويتك في هذه الختمة غير مفعّلة. تحقق من الحساب أو قبول العضوية."
                        "Only group leader can manage plans", "Only this group's leader can manage its schedule" ->
                            "إدارة هذه الختمة تتطلب صلاحية القائد. تأكد من دخولك بحساب القائد الصحيح."
                        "Leader permission required" -> "هذه العملية تحتاج صلاحية قائد أو نائب معتمد للختمة."
                        else -> "ليس لديك صلاحية لإجراء هذه العملية. تحقق من حسابك ودورك في الختمة."
                    }'''),
('                    409 -> "لا يمكن تنفيذ العملية في الحالة الحالية"',
 '''                    409 -> when (detail) {
                        "Only an active manual khatma can use reader rotation" -> "تعديل توزيع القارئ متاح للختمة اليدوية النشطة فقط."
                        "Overlapping pages are assigned to another member or completed task" -> "الصفحات متداخلة مع تكليف قارئ آخر أو ورد مكتمل. راجع الجدول قبل التأكيد."
                        "These pages have already been read; past completion is preserved" -> "هذه الصفحات أُنجزت من قبل. لا يمكن تغيير الإنجاز السابق."
                        else -> "لا يمكن تنفيذ العملية في الحالة الحالية"
                    }'''),
('                throw IllegalStateException(reason)\n            }\n            return response',
 '                if (code == 403) throw PermissionDeniedException(reason)\n                throw IllegalStateException(reason)\n            }\n            return response'),
('    // P50: shares the existing HTTPS/timeouts/401 rules; the path is always /v4.',
 '''    // P101: V3 scheduling reuses session renewal, TLS and bounded timeouts.
    fun v3Request(token: String, path: String, method: String = "GET", data: JSONObject? = null): String {
        require(path.startsWith("/") && !path.contains(".."))
        return request(path, token, method, data, "v3")
    }

    // P50: shares the existing HTTPS/timeouts/401 rules; the path is always /v4.'''),
('                    m.optBoolean("completed", false))\n            })\n    }\n    /** VC91:',
 '                    m.optBoolean("completed", false), m.optBoolean("reading_eligible", true))\n            })\n    }\n    /** VC91:'),
('            JSONObject(request("/groups/$groupId/members-directory", token))',
 '''            JSONObject(try {
                request("/groups/$groupId/reading-directory", token)
            } catch (missing: IllegalStateException) {
                // Old deployed API remains supported until separately approved rollout.
                if (missing.message == "الخدمة غير متاحة حاليًا")
                    request("/groups/$groupId/members-directory", token)
                else throw missing
            })'''),
('                m.optBoolean("completed", false))\n        })\n    }\n\n    data class PrivateMessageInfo',
 '                m.optBoolean("completed", false), m.optBoolean("reading_eligible", true))\n        })\n    }\n\n    data class PrivateMessageInfo'),
('    data class MemberInfo(val id: Long, val name: String, val juz: Int?, val completed: Boolean)',
 '    data class MemberInfo(val id: Long, val name: String, val juz: Int?, val completed: Boolean,\n                          val readingEligible: Boolean = true)'),
],
base+"PagePlanner.kt": [
('''    fun call(token: String, path: String, method: String = "GET", data: JSONObject? = null): String {
        val url = BuildConfig.V2_API_BASE_URL.trimEnd('/') + "/v3" + path
        check(BuildConfig.V2_PREVIEW_ENABLED && url.startsWith("https://")) {
            "السيرفر الآمن غير مُعد"
        }
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000; c.readTimeout = 12_000
            c.instanceFollowRedirects = false
            c.requestMethod = method
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (data != null) {
                c.doOutput = true
                c.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            if (status !in 200..299) {
                val text = when (status) {
                    401 -> "جلسة الدخول انتهت"
                    403 -> "ليس لديك صلاحية"
                    409 -> "تكليف الصفحات يتداخل أو الخطة غير مناسبة"
                    422 -> "تأكد من التاريخ والمدى المحدد"
                    else -> "تعذر تنفيذ الطلب ($status)"
                }
                error(text)
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }''',
 '''    fun call(token: String, path: String, method: String = "GET", data: JSONObject? = null): String {
        // P101: share V2's authenticated renewal; never mask a legitimate leader session.
        return SecureV2Api.v3Request(token, path, method, data)
    }'''),
('SecureV2Api.groupStatus(token, groupId).members',
 'SecureV2Api.membersDirectory(token, groupId, "").members'),
],
base+"P32KhatmaVisualScreen.kt":[
('SecureV2Api.groupStatus(token, groupId).members',
 'SecureV2Api.membersDirectory(token, groupId, "").members'),
('                                people.forEach { person ->',
 '                                people.filter { it.readingEligible }.forEach { person ->'),
('                    val sorted = people.sortedBy { it.id }',
 '                    val sorted = people.filter { it.readingEligible }.sortedBy { it.id }'),
],
base+"P31RotationPanel.kt":[
('                    people.filter { it.name.contains(memberSearch.trim(), ignoreCase = true) }',
 '                    people.filter { it.readingEligible && it.name.contains(memberSearch.trim(), ignoreCase = true) }'),
],
base+"SecureV2Screen.kt":[
('if (groupTab == "members" && item.status == "active" && item.role != "leader")',
 'if (groupTab == "members" && item.status == "active")'),
],
"app/build.gradle.kts":[
("versionCode = 123","versionCode = 124"),
('versionName = "0.19.94-p100-name-only-safe"',
 'versionName = "0.19.95-p101-stage1-test"')
]
}

baseline_sha256 = {
base+"SecureV2Api.kt":"674b9d576a3fffedec795526f72cda475d8190be6e6a7509435ffe410a9b3151",
base+"PagePlanner.kt":"0ab4bc94892d00b6ffaa5e6812f2150c78429720db010a9880aadb4e5bb1205b",
base+"P32KhatmaVisualScreen.kt":"36b1ca406230237eedd85015a075420ce2b6005b40afc5bc28445c8389098c6b",
base+"P31RotationPanel.kt":"b8e6372cf84b7000c51bc17c79a025aa84481ad6a4a6ff713f10220a9bd7f244",
base+"SecureV2Screen.kt":"c529dcf74fa6aef0a16b1c95c83d21bf78ea2db4a94d5b50ba2263c81e5e2d59",
"app/build.gradle.kts":"d6b2fc14add6c94dd67684e4aed32b4a711ac90fe8e7a0f4f6056e1b401b6c76",
}
for rel, pairlist in replacements.items():
    dest = root / rel
    before = dest.read_bytes()
    assert hashlib.sha256(before).hexdigest() == baseline_sha256[rel], "P100 source mismatch: " + rel
    value = before.decode("utf-8")
    for old, new in pairlist:
        assert value.count(old) == 1, "P101 patch anchor missing/nonunique: " + rel
        value = value.replace(old, new, 1)
    dest.write_text(value, encoding="utf-8")
    print("PASS", rel)
print("P101 phase-1 guarded source patch applied; no original files deleted")
