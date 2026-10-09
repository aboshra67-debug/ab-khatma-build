"""V0.19 P10: enable supplied, verified Firebase preview OAuth without touching data."""
from pathlib import Path
root=Path(".")
gradle=root/"app/build.gradle.kts"
source=gradle.read_text(encoding="utf-8")
old='versionCode = 25\n        versionName = "0.19.1-google-ui-safe"'
new='versionCode = 26\n        versionName = "0.19.2-google-live-oauth"'
assert source.count(old)==1, "Unexpected baseline; refusing unsafe P10 patch"
assert 'applicationIdSuffix = ".preview"' in source
assert 'buildConfigField("String", "KHATMA_GOOGLE_WEB_CLIENT_ID"' in source
gradle.write_text(source.replace(old,new,1),encoding="utf-8")
verify=root/"tools/verify_project.py"
test=verify.read_text(encoding="utf-8")
assert "versionCode 25" in test and "0.19.1-google-ui-safe" in test
test=test.replace("versionCode 25","versionCode 26").replace("versionCode = 25","versionCode = 26")
test=test.replace("0.19.1-google-ui-safe","0.19.2-google-live-oauth")
verify.write_text(test,encoding="utf-8")
google=root/"app/src/main/java/com/ab/khatma/secure/KhatmaGoogleSignIn.kt"
assert "GoogleAuthProvider.getCredential(googleToken, null)" in google.read_text()
assert "firebaseUser.getIdToken(true)" in google.read_text()
server=root/"app/src/main/java/com/ab/khatma/secure/SecureV2Api.kt"
assert '"/auth/google"' in server.read_text() and '"firebase_id_token"' in server.read_text()
print("PASS P10: OAuth client and Firebase preview will be loaded at Gradle build time. Existing accounts and groups unmodified.")
