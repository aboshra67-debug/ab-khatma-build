"""P106 VC133 patch applied after the P105 patch.
1. «ياسر سلامة (الحدر)» is removed: it only replayed the normal recitation (no real Hadr source is available).
2. Reciters that only have whole-surah files (Luhaidan, Doori, Qaloon, English meanings): choosing a verse plays the SURAH from its
   start in the reciter's own voice (until now the verse was heard in Husary's voice under the reciter's name).
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

# 1. remove the Hadr entry --------------------------------------------------------------------------------------
edit(audio, '        P52Reciter("yasser_salama_hadr", "ياسر سلامة", "مصحف الحدر", M + "yasser-salama/r1/", HADR, null),\n', "")
edit(audio, '    private const val HADR = "مصحف الحدر (سريع)"\n', "")
edit(audio, 'listOf(MUR, MUJ, MUA, "تلاوات تراثية مختارة", HADR, RIW, ENG)', 'listOf(MUR, MUJ, MUA, "تلاوات تراثية مختارة", RIW, ENG)')

# 2. whole-surah-only reciters play the surah in their own voice ---------------------------------------------------
edit(audio, "    fun play(context: Context, surahs: List<Int>, start: Int = 0, sampleId: String? = null, resume: Boolean = false) {\n"
            "        followReader = false\n        init(context)\n        resumeFirst = resume && sampleId == null\n        val chosen = P52Reciters.byId(sampleId ?: reciterId)\n",
     '''    /** P106: true for a reciter that has whole-surah files only (no verse files, no verse timings, every surah available). */
    fun playsWholeSurahOnly(r: P52Reciter = savedReciter): Boolean =
        r.ayahFolder == null && r.availableSurahs == null && !P66Timing.supported(r)

    fun play(context: Context, items: List<Int>, start: Int = 0, sampleId: String? = null, resume: Boolean = false) {
        followReader = false
        init(context)
        resumeFirst = resume && sampleId == null
        val chosen = P52Reciters.byId(sampleId ?: reciterId)
        // P106: a verse chosen with such a reciter plays its surah from the start, in the reciter's own voice.
        var begin = start
        val surahs = if (playsWholeSurahOnly(chosen)) {
            val whole = items.map { if (it >= AYAH_BASE) (it - AYAH_BASE) / 1000 else it }
            val first = whole.getOrNull(start.coerceIn(0, (whole.size - 1).coerceAtLeast(0)))
            val distinct = whole.distinct()
            begin = if (first != null) distinct.indexOf(first).coerceAtLeast(0) else 0
            distinct
        } else items
''')
edit(audio, "        index = start.coerceIn(0, clean.lastIndex)\n        message = null\n        app?.let { ctx ->\n            runCatching { ContextCompat.startForegroundService",
     "        index = begin.coerceIn(0, clean.lastIndex)\n        message = null\n        app?.let { ctx ->\n            runCatching { ContextCompat.startForegroundService")

# 3. honest messages ---------------------------------------------------------------------------------------------------
NEW_NOTE = " متاح للسورة كاملة فقط — يُشغَّل من أول السورة بصوته."
edit(j / "ui/screens/P62Reader.kt", '" متاح للسورة كاملة فقط — الآيات بصوت الحصري."', '"%s"' % NEW_NOTE)
edit(j / "ui/screens/QuranScreen.kt", '" متاح للسورة كاملة فقط — الآيات بصوت الحصري."', '"%s"' % NEW_NOTE)
edit(j / "ui/screens/QuranScreen.kt", '" متاح للسورة كاملة فقط — الآية بصوت الحصري."', '"%s"' % NEW_NOTE)
LABEL = "هذا القارئ للسورة كاملة فقط؛ عند اختيار آية تُشغَّل السورة من أولها بصوته."
edit(j / "ui/screens/P55ListenUi.kt", '"هذا القارئ متاح للسورة كاملة فقط؛ الآيات المنفردة بصوت الحصري."', '"%s"' % LABEL)
edit(j / "ui/screens/P64Ui.kt", '"هذا القارئ للسورة كاملة فقط؛ الآيات المنفردة بصوت الحصري."', '"%s"' % LABEL)

edit(j / "ui/screens/P42AudioPicker.kt", '"هذا القارئ متاح للسورة كاملة فقط؛ الآيات المنفردة بصوت الحصري."', '"%s"' % LABEL)
edit(j / "ui/screens/P55ListenUi.kt", "(للسورة كاملة فقط) = الآيات بصوت الحصري", "(للسورة كاملة فقط) = تشغيل السورة من أولها بصوته")

# 4. a single verse tapped in the Quran screen: same rule (this path used its own Husary-only player) ----------------
edit(j / "ui/screens/QuranScreen.kt", "    fun playAyah(aya: QuranAyah, fromSequence: Boolean = false) {\n        P52SurahAudio.pause()\n",
     '''    fun playAyah(aya: QuranAyah, fromSequence: Boolean = false) {
        P52SurahAudio.pause()
        if (P52SurahAudio.playsWholeSurahOnly()) {
            stopAudio()
            P52SurahAudio.play(context, listOf(aya.surah))
            audioMessage = P52SurahAudio.savedReciter.name + "%s"
            return
        }
''' % NEW_NOTE)

# 5. version ---------------------------------------------------------------------------------------------------------------
g = root / "app/build.gradle.kts"
edit(g, "versionCode = 132", "versionCode = 133")
edit(g, 'versionName = "0.19.103-p105-audio-test-qa-only"', 'versionName = "0.19.104-p106-audio-qa-only"')
print("P106 patch applied")
