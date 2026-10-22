"""P103 VC126 patch applied to the verified P102 VC125 COMPLETE-SAFE-SOURCE baseline.
UI/API additions only: no signing, no data migration. Every anchor is asserted exactly once."""
import re
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
overlay = Path(sys.argv[2]).resolve()
j = root / "app/src/main/java/com/ab/khatma"


def edit(path: Path, old: str, new: str, count: int = 1):
    s = path.read_text(encoding="utf-8")
    assert s.count(old) == count, "anchor mismatch in %s: %r (found %d)" % (path.name, old[:70], s.count(old))
    path.write_text(s.replace(old, new), encoding="utf-8")


# 1. API ---------------------------------------------------------------------------------
api = j / "secure/SecureV2Api.kt"
edit(api, "    data class TodayItem(", r'''    // P103: Google link status, undo, group schedule, late readers and reminders.
    fun googleStatus(token: String): Boolean =
        JSONObject(request("/auth/google/status", token)).optBoolean("linked", false)

    fun uncompleteSchedule(token: String, row: ScheduleRow) {
        val path = if (row.isTransfer) "/schedule/transfers/" + row.id + "/uncomplete"
                   else "/assignments/" + row.id + "/uncomplete"
        request(path, token, "POST", JSONObject())
    }

    data class P103Duty(val date: String, val juz: Int, val status: String)
    data class P103Person(val name: String, val eligible: Boolean, val duties: List<P103Duty>)
    data class P103Overview(val start: String, val end: String, val people: List<P103Person>)
    fun p103ScheduleOverview(token: String, groupId: Long, days: Int = 7): P103Overview {
        require(groupId > 0 && days in 1..14)
        val o = JSONObject(request("/groups/$groupId/schedule-overview?days=$days", token))
        val members = o.getJSONArray("members")
        return P103Overview(o.getString("start"), o.getString("end"), (0 until members.length()).map { i ->
            val m = members.getJSONObject(i)
            val d = m.getJSONArray("duties")
            P103Person(m.getString("name"), m.optBoolean("reading_eligible", true), (0 until d.length()).map { k ->
                val x = d.getJSONObject(k)
                P103Duty(x.getString("date"), x.getInt("juz"), x.getString("status"))
            })
        })
    }

    data class P103Late(val membershipId: Long, val name: String, val openToday: Boolean,
                        val overdueCount: Int, val daysLateMax: Int, val nudgedToday: Boolean)
    fun p103LateReaders(token: String, groupId: Long): List<P103Late> {
        require(groupId > 0)
        val a = JSONObject(request("/groups/$groupId/late-readers", token)).getJSONArray("readers")
        return (0 until a.length()).map { i ->
            val r = a.getJSONObject(i)
            P103Late(r.getLong("membership_id"), r.getString("name"), r.optBoolean("open_today"),
                r.optInt("overdue_count"), r.optInt("days_late_max"), r.optBoolean("nudged_today"))
        }
    }
    fun p103Nudge(token: String, groupId: Long, membershipId: Long): String {
        require(groupId > 0 && membershipId > 0)
        val o = JSONObject(request("/groups/$groupId/members/$membershipId/nudge", token, "POST", JSONObject()))
        return if (o.optBoolean("sent")) "تم إرسال التذكير" else "تم تذكيره اليوم بالفعل"
    }
    fun p103NudgeAll(token: String, groupId: Long): String {
        require(groupId > 0)
        val o = JSONObject(request("/groups/$groupId/nudge-late", token, "POST", JSONObject()))
        return "تم تذكير " + o.optInt("sent") + " • سبق تذكير " + o.optInt("already_nudged_today")
    }

    data class TodayItem(''')

# 2. Google email helper ----------------------------------------------------------------
edit(j / "secure/KhatmaGoogleSignIn.kt", "    fun signOut(context: Context) {", '''    /** Email of the signed-in Google identity on this phone, or null (shown only to its owner). */
    fun currentEmail(context: Context): String? =
        if (!isConfigured) null else runCatching {
            FirebaseAuth.getInstance(firebaseApp(context)).currentUser?.email?.takeIf { it.isNotBlank() }
        }.getOrNull()

    fun signOut(context: Context) {''')

# 3. Profile card -----------------------------------------------------------------------
prof = j / "secure/P53ProfilePage.kt"
edit(prof, "    googleConfigured: Boolean,\n", "    googleConfigured: Boolean,\n    googleLinked: Boolean = false,\n    googleEmail: String? = null,\n")
edit(prof, 'Text("اربطه عشان تدخل بضغطة من غير كلمة مرور، ومجموعاتك زي ما هي.",',
     'Text(if (googleLinked) (if (googleEmail != null) "مربوط ✓ — $googleEmail" else "مربوط ✓ — بياناتك ومجموعاتك محفوظة على حساب Google")\n                        else "اربط حسابك بـ Google عشان بياناتك ومجموعاتك تفضل معاك لو غيّرت الموبايل.",')
edit(prof, "FilledTonalButton(onClick = onLinkGoogle, enabled = !busy && googleConfigured,",
     "FilledTonalButton(onClick = onLinkGoogle, enabled = !busy && googleConfigured && !googleLinked,")
edit(prof, '{ Text("اربط الآن") }', '{ Text(if (googleLinked) "مربوط ✓" else "اربط الآن") }')

scr = j / "secure/SecureV2Screen.kt"
edit(scr, "                        P53ProfilePage(\n", '''                        val p103Linked = remember(active.userId) { mutableStateOf(false) }
                        LaunchedEffect(active.userId, busy) {
                            runCatching {
                                p103Linked.value = withContext(Dispatchers.IO) { SecureV2Api.googleStatus(active.token) }
                            }
                        }
                        P53ProfilePage(
                            googleLinked = p103Linked.value,
                            googleEmail = KhatmaGoogleSignIn.currentEmail(context),
''')

# 4. Guest entry --------------------------------------------------------------------------
calm = j / "secure/KhatmaCalmScreens.kt"
edit(calm, "    onGoogleSignIn: () -> Unit,\n    onSubmit: () -> Unit\n",
     "    onGoogleSignIn: () -> Unit,\n    onSubmit: () -> Unit,\n    onGuest: () -> Unit = {}\n")
edit(calm, "                if (!googleReady) Text(", '''                OutlinedButton(onClick = onGuest, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, edge)) {
                    Text("دخول كزائر (من غير إيميل)", color = ink)
                }
                if (!googleReady) Text(''')
SUBMIT = "                        onSubmit = {\n                            runTask {\n                                val account = withContext(Dispatchers.IO) {\n                                    if (createAccount)"
edit(scr, SUBMIT, '''                        onGuest = {
                            runTask {
                                val bytes = ByteArray(24)
                                java.security.SecureRandom().nextBytes(bytes)
                                val generated = android.util.Base64.encodeToString(
                                    bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                                val account = withContext(Dispatchers.IO) {
                                    SecureV2Api.register("زائر", generated)
                                }
                                store.save(account)
                                SecureReminderWorker.schedule(context)
                                P93FirstRunStore.begin(context, account.userId)
                                com.ab.khatma.guide.P85Guide.markRestored(context, account.userId)
                                session = account
                                password = ""
                                section = "profile"
                                message = "دخلت كزائر. اربط حسابك بـ Google من هنا عشان بياناتك تفضل معاك لو غيّرت الموبايل."
                            }
                        },
                        onSubmit = {
                            runTask {
                                val account = withContext(Dispatchers.IO) {
                                    if (createAccount)''')

# 5. Undo button + group schedule in the reader schedule ------------------------------------
p48 = j / "secure/P48ReaderSchedule.kt"
edit(p48, "           if(!isLeader&&r.canComplete)Row(", '''           if(!isLeader&&r.status=="completed")TextButton(onClick={save{withContext(Dispatchers.IO){
             SecureV2Api.uncompleteSchedule(token,r)}}},enabled=!busy){
             Text("تراجع عن (تمت القراءة) — خلال 24 ساعة",fontSize=11.sp)
           }
           if(!isLeader&&r.canComplete)Row(''')
edit(p48, "   Text(groupName,color=p48Green)\n", "   Text(groupName,color=p48Green)\n   P103GroupScheduleCard(token,groupId)\n")

# 6. Late readers in the leader panel -------------------------------------------------------
panel = j / "secure/P102ManagementPanel.kt"
edit(panel, "        if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall)\n",
     "        if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall)\n        P103LateReadersCard(token, groupId)\n")

# 7. reading_nudge push -----------------------------------------------------------------------
push = j / "secure/P79Push.kt"
edit(push, '        if (data["kind"] != "private_chat") return\n', '''        if (data["kind"] == "reading_nudge") { displayNudge(context, data); return }
        if (data["kind"] != "private_chat") return
''')
edit(push, "    fun display(context: Context, data: Map<String, String>) {", '''    private const val NUDGE_CHANNEL = "ab_khatma_reading_nudge"

    /** Reminder from a group leader/deputy: no personal data, shown only to the intended account. */
    private fun displayNudge(context: Context, data: Map<String, String>) {
        val loggedIn = SecureSessionStore(context).load() ?: return
        if (data["recipient_user_id"]?.toLongOrNull() != loggedIn.userId) return
        val group = data["group_id"]?.toLongOrNull()?.takeIf { it > 0 } ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(NUDGE_CHANNEL, "تذكير الورد", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "تذكير من قائد المجموعة بوردك" })
        }
        val name = data["group_name"].orEmpty().filter { it.code >= 32 }.take(80)
            .ifBlank { "الختمة" }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 7000 + (group % 1000).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, NUDGE_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("تذكير بوردك 🌿")
            .setContentText("قائد «" + name + "» بيفكّرك بوردك. ربنا يتقبل منك")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(7000 + (group % 1000).toInt(), notification)
    }

    fun display(context: Context, data: Map<String, String>) {''')

# 8. Version --------------------------------------------------------------------------------
g = root / "app/build.gradle.kts"
edit(g, "versionCode = 125", "versionCode = 126")
edit(g, 'versionName = "0.19.96-p102-stage2-qa-only"', 'versionName = "0.19.97-p103-qa-only"')

# 9. New file -----------------------------------------------------------------------------
(j / "secure/P103Extras.kt").write_text((overlay / "P103Extras.kt").read_text(encoding="utf-8"), encoding="utf-8")
print("P103 patch applied")
