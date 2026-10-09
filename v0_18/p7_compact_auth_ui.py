"""Safe P7 patch: compact readable RTL onboarding, no authentication behavior changes."""
from pathlib import Path

root=Path("app/src/main/java/com/ab/khatma/secure")
panels=root/"KhatmaPolishedPanels.kt"
text=panels.read_text(encoding="utf-8")
start=text.index("@Composable\nfun KhatmaPolishedLogin(")
end=text.index("@Composable\nfun KhatmaPolishedHomeHeader(", start)

new_login='''@Composable
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
    var showPassword by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = khatmaGreen)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(containerColor = khatmaGold)
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Text("۞", style = MaterialTheme.typography.headlineMedium,
                             color = khatmaGreen)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("خَتْمَة", style = MaterialTheme.typography.titleLarge,
                         fontWeight = FontWeight.Bold, color = Color.White)
                    Text("معًا نختم القرآن الكريم",
                         style = MaterialTheme.typography.bodyMedium,
                         color = Color(0xFFEAF5ED))
                }
            }
        }

        Text(
            if (createAccount) "أنشئ حسابك" else "أهلًا بعودتك",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (createAccount) "ابدأ ختمتك مع العائلة والأصدقاء"
            else "ادخل لمتابعة مجموعاتك ووردك اليوم",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (createAccount) {
                    OutlinedTextField(
                        value = name, onValueChange = onNameChange,
                        label = { Text("اسمك") },
                        placeholder = { Text("اسمك الظاهر في المجموعات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = accountId,
                        onValueChange = { onAccountIdChange(it.filter(Char::isDigit)) },
                        label = { Text("رقم الحساب") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp)
                    )
                }
                OutlinedTextField(
                    value = password, onValueChange = onPasswordChange,
                    label = { Text("كلمة المرور") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "إخفاء كلمة المرور"
                                                     else "عرض كلمة المرور"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                                            else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                if (createAccount) {
                    Text("كلمة مرور لا تقل عن 12 حرفًا لحماية حسابك.",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onSubmit,
                    enabled = !busy && password.length >= (if (createAccount) 12 else 1) &&
                        (if (createAccount) name.trim().length >= 2
                         else accountId.toLongOrNull() != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = khatmaGreen)
                ) {
                    Text(if (createAccount) "إنشاء حسابي" else "دخول")
                }
            }
        }
        Text(
            "تسجيل Google والهاتف غير مفعّل بعد؛ يحتاج إعداد المصادقة الخاص بالتطبيق.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

'''
text=text[:start]+new_login+text[end:]

# Reuse the already tested business UI while removing the prominent tinted panels.
text=text.replace(
    'ElevatedCard(\n            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),',
    'ElevatedCard(\n            colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EB)),'
)
# Avoid large tinted lavender empty-state/group-card panels.
text=text.replace(
    'ElevatedCard(modifier = Modifier.fillMaxWidth(),\n                shape = RoundedCornerShape(20.dp))',
    'ElevatedCard(modifier = Modifier.fillMaxWidth(),\n                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),\n                shape = RoundedCornerShape(20.dp))'
)
text=text.replace(
    'modifier = Modifier.fillMaxWidth(),\n        shape = RoundedCornerShape(22.dp)\n    ) {',
    'modifier = Modifier.fillMaxWidth(),\n        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),\n        shape = RoundedCornerShape(22.dp)\n    ) {'
)
assert "var showPassword by remember" in text
assert "معًا نختم القرآن الكريم" in text
assert "Google والهاتف غير مفعّل" in text
panels.write_text(text,encoding="utf-8")

ui=root/"SecureV2Screen.kt"
s=ui.read_text(encoding="utf-8")
old='''        topBar = {
            TopAppBar('''
assert s.count(old)==1
s=s.replace(old,'''        topBar = {
            if (session != null) TopAppBar(''',1)
old='''                if (session == null) {
                    Text("أهلًا بك في ختمة — قراءتك ومجموعاتك في مكان واحد.",
                        style = MaterialTheme.typography.titleLarge)
                    Text("سجّل حسابًا آمنًا لتبدأ رحلتك مع القرآن.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)'''
new='''                if (message.isNotBlank()) {
                    val isSuccess = message.startsWith("تم") || message.startsWith("مرحبًا")
                    Text(message, color = if (isSuccess) MaterialTheme.colorScheme.primary
                                          else MaterialTheme.colorScheme.error)
                }'''
assert s.count(old)==1
s=s.replace(old,new,1)
ui.write_text(s,encoding="utf-8")

gradle=Path("app/build.gradle.kts")
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 22")==1 and g.count('"0.18.1-noor-ui"')==1
gradle.write_text(g.replace("versionCode = 22","versionCode = 23",1)
                     .replace('"0.18.1-noor-ui"','"0.18.2-compact-ui"',1),encoding="utf-8")
checks=Path("tools/verify_project.py")
v=checks.read_text(encoding="utf-8")
assert "versionCode 22" in v and "0.18.1-noor-ui" in v
checks.write_text(v.replace("versionCode 22","versionCode 23")
                  .replace("versionCode = 22","versionCode = 23")
                  .replace("0.18.1-noor-ui","0.18.2-compact-ui"),encoding="utf-8")
print("PASS: compact above-fold onboarding, no fake Google button, success notices, white cards, V0.18.2.")
