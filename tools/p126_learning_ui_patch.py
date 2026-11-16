#!/usr/bin/env python3
"""P126 safe integration of human letter sound + matched QuranHive N13 shadda
exercises. No original Mushaf, user progress or other module changes.
"""
from pathlib import Path
import hashlib,sys
root=Path(sys.argv[1])
checks={
"app/src/main/java/com/ab/khatma/ui/screens/P114QuranLearningScreen.kt":"4cf0baa5be43ffd874d774fb5188d52cdc18c0b3978347b4280457be5a16eafd",
"app/src/main/java/com/ab/khatma/ui/screens/P121QuranLearningCurriculum.kt":"52fd8252aa4bdb66426f2ea40a745cd87d47aa6ebd2e0b619fdb71146b542d26",
"app/src/main/java/com/ab/khatma/ui/screens/P123VerifiedTeacherAudio.kt":"4cd69dc504bd84c1af20f87bc1e44c7ab70b6f9c6659ef4b62e21975b238f2ee",
"app/build.gradle.kts":"00b9a0b6952daf79931dc92df3875b3504db110436e7340f157e2e506367a9c7"
}
for file,sha in checks.items():
 assert hashlib.sha256((root/file).read_bytes()).hexdigest()==sha,file+" drift"
def patch(path,old,new):
 s=path.read_text(encoding="utf-8")
 assert s.count(old)==1,(path.name,"unsafe patch anchor",s.count(old),old[:70])
 path.write_text(s.replace(old,new,1),encoding="utf-8")
screen=root/"app/src/main/java/com/ab/khatma/ui/screens/P121QuranLearningCurriculum.kt"
patch(screen,
"import androidx.compose.foundation.background\n",
"import androidx.compose.foundation.background\nimport androidx.compose.foundation.horizontalScroll\n")
patch(screen,
'    onPlayWord: (P125WordSample) -> Unit,\n    onLessonChanged: () -> Unit,',
'    onPlayWord: (P125WordSample) -> Unit,\n    onPlayHuman: (P126Sound) -> Unit,\n    onLessonChanged: () -> Unit,')
# N13 originally uses an unrecorded synthetic minimal pair. Replace with two
# authentic WAV-matched QuranHive words and change the question accordingly.
lines=screen.read_text(encoding="utf-8").splitlines()
target=[l for l in lines if 'P121Lesson(0, "تدرج القاعدة النورانية", "N13"' in l]
assert len(target)==1
old=target[0]
new=old.replace('رَبَّ / رَبَ','الرَّحْمَٰنِ / بِسْمِ')
new=new.replace('درّب اللسان على الكلمتين وقارن التسجيل.','اسمع الرَّحْمَٰنِ وفيها راء مشددة، ثم بِسْمِ بلا تشديد، وقارن النطق.')
new=new.replace('أي كلمة بها تشديد؟','أي الكلمتين فيها راء مشددة؟')
new=new.replace('listOf("رَبَّ", "رَبَ", "رَبْ"), "رَبَّ"','listOf("الرَّحْمَٰنِ", "بِسْمِ", "كلتاهما"), "الرَّحْمَٰنِ"')
assert new!=old and 'رَبَّ' not in new
patch(screen,old,new)
patch(screen,
'                    if (current.id == "R01") {',
'''                    val humanLetters = P126Alphabet.forLesson(current.id)
                    if (humanLetters.isNotEmpty()) {
                        var chosenLetter by remember(current.id) { mutableStateOf(humanLetters.first().char) }
                        val chosen = humanLetters.firstOrNull { it.char == chosenLetter } ?: humanLetters.first()
                        Text("اسمع أسماء الحروف وأمثلة كلمات بصوت إنسان حقيقي", color = p121Green,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            humanLetters.forEach { letter ->
                                OutlinedButton(onClick = { chosenLetter = letter.char }, enabled = !recording,
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) {
                                    Text(letter.char, fontWeight = FontWeight.Bold, color = p121Green, fontSize = 19.sp)
                                }
                            }
                        }
                        OutlinedButton(onClick = { onPlayHuman(chosen.audio) }, enabled = !recording,
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.VolumeUp, null, tint = p121Green)
                            Spacer(Modifier.width(6.dp))
                            Text("اسمع اسم الحرف: " + chosen.name, color = p121Green)
                        }
                        chosen.words.forEach { sound ->
                            OutlinedButton(onClick = { onPlayHuman(sound) }, enabled = !recording,
                                modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.VolumeUp, null, tint = p121Green)
                                Spacer(Modifier.width(6.dp))
                                Text("اسمع الكلمة: " + sound.label, color = p121Green)
                            }
                        }
                        Text("التسجيلات البشرية للأسماء والكلمات: Ruaa GHAREEB — CC BY-SA 4.0 عبر Wikimedia Commons. ليست نطقًا منفصلًا للحركات أو اختبار تجويد.",
                            fontSize = 10.sp, color = Color.Gray, lineHeight = 16.sp)
                    }
                    if (current.id == "R01" || current.id == "N13") {''')
patch(screen,
'                        Text("اسمع كلمات البسملة بصوت القارئ، كلمة بكلمة", color = p121Green, fontSize = 13.sp)',
'                        Text(if (current.id == "N13") "اسمع الكلمتين المعروضتين من تسجيلات القرآن الأصلية" else "اسمع كلمات البسملة بصوت القارئ، كلمة بكلمة", color = p121Green, fontSize = 13.sp)')
patch(screen,
'                        P125WordAudio.bismillah.chunked(2).forEach { group ->',
'                        (if (current.id == "N13") listOf(P125WordAudio.bismillah[2], P125WordAudio.bismillah[0]) else P125WordAudio.bismillah).chunked(2).forEach { group ->')
page=root/"app/src/main/java/com/ab/khatma/ui/screens/P114QuranLearningScreen.kt"
patch(page,
'    val directWordAudio=remember(context) { P125DirectAudioPlayer(context.applicationContext) }',
'    val directWordAudio=remember(context) { P125DirectAudioPlayer(context.applicationContext) }\n    val humanAlphabetAudio=remember(context) { P126RealLetterPlayer(context.applicationContext) }')
patch(page,
'    DisposableEffect(recorder,teacherAudio,directWordAudio){ onDispose { directWordAudio.stop();teacherAudio.stop();recorder.delete() } }',
'    DisposableEffect(recorder,teacherAudio,directWordAudio,humanAlphabetAudio){ onDispose { humanAlphabetAudio.stop();directWordAudio.stop();teacherAudio.stop();recorder.delete() } }')
patch(page,
'        teacherAudio.stop();directWordAudio.stop()\n        if(id!=null) { teacherVideoTitle=title;teacherVideo=id }',
'        teacherAudio.stop();directWordAudio.stop();humanAlphabetAudio.stop()\n        if(id!=null) { teacherVideoTitle=title;teacherVideo=id }')
patch(page,
'        teacherAudio.stop();directWordAudio.stop();teacherVideo=null;mediaStatus=""',
'        teacherAudio.stop();directWordAudio.stop();humanAlphabetAudio.stop();teacherVideo=null;mediaStatus=""')
patch(page,
'    fun beginRecording() {\n        teacherAudio.stop();directWordAudio.stop()',
'    fun beginRecording() {\n        teacherAudio.stop();directWordAudio.stop();humanAlphabetAudio.stop()')
patch(page,
'                    if (lesson.id == "R01" && !recording) {',
'                    if ((lesson.id == "R01" || lesson.id == "N13") && !recording) {')
patch(page,
'                        runCatching { directWordAudio.play(P125WordAudio.bismillah.first()) { mediaStatus = it } }',
'                        runCatching { directWordAudio.play(if (lesson.id == "N13") P125WordAudio.bismillah[2] else P125WordAudio.bismillah.first()) { mediaStatus = it } }')
patch(page,
'                    } else if (sampleIndex != null && !recording) {',
'''                    } else if (P126Alphabet.forLesson(lesson.id).isNotEmpty() && !recording) {
                        runCatching { humanAlphabetAudio.play(P126Alphabet.forLesson(lesson.id).first().audio) { mediaStatus = it } }
                            .onFailure { mediaStatus = "تعذر تشغيل اسم الحرف" }
                    } else if (sampleIndex != null && !recording) {''')
patch(page,
'                        teacherAudio.stop()\n                        directWordAudio.play(sample) { mediaStatus = it }',
'                        teacherAudio.stop();humanAlphabetAudio.stop()\n                        directWordAudio.play(sample) { mediaStatus = it }')
patch(page,
'                onLessonChanged = {\n                    directWordAudio.stop();teacherAudio.stop(); recorder.delete(); recorded = false',
'''                onPlayHuman = { sample ->
                    if (recording) mediaStatus = "أوقف تسجيلك أولًا"
                    else runCatching {
                        teacherAudio.stop();directWordAudio.stop()
                        humanAlphabetAudio.play(sample) { mediaStatus = it }
                    }.onFailure { mediaStatus = "تعذر تشغيل التسجيل" }
                },
                onLessonChanged = {
                    humanAlphabetAudio.stop();directWordAudio.stop();teacherAudio.stop(); recorder.delete(); recorded = false''')
catalog=root/"app/src/main/java/com/ab/khatma/ui/screens/P123VerifiedTeacherAudio.kt"
patch(catalog,'        id == "N13" -> "شاهد شرح الشدة"',
             '        id == "N13" -> "اسمع الرَّحْمَٰنِ"')
patch(catalog,
'        id == "N13" ->\n            "فيديو لشرح قاعدة الشدة بصوت المعلم، وليس تسجيلًا منفصلًا للكلمتين رَبَّ ورَبَ. يحتاج إنترنت."',
'        id == "N13" ->\n            "الكلمتان الظاهرتان مسجلتان بصوت قارئ حقيقي من QuranHive — CC BY 4.0، وليستا صوتًا آليًا."')
patch(catalog,
'        id == "R01" || teacherVideo(id) != null',
'        id == "R01" || id == "N13" || P126Alphabet.forLesson(id).isNotEmpty() || teacherVideo(id) != null')
patch(catalog,
'        id == "R01" -> "اسمع بِسْمِ"',
'''        id == "R01" -> "اسمع بِسْمِ"
        P126Alphabet.forLesson(id).isNotEmpty() -> "اسمع اسم الحرف"''')
patch(catalog,
'        id == "R01" ->\n            "أربع كلمات قرآنية بصوت قارئ من QuranHive (CC BY 4.0)، وليس تسجيلًا منفردًا لكل درس."',
'''        id == "R01" ->
            "أربع كلمات قرآنية بصوت قارئ من QuranHive (CC BY 4.0)، وليس تسجيلًا منفردًا لكل درس."
        P126Alphabet.forLesson(id).isNotEmpty() ->
            "أصوات أسماء الحروف وكلمات كاملة بصوت بشري مرخّص من Wikimedia Commons (CC BY-SA 4.0). ليست نطقًا منعزلًا للمثال المكتوب."''')
gradle=root/"app/build.gradle.kts"
patch(gradle,"versionCode = 152","versionCode = 153")
patch(gradle,'versionName = "0.19.125-p125-real-word-audio-safe"','versionName = "0.19.126-p126-human-alphabet-audio-safe"')
test=root/"app/src/test/java/com/ab/khatma/ui/screens/P126RealAlphabetSoundTest.kt"
assert not test.exists()
test.write_text('''package com.ab.khatma.ui.screens

import org.junit.Assert.*
import org.junit.Test

class P126RealAlphabetSoundTest {
    @Test fun twentyEightLettersHaveIndividualHumanAudio() {
        assertEquals(28,P126Alphabet.letters.size)
        assertEquals(28,P126Alphabet.letters.map { it.char }.toSet().size)
        assertTrue(P126Alphabet.letters.all { it.words.isNotEmpty() })
        assertEquals(28,P126Alphabet.forLesson("N01").size)
    }
    @Test fun articulationGroupsAreExact() {
        assertEquals(listOf("ع","ح"),P126Alphabet.forLesson("F05").map { it.char })
        assertEquals(listOf("غ","خ"),P126Alphabet.forLesson("F06").map { it.char })
    }
    @Test fun shaddaLessonNowDisplaysOnlyDirectlySpokenQuranicWords() {
        val lesson=P121CurriculumBank.lessons.single { it.id=="N13" }
        assertEquals("الرَّحْمَٰنِ / بِسْمِ",lesson.example)
        assertEquals("الرَّحْمَٰنِ",lesson.answer)
        assertTrue(P123TeachingAudio.hasPlayback("N13"))
        assertEquals("اسمع الرَّحْمَٰنِ",P123TeachingAudio.playbackLabel("N13"))
    }
    @Test fun sameFiftyEightLessonIdsExist() {
        assertEquals(58,P121CurriculumBank.lessons.size)
        assertEquals(58,P121CurriculumBank.lessons.map { it.id }.toSet().size)
    }
}
''',encoding="utf-8")
print("P126 SAFE UI PATCH: QuranHive N13 matched examples, licensed human pronunciation and 58 original lesson IDs.")
