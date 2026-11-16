#!/usr/bin/env python3
"""Keep all past tests, update only assertions made obsolete by newly
verified, real, accurately labeled direct human audio on F01/F04-F06/N13.
"""
import hashlib,sys
from pathlib import Path
root=Path(sys.argv[1])
base=root/"app/src/test/java/com/ab/khatma/ui/screens"
files={
"P123TeachingAudioTest.kt":"716a576a563e402c056c4bc4e1fd62b8a206b0dce65ead2df0d24b87f7fe4850",
"P124ShaddaLessonLinkTest.kt":"b257e5aaa89e5aa3b9f17b910a52d7756ad5ed1d915d15a32587adc83a2b0bd0",
"P125RealWordAudioTest.kt":"bb770787f68bee07fbc7bd1ca2e0058ff356983b362ac7cb8b6c19aa888c8083"}
for name,sha in files.items():
 assert hashlib.sha256((base/name).read_bytes()).hexdigest()==sha,name+" baseline changed"
def patch(p,old,new):
 s=p.read_text(encoding="utf-8")
 assert s.count(old)==1,(p.name,old,s.count(old))
 p.write_text(s.replace(old,new,1),encoding="utf-8")
p=base/"P123TeachingAudioTest.kt"
patch(p,'listOf("F01", "F13", "T08", "R02")','listOf("F13", "T08", "R02")')
patch(p,'assertTrue(P123TeachingAudio.notice(it).contains("آية"))','assertTrue(P123TeachingAudio.notice(it).contains("Wikimedia"))')
p=base/"P124ShaddaLessonLinkTest.kt"
patch(p,'assertEquals("شاهد شرح الشدة", P123TeachingAudio.playbackLabel("N13"))','assertEquals("اسمع الرَّحْمَٰنِ", P123TeachingAudio.playbackLabel("N13"))')
patch(p,'assertTrue(P123TeachingAudio.notice("N13").contains("ليس تسجيلًا منفصلًا"))','assertTrue(P123TeachingAudio.notice("N13").contains("QuranHive"))')
patch(p,'assertTrue(P123TeachingAudio.notice("N13").contains("رَبَّ"))','assertTrue(P123TeachingAudio.notice("N13").contains("الكلمتان الظاهرتان"))')
patch(p,'assertTrue(P123TeachingAudio.notice("N13").contains("رَبَ"))','assertTrue(P123TeachingAudio.notice("N13").contains("صوت قارئ حقيقي"))')
patch(p,'assertFalse(P123TeachingAudio.hasPlayback("F01"))','assertTrue(P123TeachingAudio.hasPlayback("F01"))')
patch(p,'assertTrue(P123TeachingAudio.playbackLabel("F01").contains("قيد الإعداد"))','assertTrue(P123TeachingAudio.playbackLabel("F01").contains("اسم الحرف"))')
p=base/"P125RealWordAudioTest.kt"
patch(p,'assertEquals("شاهد شرح الشدة",P123TeachingAudio.playbackLabel("N13"))','assertEquals("اسمع الرَّحْمَٰنِ",P123TeachingAudio.playbackLabel("N13"))')
patch(p,'assertTrue(P123TeachingAudio.notice("N13").contains("ليس تسجيلًا منفصلًا"))','assertTrue(P123TeachingAudio.notice("N13").contains("QuranHive"))')
print("Prior unit tests preserved; source-bound old assertions updated for verified direct recordings")
