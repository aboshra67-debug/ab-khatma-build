"""Guarded P112/VC139 additive WhatsApp room invite patch from P111/VC138.
Only group-room UI and version fields change; no server/database edits.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1])
f=root/"app/src/main/java/com/ab/khatma/ui/screens/P109GroupRoomScreen.kt"
v=root/"app/build.gradle.kts"
assert f.is_file() and v.is_file(), "P111 VC138 full source missing"
s=f.read_text(encoding="utf-8");g=v.read_text(encoding="utf-8")

def once(text,old,new,label):
    assert text.count(old)==1,f"P112 unsafe {label}: {text.count(old)} anchors"
    return text.replace(old,new,1)

helper='''private fun examShareInviteByWhatsapp(context:Context, roomCode:String):Boolean {
    if(!roomCode.matches(Regex("^[0-9]{6}$"))) return false
    val message="*دعوة لاختبار حفظ القرآن – AB ختمة*\\n" +
        "كود غرفة الامتحان: $roomCode\\n\\n" +
        "للانضمام: افتح تطبيق AB ختمة ← اختبر حفظك ← الانضمام إلى امتحان، ثم اكتب الكود."
    fun shareIntent()=Intent(Intent.ACTION_SEND).apply {
        type="text/plain"
        putExtra(Intent.EXTRA_TEXT,message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Prefer WhatsApp and WhatsApp Business; preserve native share sheet fallback.
    for(pkg in listOf("com.whatsapp","com.whatsapp.w4b")) {
        try {
            context.startActivity(shareIntent().apply { setPackage(pkg) })
            return true
        } catch (_:android.content.ActivityNotFoundException) {
        } catch (_:SecurityException) {
        }
    }
    return try {
        context.startActivity(Intent.createChooser(shareIntent(),"مشاركة دعوة الامتحان"))
        true
    } catch (_:android.content.ActivityNotFoundException) { false }
      catch (_:SecurityException) { false }
}

'''
s=once(s,"/** Real online group examinations: all scoring and permissions are exclusively server-side. */",
    helper+"/** Real online group examinations: all scoring and permissions are exclusively server-side. */","helper")
s=once(s,
'            if(copiedCode)Text("تم نسخ الكود",color=examGreen,fontSize=16.sp)',
'''            OutlinedButton(onClick={
                if(!examShareInviteByWhatsapp(context,createdCode))
                    android.widget.Toast.makeText(context,"تعذّرت مشاركة الكود",android.widget.Toast.LENGTH_SHORT).show()
            },modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Share,null,tint=examGreen)
                Text(" مشاركة عبر واتساب",color=examInk,fontSize=18.sp)
            }
            if(copiedCode)Text("تم نسخ الكود",color=examGreen,fontSize=16.sp)''',
"create dialog button")
s=once(s,
'''                        OutlinedButton(onClick={val message="كود امتحان حفظ القرآن: ${room?.optString("code").orEmpty()}"
                            val share=Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,message)}
                            context.startActivity(Intent.createChooser(share,"مشاركة كود الامتحان"))
                        },modifier=Modifier.fillMaxWidth()) {Icon(Icons.Rounded.Share,null);Text(" مشاركة كود الغرفة")}''',
'''                        OutlinedButton(onClick={
                            val roomCode=room?.optString("code").orEmpty()
                            if(!examShareInviteByWhatsapp(context,roomCode))
                                android.widget.Toast.makeText(context,"حدّث الغرفة لإظهار كود صحيح قبل المشاركة",android.widget.Toast.LENGTH_LONG).show()
                        },modifier=Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Share,null,tint=examGreen)
                            Text(" مشاركة عبر واتساب",color=examInk,fontSize=18.sp)
                        }''',
"room invite button")
g=once(g,"versionCode = 138","versionCode = 139","version code")
g=once(g,'versionName = "0.19.109-p111-group-multi-scope-code-safe"',
    'versionName = "0.19.110-p112-whatsapp-room-invite-safe"',"version name")
s=s.replace("$","__DOLLAR__")
assert s.count("examShareInviteByWhatsapp(context,")==2
assert s.count("مشاركة عبر واتساب")==2
assert "com.whatsapp.w4b" in s and "Intent.createChooser" in s
assert "versionCode = 139" in g
f.write_text(s.replace("__DOLLAR__","$"),encoding="utf-8")
v.write_text(g,encoding="utf-8")
print("P112 VC139 safe WhatsApp patch PASS: 2 source files changed, no deletions")
