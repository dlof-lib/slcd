package org.dlof.slcd

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieFilter
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

private val SlcdSeasonGreen = Color(0xFF1D7A3F)

/**
 * ── شاشة "إنشاء/تعديل موسم" ─────────────────────────────────────────────
 *
 * اسم الموسم ← أيقونة الموسم (رمز صغير، ليس غلافاً كاملاً) ← وصف وتصنيف
 * اختياريان ← بانر الموسم (صورة/فيديو) وفيديو مقدّمة اختياري يُعرض أول مرة
 * يُفتح فيها الموسم.
 */
@Composable
fun SlcdSeasonInfoScreen(
    root: DocumentFile,
    season: SlcdSeason,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SlimeComicsRepository(context) }

    var title by remember { mutableStateOf(season.title.orEmpty()) }
    var description by remember { mutableStateOf(season.description.orEmpty()) }
    var genre by remember { mutableStateOf(season.genre.orEmpty()) }

    var iconUri by remember { mutableStateOf(repo.seasonIconUri(season.folder)) }
    var bannerImage by remember { mutableStateOf(repo.seasonBannerImage(season.folder)?.uri) }
    var bannerVideo by remember { mutableStateOf(repo.seasonBannerVideo(season.folder)?.uri) }
    var introVideo by remember { mutableStateOf(repo.seasonIntroVideo(season.folder)?.uri) }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) iconUri = repo.setSeasonIcon(root, season.number, uri)
    }
    val pickBannerImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { repo.setSeasonBannerImage(root, season.number, uri); bannerImage = uri; bannerVideo = null }
    }
    val pickBannerVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { repo.setSeasonBannerVideo(root, season.number, uri); bannerVideo = uri; bannerImage = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("معلومات الموسم ${season.number}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (title.isNotBlank()) repo.renameSeason(root, season.number, title)
                        repo.setSeasonDescription(root, season.number, description)
                        repo.setSeasonGenre(root, season.number, genre)
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
                        onClick = { pickIcon.launch(arrayOf("image/*")) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(84.dp)
                    ) {
                        if (iconUri != null) {
                            AsyncImage(
                                model = iconUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = SlcdSeasonGreen)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("أيقونة الموسم", fontWeight = FontWeight.Bold)
                        Text(
                            "رمز صغير مميّز للموسم (ليس غلافاً كاملاً)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (iconUri != null) {
                            TextButton(onClick = { repo.deleteSeasonIcon(root, season.number); iconUri = null }) {
                                Text("إزالة الأيقونة")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم الموسم") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف الموسم") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }

            item {
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("تصنيف الموسم (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item { Divider() }

            item {
                Text("بانر الموسم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

            item {
                Text("فيديو مقدّمة الموسم (اختياري)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "يُعرض تلقائياً أول مرة يفتح فيها القارئ هذا الموسم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val pickIntro = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) introVideo = repo.setSeasonIntroVideo(season.folder, uri)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { pickIntro.launch(arrayOf("video/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.MovieFilter, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (introVideo != null) "تغيير فيديو المقدّمة" else "إضافة فيديو مقدّمة")
                    }
                    if (introVideo != null) {
                        OutlinedButton(onClick = { repo.deleteSeasonIntroVideo(season.folder); introVideo = null }) {
                            Icon(Icons.Filled.Delete, contentDescription = "إزالة")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
