package com.ab.khatma.secure

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Device-only, account-scoped onboarding. No account/server/notification settings are changed. */
internal object P93FirstRunStore {
    private const val PREFS = "p93_first_run_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun begin(context: Context, userId: Long) {
        if (userId <= 0) return
        prefs(context).edit().putBoolean("pending_$userId", true).putInt("step_$userId", 0).commit()
    }
    fun pending(context: Context, userId: Long): Boolean =
        userId > 0 && prefs(context).getBoolean("pending_$userId", false)
    fun step(context: Context, userId: Long): Int = prefs(context).getInt("step_$userId", 0).coerceIn(0, 4)
    fun saveStep(context: Context, userId: Long, step: Int) {
        if (pending(context, userId)) prefs(context).edit().putInt("step_$userId", step.coerceIn(0, 4)).apply()
    }
    fun finish(context: Context, userId: Long) {
        prefs(context).edit().putBoolean("pending_$userId", false).remove("step_$userId").apply()
    }
}

/** Visible at top of existing account settings only when a new registration needs setup. */
@Composable
internal fun P93FirstRunCard(
    userId: Long,
    step: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onOpenReciter: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrayer: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val titles = listOf("أهلًا بك في ختمة", "اختار القارئ", "اضبط التنبيهات", "مواقيت الصلاة والأذان", "اتعرّف على التطبيق")
    val descriptions = listOf(
        "حسابك جاهز. احتفظ برقم الحساب علشان تقدر ترجع له من أي موبايل. هنجهز التطبيق مع بعض في خطوات بسيطة.",
        "اختار الصوت اللي تحب تسمع بيه القرآن. تقدر تغيّره بعد كده من الإعدادات.",
        "اختار مواعيد تذكير الورد والأذكار والصلاة. كل تنبيه تقدر تشغّله أو توقفه بنفسك.",
        "راجع المدينة ومواقيت الصلاة وصوت الأذان. مش هنستخدم موقعك إلا بموافقتك.",
        "خلصنا الأساسيات! تقدر تفتح الجولة التفاعلية وتتعرف على كل قسم، أو تبدأ استخدام ختمة دلوقتي."
    )
    val green = Color(0xFF064D3B)
    val gold = Color(0xFFD6B25B)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFAEF), border = BorderStroke(1.dp, gold)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("خطوات البداية • ${step + 1} من 5", color = green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { (step + 1f) / 5f }, modifier = Modifier.fillMaxWidth(), color = green)
            Text(titles[step], fontSize = 21.sp, fontWeight = FontWeight.Bold, color = green)
            Text(descriptions[step], fontSize = 14.sp, lineHeight = 23.sp, color = Color(0xFF3A4A42))
            if (step == 0) Text("رقم حسابك: $userId", fontWeight = FontWeight.Bold, color = green)
            val (actionLabel, action) = when (step) {
                1 -> "اختيار القارئ" to onOpenReciter
                2 -> "فتح إعدادات التنبيهات" to onOpenNotifications
                3 -> "فتح إعدادات الصلاة والأذان" to onOpenPrayer
                4 -> "ابدأ الجولة التفاعلية" to onOpenGuide
                else -> null to null
            }
            if (actionLabel != null && action != null) {
                OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0) OutlinedButton(onClick = onPrevious) { Text("السابق") }
                Button(onClick = onNext, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = green)) {
                    Text(if (step == 4) "إنهاء الإعداد" else "التالي")
                }
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("تخطي الإعداد الآن", color = Color(0xFF6D665A))
            }
        }
    }
}
