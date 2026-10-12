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
 "excused"->"إعفاء القائد";"extended"->"تمديد الموعد";else->"مطلوب اليوم"
}
@Composable
internal fun P48ReaderSchedule(token:String,groupId:Long,isLeader:Boolean,groupName:String,
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
 LaunchedEffect(token,groupId,isLeader,selected,refresh) {
   loading=true; error=""
   try {
     if(isLeader&&members.isEmpty()){
       members=withContext(Dispatchers.IO){SecureV2Api.groupStatus(token,groupId).members}
       if(selected==null)selected=members.firstOrNull()?.id
     }
     if(!isLeader||selected!=null) schedule=withContext(Dispatchers.IO){
       SecureV2Api.readingSchedule(token,groupId,if(isLeader)selected else null)
     }
   }catch(e:Exception){error=e.message?:"تعذر تحميل الجدول"}
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
     Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF9EC))){
       Column(Modifier.fillMaxWidth().padding(12.dp)){
         Text("${data.overdueCount} جزء متأخر",fontWeight=FontWeight.Bold,color=Color(0xFF98502B))
         Text("المطلوب ${data.pendingCount} • لا تُحذف المتأخرات تلقائيًا",
           color=p48Ink,fontSize=12.sp)
       }
     }
     Surface(color=Color(0xFFE6F0EA),shape=RoundedCornerShape(10.dp)){
       Row(Modifier.fillMaxWidth().padding(8.dp)){
         P48Cell("اليوم",1.1f,true);P48Cell("التاريخ",1.4f,true)
         P48Cell("الجزء",.7f,true);P48Cell("الحالة",1.4f,true)
       }
     }
     if(data.rows.isEmpty())Text("لا يوجد جدول قراءة مسجل حتى الآن.",color=p48Ink)
     data.rows.forEachIndexed { i,r->
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
