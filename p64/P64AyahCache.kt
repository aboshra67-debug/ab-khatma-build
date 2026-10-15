package com.ab.khatma.quran

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * P64: verse recitations are downloaded a few verses ahead into a small cache folder, so every verse starts from the
 * phone's own storage and the recitation never waits for the network. The cache is limited and cleaned automatically.
 */
object P64AyahCache {
    private const val MAX_BYTES = 40L * 1024L * 1024L
    private val pool = Executors.newFixedThreadPool(2)
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var dir: File? = null

    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.cacheDir, "p64_ayah")
        d.mkdirs()
        dir = d
    }

    private fun fileFor(url: String): File? {
        val d = dir ?: return null
        val name = url.removePrefix("https://").replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(d, name)
    }

    /** The downloaded copy of this verse, or null when it is not on the phone yet. */
    fun cached(url: String): File? {
        val f = fileFor(url) ?: return null
        return if (f.exists() && f.length() > 1024L) f else null
    }

    /** Downloads the verse in the background (nothing happens when it is already there or already coming). */
    fun prefetch(url: String) {
        val f = fileFor(url) ?: return
        if (f.exists() && f.length() > 1024L) {
            runCatching { f.setLastModified(System.currentTimeMillis()) }
            return
        }
        if (!inFlight.add(url)) return
        pool.execute {
            val tmp = File(f.parentFile, f.name + ".part")
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ABKhatma")
                }
                if (conn.responseCode == 200) {
                    val expected = conn.contentLengthLong
                    conn.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                    if (tmp.length() > 1024L && (expected <= 0L || tmp.length() == expected)) {
                        tmp.renameTo(f)
                        trim()
                    } else {
                        tmp.delete()
                    }
                }
            } catch (_: Exception) {
                runCatching { tmp.delete() }
            } finally {
                runCatching { conn?.disconnect() }
                inFlight.remove(url)
            }
        }
    }

    private fun trim() {
        val d = dir ?: return
        val files = d.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES * 8 / 10) break
            val size = f.length()
            if (f.delete()) total -= size
        }
    }
}
