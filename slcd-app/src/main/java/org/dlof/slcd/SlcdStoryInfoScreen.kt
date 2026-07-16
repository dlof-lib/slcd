@file:OptIn(ExperimentalMaterial3Api::class)

package org.dlof.slcd

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
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

private val SlcdStoryGreen = Color(0xFF1D7A3F)
private val SlcdStoryGold = Color(0xFFD2A020)

/** قائمة دول مقترحة للاختيار السريع — يبقى الحقل نصاً حراً فيمكن كتابة أي دولة أخرى يدوياً. */
private val SlcdCommonCountries = listOf(
    "سوريا", "السعودية", "مصر", "الإمارات", "العراق", "الأردن", "لبنان", "المغرب",
    "الجزائر", "تونس", "ليبيا", "فلسطين", "اليمن", "الكويت", "قطر", "البحرين", "عُمان",
    "السودان", "تركيا", "أخرى"
)

/**
 * ── شاشة "إنشاء/تعديل معلومات القصة" ────────────────────────────────────
 *
 * صفحة إنشاء القصة الأساسية المطلوبة: اسم القصة ← وصف/تصنيف + تفاصيل الناشر
 * والدولة ← غلاف القصة الكامل، بالإضافة إلى بانر القصة (صورة/فيديو) وبانرات
 * الإعلانات (صورة/فيديو) ورابط خارجي افتراضي لكامل القصة. كل حقل يُحفظ فوراً
 * عند "حفظ" في ملفات نصية/وسائط بجذر المكتبة (راجع [SlimeComicsRepository]).
 */
@Composable
fun SlcdStoryInfoScreen(root: DocumentFile, library: SlcdLibrary, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SlimeComicsRepository(context) }

    var title by remember { mutableStateOf(library.title.orEmpty()) }
    var description by remember { mutableStateOf(library.description.orEmpty()) }
    var genre by remember { mutableStateOf(library.genre.orEmpty()) }
    var publisher by remember { mutableStateOf(library.publisher.orEmpty()) }
    var country by remember { mutableStateOf(library.country.orEmpty()) }
    var externalUrl by remember { mutableStateOf(library.externalUrl.orEmpty()) }
    var countryMenuExpanded by remember { mutableStateOf(false) }

    var coverUri by remember { mutableStateOf(repo.storyCoverUri(root)) }
    var bannerImage by remember { mutableStateOf(repo.storyBannerImage(root)?.uri) }
    var bannerVideo by remember { mutableStateOf(repo.storyBannerVideo(root)?.uri) }
    var adImage by remember { mutableStateOf(repo.storyAdImage(root)?.uri) }
    var adVideo by remember { mutableStateOf(repo.storyAdVideo(root)?.uri) }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coverUri = repo.setStoryCover(root, uri)
    }
    val pickBannerImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { repo.setStoryBannerImage(root, uri); bannerImage = uri; bannerVideo = null }
    }
    val pickBannerVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { repo.setStoryBannerVideo(root, uri); bannerVideo = uri; bannerImage = null }
    }
    val pickAdImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) adImage = repo.setStoryAdImage(root, uri)
    }
    val pickAdVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) adVideo = repo.setStoryAdVideo(root, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("معلومات القصة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (title.isNotBlank()) repo.setLibraryTitle(root, title)
                        repo.setLibraryDescription(root, description)
                        repo.setLibraryGenre(root, genre)
                        repo.setLibraryPublisher(root, publisher)
                        repo.setLibraryCountry(root, country)
                        repo.setLibraryExternalLink(root, externalUrl)
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
                // ── غلاف القصة الكامل ──
                Text("غلاف القصة الكامل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { pickCover.launch(arrayOf("image/*")) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                ) {
                    if (coverUri != null) {
                        AsyncImage(
                            model = coverUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = SlcdStoryGreen)
                                Spacer(Modifier.height(6.dp))
                                Text("اضغط لاختيار غلاف القصة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم القصة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف القصة") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }

            item {
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("التصنيف (مثال: أكشن، مغامرة)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Text("تفاصيل الناشر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it },
                    label = { Text("اسم/تفاصيل جهة النشر") },
                    leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = countryMenuExpanded,
                    onExpandedChange = { countryMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("الدولة") },
                        leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = countryMenuExpanded,
                        onDismissRequest = { countryMenuExpanded = false }
                    ) {
                        SlcdCommonCountries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    if (option != "أخرى") country = option
                                    countryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item { Divider() }

            item {
                Text("بانر القصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "يُعرض أعلى شاشة القصة — اختر صورة أو فيديو (أحدهما فقط).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                if (bannerImage != null || bannerVideo != null) {
                    TextButton(onClick = {
                        repo.deleteStoryBanner(root); bannerImage = null; bannerVideo = null
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إزالة البانر")
                    }
                }
            }

            item { Divider() }

            item {
                Text("إعلانات القصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "بانر صورة إعلاني وفيديو إعلاني اختياريان، منفصلان عن بانر القصة العادي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pickAdImage.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (adImage != null) "تغيير بانر الإعلان" else "بانر إعلاني")
                    }
                    OutlinedButton(onClick = { pickAdVideo.launch(arrayOf("video/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (adVideo != null) "تغيير فيديو الإعلان" else "فيديو إعلاني")
                    }
                }
                if (adImage != null) {
                    TextButton(onClick = { repo.deleteStoryAdImage(root); adImage = null }) {
                        Text("إزالة بانر الإعلان")
                    }
                }
                if (adVideo != null) {
                    TextButton(onClick = { repo.deleteStoryAdVideo(root); adVideo = null }) {
                        Text("إزالة فيديو الإعلان")
                    }
                }
            }

            item { Divider() }

            item {
                Text("رابط خارجي (اختياري)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "إن أُضيف، يُحوَّل القارئ لهذا الموقع بدل القراءة داخل SLCD لأي فصل لا يملك رابطاً خاصاً به.",
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

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
