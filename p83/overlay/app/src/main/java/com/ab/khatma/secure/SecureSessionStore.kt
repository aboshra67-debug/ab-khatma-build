package com.ab.khatma.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Existing preferences/Keystore identity; bearer credentials always encrypted. */
class SecureSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("khatma_v2_session", Context.MODE_PRIVATE)

    fun load(): Session? = synchronized(lock) {
        val encoded = prefs.getString("token", null) ?: return@synchronized null
        val userId = prefs.getLong("user_id", 0L)
        if (userId <= 0L) return@synchronized null
        try {
            val localId = prefs.getString("local_id", null) ?: UUID.randomUUID().toString().also {
                check(prefs.edit().putString("local_id", it).commit())
            }
            // VC106 stored only the access token in this same encrypted field.
            val token = decrypt(encoded)
            val extra = prefs.getString("renewal", null)?.let { JSONObject(decrypt(it)) }
            Session(userId, prefs.getString("user_name", "").orEmpty(), token,
                sessionId = extra?.optString("session_id").orEmpty(),
                refreshToken = extra?.optString("refresh_token").orEmpty(),
                revokeToken = extra?.optString("revoke_token").orEmpty(),
                refreshExpiresAt = extra?.optLong("refresh_expires_at") ?: 0L,
                localId = localId, refreshRequestId = extra?.optString("request_id").orEmpty())
        } catch (_: Exception) {
            clear() // Never fall back to plaintext.
            null
        }
    }
    fun save(value: Session) = synchronized(lock) { write(value) }

    internal fun replace(expected: Session, next: Session): Boolean = synchronized(lock) {
        val current = load() ?: return@synchronized false
        if (current.localId != expected.localId || current.token != expected.token ||
            current.refreshRequestId != expected.refreshRequestId) return@synchronized false
        write(next.copy(userName = current.userName))
        true
    }
    fun updateName(localId: String, name: String): Session? = synchronized(lock) {
        val current = load()?.takeIf { it.localId == localId } ?: return@synchronized null
        current.copy(userName = name).also { write(it) }
    }
    fun clearIfCurrent(localId: String): Boolean = synchronized(lock) {
        if (load()?.localId != localId) return@synchronized false
        clear()
        true
    }
    /** Immediate local logout; durable encrypted queue handles offline revocation. */
    fun logout(localId: String): Boolean = synchronized(lock) {
        val current = load()?.takeIf { it.localId == localId } ?: return@synchronized false
        val queued = pendingLogouts().toMutableList()
        if (current.sessionId.isNotBlank() && current.revokeToken.isNotBlank()) queued.add(current)
        check(prefs.edit().remove("token").remove("renewal").remove("user_id")
            .remove("user_name").remove("local_id")
            .putString("pending_logouts", encrypt(JSONArray(queued.map { logoutJson(it) }).toString()))
            .commit()) { "تعذر تسجيل الخروج بأمان" }
        changes.value += 1L
        true
    }
    internal fun pendingLogouts(): List<Session> = synchronized(lock) {
        val encoded = prefs.getString("pending_logouts", null) ?: return@synchronized emptyList()
        val array = JSONArray(decrypt(encoded))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Session(o.getLong("user_id"), "", "", sessionId = o.getString("session_id"),
                revokeToken = o.getString("revoke_token"), refreshExpiresAt = o.getLong("expires_at"),
                localId = o.getString("local_id"))
        }
    }
    internal fun removePendingLogout(localId: String) = synchronized(lock) {
        val pending = pendingLogouts().filterNot { it.localId == localId }
        check(prefs.edit().putString("pending_logouts",
            encrypt(JSONArray(pending.map { logoutJson(it) }).toString())).commit())
    }
    fun clear() = synchronized(lock) {
        check(prefs.edit().remove("token").remove("renewal").remove("user_id")
            .remove("user_name").remove("local_id").commit())
        changes.value += 1L
    }
    private fun write(value: Session) {
        require(value.userId > 0 && value.token.isNotBlank() && value.localId.isNotBlank())
        val renewal = JSONObject().put("session_id", value.sessionId)
            .put("refresh_token", value.refreshToken).put("revoke_token", value.revokeToken)
            .put("refresh_expires_at", value.refreshExpiresAt).put("request_id", value.refreshRequestId)
        check(prefs.edit().putLong("user_id", value.userId).putString("user_name", value.userName)
            .putString("local_id", value.localId).putString("token", encrypt(value.token))
            .putString("renewal", encrypt(renewal.toString())).commit()) { "تعذر حفظ الجلسة الآمنة" }
        changes.value += 1L
    }
    private fun logoutJson(s: Session) = JSONObject().put("user_id", s.userId)
        .put("session_id", s.sessionId).put("revoke_token", s.revokeToken)
        .put("expires_at", s.refreshExpiresAt).put("local_id", s.localId)

    private fun encrypt(raw: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(raw.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    private fun secretKey(): SecretKey {
        val alias = "ab_khatma_v2_session_aes"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey()
    }
    data class Session(val userId: Long, val userName: String, val token: String,
        val sessionId: String = "", val refreshToken: String = "", val revokeToken: String = "",
        val refreshExpiresAt: Long = 0L, val localId: String = UUID.randomUUID().toString(),
        val refreshRequestId: String = "")
    companion object {
        internal val lock = Any()
        internal val changes = MutableStateFlow(0L)
    }
}
