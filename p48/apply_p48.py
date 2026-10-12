from pathlib import Path
import re,sys
root=Path(sys.argv[1]); base=root/'app/src/main/java/com/ab/khatma/secure'
gradle=root/'app/build.gradle.kts'
s=gradle.read_text()
assert s.count('versionCode = 68')==1 and 'applicationIdSuffix = ".preview"' in s
s=s.replace('versionCode = 68','versionCode = 69')
s=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "0.19.42-p48-reader-schedule"',s,count=1)
gradle.write_text(s)
target=base/'P48ReaderSchedule.kt'
assert not target.exists()
target.write_bytes((Path(__file__).parent/'P48ReaderSchedule.kt').read_bytes())
api=base/'SecureV2Api.kt'
s=api.read_text()
assert s.count('    data class TodayItem(')==1
api_part='''
    // P48 new read-only schedule plus authenticated completion/actions.
    data class ScheduleRow(val id:Long,val date:String,val juz:Int,val status:String,
        val canComplete:Boolean,val isTransfer:Boolean,val dueDate:String?,val history:List<String>)
    data class ReadingSchedule(val rows:List<ScheduleRow>,val overdueCount:Int,val pendingCount:Int)
    fun readingSchedule(token:String,groupId:Long,memberId:Long?=null):ReadingSchedule{
      val path=if(memberId==null) "/groups/"+groupId+"/my-schedule"
               else "/groups/"+groupId+"/members/"+memberId+"/schedule"
      val result=JSONObject(request(path,token));val rows=result.getJSONArray("rows")
      return ReadingSchedule((0 until rows.length()).map {i->
        val row=rows.getJSONObject(i);val events=row.optJSONArray("history")
        val history=if(events==null) emptyList() else (0 until events.length()).map{j->
          val h=events.getJSONObject(j)
          val kind=when(h.optString("action")){
            "reassign"->"إعادة توزيع";"excuse"->"إعفاء";"extend"->"تمديد";
            "received"->"تحويل من قارئ آخر";else->"قرار القائد"}
          kind+" • "+h.optString("by")+" • "+h.optString("date").take(10)
        }
        ScheduleRow(row.getLong("id"),row.getString("date"),row.getInt("juz"),
          row.getString("status"),row.optBoolean("can_complete"),
          row.optBoolean("is_transfer"),row.optString("due_date").takeIf{
            it.isNotBlank()&&it!="null"},history)
      },result.optInt("overdue_count"),result.optInt("pending_count"))
    }
    fun completeSchedule(token:String,row:ScheduleRow){
      if(row.isTransfer)request("/schedule/transfers/"+row.id+"/complete",token,"POST",JSONObject())
      else complete(token,row.id)
    }
    fun leaderScheduleAction(token:String,groupId:Long,assignmentId:Long,
        action:String,target:Long?=null,extendedTo:String?=null){
      require(action in listOf("reassign","extend","excuse"))
      val data=JSONObject().put("action",action)
      if(target!=null)data.put("target_membership_id",target)
      if(extendedTo!=null)data.put("extended_to",extendedTo)
      request("/groups/"+groupId+"/assignments/"+assignmentId+"/leader-action",token,"POST",data)
    }
'''
s=s.replace('    data class TodayItem(',api_part+'\n    data class TodayItem(',1)
api.write_text(s)
screen=base/'SecureV2Screen.kt';s=screen.read_text()
def replace(old,new,count=1):
 global s
 assert s.count(old)==count, (old[:90],s.count(old),count)
 s=s.replace(old,new,count)
replace('if (message == "تعذر تحديث المجموعات. اضغط تحديث للمحاولة مرة أخرى.") message = ""',
 'if (message.startsWith("تعذر تحديث المجموعات") || message.startsWith("تعذر الاتصال بالمجموعات")) message = ""')
replace('''        groupsLoadFailed = false
        p41LastGroupsFetchTime = android.os.SystemClock.elapsedRealtime()
    }
    LaunchedEffect(session?.token)''','''        groupsLoadFailed = false
        p41LastGroupsFetchTime = android.os.SystemClock.elapsedRealtime()
        if (message.startsWith("تعذر تحديث المجموعات") || message.startsWith("تعذر الاتصال بالمجموعات")) message = ""
    }
    LaunchedEffect(session?.token)''')
# Refresh Google login only when the refreshed identity maps to the same user.
old='is SecureV2Api.SessionExpiredException -> showExpiredSession()'
assert s.count(old)>=1
s=s.replace(old,'''is SecureV2Api.SessionExpiredException -> {
                        val refreshed = runCatching {
                            val idToken = KhatmaGoogleSignIn.firebaseIdToken(context)
                            withContext(Dispatchers.IO) { SecureV2Api.googleLogin(idToken) }
                        }.getOrNull()
                        if (refreshed != null && refreshed.userId == current.userId) {
                            store.save(refreshed); session = refreshed
                            groupsLoadFailed = false; message = ""
                        } else showExpiredSession()
                    }''',1)
replace('''                            if (groupTab == "read" && item.status == "active") {
                                Button(onClick = { section = "plans"; chatGroupId = null }) {
                                    Text("ختمات الصفحات وتسجيل القراءة")
                                }
                            }
                            if (groupTab == "read") {
                            OutlinedButton(onClick = { onOpenQuran(item.juz) }) { Text("قراءة الجزء في المصحف") }
''','''                            if (groupTab == "read") {
''')
replace('onPlans = { section = "plans"; chatGroupId = null })',
        'onPlans = { section = "schedule"; chatGroupId = null })')
replace('''onPlans = { section = "plans"; chatGroupId = null },
                                onSelect = { tab ->''',
        '''onPlans = { section = "schedule"; chatGroupId = null },
                                onSelect = { tab ->''')
replace('''                    if (section == "plans") {
                        val selectedGroup = groups.firstOrNull { it.groupId == selectedGroupId }''',
 '''                    if (section == "schedule" || section == "leaderSchedule") {
                        val chosen = groups.firstOrNull { it.groupId == selectedGroupId }
                        if (chosen != null && chosen.status == "active") {
                            P48ReaderSchedule(token = active.token, groupId = selectedGroupId,
                                isLeader = section == "leaderSchedule" && chosen.role == "leader",
                                groupName = chosen.groupName, onRead = { onOpenQuran(it) },
                                onChanged = { reload() },
                                onOpenPagePlans = { section = "plans"; chatGroupId = null })
                        }
                    }
                    if (section == "plans") {
                        val selectedGroup = groups.firstOrNull { it.groupId == selectedGroupId }''')
replace('''                            if (item.status != "active") Text("طلب الانضمام قيد المراجعة")''',
 '''                            if (item.role == "leader" && groupTab == "leader" && item.status == "active") {
                                OutlinedButton(onClick = { section = "leaderSchedule"; chatGroupId = null },
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text("متابعة المتأخرات وإجراءات القائد")
                                }
                            }
                            if (item.status != "active") Text("طلب الانضمام قيد المراجعة")''')
replace('''"plans" -> "ختمات الصفحات"''','''"plans" -> "ختمات الصفحات"
                                "schedule" -> "جدولي"
                                "leaderSchedule" -> "متابعة المتأخرات"''')
replace('''"plans" -> "group"
                                "join"''','''"plans" -> "group"
                                "schedule" -> "group"
                                "leaderSchedule" -> "group"
                                "join"''')
# Remove duplicated navigation at top; move to standard bottom in correct order.
start=s.index('                navigationIcon = {',s.index('    Scaffold('))
end=s.index('            )\n        },\n        bottomBar = {',start)
s=s[:start]+'                navigationIcon = {},\n                actions = {}\n'+s[end:]
start=s.index('        bottomBar = {',s.index('    Scaffold('))
brace=s.index('{',start);depth=0;end=None
for i in range(brace,len(s)):
 if s[i]=='{':depth+=1
 elif s[i]=='}':
  depth-=1
  if depth==0:end=i;break
assert end is not None
body=s[brace+1:end]
footer='''
                if (session != null && section != "home" && section != "profile") {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                section = when (section) {
                                    "plans", "schedule", "leaderSchedule" -> "group"
                                    "join" -> if (joinFromMeetup) "meetup" else "khatmas"
                                    "group" -> "khatmas"
                                    else -> "home"
                                }
                                if (section != "group") groupTab = "read"
                                chatGroupId = null
                            }, modifier = Modifier.weight(1f)) { Text("رجوع") }
                            Button(onClick = {
                                section = "home"; groupTab = "read"; chatGroupId = null
                            }, modifier = Modifier.weight(1f)) { Text("الرئيسية") }
                        }
                    }
                }
'''
s=s[:start]+'        bottomBar = {\n            Column {\n'+body+footer+'            }\n        }'+s[end+1:]
screen.write_text(s)
assert s.count('P48ReaderSchedule(')==1
for folder,extension in [('quran_pages','webp'),('quran_pages_colored','webp'),
 ('quran_verse_hitmaps','png'),('quran_verse_highlights','png')]:
 files=list((root/'app/src/main/assets'/folder).glob('*.'+extension))
 assert len(files)==604,(folder,len(files))
assert not list(root.rglob('*.p12')) and not list(root.rglob('*.jks'))
print('P48 apply PASS: 4 allowlisted files; all 604 Quran assets preserved; P47 adhan unchanged')


# P48.1 VC70: tightly scoped view corrections and two licensed offline adhan voices.
# All original files and voices remain present; user storage/preferences are untouched.
def safe_change(file, old, new):
    contents = file.read_text(encoding="utf-8")
    assert contents.count(old) == 1, (file.name, contents.count(old))
    file.write_text(contents.replace(old, new, 1), encoding="utf-8")

safe_change(gradle, 'versionCode = 69', 'versionCode = 70')
safe_change(gradle, 'versionName = "0.19.42-p48-reader-schedule"',
            'versionName = "0.19.43-p48-1-ui-adhan"')

# The dedicated "جدولي" tab already leads to the same page; keep the tab and reading action.
group_ui = base/'P25GroupScreens.kt'
safe_change(group_ui, '''        OutlinedButton(onClick = onPlans, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("جدول الختمة وتسجيل الإنجاز")
        }''',
'''        // Navigation remains in the dedicated "جدولي" tab above; no duplicated button here.''')

prayer_ui = root/'app/src/main/java/com/ab/khatma/ui/screens/PrayerPremiumScreen.kt'
# Always present the full, fixed heading; scroll only the controls below it.
safe_change(prayer_ui,
'''        ModalBottomSheet(
            onDismissRequest = { showSoundInfo = false },
            containerColor = Ivory,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {''',
'''        ModalBottomSheet(
            onDismissRequest = { showSoundInfo = false },
            containerColor = Ivory,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {''')
safe_change(prayer_ui,
'''                Modifier.fillMaxWidth().heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("صوت الأذان", color = DeepGreen, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("اختَر صوتًا واستمع إلى تجربة. كل الأصوات تعمل دون إنترنت.",
                    color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
                listOf(''',
'''                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("صوت الأذان", color = DeepGreen, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("اختَر صوتًا واستمع إلى تجربة. كل الأصوات تعمل دون إنترنت.",
                    color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
                Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(''')
safe_change(prayer_ui,
'''                Spacer(Modifier.height(18.dp))
            }
        }
    }
}''',
'''                Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}''')
safe_change(prayer_ui,
'''                    Triple("classic", "أذان عربي كلاسيكي", "تسجيل مختلف • 03:03 • CC BY-SA 4.0"),''',
'''                    Triple("classic", "أذان عربي كلاسيكي", "تسجيل مختلف • 03:03 • CC BY-SA 4.0"),
                    Triple("madinah", "أذان المسجد النبوي", "تسجيل ميداني • 03:07 • CC BY 3.0"),
                    Triple("shalqar", "أذان مسجد شالقار", "تسجيل مسجد بكازاخستان • 02:35 • CC BY-SA 4.0"),''')
safe_change(prayer_ui,
'''                        "وAndrewler (CC BY-SA 4.0). تفاصيل التراخيص محفوظة في ملف المصادر.",''',
'''                        "وAndrewler (CC BY-SA 4.0)، وejaz215 (CC BY 3.0)، وEsetok (CC BY-SA 4.0). تفاصيل التراخيص محفوظة في ملف المصادر.",''')

prefs_file = root/'app/src/main/java/com/ab/khatma/prayer/AdhanPreferences.kt'
contents = prefs_file.read_text(encoding='utf-8')
assert contents.count('listOf("default", "makkah", "classic")') == 2
prefs_file.write_text(contents.replace('listOf("default", "makkah", "classic")',
          'listOf("default", "makkah", "classic", "madinah", "shalqar")'), encoding='utf-8')
service_file = root/'app/src/main/java/com/ab/khatma/prayer/AdhanPlaybackService.kt'
safe_change(service_file,
'''                    "classic" -> R.raw.adhan_classic
                    else -> R.raw.adhan_full''',
'''                    "classic" -> R.raw.adhan_classic
                    "madinah" -> R.raw.adhan_madinah
                    "shalqar" -> R.raw.adhan_shalqar
                    else -> R.raw.adhan_full''')

(root/'ADHAN_EXTRA_P48_1_LICENSES.txt').write_text(
"""OFFLINE ADHAN AUDIO SOURCES, P48.1 VC70
1. Medina / Prophet's Mosque field recording
   Author/source: ejaz215 / Freesound (as credited by Wikimedia Commons)
   https://commons.wikimedia.org/wiki/File:33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg
   License: Creative Commons Attribution 3.0 Unported (CC BY 3.0)
   https://creativecommons.org/licenses/by/3.0/
   Distributed unchanged as adhan_madinah.ogg.

2. Shalqar mosque, Kazakhstan (Matey Qoqanuly Mosque)
   Author: Esetok
   https://commons.wikimedia.org/wiki/File:Adhan_in_Shalqar_mosque.webm
   License: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)
   https://creativecommons.org/licenses/by-sa/4.0/
   Audio track extracted and re-encoded from the original video to adhan_shalqar.ogg.
   The adapted audio track is distributed under CC BY-SA 4.0.
Notes: No claim is made identifying an unnamed recording's individual muezzin.
The previous three recordings remain untouched, with their existing credit files.
""", encoding="utf-8")
assert (root/'app/src/main/res/raw/adhan_classic.ogg').exists()
assert (root/'app/src/main/res/raw/adhan_makkah.ogg').exists()
assert (root/'app/src/main/res/raw/adhan_full.ogg').exists()
print("P48.1 VC70 safe UI/adhan patch applied; extra audio downloaded in next CI step")
