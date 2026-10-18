package com.ab.khatma.secure

import com.ab.khatma.ui.screens.PrayerPremiumScreen
import com.ab.khatma.ui.screens.InteractiveAdhkarScreen
import com.ab.khatma.ui.screens.P53BottomNav
import com.ab.khatma.ui.screens.P52MiniPlayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.UUID
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Isolated onboarding for new V2 accounts. Legacy accounts are not claimed by ID. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureV2Screen(onBack: () -> Unit, onOpenQuran: (Int?) -> Unit,
                   onOpenPages: (Int, Int) -> Unit,
                   p79Target: P79PrivateTarget? = null,
                   p79TargetConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember(context) { SecureSessionStore(context) }
    com.ab.khatma.guide.P85Guide.Host()
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(store.load()) }
    LaunchedEffect(Unit) { session?.let { com.ab.khatma.guide.P85Guide.markRestored(context, it.userId) } }
    var createAccount by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var idField by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<SecureV2Api.TodayItem>>(emptyList()) }
    var groupsLoaded by remember { mutableStateOf(false) }
    // A failed request is NOT an infinite loading state or evidence of 0 groups.
    var groupsLoadFailed by remember(session?.token) { mutableStateOf(false) }
    // Last successful sync per active session; manual Refresh always bypasses TTL.
    var p41LastGroupsFetchTime by remember(session?.token) { mutableLongStateOf(0L) }
    // P40: a transient auto-refresh failure must not repeatedly interrupt reading.
    var initialGroupLoadWarningShown by remember(session?.token) { mutableStateOf(false) }
    // VC105: transient network failures recover while app remains open.
    var p81PendingNetworkRecovery by remember(session?.token) { mutableStateOf(false) }
    var p81RetrySignal by remember(session?.token) { mutableIntStateOf(0) }
    var p81InForeground by remember { mutableStateOf(true) }
    var groupName by remember { mutableStateOf("") }
    var groupNote by remember { mutableStateOf("") }
    var planDays by remember { mutableStateOf("30") }
    var customPlanDays by remember { mutableStateOf("") }
    var planMode by remember { mutableStateOf("auto") }
    var groupQuery by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var groupInfo by remember { mutableStateOf<SecureV2Api.GroupInfo?>(null) }
    var p69PrivatePeer by remember { mutableStateOf<SecureV2Api.MemberInfo?>(null) }
    var p79NotificationPeer by remember { mutableStateOf<SecureV2Api.MemberInfo?>(null) }
    var p79NotificationGroup by remember { mutableLongStateOf(0L) }
    var pending by remember { mutableStateOf<List<SecureV2Api.MemberInfo>>(emptyList()) }
    var lastInvitation by remember { mutableStateOf<Pair<String, String>?>(null) }
    var chatGroupId by remember { mutableStateOf<Long?>(null) }
    var chatMessages by remember { mutableStateOf<List<SecureV2Api.ChatMessage>>(emptyList()) }
    var chatText by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<Long?>(null) }
    // User + group scoped; retry after timeout reuses the same request key.
    var p82GroupPendingPayload by rememberSaveable(session?.userId, chatGroupId) {
        mutableStateOf("")
    }
    var p82GroupPendingId by rememberSaveable(session?.userId, chatGroupId) {
        mutableStateOf("")
    }
    var actionMenu by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<SecureV2Api.ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var pinTarget by remember { mutableStateOf<SecureV2Api.ChatMessage?>(null) }
    var pinMinutes by remember { mutableStateOf("60") }
    var notifyPin by remember { mutableStateOf(false) }
    var pinnedMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var chatError by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("home") }
    var joinFromMeetup by remember { mutableStateOf(false) }
    var selectedGroupId by remember { mutableLongStateOf(0L) }
    var groupTab by remember { mutableStateOf("read") }
    var confirmTodayAssignment by remember { mutableStateOf<SecureV2Api.TodayItem?>(null) }
    var archivedIds by remember(session?.userId) { mutableStateOf(P56Archive.load(context, session?.userId ?: 0L)) }
    var p56Manage by remember { mutableStateOf<Pair<SecureV2Api.TodayItem, Boolean>?>(null) }
    val p81LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(p81LifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    p81InForeground = true
                    if (p81PendingNetworkRecovery) p81RetrySignal += 1
                }
                Lifecycle.Event.ON_STOP -> p81InForeground = false
                else -> Unit
            }
        }
        p81LifecycleOwner.lifecycle.addObserver(observer)
        p81InForeground = p81LifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { p81LifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Renewal changes the stored token without losing the current groups or account.
    val sessionChange by SecureSessionStore.changes.collectAsState()
    LaunchedEffect(sessionChange) {
        val saved = store.load()
        if (saved?.localId != session?.localId) {
            groups = emptyList()
            groupsLoaded = false
            groupInfo = null
            pending = emptyList()
            chatGroupId = null
            chatMessages = emptyList()
            p79NotificationPeer = null
            p69PrivatePeer = null
        }
        session = saved
    }

    // VC103: refresh device registration only on session changes or Firebase token rotation.
    LaunchedEffect(session?.userId, session?.token) {
        session?.let { P79Push.sync(context, it) }
    }
    // Notification navigation never trusts sender claims: directory lookup uses
    // the logged-in account and server-side membership authorization.
    LaunchedEffect(p79Target, session?.userId) {
        val target = p79Target ?: return@LaunchedEffect
        val current = session ?: return@LaunchedEffect
        if (target.recipientUserId != current.userId) {
            p79TargetConsumed()
            return@LaunchedEffect
        }
        runCatching {
            withContext(Dispatchers.IO) {
                val activeGroup = SecureV2Api.today(current.token)
                    .firstOrNull { it.groupId == target.groupId && it.status == "active" }
                    ?: error("هذه الختمة غير متاحة لحسابك")
                val directory = if (activeGroup.role == "leader")
                    SecureV2Api.groupStatus(current.token, target.groupId)
                else SecureV2Api.membersDirectory(current.token, target.groupId, activeGroup.groupName)
                directory.members.firstOrNull { it.id == target.senderMembershipId }
                    ?: error("العضو لم يعد متاحًا")
            }
        }.onSuccess { peer ->
            p79NotificationGroup = target.groupId
            p79NotificationPeer = peer
        }.onFailure { message = "تعذر فتح الرسالة؛ افتح الختمة وتحقق من العضوية والاتصال." }
        p79TargetConsumed()
    }
    if (p79NotificationPeer != null && session != null) {
        Surface(Modifier.fillMaxSize()) {
            P69PrivateChat(
                token = session!!.token, groupId = p79NotificationGroup,
                peer = p79NotificationPeer!!,
                onClose = { p79NotificationPeer = null }
            )
        }
        return
    }

    // P36: render the native prayer screen in the current secure session,
    // returning to the same authenticated home without recreating the session.
    if (session != null && section == "prayer") {
        // The prayer page supplies its own navigation and safe window insets.
        // A second parent header used to overlap the status bar on modern devices.
        PrayerPremiumScreen(onBack = { section = "home" })
        return
    }


    if (session != null && section == "ibtehal") {
        com.ab.khatma.ui.screens.P85IbtehalScreen(onBack = { section = "home" })
        return
    }

    // P38: independent offline adhkar screen inside existing secure session.
    if (session != null && section == "adhkar") {
        InteractiveAdhkarScreen(onBack = { section = "home" })
        return
    }

    fun shareCode(groupName: String, code: String) {
        val text = "انضم إلى ختمة «$groupName» عبر تطبيق ختمة.\nكود الدعوة: $code"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val whatsapp = Intent(intent).setPackage("com.whatsapp")
        try { context.startActivity(whatsapp) }
        catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(intent, "مشاركة دعوة الختمة"))
        }
    }
    fun copyCode(code: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("كود ختمة", code))
        message = "تم نسخ كود الدعوة"
    }

    fun showExpiredSession() {
        val old = session ?: return
        // Remove only the expired encrypted credential; never delete any groups,
        // server-side state, Quran annotations, or Google account linkage.
        idField = old.userId.toString()
        if (!store.clearIfCurrent(old.localId)) {
            session = store.load()
            return
        }
        KhatmaGoogleSignIn.signOut(context)
        p81PendingNetworkRecovery = false
        SecureReminderWorker.cancel(context)
        session = null
        groups = emptyList()
        groupsLoaded = false
        groupsLoadFailed = false
        section = "home"
        message = "انتهت جلسة الدخول. سجل الدخول مجددًا بنفس الحساب؛ المجموعات محفوظة."
    }
    fun runTask(task: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            message = ""
            try { task() }
            catch (_: GetCredentialCancellationException) {
                message = ""
            }
            catch (_: SecureV2Api.SessionExpiredException) { showExpiredSession() }
            catch (e: Exception) { message = e.message ?: "تعذر الاتصال بالخادم" }
            finally { busy = false }
        }
    }
    fun reload() = runTask {
        val s = session ?: return@runTask
        // VC97: a manual retry must mark an actual HTTP failure, not display zero groups.
        val fresh = try {
            P81NetworkRecovery.readOnly { SecureV2Api.today(s.token) }
        } catch (error: Exception) {
            if (error is SecureV2Api.TransientNetworkException) p81PendingNetworkRecovery = true
            if (!groupsLoaded) groupsLoadFailed = true
            throw error // runTask preserves precise timeout/auth feedback.
        }
        groups = fresh
        groupsLoaded = true
        groupsLoadFailed = false
        p81PendingNetworkRecovery = false
        p41LastGroupsFetchTime = android.os.SystemClock.elapsedRealtime()
        if (message.startsWith("تعذر تحديث المجموعات") || message.startsWith("تعذر الاتصال بالمجموعات")) message = ""
    }
    LaunchedEffect(session?.token) {
        val current = session ?: return@LaunchedEffect
        SecureReminderWorker.schedule(context)
        runCatching { withContext(Dispatchers.IO) { SecureV2Api.myDisplayName(current.token) } }
            .onSuccess { freshName ->
                if (session?.token == current.token && freshName.isNotBlank() &&
                    freshName != session?.userName) {
                    session = store.updateName(current.localId, freshName)
                }
            }
    }
    LaunchedEffect(session?.token, section, p81RetrySignal) {
        val current = session ?: return@LaunchedEffect
        if (section != "home" && section != "khatmas") return@LaunchedEffect
        // Opening home and going back must not re-download the same response.
        if (groupsLoaded && !p81PendingNetworkRecovery &&
            android.os.SystemClock.elapsedRealtime() - p41LastGroupsFetchTime < 120_000L) return@LaunchedEffect
        runCatching { P81NetworkRecovery.readOnly { SecureV2Api.today(current.token) } }
            .onSuccess {
                if (session?.token == current.token) {
                    groups = it
                    groupsLoaded = true
                    groupsLoadFailed = false
                    p41LastGroupsFetchTime = android.os.SystemClock.elapsedRealtime()
                    initialGroupLoadWarningShown = false
                    p81PendingNetworkRecovery = false
                    if (message.startsWith("تعذر تحديث المجموعات") ||
                        message.startsWith("تعذر الاتصال بالمجموعات") ||
                        message.startsWith("الاتصال ") || message.startsWith("الشبكة ") ||
                        message.startsWith("السيرفر مشغول")) message = ""
                }
            }
            .onFailure { error ->
                if (session?.token != current.token) return@onFailure
                when (error) {
                    is SecureV2Api.SessionExpiredException -> showExpiredSession()
                    else -> {
                        if (error is SecureV2Api.TransientNetworkException)
                            p81PendingNetworkRecovery = true
                        // Retain any previously loaded groups; never pretend 0 groups.
                        if (!groupsLoaded) groupsLoadFailed = true
                        if (!groupsLoaded && !initialGroupLoadWarningShown) {
                            initialGroupLoadWarningShown = true
                            message = error.message?.takeIf { it.isNotBlank() }
                                ?: "تعذر تحديث المجموعات. اضغط تحديث للمحاولة مرة أخرى."
                        }
                    }
                }
            }
    }

    // VC105: retry only after a confirmed transient network failure.
    // A slow server never triggers a busy polling loop; sleeps stop while backgrounded.
    LaunchedEffect(session?.token, section, p81PendingNetworkRecovery,
                   p81RetrySignal, p81InForeground) {
        val activeToken = session?.token ?: return@LaunchedEffect
        if (!p81InForeground || !p81PendingNetworkRecovery ||
            (section != "home" && section != "khatmas")) return@LaunchedEffect
        delay(30_000L)
        if (session?.token == activeToken && p81InForeground &&
            p81PendingNetworkRecovery && !busy) {
            p81RetrySignal += 1
        }
    }

    LaunchedEffect(chatGroupId, session?.token) {
        val id = chatGroupId ?: return@LaunchedEffect
        val token = session?.token ?: return@LaunchedEffect
        while (true) {
            runCatching { withContext(Dispatchers.IO) { SecureV2Api.messages(token, id) } }
                 .onSuccess {
                    chatMessages = it
                    chatError = ""
                    pinnedMessages = runCatching {
                        withContext(Dispatchers.IO) { SecureV2Api.pinnedMessages(token, id) }
                    }.getOrDefault(emptyList())
                }
                .onFailure { chatError = "تعذر تحديث المحادثة. تحقق من الاتصال." }
            delay(5_000)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("تعديل الرسالة") },
            text = { OutlinedTextField(editText, { editText = it.take(1000) },
                modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = {
                val target = editTarget ?: return@TextButton
                runTask {
                    withContext(Dispatchers.IO) {
                        SecureV2Api.editMessage(session!!.token, chatGroupId!!,
                            target.id, editText)
                        chatMessages = SecureV2Api.messages(session!!.token, chatGroupId!!)
                    }
                    editTarget = null
                }
            }, enabled = !busy && editText.isNotBlank()) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("إلغاء") } }
        )
    }

// P39: confirm every reader completion (home AND existing group page).
// The server remains the authority for ownership, date and current assignment.
P56ManageDialog(p56Manage, busy, onDismiss = { p56Manage = null }) { item, close ->
    runTask {
        val current = session ?: return@runTask
        try {
            withContext(Dispatchers.IO) {
                if (close) SecureV2Api.closeGroup(current.token, item.groupId)
                else SecureV2Api.leaveGroup(current.token, item.groupId)
            }
        } catch (e: Exception) { p56Manage = null; throw e }
        p56Manage = null
        archivedIds = archivedIds - item.groupId
        P56Archive.save(context, current.userId, archivedIds)
        groups = withContext(Dispatchers.IO) { SecureV2Api.today(current.token) }
        groupsLoaded = true
        message = if (close) "تم حذف الختمة «${item.groupName}»" else "خرجت من ختمة «${item.groupName}»"
    }
}
confirmTodayAssignment?.let { requested ->
    AlertDialog(
        onDismissRequest = { if (!busy) confirmTodayAssignment = null },
        title = { Text("تأكيد إتمام القراءة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("هل أتممت قراءة ${requested.juz?.let { "الجزء $it" } ?: "ورد اليوم"} في ختمة «${requested.groupName}»؟")
                Text("يمكنك تسجيل القراءة حتى لو قرأت من مصحف ورقي.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (busy) return@Button
                runTask {
                    val current = session ?: return@runTask
                    val fresh = withContext(Dispatchers.IO) { SecureV2Api.today(current.token) }
                    groups = fresh
                    groupsLoaded = true
                    val stillValid = fresh.singleOrNull {
                        it.groupId == requested.groupId &&
                        it.assignmentId == requested.assignmentId &&
                        it.status == "active"
                    }
                    if (stillValid == null || stillValid.completed || requested.assignmentId == null) {
                        confirmTodayAssignment = null
                        message = if (stillValid?.completed == true)
                            "تم تسجيل هذا الورد من قبل" else
                            "الورد تغيّر أو لم يعد متاحًا. تم تحديث القائمة."
                        return@runTask
                    }
                    withContext(Dispatchers.IO) {
                        SecureV2Api.complete(current.token, requested.assignmentId)
                    }
                    runCatching { P53LocalStats.recordCompletion(context) }
                    // Immediate UI feedback even when the refresh request is slow.
                    groups = fresh.map {
                        if (it.groupId == requested.groupId &&
                            it.assignmentId == requested.assignmentId)
                            it.copy(completed = true) else it
                    }
                    confirmTodayAssignment = null
                    message = "تم تسجيل قراءتك في ختمة «${requested.groupName}»"
                    runCatching { withContext(Dispatchers.IO) { SecureV2Api.today(current.token) } }
                        .onSuccess { groups = it }
                }
            }, enabled = !busy) { Text("نعم، أتممت القراءة") }
        },
        dismissButton = {
            TextButton(onClick = { confirmTodayAssignment = null }, enabled = !busy) {
                Text("إلغاء")
            }
        }
    )
}

    if (pinTarget != null) {
        AlertDialog(
            onDismissRequest = { pinTarget = null },
            title = { Text("مدة تثبيت الرسالة") },
            text = {
                Column {
                    OutlinedTextField(pinMinutes, { pinMinutes = it.filter(Char::isDigit).take(6) },
                        label = { Text("عدد الدقائق") })
                    Row {
                        Checkbox(notifyPin, { notifyPin = it })
                        Text("إشعار الجميع")
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val target = pinTarget ?: return@TextButton
                runTask {
                    val duration = pinMinutes.toIntOrNull() ?: 0
                    check(duration in 1..525600) { "مدة تثبيت غير صحيحة" }
                    withContext(Dispatchers.IO) {
                        SecureV2Api.pinMessage(session!!.token, chatGroupId!!,
                            target.id, duration, notifyPin)
                        pinnedMessages = SecureV2Api.pinnedMessages(session!!.token, chatGroupId!!)
                    }
                    pinTarget = null
                }
            }, enabled = !busy) { Text("تثبيت") } },
            dismissButton = { TextButton(onClick = { pinTarget = null }) { Text("إلغاء") } }
        )
    }
    Scaffold(
        topBar = {
            if (session != null && section != "home" && section != "profile" && section != "notifications") TopAppBar(
                title = {
                    Column {
                        Text("خَتْمَة", style = MaterialTheme.typography.titleLarge)
                        if (session != null) {
                            Text(when (section) {
                                "group" -> "تفاصيل المجموعة"
                                "create" -> "مجموعة جديدة"
                                "join" -> "الانضمام لمجموعة"
                                "meetup" -> "ملتقى الختمات"
                                "plans" -> "ختمات الصفحات"
                                "schedule" -> "جدولي"
                                "leaderSchedule" -> "متابعة المتأخرات"
                                "profile" -> "الملف الشخصي"
                                "khatmas" -> "الختمات والمجموعات"
                                "allwards" -> "كل ختماتي اليوم"
                                "archive" -> "الختمات المؤرشفة"
                                else -> "مجموعاتي"
                            }, style = MaterialTheme.typography.bodySmall,
                               color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {},
                actions = { if (session != null) com.ab.khatma.guide.P85Guide.HelpButton(when (section) { "group", "join", "meetup" -> "khatmas"; "notifications", "archive" -> "profile"; else -> section }) }
            )
        },
        bottomBar = {
            Column {

            if (session != null && section == "group" && groups.any {
                it.groupId == selectedGroupId && it.role == "leader" && it.status == "active"
            }) {
                P28LeaderNavigation(selected = groupTab, onChoose = { tab ->
                    groupTab = tab
                    chatGroupId = if (tab == "chat") selectedGroupId else null
                    if (tab == "chat") { chatMessages = emptyList(); chatError = "" }
                })
            } else if (session != null && (section == "home" || section == "profile")) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp) {
                    NavigationBarItem(
                        selected = section == "home",
                        onClick = { section = "home"; chatGroupId = null },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("الرئيسية") }
                    )
                    NavigationBarItem(
                        selected = false, onClick = { onOpenQuran(null) },
                        icon = { Icon(Icons.Default.Book, contentDescription = null) },
                        label = { Text("المصحف") }
                    )
                    NavigationBarItem(
                        selected = section == "profile",
                        onClick = { section = "profile"; chatGroupId = null },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("حسابي") }
                    )
                }
            }
        
                if (session != null && section != "home" && section != "profile") {
                    P53BottomNav(
                        onBack = {
                            section = when (section) {
                                "plans", "schedule", "leaderSchedule" -> "group"
                                "join" -> if (joinFromMeetup) "meetup" else "khatmas"
                                "group" -> "khatmas"
                                "notifications", "archive" -> "profile"
                                else -> "home"
                            }
                            if (section != "group") groupTab = "read"
                            chatGroupId = null
                        },
                        onHome = { section = "home"; groupTab = "read"; chatGroupId = null },
                    )
                }
            }
        }
    ) { padding ->
        // P30: a dedicated full-height chat surface, without the oversized group hero.
        // The reader's existing group screens remain unchanged.
        val leaderChatGroup = groups.firstOrNull {
            section == "group" && groupTab == "chat" &&
                it.groupId == selectedGroupId && it.status == "active" && it.role == "leader"
        }
        if (leaderChatGroup != null && session != null) {
            val signedIn = session!!
            P30LeaderChatScreen(
                groupName = leaderChatGroup.groupName,
                members = groupInfo?.takeIf { it.id == leaderChatGroup.groupId }?.members?.size,
                messages = chatMessages,
                myId = signedIn.userId,
                pins = pinnedMessages,
                error = chatError,
                notice = message,
                replyToId = replyToId,
                text = chatText,
                busy = busy,
                onBack = { groupTab = "leader"; chatGroupId = null },
                onReply = { replyToId = it },
                onTextChange = { if (it.length <= 1000) chatText = it },
                onSend = {
                    val sending = chatText.trim()
                    if (sending.isNotBlank()) runTask {
                        withContext(Dispatchers.IO) {
                            SecureV2Api.sendMessage(signedIn.token, leaderChatGroup.groupId,
                                sending, replyToId)
                        }
                        chatText = ""
                        replyToId = null
                        chatMessages = withContext(Dispatchers.IO) {
                            SecureV2Api.messages(signedIn.token, leaderChatGroup.groupId)
                        }
                    }
                },
                onReact = { chat -> runTask {
                    withContext(Dispatchers.IO) {
                        SecureV2Api.reactMessage(signedIn.token, leaderChatGroup.groupId, chat.id, "❤️")
                        chatMessages = SecureV2Api.messages(signedIn.token, leaderChatGroup.groupId)
                    }
                } },
                onEdit = { chat -> editText = chat.message; editTarget = chat },
                onDeleteMine = { chat -> runTask {
                    withContext(Dispatchers.IO) {
                        SecureV2Api.deleteMessage(signedIn.token, leaderChatGroup.groupId, chat.id, false)
                        chatMessages = SecureV2Api.messages(signedIn.token, leaderChatGroup.groupId)
                    }
                } },
                onDeleteAll = { chat -> runTask {
                    withContext(Dispatchers.IO) {
                        SecureV2Api.deleteMessage(signedIn.token, leaderChatGroup.groupId, chat.id, true)
                        chatMessages = SecureV2Api.messages(signedIn.token, leaderChatGroup.groupId)
                    }
                } },
                onPin = { pinTarget = it }
            )
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = if (section in listOf("khatmas", "create", "join", "group", "meetup")) 0.dp else 18.dp),
            contentPadding = PaddingValues(top = if (section == "group") 0.dp else 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (message.isNotBlank()) {
                    val isSuccess = message.startsWith("تم") || message.startsWith("مرحبًا")
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 1.dp
                    ) {
                        Text(
                            text = message.take(180),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3
                        )
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (session == null) {
                item {
                    KhatmaCalmLogin(
                        createAccount = createAccount,
                        onModeChange = { createAccount = it; message = "" },
                        name = name, onNameChange = { name = it },
                        accountId = idField, onAccountIdChange = { idField = it },
                        password = password, onPasswordChange = { password = it },
                        busy = busy,
                        googleReady = KhatmaGoogleSignIn.isConfigured,
                        onGoogleSignIn = {
                            runTask {
                                val firebaseToken = KhatmaGoogleSignIn.firebaseIdToken(context)
                                val account = withContext(Dispatchers.IO) {
                                    SecureV2Api.googleLogin(firebaseToken)
                                }
                                store.save(account)
                                SecureReminderWorker.schedule(context)
                                com.ab.khatma.guide.P85Guide.signedIn(context, account.userId)
                                session = account
                                password = ""
                                section = "home"
                                message = "" // no duplicate login banner above home
                            }
                        },
                        onSubmit = {
                            runTask {
                                val account = withContext(Dispatchers.IO) {
                                    if (createAccount) SecureV2Api.register(name, password)
                                    else SecureV2Api.login(idField.toLong(), password)
                                }
                                store.save(account)
                                SecureReminderWorker.schedule(context)
                                com.ab.khatma.guide.P85Guide.signedIn(context, account.userId)
                                session = account
                                password = ""
                                section = "home"
                                message = if (createAccount)
                                    "تم إنشاء حسابك. رقم الحساب: " + account.userId + " — احتفظ به."
                                else "مرحبًا بعودتك"
                            }
                        }
                    )
                }
            } else {
                val active = session!!
                item {
                    if (section == "home") {
                        P52MiniPlayer()
                        KhatmaVisualHome(
                            userName = active.userName,
                            groupCount = groups.count { it.groupId !in archivedIds },
                            groupsLoaded = groupsLoaded,
                            groupsLoadFailed = groupsLoadFailed,
                            onOpenMushaf = { onOpenQuran(null) },
                            onOpenKhatmas = { section = "khatmas"; message = "" },
                            onJoinGroup = { section = "meetup"; message = "" },
                            onOpenProfile = { section = "profile"; message = "" },
                            onOpenPrayer = { section = "prayer"; message = "" },
                            onOpenAdhkar = { section = "adhkar"; message = "" },
                            onOpenIbtehal = { section = "ibtehal"; message = "" },
                            showGenericWard = !groupsLoaded,
                            dailyReaderWards = {
                                // One independently confirmed card per active reader assignment.
                                P52CompactWards(
                                    assignments = groups.filter { it.groupId !in archivedIds },
                                    maxPreview = 4,
                                    viewerName = active.userName,
                                    onShowAll = { section = "allwards"; message = "" },
                                    busy = busy,
                                    onRead = { ward ->
                                        if (ward.juz != null) onOpenQuran(ward.juz)
                                        else {
                                            selectedGroupId = ward.groupId
                                            section = "plans"
                                            chatGroupId = null
                                        }
                                    },
                                    onConfirm = { ward -> confirmTodayAssignment = ward }
                                )
                            }
                        )
                    } else if (section == "allwards") {
                        Text("كل ختماتي اليوم", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary)
                        P52CompactWards(assignments = groups.filter { it.groupId !in archivedIds }, busy = busy,
                            viewerName = active.userName,
                            onRead = { ward ->
                                if (ward.juz != null) onOpenQuran(ward.juz)
                                else {
                                    selectedGroupId = ward.groupId
                                    section = "schedule"
                                    chatGroupId = null
                                }
                            },
                            onConfirm = { ward -> confirmTodayAssignment = ward })
                    } else if (section == "archive") {
                        P56ArchivePage(groups.filter { it.groupId in archivedIds }) { id ->
                            archivedIds = archivedIds - id
                            P56Archive.save(context, active.userId, archivedIds)
                            message = "تم استرجاع الختمة"
                        }
                    } else if (section == "khatmas") {
                        P25GroupsHub(
                            count = groups.size, loaded = groupsLoaded,
                            loadingFailed = groupsLoadFailed,
                            search = groupQuery, onSearch = { groupQuery = it },
                            onCreate = { section = "create"; message = "" },
                            onJoin = { joinFromMeetup = false; section = "join"; message = "" },
                            onRefresh = { reload() }, busy = busy,
                            onBack = { section = "home" }
                        )
                        lastInvitation?.let { invitation ->
                            P25Panel {
                                Text("تم إنشاء مجموعة «${invitation.first}»", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text("كود الدعوة: ${invitation.second}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { copyCode(invitation.second) }, modifier = Modifier.weight(1f)) {
                                        Text("نسخ الرمز")
                                    }
                                    Button(onClick = { shareCode(invitation.first, invitation.second) }, modifier = Modifier.weight(1f)) {
                                        Text("مشاركة الدعوة")
                                    }
                                }
                            }
                        }
                        if (groupsLoaded && groups.isEmpty()) {
                            P25Panel { Text("ما عندكش مجموعات حتى الآن. أنشئ مجموعة أو انضم بكود الدعوة.") }
                        }
                        if (groupsLoaded && groups.isNotEmpty() && groups.none {
                            it.groupName.contains(groupQuery.trim(), ignoreCase = true)
                        }) {
                            P25Panel { Text("لا توجد مجموعة تطابق البحث") }
                        }
                    } else if (section == "create") {
                        P25CreateGroup(
                            name = groupName, onName = { groupName = it },
                            note = groupNote, onNote = { groupNote = it },
                            days = planDays, onDays = { planDays = it },
                            custom = customPlanDays, onCustom = { customPlanDays = it },
                            method = planMode, onMethod = { planMode = it },
                            busy = busy, onBack = { section = "khatmas" },
                            onSubmit = {
                                runTask {
                                    val nameToCreate = groupName.trim()
                                    val previousIds = groups.map { it.groupId }.toSet()
                                    val daysToUse = if (planDays == "custom") customPlanDays.toInt() else planDays.toInt()
                                    val code = withContext(Dispatchers.IO) {
                                        SecureV2Api.createGroup(active.token, nameToCreate, false)
                                    }
                                    val fresh = runCatching {
                                        withContext(Dispatchers.IO) { SecureV2Api.today(active.token) }
                                    }.getOrElse { groups }
                                    groups = fresh
                                    groupsLoaded = true
                                    lastInvitation = nameToCreate to code
                                    groupName = ""; groupNote = ""
                                    section = "khatmas"
                                    // Create a plan only for a provably new group, never one selected by name alone.
                                    val created = fresh.filter {
                                        it.groupId !in previousIds && it.role == "leader" && it.groupName == nameToCreate
                                    }.singleOrNull()
                                    val planStatus = if (created == null) "يمكنك ضبط خطة الختمة من داخل المجموعة." else {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                PageApi.create(active.token, created.groupId,
                                                    java.time.LocalDate.now().toString(), daysToUse,
                                                    "once", "full", planMode)
                                            }
                                        }.fold(onSuccess = { "تم تجهيز خطة قراءة لمدة $daysToUse يومًا." },
                                            onFailure = { "المجموعة أُنشئت؛ افتح خطة الختمة لإكمال التوزيع." })
                                    }
                                    message = "تم إنشاء المجموعة. $planStatus"
                                }
                            }
                        )
                     } else if (section == "meetup") {
                        P35MeetupScreen(
                            token = active.token,
                            leaderGroups = groups.filter { it.role == "leader" && it.status == "active" },
                            onBack = { section = "home"; message = "" },
                            onJoinByCode = { joinFromMeetup = true; section = "join"; message = "" },
                            onMembershipUpdated = { reload() }
                        )
                    } else if (section == "join") {
                        P25JoinGroup(code = inviteCode, onCode = { inviteCode = it }, busy = busy,
                            onBack = { section = if (joinFromMeetup) "meetup" else "khatmas" }, onJoin = {
                                runTask {
                                    val status = withContext(Dispatchers.IO) { SecureV2Api.join(active.token, inviteCode) }
                                    groups = withContext(Dispatchers.IO) { SecureV2Api.today(active.token) }
                                    groupsLoaded = true
                                    inviteCode = ""
                                    section = "khatmas"
                                    message = if (status == "pending") "تم إرسال طلب الانضمام للقائد" else "تم الانضمام"
                                }
                            })
                    } else if (section == "group") {
                        val chosen = groups.firstOrNull { it.groupId == selectedGroupId }
                        if (chosen != null) {
                            if (chosen.role == "leader") {
                                P28LeaderHero(
                                    name = chosen.groupName,
                                    onBack = { section = "khatmas"; groupTab = "read"; chatGroupId = null },
                                    onSettings = { groupTab = "leader_plans"; chatGroupId = null }
                                )
                            } else {
                                P25GroupOverview(
                                    item = chosen, details = groupInfo?.takeIf { it.id == chosen.groupId },
                                    onBack = { section = "khatmas"; groupTab = "read"; chatGroupId = null }
                                )
                            }
                        }
                    } else if (section == "notifications") {
                        P53NotificationsPage()
                    } else if (section == "profile") {
                        P53ProfilePage(
                            userName = active.userName,
                            userId = active.userId,
                            groups = groups,
                            googleConfigured = KhatmaGoogleSignIn.isConfigured,
                            onOpenNotifications = { section = "notifications"; message = "" },
                            archivedCount = archivedIds.size,
                            onOpenArchive = { section = "archive"; message = "" },
                            onDeleteAccount = {
                                runTask {
                                    withContext(Dispatchers.IO) { SecureV2Api.deleteAccount(active.token) }
                                    SecureReminderWorker.cancel(context)
                                    KhatmaGoogleSignIn.signOut(context)
                                    store.clearIfCurrent(active.localId)
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
                            },
                            onLinkGoogle = {
                                runTask {
                                    val firebaseToken = KhatmaGoogleSignIn.firebaseIdToken(context)
                                    withContext(Dispatchers.IO) {
                                        SecureV2Api.linkGoogle(active.token, firebaseToken)
                                    }
                                    message = "تم ربط Google بحسابك الحالي ومجموعاتك"
                                }
                            },
                            onCopyId = {
                                copyCode(active.userId.toString())
                                message = "تم نسخ رقم الحساب"
                            },
                            busy = busy,
                            onSaveName = { proposed ->
                                runTask {
                                    val updated = withContext(Dispatchers.IO) {
                                        SecureV2Api.updateDisplayName(active.token, proposed)
                                    }
                                    session = store.updateName(active.localId, updated)
                                    message = "تم حفظ الاسم الظاهر"
                                }
                            },
                            onLogout = {
                                SecureReminderWorker.cancel(context)
                                P83Sessions.logout(context, active)
                                session = null
                                groups = emptyList()
                                groupsLoaded = false
                                groupInfo = null
                                pending = emptyList()
                                chatGroupId = null
                                chatMessages = emptyList()
                                lastInvitation = null
                                section = "home"
                                message = ""
                            }
                        )
                    }
                }
                item {
                    if (section == "group") {
                        val chosen = groups.firstOrNull { it.groupId == selectedGroupId }
                        if (chosen != null && chosen.status == "active" && chosen.role != "leader") {
                            P25GroupTabs(
                                selected = groupTab,
                                canManage = chosen.role == "leader" || chosen.role == "assistant",
                                onPlans = { section = "schedule"; chatGroupId = null },
                                onSelect = { tab ->
                                    groupTab = tab
                                    chatGroupId = if (tab == "chat") chosen.groupId else null
                                    if (tab == "chat") {
                                        chatMessages = emptyList()
                                        chatError = ""
                                    }
                                }
                            )
                        }
                    }
                    if (section == "schedule" || section == "leaderSchedule") {
                        val chosen = groups.firstOrNull { it.groupId == selectedGroupId }
                        if (chosen != null && chosen.status == "active") {
                            P51ScheduleBridge(token = active.token, groupId = selectedGroupId,
                                isLeader = chosen.role == "leader",
                                groupName = chosen.groupName, todayAssignment = chosen,
                                onRead = { onOpenQuran(it) },
                                onChanged = { reload() },
                                onOpenPagePlans = { section = "plans"; chatGroupId = null })
                        }
                    }
                    if (section == "plans") {
                        val selectedGroup = groups.firstOrNull { it.groupId == selectedGroupId }
                        if (selectedGroup != null && selectedGroup.status == "active") {
                            P32KhatmaVisualScreen(
                                token = active.token, groupId = selectedGroupId,
                                leader = selectedGroup.role == "leader",
                                userId = active.userId,
                                onRead = { first, last ->
                                    if (first != null && last != null) onOpenPages(first, last)
                                    else onOpenQuran(null)
                                }
                            )
                        }
                    }
                }
                items(groups.filter {
                    (section == "khatmas" && it.groupId !in archivedIds && it.groupName.contains(groupQuery.trim(), ignoreCase = true)) ||
                    (section == "group" && it.groupId == selectedGroupId)
                }, key = { it.groupId }) { item ->
                    if (section == "khatmas") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        P25GroupTile(item, menu = {
                            P56GroupMenu(item,
                                onArchive = {
                                    archivedIds = archivedIds + item.groupId
                                    P56Archive.save(context, active.userId, archivedIds)
                                    message = "تمت أرشفة «${item.groupName}». هتلاقيها في حسابي ← الختمات المؤرشفة"
                                },
                                onLeaveOrClose = { p56Manage = item to (item.role == "leader" && item.status == "active") })
                        }) {
                            selectedGroupId = item.groupId
                            p69PrivatePeer = null
                            section = "group"
                            groupTab = if (item.role == "leader") "leader" else "read"
                            groupInfo = null
                            pending = emptyList()
                            chatGroupId = null
                            message = ""
                            if (item.status == "active") {
                                runTask {
                                    groupInfo = withContext(Dispatchers.IO) {
                                        if (item.role == "leader") SecureV2Api.groupStatus(active.token, item.groupId)
                                        else SecureV2Api.membersDirectory(active.token, item.groupId, item.groupName)
                                    }
                                    if (item.role == "leader") {
                                        pending = withContext(Dispatchers.IO) { SecureV2Api.pending(active.token, item.groupId) }
                                    }
                                }
                            }
                        }
                        } // group tile padding
                    } else {
                    P29GroupFrame(leader = item.role == "leader") {
                        Column(Modifier.padding(if (item.role == "leader") 0.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (groupTab == "read" && item.status == "active") {
                                P25MemberDashboard(item,
                                    onRead = { onOpenQuran(item.juz) },
                                    onPlans = { section = "schedule"; chatGroupId = null })
                            }
                            if (item.role == "leader" && groupTab == "leader" && item.status == "active") {
                                OutlinedButton(onClick = { section = "leaderSchedule"; chatGroupId = null },
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text("متابعة المتأخرات وإجراءات القائد")
                                }
                            }
                            if (item.status != "active") Text("طلب الانضمام قيد المراجعة")
                            if (section == "home") {
                                Button(onClick = { selectedGroupId = item.groupId; section = "group"; groupInfo = null; chatGroupId = null }) {
                                    Text(if (item.status == "active") "فتح المجموعة" else "متابعة طلب الانضمام")
                                }
                            } else {
                            if (groupTab == "read") {
                            if (item.assignmentId != null && !item.completed && item.status == "active") {
                                Button(onClick = { confirmTodayAssignment = item },
                                    enabled = !busy) { Text("تمت القراءة") }
                            } else if (item.completed) Text("✓ تمت القراءة")
                            } // existing full-juz actions remain on reading tab
                            if (groupTab == "members" && item.status == "active" && item.role != "leader") {
                                if (p69PrivatePeer != null) {
                                    P69PrivateChat(
                                        token = active.token, groupId = item.groupId,
                                        peer = p69PrivatePeer!!,
                                        onClose = { p69PrivatePeer = null })
                                } else P68MemberList(
                                    details = groupInfo?.takeIf { it.id == item.groupId },
                                    onRetry = {
                                        runTask {
                                            groupInfo = withContext(Dispatchers.IO) {
                                                if (item.role == "leader") SecureV2Api.groupStatus(active.token, item.groupId)
                                                else SecureV2Api.membersDirectory(active.token, item.groupId, item.groupName)
                                            }
                                        }
                                    },
                                    onMessage = { peerId, _ ->
                                        p69PrivatePeer = groupInfo?.members?.firstOrNull { it.id == peerId }
                                    },
                                )
                            }
                            if (item.status == "active" && groupTab == "chat") {
                                if (item.role == "leader") {
                                    P28ChatTitle(groupInfo?.members?.size, pinnedMessages)
                                } else {
                                    Text("محادثة المجموعة", style = MaterialTheme.typography.titleMedium)
                                }
                                if (chatGroupId == item.groupId) {
                                    if (item.role != "leader") {
                                        Text("محادثة ${item.groupName}", style = MaterialTheme.typography.titleMedium)
                                        Text("الرسائل للأعضاء المقبولين فقط", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (chatError.isNotBlank()) Text(chatError, color = MaterialTheme.colorScheme.error)
                                    if (pinnedMessages.isNotEmpty() && item.role != "leader") {
                                        Text("الرسائل المثبّتة", style = MaterialTheme.typography.titleSmall)
                                        pinnedMessages.forEach { pin -> Text("📌 " + pin) }
                                    }
                                    if (chatMessages.isEmpty()) Text("لا توجد رسائل بعد")
                                    chatMessages.forEach { chat ->
                                        P29GroupMessageFrame(
                                            leader = item.role == "leader",
                                            mine = chat.senderId == active.userId) {
                                            Column(Modifier.padding(11.dp)) {
                                                Text(chat.senderName, style = MaterialTheme.typography.labelLarge,
                                                    color = Color(0xFF075740))
                                                if (chat.replyToId != null) {
                                                    Text("رد على رسالة " + chat.replyToId)
                                                }
                                                Text(chat.message)
                                                if (item.role == "leader") {
                                                    Text(chat.createdAt.substringAfter('T', "").take(5),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.Gray)
                                                }
                                                if (chat.edited) Text("تم التعديل",
                                                    style = MaterialTheme.typography.labelSmall)
                                                if (chat.reactionText.isNotEmpty()) Text(chat.reactionText)
                                                Row {
                                                    TextButton(onClick = { replyToId = chat.id }) { Text("رد") }
                                                    Box {
                                                        TextButton(onClick = { actionMenu = chat.id }) { Text("•••") }
                                                        DropdownMenu(expanded = actionMenu == chat.id,
                                                            onDismissRequest = { actionMenu = null }) {
                                                            DropdownMenuItem(text = { Text("❤️ تفاعل") },
                                                                onClick = {
                                                                    actionMenu = null
                                                                    runTask {
                                                                        withContext(Dispatchers.IO) {
                                                                            SecureV2Api.reactMessage(active.token,
                                                                                item.groupId, chat.id, "❤️")
                                                                            chatMessages = SecureV2Api.messages(active.token, item.groupId)
                                                                        }
                                                                    }
                                                                })
                                                            if (chat.senderId == active.userId) {
                                                                DropdownMenuItem(text = { Text("تعديل") },
                                                                    onClick = {
                                                                        editText = chat.message
                                                                        editTarget = chat
                                                                        actionMenu = null
                                                                    })
                                                            }
                                                            DropdownMenuItem(text = { Text("حذف عندي") },
                                                                onClick = {
                                                                    actionMenu = null
                                                                    runTask {
                                                                        withContext(Dispatchers.IO) {
                                                                            SecureV2Api.deleteMessage(active.token,
                                                                                item.groupId, chat.id, false)
                                                                            chatMessages = SecureV2Api.messages(active.token, item.groupId)
                                                                        }
                                                                    }
                                                                })
                                                            if (chat.senderId == active.userId || item.role == "leader") {
                                                                DropdownMenuItem(text = { Text("حذف عند الجميع") },
                                                                    onClick = {
                                                                        actionMenu = null
                                                                        runTask {
                                                                            withContext(Dispatchers.IO) {
                                                                                SecureV2Api.deleteMessage(active.token,
                                                                                    item.groupId, chat.id, true)
                                                                                chatMessages = SecureV2Api.messages(active.token, item.groupId)
                                                                            }
                                                                        }
                                                                    })
                                                            }
                                                            if (item.role == "leader") {
                                                                DropdownMenuItem(text = { Text("تثبيت") },
                                                                    onClick = {
                                                                        actionMenu = null
                                                                        pinTarget = chat
                                                                    })
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (replyToId != null) TextButton(onClick = { replyToId = null }) {
                                        Text("الرد على الرسالة " + replyToId + " (إلغاء)")
                                    }
                                    val sendChat: () -> Unit = {
                                        val sending = chatText.trim()
                                        val sendingReply = replyToId
                                        if (sending.isNotBlank()) runTask {
                                            val fingerprint = sendingReply.toString() + "|" + sending
                                            val id = if (p82GroupPendingPayload == fingerprint &&
                                                p82GroupPendingId.isNotBlank()) p82GroupPendingId
                                                else UUID.randomUUID().toString().also {
                                                    p82GroupPendingPayload = fingerprint
                                                    p82GroupPendingId = it
                                                }
                                            // Only a user click sends POST. Never retry writes automatically.
                                            withContext(Dispatchers.IO) {
                                                SecureV2Api.sendMessage(
                                                    active.token, item.groupId, sending,
                                                    sendingReply, id)
                                            }
                                            p82GroupPendingId = ""
                                            p82GroupPendingPayload = ""
                                            chatText = ""
                                            replyToId = null
                                            chatMessages = withContext(Dispatchers.IO) {
                                                SecureV2Api.messages(active.token, item.groupId)
                                            }
                                        }
                                    }
                                    if (item.role == "leader") {
                                        P29LeaderChatComposer(chatText,
                                            { if (it.length <= 1000) chatText = it }, busy, sendChat)
                                    } else {
                                        OutlinedTextField(chatText, { if (it.length <= 1000) chatText = it },
                                            label = { Text("اكتب رسالة للمجموعة") },
                                            modifier = Modifier.fillMaxWidth(), maxLines = 3)
                                        Button(onClick = sendChat, enabled = !busy && chatText.isNotBlank()) {
                                            Text("إرسال")
                                        }
                                    }
                                }
                            }
                            if (item.role == "leader" && item.status == "active") {
                                val liveGroup = groupInfo?.takeIf { it.id == item.groupId }
                                val respond: (Long, Boolean) -> Unit = { memberId, accept ->
                                    runTask {
                                        withContext(Dispatchers.IO) {
                                            SecureV2Api.approve(active.token, item.groupId, memberId, accept)
                                        }
                                        groupInfo = withContext(Dispatchers.IO) {
                                            SecureV2Api.groupStatus(active.token, item.groupId)
                                        }
                                        pending = withContext(Dispatchers.IO) {
                                            SecureV2Api.pending(active.token, item.groupId)
                                        }
                                        message = if (accept) "تم قبول العضو" else "تم رفض طلب الانضمام"
                                    }
                                }
                                when (groupTab) {
                                    "leader" -> P28LeaderHome(
                                        item, liveGroup, pending, active.token,
                                        onCopy = { copyCode(it) },
                                        onShare = { shareCode(item.groupName, it) },
                                        onManage = { groupTab = "leader_plans" }
                                    )
                                    "leader_plans" -> P28LeaderDistribution(
                                        item, liveGroup, active.token,
                                        onCopy = { copyCode(it) },
                                        onNewPlan = { section = "plans"; chatGroupId = null },
                                        onEditDistribution = { section = "plans"; chatGroupId = null }
                                    )
                                    "leader_members" -> P28LeaderMembers(
                                        liveGroup, pending, busy,
                                        token = active.token, groupId = item.groupId,
                                        onApprove = respond,
                                        onEdit = { section = "plans"; chatGroupId = null },
                                        onInvite = {
                                            liveGroup?.let { shareCode(item.groupName, it.inviteCode) }
                                        }
                                    )
                                }
                            }
                            } // All existing read/chat handlers remain on their tabs.
                        }
                    }
                    } // grouped details, not dashboard
                }
            }
        }
        } // other screens retain their existing scroll/layout
    }
    }
}
