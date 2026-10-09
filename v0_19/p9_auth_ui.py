"""AB Khatma V0.19 P9: safe login UX and Google OAuth guards, applied after P8."""
from pathlib import Path
root = Path("app/src/main/java/com/ab/khatma/secure")
def patch(name, old, new):
    f=Path(name)
    s=f.read_text(encoding="utf-8")
    assert s.count(old)==1, f"Unsafe patch anchor: {f} {old[:75]!r} ({s.count(old)})"
    f.write_text(s.replace(old,new,1),encoding="utf-8")

patch("app/build.gradle.kts",
    'versionCode = 24\n        versionName = "0.19.0-google-preview"',
    'versionCode = 25\n        versionName = "0.19.1-google-ui-safe"')

patch(root/"KhatmaGoogleSignIn.kt",
'''    val isConfigured: Boolean
        get() = BuildConfig.KHATMA_GOOGLE_WEB_CLIENT_ID
            .endsWith(".apps.googleusercontent.com")''',
'''    val isConfigured: Boolean
        get() = BuildConfig.KHATMA_FIREBASE_PROJECT_ID == "ab-khatma" &&
            BuildConfig.KHATMA_FIREBASE_APP_ID.startsWith("1:") &&
            BuildConfig.KHATMA_FIREBASE_API_KEY.startsWith("AIza") &&
            BuildConfig.KHATMA_GOOGLE_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")''')

p=root/"KhatmaPolishedPanels.kt"
patch(p,'import androidx.compose.ui.graphics.Color',
    'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Brush\nimport androidx.compose.foundation.Image\nimport androidx.compose.ui.res.painterResource\nimport androidx.compose.ui.layout.ContentScale')
patch(p,'''        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Card(''',
'''        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(''')
patch(p,'''            colors = CardDefaults.cardColors(containerColor = khatmaGreen)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),''',
'''            colors = CardDefaults.cardColors(containerColor = khatmaGreen)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(khatmaGreen, Color(0xFF0A432E))))
                    .padding(horizontal = 18.dp, vertical = 19.dp),''')
patch(p,'''                        Text("۞", style = MaterialTheme.typography.headlineMedium,
                             color = khatmaGreen)''',
'''                        Image(
                            painter = painterResource(com.ab.khatma.R.drawable.ab_khatma_mark),
                            contentDescription = "شعار ختمة AB",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(45.dp)
                        )''')
patch(p,'''        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(13.dp)
        ) { Text("G  المتابعة باستخدام Google") }''',
'''        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !busy && googleReady,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("G", fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                Text("المتابعة بحساب Google", fontWeight = FontWeight.SemiBold)
            }
        }''')
patch(p,'''            Text("ينقص إعداد Web OAuth من مشروع Firebase لإتاحة تسجيل Google.",''',
'''            Text("Google غير متاح مؤقتًا: يلزم تفعيل OAuth وإضافة بصمة التطبيق في Firebase. يمكنك الدخول برقم الحساب.",''')

u=root/"SecureV2Screen.kt"
patch(u,"import androidx.compose.material3.*",
'''import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.credentials.exceptions.GetCredentialCancellationException''')
patch(u,"var createAccount by remember { mutableStateOf(true) }",
       "var createAccount by remember { mutableStateOf(false) }")
patch(u,'''            catch (e: Exception) { message = e.message ?: "تعذر الاتصال بالخادم" }
            finally { busy = false }''',
'''            catch (_: GetCredentialCancellationException) {
                message = ""
            }
            catch (e: Exception) { message = e.message ?: "تعذر الاتصال بالخادم" }
            finally { busy = false }''')
patch(u,'''                        icon = { Text("⌂") }, label = { Text("الرئيسية") }''',
'''                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("الرئيسية") }''')
patch(u,'''                        icon = { Text("۞") }, label = { Text("المصحف") }''',
'''                        icon = { Icon(Icons.Default.Book, contentDescription = null) },
                        label = { Text("المصحف") }''')
patch(u,'''                        icon = { Text("●") }, label = { Text("حسابي") }''',
'''                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("حسابي") }''')
patch(u,'''                        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("ربط Google بهذا الحساب دون فقد مجموعاتي")''',
'''                        }, enabled = !busy && KhatmaGoogleSignIn.isConfigured,
                           modifier = Modifier.fillMaxWidth()) {
                            Text("ربط Google بهذا الحساب دون فقد مجموعاتي")''')
v=Path("tools/verify_project.py")
s=v.read_text(encoding="utf-8")
assert "versionCode 24" in s and "0.19.0-google-preview" in s
s=s.replace("versionCode 24","versionCode 25").replace("versionCode = 24","versionCode = 25")
s=s.replace("0.19.0-google-preview","0.19.1-google-ui-safe")
v.write_text(s,encoding="utf-8")
assert "enabled = !busy && googleReady" in p.read_text()
assert "GetCredentialCancellationException" in u.read_text()
assert "linkGoogle(active.token, firebaseToken)" in u.read_text()
assert "firebase_id_token" in (root/"SecureV2Api.kt").read_text()
print("PASS P9: Google guard, cancellation, login UI, navigation; data untouched.")
