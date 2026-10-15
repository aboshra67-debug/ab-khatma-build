package com.ab.khatma.quran

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** P66: start / end time (milliseconds) of every verse inside the full-surah recording of a reciter,
 * so a surah can play without any gap and the verse being read is still known.
 * Only reciters whose full-surah file comes from mp3quran.net have timings (same recording as the timings). */
object P66Timing {
    /** reciter id of the app -> reads id of mp3quran.net «ayat_timing» (the id of the same recording). */
    private val reads = mapOf(
        "husary_murattal" to 118,
        "husary_mujawwad" to 119,
        "minshawi_murattal" to 112,
        "ayyub" to 109,
        "alafasy" to 123,
        "idrees" to 12,
        "khalid_jalil" to 20,
    )

    /** ayah -> [start, end] in milliseconds */
    class Surah(val times: Map<Int, IntArray>) {
        val lastEnd: Int get() = times.values.maxOfOrNull { it[1] } ?: 0
    }

    private val memory = HashMap<String, Surah>()
    private val bad = HashSet<String>()
    private var dir: File? = null

    fun init(context: Context) {
        if (dir == null) dir = File(context.applicationContext.filesDir, "p66_timing").also { it.mkdirs() }
    }

    fun supported(r: P52Reciter): Boolean = reads.containsKey(r.id)

    private fun key(r: P52Reciter, surah: Int) = "${reads[r.id]}_$surah"

    /** The timings when they are already on the phone (memory or file); never touches the network. */
    @Synchronized
    fun get(r: P52Reciter, surah: Int): Surah? {
        if (!supported(r)) return null
        val k = key(r, surah)
        if (k in bad) return null
        memory[k]?.let { return it }
        val f = File(dir ?: return null, "$k.txt")
        if (!f.exists()) return null
        val parsed = runCatching {
            val map = HashMap<Int, IntArray>()
            f.readLines().forEach { line ->
                val p = line.split(',')
                if (p.size == 3) map[p[0].toInt()] = intArrayOf(p[1].toInt(), p[2].toInt())
            }
            map
        }.getOrNull()
        if (parsed.isNullOrEmpty() || !parsed.containsKey(1)) { runCatching { f.delete() }; return null }
        return Surah(parsed).also { memory[k] = it }
    }

    /** True when it is worth asking the network: supported, not known to be wrong, not yet on the phone. */
    @Synchronized
    fun needsFetch(r: P52Reciter, surah: Int): Boolean =
        supported(r) && key(r, surah) !in bad && get(r, surah) == null

    @Synchronized
    fun markBad(r: P52Reciter, surah: Int) {
        val k = key(r, surah)
        bad.add(k)
        memory.remove(k)
        runCatching { File(dir ?: return, "$k.txt").delete() }
    }

    /** Downloads the timings of one surah in the background; [done] gets true when they are now available. */
    fun fetch(r: P52Reciter, surah: Int, done: (Boolean) -> Unit) {
        val read = reads[r.id] ?: run { done(false); return }
        val k = key(r, surah)
        Thread {
            var ok = false
            try {
                val c = URL("https://mp3quran.net/api/v3/ayat_timing?surah=$surah&read=$read").openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 10000
                val text = try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }.trim()
                val arr = if (text.startsWith("[")) org.json.JSONArray(text) else {
                    val o = org.json.JSONObject(text)
                    var found: org.json.JSONArray? = null
                    val keys = o.keys()
                    while (keys.hasNext() && found == null) found = o.optJSONArray(keys.next())
                    found
                }
                val map = HashMap<Int, IntArray>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val a = e.optInt("ayah", -1)
                    val s = e.optInt("start_time", -1)
                    val en = e.optInt("end_time", -1)
                    if (a >= 1 && s >= 0 && en > s) map[a] = intArrayOf(s, en)
                }
                if (map.containsKey(1)) {
                    val d = dir
                    if (d != null) runCatching {
                        File(d, "$k.txt").writeText(map.toSortedMap().entries.joinToString("\n") { "${it.key},${it.value[0]},${it.value[1]}" })
                    }
                    synchronized(this) { memory[k] = Surah(map) }
                    ok = true
                }
            } catch (_: Exception) {
            }
            if (!ok) synchronized(this) { bad.add(k) }
            done(ok)
        }.start()
    }
}
