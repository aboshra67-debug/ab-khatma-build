package com.ab.khatma.secure

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ab.khatma.R

private val Forest = Color(0xFF11533D)
private val ForestDark = Color(0xFF083B2D)
private val Gold = Color(0xFFD5AB55)
private val Paper = Color(0xFFFAF7F0)
private val Ink = Color(0xFF203B32)

@Composable
fun PremiumKhatmaTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF9EDDB8),
        onPrimary = ForestDark,
        secondary = Gold,
        background = Color(0xFF101F1A),
        surface = Color(0xFF1B3028),
        surfaceVariant = Color(0xFF2A453A),
        onSurface = Color(0xFFF7F6ED),
        onBackground = Color(0xFFF7F6ED),
        onSurfaceVariant = Color(0xFFD4E7DB),
        outline = Color(0xFF789B8A)
    ) else lightColorScheme(
        primary = Forest,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0EFE5),
        onPrimaryContainer = ForestDark,
        secondary = Color(0xFF947027),
        onSecondary = Color.White,
        background = Paper,
        surface = Color.White,
        surfaceVariant = Color(0xFFF0F4EE),
        onSurface = Ink,
        onBackground = Ink,
        onSurfaceVariant = Color(0xFF64766D),
        outline = Color(0xFFB4C2B6)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun PremiumLoginPanel(
    isRegister: Boolean,
    onRegisterMode: (Boolean) -> Unit,
    name: String,
    onName: (String) -> Unit,
    accountId: String,
    onAccountId: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    busy: Boolean,
    onSubmit: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.size(110.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(ForestDark, Forest))),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "شعار ختمة",
                modifier = Modifier.size(91.dp)
            )
        }
        Text("ختمة", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("نقرأ معًا.. ونختم معًا", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary)
        Text(
            "مجموعتك، وردك اليومي، وإنجازك.. في مكان واحد",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(7.dp))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(19.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("أهلًا بيك في ختمة", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                TabRow(selectedTabIndex = if (isRegister) 0 else 1) {
                    Tab(selected = isRegister, onClick = { onRegisterMode(true) },
                        text = { Text("إنشاء حساب") })
                    Tab(selected = !isRegister, onClick = { onRegisterMode(false) },
                        text = { Text("عندي حساب") })
                }
                if (isRegister) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = onName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اسمك اللي هيظهر في مجموعاتك") },
                        placeholder = { Text("الاسم الأول واسم العائلة") },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = accountId,
                        onValueChange = onAccountId,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("رقم العضوية") },
                        supportingText = { Text("تجده في صفحة حسابي أو شاشة إنشاء الحساب") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = onPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("كلمة المرور") },
                    visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "إخفاء" else "إظهار")
                        }
                    },
                    supportingText = { if (isRegister) Text("12 حرفًا على الأقل لحماية حسابك") },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
                Button(
                    onClick = onSubmit,
                    enabled = !busy && password.length >= 12 &&
                        (if (isRegister) name.trim().length >= 2
                         else accountId.toLongOrNull() != null),
                    modifier = Modifier.fillMaxWidth().height(55.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(if (isRegister) "إنشاء حسابي وبدء الختمة" else "دخول إلى ختمة") }
                Text(
                    "هذه طريقة دخول مؤقتة وآمنة للاختبار. تسجيل Google ورقم الهاتف يتطلب تفعيل Firebase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("ختمة · رحلة طيبة مع كتاب الله", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun PremiumHomeHeader(userName: String, count: Int) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
            .background(Brush.horizontalGradient(listOf(ForestDark, Forest)))
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("السلام عليكم، $userName", style = MaterialTheme.typography.titleLarge,
                color = Color.White, fontWeight = FontWeight.Bold)
            Text("كل صفحة تقرّبنا من ختمة جديدة", color = Color(0xFFFFE6AE),
                style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
            Text(
                if (count == 0) "ابدأ مجموعتك الأولى" else "مجموعاتك النشطة والمنتظرة: $count",
                color = Color.White, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun PremiumHomeActions(onCreate: () -> Unit, onJoin: () -> Unit, onQuran: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCreate, modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text("＋ مجموعة جديدة")
            }
            OutlinedButton(onClick = onJoin, modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text("انضم بكود")
            }
        }
        OutlinedCard(onClick = onQuran, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("المصحف الشريف", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("تابع قراءتك في أي وقت", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("۞", style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun PremiumGroupTile(
    groupName: String,
    role: String,
    status: String,
    completed: Int,
    total: Int,
    onOpen: () -> Unit
) {
    val isActive = status == "active"
    val roleName = when (role) {
        "leader" -> "قائد المجموعة"
        "assistant" -> "مشرف مساعد"
        else -> "عضو"
    }
    val progress = if (total > 0) completed.toFloat()/total else 0f
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(47.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center) {
                    Text("۞", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(groupName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(roleName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (isActive) "● نشطة" else "◷ بانتظار الموافقة",
                    color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall)
            }
            if (isActive) {
                LinearProgressIndicator(progress = { progress.coerceIn(0f,1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(5.dp)))
                Text("إنجاز المجموعة اليوم: $completed من $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("طلب الانضمام في انتظار قرار القائد",
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(if (isActive) "فتح المجموعة ←" else "تفاصيل الطلب ←",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PremiumEmptyGroups() {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("۞", style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.secondary)
            Text("مفيش مجموعات لسه", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("أنشئ مجموعة وشارك أصحابك، أو انضم بكود الدعوة",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
