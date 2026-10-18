package com.ab.khatma.guide

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private data class GuideStep(val key: String, val title: String, val text: String)
/** Local-only guide state. No credentials, analytics, server writes, or content changes. */
object P85Guide {
    private val bounds = mutableStateMapOf<String, Rect>()
    private var topic by mutableStateOf<String?>(null)
    private var index by mutableIntStateOf(0)
    private var menu by mutableStateOf(false)
    private val green = Color(0xFF064D3B)
    private val chapters = linkedMapOf(
        "home" to ("البداية والأقسام" to listOf(
            GuideStep("home.mushaf", "المصحف", "من هنا تفتح المصحف. تقدر تختار سورة أو جزء، وتكمّل من آخر موضع وقفت عنده."),
            GuideStep("home.khatmas", "الختمات ووردك", "هنا ختماتك وأورادك. بعد ما تخلص القراءة اضغط تأكيد إتمام الورد؛ مجرد فتح المصحف ما بيسجّلش الورد كمكتمل."),
            GuideStep("home.meetup", "ملتقى الختمات", "تقدر تنضم لختمة متاحة أو تدخل بكود الدعوة. راجع خطة الختمة والورد المطلوب قبل المشاركة."),
            GuideStep("home.prayer", "الصلاة والأذان", "اضبط المدينة وطريقة الحساب، واختار صوت الأذان ومستواه. تقدر توقف صوت صلاة معيّنة وتسيب تنبيه دخول الوقت شغّال."),
            GuideStep("home.adhkar", "الأدعية والأذكار", "اختار الباب واضغط على الذكر مع كل مرة. تقدّمك بيتحفظ، وتقدر تكمّل من مكانك."),
            GuideStep("home.profile", "حسابك والإعدادات", "من حسابي تقدر تضبط الاسم والقارئ والتنبيهات. زر شرح التطبيق هنا بيرجّع الدليل وقت ما تحب."),
            GuideStep("home.ibtehal", "الابتهالات", "افتح بث الابتهالات والتواشيح أو تسجيلات الأرشيف من مصادرها. الاستماع هنا اختياري ويحتاج إنترنت."))),
        "reader" to ("المصحف والاستماع" to listOf(
            GuideStep("reader.index", "الفهرس", "اختار السورة أو الجزء أو الصفحة من الفهرس. تنقّل بين الصفحات بالسحب أو أزرار السابق والتالي."),
            GuideStep("reader.search", "البحث", "ابحث بكلمة من الآية، وبعدها اختار النتيجة لفتح موضعها في المصحف."),
            GuideStep("reader.marks", "علاماتي", "احفظ موضع القراءة وارجع له من علاماتي. العلامات المحفوظة على الهاتف تفضل زي ما هي."),
            GuideStep("reader.tools", "الأدوات", "افتح أدوات التعليم والملاحظات عند الحاجة. اختيار الأداة بيحدد طريقة تعاملك مع الصفحة."),
            GuideStep("reader.full", "تكبير الصفحة", "زر تكبير بيخفي أجزاء الواجهة علشان يوسّع القراءة. تقدر تكبّر بإصبعين وترجع للتكبير العادي."),
            GuideStep("reader.read", "وضع القراءة", "وضع القراءة بيعرض الصفحة بشكل أهدى وأوضح. اضغط على كلمات الآية في الطبعة الملونة للاستماع لها."),
            GuideStep("reader.settings", "الإعدادات والصوت", "اختار القارئ والشكل المريح لك. من لوحة الصوت شغّل أو أوقف، وغيّر السرعة والتكرار؛ تحميل التسجيلات يحتاج إنترنت."),
            GuideStep("", "الحفظ والتسميع والمعاني", "من أقسام المصحف تلاقي الحفظ والتسميع والمتشابهات ومعاني الكلمات. أدوات المقارنة مساعدة للتعلّم، وراجع التلاوة مع معلّم عند الحاجة."))),
        "hifz" to ("الحفظ والتسميع" to listOf(
            GuideStep("", "اختيار المحفوظ", "من قسم الحفظ اختار السورة ونطاق الآيات. ابدأ بمقدار مناسب ليك وراجعه قبل الانتقال لغيره."),
            GuideStep("", "التكرار والاستماع", "اختار القارئ وعدد مرات التكرار، واستمع للمقطع ثم ردّده. التشغيل والإيقاف واختيار القارئ تحت تحكمك."),
            GuideStep("", "إخفاء وإظهار النص", "استخدم وسائل إخفاء النص أو إظهاره الموجودة في شاشة الحفظ للتدرّج في المراجعة من غير تغيير نص الآيات."),
            GuideStep("", "التسميع بالصوت", "افتح التسميع واختار الآيات ثم ابدأ بنفسك. التعرف على الكلام يحتاج إذن الميكروفون، وقد يحتاج إنترنت حسب خدمة الهاتف."),
            GuideStep("", "مراجعة نتيجة التسميع", "النتيجة مساعدة آلية وقد تخطئ في التعرف على صوتك؛ راجع الآية والتلاوة مع معلّم، ولا تعتمد عليها وحدها للحكم على صحة القراءة."))),
        "khatmas" to ("الختمات والأعضاء والمحادثات" to listOf(
            GuideStep("", "إنشاء أو انضمام", "من الختمات تقدر تنشئ ختمة أو تنضم بالكود. اختار الخطة ومدة الورد المناسبة للمجموعة."),
            GuideStep("", "متابعة الورد", "افتح وردك، واقرأه، وبعد الإتمام أكّد القراءة بنفسك. الجدول بيعرض التوزيع وحالة الإنجاز."),
            GuideStep("", "الأعضاء والمحادثات", "من الأعضاء تقدر تفتح محادثة خاصة مع عضو متاح. احترم خصوصية الآخرين، واستخدم الحظر أو الإبلاغ عند الحاجة."),
            GuideStep("", "صلاحيات القائد", "إعدادات الختمة وإدارة الأعضاء والدعوات بتظهر حسب دورك وصلاحياتك. أرشفة الختمة بتخفيها على هذا الهاتف."))),
        "prayer" to ("الصلاة والأذان" to listOf(
            GuideStep("", "المكان والمواقيت", "اختار مدينتك يدويًا أو استخدم الموقع بإذنك، وراجع المنطقة الزمنية وطريقة الحساب والتعديلات المحلية."),
            GuideStep("", "الأذان الكامل", "من الأصوات والضبط اختار التسجيل واضغط استماع لتجربته للآخر. مستوى الأذان داخل التطبيق مستقل عن مستوى المنبّه في الهاتف."),
            GuideStep("", "الصوت والتنبيه", "تقدر تكتم صوت صلاة معيّنة مع بقاء إشعار دخول الوقت. إيقاف الآن أو زر الإشعار يوقف الصوت فورًا."),
            GuideStep("", "أذونات الهاتف", "اسمح بالتنبيهات والمنبّهات الدقيقة لو عايز التنبيه في موعده. إعدادات توفير البطارية على بعض الموبايلات ممكن تأثر على التشغيل بالخلفية."))),
        "adhkar" to ("الأذكار والتقدّم" to listOf(
            GuideStep("", "اختار الباب", "افتح القسم المناسب لوقتك، أو ابحث باسم الباب. عدد الأذكار والتقدّم ظاهرين في القائمة."),
            GuideStep("", "العدّاد", "مع كل ترديد اضغط على الذكر. عند اكتمال العدد تظهر علامة الإتمام، وتقدر تنتقل للذكر التالي."),
            GuideStep("", "كمّل من حيث وقفت", "التقدّم بيتحفظ محليًا. استخدم كمّل من حيث وقفت للرجوع للباب اللي بدأت فيه."),
            GuideStep("", "البحث والمفضّلة", "ابحث عن الباب أو الذكر، واحفظ الأذكار اللي بترجع لها كتير في المفضّلة. عدّاد اليوم منفصل عن المفضلة."),
            GuideStep("", "السبحة", "افتح السبحة واختار الذكر والعدد المناسب. اضغط مع كل تسبيحة وتابع العدّاد بدل ما تغيّر تقدّم أبواب الأذكار."),
            GuideStep("", "الرقية وأدعية القرآن والختم", "من أبواب القسم تقدر تفتح الرقية الشرعية وأدعية القرآن ودعاء الختم. النصوص والمصادر الأصلية محفوظة كما هي."),
            GuideStep("", "الاستماع وأدوات الصوت", "ابدأ الاستماع أو استخدام الميكروفون باختيارك فقط. لو الصوت مش متاح لعنصر معيّن، تقدر تقرأ النص وتكمل العدّاد عادي."))),
        "profile" to ("الحساب والتنبيهات والخصوصية" to listOf(
            GuideStep("", "الاسم والدخول", "عدّل الاسم من علامة القلم. طريقة دخولك الحالية بالإيميل أو Google تفضل زي ما هي؛ احتفظ ببيانات حسابك."),
            GuideStep("", "التنبيهات والأصوات", "اضبط تذكيرات الورد والأذكار والصلاة. صوت الافتتاحية وصوت تقليب الورق ليهم اختيارات تشغيل وإيقاف مستقلة."),
            GuideStep("", "القارئ والوضع الليلي", "اختار قارئك المفضّل، واضبط الوضع الليلي للمصحف بالطريقة المريحة لعينيك."),
            GuideStep("", "الخصوصية", "من الخصوصية وبياناتك تعرف البيانات والصلاحيات وطرق طلب الحذف. حذف الحساب إجراء نهائي وله شاشة تأكيد منفصلة."))),
        "ibtehal" to ("الابتهالات" to listOf(
            GuideStep("", "الاستماع", "قسم الابتهالات يفتح لك مصادر الاستماع المتاحة. البث يحتاج إنترنت، وبيبدأ باختيارك فقط.")))
    )

    fun markRestored(context: Context, userId: Long) {
        context.getSharedPreferences("p85_guide", Context.MODE_PRIVATE).edit().putBoolean("seen_$userId", true).apply()
    }
    fun signedIn(context: Context, userId: Long) {
        val prefs = context.getSharedPreferences("p85_guide", Context.MODE_PRIVATE)
        if (P85Rules.showGuide(false, prefs.getBoolean("seen_$userId", false), userId > 0)) {
            prefs.edit().putBoolean("seen_$userId", true).apply()
            start("home")
        }
    }
    fun start(chapter: String = "home") { topic = chapter.takeIf { it in chapters } ?: "home"; index = 0; menu = false }
    fun openMenu() { topic = "home"; index = 0; menu = true }
    private fun key() = topic?.let { chapters[it]?.second?.getOrNull(index)?.key }.orEmpty()

    private fun windowOffset(view:android.view.View):androidx.compose.ui.geometry.Offset {
        val screen=IntArray(2);val window=IntArray(2)
        view.getLocationOnScreen(screen);view.getLocationInWindow(window)
        return androidx.compose.ui.geometry.Offset((screen[0]-window[0]).toFloat(),(screen[1]-window[1]).toFloat())
    }

    @OptIn(ExperimentalFoundationApi::class)
    fun Modifier.target(id: String): Modifier = composed {
        val view=LocalView.current
        val bring = remember { BringIntoViewRequester() }
        LaunchedEffect(key() == id, menu) {
            if (key() == id && !menu) { delay(80); runCatching { bring.bringIntoView() } }
        }
        DisposableEffect(id) { onDispose { bounds.remove(id) } }
        this.bringIntoViewRequester(bring).onGloballyPositioned { bounds[id] = it.boundsInWindow().translate(windowOffset(view)) }
    }

    @Composable fun HelpButton(chapter: String? = null, modifier: Modifier = Modifier) {
        TextButton(onClick = { if (chapter == null) openMenu() else start(chapter) }, modifier = modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(if (chapter == null) "❔ شرح التطبيق" else "❔ شرح الشاشة", color = green)
        }
    }

    @Composable fun Host() {
        val current = topic ?: return
        val pair = chapters[current] ?: return
        val steps = pair.second
        val step = steps[index.coerceIn(0, steps.lastIndex)]
        var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        Dialog(onDismissRequest = { topic = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val dialogView=LocalView.current
                BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft + windowOffset(dialogView) }) {
                    val rect = if (menu) null else bounds[step.key]?.translate(-origin)
                    Canvas(Modifier.fillMaxSize()) {
                        val visible = rect?.takeIf { it.width > 0 && it.height > 0 && it.top >= 0 && it.bottom <= size.height }
                        val scrim = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            visible?.let { addRoundRect(RoundRect(it.inflate(4.dp.toPx()), CornerRadius(15.dp.toPx()))) }
                        }
                        drawPath(scrim, Color(0xBB001E16))
                        visible?.let { drawRoundRect(Color(0xFFD6B25B), it.topLeft, it.size, CornerRadius(15.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }
                    }
                    val alignment = if (rect != null && rect.center.y > constraints.maxHeight * .52f) Alignment.TopCenter else Alignment.BottomCenter
                    Surface(Modifier.align(alignment).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
                        .fillMaxWidth().heightIn(max = maxHeight * .58f), shape = RoundedCornerShape(22.dp), color = Color(0xFFFFFAEF)) {
                        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (menu) {
                                Text("دليل استخدام ختمة", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = green)
                                Text("اختار القسم اللي تحب تعرفه. الشرح ما بينفّذش أي إجراء على حسابك.", fontSize = 13.sp)
                                chapters.forEach { (id, info) ->
                                    OutlinedButton(onClick = { start(id) }, modifier = Modifier.fillMaxWidth()) { Text(info.first) }
                                }
                                TextButton(onClick = { topic = null }, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
                            } else {
                                Text("${pair.first} • ${index + 1} من ${steps.size}", fontSize = 12.sp, color = green)
                                LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth(), color = green)
                                Text(step.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = green)
                                Text(step.text, fontSize = 16.sp, lineHeight = 26.sp)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = { if (index < steps.lastIndex) index++ else { menu = true; index = 0 } }, colors = ButtonDefaults.buttonColors(containerColor = green)) {
                                        Text(if (index < steps.lastIndex) "التالي" else "دليل الأقسام")
                                    }
                                    TextButton(enabled = index > 0, onClick = { index-- }) { Text("السابق") }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { topic = null }) { Text("تخطي") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
