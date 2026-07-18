@file:OptIn(ExperimentalMaterial3Api::class)

package org.dlof.slcd

import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val SlcdRightsGreen = Color(0xFF10B981)
private val SlcdRightsGold = Color(0xFFFBBF24)

/**
 * ── قوالب "قاموس الحقوق" الاحترافية الجاهزة ─────────────────────────────
 *
 * نصوص قانونية/حقوقية شائعة جاهزة للإدراج الفوري (زر واحد) بدل الكتابة من
 * الصفر — تغطي أشيع ما تحتاجه مكتبة قصص مصورة مستقلة: حقوق النشر الأساسية،
 * حظر إعادة النشر، سياسة استخدام عادل مختصرة، وإخلاء مسؤولية محتوى. كل
 * قالب متاح بصيغتي HTML وMarkdown ونص عادي تلقائياً بحسب الصيغة المختارة.
 */
private enum class SlcdRightsTemplate(val label: String, val icon: String) {
    COPYRIGHT("© حقوق النشر الأساسية", "©"),
    NO_REPUBLISH("⛔ حظر إعادة النشر", "⛔"),
    FAIR_USE("⚖️ سياسة استخدام عادل", "⚖️"),
    DISCLAIMER("ℹ️ إخلاء مسؤولية المحتوى", "ℹ️"),
    DMCA("📩 بلاغ انتهاك حقوق (DMCA)", "📩");

    fun render(format: SlcdRightsFormat, publisherName: String, year: String): String {
        val name = publisherName.ifBlank { "[اسم الناشر/المؤلف]" }
        val body = when (this) {
            COPYRIGHT -> "© $year $name. جميع الحقوق محفوظة.\nلا يجوز نسخ أي جزء من هذا العمل (نصوصاً أو رسوماً) أو توزيعه أو نشره بأي وسيلة دون إذن كتابي مسبق من $name، باستثناء الاقتباسات القصيرة لأغراض المراجعة النقدية."
            NO_REPUBLISH -> "هذا العمل مخصّص للقراءة داخل هذا التطبيق فقط.\nيُمنع منعاً باتاً إعادة رفع الفصول أو ترجمتها أو نشرها على أي منصة أو موقع أو تطبيق آخر دون إذن كتابي مسبق من $name. أي مخالفة تُعرّض صاحبها للمساءلة القانونية."
            FAIR_USE -> "يُسمح باقتباس مقاطع قصيرة (نص أو صورة لوحة واحدة) لأغراض المراجعة النقدية أو التعليق أو التعليم، بشرط ذكر المصدر ورابط العمل الأصلي بوضوح، وعدم الإخلال بالمعنى العام للعمل."
            DISCLAIMER -> "هذا العمل قصة خيالية؛ أي تشابه مع أشخاص حقيقيين أو أحداث واقعية هو محض صدفة ما لم يُذكر خلاف ذلك صراحة. آراء الشخصيات لا تعبّر بالضرورة عن رأي $name."
            DMCA -> "إن كنت تعتقد أن محتوى هذه المكتبة ينتهك حقوق ملكيتك الفكرية، راسلنا موضحاً: (1) العمل المحمي، (2) موقع المحتوى المخالف، (3) بيانات التواصل معك، (4) إقرار بحسن النية. سنراجع البلاغ ونرد خلال مدة معقولة."
        }
        return when (format) {
            SlcdRightsFormat.HTML -> "<p>" + body.replace("\n", "<br/>") + "</p>"
            SlcdRightsFormat.MARKDOWN -> body
            else -> body
        }
    }
}

/**
 * ── شاشة "قاموس الحقوق" ─────────────────────────────────────────────────
 *
 * صفحة حقوق واحدة لكامل المكتبة، بأي من أربع صيغ: HTML (منسّق، مع معاينة
 * حقيقية عبر WebView)، Markdown، نص عادي، أو صورة واحدة. اختيار صيغة جديدة
 * يستبدل أي صيغة سابقة تلقائياً (راجع [SlimeComicsRepository.saveRightsText]
 * و[SlimeComicsRepository.saveRightsImage]).
 *
 * تحسينات هذا الإصدار:
 *  - إصلاح: رسائل التأكيد ("تم الحفظ"...) كانت تُحسب داخلياً لكنها لم تكن
 *    تُعرض فعلياً على الشاشة (لا Snackbar) — أُضيف [SnackbarHost] حقيقي.
 *  - تأكيد قبل الحذف بدل حذف فوري بلا رجعة بلمسة واحدة.
 *  - قاموس قوالب احترافية جاهزة ([SlcdRightsTemplate]) للإدراج الفوري.
 */
@Composable
fun SlcdRightsScreen(root: DocumentFile, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SlimeComicsRepository(context) }

    val existing = remember { repo.loadRightsDoc(root) }
    var format by remember { mutableStateOf(existing?.format ?: SlcdRightsFormat.TEXT) }
    var textContent by remember { mutableStateOf(existing?.textContent.orEmpty()) }
    var imageUri by remember { mutableStateOf(existing?.takeIf { it.format == SlcdRightsFormat.IMAGE }?.fileUri) }
    var showPreview by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            repo.saveRightsImage(root, uri)
            imageUri = repo.loadRightsDoc(root)?.fileUri
            notify("تم حفظ صفحة الحقوق كصورة")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("قاموس الحقوق") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (format != SlcdRightsFormat.IMAGE) {
                        IconButton(onClick = { showTemplates = true }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "قوالب جاهزة", tint = SlcdRightsGold)
                        }
                        IconButton(onClick = { showPreview = !showPreview }) {
                            Icon(Icons.Filled.Visibility, contentDescription = "معاينة")
                        }
                        IconButton(onClick = {
                            repo.saveRightsText(root, format, textContent)
                            notify("تم حفظ صفحة الحقوق")
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "حفظ")
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp)) {
            Text("صيغة صفحة الحقوق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SlcdRightsFormat.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = format == option,
                        onClick = { format = option; showPreview = false },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = SlcdRightsFormat.entries.size)
                    ) {
                        Text(option.displayName)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (format == SlcdRightsFormat.IMAGE) {
                Surface(
                    onClick = { pickImage.launch(arrayOf("image/*")) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = SlcdRightsGreen)
                                Spacer(Modifier.height(6.dp))
                                Text("اضغط لاختيار صورة صفحة الحقوق")
                            }
                        }
                    }
                }
            } else if (showPreview && format == SlcdRightsFormat.HTML) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx -> WebView(ctx) },
                    update = { webView -> webView.loadDataWithBaseURL(null, textContent, "text/html", "UTF-8", null) }
                )
            } else if (showPreview) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Text(
                        textContent,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    label = {
                        Text(
                            when (format) {
                                SlcdRightsFormat.HTML -> "محتوى HTML"
                                SlcdRightsFormat.MARKDOWN -> "محتوى Markdown"
                                else -> "نص الحقوق"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }

    if (showTemplates) {
        SlcdRightsTemplatesDialog(
            format = format,
            onPick = { rendered ->
                textContent = if (textContent.isBlank()) rendered else textContent + "\n\n" + rendered
                showTemplates = false
                showPreview = false
                notify("أُدرج القالب — لا تنسَ الحفظ")
            },
            onDismiss = { showTemplates = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف صفحة الحقوق؟") },
            text = { Text("سيُحذف محتوى صفحة الحقوق الحالي نهائياً من مجلد المكتبة. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteRights(root)
                    textContent = ""; imageUri = null
                    showDeleteConfirm = false
                    notify("تم حذف صفحة الحقوق")
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") }
            }
        )
    }
}

/** حوار اختيار قالب جاهز من قاموس الحقوق الاحترافي وإدراجه فوراً في المحتوى. */
@Composable
private fun SlcdRightsTemplatesDialog(
    format: SlcdRightsFormat,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var publisherName by remember { mutableStateOf("") }
    val currentYear = remember { java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قاموس الحقوق — قوالب جاهزة") },
        text = {
            Column {
                Text(
                    "اختر قالباً لإدراجه فوراً في المحتوى الحالي، ثم عدّله كما تشاء قبل الحفظ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = publisherName,
                    onValueChange = { publisherName = it },
                    label = { Text("اسم الناشر/المؤلف (اختياري، يُدرَج في القوالب)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                SlcdRightsTemplate.entries.forEach { template ->
                    Surface(
                        onClick = { onPick(template.render(format, publisherName, currentYear)) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            template.label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
