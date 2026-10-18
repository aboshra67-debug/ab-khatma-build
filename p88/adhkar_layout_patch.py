from pathlib import Path
import sys
p=Path(sys.argv[1])/'app/src/main/java/com/ab/khatma/ui/screens/P85IbtehalScreen.kt'
s=p.read_text(encoding='utf-8')
def replace_once(old,new):
    global s
    assert old in s, 'P88 expected anchor missing: '+old[:70]
    s=s.replace(old,new,1)
replace_once('import androidx.compose.foundation.background\n','import androidx.compose.foundation.background\nimport androidx.compose.ui.Alignment\n')
replace_once('    var artist by remember { mutableStateOf("الكل") }','    var artist by remember { mutableStateOf("الكل") }\n    var categoryPage by remember { mutableStateOf(true) }')
replace_once('BackHandler { if (officialPreview != null) officialPreview = null else { stop();onBack() } }',
'''BackHandler {
        if(officialPreview != null) officialPreview = null
        else if(!categoryPage) { stop();categoryPage=true;artist="الكل";filter="" }
        else { stop();onBack() }
    }''')
replace_once('Text("الابتهالات والتواشيح",fontWeight=FontWeight.Bold,color=P87Green,fontSize=24.sp)',
'''Text(if(categoryPage) "الابتهالات والتواشيح" else if(artist=="الكل") "كل التسجيلات" else artist,
                fontWeight=FontWeight.Bold,color=P87Green,fontSize=24.sp)
            if(!categoryPage) TextButton(onClick={stop();categoryPage=true;artist="الكل";filter=""}) {
                Text("‹ الرجوع إلى أقسام الابتهالات")
            }''')
replace_once('''        item {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("الكل","نصر الدين طوبار","سيد النقشبندي").forEach { a ->''',
'''        if(categoryPage && filter.isBlank()) {
            item { Text("الأقسام",fontWeight=FontWeight.Bold,fontSize=18.sp,color=P87Green) }
            items(listOf("كل التسجيلات","نصر الدين طوبار","سيد النقشبندي")) { section ->
                val count=if(section=="كل التسجيلات") p87Tracks.size else p87Tracks.count{it.artist==section}
                Card(onClick={stop();artist=if(section=="كل التسجيلات")"الكل" else section;categoryPage=false},
                    modifier=Modifier.fillMaxWidth().height(102.dp),shape=RoundedCornerShape(20.dp),
                    colors=CardDefaults.cardColors(containerColor=P87Green)) {
                    Box(Modifier.fillMaxSize()) {
                        Text("♫",modifier=Modifier.align(Alignment.TopEnd).padding(12.dp),
                            fontSize=32.sp,color=Color.White.copy(alpha=.55f))
                        Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                            Text(section,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White)
                            Text(count.toString()+" تسجيلات صوتية",fontSize=12.sp,color=Color.White.copy(alpha=.84f))
                        }
                    }
                }
            }
        }
        if(!categoryPage) item {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("الكل","نصر الدين طوبار","سيد النقشبندي").forEach { a ->''')
replace_once('        items(visible,key={it.id}) { track ->','        if(!categoryPage || filter.isNotBlank()) items(visible,key={it.id}) { track ->')
replace_once('        if(visible.isEmpty())item{Text("لا توجد تسجيلات تطابق البحث",modifier=Modifier.padding(12.dp))}',
             '        if(visible.isEmpty() && (!categoryPage || filter.isNotBlank()))item{Text("لا توجد تسجيلات تطابق البحث",modifier=Modifier.padding(12.dp))}')
old='''                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        Button(onClick={play(track)},enabled=attached&&!loading&&current!=track.id,
                            colors=ButtonDefaults.buttonColors(containerColor=P87Green)) {Text(if(current==track.id&&playing)"يعمل الآن" else "▶ تشغيل",fontSize=12.sp)}
                        OutlinedButton(onClick={pending=track.id;chooser.launch(arrayOf("audio/*"))}) {Text(if(attached)"تغيير الملف" else "ربط ملف",fontSize=12.sp)}
                    }
                    TextButton(onClick={stop(); P52SurahAudio.pause(); officialPreview=track.official}) {
                        Text("♫ استمع من أرشيف الإذاعة داخل التطبيق",fontSize=12.sp)
                    }'''
new='''                    Button(onClick={stop();P52SurahAudio.pause();officialPreview=track.official},
                        modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=P87Green)) {
                        Text("▶ استماع داخل التطبيق",fontSize=13.sp)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        if(attached) OutlinedButton(onClick={play(track)},enabled=!loading&&current!=track.id) {
                            Text("تشغيل الملف المحلي",fontSize=11.sp)
                        }
                        TextButton(onClick={pending=track.id;chooser.launch(arrayOf("audio/*"))}) {
                            Text(if(attached)"تغيير الملف" else "إضافة ملف محلي اختياري",fontSize=11.sp)
                        }
                    }'''
replace_once(old,new)
replace_once('''لحفظ حقوق التسجيلات، اربط ملفك الصوتي المصرّح به مرة واحدة؛ بعدها يشتغل داخل التطبيق من غير إنترنت.''',
'''للاستماع اضغط التسجيل من مشغّل الإذاعة داخل التطبيق بالإنترنت. ويمكنك إضافة ملف صوتي مرخّص للاستماع دون إنترنت.''')
p.write_text(s,encoding='utf-8')
print('P88 audio sections and adhkar-like chapter navigation safely applied')
