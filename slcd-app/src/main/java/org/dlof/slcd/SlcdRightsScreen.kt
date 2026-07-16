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

private val SlcdRightsGreen = Color(0xFF1D7A3F)

/**
 * ── شاشة "قاموس الحقوق" ─────────────────────────────────────────────────
 *
 * صفحة حقوق واحدة لكامل المكتبة، بأي من أربع صيغ: HTML (منسّق، مع معاينة
 * حقيقية عبر WebView)، Markdown، نص عادي، أو صورة واحدة. اختيار صيغة جديدة
 * يستبدل أي صيغة سابقة تلقائياً (راجع [SlimeComicsRepository.saveRightsText]
 * و[SlimeComicsRepository.saveRightsImage]).
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
    var toast by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            repo.saveRightsImage(root, uri)
            imageUri = repo.loadRightsDoc(root)?.fileUri
            toast = "تم حفظ صفحة الحقوق كصورة"
        }
    }

    Scaffold(
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
                        IconButton(onClick = { showPreview = !showPreview }) {
                            Icon(Icons.Filled.Visibility, contentDescription = "معاينة")
                        }
                        IconButton(onClick = {
                            repo.saveRightsText(root, format, textContent)
                            toast = "تم حفظ صفحة الحقوق"
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "حفظ")
                        }
                    }
                    IconButton(onClick = {
                        repo.deleteRights(root)
                        textContent = ""; imageUri = null
                        toast = "تم حذف صفحة الحقوق"
                    }) {
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

    toast?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
    }
}
