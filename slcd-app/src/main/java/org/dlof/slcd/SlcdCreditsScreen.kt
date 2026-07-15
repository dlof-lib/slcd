package org.dlof.slcd

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val SlcdCreditsGreen = Color(0xFF1D7A3F)

/**
 * ── شاشة "فريق العمل والنشر" لمكتبة SLCD ────────────────────────────────
 *
 * تُدير كامل معلومات الاعتماد الاختيارية لمكتبة SLCD: أفراد فريق العمل
 * بأدوارهم الثابتة (المؤلف، المخرج، المشرف، الكاتب، الرسام، التلوين،
 * الترجمة) مع صورة شخصية اختيارية لكل فرد، اسم الشركة/الجهة الناشرة،
 * نص حقوق الطبع، وروابط التواصل الاجتماعي. تُحفظ في credits/credits.json
 * بجذر المكتبة (انظر [SlimeComicsRepository.saveCredits]) بشكل منفصل عن
 * [SlcdLibrary] الأساسية، فلا تُحمَّل إلا عند فتح هذه الشاشة تحديداً.
 */
@Composable
fun SlcdCreditsScreen(root: DocumentFile, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SlimeComicsRepository(context) }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var people by remember { mutableStateOf<List<SlcdCreditPerson>>(emptyList()) }
    var company by remember { mutableStateOf("") }
    var copyright by remember { mutableStateOf("") }
    var socialLinks by remember { mutableStateOf<List<SlcdSocialLink>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var dialogRole by remember { mutableStateOf<SlcdCreditRole?>(null) }
    var editingPerson by remember { mutableStateOf<SlcdCreditPerson?>(null) }
    var deleteTarget by remember { mutableStateOf<SlcdCreditPerson?>(null) }

    LaunchedEffect(root) {
        val loaded = withContext(Dispatchers.IO) { repo.loadCredits(root) }
        people = loaded.people
        company = loaded.company ?: ""
        copyright = loaded.copyright ?: ""
        socialLinks = loaded.socialLinks
        isLoading = false
    }

    fun persist() {
        scope.launch {
            withContext(Dispatchers.IO) {
                repo.saveCredits(
                    root,
                    SlcdCredits(
                        people = people,
                        company = company.trim().takeIf { it.isNotBlank() },
                        copyright = copyright.trim().takeIf { it.isNotBlank() },
                        socialLinks = socialLinks.filter { it.platform.isNotBlank() && it.url.isNotBlank() }
                    )
                )
            }
            toast = "تم حفظ معلومات فريق العمل والنشر"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فريق العمل والنشر") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { persist() }) {
                        Icon(Icons.Filled.Save, contentDescription = "حفظ")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Text("الشركة وحقوق الطبع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        singleLine = true,
                        label = { Text("الشركة / الجهة الناشرة") },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = copyright,
                        onValueChange = { copyright = it },
                        label = { Text("نص حقوق الطبع (مثال: © 2026 اسم الشركة. جميع الحقوق محفوظة)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Divider()
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("روابط التواصل الاجتماعي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            socialLinks = socialLinks + SlcdSocialLink(platform = "", url = "")
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "إضافة رابط")
                        }
                    }
                }
                itemsIndexed(socialLinks) { index, link ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = SlcdCreditsGreen)
                        OutlinedTextField(
                            value = link.platform,
                            onValueChange = { newPlatform ->
                                socialLinks = socialLinks.toMutableList().apply {
                                    this[index] = this[index].copy(platform = newPlatform)
                                }
                            },
                            singleLine = true,
                            label = { Text("المنصّة") },
                            modifier = Modifier.weight(0.4f)
                        )
                        OutlinedTextField(
                            value = link.url,
                            onValueChange = { newUrl ->
                                socialLinks = socialLinks.toMutableList().apply {
                                    this[index] = this[index].copy(url = newUrl)
                                }
                            },
                            singleLine = true,
                            label = { Text("الرابط") },
                            modifier = Modifier.weight(0.6f)
                        )
                        IconButton(onClick = {
                            socialLinks = socialLinks.toMutableList().apply { removeAt(index) }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف الرابط")
                        }
                    }
                }

                item {
                    Divider()
                }

                item {
                    Text("فريق العمل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                SlcdCreditRole.values().forEach { role ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(role.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = SlcdCreditsGreen)
                            TextButton(onClick = { dialogRole = role }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إضافة")
                            }
                        }
                    }

                    val rolePeople = people.filter { it.role == role }
                    if (rolePeople.isEmpty()) {
                        item {
                            Text(
                                "لا يوجد بعد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(rolePeople, key = { it.id }) { person ->
                            SlcdCreditPersonRow(
                                person = person,
                                onEdit = { editingPerson = person },
                                onDelete = { deleteTarget = person }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    val addOrEditRole = dialogRole
    if (addOrEditRole != null) {
        SlcdPersonEditDialog(
            role = addOrEditRole,
            initialName = "",
            initialPhotoUri = null,
            onDismiss = { dialogRole = null },
            onConfirm = { name, photoUri ->
                val newPerson = SlcdCreditPerson(id = UUID.randomUUID().toString(), role = addOrEditRole, name = name)
                scope.launch {
                    val savedFileName = photoUri?.let { uri ->
                        withContext(Dispatchers.IO) { repo.saveCreditPersonPhoto(root, newPerson.id, uri) }
                    }
                    people = people + newPerson.copy(photoFileName = savedFileName, photoUri = photoUri)
                    dialogRole = null
                    persist()
                }
            }
        )
    }

    val personBeingEdited = editingPerson
    if (personBeingEdited != null) {
        SlcdPersonEditDialog(
            role = personBeingEdited.role,
            initialName = personBeingEdited.name,
            initialPhotoUri = personBeingEdited.photoUri,
            onDismiss = { editingPerson = null },
            onConfirm = { name, photoUri ->
                scope.launch {
                    var updated = personBeingEdited.copy(name = name)
                    if (photoUri != null && photoUri != personBeingEdited.photoUri) {
                        val savedFileName = withContext(Dispatchers.IO) {
                            repo.saveCreditPersonPhoto(root, personBeingEdited.id, photoUri)
                        }
                        updated = updated.copy(photoFileName = savedFileName, photoUri = photoUri)
                    }
                    people = people.map { if (it.id == updated.id) updated else it }
                    editingPerson = null
                    persist()
                }
            }
        )
    }

    val personToDelete = deleteTarget
    if (personToDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف ${personToDelete.name}؟") },
            text = { Text("سيُحذف هذا الفرد من فريق العمل وصورته الشخصية إن وُجدت.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteCreditPersonPhoto(root, personToDelete.id) }
                        people = people.filter { it.id != personToDelete.id }
                        deleteTarget = null
                        persist()
                    }
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }

    if (toast != null) {
        LaunchedEffect(toast) {
            kotlinx.coroutines.delay(1800)
            toast = null
        }
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SlcdCreditsGreen,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(toast!!, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/** صفّ فرد واحد من فريق العمل: صورة دائرية (أو أيقونة نائبة)، الاسم، تعديل، حذف. */
@Composable
private fun SlcdCreditPersonRow(
    person: SlcdCreditPerson,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SlcdCreditsGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (person.photoUri != null) {
                AsyncImage(
                    model = person.photoUri,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = SlcdCreditsGreen)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(person.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "تعديل")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "حذف")
        }
    }
}

/** حوار إضافة/تعديل فرد: اسم + اختيار صورة شخصية اختيارية عبر منتقي صور SAF. */
@Composable
private fun SlcdPersonEditDialog(
    role: SlcdCreditRole,
    initialName: String,
    initialPhotoUri: Uri?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, photoUri: Uri?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var pickedPhoto by remember { mutableStateOf(initialPhotoUri) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pickedPhoto = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isBlank()) "إضافة — ${role.displayName}" else "تعديل — ${role.displayName}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(SlcdCreditsGreen.copy(alpha = 0.12f))
                        .clickable { pickPhoto.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    if (pickedPhoto != null) {
                        AsyncImage(
                            model = pickedPhoto,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = "إضافة صورة", tint = SlcdCreditsGreen)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("الاسم") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), pickedPhoto) }
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
