package com.ab.khatma.secure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** P51: future v4 leader plans when server has them; otherwise read existing
 *  P35/P49 saved page-plans and today's persisted duty. No synthetic assignments.
 */
@Composable
internal fun P51ScheduleBridge(
    token: String, groupId: Long, isLeader: Boolean, groupName: String,
    todayAssignment: SecureV2Api.TodayItem?,
    onRead: (Int)->Unit, onChanged: ()->Unit, onOpenPagePlans: ()->Unit
) {
    var route by remember(token,groupId){ mutableStateOf<String?>(null) }
    LaunchedEffect(token,groupId,isLeader) {
        route = withContext(Dispatchers.IO) {
            try {
                val response=SecureV2Api.p50Request(token,"/groups/"+groupId+"/leader-plan")
                if(response.optJSONObject("plan") != null || isLeader) "v4"
                else "legacy"
            } catch(e:Exception) {
                if(e is SecureV2Api.SessionExpiredException) "expired"
                else "legacy"
            }
        }
    }
    when(route) {
        "v4" -> P50LeaderSchedule(token=token,groupId=groupId,isLeader=isLeader,
                    groupName=groupName,onRead=onRead,onChanged=onChanged)
        "legacy" -> P48ReaderSchedule(token=token,groupId=groupId,isLeader=isLeader,
                    groupName=groupName,todayAssignment=todayAssignment,
                    onRead=onRead,onChanged=onChanged,onOpenPagePlans=onOpenPagePlans)
        "expired" -> Text("انتهت جلسة الدخول. سجّل الدخول بنفس حسابك؛ توزيعك محفوظ.")
        else -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(10.dp))
    }
}
