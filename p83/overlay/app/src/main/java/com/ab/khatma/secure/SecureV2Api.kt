package com.ab.khatma.secure

import com.ab.khatma.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Preview-only authenticated client. Does not call or modify legacy endpoints. */
object SecureV2Api {
    // A typed 401 allows the UI to distinguish an expired login from an offline server.
    class SessionExpiredException : IllegalStateException("انتهت جلسة الدخول. سجل الدخول مرة أخرى بنفس الحساب؛ مجموعاتك محفوظة.")
    /** Only transport/gateway failures are safe to retry for read-only requests. */
    class TransientNetworkException(message: String, cause: Throwable) : IllegalStateException(message, cause)
    private fun request(path: String, token: String? = null, method: String = "GET", data: JSONObject? = null, apiVersion: String = "v2"): String {
        if (token == null) return rawRequest(path, method = method, data = data, apiVersion = apiVersion)
        val active = P83Sessions.tokenFor(token)
        val result = try {
            rawRequest(path, active, method, data, apiVersion)
        } catch (_: SessionExpiredException) {
            // Authentication failed before the route body ran: one renewal, one retry.
            val renewed = P83Sessions.renew(active)
            rawRequest(path, renewed, method, data, apiVersion)
        }
        P83Sessions.tokenFor(token) // Discard an in-flight result after logout/account switch.
        return result
    }

    private fun rawRequest(path: String, token: String? = null, method: String = "GET", data: JSONObject? = null,
                           apiVersion: String = "v2", credential401: Boolean = false): String {
        val root = BuildConfig.V2_API_BASE_URL.trimEnd('/')
        require(BuildConfig.V2_PREVIEW_ENABLED && root.startsWith("https://")) {
            "الخادم التجريبي الآمن غير مُعد بعد"
        }
        val connection = URL(root + "/" + apiVersion + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false // prevent redirecting Authorization to a different host
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (data != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("detail") }.getOrNull()
                // Do not return raw server bodies or bearer token to logs/user.
                val reason = when (code) {
                    401 -> "انتهت الجلسة أو بيانات الدخول غير صحيحة"
                    403 -> "ليس لديك صلاحية لإجراء العملية"
                    404 -> "الخدمة غير متاحة على خادم التجربة"
                    409 -> "لا يمكن تنفيذ العملية في الحالة الحالية"
                    else -> detail?.take(160)?.takeIf { it.isNotBlank() } ?: "خطأ اتصال: $code"
                }
                if (code == 401 && (token != null || credential401)) throw SessionExpiredException()
                if (code in 502..504) throw TransientNetworkException(
                    "السيرفر مشغول شوية. هنحاول نتصل تاني تلقائيًا.", java.io.IOException("gateway $code"))
                throw IllegalStateException(reason)
            }
            return response
        } catch (ex: java.net.SocketTimeoutException) {
            // Covers BOTH sending JSON (outputStream) and waiting for the response.
            throw TransientNetworkException("الاتصال اتأخر شوية. هنحاول تاني تلقائيًا؛ مش محتاج تقفل التطبيق.", ex)
        } catch (ex: java.net.UnknownHostException) {
            throw TransientNetworkException("الاتصال بالإنترنت مش متاح حاليًا. هنحاول نرجع تلقائيًا.", ex)
        } catch (ex: java.net.ConnectException) {
            throw TransientNetworkException("الاتصال بالسيرفر اتقطع مؤقتًا. هنحاول تاني تلقائيًا.", ex)
        } catch (ex: java.net.SocketException) {
            throw TransientNetworkException("الشبكة فصلت لحظة. هنحاول نوصل تلقائيًا.", ex)
        } catch (ex: javax.net.ssl.SSLException) {
            throw IllegalStateException("تعذر إنشاء اتصال آمن بالسيرفر. حاول مرة أخرى.", ex)
        } catch (ex: java.io.IOException) {
            // Never expose raw URLs, device IPs, headers, or Java exception text in UI.
            throw TransientNetworkException("الاتصال اتعطل مؤقتًا. هنحاول تاني تلقائيًا.", ex)
        } finally {
            connection.disconnect()
        }
    }

    // P50: shares the existing HTTPS/timeouts/401 rules; the path is always /v4.
    fun p50Request(token: String, path: String, method: String = "GET",
                   data: JSONObject? = null): JSONObject {
        require(path.startsWith("/") && !path.contains(".."))
        return JSONObject(request(path, token, method, data, "v4"))
    }
    fun register(name: String, password: String): SecureSessionStore.Session {
        val payload = JSONObject().put("name", name.trim()).put("password", password).put("supports_refresh", true)
        return authResponse(request("/auth/register", method = "POST", data = payload))
    }
    fun googleLogin(firebaseToken: String): SecureSessionStore.Session {
        return authResponse(request("/auth/google", method = "POST",
            data = JSONObject().put("firebase_id_token", firebaseToken).put("supports_refresh", true)))
    }
    fun linkGoogle(token: String, firebaseToken: String) {
        request("/auth/google/link", token, "POST",
            JSONObject().put("firebase_id_token", firebaseToken))
    }
    fun login(userId: Long, password: String): SecureSessionStore.Session {
        val payload = JSONObject().put("user_id", userId).put("password", password).put("supports_refresh", true)
        return authResponse(request("/auth/login", method = "POST", data = payload))
    }
    private fun authResponse(response: String): SecureSessionStore.Session {
        val o = JSONObject(response)
        return SecureSessionStore.Session(o.getLong("user_id"), o.getString("name"), o.getString("token"),
            sessionId = o.optString("session_id"), refreshToken = o.optString("refresh_token"),
            revokeToken = o.optString("revoke_token"), refreshExpiresAt = o.optLong("refresh_expires_at"))
    }
    internal fun enrollSession(session: SecureSessionStore.Session) = authResponse(
        rawRequest("/auth/session/enroll", session.token, "POST", JSONObject()))
    internal fun refreshSession(session: SecureSessionStore.Session, requestId: String) = authResponse(
        rawRequest("/auth/refresh", method = "POST", credential401 = true, data = JSONObject()
            .put("session_id", session.sessionId).put("refresh_token", session.refreshToken)
            .put("request_id", requestId)))
    internal fun renewLegacyGoogle(userId: Long, firebaseToken: String) = authResponse(
        rawRequest("/auth/google/renew", method = "POST", credential401 = true, data = JSONObject()
            .put("firebase_id_token", firebaseToken).put("expected_user_id", userId)))
    internal fun revokeSession(session: SecureSessionStore.Session) {
        val data = JSONObject().put("session_id", session.sessionId).put("revoke_token", session.revokeToken)
        rawRequest("/auth/session/logout", method = "POST", data = data, credential401 = true)
    }
    fun myDisplayName(token: String): String =
        JSONObject(request("/auth/me", token)).getString("name")
    fun updateDisplayName(token: String, newName: String): String =
        JSONObject(request("/auth/profile", token, "PUT",
            JSONObject().put("name", newName.trim()))).getString("name")

    /** P54: permanent deletion (server erases name, login and personal content). */
    fun deleteAccount(token: String) {
        request("/auth/account/delete", token, "POST", JSONObject().put("confirm", "DELETE"))
    }

    /** P56: a member leaves a khatma (server keeps the history, the khatma just disappears for them). */
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

    fun checkSession(token: String): Boolean =
        runCatching { JSONObject(request("/auth/me", token)).getLong("user_id") > 0L }.getOrDefault(false)

    fun today(token: String): List<TodayItem> {
        val a = JSONArray(request("/users/me/today", token))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            TodayItem(
                groupId = o.getLong("group_id"), groupName = o.getString("group_name"),
                role = o.getString("role"), status = o.getString("membership_status"),
                assignmentId = if (o.isNull("assignment_id")) null else o.optLong("assignment_id"),
                juz = if (o.isNull("juz_number")) null else o.optInt("juz_number"),
                completed = o.optBoolean("completed", false),
                doneCount = o.optInt("completed_count", 0), totalCount = o.optInt("total_count", 0),
                leaderName = o.optString("leader_name").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
    fun createGroup(token: String, name: String, leaderParticipates: Boolean): String {
        val o = JSONObject(request("/groups", token, "POST", JSONObject()
            .put("name", name.trim()).put("require_approval", true)
            .put("leader_participates", leaderParticipates)))
        return o.getString("invite_code")
    }
    fun join(token: String, inviteCode: String): String = JSONObject(
        request("/groups/join", token, "POST", JSONObject().put("invite_code", inviteCode.trim()))
    ).getString("status")
    fun complete(token: String, assignmentId: Long) {
        request("/assignments/$assignmentId/complete", token, "POST", JSONObject())
    }
    fun groupStatus(token: String, groupId: Long): GroupInfo {
        val o = JSONObject(request("/groups/$groupId/status", token))
        val members = o.getJSONArray("members")
        return GroupInfo(o.getLong("group_id"), o.getString("group_name"), o.getString("invite_code"),
            (0 until members.length()).map { index ->
                val m = members.getJSONObject(index)
                MemberInfo(m.getLong("membership_id"), m.getString("member_name"),
                    if (m.isNull("juz_number")) null else m.optInt("juz_number"),
                    m.optBoolean("completed", false))
            })
    }
    /** VC91: members-only directory; never request the leader-only status endpoint. */
    fun membersDirectory(token: String, groupId: Long, groupName: String): GroupInfo {
        val o = try {
            JSONObject(request("/groups/$groupId/members-directory", token))
        } catch (error: SessionExpiredException) {
            throw error
        } catch (error: IllegalStateException) {
            if (error.message?.contains("الخدمة غير متاحة على خادم التجربة") == true)
                throw IllegalStateException("قائمة الأعضاء تحتاج تحديث سيرفر الختمات. بياناتك محفوظة.", error)
            throw error
        }
        val members = o.getJSONArray("members")
        return GroupInfo(groupId, groupName, "", (0 until members.length()).map { index ->
            val m = members.getJSONObject(index)
            MemberInfo(m.getLong("membership_id"), m.getString("name"),
                if (m.isNull("juz")) null else m.optInt("juz"),
                m.optBoolean("completed", false))
        })
    }

    data class PrivateMessageInfo(val id: Long, val mine: Boolean, val text: String,
                                  val whenSent: String, val read: Boolean)
    fun privateMessages(token: String, groupId: Long, peerMembershipId: Long): List<PrivateMessageInfo> {
        val arr = JSONArray(request("/groups/$groupId/private/$peerMembershipId", token))
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            PrivateMessageInfo(m.getLong("id"), m.optBoolean("mine"), m.getString("text"),
                m.optString("created_at"), m.optBoolean("read"))
        }
    }
    fun registerPushDevice(token: String, deviceToken: String) {
        request("/push/devices", token, "POST", JSONObject().put("token", deviceToken))
    }
    fun unregisterPushDevice(token: String, deviceToken: String) {
        request("/push/devices", token, "DELETE", JSONObject().put("token", deviceToken))
    }
    fun sendPrivate(token: String, groupId: Long, peerId: Long, text: String,
                    clientMessageId: String? = null) {
        val data = JSONObject().put("text", text)
        if (clientMessageId != null) data.put("client_message_id", clientMessageId)
        request("/groups/$groupId/private/$peerId", token, "POST", data)
    }
    fun blockPrivate(token: String, groupId: Long, peerId: Long) {
        request("/groups/$groupId/private/$peerId/block", token, "POST", JSONObject())
    }
    fun reportPrivate(token: String, groupId: Long, peerId: Long, reason: String) {
        request("/groups/$groupId/private/$peerId/report", token, "POST", JSONObject().put("reason", reason))
    }

    /** Most recent 50 messages. User identity comes from the server session. */
    fun messages(token: String, groupId: Long): List<ChatMessage> {
        val a = JSONArray(request("/groups/$groupId/chat", token))
        return (0 until a.length()).map { index ->
            val m = a.getJSONObject(index)
            ChatMessage(m.getLong("id"), m.getLong("user_id"), m.getString("name"),
                m.getString("message"), m.optString("created_at"),
                edited = m.optBoolean("edited"),
                replyToId = if (m.isNull("reply_to_id")) null else m.optLong("reply_to_id"),
                pinned = m.optBoolean("pinned"),
                reactionText = m.optJSONObject("reactions")?.let { reactions ->
                    reactions.keys().asSequence().map { emoji ->
                        emoji + " " + reactions.optInt(emoji)
                    }.joinToString("  ")
                } ?: "")
        }
    }
    fun sendMessage(token: String, groupId: Long, text: String, replyTo: Long? = null,
                    clientMessageId: String? = null) {
        val json = JSONObject().put("message", text.trim())
        if (replyTo != null) json.put("reply_to_id", replyTo)
        if (clientMessageId != null) json.put("client_message_id", clientMessageId)
        request("/groups/$groupId/chat", token, "POST", json)
    }
    fun editMessage(token: String, groupId: Long, messageId: Long, text: String) {
        request("/groups/$groupId/chat/$messageId/edit", token, "POST",
            JSONObject().put("message", text.trim()))
    }
    fun deleteMessage(token: String, groupId: Long, messageId: Long, forEveryone: Boolean) {
        val scope = if (forEveryone) "everyone" else "me"
        request("/groups/$groupId/chat/$messageId?scope=$scope", token, "DELETE")
    }
    fun reactMessage(token: String, groupId: Long, messageId: Long, emoji: String) {
        request("/groups/$groupId/chat/$messageId/reaction", token, "PUT",
            JSONObject().put("emoji", emoji))
    }
    fun pinMessage(token: String, groupId: Long, messageId: Long,
                   minutes: Int, notify: Boolean) {
        request("/groups/$groupId/chat/$messageId/pin", token, "POST",
            JSONObject().put("duration_minutes", minutes)
                .put("notify_members", notify))
    }
    fun pinnedMessages(token: String, groupId: Long): List<String> {
        val data = JSONArray(request("/groups/$groupId/chat/pins", token))
        return (0 until data.length()).map { data.getJSONObject(it).getString("message") }
    }

    fun pending(token: String, groupId: Long): List<MemberInfo> {
        val a = JSONArray(request("/groups/$groupId/pending", token))
        return (0 until a.length()).map { i ->
            val m = a.getJSONObject(i)
            MemberInfo(m.getLong("membership_id"), m.getString("name"), null, false)
        }
    }
    fun approve(token: String, groupId: Long, membershipId: Long, approved: Boolean) {
        request("/groups/$groupId/members/$membershipId/approval", token, "POST", JSONObject().put("approve", approved))
    }
    fun due(token: String): List<Reminder> {
        val a = JSONArray(request("/reminders/due", token))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Reminder(o.getLong("assignment_id"), o.getString("stage"),
                o.getString("title"), o.getString("message"))
        }
    }
    fun ack(token: String, r: Reminder) {
        request("/reminders/ack", token, "POST", JSONObject()
            .put("assignment_id", r.assignmentId).put("stage", r.stage))
    }


    // P48 new read-only schedule plus authenticated completion/actions.
    data class ScheduleRow(val id:Long,val date:String,val juz:Int,val status:String,
        val canComplete:Boolean,val isTransfer:Boolean,val dueDate:String?,val history:List<String>)
    data class ReadingSchedule(val rows:List<ScheduleRow>,val overdueCount:Int,val pendingCount:Int)
    fun readingSchedule(token:String,groupId:Long,memberId:Long?=null):ReadingSchedule{
      val path=if(memberId==null) "/groups/"+groupId+"/my-schedule"
               else "/groups/"+groupId+"/members/"+memberId+"/schedule"
      val result=JSONObject(request(path,token));val rows=result.getJSONArray("rows")
      return ReadingSchedule((0 until rows.length()).map {i->
        val row=rows.getJSONObject(i);val events=row.optJSONArray("history")
        val history=if(events==null) emptyList() else (0 until events.length()).map{j->
          val h=events.getJSONObject(j)
          val kind=when(h.optString("action")){
            "reassign"->"إعادة توزيع";"excuse"->"إعفاء";"extend"->"تمديد";
            "received"->"تحويل من قارئ آخر";else->"قرار القائد"}
          kind+" • "+h.optString("by")+" • "+h.optString("date").take(10)
        }
        ScheduleRow(row.getLong("id"),row.getString("date"),row.getInt("juz"),
          row.getString("status"),row.optBoolean("can_complete"),
          row.optBoolean("is_transfer"),row.optString("due_date").takeIf{
            it.isNotBlank()&&it!="null"},history)
      },result.optInt("overdue_count"),result.optInt("pending_count"))
    }
    fun completeSchedule(token:String,row:ScheduleRow){
      if(row.isTransfer)request("/schedule/transfers/"+row.id+"/complete",token,"POST",JSONObject())
      else complete(token,row.id)
    }
    fun leaderScheduleAction(token:String,groupId:Long,assignmentId:Long,
        action:String,target:Long?=null,extendedTo:String?=null){
      require(action in listOf("reassign","extend","excuse"))
      val data=JSONObject().put("action",action)
      if(target!=null)data.put("target_membership_id",target)
      if(extendedTo!=null)data.put("extended_to",extendedTo)
      request("/groups/"+groupId+"/assignments/"+assignmentId+"/leader-action",token,"POST",data)
    }

    data class TodayItem(val groupId: Long, val groupName: String, val role: String, val status: String,
                         val assignmentId: Long?, val juz: Int?, val completed: Boolean,
                         val doneCount: Int, val totalCount: Int, val leaderName: String? = null)
    data class GroupInfo(val id: Long, val name: String, val inviteCode: String, val members: List<MemberInfo>)
    data class ChatMessage(val id: Long, val senderId: Long, val senderName: String,
                           val message: String, val createdAt: String,
                           val edited: Boolean = false, val replyToId: Long? = null,
                           val pinned: Boolean = false, val reactionText: String = "")
    data class MemberInfo(val id: Long, val name: String, val juz: Int?, val completed: Boolean)
    data class Reminder(val assignmentId: Long, val stage: String, val title: String, val message: String)

    // P35: public meetup on the existing authenticated V2 connection.
    data class MeetupPostInfo(val id: Long, val kind: String, val authorName: String,
        val groupName: String?, val text: String, val date: String, val mine: Boolean,
        val closed: Boolean, val seats: Int?, val thanks: Int, val thanked: Boolean, val comments: Int)
    data class MeetupCommentInfo(val id: Long, val authorName: String, val text: String, val mine: Boolean)

    private fun meetupPostFromJson(o: JSONObject) = MeetupPostInfo(
        id = o.getLong("id"), kind = o.getString("kind"),
        authorName = o.getString("author_name"),
        groupName = o.optString("group_name").takeIf { it.isNotBlank() && it != "null" },
        text = o.getString("text"), date = o.optString("created_at"), mine = o.optBoolean("mine"),
        closed = o.optBoolean("closed"), seats = if (o.isNull("seats_remaining")) null else o.optInt("seats_remaining"),
        thanks = o.optInt("thanks"), thanked = o.optBoolean("thanked"), comments = o.optInt("comments"))

    fun meetupPosts(token: String, kind: String = "all", offset: Int = 0): List<MeetupPostInfo> {
        require(kind in listOf("all", "group", "reader") && offset >= 0)
        val a = JSONArray(request("/meetup/posts?kind=$kind&offset=$offset&limit=20", token))
        return (0 until a.length()).map { meetupPostFromJson(a.getJSONObject(it)) }
    }
    fun meetupPublish(token: String, kind: String, text: String, groupId: Long?, seats: Int?) {
        val data = JSONObject().put("kind", kind).put("text", text.trim())
        if (kind == "group") {
            require(groupId != null && seats != null)
            data.put("group_id", groupId).put("seats_needed", seats)
        }
        request("/meetup/posts", token, "POST", data)
    }
    fun meetupEdit(token: String, postId: Long, text: String, closed: Boolean) {
        request("/meetup/posts/$postId", token, "PUT", JSONObject().put("text", text.trim()).put("closed", closed))
    }
    fun meetupDelete(token: String, postId: Long) { request("/meetup/posts/$postId", token, "DELETE") }
    fun meetupThanks(token: String, postId: Long) { request("/meetup/posts/$postId/thanks", token, "POST", JSONObject()) }
    fun meetupJoin(token: String, postId: Long): String = JSONObject(
        request("/meetup/posts/$postId/join", token, "POST", JSONObject())).getString("status")
    fun meetupComments(token: String, postId: Long): List<MeetupCommentInfo> {
        val a = JSONArray(request("/meetup/posts/$postId/comments", token))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            MeetupCommentInfo(o.getLong("id"), o.getString("author_name"), o.getString("text"), o.optBoolean("mine"))
        }
    }
    fun meetupComment(token: String, postId: Long, text: String) {
        request("/meetup/posts/$postId/comments", token, "POST", JSONObject().put("text", text.trim()))
    }
    fun meetupDeleteComment(token: String, postId: Long, commentId: Long) {
        request("/meetup/posts/$postId/comments/$commentId", token, "DELETE")
    }
    fun meetupReport(token: String, postId: Long, reason: String, commentId: Long? = null) {
        val path = if (commentId == null) "/meetup/posts/$postId/report"
                   else "/meetup/posts/$postId/comments/$commentId/report"
        request(path, token, "POST", JSONObject().put("reason", reason))
    }

}
