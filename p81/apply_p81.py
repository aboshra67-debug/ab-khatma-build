#!/usr/bin/env python3
"""VC105 guarded, additive auto-recovery for transient reads in Secure V2 UI only.
No retries for mutation calls; no changes to user data, Quran, prayer or notification schedules.
"""
import sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
base = root / "app/src/main/java/com/ab/khatma/secure"

def modify(rel, old, new):
    p = root / rel
    src = p.read_text(encoding="utf-8")
    assert src.count(old) == 1, f"VC105 incompatible source: {rel}: {old[:110]!r} count={src.count(old)}"
    p.write_text(src.replace(old,new,1), encoding="utf-8")

api = "app/src/main/java/com/ab/khatma/secure/SecureV2Api.kt"
ui = "app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt"

modify(api,
'    class SessionExpiredException : IllegalStateException("انتهت جلسة الدخول. سجل الدخول مرة أخرى بنفس الحساب؛ مجموعاتك محفوظة.")',
'''    class SessionExpiredException : IllegalStateException("انتهت جلسة الدخول. سجل الدخول مرة أخرى بنفس الحساب؛ مجموعاتك محفوظة.")
    /** Only transport/gateway failures are safe to retry for read-only requests. */
    class TransientNetworkException(message: String, cause: Throwable) : IllegalStateException(message, cause)''')
modify(api,
'                if (code == 401 && token != null) throw SessionExpiredException()\n                throw IllegalStateException(reason)',
'''                if (code == 401 && token != null) throw SessionExpiredException()
                if (code in 502..504) throw TransientNetworkException(
                    "السيرفر مشغول شوية. هنحاول نتصل تاني تلقائيًا.", java.io.IOException("gateway $code"))
                throw IllegalStateException(reason)''')
repl = [
('throw IllegalStateException("انتهت مهلة الاتصال. جرّب بيانات الموبايل أو شبكة أخرى ثم أعد المحاولة.", ex)',
 'throw TransientNetworkException("الاتصال اتأخر شوية. هنحاول تاني تلقائيًا؛ مش محتاج تقفل التطبيق.", ex)'),
('throw IllegalStateException("تعذر الوصول إلى السيرفر. تحقق من اتصال الإنترنت.", ex)',
 'throw TransientNetworkException("الاتصال بالإنترنت مش متاح حاليًا. هنحاول نرجع تلقائيًا.", ex)'),
('throw IllegalStateException("تعذر الاتصال بالسيرفر. جرّب شبكة أخرى ثم أعد المحاولة.", ex)',
 'throw TransientNetworkException("الاتصال بالسيرفر اتقطع مؤقتًا. هنحاول تاني تلقائيًا.", ex)'),
('throw IllegalStateException("انقطع الاتصال بالسيرفر. تحقق من الشبكة وحاول مرة أخرى.", ex)',
 'throw TransientNetworkException("الشبكة فصلت لحظة. هنحاول نوصل تلقائيًا.", ex)'),
('throw IllegalStateException("تعذر التواصل مع السيرفر. تأكد من الإنترنت وحاول مرة أخرى.", ex)',
 'throw TransientNetworkException("الاتصال اتعطل مؤقتًا. هنحاول تاني تلقائيًا.", ex)'),
]
for a,b in repl: modify(api,a,b)

modify(ui, "import androidx.compose.ui.platform.LocalContext",
'''import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner''')
modify(ui,
'    var initialGroupLoadWarningShown by remember(session?.token) { mutableStateOf(false) }',
'''    var initialGroupLoadWarningShown by remember(session?.token) { mutableStateOf(false) }
    // VC105: transient network failures recover while app remains open.
    var p81PendingNetworkRecovery by remember(session?.token) { mutableStateOf(false) }
    var p81RetrySignal by remember(session?.token) { mutableIntStateOf(0) }
    var p81InForeground by remember { mutableStateOf(true) }''')
modify(ui,
'    var p56Manage by remember { mutableStateOf<Pair<SecureV2Api.TodayItem, Boolean>?>(null) }',
'''    var p56Manage by remember { mutableStateOf<Pair<SecureV2Api.TodayItem, Boolean>?>(null) }
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
    }''')
modify(ui,
'            withContext(Dispatchers.IO) { SecureV2Api.today(s.token) }\n        } catch (error: Exception) {\n            if (!groupsLoaded) groupsLoadFailed = true',
'''            P81NetworkRecovery.readOnly { SecureV2Api.today(s.token) }
        } catch (error: Exception) {
            if (error is SecureV2Api.TransientNetworkException) p81PendingNetworkRecovery = true
            if (!groupsLoaded) groupsLoadFailed = true''')
modify(ui,
'        groupsLoadFailed = false\n        p41LastGroupsFetchTime = android.os.SystemClock.elapsedRealtime()',
'''        groupsLoadFailed = false
        p81PendingNetworkRecovery = false
        p41LastGroupsFetchTime = android.os.SystemClock.elapsedRealtime()''')
modify(ui,
'    LaunchedEffect(session?.token, section) {\n        val current = session ?: return@LaunchedEffect',
'''    LaunchedEffect(session?.token, section, p81RetrySignal) {
        val current = session ?: return@LaunchedEffect''')
modify(ui,
'        if (groupsLoaded && android.os.SystemClock.elapsedRealtime() - p41LastGroupsFetchTime < 120_000L) return@LaunchedEffect',
'''        if (groupsLoaded && !p81PendingNetworkRecovery &&
            android.os.SystemClock.elapsedRealtime() - p41LastGroupsFetchTime < 120_000L) return@LaunchedEffect''')
modify(ui,
'        runCatching { withContext(Dispatchers.IO) { SecureV2Api.today(current.token) } }\n            .onSuccess {',
'''        runCatching { P81NetworkRecovery.readOnly { SecureV2Api.today(current.token) } }
            .onSuccess {''')
modify(ui,
'                    initialGroupLoadWarningShown = false\n                    if (message.startsWith("تعذر تحديث المجموعات") || message.startsWith("تعذر الاتصال بالمجموعات")) message = ""',
'''                    initialGroupLoadWarningShown = false
                    p81PendingNetworkRecovery = false
                    if (message.startsWith("تعذر تحديث المجموعات") ||
                        message.startsWith("تعذر الاتصال بالمجموعات") ||
                        message.startsWith("الاتصال ") || message.startsWith("الشبكة ") ||
                        message.startsWith("السيرفر مشغول")) message = ""''')
modify(ui,
'                            groupsLoadFailed = false; message = ""\n                        } else showExpiredSession()',
'''                            groupsLoadFailed = false
                            p81PendingNetworkRecovery = false
                            message = ""
                        } else {
                            val refreshError = refreshedAttempt.exceptionOrNull()
                            if (refreshError is java.io.IOException ||
                                refreshError is SecureV2Api.TransientNetworkException ||
                                refreshError?.cause is java.io.IOException) {
                                // Never erase the saved session because Google/Firebase was offline.
                                if (!groupsLoaded) groupsLoadFailed = true
                                p81PendingNetworkRecovery = true
                                message = "الاتصال اتقطع وقت تجديد الجلسة. حسابك محفوظ، هنحاول تاني."
                            } else showExpiredSession()
                        }''')
modify(ui,
'                        val refreshed = runCatching {\n                            val idToken = KhatmaGoogleSignIn.firebaseIdToken(context)',
'''                        val refreshedAttempt = runCatching {
                            val idToken = KhatmaGoogleSignIn.firebaseIdToken(context)''')
modify(ui,
'                        }.getOrNull()\n                        if (refreshed != null && refreshed.userId == current.userId) {',
'''                        }
                        val refreshed = refreshedAttempt.getOrNull()
                        if (refreshed != null && refreshed.userId == current.userId) {''')
modify(ui,
'                    else -> {\n                        // Retain any previously loaded groups; never pretend 0 groups.',
'''                    else -> {
                        if (error is SecureV2Api.TransientNetworkException)
                            p81PendingNetworkRecovery = true
                        // Retain any previously loaded groups; never pretend 0 groups.''')
marker='    LaunchedEffect(chatGroupId, session?.token) {'
block='''    // VC105: retry only after a confirmed transient network failure.
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

'''
modify(ui,marker,block+marker)
modify(ui,
'        store.clear()\n        SecureReminderWorker.cancel(context)',
'''        store.clear()
        p81PendingNetworkRecovery = false
        SecureReminderWorker.cancel(context)''')
add = Path(__file__).with_name("P81NetworkRecovery.kt")
target=base/"P81NetworkRecovery.kt"
assert add.exists() and not target.exists(), "VC105 would overwrite existing recovery class"
target.write_bytes(add.read_bytes())
build="app/build.gradle.kts"
modify(build,"versionCode = 104","versionCode = 105")
modify(build,'versionName = "0.19.75-p80-personal-egyptian-notices-safe-qa"',
       'versionName = "0.19.76-p81-safe-auto-reconnect-qa"')
print("VC105 Safe Patch PASS: typed transient errors, read-only coroutine retry, foreground auto recovery, no new permissions or server changes")
