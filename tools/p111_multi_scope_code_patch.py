"""Safe additive P111/VC138 group exam UI patch from P110/VC137.
Only group room screen and Android version fields are altered.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1])
f=root/"app/src/main/java/com/ab/khatma/ui/screens/P109GroupRoomScreen.kt"
v=root/"app/build.gradle.kts"
assert f.is_file() and v.is_file(),"P110 baseline missing"
s=f.read_text(encoding="utf-8")
g=v.read_text(encoding="utf-8")

def once(text,old,new,label):
    n=text.count(old)
    assert n==1, f"Unsafe P111 patch {label}: {n} anchors"
    return text.replace(old,new,1)

s=once(s,'    var scopeValue by remember { mutableIntStateOf(2) }',
'''    var scopeValue by remember { mutableIntStateOf(2) }
    // Multi-selection is limited to surahs and juz; single scope is default.
    val scopeValues=remember { mutableStateListOf(2) }
    var createdCode by remember { mutableStateOf("") }
    var copiedCode by remember { mutableStateOf(false) }''',"state")
s=once(s,'                color=if(key==selected)examGreen else Color.White,',
'                color=if(key==selected)Color(0xFFFFE6A4) else Color.White,',"selected chip")
s=once(s,'                    color=if(key==selected) Color.White else examInk,fontWeight=FontWeight.Bold,fontSize=15.sp)',
'                    color=examInk,fontWeight=FontWeight.Bold,fontSize=17.sp)',"readable chips")
s=once(s,'                        scopeType=it;scopeValue=if(it=="surah"||it=="ayahs")2 else 1\n',
'''                        scopeType=it;scopeValue=if(it=="surah"||it=="ayahs")2 else 1
                        scopeValues.clear();scopeValues.add(scopeValue)
''',"mode switch")

anchor='                    Box {\n                        OutlinedButton(onClick={scopeOpen=true}'
assert s.count(anchor)==1,"selection block anchor invalid"
start=s.index(anchor)
end=s.index('                    if(scopeType=="ayahs") {',start)
assert s[start:end].count('LazyColumn(')==1
picker='''                    val multiScope=(scopeType=="surah"||scopeType=="juz")
                    OutlinedButton(onClick={scopeOpen=true},modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(15.dp)) {
                        Text(when {
                            multiScope && scopeValues.size>1 -> "تم اختيار __D__{scopeValues.size} نطاقات"
                            scopeType=="surah"||scopeType=="ayahs" -> "سورة __D__{repo.surahName(scopeValue)}"
                            scopeType=="juz" -> "الجزء __D__scopeValue"
                            scopeType=="hizb" -> "الحزب __D__scopeValue"
                            else -> "الصفحة __D__scopeValue"
                        },fontSize=19.sp,color=examInk,modifier=Modifier.weight(1f),textAlign=TextAlign.Right)
                        Icon(Icons.Rounded.ArrowDropDown,null,tint=examGreen)
                    }
                    if(multiScope) {
                        Text("حدد سورة أو جزءًا واحدًا أو أكثر (حتى 10 اختيارات)",
                            color=examInk,fontSize=15.sp)
                        Text(if(scopeType=="surah") scopeValues.joinToString("، ") { repo.surahName(it) }
                             else scopeValues.joinToString("، ") { "الجزء __D__it" },
                            color=examGreen,fontSize=16.sp,fontWeight=FontWeight.Bold,
                            modifier=Modifier.fillMaxWidth())
                    }
                    // A bounded dialog avoids nested DropdownMenu/LazyColumn scroll constraints.
                    if(scopeOpen) Dialog(onDismissRequest={scopeOpen=false}) {
                        Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(19.dp),
                            color=examCream,border=BorderStroke(1.dp,examGold)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(when(scopeType){"surah","ayahs"->"اختر السور";"juz"->"اختر الأجزاء";
                                    "hizb"->"اختر الحزب";else->"اختر الصفحة"},
                                    fontSize=21.sp,color=examGreen,fontWeight=FontWeight.Bold,
                                    modifier=Modifier.padding(bottom=6.dp))
                                val max=when(scopeType){"surah","ayahs"->114;"juz"->30;"hizb"->60;else->604}
                                LazyColumn(Modifier.fillMaxWidth().heightIn(max=340.dp)) {
                                    items(max) { i->
                                        val n=i+1
                                        DropdownMenuItem(text={
                                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                                Text(when(scopeType){"surah","ayahs"->"سورة __D__{repo.surahName(n)}";
                                                    "juz"->"الجزء __D__n";"hizb"->"الحزب __D__n";else->"الصفحة __D__n"},
                                                    fontSize=18.sp,color=examInk,textAlign=TextAlign.Right,
                                                    modifier=Modifier.weight(1f))
                                                if(multiScope) Checkbox(checked=scopeValues.contains(n),
                                                    onCheckedChange=null,
                                                    colors=CheckboxDefaults.colors(checkedColor=examGreen))
                                            }
                                        },onClick={
                                            if(multiScope) {
                                                if(scopeValues.contains(n)) {
                                                    if(scopeValues.size>1) scopeValues.remove(n)
                                                } else if(scopeValues.size<10) scopeValues.add(n)
                                                scopeValue=scopeValues.first()
                                            } else {
                                                scopeValue=n;scopeValues.clear();scopeValues.add(n);scopeOpen=false
                                            }
                                        })
                                    }
                                }
                                if(multiScope) Button(onClick={scopeOpen=false},
                                    modifier=Modifier.fillMaxWidth(),
                                    colors=ButtonDefaults.buttonColors(containerColor=examGreen)) {
                                    Text("تم الاختيار (__D__{scopeValues.size})",fontSize=18.sp)
                                }
                            }
                        }
                    }
'''
s=s[:start]+picker+s[end:]

pop='''    // Only the six-digit code actually returned by the authenticated API is displayed.
    if(createdCode.isNotEmpty()) AlertDialog(onDismissRequest={createdCode=""},
        title={Text("تم إنشاء غرفة الامتحان",fontWeight=FontWeight.Bold,color=examGreen)},
        text={Column(horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("شارك هذا الكود مع المتسابقين",color=examInk,fontSize=18.sp)
            Text(createdCode,fontSize=36.sp,fontWeight=FontWeight.ExtraBold,
                letterSpacing=5.sp,color=examInk,textAlign=TextAlign.Center,
                modifier=Modifier.fillMaxWidth())
            if(copiedCode)Text("تم نسخ الكود",color=examGreen,fontSize=16.sp)
        }},
        confirmButton={Button(onClick={
            val clip=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clip.setPrimaryClip(android.content.ClipData.newPlainText("كود امتحان ختمة",createdCode))
            copiedCode=true
        },colors=ButtonDefaults.buttonColors(containerColor=examGreen)) {Text("نسخ الكود")}},
        dismissButton={TextButton(onClick={createdCode=""}) {Text("الذهاب للغرفة")}})

'''
s=once(s,'    Column(Modifier.fillMaxSize().background(examCream)) {',
pop+'    Column(Modifier.fillMaxSize().background(examCream)) {',"server issued code dialog")

old='''                Button(onClick={api("","POST",JSONObject()
                    .put("title",title).put("scope_type",scopeType).put("scope_value",scopeValue)
                    .put("from_ayah",fromAyah).put("to_ayah",toAyah)
                    .put("question_count",count).put("level",level).put("start_mode",startMode)
                    .put("timed",timed).put("duration_minutes",minutes.toIntOrNull()?:20)
                    .put("leader_competes",compete)) {updateRoom(it);screen="room"}},
                    enabled=!busy&&title.trim().length>=2,modifier=Modifier.fillMaxWidth().height(54.dp),'''
new='''                Button(onClick={api("","POST",JSONObject()
                    .put("title",title).put("scope_type",scopeType).put("scope_value",scopeValues.first())
                    .put("scope_values",JSONArray(scopeValues.toList()))
                    .put("from_ayah",fromAyah).put("to_ayah",toAyah)
                    .put("question_count",count).put("level",level).put("start_mode",startMode)
                    .put("timed",timed).put("duration_minutes",minutes.toIntOrNull()?:20)
                    .put("leader_competes",compete)) {created ->
                        updateRoom(created);screen="room"
                        val returnedCode=created.optString("code")
                        if(returnedCode.matches(Regex("^[0-9]{6}$"))) {
                            createdCode=returnedCode;copiedCode=false
                        } else error="تم إنشاء الغرفة لكن الكود غير ظاهر؛ اضغط تحديث الغرفة"
                    }},
                    enabled=!busy&&title.trim().length>=2&&scopeValues.isNotEmpty(),modifier=Modifier.fillMaxWidth().height(56.dp),'''
s=once(s,old,new,"submit multi scope and code")
s=once(s,'                        Text(room?.optString("code").orEmpty(),fontSize=33.sp,color=examInk,fontWeight=FontWeight.ExtraBold,',
'''                        Text(room?.optString("code").orEmpty().ifBlank { "الكود غير ظاهر؛ اضغط تحديث الغرفة" },
                            fontSize=33.sp,color=examInk,fontWeight=FontWeight.ExtraBold,''',"empty code hint")
s=once(s,'                        OutlinedButton(onClick={val message="كود امتحان حفظ القرآن:',
'''                        OutlinedButton(onClick={refresh()},modifier=Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Refresh,null);Text(" تحديث بيانات الغرفة")
                        }
                        OutlinedButton(onClick={val message="كود امتحان حفظ القرآن:''',"refresh button")
g=once(g,'versionCode = 137','versionCode = 138',"versionCode")
g=once(g,'versionName = "0.19.108-p110-group-surah-dialog-safe"',
'versionName = "0.19.109-p111-group-multi-scope-code-safe"',"versionName")
s=s.replace("__D__","$")
assert '"scope_values",JSONArray(scopeValues.toList())' in s
assert s.count('if(createdCode.isNotEmpty()) AlertDialog')==1
assert 'versionCode = 138' in g
f.write_text(s,encoding="utf-8");v.write_text(g,encoding="utf-8")
print("P111 VC138 safe patch PASS: 2 UI/version files, no deletes")
