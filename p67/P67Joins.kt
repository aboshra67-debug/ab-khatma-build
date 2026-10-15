package com.ab.khatma.quran

import android.content.Context
import org.json.JSONObject

/** P67: where a reciter's verse file already runs into the next verse (the reciter joined them — وصل).
 * Example (Maher Al-Muaiqly): Al-Fatiha 6 ends with «…المستقيم صـ» and verse 7 starts again with «صراط». Verse 6 now
 * stops just before that «صـ» and verse 7 starts at once from its first sound.
 * The data was built once by p67/p67_overlap.py; verses not found there play whole, exactly as before. */
object P67Joins {
    /** reciter id of the app -> asset with { "surah": [[cutMs, headMs], ...] } */
    private val assets = mapOf(
        "maher" to "quran/p67_joins_maher.json",
    )

    private var app: Context? = null
    private val loaded = HashMap<String, Map<Int, Array<IntArray>>>()

    fun init(context: Context) {
        if (app == null) app = context.applicationContext
    }

    fun has(id: String): Boolean = assets.containsKey(id)

    @Synchronized
    private fun all(id: String): Map<Int, Array<IntArray>> {
        loaded[id]?.let { return it }
        val name = assets[id] ?: return emptyMap()
        val ctx = app ?: return emptyMap()
        val map = runCatching {
            val o = JSONObject(ctx.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() })
            val out = HashMap<Int, Array<IntArray>>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val s = k.toIntOrNull() ?: continue
                val arr = o.getJSONArray(k)
                out[s] = Array(arr.length()) { i ->
                    val p = arr.getJSONArray(i)
                    intArrayOf(p.optInt(0, 0), p.optInt(1, 0))
                }
            }
            out
        }.getOrDefault(emptyMap())
        loaded[id] = map
        return map
    }

    /** Where verse [ayah] must stop because it already holds the next verse; 0 = play the whole file. */
    fun cut(id: String, surah: Int, ayah: Int): Int =
        if (has(id)) all(id)[surah]?.getOrNull(ayah - 1)?.get(0) ?: 0 else 0

    /** Where the voice starts in verse [ayah] (to start it right away after a joined verse). */
    fun head(id: String, surah: Int, ayah: Int): Int =
        if (has(id)) all(id)[surah]?.getOrNull(ayah - 1)?.get(1) ?: 0 else 0
}
