@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.dlof.slcd

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dlof.reader.model.AttachmentKind
import org.dlof.slcd.settings.SlcdSettings

/**
 * ══════════════════════════════════════════════════════════════════════════
 * شاشة قراءة القصص المصورة **SLCD+** — أسلوب القراءة الجديد بالتقليب
 * ══════════════════════════════════════════════════════════════════════════
 *
 * على عكس [SlcdComicReaderScreen] (تمرير رأسي متواصل بلا فواصل)، يعرض
 * SLCD+ **لوحة واحدة تملأ الشاشة في كل مرة**، بروح قارئ مانجا/كوميك
 * مخصّص:
 *
 *  • تقليب أفقي سلس بين اللوحات ([HorizontalPager])، مع حركة "دخول"
 *    خفيفة لكل لوحة (تكبير + تلاشي تدريجي بسيط) تمنحها إحساساً بالتركيز
 *    بدل الظهور المفاجئ — هذا هو الطابع البصري المميّز لـ SLCD+.
 *  • نقرة مزدوجة على اللوحة تكبّرها للتركيز على التفاصيل، ونقرة مزدوجة
 *    أخرى تعيدها لحجمها الطبيعي.
 *  • **نفس فلسفة التحميل الهيكلي على مرحلتين** الموروثة من القارئ
 *    الكلاسيكي (أبعاد فقط أولاً، ثم المحتوى الكامل)، مع نافذة ذاكرة
 *    محدودة وتفريغ فعلي (`recycle`) لما يخرج عنها — لكن ممتدة هنا عبر
 *    [SlcdStructuralCache] بحيث تُحمَّل مانفستات الفصل التالي/السابق
 *    هيكلياً (أسماء ملفات فقط، بلا صور) بمجرد الاقتراب من حافة الفصل
 *    الحالي، فيصبح الانتقال بين الفصول فورياً بلا وميض تحميل ملموس.
 *  • شريط نقاط سفلي مضغوط للفصول القصيرة، أو عدّاد رقمي للفصول الطويلة.
 *  • زر صغير في الشريط العلوي للعودة الفورية لأسلوب القراءة الكلاسيكي
 *    (Webtoon) — الاختيار يُحفظ في [SlcdSettings.readingStyle].
 */

private val SlcdGreen = Color(0xFF1D7A3F)
private val SlcdGold = Color(0xFFD2A020)
private val SlcdInk = Color(0xFF0D0D10)

private const val PLUS_CACHE_WINDOW = 2
private const val PLUS_DOT_STRIP_MAX = 24

@Composable
fun SlcdPlusReaderScreen(
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
    /** يُستدعى عند طلب المستخدم العودة لأسلوب القراءة الكلاسيكي (Webtoon). */
    onSwitchToWebtoon: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    // ── المرحلة ١ من التحميل الهيكلي: قائمة اللوحات، عبر ذاكرة هيكلية مشتركة ──
    var pages by remember(seasonNumber, chapterNumber) { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var listReady by remember(seasonNumber, chapterNumber) { mutableStateOf(false) }

    LaunchedEffect(seasonNumber, chapterNumber) {
        listReady = false
        pages = SlcdStructuralCache.ensurePages(repository, root, seasonNumber, chapterNumber)
        listReady = true
    }

    val pagerState = rememberPagerState(initialPage = 0) { pages.size.coerceAtLeast(1) }

    // ── تحميل هيكلي مسبق للفصل المجاور عند الاقتراب من حافة الفصل الحالي ──
    LaunchedEffect(pagerState, pages.size, seasonNumber, chapterNumber) {
        snapshotFlow { pagerState.currentPage }.collect { current ->
            if (pages.isEmpty()) return@collect
            if (hasNextChapter && current >= pages.size - 2) {
                SlcdStructuralCache.prefetchPages(repository, root, seasonNumber, chapterNumber + 1)
            }
            if (hasPreviousChapter && current <= 1) {
                SlcdStructuralCache.prefetchPages(repository, root, seasonNumber, chapterNumber - 1)
            }
        }
    }

    // ── المرحلة ٢: ذاكرة صور محدودة النافذة حول اللوحة الحالية ──
    val bitmapCache = remember(seasonNumber, chapterNumber) { mutableStateMapOf<Int, Bitmap>() }
    DisposableEffect(seasonNumber, chapterNumber) {
        onDispose {
            bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
            bitmapCache.clear()
        }
    }
    LaunchedEffect(pagerState, pages.size) {
        snapshotFlow { pagerState.currentPage }.collect { current ->
            val keep = (current - PLUS_CACHE_WINDOW)..(current + PLUS_CACHE_WINDOW)
            val toEvict = bitmapCache.keys.filter { it !in keep }
            toEvict.forEach { idx -> bitmapCache.remove(idx)?.let { if (!it.isRecycled) it.recycle() } }
        }
    }

    var chromeVisible by remember { mutableStateOf(true) }
    var hasMarkedRead by remember(seasonNumber, chapterNumber) { mutableStateOf(false) }

    LaunchedEffect(pagerState, pages.size) {
        snapshotFlow { pagerState.currentPage }.collect { current ->
            if (pages.isNotEmpty() && current == pages.size - 1 && !hasMarkedRead) {
                hasMarkedRead = true
                withContext(Dispatchers.IO) { repository.markChapterRead(root, seasonNumber, chapterNumber) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SlcdInk)) {
        when {
            !listReady -> ReaderLoadingStatePlus()
            pages.isEmpty() -> ReaderEmptyStatePlus(onBack = onBack)
            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { index -> pages.getOrNull(index)?.uri?.toString() ?: index }
                ) { index ->
                    ComicPlusPanel(
                        index = index,
                        isSettled = pagerState.settledPage == index,
                        pageFile = pages[index],
                        repository = repository,
                        cache = bitmapCache,
                        onTap = { chromeVisible = !chromeVisible }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PlusReaderTopBar(
                seasonTitle = seasonTitle,
                chapterTitle = chapterTitle,
                seasonNumber = seasonNumber,
                chapterNumber = chapterNumber,
                currentPage = pagerState.currentPage + 1,
                totalPages = pages.size,
                onBack = onBack,
                onSwitchToWebtoon = onSwitchToWebtoon
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlusReaderBottomBar(
                totalPages = pages.size,
                currentIndex = pagerState.currentPage,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                isLastPage = pages.isNotEmpty() && pagerState.currentPage == pages.size - 1,
                onSeek = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
                onBackToLibrary = onBack
            )
        }
    }
}

// ── لوحة SLCD+ واحدة: تحميل هيكلي على مرحلتين + حركة تركيز دخول + تكبير بالنقر المزدوج ──
@Composable
private fun ComicPlusPanel(
    index: Int,
    isSettled: Boolean,
    pageFile: DocumentFile,
    repository: SlimeComicsRepository,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Bitmap>,
    onTap: () -> Unit
) {
    val uri = pageFile.uri
    var failed by remember(uri) { mutableStateOf(false) }
    var isZoomed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (cache[index] != null) return@LaunchedEffect
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
        // مرحلة أ: أبعاد فقط (رخيصة) — لا حاجة لحجزها هنا لأن اللوحة تملأ الشاشة
        // بالفعل بمقياس Fit، على عكس القارئ الكلاسيكي حيث تحدّد عرض العمود.
        withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
        // مرحلة ب: فكّ المحتوى كاملاً.
        val decoded = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
        if (decoded != null) cache[index] = decoded else failed = true
    }

    // حركة تركيز دخول: تكبير خفيف + تلاشي عند استقرار اللوحة كحالية فعلياً.
    val entrance by animateFloatAsState(
        targetValue = if (isSettled) 1f else 0.96f,
        animationSpec = tween(durationMillis = 260),
        label = "slcd_plus_entrance"
    )
    val zoomScale by animateFloatAsState(
        targetValue = if (isZoomed) 2.1f else 1f,
        animationSpec = spring(dampingRatio = 0.75f),
        label = "slcd_plus_zoom"
    )

    val bitmap = cache[index]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { isZoomed = !isZoomed }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = entrance * zoomScale
                        scaleX = s
                        scaleY = s
                        alpha = if (isSettled) 1f else 0.85f
                    }
            )
            failed -> Text("تعذّر تحميل هذه اللوحة", color = Color.White.copy(alpha = 0.6f))
            else -> CircularProgressIndicator(color = SlcdGold, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun ReaderLoadingStatePlus() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SlcdGold)
    }
}

@Composable
private fun ReaderEmptyStatePlus(onBack: () -> Unit) {
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
private fun PlusReaderTopBar(
    seasonTitle: String?,
    chapterTitle: String?,
    seasonNumber: Int,
    chapterNumber: Int,
    currentPage: Int,
    totalPages: Int,
    onBack: () -> Unit,
    onSwitchToWebtoon: () -> Unit
) {
    Surface(color = SlcdInk.copy(alpha = 0.92f), shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onSwitchToWebtoon
                    )
            ) {
                Text(
                    "ويبتون",
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
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
private fun PlusReaderBottomBar(
    totalPages: Int,
    currentIndex: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    isLastPage: Boolean,
    onSeek: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onBackToLibrary: () -> Unit
) {
    Surface(color = SlcdInk.copy(alpha = 0.94f), shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (isLastPage && hasNextChapter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SlcdGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("أنهيت الفصل — تابع للفصل التالي", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
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

                if (totalPages in 1..PLUS_DOT_STRIP_MAX) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (i in 0 until totalPages) {
                            val active = i == currentIndex
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.5.dp)
                                    .size(if (active) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (active) SlcdGold else Color.White.copy(alpha = 0.25f))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { onSeek(i) }
                            )
                        }
                    }
                } else {
                    Text(
                        "لوحة ${currentIndex + 1} من $totalPages",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = onNextChapter, enabled = hasNextChapter, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "الفصل التالي",
                        tint = if (hasNextChapter) Color.White else Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}
