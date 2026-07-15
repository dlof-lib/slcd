@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.dlof.slcd

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.dlof.slcd.settings.SlcdSettings

private val SlcdGreen = Color(0xFF1D7A3F)
private val SlcdGold = Color(0xFFD2A020)

private sealed class SlcdRoute {
    data object Library : SlcdRoute()
    data class SeasonDetail(val seasonNumber: Int) : SlcdRoute()
    data class ChapterDetail(val seasonNumber: Int, val chapterNumber: Int) : SlcdRoute()
    /** شاشة القراءة الجديدة المخصّصة (تمرير رأسي + شريط تقدّم قابل للسحب) — تحلّ محل onOpenDlof لصفحات SLCD. */
    data class Reader(val seasonNumber: Int, val chapterNumber: Int) : SlcdRoute()
    /** شاشة فريق العمل والنشر — معلومات اعتماد اختيارية على مستوى المكتبة كاملة. */
    data object Credits : SlcdRoute()
}

/**
 * ── الشاشة الرئيسية لمكتبة SLCD (بعد التثبيت) ──────────────────────────
 *
 * تصفّح: الأغلفة (covers/coverN) أعلى الشاشة كصف أفقي، ثم المواسم
 * (seasons/seasonN) كبطاقات، وعند فتح موسم تظهر فصوله (chapters/chapterM)
 * مع عدد صفحاته وحالة تعبئته كحزمة `.slcdpkg` حصرية بـ SLCD. تدعم كذلك: فتح
 * الفصل للقراءة مباشرة، حذف/إعادة تسمية الأغلفة والمواسم والفصول، وإعادة
 * ترتيبها بسحب مقبض السحب (أيقونة ⋮⋮ الصغيرة).
 *
 * [onOpenDlof] يفتح ملف `.dlof` في عارض DLoF Reader الرئيسي (نفس التدفّق
 * المستخدم من بقية شاشات التطبيق)، ليُستخدم عند "فتح فصل للقراءة".
 */
@Composable
fun SLCDHomeScreen(onBack: () -> Unit, onOpenDlof: (Uri) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SlimeComicsRepository(context) }
    val scope = rememberCoroutineScope()

    val rootUriString = SlcdSettings.slcdRootUri
    var route by remember { mutableStateOf<SlcdRoute>(SlcdRoute.Library) }
    var library by remember { mutableStateOf<SlcdLibrary?>(null) }
    var isLoadingLibrary by remember { mutableStateOf(true) }
    var reloadTick by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rootUriString, reloadTick) {
        isLoadingLibrary = true
        val rootUri = rootUriString?.let { Uri.parse(it) }
        library = rootUri?.let { repo.loadLibrary(it) }
        isLoadingLibrary = false
    }

    if (rootUriString == null || library == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SLCD") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { padding ->
            if (isLoadingLibrary) {
                // تحميل هيكلي أولي لجذر المكتبة — عناصر نائبة بشكل شعار SLCD
                // بدل شاشة فارغة، بنفس تخطيط ومقاسات الشاشة الحقيقية.
                SlcdLibrarySkeleton(modifier = Modifier.padding(padding).fillMaxSize())
            } else {
                // احتياطي: لا يجب الوصول هنا فعلياً لأن SLCDInstallScreen تُعرض أولاً،
                // لكن نتعامل بلطف مع أي حالة غير متوقعة (مثل إلغاء صلاحية المجلد يدوياً).
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "تعذّر الوصول لمكتبة SLCD. جرّب إعادة التثبيت من الإعدادات.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val lib = library!!

    when (val current = route) {
        is SlcdRoute.Library -> SlcdLibraryScreen(
            library = lib,
            onBack = onBack,
            onOpenSeason = { route = SlcdRoute.SeasonDetail(it) },
            continueReading = SlcdSettings.slcdLastSeason?.let { s ->
                SlcdSettings.slcdLastChapter?.let { c ->
                    lib.seasons.firstOrNull { it.number == s }?.chapters?.firstOrNull { it.chapterNumber == c }
                }
            },
            onContinueReading = { chapter ->
                if (chapter.pageCount > 0) {
                    SlcdSettings.markSlcdLastRead(context, chapter.seasonNumber, chapter.chapterNumber)
                    route = SlcdRoute.Reader(chapter.seasonNumber, chapter.chapterNumber)
                } else {
                    toast = "لا توجد صفحات في هذا الفصل بعد"
                }
            },
            onAddSeason = { number ->
                repo.createSeason(lib.root, number)
                reloadTick++
                toast = "تم إنشاء الموسم $number"
            },
            onAddCoverImage = { number, uri ->
                repo.createCover(lib.root, number, uri)
                reloadTick++
                toast = "تم حفظ الغلاف $number"
            },
            onRenameCover = { number, title ->
                repo.renameCover(lib.root, number, title)
                reloadTick++
            },
            onDeleteCover = { number ->
                repo.deleteCover(lib.root, number)
                reloadTick++
                toast = "تم حذف الغلاف $number"
            },
            onReorderCovers = { orderedNumbers ->
                repo.reorderCovers(lib.root, orderedNumbers)
                reloadTick++
            },
            onRenameSeason = { number, title ->
                repo.renameSeason(lib.root, number, title)
                reloadTick++
            },
            onDeleteSeason = { number ->
                repo.deleteSeason(lib.root, number)
                reloadTick++
                toast = "تم حذف الموسم $number"
            },
            onReorderSeasons = { orderedNumbers ->
                repo.reorderSeasons(lib.root, orderedNumbers)
                reloadTick++
            },
            onUninstall = {
                SlcdSettings.uninstallSlcd(context)
                onBack()
            },
            onOpenCredits = { route = SlcdRoute.Credits }
        )

        is SlcdRoute.Credits -> SlcdCreditsScreen(
            root = lib.root,
            onBack = { route = SlcdRoute.Library }
        )

        is SlcdRoute.SeasonDetail -> {
            val season = lib.seasons.firstOrNull { it.number == current.seasonNumber }
            SlcdSeasonScreen(
                seasonNumber = current.seasonNumber,
                seasonTitle = season?.title,
                seasonFolder = season?.folder,
                chapters = season?.chapters.orEmpty(),
                onBack = { route = SlcdRoute.Library },
                onOpenChapter = { chapterNum -> route = SlcdRoute.ChapterDetail(current.seasonNumber, chapterNum) },
                onReadChapter = { chapterNum ->
                    val chapter = season?.chapters?.firstOrNull { it.chapterNumber == chapterNum }
                    if ((chapter?.pageCount ?: 0) > 0) {
                        SlcdSettings.markSlcdLastRead(context, current.seasonNumber, chapterNum)
                        route = SlcdRoute.Reader(current.seasonNumber, chapterNum)
                    } else {
                        toast = "لا توجد صفحات في هذا الفصل بعد"
                    }
                },
                onToggleFavorite = { chapterNum ->
                    repo.toggleChapterFavorite(lib.root, current.seasonNumber, chapterNum)
                    reloadTick++
                },
                onToggleRead = { chapterNum ->
                    repo.toggleChapterRead(lib.root, current.seasonNumber, chapterNum)
                    reloadTick++
                },
                onImportPackage = { uri ->
                    val info = repo.peekExclusivePackage(uri)
                    if (info == null) {
                        toast = "الملف المختار ليس حزمة SLCD حصرية صالحة (.slcdpkg)"
                    } else {
                        val imported = repo.importExclusivePackage(uri, lib.root, current.seasonNumber)
                        if (imported != null) {
                            reloadTick++
                            toast = "تم استيراد \"${imported.title ?: "فصل ${imported.chapterNumber}"}\" (${imported.pageCount} صفحة)"
                        } else {
                            toast = "تعذّر استيراد الحزمة"
                        }
                    }
                },
                onAddChapter = { chapterNumber, title, pageUris ->
                    scope.launch {
                        try {
                            repo.addChapterPages(
                                root = lib.root,
                                seasonNumber = current.seasonNumber,
                                chapterNumber = chapterNumber,
                                chapterTitle = title,
                                pageImageUris = pageUris
                            )
                            reloadTick++
                            toast = "تم إنشاء الفصل $chapterNumber (${pageUris.size} صفحة)"
                        } catch (e: Exception) {
                            toast = e.message ?: "تعذّر إنشاء الفصل"
                        }
                    }
                },
                onRenameChapter = { chapterNumber, title ->
                    repo.renameChapter(lib.root, current.seasonNumber, chapterNumber, title)
                    reloadTick++
                },
                onDeleteChapter = { chapterNumber ->
                    repo.deleteChapter(lib.root, current.seasonNumber, chapterNumber)
                    reloadTick++
                    toast = "تم حذف الفصل $chapterNumber"
                },
                onReorderChapters = { orderedNumbers ->
                    repo.reorderChapters(lib.root, current.seasonNumber, orderedNumbers)
                    reloadTick++
                }
            )
        }

        is SlcdRoute.ChapterDetail -> {
            val season = lib.seasons.firstOrNull { it.number == current.seasonNumber }
            val chapter = season?.chapters?.firstOrNull { it.chapterNumber == current.chapterNumber }
            if (chapter == null) {
                route = SlcdRoute.SeasonDetail(current.seasonNumber)
            } else {
                SlcdChapterScreen(
                    chapter = chapter,
                    seasonNumber = current.seasonNumber,
                    seasonTitle = season?.title,
                    seasonChapters = season?.chapters.orEmpty(),
                    onBack = { route = SlcdRoute.SeasonDetail(current.seasonNumber) },
                    onOpenChapter = { chapterNum -> route = SlcdRoute.ChapterDetail(current.seasonNumber, chapterNum) },
                    onRead = {
                        if (chapter.pageCount > 0) {
                            SlcdSettings.markSlcdLastRead(context, chapter.seasonNumber, chapter.chapterNumber)
                            route = SlcdRoute.Reader(chapter.seasonNumber, chapter.chapterNumber)
                        } else {
                            toast = "لا توجد صفحات في هذا الفصل بعد"
                        }
                    },
                    onRename = { title ->
                        repo.renameChapter(lib.root, chapter.seasonNumber, chapter.chapterNumber, title)
                        reloadTick++
                    },
                    onToggleFavorite = {
                        repo.toggleChapterFavorite(lib.root, chapter.seasonNumber, chapter.chapterNumber)
                        reloadTick++
                    },
                    onPackage = { destinationUri ->
                        repo.packageChapterExclusive(
                            chapterFolder = chapter.folder,
                            seasonNumber = chapter.seasonNumber,
                            chapterNumber = chapter.chapterNumber,
                            title = chapter.title,
                            destinationUri = destinationUri
                        )
                        reloadTick++
                        toast = "تم تصدير ${chapter.suggestedPackageName} — حزمة SLCD حصرية، تُفتح داخل SLCD فقط"
                    }
                )
            }
        }

        is SlcdRoute.Reader -> {
            // قائمة مسطّحة لكل (موسم، فصل) عبر المكتبة كاملة، بترتيب العرض،
            // لحساب "الفصل التالي/السابق" حتى عبر حدود المواسم لا داخل موسم واحد فقط.
            val flatChapters = remember(lib) {
                lib.seasons.sortedBy { it.number }
                    .flatMap { s -> s.chapters.sortedBy { it.chapterNumber }.map { s.number to it.chapterNumber } }
            }
            val position = flatChapters.indexOf(current.seasonNumber to current.chapterNumber)
            val previousPair = flatChapters.getOrNull(position - 1)
            val nextPair = flatChapters.getOrNull(position + 1)
            val season = lib.seasons.firstOrNull { it.number == current.seasonNumber }
            val chapter = season?.chapters?.firstOrNull { it.chapterNumber == current.chapterNumber }

            SlcdReaderRouter(
                repository = repo,
                root = lib.root,
                seasonNumber = current.seasonNumber,
                chapterNumber = current.chapterNumber,
                seasonTitle = season?.title,
                chapterTitle = chapter?.title,
                hasPreviousChapter = previousPair != null,
                hasNextChapter = nextPair != null,
                onBack = {
                    reloadTick++
                    route = SlcdRoute.ChapterDetail(current.seasonNumber, current.chapterNumber)
                },
                onPreviousChapter = {
                    previousPair?.let { (s, c) ->
                        SlcdSettings.markSlcdLastRead(context, s, c)
                        route = SlcdRoute.Reader(s, c)
                    }
                },
                onNextChapter = {
                    nextPair?.let { (s, c) ->
                        SlcdSettings.markSlcdLastRead(context, s, c)
                        route = SlcdRoute.Reader(s, c)
                    }
                }
            )
        }
    }

    toast?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ───────────────────────── حوارات عامة: إعادة تسمية / تأكيد حذف ─────────────────────────

@Composable
private fun SlcdRenameDialog(
    title: String,
    currentValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("بلا عنوان مخصّص") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value); onDismiss() }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun SlcdConfirmDeleteDialog(
    itemLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف $itemLabel؟") },
        text = { Text("سيُحذف كل محتوى $itemLabel نهائياً من مجلد المكتبة. لا يمكن التراجع عن هذا الإجراء.") },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text("حذف", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// ───────────────────────── مكتبة (الجذر) ─────────────────────────

@Composable
private fun SlcdLibraryScreen(
    library: SlcdLibrary,
    onBack: () -> Unit,
    onOpenSeason: (Int) -> Unit,
    continueReading: SlcdChapter? = null,
    onContinueReading: (SlcdChapter) -> Unit = {},
    onAddSeason: (Int) -> Unit,
    onAddCoverImage: (Int, Uri) -> Unit,
    onRenameCover: (Int, String) -> Unit,
    onDeleteCover: (Int) -> Unit,
    onReorderCovers: (List<Int>) -> Unit,
    onRenameSeason: (Int, String) -> Unit,
    onDeleteSeason: (Int) -> Unit,
    onReorderSeasons: (List<Int>) -> Unit,
    onUninstall: () -> Unit,
    onOpenCredits: () -> Unit
) {
    var showAddSeasonDialog by remember { mutableStateOf(false) }
    var pendingCoverNumber by remember { mutableStateOf<Int?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var renameCoverTarget by remember { mutableStateOf<SlcdCover?>(null) }
    var deleteCoverTarget by remember { mutableStateOf<SlcdCover?>(null) }
    var renameSeasonTarget by remember { mutableStateOf<SlcdSeason?>(null) }
    var deleteSeasonTarget by remember { mutableStateOf<SlcdSeason?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val density = LocalDensity.current
    val coverSlotPx = with(density) { 84.dp.toPx() }
    val seasonSlotPx = with(density) { 88.dp.toPx() }

    var coverOrder by remember(library.covers) { mutableStateOf(library.covers) }
    var seasonOrder by remember(library.seasons) { mutableStateOf(library.seasons) }
    val coverDragState = rememberSlcdDragState()
    val seasonDragState = rememberSlcdDragState()

    val pickCoverImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val number = pendingCoverNumber
        if (uri != null && number != null) onAddCoverImage(number, uri)
        pendingCoverNumber = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Slime Comics dlof") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "خيارات")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("فريق العمل والنشر") },
                            onClick = { showMenu = false; onOpenCredits() }
                        )
                        DropdownMenuItem(
                            text = { Text("إلغاء تثبيت SLCD") },
                            onClick = { showMenu = false; onUninstall() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSeasonDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("موسم جديد") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (continueReading != null) {
                item {
                    Surface(
                        onClick = { onContinueReading(continueReading) },
                        shape = RoundedCornerShape(16.dp),
                        color = SlcdGreen.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = SlcdGreen, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("متابعة القراءة", fontWeight = FontWeight.Bold, color = SlcdGreen)
                                Text(
                                    continueReading.title?.takeIf { it.isNotBlank() }
                                        ?: "الموسم ${continueReading.seasonNumber} — الفصل ${continueReading.chapterNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text("بحث في المواسم والفصول...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("الأغلفة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(coverOrder, key = { _, cover -> cover.number }) { index, cover ->
                        SlcdCoverThumb(
                            cover = cover,
                            modifier = Modifier.animateItemPlacement().slcdDragReorder(
                                state = coverDragState,
                                axis = SlcdDragAxis.Horizontal,
                                index = index,
                                itemCount = coverOrder.size,
                                itemSizePx = coverSlotPx,
                                onMove = { from, to ->
                                    coverOrder = coverOrder.toMutableList().apply { add(to, removeAt(from)) }
                                },
                                onDragFinished = { onReorderCovers(coverOrder.map { it.number }) }
                            ),
                            onRename = { renameCoverTarget = cover },
                            onDelete = { deleteCoverTarget = cover }
                        )
                    }
                    item {
                        val nextCoverNumber = (coverOrder.maxOfOrNull { it.number } ?: 0) + 1
                        AddCoverButton(onClick = {
                            pendingCoverNumber = nextCoverNumber
                            pickCoverImage.launch(arrayOf("image/*"))
                        })
                    }
                }
            }

            item {
                Text("المواسم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            val query = searchQuery.trim()
            val filteredSeasons = if (query.isBlank()) {
                seasonOrder
            } else {
                seasonOrder.filter { season ->
                    (season.title?.contains(query, ignoreCase = true) == true) ||
                        "الموسم ${season.number}".contains(query, ignoreCase = true) ||
                        season.chapters.any { chapter ->
                            (chapter.title?.contains(query, ignoreCase = true) == true) ||
                                "الفصل ${chapter.chapterNumber}".contains(query, ignoreCase = true)
                        }
                }
            }

            if (filteredSeasons.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "لا توجد مواسم بعد — اضغط \"موسم جديد\" لإنشاء أول موسم."
                        else "لا توجد نتائج مطابقة لـ \"$query\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (query.isBlank()) {
            itemsIndexed(seasonOrder, key = { _, season -> season.number }) { index, season ->
                SlcdSeasonRow(
                    season = season,
                    onClick = { onOpenSeason(season.number) },
                    onRename = { renameSeasonTarget = season },
                    onDelete = { deleteSeasonTarget = season },
                    itemModifier = Modifier.animateItemPlacement(),
                    dragModifier = Modifier.slcdDragReorder(
                        state = seasonDragState,
                        axis = SlcdDragAxis.Vertical,
                        index = index,
                        itemCount = seasonOrder.size,
                        itemSizePx = seasonSlotPx,
                        onMove = { from, to ->
                            seasonOrder = seasonOrder.toMutableList().apply { add(to, removeAt(from)) }
                        },
                        onDragFinished = { onReorderSeasons(seasonOrder.map { it.number }) }
                    )
                )
            }
            } else {
                items(filteredSeasons, key = { season -> "search_${season.number}" }) { season ->
                    SlcdSeasonRow(
                        season = season,
                        onClick = { onOpenSeason(season.number) },
                        onRename = { renameSeasonTarget = season },
                        onDelete = { deleteSeasonTarget = season },
                        dragModifier = Modifier
                    )
                }
            }
        }
    }

    if (showAddSeasonDialog) {
        val nextNumber = (library.seasons.maxOfOrNull { it.number } ?: 0) + 1
        AlertDialog(
            onDismissRequest = { showAddSeasonDialog = false },
            title = { Text("إنشاء موسم جديد") },
            text = { Text("سيُنشأ الموسم رقم $nextNumber (seasons/season$nextNumber).") },
            confirmButton = {
                TextButton(onClick = { onAddSeason(nextNumber); showAddSeasonDialog = false }) {
                    Text("إنشاء")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSeasonDialog = false }) { Text("إلغاء") }
            }
        )
    }

    renameCoverTarget?.let { cover ->
        SlcdRenameDialog(
            title = "عنوان الغلاف ${cover.number}",
            currentValue = cover.title.orEmpty(),
            onConfirm = { onRenameCover(cover.number, it) },
            onDismiss = { renameCoverTarget = null }
        )
    }
    deleteCoverTarget?.let { cover ->
        SlcdConfirmDeleteDialog(
            itemLabel = "الغلاف ${cover.number}",
            onConfirm = { onDeleteCover(cover.number) },
            onDismiss = { deleteCoverTarget = null }
        )
    }
    renameSeasonTarget?.let { season ->
        SlcdRenameDialog(
            title = "عنوان الموسم ${season.number}",
            currentValue = season.title.orEmpty(),
            onConfirm = { onRenameSeason(season.number, it) },
            onDismiss = { renameSeasonTarget = null }
        )
    }
    deleteSeasonTarget?.let { season ->
        SlcdConfirmDeleteDialog(
            itemLabel = "الموسم ${season.number} (بكل فصوله)",
            onConfirm = { onDeleteSeason(season.number) },
            onDismiss = { deleteSeasonTarget = null }
        )
    }
}

@Composable
private fun SlcdSeasonRow(
    season: SlcdSeason,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
    itemModifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = itemModifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlcdGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = SlcdGreen)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    season.title?.takeIf { it.isNotBlank() } ?: "الموسم ${season.number}",
                    fontWeight = FontWeight.Bold
                )
                val readCount = season.chapters.count { it.isRead }
                Text(
                    if (season.chapters.isEmpty()) "لا فصول بعد"
                    else "${season.chapters.size} فصل · $readCount مقروء",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (season.chapters.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val progress = readCount.toFloat() / season.chapters.size.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(if (progress >= 1f) SlcdGold else SlcdGreen)
                        )
                    }
                }
            }
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "اسحب لإعادة الترتيب",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp).then(dragModifier)
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "خيارات الموسم")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("إعادة التسمية") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف الموسم") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SlcdCoverThumb(cover: SlcdCover, modifier: Modifier = Modifier, onRename: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box {
            Surface(
                onClick = { showMenu = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(72.dp)
            ) {
                if (cover.imageUri != null) {
                    AsyncImage(
                        model = cover.imageUri,
                        contentDescription = "غلاف ${cover.number}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("إعادة التسمية") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { showMenu = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("حذف الغلاف") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            cover.title?.takeIf { it.isNotBlank() } ?: "غلاف ${cover.number}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AddCoverButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = SlcdGreen.copy(alpha = 0.1f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة غلاف", tint = SlcdGreen)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("إضافة", style = MaterialTheme.typography.labelSmall)
    }
}

// ───────────────────────── موسم (فصوله) ─────────────────────────

private enum class SlcdChapterFilter { ALL, FAVORITES, UNREAD }

@Composable
private fun SlcdSeasonScreen(
    seasonNumber: Int,
    seasonTitle: String?,
    seasonFolder: androidx.documentfile.provider.DocumentFile?,
    chapters: List<SlcdChapter>,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onReadChapter: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit = {},
    onToggleRead: (Int) -> Unit = {},
    onImportPackage: (Uri) -> Unit = {},
    onAddChapter: (chapterNumber: Int, title: String, pages: List<Uri>) -> Unit,
    onRenameChapter: (Int, String) -> Unit,
    onDeleteChapter: (Int) -> Unit,
    onReorderChapters: (List<Int>) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SlimeComicsRepository(context) }

    // ── بانر الموسم وفيديو المقدّمة (ملفات حقيقية اختيارية من مجلد الموسم) ──
    val bannerImage = remember(seasonFolder) { seasonFolder?.let { repo.seasonBannerImage(it) } }
    val bannerVideo = remember(seasonFolder) { seasonFolder?.let { repo.seasonBannerVideo(it) } }
    val introVideo = remember(seasonFolder) { seasonFolder?.let { repo.seasonIntroVideo(it) } }
    var showIntro by remember(seasonFolder) {
        mutableStateOf(introVideo != null && seasonFolder != null && !repo.hasSeenSeasonIntro(seasonFolder))
    }
    var showBannerVideoDialog by remember { mutableStateOf(false) }

    if (showIntro && introVideo != null && seasonFolder != null) {
        org.dlof.reader.ui.screens.VideoPlayerDialog(
            uri = introVideo.uri.toString(),
            title = seasonTitle?.takeIf { it.isNotBlank() } ?: "الموسم $seasonNumber",
            caption = "فيديو مقدّمة الموسم",
            onDismiss = {
                repo.markSeasonIntroSeen(seasonFolder)
                showIntro = false
            }
        )
    }
    if (showBannerVideoDialog && bannerVideo != null) {
        org.dlof.reader.ui.screens.VideoPlayerDialog(
            uri = bannerVideo.uri.toString(),
            title = seasonTitle?.takeIf { it.isNotBlank() } ?: "الموسم $seasonNumber",
            onDismiss = { showBannerVideoDialog = false }
        )
    }

    var showAddChapter by remember { mutableStateOf(false) }
    var pendingTitle by remember { mutableStateOf("") }
    var pendingPages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var renameChapterTarget by remember { mutableStateOf<SlcdChapter?>(null) }
    var deleteChapterTarget by remember { mutableStateOf<SlcdChapter?>(null) }
    var filter by remember { mutableStateOf(SlcdChapterFilter.ALL) }

    var chapterOrder by remember(chapters) { mutableStateOf(chapters) }
    val dragState = rememberSlcdDragState()
    val density = LocalDensity.current
    val chapterSlotPx = with(density) { 88.dp.toPx() }

    val pickPagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) pendingPages = uris }

    val importPackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportPackage(uri) }

    val visibleChapters = when (filter) {
        SlcdChapterFilter.ALL -> chapterOrder
        SlcdChapterFilter.FAVORITES -> chapterOrder.filter { it.isFavorite }
        SlcdChapterFilter.UNREAD -> chapterOrder.filter { !it.isRead }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seasonTitle?.takeIf { it.isNotBlank() } ?: "الموسم $seasonNumber") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { importPackageLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Archive, contentDescription = "استيراد حزمة SLCD (.slcdpkg)")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddChapter = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("فصل جديد") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bannerVideo != null || bannerImage != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                if (bannerVideo != null) Modifier.clickable { showBannerVideoDialog = true }
                                else Modifier
                            )
                    ) {
                        if (bannerImage != null) {
                            AsyncImage(
                                model = bannerImage.uri,
                                contentDescription = "بانر الموسم",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SlcdGreen.copy(alpha = 0.18f))
                            )
                        }
                        if (bannerVideo != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.PlayCircle,
                                        contentDescription = "تشغيل بانر الفيديو",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter == SlcdChapterFilter.ALL,
                        onClick = { filter = SlcdChapterFilter.ALL },
                        label = { Text("الكل") }
                    )
                    FilterChip(
                        selected = filter == SlcdChapterFilter.FAVORITES,
                        onClick = { filter = SlcdChapterFilter.FAVORITES },
                        label = { Text("المفضّلة") }
                    )
                    FilterChip(
                        selected = filter == SlcdChapterFilter.UNREAD,
                        onClick = { filter = SlcdChapterFilter.UNREAD },
                        label = { Text("غير مقروء") }
                    )
                }
            }

            if (visibleChapters.isEmpty()) {
                item {
                    Text(
                        if (chapterOrder.isEmpty()) "لا توجد فصول في هذا الموسم بعد."
                        else "لا توجد فصول مطابقة لهذا الفلتر.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (filter == SlcdChapterFilter.ALL) {
                itemsIndexed(chapterOrder, key = { _, chapter -> chapter.chapterNumber }) { index, chapter ->
                    SlcdChapterRow(
                        chapter = chapter,
                        onClick = { onOpenChapter(chapter.chapterNumber) },
                        onRead = { onReadChapter(chapter.chapterNumber) },
                        onRename = { renameChapterTarget = chapter },
                        onDelete = { deleteChapterTarget = chapter },
                        onToggleFavorite = { onToggleFavorite(chapter.chapterNumber) },
                        onToggleRead = { onToggleRead(chapter.chapterNumber) },
                        itemModifier = Modifier.animateItemPlacement(),
                        dragModifier = Modifier.slcdDragReorder(
                            state = dragState,
                            axis = SlcdDragAxis.Vertical,
                            index = index,
                            itemCount = chapterOrder.size,
                            itemSizePx = chapterSlotPx,
                            onMove = { from, to ->
                                chapterOrder = chapterOrder.toMutableList().apply { add(to, removeAt(from)) }
                            },
                            onDragFinished = { onReorderChapters(chapterOrder.map { it.chapterNumber }) }
                        )
                    )
                }
            } else {
                items(visibleChapters, key = { chapter -> "filtered_${chapter.chapterNumber}" }) { chapter ->
                    SlcdChapterRow(
                        chapter = chapter,
                        onClick = { onOpenChapter(chapter.chapterNumber) },
                        onRead = { onReadChapter(chapter.chapterNumber) },
                        onRename = { renameChapterTarget = chapter },
                        onDelete = { deleteChapterTarget = chapter },
                        onToggleFavorite = { onToggleFavorite(chapter.chapterNumber) },
                        onToggleRead = { onToggleRead(chapter.chapterNumber) },
                        dragModifier = Modifier
                    )
                }
            }
        }
    }

    if (showAddChapter) {
        val nextNumber = (chapterOrder.maxOfOrNull { it.chapterNumber } ?: 0) + 1
        AlertDialog(
            onDismissRequest = { showAddChapter = false; pendingPages = emptyList(); pendingTitle = "" },
            title = { Text("فصل جديد — الفصل $nextNumber") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pendingTitle,
                        onValueChange = { pendingTitle = it },
                        label = { Text("عنوان الفصل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { pickPagesLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (pendingPages.isEmpty()) "اختر صور صفحات الفصل" else "${pendingPages.size} صورة مُختارة")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pendingPages.isNotEmpty(),
                    onClick = {
                        onAddChapter(
                            nextNumber,
                            pendingTitle.ifBlank { "الفصل $nextNumber" },
                            pendingPages
                        )
                        showAddChapter = false
                        pendingPages = emptyList()
                        pendingTitle = ""
                    }
                ) { Text("إنشاء") }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapter = false; pendingPages = emptyList(); pendingTitle = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

    renameChapterTarget?.let { chapter ->
        SlcdRenameDialog(
            title = "عنوان الفصل ${chapter.chapterNumber}",
            currentValue = chapter.title.orEmpty(),
            onConfirm = { onRenameChapter(chapter.chapterNumber, it) },
            onDismiss = { renameChapterTarget = null }
        )
    }
    deleteChapterTarget?.let { chapter ->
        SlcdConfirmDeleteDialog(
            itemLabel = "الفصل ${chapter.chapterNumber}",
            onConfirm = { onDeleteChapter(chapter.chapterNumber) },
            onDismiss = { deleteChapterTarget = null }
        )
    }
}

@Composable
private fun SlcdChapterRow(
    chapter: SlcdChapter,
    onClick: () -> Unit,
    onRead: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onToggleRead: () -> Unit = {},
    dragModifier: Modifier,
    itemModifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = itemModifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (chapter.isRead) SlcdGold.copy(alpha = 0.16f) else SlcdGreen.copy(alpha = 0.14f))
                    .border(
                        width = if (chapter.isRead) 0.dp else 1.5.dp,
                        color = SlcdGreen.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (chapter.isRead) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SlcdGold, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        "${chapter.chapterNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlcdGreen
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chapter.title?.takeIf { it.isNotBlank() } ?: "الفصل ${chapter.chapterNumber}",
                        fontWeight = FontWeight.Bold
                    )
                    if (chapter.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Star, contentDescription = "مفضّل", tint = SlcdGold, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    "${chapter.pageCount} صفحة" + if (chapter.alreadyPackaged) " · مُصدَّر كحزمة SLCD حصرية" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (chapter.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "تبديل المفضّلة",
                    tint = SlcdGold
                )
            }
            IconButton(onClick = onRead, enabled = chapter.pageCount > 0) {
                Icon(Icons.Filled.PlayCircle, contentDescription = "قراءة الفصل", tint = SlcdGreen)
            }
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "اسحب لإعادة الترتيب",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp).then(dragModifier)
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "خيارات الفصل")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("إعادة التسمية") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (chapter.isRead) "وضع علامة كغير مقروء" else "وضع علامة كمقروء") },
                        leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                        onClick = { showMenu = false; onToggleRead() }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف الفصل") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ───────────────────────── تفاصيل فصل + قراءة + تصدير .slcdpkg حصري ─────────────────────────

@Composable
private fun SlcdChapterScreen(
    chapter: SlcdChapter,
    seasonNumber: Int = chapter.seasonNumber,
    seasonTitle: String? = null,
    seasonChapters: List<SlcdChapter> = listOf(chapter),
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit = {},
    onRead: () -> Unit,
    onRename: (String) -> Unit,
    onToggleFavorite: () -> Unit = {},
    onPackage: (Uri) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val createPackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) onPackage(uri) }

    val orderedChapters = remember(seasonChapters) { seasonChapters.sortedBy { it.chapterNumber } }
    val positionInSeason = orderedChapters.indexOfFirst { it.chapterNumber == chapter.chapterNumber } + 1
    val totalInSeason = orderedChapters.size.coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        chapter.title?.takeIf { it.isNotBlank() }
                            ?: "الموسم ${chapter.seasonNumber} — الفصل ${chapter.chapterNumber}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (chapter.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "تبديل المفضّلة",
                            tint = SlcdGold
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "خيارات الفصل")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("إعادة التسمية") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { showMenu = false; showRenameDialog = true }
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (orderedChapters.size > 1) {
                SlcdChapterNavigationBar(
                    seasonNumber = seasonNumber,
                    seasonTitle = seasonTitle,
                    chapters = orderedChapters,
                    currentChapterNumber = chapter.chapterNumber,
                    onOpenChapter = onOpenChapter
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp)
        ) {
            // ── بطاقة تعريفية بصرية للفصل: أيقونة الحالة + رقمه ضمن الموسم ──
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (chapter.isRead) SlcdGold.copy(alpha = 0.15f) else SlcdGreen.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (chapter.isRead) Icons.Filled.CheckCircle else Icons.Filled.AutoStories,
                            contentDescription = null,
                            tint = if (chapter.isRead) SlcdGold else SlcdGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "فصل $positionInSeason من $totalInSeason في هذا الموسم",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (chapter.isRead) SlcdGold else SlcdGreen
                        )
                        Text(
                            if (chapter.isRead) "تمت قراءة هذا الفصل" else "لم تُقرأ بعد",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "${chapter.pageCount} صفحة في هذا الفصل.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "اسم الحزمة المقترح: ${chapter.suggestedPackageName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onRead,
                enabled = chapter.pageCount > 0,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("فتح الفصل للقراءة")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { createPackageLauncher.launch(chapter.suggestedPackageName) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("تصدير كحزمة SLCD حصرية (.slcdpkg)")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "هذه الحزمة لا تُفتح إلا داخل SLCD — لا يمكن قراءتها أو فكّها بأي عارض dlof أو أداة ضغط عامة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showRenameDialog) {
        SlcdRenameDialog(
            title = "عنوان الفصل ${chapter.chapterNumber}",
            currentValue = chapter.title.orEmpty(),
            onConfirm = onRename,
            onDismiss = { showRenameDialog = false }
        )
    }
}
