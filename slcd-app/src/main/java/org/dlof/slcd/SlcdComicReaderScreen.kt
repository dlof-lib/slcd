@file:OptIn(ExperimentalMaterial3Api::class)

package org.dlof.slcd

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dlof.reader.model.AttachmentKind
import org.dlof.slcd.settings.SlcdSettings

/**
 * ══════════════════════════════════════════════════════════════════════════
 * شاشة قراءة القصص المصورة SLCD (الجيل الجديد)
 * ══════════════════════════════════════════════════════════════════════════
 *
 * تحلّ محل عرض صفحات SLCD عبر شاشة المستند العامة [org.dlof.reader.ui.screens
 * .documents.DocumentViewScreen]، بتجربة قراءة كوميك/ويبتون مخصّصة:
 *
 *  • تمرير رأسي متواصل: كل لوحات الفصل في عمود واحد متتابع بلا فواصل صفحات.
 *  • **تحميل هيكلي**: قائمة اللوحات (أسماء الملفات فقط) تُحمَّل أولاً بسرعة
 *    من مجلد الفصل، ثم كل لوحة تُفتح وتُفكّ (base64 → Bitmap) بشكل كسول
 *    فقط عند اقترابها من منطقة العرض عبر [LazyColumn] — وذاكرة الـ Bitmaps
 *    محدودة بنافذة حول الموضع الحالي (± [PAGE_CACHE_WINDOW]) مع تفريغ فعلي
 *    (`recycle`) لما يخرج عنها، بدل تحميل الفصل كاملاً في الذاكرة دفعة واحدة.
 *  • **طول/حجم الصور**: كل لوحة تُعرض بعرض الشاشة الكامل وارتفاع يطابق
 *    نسبتها الحقيقية (لا تشويه). قبل فكّ محتوى اللوحة بالكامل، تُفكّ
 *    أبعادها فقط (`inJustDecodeBounds`) — عملية رخيصة جداً — فتُحجز
 *    مساحتها النائبة بنفس نسبة العرض/الارتفاع الحقيقية للوحة القادمة،
 *    فلا تحدث أي "قفزة" تخطيط فعلية عند اكتمال فكّ المحتوى الكامل.
 *  • شريط تقدّم صفحات قابل للسحب أسفل الشاشة (بنفس روح [SlcdChapterNavigationBar]
 *    ولونيّة SLCD أخضر/ذهبي) يبيّن اللوحة الحالية/الإجمالي، يمكن سحبه للقفز
 *    فوراً لأي لوحة، ويتزامن تلقائياً مع موضع التمرير أثناء القراءة العادية.
 *  • لمسة واحدة على اللوحة تُخفي/تُظهر الأشرطة العلوية والسفلية (وضع غامر).
 *  • عند الوصول للوحة الأخيرة يُعلَّم الفصل مقروءاً تلقائياً، وتظهر بطاقة
 *    ختامية بزر "الفصل التالي" إن وُجد.
 */

// SlcdGreen / SlcdGold مُعرَّفتان بالفعل (internal) في SlcdFanMark.kt — لا تُكرَّر هنا
private val SlcdReaderInk = Color(0xFF0D0D10) // أغمق قليلاً من SlcdInk المشتركة، خاص بخلفية القارئ

private const val PAGE_CACHE_WINDOW = 3

@Composable
fun SlcdComicReaderScreen(
    repository: SlimeComicsRepository,
    root: DocumentFile,
    seasonNumber: Int,
    chapterNumber: Int,
    seasonTitle: String?,
    chapterTitle: String?,
    hasNextChapter: Boolean = false,
    hasPreviousChapter: Boolean = false,
    onBack: () -> Unit,
    onNextChapter: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    /** إن كانت غير null، يظهر زر صغير في الشريط العلوي للتبديل الفوري إلى أسلوب SLCD+. */
    onSwitchStyle: (() -> Unit)? = null,
    /**
     * أجنحة هذا الفصل (نظام الأجنحة — راجع [SlcdWing])، فارغة لفصل عادي
     * بصفحات مباشرة بلا تقسيم درامي. عند توفّرها تُقرأ الأجنحة بالتتابع
     * داخل نفس شاشة القارئ (بلا إعادة تحميل هيكلي) وتظهر بطاقة تشويق
     * خاصة (Cliffhanger) بدل بطاقة "نهاية الفصل" العادية عند نهاية كل
     * جناح ما عدا الأخير.
     */
    wings: List<SlcdWing> = emptyList(),
    /** رقم الجناح الذي يجب فتحه أولاً؛ null = قراءة صفحات الفصل المباشرة (بلا أجنحة) أو أول جناح تلقائياً إن وُجدت أجنحة. */
    initialWingNumber: Int? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // إعداد متقدّم/مخفي: إبقاء الشاشة مضاءة أثناء القراءة (يمنع القفل
    // التلقائي في منتصف فصل طويل). يُعاد الوضع الطبيعي عند مغادرة القارئ.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = SlcdSettings.keepScreenOnWhileReading
        onDispose { view.keepScreenOn = false }
    }

    // ── الجناح النشط حالياً ضمن نظام الأجنحة (null = فصل بلا أجنحة) ──
    var activeWing by remember(seasonNumber, chapterNumber) {
        mutableStateOf(initialWingNumber ?: wings.firstOrNull()?.number)
    }
    val sortedWings = remember(wings) { wings.sortedBy { it.number } }
    val activeWingIndex = remember(sortedWings, activeWing) { sortedWings.indexOfFirst { it.number == activeWing } }
    val currentWing = sortedWings.getOrNull(activeWingIndex)
    val nextWing = sortedWings.getOrNull(activeWingIndex + 1)
    val isLastWingOrNoWings = sortedWings.isEmpty() || nextWing == null

    // ── المرحلة ١ من التحميل الهيكلي: قائمة اللوحات فقط (سريع وخفيف) ──
    var pages by remember(seasonNumber, chapterNumber, activeWing) { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var listReady by remember(seasonNumber, chapterNumber, activeWing) { mutableStateOf(false) }

    LaunchedEffect(seasonNumber, chapterNumber, activeWing) {
        listReady = false
        pages = SlcdStructuralCache.ensurePages(repository, root, seasonNumber, chapterNumber, activeWing)
        listReady = true
        // ربط مباشر مع نظام التقدّم في SlcdSettings: يُحدَّث "آخر جناح مقروء"
        // لهذا الفصل تحديداً (يظهر لاحقاً كبطاقة تقدّم في شاشة الموسم) وكذلك
        // "آخر قراءة عامة" لبطاقة متابعة القراءة في المكتبة، بحيث تُستأنف
        // القراءة عند نفس الجناح بالضبط لا بداية الفصل دائماً.
        if (activeWing != null) {
            SlcdSettings.recordWingRead(context, seasonNumber, chapterNumber, activeWing!!)
        }
        SlcdSettings.markSlcdLastRead(context, seasonNumber, chapterNumber, activeWing)
        // تحميل هيكلي مسبق (بلا بيتماب) للجناح التالي إن وُجد، وإلا لمانفست
        // الفصل التالي/السابق *ضمن نفس الموسم* — تخمين أفضل جهد لا يكلّف شيئاً
        // إن أخطأ؛ لا يحدث أي وميض تحميل عند الانتقال الفعلي حين يكون صحيحاً.
        if (nextWing != null) {
            SlcdStructuralCache.prefetchPages(repository, root, seasonNumber, chapterNumber, nextWing.number)
        } else {
            if (hasNextChapter) SlcdStructuralCache.prefetchPages(repository, root, seasonNumber, chapterNumber + 1)
        }
        if (hasPreviousChapter && sortedWings.isEmpty()) {
            SlcdStructuralCache.prefetchPages(repository, root, seasonNumber, chapterNumber - 1)
        }
    }

    // ── المرحلة ٢: ذاكرة صور محدودة النافذة — تُملأ عند itemsIndexed فقط ──
    val bitmapCache = remember(seasonNumber, chapterNumber, activeWing) { mutableStateMapOf<Int, Bitmap>() }
    DisposableEffect(seasonNumber, chapterNumber, activeWing) {
        onDispose {
            bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
            bitmapCache.clear()
        }
    }

    val listState = rememberLazyListState()
    var chromeVisible by remember { mutableStateOf(true) }
    var hasMarkedRead by remember(seasonNumber, chapterNumber) { mutableStateOf(false) }

    val currentIndex by remember {
        derivedStateOf { visibleCenterIndex(listState) }
    }

    // تفريغ اللوحات البعيدة عن نافذة العرض + تحرير ذاكرتها فعلياً.
    LaunchedEffect(currentIndex, pages.size) {
        if (pages.isEmpty()) return@LaunchedEffect
        val window = SlcdSettings.pageBitmapWindow
        val keep = (currentIndex - window)..(currentIndex + window)
        val toEvict = bitmapCache.keys.filter { it !in keep }
        toEvict.forEach { idx ->
            bitmapCache.remove(idx)?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    // وصلنا للوحة الأخيرة فعلياً؟ نعلّم الفصل مقروءاً تلقائياً مرة واحدة — فقط
    // عند آخر جناح (أو فصل بلا أجنحة إطلاقاً)، فلا يُعلَّم الفصل "مقروء" وهو
    // ما يزال في منتصف رحلته السردية عبر أجنحته.
    LaunchedEffect(listState, pages.size, activeWing) {
        snapshotFlowLastItemVisible(listState, pages.size).collect { reachedEnd ->
            if (reachedEnd && pages.isNotEmpty() && !hasMarkedRead && isLastWingOrNoWings) {
                hasMarkedRead = true
                withContext(Dispatchers.IO) {
                    repository.markChapterRead(root, seasonNumber, chapterNumber)
                }
            }
        }
    }

    fun seekToPage(index: Int) {
        val target = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        scope.launch { listState.scrollToItem(target) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlcdReaderInk)
    ) {
        when {
            !listReady -> ReaderLoadingState()
            pages.isEmpty() -> ReaderEmptyState(onBack = onBack)
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(pages, key = { _, f -> f.uri.toString() }) { index, pageFile ->
                        ComicPageItem(
                            index = index,
                            pageFile = pageFile,
                            repository = repository,
                            cache = bitmapCache,
                            onTap = { chromeVisible = !chromeVisible }
                        )
                    }
                    item(key = "end-of-chapter-$activeWing") {
                        if (currentWing != null && nextWing != null) {
                            // ── نهاية جناح غير أخير: بطاقة تشويق (Cliffhanger) ──
                            WingCliffhangerCard(
                                wing = currentWing,
                                nextWing = nextWing,
                                onNextWing = { activeWing = nextWing.number },
                                onBackToLibrary = onBack
                            )
                        } else {
                            EndOfChapterCard(
                                hasNextChapter = hasNextChapter,
                                onNextChapter = onNextChapter,
                                onBackToLibrary = onBack
                            )
                        }
                    }
                }
            }
        }

        // ── الشريط العلوي: عنوان الفصل/الموسم + رجوع، يختفي في الوضع الغامر ──
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                seasonTitle = seasonTitle,
                chapterTitle = if (currentWing != null) {
                    val wingLabel = currentWing.title?.let { "· $it" } ?: ""
                    "${chapterTitle ?: "الفصل $chapterNumber"} — الجناح ${currentWing.number} $wingLabel".trim()
                } else chapterTitle,
                seasonNumber = seasonNumber,
                chapterNumber = chapterNumber,
                currentPage = currentIndex + 1,
                totalPages = pages.size,
                onBack = onBack,
                onSwitchStyle = onSwitchStyle
            )
        }

        // ── الشريط السفلي: شريط تقدّم اللوحات القابل للسحب ──
        AnimatedVisibility(
            visible = chromeVisible && pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SlcdPageScrubber(
                totalPages = pages.size,
                currentIndex = currentIndex,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                onSeek = { idx ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    seekToPage(idx)
                },
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter
            )
        }
    }
}

// ── لوحة واحدة: تحميل كسول على مرحلتين — أبعاد فقط ثم محتوى كامل ─────────
// المرحلة أ (رخيصة جداً): فكّ رأس الصورة فقط (inJustDecodeBounds) لمعرفة
// نسبة العرض/الارتفاع الحقيقية للوحة خلال ميلي ثانية، فتُحجز مساحتها
// النائبة بهذه النسبة بالضبط. المرحلة ب: فكّ المحتوى كاملاً كالسابق.
// هذا يمنع "قفزة" التخطيط الفعلية التي كانت تحدث سابقاً عندما تختلف
// اللوحة المحمَّلة عن حجم نائب تخميني ثابت (480dp لكل اللوحات).
@Composable
private fun ComicPageItem(
    index: Int,
    pageFile: DocumentFile,
    repository: SlimeComicsRepository,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Bitmap>,
    onTap: () -> Unit
) {
    val uri = pageFile.uri
    var failed by remember(uri) { mutableStateOf(false) }
    var aspectRatio by remember(uri) { mutableStateOf<Float?>(null) } // عرض / ارتفاع الصورة الحقيقيَّين

    LaunchedEffect(uri) {
        if (cache[index] != null) {
            // الصورة موجودة أصلاً في الذاكرة (لم تُفرَّغ بعد) — استنتج نسبتها منها مباشرة.
            cache[index]?.let { bmp ->
                if (bmp.height > 0) aspectRatio = bmp.width.toFloat() / bmp.height.toFloat()
            }
            return@LaunchedEffect
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                val doc = repository.openPageDocument(uri).document
                val attachment = doc.attachments.firstOrNull { it.kind == AttachmentKind.IMAGE }
                    ?: doc.attachments.firstOrNull()
                attachment?.let { repository.decodePageAttachmentBytes(it) }
            }.getOrNull()
        }
        if (bytes == null) {
            failed = true
            return@LaunchedEffect
        }

        // مرحلة أ: أبعاد فقط، بلا تخصيص ذاكرة للبكسلات (رخيصة جداً).
        val bounds = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            opts
        }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            aspectRatio = bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
        }

        // مرحلة ب: فكّ المحتوى الكامل.
        val decoded = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
        if (decoded != null) {
            cache[index] = decoded
            if (decoded.height > 0) aspectRatio = decoded.width.toFloat() / decoded.height.toFloat()
        } else {
            failed = true
        }
    }

    val bitmap = cache[index]
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onTap
            )
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
            failed -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFF1A1A1E)),
                contentAlignment = Alignment.Center
            ) {
                Text("تعذّر تحميل هذه اللوحة", color = Color.White.copy(alpha = 0.6f))
            }
            aspectRatio != null -> Box(
                // مساحة نائبة بالنسبة الحقيقية للوحة (معروفة من مرحلة أ) — لا قفزة تخطيط لاحقاً.
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio!!)
                    .background(Color(0xFF16161A)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SlcdGold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            else -> Box(
                // ريثما تُعرف حتى الأبعاد (لحظات معدودة): نسبة كوميك افتراضية شائعة.
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .background(Color(0xFF16161A)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SlcdGold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun ReaderLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SlcdGold)
    }
}

@Composable
private fun ReaderEmptyState(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("لا توجد لوحات في هذا الفصل بعد", color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("رجوع", color = SlcdGold) }
    }
}

@Composable
private fun ReaderTopBar(
    seasonTitle: String?,
    chapterTitle: String?,
    seasonNumber: Int,
    chapterNumber: Int,
    currentPage: Int,
    totalPages: Int,
    onBack: () -> Unit,
    onSwitchStyle: (() -> Unit)? = null
) {
    Surface(color = SlcdReaderInk.copy(alpha = 0.92f), shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPaddingCompat()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chapterTitle?.takeIf { it.isNotBlank() } ?: "الفصل $chapterNumber",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    seasonTitle?.takeIf { it.isNotBlank() } ?: "الموسم $seasonNumber",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            if (onSwitchStyle != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onSwitchStyle
                        )
                ) {
                    Text(
                        "SLCD+",
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = SlcdGold.copy(alpha = 0.16f)) {
                Text(
                    "$currentPage/${totalPages.coerceAtLeast(1)}",
                    color = SlcdGold,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EndOfChapterCard(
    hasNextChapter: Boolean,
    onNextChapter: () -> Unit,
    onBackToLibrary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SlcdGold, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("أنهيت هذا الفصل", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (hasNextChapter) {
            Button(
                onClick = onNextChapter,
                colors = ButtonDefaults.buttonColors(containerColor = SlcdGreen)
            ) {
                Text("الفصل التالي")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ChevronLeft, contentDescription = null) // اتجاه RTL: "التالي" نحو اليسار
            }
            Spacer(Modifier.height(10.dp))
        }
        TextButton(onClick = onBackToLibrary) {
            Text("العودة للمكتبة", color = Color.White.copy(alpha = 0.7f))
        }
    }
}

/**
 * ── بطاقة ختام جناح غير أخير: لحظة تشويق (Cliffhanger) ─────────────────
 *
 * تظهر بدل [EndOfChapterCard] كلما وصل القارئ لنهاية جناح ضمن نظام الأجنحة
 * وله جناح تالٍ ضمن نفس الفصل. تبرز بصرياً (لون كهرماني/مرجاني بدل الأخضر
 * المعتاد) وتعرض ملاحظة التشويق إن كتبها المؤلف عبر
 * [SlimeComicsRepository.setWingCliffhanger]، مع زر مباشر للانتقال للجناح
 * التالي بلا خروج من شاشة القراءة (الانتقال محلي داخل [SlcdComicReaderScreen]).
 */
@Composable
private fun WingCliffhangerCard(
    wing: SlcdWing,
    nextWing: SlcdWing,
    onNextWing: () -> Unit,
    onBackToLibrary: () -> Unit
) {
    val accent = if (wing.isCliffhanger) Color(0xFFFB7185) else SlcdGold
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = accent.copy(alpha = 0.16f),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                if (wing.isCliffhanger) "لحظة تشويق ⚡" else "نهاية الجناح ${wing.number}",
                color = accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (!wing.cliffhangerNote.isNullOrBlank()) {
            Text(
                wing.cliffhangerNote,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            Text(
                "انتهى الجناح ${wing.number} — تابع الجناح ${nextWing.number} الآن",
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onNextWing,
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Text(nextWing.title?.let { "الجناح ${nextWing.number} · $it" } ?: "الجناح ${nextWing.number} التالي", color = Color.Black)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = Color.Black)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBackToLibrary) {
            Text("العودة للمكتبة", color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// شريط تقدّم اللوحات القابل للسحب — نفس لغة SlcdChapterProgressTrack بصرياً
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun SlcdPageScrubber(
    totalPages: Int,
    currentIndex: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onSeek: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    val safeTotal = totalPages.coerceAtLeast(1)
    val settledFraction = if (safeTotal > 1) currentIndex.toFloat() / (safeTotal - 1).toFloat() else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(settledFraction) }
    val displayedFraction = if (isDragging) dragFraction else settledFraction
    val animatedFraction by animateFloatAsState(
        targetValue = displayedFraction,
        animationSpec = if (isDragging) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.8f),
        label = "slcd_page_progress_fraction"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "slcd_page_thumb_scale"
    )

    fun fractionToIndex(fraction: Float): Int = Math.round(fraction.coerceIn(0f, 1f) * (safeTotal - 1))

    Surface(color = SlcdReaderInk.copy(alpha = 0.94f), shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPaddingCompat()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousChapter, enabled = hasPreviousChapter, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "الفصل السابق",
                        tint = if (hasPreviousChapter) Color.White else Color.White.copy(alpha = 0.25f)
                    )
                }
                Text(
                    if (isDragging) "اسحب للانتقال بين اللوحات" else "لوحة ${fractionToIndex(displayedFraction) + 1} من $safeTotal",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDragging) SlcdGold else Color.White.copy(alpha = 0.6f)
                )
                IconButton(onClick = onNextChapter, enabled = hasNextChapter, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "الفصل التالي",
                        tint = if (hasNextChapter) Color.White else Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInputScrubBar(
                        total = safeTotal,
                        onStart = { f -> isDragging = true; dragFraction = f },
                        onDrag = { f ->
                            dragFraction = f
                            onSeek(fractionToIndex(f))
                        },
                        onEnd = { isDragging = false },
                        onCancel = { isDragging = false }
                    )
            ) {
                val trackWidth = maxWidth
                val trackHeight = 6.dp
                val thumbCenter = trackWidth * animatedFraction

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF2A2A2E))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(thumbCenter)
                        .height(trackHeight)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(SlcdGreen, SlcdGold)))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = thumbCenter - 12.dp)
                        .size(24.dp)
                        .scale(thumbScale)
                        .shadow(if (isDragging) 12.dp else 4.dp, CircleShape, ambientColor = SlcdGold, spotColor = SlcdGold)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, SlcdGold.copy(alpha = 0.3f), CircleShape)
                )

                if (isDragging) {
                    val bubbleWidth = 96.dp
                    val bubbleX = (thumbCenter - bubbleWidth / 2).coerceIn(0.dp, (trackWidth - bubbleWidth).coerceAtLeast(0.dp))
                    Surface(
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = bubbleX, y = (-42).dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1A1A1E),
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            "لوحة ${fractionToIndex(dragFraction) + 1}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(bubbleWidth).padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── إيماءة سحب/لمس موحّدة: تستجيب من أول لمسة (بلا عتبة touch-slop) ──
private fun Modifier.pointerInputScrubBar(
    total: Int,
    onStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
): Modifier = this.then(
    Modifier.pointerInput(total) {
        val widthPx = size.width.toFloat()
        if (widthPx <= 0f) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            onStart((down.position.x / widthPx).coerceIn(0f, 1f))

            var pointerId = down.id
            var endedNormally = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (change.changedToUpIgnoreConsumed()) {
                    endedNormally = true
                    break
                }
                if (!change.pressed) break
                change.consume()
                onDrag((change.position.x / widthPx).coerceIn(0f, 1f))
                pointerId = change.id
            }
            if (endedNormally) onEnd() else onCancel()
        }
    }
)

// ── يحسب فهرس اللوحة "المركزية" حالياً بحسب أكبر مساحة ظاهرة من كل عنصر ──
private fun visibleCenterIndex(listState: LazyListState): Int {
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return 0
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { item ->
        val itemCenter = item.offset + item.size / 2
        kotlin.math.abs(itemCenter - viewportCenter)
    }?.index ?: 0
}

// ── تدفّق يصدر true حين تصبح آخر لوحة ظاهرة فعلياً على الشاشة ──
private fun snapshotFlowLastItemVisible(listState: LazyListState, totalPages: Int) =
    androidx.compose.runtime.snapshotFlow {
        totalPages > 0 && listState.layoutInfo.visibleItemsInfo.any { it.index == totalPages - 1 }
    }

// ── حشوة أشرطة النظام (الحالة/التنقّل) حتى لا تتداخل الأشرطة الغامرة معها ──
@Composable
private fun Modifier.statusBarsPaddingCompat(): Modifier = this.then(Modifier.statusBarsPadding())

@Composable
private fun Modifier.navigationBarsPaddingCompat(): Modifier = this.then(Modifier.navigationBarsPadding())
