"""P110 VC137. Only change group-exam selection UI, no server or data changes."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
screen=root/'app/src/main/java/com/ab/khatma/ui/screens/P109GroupRoomScreen.kt'
gradle=root/'app/build.gradle.kts'
assert screen.is_file() and gradle.is_file()
s=screen.read_text(encoding='utf8'); g=gradle.read_text(encoding='utf8')
old='''                        DropdownMenu(expanded=scopeOpen,onDismissRequest={scopeOpen=false}) {
                            val max=when(scopeType){"surah","ayahs"->114;"juz"->30;"hizb"->60;else->604}
                            LazyColumn(Modifier.heightIn(max=300.dp).widthIn(min=220.dp)) {
                                items(max) { i->val n=i+1
                                    DropdownMenuItem(text={Text(when(scopeType){"surah","ayahs"->"سورة ${repo.surahName(n)}";
                                        "juz"->"الجزء $n";"hizb"->"الحزب $n";else->"الصفحة $n"})},
                                        onClick={scopeValue=n;scopeOpen=false})
                                }
                            }
                        }'''
new='''                        // P110: A bounded dialog prevents nested vertical scrolling inside DropdownMenu.
                        if(scopeOpen) Dialog(onDismissRequest={scopeOpen=false}) {
                            Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(19.dp),
                                color=examCream,border=BorderStroke(1.dp,examGold)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(when(scopeType){"surah","ayahs"->"اختر السورة";"juz"->"اختر الجزء";
                                        "hizb"->"اختر الحزب";else->"اختر الصفحة"},
                                        fontSize=21.sp,color=examGreen,fontWeight=FontWeight.Bold,
                                        modifier=Modifier.padding(bottom=6.dp))
                                    val max=when(scopeType){"surah","ayahs"->114;"juz"->30;"hizb"->60;else->604}
                                    LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp)) {
                                        items(max) { i->val n=i+1
                                            DropdownMenuItem(text={Text(when(scopeType){"surah","ayahs"->"سورة ${repo.surahName(n)}";
                                                "juz"->"الجزء $n";"hizb"->"الحزب $n";else->"الصفحة $n"},
                                                fontSize=18.sp,color=examInk,textAlign=TextAlign.Right,
                                                modifier=Modifier.fillMaxWidth())},
                                                onClick={scopeValue=n;scopeOpen=false})
                                        }
                                    }
                                }
                            }
                        }'''
def guarded(src, a,b, label):
    n=src.count(a)
    assert n==1,f"P110 guard {label} expected 1, got {n}"
    return src.replace(a,b,1)
s=guarded(s,'import androidx.compose.ui.unit.sp\n','import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.window.Dialog\n','import')
s=guarded(s,old,new,'picker')
g=guarded(g,'versionCode = 136','versionCode = 137','versionCode')
g=guarded(g,'versionName = "0.19.107-p109-group-exam-online-safe"',
 'versionName = "0.19.108-p110-group-surah-dialog-safe"','versionName')
assert 'DropdownMenu(expanded=scopeOpen' not in s
assert 'LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp))' in s
screen.write_text(s,encoding='utf8');gradle.write_text(g,encoding='utf8')
print('P110 safe patch PASS, UI picker + version only')
