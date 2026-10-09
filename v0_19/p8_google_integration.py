"""Safe Android V0.19 Firebase Google wiring; keep password accounts and groups."""
from pathlib import Path
import os, shutil

root=Path("app/src/main/java/com/ab/khatma/secure")
source=Path(os.environ["GITHUB_WORKSPACE"])/"v0_19/KhatmaGoogleSignIn.kt"
assert source.is_file() and not (root/source.name).exists()
shutil.copyfile(source,root/source.name)

def patch(filename, old, new):
    path=Path(filename)
    text=path.read_text(encoding="utf-8")
    assert text.count(old)==1, "Unsafe patch anchor: "+filename+" "+old[:70]
    path.write_text(text.replace(old,new,1),encoding="utf-8")

g="app/build.gradle.kts"
patch(g, 'android {\n',
'''val firebaseProjectId = (project.findProperty("KHATMA_FIREBASE_PROJECT_ID") ?: "ab-khatma").toString()
val firebaseAppId = (project.findProperty("KHATMA_FIREBASE_APP_ID") ?: "").toString()
val firebaseApiKey = (project.findProperty("KHATMA_FIREBASE_API_KEY") ?: "").toString()
val googleWebClientId = (project.findProperty("KHATMA_GOOGLE_WEB_CLIENT_ID") ?: "").toString()
fun firebaseBuildValue(value: String) = "\\\"" + value.replace("\\\\", "\\\\\\\\")
    .replace("\\\"", "\\\\\\\"") + "\\\""

android {
''')
patch(g, '        versionCode = 23\n        versionName = "0.18.2-compact-ui"',
'''        versionCode = 24
        versionName = "0.19.0-google-preview"
        buildConfigField("String", "KHATMA_FIREBASE_PROJECT_ID", firebaseBuildValue(firebaseProjectId))
        buildConfigField("String", "KHATMA_FIREBASE_APP_ID", firebaseBuildValue(firebaseAppId))
        buildConfigField("String", "KHATMA_FIREBASE_API_KEY", firebaseBuildValue(firebaseApiKey))
        buildConfigField("String", "KHATMA_GOOGLE_WEB_CLIENT_ID", firebaseBuildValue(googleWebClientId))''')
patch(g, '    implementation("androidx.work:work-runtime:2.11.2")',
'''    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.work:work-runtime:2.11.2")''')

api=root/"SecureV2Api.kt"
patch(str(api), '    fun login(userId: Long, password: String): SecureSessionStore.Session {',
'''    fun googleLogin(firebaseToken: String): SecureSessionStore.Session {
        return authResponse(request("/auth/google", method = "POST",
            data = JSONObject().put("firebase_id_token", firebaseToken)))
    }
    fun linkGoogle(token: String, firebaseToken: String) {
        request("/auth/google/link", token, "POST",
            JSONObject().put("firebase_id_token", firebaseToken))
    }
    fun login(userId: Long, password: String): SecureSessionStore.Session {''')

panels=root/"KhatmaPolishedPanels.kt"
patch(str(panels),'    busy: Boolean,\n    onSubmit: () -> Unit\n) {',
'''    busy: Boolean,
    googleReady: Boolean,
    onGoogleSignIn: () -> Unit,
    onSubmit: () -> Unit
) {''')
patch(str(panels),'''        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),''',
'''        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(13.dp)
        ) { Text("G  المتابعة باستخدام Google") }
        if (!googleReady) {
            Text("ينقص إعداد Web OAuth من مشروع Firebase لإتاحة تسجيل Google.",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),''')
patch(str(panels),
    '            "تسجيل Google والهاتف غير مفعّل بعد؛ يحتاج إعداد المصادقة الخاص بالتطبيق.",',
    '            "الدخول بكلمة المرور متاح أيضًا، ويمكن ربط Google بحسابك من صفحة حسابي.",')

ui=root/"SecureV2Screen.kt"
patch(str(ui),'                        busy = busy,\n                        onSubmit = {',
'''                        busy = busy,
                        googleReady = KhatmaGoogleSignIn.isConfigured,
                        onGoogleSignIn = {
                            runTask {
                                val firebaseToken = KhatmaGoogleSignIn.firebaseIdToken(context)
                                val account = withContext(Dispatchers.IO) {
                                    SecureV2Api.googleLogin(firebaseToken)
                                }
                                store.save(account)
                                SecureReminderWorker.schedule(context)
                                session = account
                                password = ""
                                section = "home"
                                message = "مرحبًا بك في ختمة عبر Google"
                            }
                        },
                        onSubmit = {''')
patch(str(ui),'''                        )
                    }
                }
                item {
                    if (section == "group") {''',
'''                        )
                        OutlinedButton(onClick = {
                            runTask {
                                val firebaseToken = KhatmaGoogleSignIn.firebaseIdToken(context)
                                withContext(Dispatchers.IO) {
                                    SecureV2Api.linkGoogle(active.token, firebaseToken)
                                }
                                message = "تم ربط Google بحسابك الحالي ومجموعاتك"
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("ربط Google بهذا الحساب دون فقد مجموعاتي")
                        }
                    }
                }
                item {
                    if (section == "group") {''')

v=Path("tools/verify_project.py")
s=v.read_text(encoding="utf-8")
assert "versionCode 23" in s and "0.18.2-compact-ui" in s
s=s.replace("versionCode 23","versionCode 24").replace("versionCode = 23","versionCode = 24")
s=s.replace("0.18.2-compact-ui","0.19.0-google-preview")
v.write_text(s,encoding="utf-8")
assert "KhatmaGoogleSignIn.firebaseIdToken" in ui.read_text()
print("PASS: Google Credential Manager + Firebase Auth + secured V2 backend exchange wired.")
