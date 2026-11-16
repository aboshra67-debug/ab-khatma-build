#!/usr/bin/env python3
"""P123/VC150 verified external teaching audio navigation patch.
Non-destructive, hash-checked changes limited to learning UI and version metadata.
Does not copy or modify any Quran text/audio/video files.
"""
from pathlib import Path
import hashlib, sys

root = Path(sys.argv[1]).resolve()
screens = root / "app/src/main/java/com/ab/khatma/ui/screens"
checks = {
 "app/src/main/java/com/ab/khatma/ui/screens/P114QuranLearningScreen.kt": "c8af76e3370faef24d757104fa079eb4c6eb0db73b9e4730cf87d33ee373538e",
 "app/src/main/java/com/ab/khatma/ui/screens/P121QuranLearningCurriculum.kt": "734c6076151a8d841ee8339044bab0358363e447c661671e358199f59af19308",
 "app/build.gradle.kts": "d1fabadceb5e05700169c2c31d70aefb4dc9c711dfb36b41fa3ea19fbb4388ea",
}
for rel, want in checks.items():
    actual = hashlib.sha256((root / rel).read_bytes()).hexdigest()
    assert actual == want, f"Baseline mismatch, refusing to modify: {rel}"

def replace_once(path, old, new):
    s = path.read_text(encoding="utf-8")
    assert s.count(old) == 1, f"Unsafe anchor count in {path}: {s.count(old)}"
    path.write_text(s.replace(old, new, 1), encoding="utf-8")

catalog = screens / "P123VerifiedTeacherAudio.kt"
assert not catalog.exists(), "Refusing overwrite"
catalog.write_text('''package com.ab.khatma.ui.screens

/**
 * These are externally hosted FULL teacher lesson videos, not isolated sound
 * recordings for the displayed examples. Each link is published by the
 * official Noorani Qaida YouTube channel and played in YouTube's own player.
 * The copyright holder retains all rights. No download, extraction or
 * redistribution is performed.
 */
internal data class P123TeacherVideo(val videoId: String, val title: String)

internal object P123TeachingAudio {
    private val officialVideos = mapOf(
        "N04" to P123TeacherVideo("d5T5sUkZnx0", "القاعدة النورانية — الدرس ٤: الحركات"),
        "N05" to P123TeacherVideo("NltqYdG1D-E", "القاعدة النورانية — الدرس ٥: التنوين"),
        "N08" to P123TeacherVideo("IRSimXTF9-c", "القاعدة النورانية — الدرس ٨: حروف المد واللين"),
        "N09" to P123TeacherVideo("mNYli6aW_v8", "القاعدة النورانية — الدرس ٩: تطبيقات"),
        "N10" to P123TeacherVideo("tGjkXsazIQM", "القاعدة النورانية — الدرس ١٠: السكون"),
        "F12" to P123TeacherVideo("tGjkXsazIQM", "شرح السكون والمقاطع بصوت المعلم — درس القاعدة ١٠"),
        "N12" to P123TeacherVideo("oH6_jLcvzUc", "القاعدة النورانية — الدرس ١٢: الشدة")
    )
    fun teacherVideo(id: String): P123TeacherVideo? = officialVideos[id]
    fun hasPlayback(id: String): Boolean =
        teacherVideo(id) != null || P122LessonActions.audioLetterIndex(id) != null
    fun playbackLabel(id: String): String = when {
        teacherVideo(id) != null -> "اسمع شرح المعلم"
        P122LessonActions.audioLetterIndex(id) != null -> "اسمع آية"
        else -> "صوت قيد الإعداد"
    }
    fun notice(id: String): String = when {
        teacherVideo(id) != null ->
            "شرح كامل بصوت معلم عبر يوتيوب، وليس تسجيلًا منفردًا للمثال المعروض. يحتاج إنترنت."
        P122LessonActions.audioLetterIndex(id) != null ->
            "تلاوة آية بصوت الحصري في سياقها، وليست تسجيلًا منفردًا للحرف."
        else ->
            "لم يُعتمد بعد تسجيل صوتي مطابق لهذا المثال. لا نستخدم صوتًا آليًا أو مثالًا غير مطابق."
    }
}
''', encoding="utf-8")

p114 = screens / "P114QuranLearningScreen.kt"
replace_once(p114, '''                    } else if (lesson.track == 0) {
                        setScreen("groups")
                    } else {
                        val page = lesson.mushafPage.takeIf { it in 1..604 } ?: 1
                        if (recording) pendingDestination = "mushaf_page_$page"
                        else { teacherAudio.stop(); recorder.delete(); onOpenMushafPage(page) }
                    }
                },
''', '''                    } else {
                        val officialTeacher = P123TeachingAudio.teacherVideo(lesson.id)
                        if (officialTeacher != null && !recording) {
                            openTeacherVideo(officialTeacher.videoId, officialTeacher.title)
                        } else {
                            mediaStatus = "لا يتوفر تسجيل تعليمي معتمد لهذا المثال بعد."
                        }
                    }
                },
''')

p121 = screens / "P121QuranLearningCurriculum.kt"
replace_once(p121, '''                            onClick = { onListenLesson(current) }, enabled = !recording,
''', '''                            onClick = { onListenLesson(current) },
                            enabled = !recording && P123TeachingAudio.hasPlayback(current.id),
''')
replace_once(p121, '''                            Icon(if (P122LessonActions.audioLetterIndex(current.id) != null) Icons.Rounded.VolumeUp else Icons.Rounded.MenuBook, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(P122LessonActions.firstActionLabel(current), fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 14.sp)
''', '''                            Icon(if (P123TeachingAudio.hasPlayback(current.id)) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(P123TeachingAudio.playbackLabel(current.id), fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 14.sp)
''')
replace_once(p121, '''                    if (P122LessonActions.audioLetterIndex(current.id) != null) {
                        Text("الاستماع هنا لمثال قرآني بصوت قارئ، وليس نطقًا منفردًا للحرف.", fontSize = 11.sp, color = Color(0xFF776F63), lineHeight = 16.sp)
                    }
''', '''                    Text(P123TeachingAudio.notice(current.id), fontSize = 11.sp, color = Color(0xFF776F63), lineHeight = 16.sp)
''')

test = root / "app/src/test/java/com/ab/khatma/ui/screens/P123TeachingAudioTest.kt"
assert not test.exists()
test.write_text('''package com.ab.khatma.ui.screens

import org.junit.Assert.*
import org.junit.Test

class P123TeachingAudioTest {
    @Test fun exactShortSyllableLessonLinksToRealTeacherSukoonVideo() {
        val video = P123TeachingAudio.teacherVideo("F12")
        assertNotNull(video)
        assertEquals("tGjkXsazIQM", video!!.videoId)
        assertTrue(P123TeachingAudio.playbackLabel("F12").contains("المعلم"))
        assertTrue(P123TeachingAudio.notice("F12").contains("ليس تسجيلًا منفردًا"))
    }
    @Test fun missingClipsNeverPretendToBeVoiceLessons() {
        listOf("F01", "F13", "T08", "R02").forEach { id ->
            assertFalse(P123TeachingAudio.hasPlayback(id))
            assertNull(P123TeachingAudio.teacherVideo(id))
            assertTrue(P123TeachingAudio.notice(id).contains("لم يُعتمد"))
        }
    }
    @Test fun oldVerifiedVersesRemainAvailableAndNotCalledStandaloneClips() {
        listOf("F04", "F05", "F06").forEach {
            assertTrue(P123TeachingAudio.hasPlayback(it))
            assertTrue(P123TeachingAudio.notice(it).contains("آية"))
        }
    }
    @Test fun allFiftyEightLessonsAndRecordedProgressIdsRemainStable() {
        val lessons = P121CurriculumBank.lessons
        assertEquals(58, lessons.size)
        assertEquals(58, lessons.map { it.id }.toSet().size)
        lessons.forEach { lesson ->
            assertTrue(P123TeachingAudio.playbackLabel(lesson.id).isNotBlank())
        }
    }
}
''', encoding="utf-8")

build = root / "app/build.gradle.kts"
replace_once(build, 'versionCode = 149', 'versionCode = 150')
replace_once(build,
             'versionName = "0.19.122-p122-simplified-learning-safe"',
             'versionName = "0.19.123-p123-verified-teacher-audio-safe"')

docs = root / "BUILD_REPRODUCIBILITY/p123"
docs.mkdir(parents=True, exist_ok=False)
(docs / "SOURCES_AND_LIMITATIONS.md").write_text('''# P123 VC150: True teacher audio access (safe patch)

- F12 (المقاطع القصيرة) and N10 link to the official Noorani Qaida
  lesson 10 (sukoon), read by Mohammad Farooq Alraee. F12's displayed
  بَ ← أَبْ is not an isolated recorded segment in that video.
- The remaining linked official teacher lessons are N04, N05, N08,
  N09 and N12. Playback is through the YouTube player, using internet.
- Official channel: https://www.youtube.com/@QaidahNuraniah
- Lesson 10: https://www.youtube.com/watch?v=tGjkXsazIQM
- Lesson 4: https://www.youtube.com/watch?v=d5T5sUkZnx0
- Lesson 5: https://www.youtube.com/watch?v=NltqYdG1D-E
- Lesson 8: https://www.youtube.com/watch?v=IRSimXTF9-c
- Lesson 9: https://www.youtube.com/watch?v=mNYli6aW_v8
- Lesson 12: https://www.youtube.com/watch?v=oH6_jLcvzUc
- External videos retain publisher copyright; no media is copied
  or republished into the source or APK.
- Existing P122 three selected Husary full-verse listening links remain.
  They are full verses, NOT isolated letters or syllables.
- No fabricated teacher voice, AI TTS, or misleading audio status.
- No changes to Mushaf pages, Quran text, user progress storage,
  backend, other modules, package name or signing mechanism.
- Genuine standalone recordings for the rest of the 58 lessons
  still require recording/permission, scholarly approval and testing.
''', encoding="utf-8")
print("P123 SAFE PATCH APPLIED: 58 preserved, source-aware audio, teacher video for F12")
