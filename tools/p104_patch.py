"""P104 VC127 patch applied after the P103 patch.
Audio only: consecutive verses recited as separate files no longer stop between them
(the next prepared verse takes over a moment before the silent end of the current file).
Every anchor is asserted exactly once; no signing, no data migration."""
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
j = root / "app/src/main/java/com/ab/khatma"


def edit(path: Path, old: str, new: str, count: int = 1):
    s = path.read_text(encoding="utf-8")
    assert s.count(old) == count, "anchor mismatch in %s: %r (found %d)" % (path.name, old[:70], s.count(old))
    path.write_text(s.replace(old, new), encoding="utf-8")


audio = j / "quran/P52SurahAudio.kt"

# 1. early hand-over to the prepared next verse ------------------------------------------
edit(audio, "    private var switchWhenReady = false\n", r'''    private var switchWhenReady = false

    // P104: a verse file ends with a short silence. When the next verse is already prepared it takes over a moment
    // before the end of the current file, so verses that continue one sentence (e.g. the oaths at the start of
    // surat An-Nazi'at) are heard without an audible stop. The old player is released a little later: nothing is cut.
    private const val EARLY_SWITCH_MS = 180
    private val earlyRunnable = object : Runnable {
        override fun run() {
            earlyTick()
            if (player != null) main.postDelayed(this, 25)
        }
    }

    private fun earlyTick() {
        val p = player ?: return
        if (segSurah > 0 || isLoading || !isPlaying || !nextReady || switchWhenReady) return
        if (nextPlayer == null || index + 1 >= queue.size) return
        val cur = queue.getOrNull(index) ?: return
        if (cur < AYAH_BASE) return
        val dur = runCatching { p.duration }.getOrDefault(0)
        if (dur <= 0) return
        val pos = runCatching { p.currentPosition }.getOrDefault(0)
        val lead = (EARLY_SWITCH_MS * speed).toInt()
        if (dur - pos <= lead) switchToNext()
    }
''')

# 2. the old player finishes its last moment instead of being cut --------------------------
edit(audio, '''        old?.let { o ->
            runCatching { o.setOnPreparedListener(null) }
            runCatching { o.setOnCompletionListener(null) }
            runCatching { o.setOnErrorListener(null) }
            runCatching { o.release() }
        }
''', '''        old?.let { o ->
            runCatching { o.setOnPreparedListener(null) }
            runCatching { o.setOnCompletionListener(null) }
            runCatching { o.setOnErrorListener(null) }
            main.postDelayed({ runCatching { o.release() } }, 500L)
        }
''')

# 3. start the watcher wherever the progress ticker starts; stop it with the player ---------
edit(audio, "main.removeCallbacks(progressRunnable); main.post(progressRunnable)",
     "main.removeCallbacks(progressRunnable); main.post(progressRunnable); main.removeCallbacks(earlyRunnable); main.post(earlyRunnable)",
     count=4)
edit(audio, "        main.removeCallbacks(segRunnable)\n        player?.let { p ->\n",
     "        main.removeCallbacks(segRunnable)\n        main.removeCallbacks(earlyRunnable)\n        player?.let { p ->\n")

# 4. version -----------------------------------------------------------------------------
g = root / "app/build.gradle.kts"
edit(g, "versionCode = 126", "versionCode = 127")
edit(g, 'versionName = "0.19.97-p103-qa-only"', 'versionName = "0.19.98-p104-audio-qa-only"')
print("P104 patch applied")
