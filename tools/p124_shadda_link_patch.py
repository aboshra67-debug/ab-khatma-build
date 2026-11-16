#!/usr/bin/env python3
"""P124 / VC151: link N13 shadda practice to a real, openly linked teacher video.
This video demonstrates the shadda rule, NOT a verified isolated recording
of the displayed pair رَبَّ / رَبَ. Never claim otherwise.
"""
from pathlib import Path
import hashlib, sys

root=Path(sys.argv[1]).resolve()
expected={
"app/src/main/java/com/ab/khatma/ui/screens/P123VerifiedTeacherAudio.kt":"1292e6457a65e2e991afeabde99a7c328afd3b3c7c4543f2db8d7bfe8daf1c52",
"app/src/test/java/com/ab/khatma/ui/screens/P123TeachingAudioTest.kt":"716a576a563e402c056c4bc4e1fd62b8a206b0dce65ead2df0d24b87f7fe4850",
"app/build.gradle.kts":"e896f2dbf89ff7ef2daf6075e3c7b43b9f38de4ead470756330141ea26593b2a",
}
for n,sha in expected.items():
    actual=hashlib.sha256((root/n).read_bytes()).hexdigest()
    assert actual==sha, f"FAIL-CLOSED: changed baseline {n}"
def replace_once(file,old,new):
    s=file.read_text(encoding="utf-8")
    assert s.count(old)==1, f"Expected exactly one occurrence in {file}: {old}"
    file.write_text(s.replace(old,new,1),encoding="utf-8")
src=root/"app/src/main/java/com/ab/khatma/ui/screens/P123VerifiedTeacherAudio.kt"
replace_once(src,
''' * These are externally hosted FULL teacher lesson videos, not isolated sound
 * recordings for the displayed examples. Each link is published by the
 * official Noorani Qaida YouTube channel and played in YouTube's own player.
''',
''' * These are externally hosted FULL teacher lesson videos, not isolated sound
 * recordings for the displayed examples. Links may have independent uploaders.
 * They are opened in YouTube's own player; check publisher rights independently.
''')
replace_once(src,
'''        "N12" to P123TeacherVideo("oH6_jLcvzUc", "القاعدة النورانية — الدرس ١٢: الشدة")
''',
'''        "N12" to P123TeacherVideo("oH6_jLcvzUc", "القاعدة النورانية — الدرس ١٢: الشدة"),
        "N13" to P123TeacherVideo("4P39Oo7eHr0", "شرح الشدة — القاعدة النورانية، الدرس ١٢، بصوت محمد فاروق الراعي")
''')
replace_once(src,
'''    fun playbackLabel(id: String): String = when {
        teacherVideo(id) != null -> "اسمع شرح المعلم"
''',
'''    fun playbackLabel(id: String): String = when {
        id == "N13" -> "شاهد شرح الشدة"
        teacherVideo(id) != null -> "اسمع شرح المعلم"
''')
replace_once(src,
'''    fun notice(id: String): String = when {
        teacherVideo(id) != null ->
''',
'''    fun notice(id: String): String = when {
        id == "N13" ->
            "فيديو لشرح قاعدة الشدة بصوت المعلم، وليس تسجيلًا منفصلًا للكلمتين رَبَّ ورَبَ. يحتاج إنترنت."
        teacherVideo(id) != null ->
''')
test=root/"app/src/test/java/com/ab/khatma/ui/screens/P124ShaddaLessonLinkTest.kt"
assert not test.exists()
test.write_text('''package com.ab.khatma.ui.screens

import org.junit.Assert.*
import org.junit.Test

class P124ShaddaLessonLinkTest {
    @Test fun shaddaPracticeHasExplicitTeacherExplanationLink() {
        assertEquals("4P39Oo7eHr0", P123TeachingAudio.teacherVideo("N13")?.videoId)
        assertTrue(P123TeachingAudio.hasPlayback("N13"))
        assertEquals("شاهد شرح الشدة", P123TeachingAudio.playbackLabel("N13"))
        assertTrue(P123TeachingAudio.notice("N13").contains("ليس تسجيلًا منفصلًا"))
        assertTrue(P123TeachingAudio.notice("N13").contains("رَبَّ"))
        assertTrue(P123TeachingAudio.notice("N13").contains("رَبَ"))
    }
    @Test fun sourceForOriginalShaddaLessonStillPreserved() {
        assertEquals("oH6_jLcvzUc", P123TeachingAudio.teacherVideo("N12")?.videoId)
    }
    @Test fun unrelatedLessonsStayFailClosedWithoutAudio() {
        assertFalse(P123TeachingAudio.hasPlayback("F01"))
        assertTrue(P123TeachingAudio.playbackLabel("F01").contains("قيد الإعداد"))
    }
}
''',encoding="utf-8")
build=root/"app/build.gradle.kts"
replace_once(build,'versionCode = 150','versionCode = 151')
replace_once(build,'versionName = "0.19.123-p123-verified-teacher-audio-safe"','versionName = "0.19.124-p124-shadda-teacher-lesson-safe"')
audit=root/"BUILD_REPRODUCIBILITY/p124";audit.mkdir(parents=True,exist_ok=False)
(audit/"README.md").write_text('''# P124 / VC151 — targeted shadda teaching-link fix

Screenshot lesson 24 / 31 = N13 (تطبيق الشدة). Previously N12 was linked to
an external teacher video, but N13 was not linked, showing صوت قيد الإعداد.

Added openly linked instructional video for N13: YouTube 4P39Oo7eHr0,
a 2015 upload titled القاعدة النورانية - [12] الشدة, credited by its
uploader as recited by محمد فاروق الراعي.

Not a standalone recording of رَبَّ or رَبَ. The UI explicitly says so,
and the button says شاهد شرح الشدة. Full audio examples still missing.
Video plays through YouTube, requires internet. No media extracted/copy.
No Quran source pages, user progress or other modules edited.
Source and UI content must be reviewed on device after building.
''',encoding="utf-8")
print("P124 safe patch done: N13 explanation link and explicit limitations; 58 lessons and user data preserved.")
