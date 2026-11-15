"""P114 guarded compile fix after original learning module patch."""
from pathlib import Path
import sys
root=Path(sys.argv[1]);k=root/'app/src/main/java/com/ab/khatma/ui/screens'
p=k/'P114QuranLearningScreen.kt';hfile=k/'P108MushafUi.kt'
assert p.exists() and hfile.exists()
s=p.read_text('utf8');h=hfile.read_text('utf8')
def one(t,a,b,label):
    assert t.count(a)==1,(label,t.count(a))
    return t.replace(a,b,1)
s=one(s,'onClick={if(available)({setScreen("groups")}) else ({setScreen("plan")})}',
    'onClick={if(available) setScreen("groups") else setScreen("plan")}','stage click')
s=one(s,'onClick={if(available)({setScreen("intro")}) else ({setScreen("plan")})}',
    'onClick={if(available) setScreen("intro") else setScreen("plan")}','group click')
s=one(s,'''Surface(onClick={if(correctAnswer==null)({
                                    selectedChoice=option
                                    correctAnswer=option==letter.symbol
                                    if(option==letter.symbol) {
                                        val updated=completed+letter.symbol
                                        completed=updated
                                        prefs.edit().putStringSet("completed_$userKey",updated.toSet()).apply()
                                    }
                                }) else ({})},modifier=Modifier.weight(1f),''',
'''Surface(onClick={
                                    if(correctAnswer==null) {
                                        selectedChoice=option
                                        correctAnswer=option==letter.symbol
                                        if(option==letter.symbol) {
                                            val updated=completed+letter.symbol
                                            completed=updated
                                            prefs.edit().putStringSet("completed_$userKey",updated.toSet()).apply()
                                        }
                                    }
                                },modifier=Modifier.weight(1f),''','quiz click')
assert s.count('Icons.Rounded.LockOutline')==3
s=s.replace('Icons.Rounded.LockOutline','Icons.Rounded.Lock')
h=one(h,'    onMeanings: () -> Unit, onQuiz: () -> Unit,\n    onSearch:',
      '    onMeanings: () -> Unit, onQuiz: () -> Unit, onLearn: () -> Unit,\n    onSearch:','hub signature')
a=h.index('            Surface(onClick=onQuiz,modifier=Modifier.fillMaxWidth(),')
b=h.index('\n        }\n        P53BottomNav',a)
assert h[a:b].count('اختبر حفظك')==1
h=h[:a]+'''            // P114: learning module directly beside the original group/individual quiz.
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                P114P108HubTile(Modifier.weight(1f),"اختبر حفظك","فردي وجماعي",
                    Icons.Rounded.FactCheck,onQuiz,Color(0xFFF3E8FA),Color(0xFFC7A5DD))
                P114P108HubTile(Modifier.weight(1f),"تعلّم قراءة القرآن","بالقاعدة النورانية",
                    Icons.Rounded.MenuBook,onLearn,Color(0xFFFFECC1),p108Gold)
            }'''+h[b:]
h+='''
/** The two adjacent tiles in the active P108 Quran hub. */
@Composable
private fun P114P108HubTile(modifier: Modifier,title:String,subtitle:String,
    icon:ImageVector,onClick:()->Unit,background:Color,accent:Color) {
    Surface(onClick=onClick,modifier=modifier.heightIn(min=114.dp),
        shape=RoundedCornerShape(18.dp),color=background,
        border=BorderStroke(1.dp,accent),shadowElevation=2.dp) {
        Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.Center) {
            Icon(icon,null,tint=p108Green,modifier=Modifier.size(28.dp))
            Spacer(Modifier.height(5.dp))
            Text(title,color=p108Ink,fontWeight=FontWeight.ExtraBold,fontSize=16.sp,
                lineHeight=21.sp,maxLines=2)
            Text(subtitle,color=Color(0xFF645C52),fontSize=11.sp,maxLines=2)
        }
    }
}
'''
assert 'P114P108HubTile' in h and 'onLearn: () -> Unit' in h
p.write_text(s,'utf8');hfile.write_text(h,'utf8')
print('P114 guarded Kotlin compile fix PASS')
