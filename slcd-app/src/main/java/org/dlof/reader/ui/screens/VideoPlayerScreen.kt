package org.dlof.reader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════
//  VideoPlayerScreen — مشغل فيديو احترافي كامل الميزات
//  يُعرض كـ Dialog ملء الشاشة. يدعم تشغيل فيديو واحد أو قائمة تشغيل
//  كاملة (بالانتقال التلقائي بين الحلقات وأزرار التالي/السابق).
// ══════════════════════════════════════════════════════════════════════

@Composable
fun VideoPlayerDialog(
    uri: String,
    title: String = "",
    caption: String? = null,
    playlistItems: List<org.dlof.reader.model.PlaylistItem>? = null,
    startIndex: Int = 0,
    loopPlaylist: Boolean = false,
    onIndexChanged: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        VideoPlayerContent(
            uri = uri,
            title = title,
            caption = caption,
            playlistItems = playlistItems,
            startIndex = startIndex,
            loopPlaylist = loopPlaylist,
            onIndexChanged = onIndexChanged,
            onDismiss = onDismiss
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerContent(
    uri: String,
    title: String,
    caption: String?,
    playlistItems: List<org.dlof.reader.model.PlaylistItem>? = null,
    startIndex: Int = 0,
    loopPlaylist: Boolean = false,
    onIndexChanged: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── قائمة العناصر القابلة للتشغيل فعلياً (فيديو + رابط غير فارغ) ──
    val playableItems = remember(playlistItems) {
        playlistItems?.filter { it.uri.isNotBlank() && it.mimeType.startsWith("video/") } ?: emptyList()
    }
    val hasPlaylist = playableItems.size > 1

    // ── حالة المشغل ───────────────────────────────────────────────
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var seekFeedback by remember { mutableStateOf<String?>(null) } // "+10 ث"
    var currentQueueIndex by remember { mutableIntStateOf(startIndex.coerceIn(0, (playableItems.size - 1).coerceAtLeast(0))) }

    // العنوان/الوصف المعروضان: يتحدّثان تلقائياً مع تقدّم قائمة التشغيل
    val displayTitle = if (hasPlaylist) playableItems.getOrNull(currentQueueIndex)?.title?.ifBlank { title } ?: title else title
    val displayCaption = caption

    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    // ── ExoPlayer ─────────────────────────────────────────────────
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            if (hasPlaylist) {
                val mediaItems = playableItems.map { MediaItem.fromUri(Uri.parse(it.uri)) }
                setMediaItems(mediaItems, currentQueueIndex.coerceIn(0, mediaItems.lastIndex), 0L)
                repeatMode = if (loopPlaylist) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            } else {
                setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            }
            prepare()
            playWhenReady = true
        }
    }

    // تحديث الحالة من المشغل
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (hasPlaylist) {
                    currentQueueIndex = exoPlayer.currentMediaItemIndex
                    onIndexChanged?.invoke(currentQueueIndex)
                    duration = 0L
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0)
                }
                if (state == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0, 0L)
                    exoPlayer.pause()
                    showControls = true
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val cause = error.cause?.message ?: error.message ?: "Source error"
                playerError = when {
                    cause.contains("Permission") || cause.contains("permission") || cause.contains("403") ->
                        "لا توجد صلاحية للوصول إلى الفيديو — أعِد إضافته من المحرر باستخدام زر الاختيار."
                    cause.contains("SOURCE") || cause.contains("source") || cause.contains("FileNotFound") ->
                        "تعذّر العثور على ملف الفيديو — قد يكون قد حُذف أو نُقل."
                    cause.contains("http") || cause.contains("network") || cause.contains("Network") ->
                        "خطأ في الشبكة — تحقق من الاتصال بالإنترنت."
                    else -> "تعذّر تشغيل الفيديو: $cause"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // تحديث الموضع كل 500 مللي ثانية
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0)
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0)
            if (duration <= 0) duration = exoPlayer.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    // إخفاء عناصر التحكم تلقائياً بعد 3 ثوانٍ
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // ── واجهة المشغل ──────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2) {
                            // نقر مزدوج يسار → رجوع 10 ثوانٍ
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                            seekFeedback = "⏪ 10 ث"
                        } else {
                            // نقر مزدوج يمين → تقدم 10 ثوانٍ
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(duration))
                            seekFeedback = "⏩ 10 ث"
                        }
                        scope.launch {
                            delay(800)
                            seekFeedback = null
                        }
                    }
                )
            }
    ) {
        // ── سطح العرض (ExoPlayer) ──────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false  // نستخدم عناصر تحكم Compose
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── تعليق Seek ────────────────────────────────────────────
        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    seekFeedback ?: "",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // ── مؤشر التحميل ──────────────────────────────────────────
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color(0xFF6C63FF),
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
            )
        }

        // ── رسالة الخطأ ───────────────────────────────────────────
        playerError?.let { err ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    err,
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                // زر الفتح الخارجي كحل بديل
                OutlinedButton(
                    onClick = {
                        val parsedUri = Uri.parse(uri)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, parsedUri).apply {
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B6B))
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("فتح بتطبيق خارجي", fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))) {
                    Text("إغلاق", fontSize = 13.sp)
                }
            }
        }

        // ── طبقة عناصر التحكم ─────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // ── شريط العنوان العلوي ────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "إغلاق",
                            tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (displayTitle.isNotBlank()) {
                            Text(
                                displayTitle,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (hasPlaylist) {
                                Text(
                                    "الحلقة ${currentQueueIndex + 1} من ${playableItems.size}",
                                    color = Color(0xFFBBB0FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!displayCaption.isNullOrBlank()) {
                                    Text("·", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                }
                            }
                            displayCaption?.let {
                                Text(it, color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // زر الصوت
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else volume
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = "الصوت", tint = Color.White
                        )
                    }
                }

                // ── أزرار التحكم المركزية ──────────────────────────
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // العنصر السابق في قائمة التشغيل (إن وُجدت)
                    if (hasPlaylist) {
                        PlayerControlButton(
                            onClick = { if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPrevious() },
                            icon = Icons.Filled.SkipPrevious,
                            size = 38
                        )
                    }

                    // رجوع 10 ث
                    PlayerControlButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                        },
                        icon = Icons.Filled.Replay10,
                        size = 44
                    )

                    // تشغيل/إيقاف مؤقت
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6C63FF))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(targetState = isPlaying, label = "play_pause") { playing ->
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // تقدم 10 ث
                    PlayerControlButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(duration))
                        },
                        icon = Icons.Filled.Forward10,
                        size = 44
                    )

                    // العنصر التالي في قائمة التشغيل (إن وُجدت)
                    if (hasPlaylist) {
                        PlayerControlButton(
                            onClick = { if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNext() },
                            icon = Icons.Filled.SkipNext,
                            size = 38
                        )
                    }
                }

                // ── شريط التقدم السفلي ────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // الوقت الحالي / الإجمالي
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDuration(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // زر سرعة التشغيل
                            Box {
                                TextButton(
                                    onClick = { showSpeedMenu = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "${if (playbackSpeed == 1f) "1" else playbackSpeed}x",
                                        color = Color(0xFFBBB0FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    speeds.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${if (speed == 1f) "1" else speed}x",
                                                    fontWeight = if (speed == playbackSpeed)
                                                        FontWeight.Bold else FontWeight.Normal,
                                                    color = if (speed == playbackSpeed)
                                                        MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                playbackSpeed = speed
                                                exoPlayer.setPlaybackSpeed(speed)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            Text(
                                formatDuration(duration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // شريط التقدم مع Buffer
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // طبقة Buffer (رمادية)
                        if (duration > 0) {
                            LinearProgressIndicator(
                                progress = { (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color.White.copy(alpha = 0.3f),
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                        // شريط التقدم الحقيقي
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { fraction ->
                                val seekTo = (fraction * duration).toLong()
                                exoPlayer.seekTo(seekTo)
                                currentPosition = seekTo
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6C63FF),
                                activeTrackColor = Color(0xFF6C63FF),
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 0.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── زر تحكم دائري صغير ────────────────────────────────────────────

@Composable
private fun PlayerControlButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: Int = 40
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.58f).dp)
        )
    }
}

// ── تنسيق الوقت ────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
