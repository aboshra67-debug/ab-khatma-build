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
assert s.count(old)>=2
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
