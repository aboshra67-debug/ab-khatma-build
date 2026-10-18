package com.ab.khatma.secure

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** An offline disclosure page: opening it starts no network call or permission request. */
@Composable
internal fun P84PrivacyPage(userId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val sections = listOf(
        "عن هذه السياسة" to "ختمة — AB Khatma من AB Software. آخر تحديث: ١٨ أكتوبر ٢٠٢٦. توضح هذه الصفحة البيانات المستخدمة لتشغيل الحساب والختمات والقراءة والتنبيهات، والخيارات المتاحة لك.",
        "الحساب والختمات" to "يحتفظ سيرفر ختمة برقم الحساب والاسم الذي تختاره وعضويتك في الختمات وجدول الورد وحالة الإنجاز. عند اختيار الدخول بحساب Google، تُستخدم Google وFirebase للتحقق من هويتك، ويرتبط حساب ختمة بمعرّف حسابك الموثّق وبريده إن توفر. التطبيق لا يطلب كلمة مرور بريدك ولا يخزنها. تظهر أسماء الأعضاء وتقدمهم للمشاركين المقبولين حسب صلاحيات الختمة.",
        "المحادثات والبلاغات" to "تُحفظ الرسائل على السيرفر حتى تستطيع فتحها من أجهزتك. لا يقرأ المحادثة الخاصة إلا طرفاها بحسب الصلاحيات؛ رسائل المجموعة متاحة لأعضائها المقبولين، ومنشورات الملتقى للمستخدمين المسموح لهم. يعرض بلاغ المحادثة الخاصة للمشرف سبب البلاغ المكتوب فقط. الاتصال مشفّر عبر HTTPS، لكن المحادثات ليست مشفّرة بين الطرفين تشفيرًا تامًا. لا تكتب معلومات حساسة في الرسائل أو البلاغات.",
        "الصلاة والموقع" to "يُطلب إذن الموقع عند تشغيل تحديد الموقع لمواقيت الصلاة. يستخدم التطبيق الإحداثيات على هذا الهاتف لحساب المواقيت، ولا يرفعها كود مواقيت الصلاة إلى سيرفر ختمة. يمكنك اختيار مدينة يدويًا دون منح إذن الموقع. خدمات الموقع التابعة لنظام الهاتف تخضع لإعداداتها وسياساتها.",
        "التسميع والميكروفون" to "الميكروفون اختياري ويُستخدم عند بدء التسميع. قد تعالج خدمة التعرف على الكلام الموجودة على هاتفك الصوت عبر الإنترنت بحسب مزود الخدمة وإعدادات الجهاز. التسجيل الذي تختار حفظه يبقى في مساحة التطبيق على الهاتف، ولا يرفعه كود التسميع إلى سيرفر ختمة. يمكن استخدام التسميع بالكتابة دون الميكروفون.",
        "التنبيهات والخدمات الخارجية" to "تذكيرات الصلاة والورد والأذكار تعتمد على اختياراتك وأذونات التنبيهات والمنبهات في الهاتف. تستخدم رسائل المحادثات Firebase Cloud Messaging ومعرّف تسجيل الجهاز لتوجيه التنبيه؛ لا يتضمن تنبيه الرسالة الخاصة المرسل عبر FCM نص المحادثة. خدمات Google وFirebase والاستضافة ومصادر بث التلاوات قد تعالج بيانات تقنية مثل عنوان الإنترنت ومعرّفات الخدمة اللازمة للتشغيل وفق سياساتها. لا يحتوي هذا البناء على حزمة إعلانات أو Firebase Analytics.",
        "البيانات على الهاتف" to "تُحفظ إعداداتك ومكان القراءة والإحصاءات المحلية والملفات المحمّلة على الهاتف. تُحفظ بيانات الجلسة الحساسة مشفّرة باستخدام Android Keystore. لا نحتفظ بكلمة مرور الحساب داخل التطبيق لتجديد الدخول. تستمر الجلسة حتى تسجيل الخروج أو إلغائها لأسباب أمنية؛ لا يعني ذلك إزالة حماية الحساب.",
        "حذف الحساب والتحكم في بياناتك" to "من حسابي اختر «حذف حسابي نهائيًا» واتبع التأكيد. يحذف المسار الاسم وبيانات دخول ختمة وربط Google والرسائل والمنشورات الشخصية من بيانات الخدمة، ويحتفظ بسجلات قراءة مجهولة داخل الختمات لحماية سجل بقية الأعضاء. حذف حساب ختمة لا يحذف حساب Google نفسه. إذا كنت قائدًا لختمة فيها أعضاء، أغلق الختمة أو أنهِ عضويتهم أولًا وفق واجهة التطبيق. تظل الملفات والتسجيلات والإعدادات المحلية على الهاتف حتى تديرها أو تمسح بيانات التطبيق. النسخ الاحتياطية وسجلات الأمان ليست بديلًا للبيانات التشغيلية؛ لا تُستخدم لإعادة فتح حساب محذوف. عند فقد الوصول للتطبيق، يمكنك إرسال طلب حذف إلى بريد التواصل أدناه، وسنتحقق من ملكية الحساب قبل التنفيذ. لا ترسل كلمات مرور أو رموز دخول.",
        "التواصل" to "للاستفسار عن الخصوصية أو تقديم طلب حذف بيانات حساب ختمة: aboshra67@gmail.com. عند طلب الحذف اذكر رقم حساب ختمة، واستخدم البريد المرتبط بحسابك إن أمكن. لا ترسل كلمة مرور بريدك أو كلمة مرور ختمة.",
    )
    fun requestDeletion() {
        val subject = Uri.encode("طلب حذف حساب ختمة — AB Khatma")
        val body = Uri.encode("أطلب حذف حسابي وبياناته الشخصية من تطبيق ختمة.\nرقم حساب ختمة: $userId\nأرسل هذا الطلب من البريد المرتبط بحسابي إن أمكن. يرجى توضيح طريقة التحقق من الملكية.\nلا أرفق أي كلمة مرور أو رمز دخول.")
        val intent = Intent(Intent.ACTION_SENDTO,
            Uri.parse("mailto:aboshra67@gmail.com?subject=$subject&body=$body"))
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "اكتب طلبك إلى aboshra67@gmail.com من تطبيق البريد الذي تستخدمه.", Toast.LENGTH_LONG).show()
        }
    }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFFFFAEF)) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("الخصوصية وبياناتك", Modifier.weight(1f).padding(vertical = 10.dp),
                        color = Color(0xFF064D3B), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClose) { Text("رجوع") }
                }
                HorizontalDivider(color = Color(0xFFE8E0CE))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(sections) { (title, text) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, color = Color(0xFF064D3B), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(text, color = Color(0xFF263F35), fontSize = 15.sp, lineHeight = 25.sp)
                        }
                    }
                    item {
                        OutlinedButton(onClick = { requestDeletion() }, modifier = Modifier.fillMaxWidth()) {
                            Text("فتح البريد لطلب حذف الحساب")
                        }
                        Text("سيُفتح تطبيق البريد؛ راجع الطلب ثم أرسله بنفسك. لن يُحذف الحساب بمجرد الضغط.",
                            color = Color(0xFF746D62), fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}
