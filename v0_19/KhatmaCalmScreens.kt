package com.ab.khatma.secure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared calm UI for all roles. Prayer and duas are intentionally not marked implemented. */
private val green = Color(0xFF17644A)
private val forest = Color(0xFF103E30)
private val gold = Color(0xFFC5A45C)
private val ink = Color(0xFF21342C)
private val soft = Color(0xFFF1F6F0)
private val edge = Color(0xFFE2E4DA)

@Composable
fun KhatmaCalmLogin(
    createAccount: Boolean,
    onModeChange: (Boolean) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    accountId: String,
    onAccountIdChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    busy: Boolean,
    googleReady: Boolean,
    onGoogleSignIn: () -> Unit,
    onSubmit: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Card(shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8D4A5))) {
                Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(com.ab.khatma.R.drawable.ab_khatma_mark),
                        contentDescription = "شعار ختمة AB", contentScale = ContentScale.Fit,
                        modifier = Modifier.size(75.dp))
                }
            }
            Text("خَتْمَة", color = forest, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium)
            Text("مَعًا نَخْتِمُ الْقُرْآنَ الْكَرِيمَ", color = green,
                style = MaterialTheme.typography.bodyMedium)
            Text("۞  ﷽  ۞", color = gold, style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (createAccount) "أنشئ حسابك" else "أهلًا بعودتك",
                fontWeight = FontWeight.Bold, color = ink,
                style = MaterialTheme.typography.titleLarge)
            Text(if (createAccount) "شارك ختمتك مع العائلة والأصدقاء"
                else "واصل رحلتك مع القرآن ومجموعاتك",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF607067))
        }
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, edge)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                OutlinedButton(onClick = onGoogleSignIn,
                    enabled = googleReady && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 53.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, edge)) {
                    Text("G", fontWeight = FontWeight.Black, color = Color(0xFF4285F4))
                    Spacer(Modifier.width(11.dp))
                    Text("المتابعة بحساب Google", color = ink)
                }
                if (!googleReady) Text("Google غير مُهيّأ في هذه النسخة. يمكنك الدخول برقم الحساب.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("أو بحساب ختمة", color = Color(0xFF68756C),
                        style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider(Modifier.weight(1f))
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !createAccount,
                        onClick = { onModeChange(false) },
                        shape = SegmentedButtonDefaults.itemShape(0,2),
                        label = { Text("تسجيل الدخول") })
                    SegmentedButton(selected = createAccount,
                        onClick = { onModeChange(true) },
                        shape = SegmentedButtonDefaults.itemShape(1,2),
                        label = { Text("حساب جديد") })
                }
                if (createAccount) {
                    OutlinedTextField(name, onNameChange,
                        label = { Text("الاسم الظاهر داخل ختمة") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                } else {
                    OutlinedTextField(accountId, { onAccountIdChange(it.filter(Char::isDigit)) },
                        label = { Text("رقم الحساب") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp))
                }
                OutlinedTextField(password, onPasswordChange,
                    label = { Text("كلمة المرور") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (showPassword) "إخفاء كلمة المرور" else "عرض كلمة المرور")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp))
                if (createAccount) Text("كلمة المرور لا تقل عن 12 حرفًا.",
                    color = Color(0xFF68756C), style = MaterialTheme.typography.labelSmall)
                Button(onClick = onSubmit,
                    enabled = !busy && password.length >= (if (createAccount) 12 else 1) &&
                        (if (createAccount) name.trim().length >= 2
                         else accountId.toLongOrNull() != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 51.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green)) {
                    Text(if (createAccount) "إنشاء حسابي" else "دخول إلى ختمة")
                }
            }
        }
        Text("يمكن ربط حسابك القديم بـGoogle من صفحة حسابي دون فقد مجموعاتك.",
            color = Color(0xFF657169), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HubAction(title: String, note: String, enabled: Boolean = true,
                      onClick: () -> Unit, modifier: Modifier = Modifier,
                      icon: @Composable () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, edge),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.White,
            disabledContainerColor = Color(0xFFF8F6F0))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            icon()
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = ink, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note, style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64736A), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun KhatmaCalmHome(userName: String, groupCount: Int, groupsLoaded: Boolean,
                   onOpenMushaf: () -> Unit, onOpenKhatmas: () -> Unit,
                   onJoinGroup: () -> Unit, onOpenProfile: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8D4A5))) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(com.ab.khatma.R.drawable.ab_khatma_mark),
                        "شعار ختمة", modifier = Modifier.size(48.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("خَتْمَة", fontWeight = FontWeight.Bold,
                    color = forest, style = MaterialTheme.typography.titleLarge)
                Text("رفيقك اليومي للقرآن الكريم",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF637468))
            }
            Text("۞", style = MaterialTheme.typography.headlineSmall, color = gold)
        }
        Card(colors = CardDefaults.cardColors(containerColor = green),
            shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(green, forest)))
                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ",
                    color = Color(0xFFDDEBDA), style = MaterialTheme.typography.bodySmall)
                Text(userName.ifBlank { "أهلًا بك" },
                    style = MaterialTheme.typography.titleLarge, color = Color.White,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("يوم جديد وورد جديد من القرآن الكريم",
                    color = Color(0xFFE1EDE3), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = soft),
            border = BorderStroke(1.dp, Color(0xFFDDE9DE))) {
            Row(Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(29.dp),
                    tint = green)
                Column(Modifier.weight(1f)) {
                    Text("وردك اليوم", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium, color = forest)
                    Text("افتح المصحف وتابع قراءتك",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF576B5D))
                }
                Button(onClick = onOpenMushaf,
                    colors = ButtonDefaults.buttonColors(containerColor = green)) { Text("اقرأ") }
            }
        }
        Text("الأقسام الرئيسية", color = ink, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubAction("المصحف", "القراءة والتدبر", onClick = onOpenMushaf,
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.MenuBook, null, tint = green)
            }
            HubAction("الختمات", if (groupsLoaded) "مجموعاتي: $groupCount" else "جاري التحميل",
                onClick = onOpenKhatmas, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Groups, null, tint = green)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubAction("الانضمام", "برمز الدعوة", onClick = onJoinGroup,
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.GroupAdd, null, tint = green)
            }
            HubAction("الصلاة والأذان", "قريبًا", enabled = false, onClick = {},
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.NotificationsActive, null, tint = gold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubAction("الأدعية والأذكار", "قريبًا", enabled = false, onClick = {},
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FavoriteBorder, null, tint = gold)
            }
            HubAction("حسابي", "الاسم والإعدادات", onClick = onOpenProfile,
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PersonOutline, null, tint = green)
            }
        }
        Text("وَاذْكُرْ رَبَّكَ كَثِيرًا", color = green,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
    }
}
