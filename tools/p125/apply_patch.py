#!/usr/bin/env python3
from pathlib import Path
import hashlib,sys,shutil
root=Path(sys.argv[1]).resolve()
repo=Path(sys.argv[2]).resolve()
hashes={
'app/src/main/java/com/ab/khatma/ui/screens/P114QuranLearningScreen.kt':'6f52230abf450b86bf434d92e5f5ad26e2d4e22b2ec92f33b9d719391c9b18f7',
'app/src/main/java/com/ab/khatma/ui/screens/P121QuranLearningCurriculum.kt':'b99c2c5e2f4a1c9092359396f725519ebdb7c79843c4208a9f7fb86a42c22dc5',
'app/src/main/java/com/ab/khatma/ui/screens/P123VerifiedTeacherAudio.kt':'7498a6ed66c6bbd1326c9da69956b6318d87fde6bf2c91fb91c904ed5dff1667',
'app/build.gradle.kts':'febe3e64841f35975cda1f1efe58b562ec8dc4559c8bbbd3678cf15d2d8e5ac0',
}
for name,digest in hashes.items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest, f"Baseline changed: {name}"
def rep(name,old,new):
    p=root/name; s=p.read_text(encoding='utf-8')
    assert s.count(old)==1, (name,old[:70],s.count(old))
    p.write_text(s.replace(old,new,1),encoding='utf-8')
base='app/src/main/java/com/ab/khatma/ui/screens/'
n=base+'P114QuranLearningScreen.kt'
rep(n,'    val teacherAudio=remember { P119GuidedAyahAudio() }',
      '    val teacherAudio=remember { P119GuidedAyahAudio() }\n    val directWordAudio=remember(context) { P125DirectAudioPlayer(context.applicationContext) }')
rep(n,'    DisposableEffect(recorder,teacherAudio){ onDispose { teacherAudio.stop();recorder.delete() } }',
      '    DisposableEffect(recorder,teacherAudio,directWordAudio){ onDispose { directWordAudio.stop();teacherAudio.stop();recorder.delete() } }')
rep(n,'        teacherAudio.stop()\n        if(id!=null) { teacherVideoTitle=title;teacherVideo=id }',
      '        teacherAudio.stop();directWordAudio.stop()\n        if(id!=null) { teacherVideoTitle=title;teacherVideo=id }')
rep(n,'        teacherAudio.stop();teacherVideo=null;mediaStatus=""',
      '        teacherAudio.stop();directWordAudio.stop();teacherVideo=null;mediaStatus=""')
rep(n,'    fun beginRecording() {\n        teacherAudio.stop()',
      '    fun beginRecording() {\n        teacherAudio.stop();directWordAudio.stop()')
rep(n,
'''                onListenLesson = { lesson ->
                    val sampleIndex = P122LessonActions.audioLetterIndex(lesson.id)
                    if (sampleIndex != null && !recording) {''',
'''                onListenLesson = { lesson ->
                    val sampleIndex = P122LessonActions.audioLetterIndex(lesson.id)
                    if (lesson.id == "R01" && !recording) {
                        runCatching { directWordAudio.play(P125WordAudio.bismillah.first()) { mediaStatus = it } }
                            .onFailure { mediaStatus = "تعذّر تشغيل الكلمة الصوتية" }
                    } else if (sampleIndex != null && !recording) {''')
rep(n,
'''                onLessonChanged = {
                    teacherAudio.stop(); recorder.delete(); recorded = false''',
'''                onPlayWord = { sample ->
                    if (recording) mediaStatus = "أوقف تسجيل صوتك أولًا"
                    else runCatching {
                        teacherAudio.stop()
                        directWordAudio.play(sample) { mediaStatus = it }
                    }.onFailure { mediaStatus = "تعذّر تشغيل الكلمة الصوتية" }
                },
                onLessonChanged = {
                    directWordAudio.stop();teacherAudio.stop(); recorder.delete(); recorded = false''')

n=base+'P121QuranLearningCurriculum.kt'
rep(n,'    onListenLesson: (P121Lesson) -> Unit,\n    onLessonChanged: () -> Unit,',
      '    onListenLesson: (P121Lesson) -> Unit,\n    onPlayWord: (P125WordSample) -> Unit,\n    onLessonChanged: () -> Unit,')
rep(n,
'''                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onListenLesson(current) },''',
'''                    if (current.id == "R01") {
                        Text("اسمع كلمات البسملة بصوت القارئ، كلمة بكلمة", color = p121Green, fontSize = 13.sp)
                        P125WordAudio.bismillah.chunked(2).forEach { group ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                group.forEach { sample ->
                                    OutlinedButton(
                                        onClick = { onPlayWord(sample) },
                                        enabled = !recording,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.VolumeUp, null, Modifier.size(17.dp), tint = p121Green)
                                        Spacer(Modifier.width(5.dp))
                                        Text(sample.label, fontSize = 13.sp, color = p121Green)
                                    }
                                }
                            }
                        }
                        Text("المصدر: QuranHive — CC BY 4.0 · quranhive.com", fontSize = 10.sp, color = Color.Gray)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onListenLesson(current) },''')
n=base+'P123VerifiedTeacherAudio.kt'
rep(n,'        teacherVideo(id) != null || P122LessonActions.audioLetterIndex(id) != null',
      '        id == "R01" || teacherVideo(id) != null || P122LessonActions.audioLetterIndex(id) != null')
rep(n,
'''    fun playbackLabel(id: String): String = when {
        id == "N13" -> "شاهد شرح الشدة"''',
'''    fun playbackLabel(id: String): String = when {
        id == "R01" -> "اسمع بِسْمِ"
        id == "N13" -> "شاهد شرح الشدة"''')
rep(n,
'''    fun notice(id: String): String = when {
        id == "N13" ->''',
'''    fun notice(id: String): String = when {
        id == "R01" ->
            "أربع كلمات قرآنية بصوت قارئ من QuranHive (CC BY 4.0)، وليس تسجيلًا منفردًا لكل درس."
        id == "N13" ->''')
n='app/build.gradle.kts'
rep(n,'versionCode = 151','versionCode = 152')
rep(n,'versionName = "0.19.124-p124-shadda-teacher-lesson-safe"',
      'versionName = "0.19.125-p125-real-word-audio-safe"')
for src,dst in [
    ('P125WordAudio.kt',base+'P125WordAudio.kt'),
    ('P125RealWordAudioTest.kt','app/src/test/java/com/ab/khatma/ui/screens/P125RealWordAudioTest.kt'),
    ('AUDIO_SOURCES.md','BUILD_REPRODUCIBILITY/p125/AUDIO_SOURCES.md')
]:
    target=root/dst
    assert not target.exists(),f"Won't overwrite {dst}"
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(repo/'tools/p125'/src,target)
print('P125 SAFE PATCH: added offline audio player and precise R01 word actions; other lessons unchanged')
