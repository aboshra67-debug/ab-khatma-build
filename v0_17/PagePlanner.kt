package com.ab.khatma.secure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ab.khatma.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** All reads and writes are authenticated and isolated by the server's group membership. */
private object PageApi {
    fun call(token: String, path: String, method: String = "GET", data: JSONObject? = null): String {
        val url = BuildConfig.V2_API_BASE_URL.trimEnd('/') + "/v3" + path
        check(BuildConfig.V2_PREVIEW_ENABLED && url.startsWith("https://")) {
            "السيرفر الآمن غير مُعد"
        }
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000; c.readTimeout = 12_000
            c.instanceFollowRedirects = false
            c.requestMethod = method
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (data != null) {
                c.doOutput = true
                c.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            if (status !in 200..299) {
                val text = when (status) {
                    401 -> "جلسة الدخول انتهت"
                    403 -> "ليس لديك صلاحية"
                    409 -> "تكليف الصفحات يتداخل أو الخطة غير مناسبة"
                    422 -> "تأكد من التاريخ والمدى المحدد"
                    else -> "تعذر تنفيذ الطلب ($status)"
                }
                error(text)
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
    data class Run(val id: Long, val percentage: Double, val done: Int, val total: Int,
                   val title: String, val mode: String)
    data class Task(val id: Long, val start: Int, val end: Int, val read: Int,
                    val date: String, val finished: Boolean)

    fun runs(t: String, g: Long): List<Run> {
        val a = JSONArray(call(t, "/groups/$g/runs"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Run(o.getLong("run_id"), o.getDouble("progress_percent"),
                o.getInt("pages_completed"), o.getInt("pages_total"),
                o.getString("start_date") + " — " + o.getString("end_date"),
                o.getString("assignment_mode"))
        }
    }
    fun tasks(t: String, r: Long): List<Task> {
        val a = JSONArray(call(t, "/runs/$r/tasks"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Task(o.getLong("id"), o.getInt("start_page"), o.getInt("end_page"),
                o.getInt("completed_through"), o.getString("reading_date"),
                o.getBoolean("completed"))
        }
    }
    fun create(t: String, g: Long, start: String, days: Int,
               goal: String, schedule: String, distribution: String) {
        call(t, "/groups/$g/runs", "POST", JSONObject()
            .put("start_date", start).put("duration_days", days).put("goal", goal)
            .put("scheduling", schedule).put("assignment_mode", distribution)
            .put("repeat_mode", "manual"))
    }
    fun save(t: String, task: Long, last: Int) {
        call(t, "/tasks/$task/progress", "POST", JSONObject().put("completed_through", last))
    }
}

/** Renders inside the group's authenticated screen; avoids a second login or legacy V1 endpoint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagePlanner(token: String, groupId: Long, leader: Boolean, onRead: () -> Unit) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var plans by remember(groupId) { mutableStateOf<List<PageApi.Run>>(emptyList()) }
    var selected by remember(groupId) { mutableStateOf<PageApi.Run?>(null) }
    var assignments by remember(groupId) { mutableStateOf<List<PageApi.Task>>(emptyList()) }
    var lastPages by remember(groupId) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var days by remember { mutableStateOf("30") }
    var goal by remember { mutableStateOf("once") }
    var schedule by remember { mutableStateOf("full") }
    var distribution by remember { mutableStateOf("auto") }

    suspend fun refresh() {
        val newPlans = withContext(Dispatchers.IO) { PageApi.runs(token, groupId) }
        plans = newPlans
        val id = selected?.id
        if (id != null) {
            selected = newPlans.firstOrNull { it.id == id }
            assignments = if (selected != null) withContext(Dispatchers.IO) {
                PageApi.tasks(token, id)
            } else emptyList()
        }
    }
    fun work(action: suspend () -> Unit) {
        if (running) return
        scope.launch {
            running = true; notice = ""
            try { action() }
            catch (ex: Exception) { notice = ex.message?.take(140) ?: "تعذر الاتصال بالخدمة" }
            finally { running = false }
        }
    }
    LaunchedEffect(token, groupId) {
        work { refresh() }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ختمات الصفحات", style = MaterialTheme.typography.titleLarge)
        Text("احفظ تقدمك من أول صفحة لآخر صفحة كُلّفت بها، بدون احتساب مكرر.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
        OutlinedButton(onClick = { work { refresh() } }, enabled = !running) { Text("تحديث") }

        if (leader) {
            ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ختمة جديدة", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(startDate, { startDate = it.take(10) },
                        label = { Text("تاريخ البداية (2026-10-09)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(days, { days = it.filter(Char::isDigit).take(3) },
                        label = { Text("عدد الأيام") }, modifier = Modifier.fillMaxWidth())
                    Row {
                        FilterChip(goal == "once", { goal = "once" }, label = { Text("ختمة خلال المدة") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(goal == "daily_full", { goal = "daily_full" }, label = { Text("ختمة كل يوم") })
                    }
                    Row {
                        FilterChip(schedule == "full", { schedule = "full" }, label = { Text("جدول كامل") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(schedule == "daily", { schedule = "daily" }, label = { Text("جدولة يومية") })
                    }
                    Row {
                        FilterChip(distribution == "auto", { distribution = "auto" }, label = { Text("تلقائي") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(distribution == "manual", { distribution = "manual" }, label = { Text("يدوي") })
                    }
                    Button(
                        enabled = !running,
                        onClick = { work {
                            val duration = days.toIntOrNull() ?: 0
                            check(duration in 1..365) { "اكتب مدة صحيحة" }
                            val date = LocalDate.parse(startDate).toString()
                            withContext(Dispatchers.IO) {
                                PageApi.create(token, groupId, date, duration, goal, schedule, distribution)
                            }
                            refresh()
                            notice = "تم إنشاء الختمة"
                        } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إنشاء الختمة وتوزيع الصفحات") }
                }
            }
        }

        if (plans.isEmpty()) Text("لم تُنشأ خطة صفحات بعد.")
        plans.forEach { plan ->
            ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("من " + plan.title, style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { (plan.percentage / 100).toFloat().coerceIn(0f,1f) },
                        modifier = Modifier.fillMaxWidth())
                    Text("المُنجز: " + plan.done + " / " + plan.total + " صفحة")
                    OutlinedButton(onClick = { work {
                        selected = plan
                        assignments = withContext(Dispatchers.IO) { PageApi.tasks(token, plan.id) }
                    } }, enabled = !running) { Text("تكليفات الصفحات") }
                }
            }
        }
        if (selected != null) {
            Text("تكليفاتي", style = MaterialTheme.typography.titleLarge)
            if (assignments.isEmpty()) Text("لا توجد تكليفات حالية لهذا الحساب.")
            assignments.forEach { task ->
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("من صفحة " + task.start + " إلى " + task.end + " — " + task.date)
                        Text("تمت القراءة حتى صفحة " + task.read)
                        if (task.finished) Text("✓ اكتمل التكليف")
                        else {
                            OutlinedTextField(
                                lastPages[task.id] ?: task.read.toString(),
                                { lastPages = lastPages + (task.id to it.filter(Char::isDigit).take(3)) },
                                label = { Text("آخر صفحة قرأتها") },
                                modifier = Modifier.fillMaxWidth())
                            Button(onClick = { work {
                                val last = (lastPages[task.id] ?: task.read.toString()).toIntOrNull() ?: 0
                                check(last in maxOf(task.start, task.read)..task.end) {
                                    "الصفحة خارج حدود تكليفك"
                                }
                                withContext(Dispatchers.IO) { PageApi.save(token, task.id, last) }
                                refresh()
                                notice = "تم حفظ التقدم"
                            } }, enabled = !running) { Text("حفظ التقدم") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onRead) { Text("فتح المصحف") }
            if (leader && selected?.mode == "manual") {
                Text("التوزيع اليدوي متاح من واجهة القائد في التحديث التالي.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
