package com.ab.khatma.secure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** UI-only composables: no API calls, secrets, storage or legacy endpoints. */
private val khatmaGreen = Color(0xFF126948)
private val khatmaGold = Color(0xFFD9B560)

@Composable
fun KhatmaPolishedLogin(
    createAccount: Boolean,
    onModeChange: (Boolean) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    accountId: String,
    onAccountIdChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    busy: Boolean,
    onSubmit: () -> Unit
) {
    var revealPassword by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(14.dp))
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = khatmaGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("۞", style = MaterialTheme.typography.displayLarge, color = khatmaGold)
                Text("خَتْمَة", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
                Text("معًا نختم القرآن الكريم", style = MaterialTheme.typography.titleMedium,
                    color = Color.White)
                Text("مجموعات للقراءة · توزيع واضح · إنجاز يومي",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6F5EA))
            }
        }
        Text(if (createAccount) "أنشئ حسابك" else "أهلًا بعودتك",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth())
        Text("كل مجموعاتك وختماتك في مكان واحد، وبخصوصية تامة.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth())
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = createAccount,
                onClick = { onModeChange(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                label = { Text("حساب جديد") }
            )
            SegmentedButton(
                selected = !createAccount,
                onClick = { onModeChange(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                label = { Text("تسجيل الدخول") }
            )
        }
        ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (createAccount) {
                    OutlinedTextField(
                        value = name, onValueChange = onNameChange,
                        label = { Text("اسمك") }, placeholder = { Text("الاسم الظاهر للمجموعة") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = accountId, onValueChange = { onAccountIdChange(it.filter(Char::isDigit)) },
                        label = { Text("رقم الحساب") },
                        supportingText = { Text("الرقم الذي ظهر عند إنشاء الحساب") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                OutlinedTextField(
                    value = password, onValueChange = onPasswordChange,
                    label = { Text("كلمة المرور") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { revealPassword = !revealPassword }) {
                            Icon(
                                if (revealPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (revealPassword) "إخفاء كلمة المرور" else "عرض كلمة المرور"
                            )
                        }
                    },
                    visualTransformation = if (revealPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                if (createAccount) {
                    Text("استخدم كلمة مرور لا تقل عن 12 حرفًا.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onSubmit,
                    enabled = !busy && password.length >= (if (createAccount) 12 else 1) &&
                        (if (createAccount) name.trim().length >= 2 else accountId.toLongOrNull() != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = khatmaGreen)
                ) {
                    Text(if (createAccount) "إنشاء الحساب والمتابعة" else "دخول إلى ختمة")
                }
            }
        }
        Text(
            "الدخول بكلمة مرور متاح مؤقتًا في نسخة الاختبار. تسجيل Google ورقم الهاتف سيتم تفعيله بعد تجهيز Firebase.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun KhatmaPolishedHomeHeader(
    userName: String,
    groupCount: Int,
    onNewGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onOpenMushaf: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("السلام عليكم، " + userName, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("ابدأ قراءتك اليوم وشارك أجر الختمة مع مجموعتك.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ElevatedCard(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("۞", style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.secondary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("وردك اليوم", style = MaterialTheme.typography.titleLarge)
                    Text("افتح المصحف وتابع قراءتك",
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onOpenMushaf, shape = RoundedCornerShape(12.dp)) {
                        Text("افتح المصحف")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNewGroup, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)) { Text("＋ مجموعة جديدة") }
            OutlinedButton(onClick = onJoinGroup, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)) { Text("انضم بكود") }
        }
        Text("مجموعاتي (" + groupCount + ")",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (groupCount == 0) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("لسه ما عندكش مجموعات", style = MaterialTheme.typography.titleMedium)
                    Text("أنشئ مجموعة وشارك كود الدعوة، أو انضم لمجموعة موجودة.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun KhatmaPolishedGroupCard(
    item: SecureV2Api.TodayItem,
    onOpen: () -> Unit
) {
    val status = if (item.status == "active") "نشطة" else if (item.status == "pending") {
        "بانتظار موافقة القائد"
    } else "غير نشطة"
    val role = when (item.role) { "leader" -> "قائد"; "assistant" -> "مساعد"; else -> "عضو" }
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.groupName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(role + " · " + status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("‹", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (item.status == "active" && item.totalCount > 0) {
                val progress = (item.doneCount.toFloat() / item.totalCount).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress },
                    modifier = Modifier.fillMaxWidth())
                Text("إنجاز قراءة الأجزاء اليوم: " + item.doneCount + " من " + item.totalCount,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(if (item.status == "active") "اضغط لمتابعة الختمة" else "اضغط لعرض حالة الطلب",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun KhatmaPolishedGroupNavigation(selected: String, canManage: Boolean,
                                  onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("إدارة المجموعة وقراءتك",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val tabs = if (canManage) listOf("read" to "القراءة", "chat" to "الشات", "leader" to "الإدارة")
                       else listOf("read" to "القراءة", "chat" to "الشات")
            tabs.forEachIndexed { i, tab ->
                SegmentedButton(
                    selected = selected == tab.first,
                    onClick = { onSelect(tab.first) },
                    shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                    label = { Text(tab.second) }
                )
            }
        }
    }
}

@Composable
fun KhatmaPolishedProfile(userName: String, id: Long,
                          onCopyId: () -> Unit, onLogout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Text("حسابي", style = MaterialTheme.typography.headlineSmall)
        ElevatedCard(shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(userName, style = MaterialTheme.typography.titleLarge)
                Text("رقم الحساب: " + id)
                Text("احتفظ برقم الحساب للدخول إلى النسخة التجريبية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onCopyId) { Text("نسخ رقم الحساب") }
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("تسجيل الخروج")
        }
    }
}
