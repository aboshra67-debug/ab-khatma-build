package com.ab.khatma.secure

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.ab.khatma.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Google account -> Google credential -> Firebase Auth -> verified server ID token.
 *  Only public Firebase client identifiers are stored in BuildConfig. No service keys.
 *  The backend independently verifies the signed Firebase token before issuing a Khatma session.
 */
object KhatmaGoogleSignIn {
    val isConfigured: Boolean
        get() = BuildConfig.KHATMA_GOOGLE_WEB_CLIENT_ID
            .endsWith(".apps.googleusercontent.com")

    private fun firebaseApp(context: Context): FirebaseApp {
        val opts = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.KHATMA_FIREBASE_APP_ID)
            .setApiKey(BuildConfig.KHATMA_FIREBASE_API_KEY)
            .setProjectId(BuildConfig.KHATMA_FIREBASE_PROJECT_ID)
            .build()
        return FirebaseApp.getApps(context).firstOrNull { it.name == "KHATMA_AUTH" }
            ?: synchronized(this) {
                FirebaseApp.getApps(context).firstOrNull { it.name == "KHATMA_AUTH" }
                    ?: FirebaseApp.initializeApp(context.applicationContext, opts, "KHATMA_AUTH")
            }
    }

    suspend fun firebaseIdToken(context: Context): String {
        check(isConfigured) {
            "تسجيل Google يحتاج ملف Firebase محدّث يحتوي على Web OAuth Client ID. فعّل Google من Firebase ثم حمّل الإعدادات الجديدة."
        }
        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(BuildConfig.KHATMA_GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val response = CredentialManager.create(context).getCredential(
            context = context, request = request
        )
        val credential = response.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "لم يتم اختيار حساب Google صالح." }
        val googleToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val auth = FirebaseAuth.getInstance(firebaseApp(context))
        val result = auth.signInWithCredential(
            GoogleAuthProvider.getCredential(googleToken, null)
        ).await()
        val firebaseUser = result.user ?: error("تعذر التحقق من حساب Google في Firebase")
        return firebaseUser.getIdToken(true).await().token
            ?: error("تعذر استلام رمز المصادقة من Firebase")
    }
}
