package com.ab.khatma.secure

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Runs with a normal JDK before the Android build; tests real Kotlin nonce logic. */
fun main() {
    val token = "unit-test-token-only"
    val conversation = "private:222:333"
    val body = "السلام عليكم"
    val original = P82SendNonce.begin(token, conversation, body)
    check(original.matches(Regex("[A-Za-z0-9_-]{16,80}"))) { "Invalid request ID" }
    check(P82SendNonce.begin(token, conversation, body) == original) {
        "Connection failure MUST reuse the original request ID"
    }
    check(P82SendNonce.begin("a different session", conversation, body) != original) {
        "Different accounts must not share request IDs"
    }
    check(P82SendNonce.begin(token, "private:222:444", body) != original) {
        "Different conversations must not share request IDs"
    }
    val modified = P82SendNonce.begin(token, conversation, body + "!")
    check(modified != original) { "Modified message must use a new ID" }
    P82SendNonce.confirmed(token, conversation, original)
    check(P82SendNonce.begin(token, conversation, body + "!") == modified) {
        "Confirming an outdated ID must not clear current pending attempt"
    }
    P82SendNonce.confirmed(token, conversation, modified)
    check(P82SendNonce.begin(token, conversation, body + "!") != modified) {
        "A genuinely new identical send after success needs a new ID"
    }
    val key = "group:998"
    val workers = Executors.newFixedThreadPool(8)
    try {
        val tasks = (1..20).map {
            Callable { P82SendNonce.begin(token, key, "text with reply: 18") }
        }
        val ids = workers.invokeAll(tasks).map { it.get() }
        check(ids.toSet().size == 1) { "Concurrent retries MUST use one request ID" }
    } finally {
        workers.shutdownNow()
    }
    repeat(100) {
        P82SendNonce.begin(token, "group:$it", "many conversations, bounded memory")
    }
    check(P82SendNonce.begin(token, "group:99", "many conversations, bounded memory").isNotBlank())
    println("P82 VC106 NONCE PASS: retry, account and chat isolation, changed text, successful reset, 20 parallel calls, bounds")
}
