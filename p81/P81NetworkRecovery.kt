package com.ab.khatma.secure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** VC105: repeat ONLY read-only requests after transient transport errors.
 * No retries of POST/PUT/PATCH/DELETE (a previous attempt may already be committed).
 * No timers, background workers or new Android permissions.
 */
internal object P81NetworkRecovery {
    suspend fun <T> readOnly(call: () -> T): T {
        var retry = 0
        while (true) {
            try {
                return withContext(Dispatchers.IO) { call() }
            } catch (error: SecureV2Api.TransientNetworkException) {
                if (retry >= 1) throw error
                retry += 1
                delay(900L)
            }
        }
    }
}
