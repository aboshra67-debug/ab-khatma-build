#!/usr/bin/env python3
"""P79 / VC103 Safe Patch: add FCM push without touching any Quran assets/reading code.
Requires the P78 VC102 rebuilt source with exact guarded anchor points.
"""
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
app = root / "app"
java = app / "src/main/java/com/ab/khatma"

def patch(path, old, new):
    p = root / path
    content = p.read_text(encoding="utf-8")
    assert content.count(old) == 1, f"VC103 anchor mismatch: {path}: {old[:75]!r}"
    p.write_text(content.replace(old, new, 1), encoding="utf-8")

def add_once(path, marker, text):
    p = root / path
    content = p.read_text(encoding="utf-8")
    assert content.count(marker) == 1, (path, marker)
    p.write_text(content.replace(marker, marker + text, 1), encoding="utf-8")

source = Path(__file__).resolve().with_name("P79Push.kt")
assert source.is_file()
target = java / "secure/P79Push.kt"
assert not target.exists(), "Refuse overwriting existing FCM implementation"
target.write_bytes(source.read_bytes())

patch("app/build.gradle.kts",
      '    implementation("com.google.firebase:firebase-auth")',
      '    implementation("com.google.firebase:firebase-auth")\n    implementation("com.google.firebase:firebase-messaging")')

patch("app/build.gradle.kts", 'versionCode = 102', 'versionCode = 103')
patch("app/build.gradle.kts",
      'versionName = "0.19.73-p78-restore-ayah-actions-safe-qa"',
      'versionName = "0.19.74-p79-private-fcm-push-safe-qa"')

patch("app/src/main/AndroidManifest.xml",
      '        <receiver android:name=".prayer.AdhanAlarmReceiver"',
      '''        <service android:name=".secure.P79FirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        <receiver android:name=".prayer.AdhanAlarmReceiver"''')

patch("app/src/main/java/com/ab/khatma/secure/SecureV2Api.kt",
      '    fun sendPrivate(token: String, groupId: Long, peerId: Long, text: String) {',
      '''    fun registerPushDevice(token: String, deviceToken: String) {
        request("/push/devices", token, "POST", JSONObject().put("token", deviceToken))
    }
    fun unregisterPushDevice(token: String, deviceToken: String) {
        request("/push/devices", token, "DELETE", JSONObject().put("token", deviceToken))
    }
    fun sendPrivate(token: String, groupId: Long, peerId: Long, text: String) {''')

patch("app/src/main/java/com/ab/khatma/KhatmaApplication.kt",
      '        super.onCreate()\n',
      '        super.onCreate()\n        runCatching { com.ab.khatma.secure.P79Push.initialize(this) }\n')

patch("app/src/main/java/com/ab/khatma/MainActivity.kt",
      'import android.Manifest\n', 'import android.Manifest\nimport android.content.Intent\n')
patch("app/src/main/java/com/ab/khatma/MainActivity.kt",
      'import com.ab.khatma.ui.screens.AppRoot\n',
      'import com.ab.khatma.ui.screens.AppRoot\nimport com.ab.khatma.secure.P79Push\n')
patch("app/src/main/java/com/ab/khatma/MainActivity.kt",
      'class MainActivity : ComponentActivity() {\n',
      '''class MainActivity : ComponentActivity() {
    private var p79Intent by mutableStateOf<Intent?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        p79Intent = intent
    }
''')
patch("app/src/main/java/com/ab/khatma/MainActivity.kt",
      '        super.onCreate(savedInstanceState)\n',
      '''        super.onCreate(savedInstanceState)
        p79Intent = intent
''')
patch("app/src/main/java/com/ab/khatma/MainActivity.kt",
      '                                vm = vm,\n',
      '''                                vm = vm,
                                pushTarget = P79Push.targetFromIntent(p79Intent),
                                onPushTargetConsumed = {
                                    P79Push.clearTarget(p79Intent)
                                    p79Intent = null
                                },
''')

patch("app/src/main/java/com/ab/khatma/ui/screens/AppRoot.kt",
      'import com.ab.khatma.secure.SecureV2Screen\n',
      'import com.ab.khatma.secure.SecureV2Screen\nimport com.ab.khatma.secure.P79PrivateTarget\n')
patch("app/src/main/java/com/ab/khatma/ui/screens/AppRoot.kt",
      'fun AppRoot(vm: AppViewModel, themeStyle: KhatmaThemeStyle, onThemeStyleChange: (KhatmaThemeStyle) -> Unit) {',
      '''fun AppRoot(vm: AppViewModel, themeStyle: KhatmaThemeStyle,
    onThemeStyleChange: (KhatmaThemeStyle) -> Unit,
    pushTarget: P79PrivateTarget? = null, onPushTargetConsumed: () -> Unit = {}) {''')
patch("app/src/main/java/com/ab/khatma/ui/screens/AppRoot.kt",
      '    vm.error?.let { message ->',
      '''    LaunchedEffect(pushTarget?.messageId) {
        if (pushTarget != null) screen = Screen.SecureV2
    }

    vm.error?.let { message ->''')
patch("app/src/main/java/com/ab/khatma/ui/screens/AppRoot.kt",
      '            onOpenPages = { start, end -> screen = Screen.SecureQuranPages(start, end) })',
      '''            onOpenPages = { start, end -> screen = Screen.SecureQuranPages(start, end) },
            p79Target = pushTarget, p79TargetConsumed = onPushTargetConsumed)''')

patch("app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt",
      '                   onOpenPages: (Int, Int) -> Unit) {',
      '''                   onOpenPages: (Int, Int) -> Unit,
                   p79Target: P79PrivateTarget? = null,
                   p79TargetConsumed: () -> Unit = {}) {''')
patch("app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt",
      '    var p69PrivatePeer by remember { mutableStateOf<SecureV2Api.MemberInfo?>(null) }',
      '''    var p69PrivatePeer by remember { mutableStateOf<SecureV2Api.MemberInfo?>(null) }
    var p79NotificationPeer by remember { mutableStateOf<SecureV2Api.MemberInfo?>(null) }
    var p79NotificationGroup by remember { mutableLongStateOf(0L) }''')
patch("app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt",
      '    // P36: render the native prayer screen in the current secure session,',
      '''    // VC103: refresh device registration only on session changes or Firebase token rotation.
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

    // P36: render the native prayer screen in the current secure session,''')

# Both explicit logouts and account deletion attempt server-side revocation; on-device
# user-id checks prevent cross-account notifications even when offline/unregister fails.
patch("app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt",
      '                                    SecureReminderWorker.cancel(context)\n                                    store.clear()',
      '                                    SecureReminderWorker.cancel(context)\n                                    P79Push.unregister(context, active)\n                                    store.clear()')
patch("app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt",
      '                                SecureReminderWorker.cancel(context)\n                                store.clear()',
      '                                SecureReminderWorker.cancel(context)\n                                P79Push.unregister(context, active)\n                                store.clear()')

print("VC103 Safe Patch applied: FCM bridge, device register/unregister, notification deep link; Quran untouched")
