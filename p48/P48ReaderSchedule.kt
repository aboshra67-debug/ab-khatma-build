package com.ab.khatma.secure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

private val p48Green=Color(0xFF14604A)
private val p48Ink=Color(0xFF203D33)
private fun p48Day(d:String)=runCatching { when(LocalDate.parse(d).dayOfWeek) {
  DayOfWeek.SATURDAY->"السبت"; DayOfWeek.SUNDAY->"الأحد"; DayOfWeek.MONDAY->"الاثنين";
  DayOfWeek.TUESDAY->"الثلاثاء"; DayOfWeek.WEDNESDAY->"الأربعاء";
  DayOfWeek.THURSDAY->"الخميس"; DayOfWeek.FRIDAY->"الجمعة" }}.getOrDefault("")
private fun p48Date(d:String)=runCatching {
  LocalDate.parse(d).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(d)
private fun p48Label(r:SecureV2Api.ScheduleRow)=when(r.status) {
 "completed"->"تمت القراءة ✓";"overdue"->"متأخر";"reassigned"->"أعاد القائد توزيعه";
 "excused"->"إعفاء القائد";"extended"->"تمديد الموعد";"upcoming"->"قادم";else->"مطلوب اليوم"
}
@Composable
internal fun P48ReaderSchedule(token:String,groupId:Long,isLeader:Boolean,groupName:String,
  todayAssignment:SecureV2Api.TodayItem?,
  onRead:(Int)->Unit,onChanged:()->Unit,onOpenPagePlans:()->Unit) {
 val scope=rememberCoroutineScope()
 var refresh by remember(token,groupId){mutableIntStateOf(0)}
 var members by remember(token,groupId){mutableStateOf<List<SecureV2Api.MemberInfo>>(emptyList())}
 var selected by remember(token,groupId){mutableStateOf<Long?>(null)}
 var schedule by remember(token,groupId){mutableStateOf<SecureV2Api.ReadingSchedule?>(null)}
 var loading by remember{mutableStateOf(true)}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 var menu by remember{mutableStateOf(false)}
 var confirming by remember{mutableStateOf<SecureV2Api.ScheduleRow?>(null)}
 var action by remember{mutableStateOf<SecureV2Api.ScheduleRow?>(null)}
 var toMember by remember{mutableStateOf<Long?>(null)}
 var days by remember{mutableStateOf("3")}
 // P49: read previously stored, leader-approved page-plan dates without creating records.
 var pageRun by remember(token,groupId){mutableStateOf<PageApi.Run?>(null)}
 var pageDuties by remember(token,groupId){mutableStateOf<List<PageApi.Task>>(emptyList())}
 var pageConfirm by remember{mutableStateOf<PageApi.Task?>(null)}
 LaunchedEffect(token,groupId,isLeader,selected,refresh) {
   loading=true; error=""
   try {
     if(isLeader&&members.isEmpty()){
       members=withContext(Dispatchers.IO){SecureV2Api.groupStatus(token,groupId).members}
       if(selected==null)selected=members.firstOrNull()?.id
     }
     val savedPlans = withContext(Dispatchers.IO) {
       runCatching { PageApi.runs(token,groupId) }.getOrDefault(emptyList())
     }
     val cairoToday=LocalDate.now(ZoneId.of("Africa/Cairo"))
     pageRun = savedPlans.firstOrNull {
       runCatching {
         !LocalDate.parse(it.startDate).isAfter(cairoToday) &&
         !LocalDate.parse(it.endDate).isBefore(cairoToday)
       }.getOrDefault(false)
     } ?: savedPlans.maxByOrNull{it.id}
     pageDuties = pageRun?.let{run ->
       withContext(Dispatchers.IO) {
         PageApi.tasks(token,run.id).filter{!isLeader||it.memberId==selected}
       }
     } ?: emptyList()
     if(!isLeader||selected!=null) schedule=withContext(Dispatchers.IO){
       SecureV2Api.readingSchedule(token,groupId,if(isLeader)selected else null)
     }
   }catch(e:Exception){
     val saved = todayAssignment
     // P35 staging has no P48 history endpoint yet. Show ONLY today's saved duty;
     // never synthesize older dues or interfere with leader's manual distribution.
     if(!isLeader && e.message == "الخدمة غير متاحة على خادم التجربة" &&
          saved?.assignmentId != null && saved.juz != null) {
       val row=SecureV2Api.ScheduleRow(saved.assignmentId,
         LocalDate.now(ZoneId.of("Africa/Cairo")).toString(),saved.juz,
         if(saved.completed)"completed" else "today",!saved.completed,false,null,emptyList())
       schedule=SecureV2Api.ReadingSchedule(listOf(row),0,if(saved.completed)0 else 1)
       error="يظهر تكليف اليوم المحفوظ فقط. سجل المتأخرات وإجراءات القائد ينتظر تحديث السيرفر."
     } else error=e.message?:"تعذر تحميل الجدول"
   }
   finally{loading=false}
 }
 fun save(job:suspend()->Unit) {
   if(busy)return
   scope.launch {busy=true;error=""
     try {job();refresh++;onChanged()}catch(e:Exception){error=e.message?:"تعذر الحفظ"}
     finally{busy=false}
   }
 }
 Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(if(isLeader)"متابعة الأجزاء المتأخرة" else "جدولي في الختمة",
     fontWeight=FontWeight.Bold,color=p48Ink,style=MaterialTheme.typography.titleLarge)
   Text(groupName,color=p48Green)
   if(isLeader)Box{
     OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()){
       Text(members.firstOrNull{it.id==selected}?.name?:"اختيار القارئ")
     }
     DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
       members.forEach { person->DropdownMenuItem(text={Text(person.name)},onClick={
         selected=person.id;menu=false;schedule=null
       }) }
     }
   }
   if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
   if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
   schedule?.let { data->
     val today=LocalDate.now(ZoneId.of("Africa/Cairo"))
     val anchor=pageRun?.let{run ->
       runCatching{
         val first=LocalDate.parse(run.startDate)
         val last=LocalDate.parse(run.endDate)
         val since=ChronoUnit.DAYS.between(first,today).coerceIn(0,ChronoUnit.DAYS.between(first,last).coerceAtLeast(0))
         first.plusDays((since/30)*30)
       }.getOrNull()
     } ?: data.rows.mapNotNull{runCatching{LocalDate.parse(it.date)}.getOrNull()}
       .minOrNull()?.let{if(it.isBefore(today.minusDays(29)))today.minusDays(29) else it} ?: today
     val calendarDays=(0 until 30).map{anchor.plusDays(it.toLong())}
     val usingPlan=pageRun!=null
     val overdue=if(usingPlan)pageDuties.count{!it.finished && runCatching{LocalDate.parse(it.date).isBefore(today)}.getOrDefault(false)}
       else data.overdueCount
     val pending=if(usingPlan)pageDuties.count{!it.finished && runCatching{!LocalDate.parse(it.date).isAfter(today)}.getOrDefault(false)}
       else data.pendingCount
     Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF9EC))){
       Column(Modifier.fillMaxWidth().padding(12.dp)){
         Text("جدول 30 يومًا • ${p48Date(anchor.toString())} — ${p48Date(calendarDays.last().toString())}",
             fontWeight=FontWeight.Bold,color=p48Ink)
         Text("${overdue} جزء/تكليف متأخر • المطلوب حتى اليوم ${pending}",
             fontWeight=FontWeight.Bold,color=Color(0xFF98502B))
         Text("الأيام دون توزيع محفوظ تظهر بدون رقم جزء؛ لا تُنشأ تكليفات افتراضية.",
             color=p48Ink,fontSize=11.sp)
       }
     }
     Surface(color=Color(0xFFE6F0EA),shape=RoundedCornerShape(10.dp)){
       Row(Modifier.fillMaxWidth().padding(8.dp)){
         P48Cell("اليوم",1.1f,true);P48Cell("التاريخ",1.4f,true)
         P48Cell("الجزء",.7f,true);P48Cell("الحالة",1.4f,true)
       }
     }
     // Each calendar day is visible even when a leader has not saved a duty for it.
     calendarDays.forEachIndexed { i,day->
       val duties=if(usingPlan)pageDuties.filter{it.date==day.toString()} else emptyList()
       val juzRows=if(usingPlan)emptyList() else data.rows.filter{it.date==day.toString()}
       if(duties.isEmpty()&&juzRows.isEmpty()){
         Card(shape=RoundedCornerShape(10.dp),
           border=BorderStroke(if(day==today)2.dp else 1.dp,if(day==today)p48Green else Color(0xFFE4EAE3)),
           colors=CardDefaults.cardColors(containerColor=if(day==today)Color(0xFFE6F0EA) else Color.White)){
           Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
             P48Cell(p48Day(day.toString()),1.1f)
             P48Cell(p48Date(day.toString()),1.4f)
             P48Cell("—",.7f)
             P48Cell("لم يُوزّع بعد",1.4f,color=Color.Gray)
           }
         }
       }
       duties.forEach{task->
         val taskStatus=if(task.finished)"completed" else if(day.isBefore(today))"overdue"
             else if(day.isAfter(today))"upcoming" else "today"
         val starts=intArrayOf(1,22,42,62,82,102,121,142,162,182,201,222,242,262,282,302,
             322,342,362,382,402,422,442,462,482,502,522,542,562,582,605)
         val whole=(0 until 30).firstOrNull{starts[it]==task.start&&starts[it+1]-1==task.end}
         val portion=if(whole!=null)"الجزء ${whole+1}" else "ص ${task.start}–${task.end}"
         Card(shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,p48Green),
           colors=CardDefaults.cardColors(containerColor=if(day==today)Color(0xFFE6F0EA) else Color.White)){
           Column(Modifier.fillMaxWidth().padding(9.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
             Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
               P48Cell(p48Day(day.toString()),1.1f)
               P48Cell(p48Date(day.toString()),1.4f)
               P48Cell(portion,.7f)
               P48Cell(p48Label(SecureV2Api.ScheduleRow(task.id,task.date,whole?.plus(1)?:0,
                   taskStatus,false,false,null,emptyList())),1.4f,
                   color=if(taskStatus=="overdue")Color(0xFFBB442A) else p48Green)
             }
             if(task.read>=task.start && !task.finished)Text("قرأت حتى صفحة ${task.read}",fontSize=11.sp)
             if(!isLeader&&!task.finished&&!day.isAfter(today)){
               Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                 OutlinedButton(onClick=onOpenPagePlans,modifier=Modifier.weight(1f)){
                   Text("ابدأ القراءة",fontSize=11.sp)
                 }
                 Button(onClick={pageConfirm=task},enabled=!busy,modifier=Modifier.weight(1f)){
                   Text("تمت القراءة",fontSize=11.sp)
                 }
               }
             }
           }
         }
       }
       juzRows.forEach { r->
       Card(shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Color(0xFFE4EAE3)),
         colors=CardDefaults.cardColors(containerColor=if(i%2==0)Color.White else Color(0xFFFAFCFA))){
         Column(Modifier.fillMaxWidth().padding(8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
           Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
             P48Cell(p48Day(r.date),1.1f);P48Cell(p48Date(r.date),1.4f)
             P48Cell("${r.juz}",.7f)
             P48Cell(p48Label(r),1.4f,color=if(r.status=="overdue")Color(0xFFBB442A)else p48Green)
           }
           if(r.history.isNotEmpty()) Text(r.history.joinToString(" • "),fontSize=11.sp,color=Color(0xFF766042))
           if(!isLeader&&r.canComplete)Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
             OutlinedButton(onClick={onRead(r.juz)},modifier=Modifier.weight(1f)){
               Text(if(r.status=="overdue")"تعويض القراءة" else "ابدأ القراءة",fontSize=11.sp,maxLines=1)
             }
             Button(onClick={confirming=r},enabled=!busy,modifier=Modifier.weight(1f),
               colors=ButtonDefaults.buttonColors(containerColor=p48Green)){
               Text("تمت القراءة",fontSize=11.sp,maxLines=1)
             }
           }
           if(isLeader&&r.status=="overdue"&&!r.isTransfer)
             OutlinedButton(onClick={action=r;toMember=null},enabled=!busy,
               modifier=Modifier.fillMaxWidth()){Text("إجراء القائد")}
         }
       }
       }
     }
     if(!isLeader)OutlinedButton(onClick=onOpenPagePlans,modifier=Modifier.fillMaxWidth()){
       Text("ختمات الصفحات")
     }
   }
   OutlinedButton(onClick={refresh++},enabled=!loading&&!busy,modifier=Modifier.fillMaxWidth()){
     Text("تحديث الجدول")
   }
 }
 confirming?.let{r->AlertDialog(onDismissRequest={confirming=null},
   title={Text("تأكيد القراءة")},text={Text("هل قرأت الجزء ${r.juz} كاملًا؟")},
   confirmButton={Button(onClick={confirming=null;save{withContext(Dispatchers.IO){
     SecureV2Api.completeSchedule(token,r)}}}){Text("نعم، تمت القراءة")}},
   dismissButton={TextButton(onClick={confirming=null}){Text("إلغاء")}})
 }
 pageConfirm?.let{task->AlertDialog(onDismissRequest={pageConfirm=null},
   title={Text("تأكيد قراءة الصفحات")},
   text={Text("هل أتممت الصفحات من ${task.start} إلى ${task.end}؟")},
   confirmButton={Button(onClick={pageConfirm=null;save{withContext(Dispatchers.IO){
     PageApi.save(token,task.id,task.end)
   }}}){Text("نعم، تمت القراءة")}},
   dismissButton={TextButton(onClick={pageConfirm=null}){Text("إلغاء")}})
 }
 action?.let{r->
   AlertDialog(onDismissRequest={action=null},title={Text("إجراء القائد — الجزء ${r.juz}")},
     text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
       Text("يظهر الإجراء وتاريخه في سجل القارئ.")
       var choosing by remember{mutableStateOf(false)}
       Box{
         OutlinedButton(onClick={choosing=true},modifier=Modifier.fillMaxWidth()){
           Text(members.firstOrNull{it.id==toMember}?.name?:"اختر قارئًا آخر")
         }
         DropdownMenu(expanded=choosing,onDismissRequest={choosing=false}){
           members.filter{it.id!=selected}.forEach {p->
             DropdownMenuItem(text={Text(p.name)},onClick={toMember=p.id;choosing=false})
           }
         }
       }
       Button(onClick={val target=toMember;if(target!=null){action=null;save{
         withContext(Dispatchers.IO){SecureV2Api.leaderScheduleAction(token,groupId,r.id,"reassign",target)}
       }}},enabled=toMember!=null&&!busy,modifier=Modifier.fillMaxWidth()){
         Text("إعادة توزيع الجزء")
       }
       OutlinedTextField(value=days,onValueChange={days=it.take(2)},
         label={Text("التمديد بالأيام (1–60)")},singleLine=true)
       OutlinedButton(onClick={
         val n=days.toIntOrNull()
         if(n!=null&&n in 1..60){action=null;save{withContext(Dispatchers.IO){
           SecureV2Api.leaderScheduleAction(token,groupId,r.id,"extend",
             extendedTo=LocalDate.now().plusDays(n.toLong()).toString())
         }}}else error="مدة التمديد غير صحيحة"
       },enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("تمديد الموعد")}
       OutlinedButton(onClick={action=null;save{withContext(Dispatchers.IO){
         SecureV2Api.leaderScheduleAction(token,groupId,r.id,"excuse")
       }}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("إعفاء من الجزء")}
     }},
     confirmButton={TextButton(onClick={action=null}){Text("إغلاق")}})
 }
}
@Composable
private fun RowScope.P48Cell(value:String,weight:Float,bold:Boolean=false,color:Color=p48Ink){
 Text(value,modifier=Modifier.weight(weight),textAlign=TextAlign.Center,
   fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,
   fontSize=if(bold)11.sp else 10.sp,color=color,maxLines=2,lineHeight=15.sp)
}
