package com.ab.khatma.secure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// P50: no invented reader assignments; only leader-saved server state.
private val p50Green = Color(0xFF17664E)
private val p50Red = Color(0xFFA74435)
private val p50Ink = Color(0xFF233B34)
private val p50Cairo = ZoneId.of("Africa/Cairo")
private val p50Fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val p50Days = listOf("الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت","الأحد")
private fun p50Date(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()
private fun p50Status(s: String) = when(s) {
    "completed" -> "تمت القراءة ✓"; "overdue" -> "متأخر"; "today" -> "مطلوب اليوم"
    "upcoming" -> "قادم"; "extended" -> "تمديد الموعد"; "excused" -> "إعفاء"
    else -> "غير موزع"
}

private object P50Plans {
    data class Plan(val id:Long, val start:String, val duration:Int, val state:String)
    data class Duty(val id:Long, val part:Int, val member:Long, val date:String,
                    val due:String, val status:String, val canComplete:Boolean,
                    val events:List<String>)
    data class Data(val plan:Plan?, val duties:List<Duty>, val notices:List<String>,
                    val overdue:Int, val pending:Int, val count:Int)
    private fun call(token:String, path:String, method:String="GET", json:JSONObject?=null) =
        SecureV2Api.p50Request(token,path,method,json)
    fun load(token:String,gid:Long):Data {
        val result=call(token,"/groups/"+gid+"/leader-plan")
        val p=result.optJSONObject("plan")
        val plan=if(p==null) null else Plan(p.getLong("id"),p.getString("start_date"),
            p.getInt("duration_days"),p.getString("status"))
        val arr=result.getJSONArray("rows")
        val duties=(0 until arr.length()).map { i->
            val o=arr.getJSONObject(i);val history=o.optJSONArray("history")
            val events=if(history==null) emptyList() else (0 until history.length()).map { j->
                val e=history.getJSONObject(j)
                val name=when(e.optString("action")){
                    "assign"->"توزيع";"approve"->"اعتماد القائد";"revise"->"تعديل";"reassign"->"إعادة توزيع"
                    "reschedule"->"تغيير التاريخ";"extend"->"تمديد"
                    "excuse"->"إعفاء";"complete"->"قراءة مكتملة";else->"إجراء"}
                name+" • "+e.optString("by")+" • "+e.optString("date").take(10)
            }
            Duty(o.getLong("id"),o.getInt("juz"),o.getLong("membership_id"),
                o.getString("date"),o.getString("due_date"),o.getString("status"),
                o.optBoolean("can_complete"),events)
        }
        val changes=result.optJSONArray("notices")
        val notices=if(changes==null)emptyList() else (0 until changes.length()).map {i->
            val o=changes.getJSONObject(i)
            "الجزء "+o.getInt("juz")+" • "+o.optString("message")
        }
        return Data(plan,duties,notices,result.optInt("overdue_count"),
            result.optInt("pending_count"),result.optInt("assigned_parts"))
    }
    fun readers(token:String,gid:Long):List<SecureV2Api.MemberInfo> {
        val array=call(token,"/groups/"+gid+"/leader-plan/readers").getJSONArray("members")
        return (0 until array.length()).map{i->
            val x=array.getJSONObject(i)
            SecureV2Api.MemberInfo(x.getLong("membership_id"),x.getString("name"),null,false)
        }
    }
    fun create(token:String,gid:Long,start:String,days:Int,automatic:Boolean) {
        call(token,"/groups/"+gid+"/leader-plan","POST",JSONObject()
            .put("start_date",start).put("duration_days",days)
            .put("distribution",if(automatic)"auto" else "manual"))
    }
    fun assign(token:String,plan:Long,part:Int,member:Long,date:String) {
        call(token,"/plans/"+plan+"/duties","POST",JSONObject()
            .put("juz_number",part).put("membership_id",member).put("reading_date",date))
    }
    fun approve(token:String,plan:Long){call(token,"/plans/"+plan+"/approve","POST",JSONObject())}
    fun complete(token:String,id:Long){call(token,"/duties/"+id+"/complete","POST",JSONObject())}
    fun act(token:String,id:Long,kind:String,due:String?=null){
        val j=JSONObject().put("action",kind)
        if(due!=null)j.put("extended_to",due)
        call(token,"/duties/"+id+"/action","POST",j)
    }
}

@Composable
internal fun P50LeaderSchedule(token:String,groupId:Long,isLeader:Boolean,groupName:String,
                               onRead:(Int)->Unit,onChanged:()->Unit){
    val scope=rememberCoroutineScope()
    val today=LocalDate.now(p50Cairo)
    var refresh by remember(token,groupId){mutableIntStateOf(0)}
    var loading by remember(token,groupId){mutableStateOf(true)}
    var working by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var data by remember(token,groupId){mutableStateOf<P50Plans.Data?>(null)}
    var people by remember(token,groupId){mutableStateOf<List<SecureV2Api.MemberInfo>>(emptyList())}
    var start by remember {mutableStateOf(today.toString())}
    var length by remember {mutableStateOf("30")}
    var auto by remember {mutableStateOf(true)}
    var editPart by remember {mutableStateOf<Int?>(null)}
    var editorMember by remember {mutableStateOf<Long?>(null)}
    var editorDate by remember {mutableStateOf(today.toString())}
    var showMembers by remember {mutableStateOf(false)}
    var readerConfirm by remember {mutableStateOf<P50Plans.Duty?>(null)}
    LaunchedEffect(token,groupId,refresh,isLeader) {
        loading=true;error=""
        try {
            data=withContext(Dispatchers.IO){P50Plans.load(token,groupId)}
            if(isLeader)people=withContext(Dispatchers.IO){P50Plans.readers(token,groupId)}
        }catch(e:Exception){
            data=null
            error=if(e.message=="الخدمة غير متاحة على خادم التجربة")
                "جدول القائد يحتاج نشر السيرفر الجديد؛ لن نعرض توزيعات غير محفوظة."
                else e.message?.take(140)?:"تعذر تحميل جدول القائد"
        }finally{loading=false}
    }
    fun save(block:suspend ()->Unit){
        if(working)return
        scope.launch {
            working=true;error=""
            try{
                withContext(Dispatchers.IO){block()}
                refresh++;onChanged()
            }catch(e:Exception){error=e.message?.take(140)?:"تعذر الحفظ"}
            finally{working=false}
        }
    }
    val snapshot=data;val plan=snapshot?.plan
    Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text(if(isLeader)"جدول القائد" else "جدولي في الختمة",
            fontSize=23.sp,fontWeight=FontWeight.Bold,color=p50Ink)
        Text(groupName,color=p50Green)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=p50Red,fontSize=12.sp)
        if(!loading&&snapshot!=null&&plan==null) {
            if(!isLeader)Text("لم يعتمد قائد المجموعة جدولًا بعد.",color=p50Ink)
            else{
                Text("إنشاء خطة — لن تظهر للقارئ قبل اعتمادها",color=p50Ink)
                OutlinedTextField(start,{start=it.take(10)},label={Text("تاريخ البداية YYYY-MM-DD")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(length,{length=it.filter(Char::isDigit).take(3)},
                    label={Text("عدد الأيام: 7، 15، 30، 60 أو أي مدة حتى 365")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    FilterChip(auto,{auto=true},label={Text("تلقائي")})
                    FilterChip(!auto,{auto=false},label={Text("يدوي")})
                }
                Button(onClick={
                    val day=p50Date(start);val count=length.toIntOrNull()
                    if(day==null||count==null||count !in 1..365)error="تاريخ أو مدة غير صحيحة"
                    else save{P50Plans.create(token,groupId,day.toString(),count,auto)}
                },enabled=!working,modifier=Modifier.fillMaxWidth()){
                    Text("إنشاء مسودة جدول الختمة")
                }
            }
        }
        if(plan!=null&&snapshot!=null){
            Surface(color=Color(0xFFFFF7EB),shape=RoundedCornerShape(12.dp)){
                Column(Modifier.fillMaxWidth().padding(12.dp)){
                    Text("مدة الختمة: "+plan.duration+" يومًا • البداية: "+plan.start,
                        color=p50Ink,fontWeight=FontWeight.Bold)
                    Text("الأجزاء: "+snapshot.count+"/30 • متأخر: "+snapshot.overdue+
                        " • مطلوب: "+snapshot.pending,color=p50Green,fontSize=12.sp)
                    Text(if(plan.state=="draft")"مسودة غير معتمدة" else "جدول معتمد",
                        color=if(plan.state=="draft")p50Red else p50Green)
                }
            }
            if(snapshot.notices.isNotEmpty()) {
                Text("قرارات القائد",fontWeight=FontWeight.Bold,color=p50Ink)
                snapshot.notices.forEach{Text(it,color=p50Red,fontSize=12.sp)}
            }
            if(isLeader){
                if(plan.state=="draft"){
                    Button(onClick={save{P50Plans.approve(token,plan.id)}},
                        enabled=!working&&snapshot.count==30,modifier=Modifier.fillMaxWidth()){
                        Text("اعتماد توزيع الثلاثين جزءًا")
                    }
                    if(snapshot.count<30)Text("باقي "+(30-snapshot.count)+" أجزاء للتوزيع",
                        color=p50Red,fontSize=12.sp)
                }
                Text("توزيع الأجزاء (كل جزء مستقل)",fontWeight=FontWeight.Bold,color=p50Ink)
                val mapped=snapshot.duties.associateBy{it.part}
                (1..30).forEach{part->
                    val d=mapped[part]
                    OutlinedButton(onClick={
                        editPart=part;editorMember=d?.member?:people.firstOrNull()?.id
                        editorDate=d?.date?:plan.start
                    },enabled=!working&&d?.status!="completed",
                        modifier=Modifier.fillMaxWidth()){
                        Text("الجزء "+part+" — "+(if(d==null)"لم يوزع" else
                            (people.firstOrNull{it.id==d.member}?.name?:"قارئ")+" • "+
                              d.date+" • "+p50Status(d.status)),fontSize=12.sp)
                    }
                }
            }
            Text("الجدول اليومي",fontWeight=FontWeight.Bold,color=p50Ink)
            val first=p50Date(plan.start)
            if(first!=null){
                val dates=remember(plan.id,plan.start,plan.duration){
                    (0 until plan.duration).map{first.plusDays(it.toLong())}
                }
                val byDate=snapshot.duties.groupBy{it.date}
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=540.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)){
                    items(dates,key={it.toString()}){day->
                        val rows=byDate[day.toString()].orEmpty()
                        Card(border=BorderStroke(if(day==today)2.dp else 1.dp,
                            if(day==today)p50Green else Color(0xFFE1E5DF)),
                            shape=RoundedCornerShape(10.dp),
                            colors=CardDefaults.cardColors(containerColor=if(day==today)
                                Color(0xFFE7F2E9) else Color.White)){
                            Column(Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(p50Days[day.dayOfWeek.value-1]+" • "+day.format(p50Fmt),
                                    color=p50Green,fontWeight=FontWeight.Bold)
                                if(rows.isEmpty())Text("لا يوجد تكليف لهذا اليوم",
                                    fontSize=12.sp,color=Color.Gray)
                                rows.forEach{d->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                        Text("الجزء "+d.part,color=p50Ink)
                                        Text(p50Status(d.status),color=if(d.status=="overdue")
                                            p50Red else p50Green,fontSize=12.sp)
                                    }
                                    if(d.due!=d.date)Text("موعد ممدد إلى: "+d.due,fontSize=11.sp)
                                    if(d.events.isNotEmpty())Text(d.events.takeLast(2).joinToString(" • "),
                                        fontSize=10.sp,color=Color.Gray)
                                    if(!isLeader&&d.canComplete){
                                        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                                            OutlinedButton(onClick={onRead(d.part)},
                                                modifier=Modifier.weight(1f)){
                                                Text("قراءة الجزء",fontSize=11.sp)
                                            }
                                            Button(onClick={readerConfirm=d},enabled=!working,
                                                modifier=Modifier.weight(1f)){
                                                Text("تمت القراءة",fontSize=11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick={refresh++},enabled=!loading&&!working,
            modifier=Modifier.fillMaxWidth()){Text("تحديث الجدول")}
    }
    readerConfirm?.let{d->
        AlertDialog(onDismissRequest={readerConfirm=null},
            title={Text("تأكيد القراءة")},
            text={Text("هل أتممت قراءة الجزء "+d.part+" بالكامل؟")},
            confirmButton={Button(onClick={
                readerConfirm=null;save{P50Plans.complete(token,d.id)}
            }){Text("نعم")}},
            dismissButton={TextButton(onClick={readerConfirm=null}){Text("إلغاء")}})
    }
    editPart?.let{part->
        val d=snapshot?.duties?.firstOrNull{it.part==part}
        AlertDialog(onDismissRequest={editPart=null},title={Text("توزيع الجزء "+part)},
            text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
                Box{
                    OutlinedButton(onClick={showMembers=true},modifier=Modifier.fillMaxWidth()){
                        Text(people.firstOrNull{it.id==editorMember}?.name?:"اختيار القارئ")
                    }
                    DropdownMenu(expanded=showMembers,onDismissRequest={showMembers=false}){
                        people.forEach{m->DropdownMenuItem(text={Text(m.name)},onClick={
                            editorMember=m.id;showMembers=false
                        })}
                    }
                }
                OutlinedTextField(editorDate,{editorDate=it.take(10)},
                    label={Text("التاريخ YYYY-MM-DD")},singleLine=true)
                if(d!=null&&snapshot?.plan?.state=="approved"){
                    OutlinedButton(onClick={
                        val date=p50Date(editorDate)
                        if(date==null)error="التاريخ غير صحيح"
                        else{editPart=null;save{P50Plans.act(token,d.id,"extend",date.toString())}}
                    },modifier=Modifier.fillMaxWidth()){Text("تمديد الموعد للتاريخ المحدد")}
                    OutlinedButton(onClick={
                        editPart=null;save{P50Plans.act(token,d.id,"excuse")}
                    },modifier=Modifier.fillMaxWidth()){Text("إعفاء القارئ")}
                }
                Text("كل تعديل يُحفظ مع اسم القائد والتاريخ دون حذف السجل.",
                    fontSize=11.sp,color=p50Ink)
            }},
            confirmButton={Button(onClick={
                val p=snapshot?.plan;val m=editorMember;val day=p50Date(editorDate)
                if(p==null||m==null||day==null)error="اختر القارئ والتاريخ"
                else{editPart=null;save{P50Plans.assign(token,p.id,part,m,day.toString())}}
            },enabled=!working){Text("حفظ التوزيع")}},
            dismissButton={TextButton(onClick={editPart=null}){Text("إلغاء")}})
    }
}
