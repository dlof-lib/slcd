@file:OptIn(ExperimentalMaterial3Api::class)

package org.dlof.slcd

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val SlcdGreen = Color(0xFF1D7A3F)
private val SlcdGold = Color(0xFFD2A020)

/**
 * ══════════════════════════════════════════════════════════════════════════
 * شريط تقدّم الفصول الاحترافي داخل SLCD
 * ══════════════════════════════════════════════════════════════════════════
 *
 * يظهر أسفل شاشة تفاصيل الفصل، بنفس روح شريط تقدّم الحلقات في عارض
 * المستندات (مسار متدرّج، هالة توهّج، مؤشر قابل للسحب، فقاعة معاينة)
 * لكن بألوان SLCD (أخضر/ذهبي)، ويضيف:
 *  • ترقيماً "current/total" وشارة الفصل الحالي.
 *  • نقاطاً على طول المسار: ذهبية للفصول المقروءة، خضراء لما قبل الموضع
 *    الحالي، رمادية لما بعده — قفزة مباشرة لأي فصل بالنقر أو السحب.
 *  • زر "قفز سريع" يفتح ورقة سفلية بكل فصول الموسم لاختيار أي فصل مباشرة.
 *  • التنقّل بين الفصل السابق/التالي أصبح فقط عبر سحب الشريط نفسه (بلا
 *    صفّ أزرار منفصل)، بإيماءة سحب موحّدة تستجيب من أول لمسة مباشرة.
 */
@Composable
fun SlcdChapterNavigationBar(
    seasonNumber: Int,
    seasonTitle: String?,
    chapters: List<SlcdChapter>,
    currentChapterNumber: Int,
    onOpenChapter: (Int) -> Unit,
) {
    if (chapters.isEmpty()) return
    val ordered = remember(chapters) { chapters.sortedBy { it.chapterNumber } }
    val total = ordered.size
    val currentIndex = ordered.indexOfFirst { it.chapterNumber == currentChapterNumber }
        .let { if (it < 0) 0 else it }

    val haptics = LocalHapticFeedback.current
    var showJumpSheet by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        SlcdChapterProgressTrack(
            total = total,
            currentIndex = currentIndex,
            ordered = ordered,
            onJumpClick = { showJumpSheet = true },
            onSeekIndex = { idx -> ordered.getOrNull(idx)?.let { onOpenChapter(it.chapterNumber) } }
        )
    }

    if (showJumpSheet) {
        SlcdChapterJumpSheet(
            seasonNumber = seasonNumber,
            seasonTitle = seasonTitle,
            chapters = ordered,
            currentChapterNumber = currentChapterNumber,
            onSelect = { chapterNum ->
                showJumpSheet = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenChapter(chapterNum)
            },
            onDismiss = { showJumpSheet = false }
        )
    }
}

// ── جسم شريط التقدّم القابل للسحب: مسار متدرّج + هالة توهّج + نقاط الفصول ──
@Composable
private fun SlcdChapterProgressTrack(
    total: Int,
    currentIndex: Int,
    ordered: List<SlcdChapter>,
    onJumpClick: () -> Unit,
    onSeekIndex: (Int) -> Unit
) {
    val safeTotal = total.coerceAtLeast(1)
    val settledFraction = if (safeTotal > 1) currentIndex.toFloat() / (safeTotal - 1).toFloat() else 0f
    val trackColor = Color(0xFF2A2A2E)
    val haptics = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(settledFraction) }
    var lastHapticIndex by remember { mutableStateOf(currentIndex) }

    val displayedFraction = if (isDragging) dragFraction else settledFraction
    val animatedFraction by animateFloatAsState(
        targetValue = displayedFraction,
        animationSpec = if (isDragging) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.8f),
        label = "slcd_chapter_progress_fraction"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "slcd_chapter_thumb_scale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.6f else 0.28f,
        animationSpec = spring(),
        label = "slcd_chapter_glow_alpha"
    )

    fun fractionToIndex(fraction: Float): Int =
        Math.round(fraction.coerceIn(0f, 1f) * (safeTotal - 1))

    LaunchedEffect(dragFraction, isDragging) {
        if (isDragging) {
            val idx = fractionToIndex(dragFraction)
            if (idx != lastHapticIndex) {
                lastHapticIndex = idx
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isDragging) "اسحب للانتقال إلى فصل آخر" else "تقدّم الفصول",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDragging) SlcdGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onJumpClick, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Filled.FormatListBulleted,
                        contentDescription = "قفز سريع إلى فصل",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SlcdGold.copy(alpha = if (isDragging) 0.22f else 0.14f)
            ) {
                Text(
                    "${fractionToIndex(displayedFraction) + 1}/$safeTotal",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlcdGold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInputScrubBar(
                    total = safeTotal,
                    onStart = { fraction ->
                        isDragging = true
                        lastHapticIndex = fractionToIndex(settledFraction)
                        dragFraction = fraction
                    },
                    onDrag = { fraction -> dragFraction = fraction },
                    onEnd = {
                        isDragging = false
                        onSeekIndex(fractionToIndex(dragFraction))
                    },
                    onCancel = { isDragging = false }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            val trackWidth = maxWidth
            val thumbCenter = trackWidth * animatedFraction
            val trackHeight = if (isDragging) 7.dp else 5.dp

            // هالة توهّج خلف الجزء المكتمل
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(thumbCenter.coerceAtLeast(1.dp))
                    .height(18.dp)
                    .blur(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SlcdGold.copy(alpha = glowAlpha))
            )

            // المسار الكامل
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(trackColor)
            )

            // نقاط الفصول: ذهبي = مقروء، أخضر = قبل الموضع الحالي، رمادي = لاحق
            if (safeTotal in 2..40) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ordered.forEachIndexed { i, chapter ->
                        val passed = i <= fractionToIndex(animatedFraction)
                        val dotColor = when {
                            chapter.isRead -> SlcdGold
                            passed -> SlcdGreen.copy(alpha = 0.85f)
                            else -> Color.White.copy(alpha = 0.18f)
                        }
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }

            // الجزء المكتمل بتدرّج لوني أخضر → ذهبي
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(thumbCenter)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(SlcdGreen, SlcdGold)))
            )

            // المؤشر القابل للسحب
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbCenter - 12.dp)
                    .size(24.dp)
                    .scale(thumbScale)
                    .shadow(if (isDragging) 14.dp else 6.dp, CircleShape, ambientColor = SlcdGold, spotColor = SlcdGold)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, SlcdGold.copy(alpha = 0.3f), CircleShape)
                    .padding(3.5.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(SlcdGreen, SlcdGold)))
            )

            // فقاعة معاينة أثناء السحب
            if (isDragging) {
                val bubbleWidth = 96.dp
                val bubbleX = (thumbCenter - bubbleWidth / 2)
                    .coerceIn(0.dp, (trackWidth - bubbleWidth).coerceAtLeast(0.dp))
                val previewChapter = ordered.getOrNull(fractionToIndex(dragFraction))
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = bubbleX, y = (-46).dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1A1A1E),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        previewChapter?.title?.takeIf { it.isNotBlank() }
                            ?: "الفصل ${previewChapter?.chapterNumber ?: (fractionToIndex(dragFraction) + 1)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .width(bubbleWidth)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

// ── ورقة سفلية للقفز السريع إلى أي فصل في الموسم ──────────────────────────
@Composable
private fun SlcdChapterJumpSheet(
    seasonNumber: Int,
    seasonTitle: String?,
    chapters: List<SlcdChapter>,
    currentChapterNumber: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                seasonTitle?.takeIf { it.isNotBlank() } ?: "الموسم $seasonNumber — القفز إلى فصل",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumnJumpList(
                chapters = chapters,
                currentChapterNumber = currentChapterNumber,
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun LazyColumnJumpList(
    chapters: List<SlcdChapter>,
    currentChapterNumber: Int,
    onSelect: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 420.dp)
    ) {
        items(chapters, key = { it.chapterNumber }) { chapter ->
            val isCurrent = chapter.chapterNumber == currentChapterNumber
            Surface(
                onClick = { onSelect(chapter.chapterNumber) },
                color = if (isCurrent) SlcdGreen.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (chapter.isRead) SlcdGold.copy(alpha = 0.18f) else SlcdGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (chapter.isRead) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SlcdGold, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                "${chapter.chapterNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SlcdGreen
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            chapter.title?.takeIf { it.isNotBlank() } ?: "الفصل ${chapter.chapterNumber}",
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) SlcdGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${chapter.pageCount} صفحة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (chapter.isFavorite) {
                        Icon(Icons.Filled.Star, contentDescription = "مفضّل", tint = SlcdGold, modifier = Modifier.size(16.dp))
                    }
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.PlayCircle, contentDescription = "الفصل الحالي", tint = SlcdGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── إيماءة سحب/لمس موحّدة لشريط السحب: تستجيب فوراً عند أول لمسة (لا تنتظر
// حركة الإصبع كما تفعل detectHorizontalDragGestures الافتراضية بعتبة touch-slop)،
// فيبدأ المؤشر بالتحرك من لحظة اللمس الأولى نفسها ثم يتابع الإصبع بسلاسة —
// هذا ما يجعل السحب "احترافياً" بدل شعوره بالتأخّر أو التقطّع سابقاً حين
// كانت وظيفتا اللمس والسحب منفصلتين على نفس الـ Modifier وتتنازعان الحدث.
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
