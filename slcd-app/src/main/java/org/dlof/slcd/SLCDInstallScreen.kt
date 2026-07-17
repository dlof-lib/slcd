@file:OptIn(ExperimentalMaterial3Api::class)

package org.dlof.slcd

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dlof.slcd.settings.SlcdSettings

private val SlcdGreen = Color(0xFF10B981)

/**
 * ── شاشة "تثبيت" Slime Comics dlof (SLCD) ─────────────────────────────
 *
 * SLCD تطبيق مصغّر داخل DLoF لا يُستخدم مباشرة: يجب أولاً أن يختار
 * المستخدم (أو ينشئ) مجلد مكتبة القصص المصورة عبر منتقي مجلدات النظام
 * (Storage Access Framework)، فتُمنح للتطبيق صلاحية دائمة عليه ويُنشأ
 * هيكل `covers/` و`seasons/` بداخله — هذا تثبيت حقيقي (وليس شاشة وهمية):
 * بدونه لا تُفتح شاشة تصفّح المكتبة إطلاقاً، وبعده يبقى مثبَّتاً دائماً
 * (يُحفظ في [SlcdSettings]) حتى لو أُغلق التطبيق.
 */
@Composable
fun SLCDInstallScreen(
    onBack: () -> Unit,
    onInstalled: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SlimeComicsRepository(context) }
    val scope = rememberCoroutineScope()

    var isInstalling by remember { mutableStateOf(false) }
    var installStep by remember { mutableStateOf(0) } // 0..3 لعرض تقدّم التثبيت
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        errorMessage = null
        isInstalling = true
        scope.launch {
            try {
                installStep = 1
                delay(220)
                val root = repo.installLibrary(uri) // ← الخطوة الحقيقية: صلاحية دائمة + إنشاء covers/ وseasons/
                SlcdTemplateSeeder.seedLibraryIfEmpty(context, root) // يزرع الغلاف والموسم التجريبيَّين من أصول التطبيق، إن كانت المكتبة فارغة
                installStep = 2
                delay(220)
                SlcdSettings.markSlcdInstalled(context, uri.toString())
                installStep = 3
                delay(260)
                onInstalled()
            } catch (e: Exception) {
                errorMessage = e.message ?: "تعذّر تثبيت SLCD في المجلد المحدَّد"
                isInstalling = false
                installStep = 0
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تثبيت SLCD") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            Icon(
                Icons.Filled.Extension,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = SlcdGreen
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Slime Comics dlof (SLCD)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "هذه أداة مصغّرة داخل DLoF لتنظيم قصصك المصورة بمواسم وفصول " +
                    "وأغلفة. تحتاج تثبيتاً حقيقياً مرة واحدة: اختر أو أنشئ مجلداً " +
                    "على جهازك ليصبح مكتبة SLCD؛ سيُنشئ التطبيق بداخله تلقائياً " +
                    "مجلدَي covers/ وseasons/، وتُمنح صلاحية دائمة عليه.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    InstallStepRow("اختيار/إنشاء مجلد المكتبة", done = installStep >= 1, active = isInstalling && installStep == 0)
                    Spacer(Modifier.height(10.dp))
                    InstallStepRow("إنشاء covers/ وseasons/", done = installStep >= 2, active = installStep == 1)
                    Spacer(Modifier.height(10.dp))
                    InstallStepRow("حفظ التثبيت بشكل دائم", done = installStep >= 3, active = installStep == 2)
                }
            }

            errorMessage?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { pickFolderLauncher.launch(null) },
                enabled = !isInstalling,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("جارٍ التثبيت…")
                } else {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("اختر مجلد مكتبة SLCD وثبّت الأداة")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InstallStepRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            done -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SlcdGreen, modifier = Modifier.size(20.dp))
            active -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else -> Icon(
                Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
