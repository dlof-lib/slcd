package org.dlof.slcd

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val SlcdChapterGreen = Color(0xFF1D7A3F)
private val SlcdChapterGold = Color(0xFFD2A020)

/**
 * ── شاشة "معلومات الفصل المتقدّمة" ───────────────────────────────────────
 *
 * عنوان الفصل، رقمه (للعرض فقط — ترقيمه الهيكلي ثابت)، تصنيف اختياري خاص
 * بالفصل، غلاف مخصّص اختياري (يعتمد سلسلة احتياط: غلاف الفصل ← أيقونة
 * الموسم ← غلاف القصة)، بانر الفصل، رابط خارجي خاص بهذا الفصل (يتجاوز رابط
 * القصة الافتراضي)، وإدارة أجنحة الفصل (لفصل طويل مقسّم لأجزاء منفصلة
 * الصفحات).
 */
@Composable
fun SlcdChapterInfoScreen(
    root: DocumentFile,
    chapter: SlcdChapter,
    fallbackSeasonIconUri: Uri?,
    fallbackStoryCoverUri: Uri?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SlimeComicsRepository(context) }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(chapter.title.orEmpty()) }
    var genre by remember { mutableStateOf(chapter.genre.orEmpty()) }
    var externalUrl by remember { mutableStateOf(chapter.externalUrl.orEmpty()) }

    var customCover by remember { mutableStateOf(chapter.customCoverUri) }
    var bannerImage by remember { mutableStateOf(repo.chapterBannerImage(chapter.folder)?.uri) }
    var bannerVideo by remember { mutableStateOf(repo.chapterBannerVideo(chapter.folder)?.uri) }
    var wings by remember { mutableStateOf(chapter.wings) }

    var showAddWingDialog by remember { mutableStateOf(false) }
    var renameWingTarget by remember { mutableStateOf<SlcdWing?>(null) }
    var deleteWingTarget by remember { mutableStateOf<SlcdWing?>(null) }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) customCover = repo.setChapterCover(root, chapter.seasonNumber, chapter.chapterNumber, uri)
    }
    val pickBannerImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            repo.setChapterBannerImage(root, chapter.seasonNumber, chapter.chapterNumber, uri)
            bannerImage = uri; bannerVideo = null
        }
    }
    val pickBannerVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            repo.setChapterBannerVideo(root, chapter.seasonNumber, chapter.chapterNumber, uri)
            bannerVideo = uri; bannerImage = null
        }
    }

    val effectiveCoverPreview = customCover ?: fallbackSeasonIconUri ?: fallbackStoryCoverUri

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("معلومات الفصل ${chapter.chapterNumber}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (title.isNotBlank()) repo.renameChapter(root, chapter.seasonNumber, chapter.chapterNumber, title)
                        repo.setChapterGenre(root, chapter.seasonNumber, chapter.chapterNumber, genre)
                        if (externalUrl.isNotBlank()) {
                            repo.setChapterExternalLink(root, chapter.seasonNumber, chapter.chapterNumber, externalUrl)
                        } else {
                            repo.clearChapterExternalLink(root, chapter.seasonNumber, chapter.chapterNumber)
                        }
                        onSaved()
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "حفظ")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { pickCover.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(width = 70.dp, height = 96.dp)
                    ) {
                        if (effectiveCoverPreview != null) {
                            AsyncImage(
                                model = effectiveCoverPreview,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = SlcdChapterGreen)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("غلاف الفصل (اختياري)", fontWeight = FontWeight.Bold)
                        Text(
                            if (customCover != null) "مخصّص لهذا الفصل" else "يعتمد على غلاف الموسم/القصة تلقائياً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (customCover != null) {
                            TextButton(onClick = {
                                repo.deleteChapterCover(root, chapter.seasonNumber, chapter.chapterNumber)
                                customCover = null
                            }) { Text("استخدام الغلاف الافتراضي") }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الفصل") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = chapter.chapterNumber.toString(),
                    onValueChange = {},
                    label = { Text("رقم الفصل") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("تصنيف الفصل (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item { Divider() }

            item {
                Text("بانر الفصل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pickBannerImage.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (bannerImage != null) "تغيير الصورة" else "صورة بانر")
                    }
                    OutlinedButton(onClick = { pickBannerVideo.launch(arrayOf("video/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (bannerVideo != null) "تغيير الفيديو" else "فيديو بانر")
                    }
                }
            }

            item { Divider() }

            item {
                Text("رابط خارجي لهذا الفصل (اختياري)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "إن تُرك فارغاً، يُستخدم الرابط الخارجي الافتراضي للقصة (إن وُجد). إن أُضيف هنا، يُحوَّل القارئ لهذا الرابط تحديداً عند فتح الفصل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = externalUrl,
                    onValueChange = { externalUrl = it },
                    label = { Text("https://") },
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item { Divider() }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ViewCarousel, contentDescription = null, tint = SlcdChapterGreen)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("أجنحة الفصل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "لفصل طويل مقسّم لأجزاء (مثال: الجناح 1 = 10 صفحات، الجناح 2 = 9 صفحات...)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showAddWingDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "إضافة جناح", tint = SlcdChapterGreen)
                    }
                }
            }

            if (wings.isEmpty()) {
                item {
                    Text(
                        "لا توجد أجنحة بعد — الفصل يقرأ بصفحاته المباشرة (${chapter.pageCount} صفحة).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(wings, key = { it.number }) { wing ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    wing.title?.takeIf { it.isNotBlank() } ?: "الجناح ${wing.number}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${wing.pageCount} صفحة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { renameWingTarget = wing }) {
                                Icon(Icons.Filled.Edit, contentDescription = "إعادة تسمية")
                            }
                            IconButton(onClick = { deleteWingTarget = wing }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddWingDialog) {
        SlcdAddWingDialog(
            onDismiss = { showAddWingDialog = false },
            onConfirm = { wingTitle, pages ->
                scope.launch {
                    repo.addWingPages(root, chapter.seasonNumber, chapter.chapterNumber, wingTitle, pages)
                    wings = repo.listWings(chapter.folder)
                }
                showAddWingDialog = false
            }
        )
    }

    renameWingTarget?.let { wing ->
        SlcdRenameDialog(
            title = "عنوان الجناح ${wing.number}",
            currentValue = wing.title.orEmpty(),
            onConfirm = { newTitle ->
                repo.renameWing(chapter.folder, wing.number, newTitle)
                wings = repo.listWings(chapter.folder)
                renameWingTarget = null
            },
            onDismiss = { renameWingTarget = null }
        )
    }

    deleteWingTarget?.let { wing ->
        SlcdConfirmDeleteDialog(
            itemLabel = "الجناح ${wing.number}",
            onConfirm = {
                repo.deleteWing(chapter.folder, wing.number)
                wings = repo.listWings(chapter.folder)
                deleteWingTarget = null
            },
            onDismiss = { deleteWingTarget = null }
        )
    }
}

@Composable
private fun SlcdAddWingDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, pages: List<Uri>) -> Unit
) {
    var wingTitle by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val pickPages = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) pages = uris }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جناح جديد") },
        text = {
            Column {
                OutlinedTextField(
                    value = wingTitle,
                    onValueChange = { wingTitle = it },
                    label = { Text("عنوان الجناح (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { pickPages.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (pages.isEmpty()) "اختيار صفحات الجناح" else "${pages.size} صفحة مختارة")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(wingTitle, pages) },
                enabled = pages.isNotEmpty()
            ) { Text("إنشاء") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
