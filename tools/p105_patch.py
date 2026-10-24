"""P105 VC132 patch applied after the P104 patch.
Audio test: surat An-Nazi'at (79) of Maher, Banna, Yasser Salama and Jibreel plays as ONE continuous file with bundled verse timings.
Every anchor is asserted exactly once; no signing, no data migration."""
import shutil
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
overlay = Path(sys.argv[2]).resolve()
j = root / "app/src/main/java/com/ab/khatma"


def edit(path: Path, old: str, new: str, count: int = 1):
    s = path.read_text(encoding="utf-8")
    assert s.count(old) == count, "anchor mismatch in %s: %r (found %d)" % (path.name, old[:70], s.count(old))
    path.write_text(s.replace(old, new), encoding="utf-8")


timing = j / "quran/P66Timing.kt"
audio = j / "quran/P52SurahAudio.kt"

# 1. bundled timings (assets/p105_timing/<reciter id>_<surah>.txt, lines "ayah,startMs,endMs") -------------
edit(timing, "    private var dir: File? = null\n", '''    private var dir: File? = null
    private var assetMgr: android.content.res.AssetManager? = null
    /** P105: timings that ship inside the app (computed by aligning the verse recordings with the whole-surah file). */
    private val bundled = setOf("jibreel", "maher", "banna_murattal", "yasser_salama")

    private fun bundledKey(r: P52Reciter, surah: Int) = "b_${r.id}_$surah"

    @Synchronized
    private fun bundledSurah(r: P52Reciter, surah: Int): Surah? {
        if (r.id !in bundled) return null
        val k = bundledKey(r, surah)
        if (k in bad) return null
        memory[k]?.let { return it }
        val am = assetMgr ?: return null
        val parsed = runCatching {
            val map = HashMap<Int, IntArray>()
            am.open("p105_timing/${r.id}_$surah.txt").bufferedReader().use { br ->
                br.readLines().forEach { line ->
                    val p = line.trim().split(',')
                    if (p.size == 3) map[p[0].toInt()] = intArrayOf(p[1].toInt(), p[2].toInt())
                }
            }
            map
        }.getOrNull()
        if (parsed.isNullOrEmpty() || !parsed.containsKey(1)) return null
        return Surah(parsed).also { memory[k] = it }
    }

    fun hasBundled(r: P52Reciter, surah: Int): Boolean = bundledSurah(r, surah) != null
''')
edit(timing, "    fun init(context: Context) {\n", "    fun init(context: Context) {\n        assetMgr = context.applicationContext.assets\n")
edit(timing, "        if (!supported(r)) return null\n        val k = key(r, surah)\n        if (k in bad) return null\n",
     "        bundledSurah(r, surah)?.let { return it }\n        if (!supported(r)) return null\n        val k = key(r, surah)\n        if (k in bad) return null\n")
edit(timing, "        val k = key(r, surah)\n        bad.add(k)\n        memory.remove(k)\n",
     "        val k = key(r, surah)\n        bad.add(k)\n        bad.add(bundledKey(r, surah))\n        memory.remove(k)\n        memory.remove(bundledKey(r, surah))\n")

# 2. the player may use the continuous mode for a bundled surah as well ----------------------------------------
edit(audio, "        if (item < AYAH_BASE || (queue.size < 2 && r.ayahFolder != null) || !P66Timing.supported(r)) return false\n        val s = (item - AYAH_BASE) / 1000\n",
     "        if (item < AYAH_BASE || (queue.size < 2 && r.ayahFolder != null)) return false\n        val s = (item - AYAH_BASE) / 1000\n        if (!P66Timing.supported(r) && !P66Timing.hasBundled(r, s)) return false\n")

# 3. the bundled timing files -----------------------------------------------------------------------------------
dst = root / "app/src/main/assets/p105_timing"
dst.mkdir(parents=True, exist_ok=True)
files = sorted((overlay / "assets/p105_timing").glob("*.txt"))
assert len(files) == 4
for f in files:
    shutil.copy(f, dst / f.name)

# 4. version -----------------------------------------------------------------------------------------------------
g = root / "app/build.gradle.kts"
edit(g, "versionCode = 131", "versionCode = 132")
edit(g, 'versionName = "0.19.102-p104-audio-qa-only"', 'versionName = "0.19.103-p105-audio-test-qa-only"')
print("P105 patch applied")
