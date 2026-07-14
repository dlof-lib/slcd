package org.dlof.slcd

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** الأخضر/الذهبي الأساسي في DLoF، بإضافة بنفسجي مائل للطين (slime) خاص بهوية SLCD. */
private val SlcdGreen = Color(0xFF1D7A3F)
private val SlcdSlimePurple = Color(0xFF7C4DFF)
private val SlcdGold = Color(0xFFD2A020)
private val SlcdInk = Color(0xFF0B0B14)
private val SlcdBackground = Color(0xFFEDEFF7)

/**
 * مسار فيديو السبلاش التشويقي داخل أصول التطبيق — اختياري تماماً. لا يوجد
 * أي ملف بهذا الاسم افتراضياً (لا يُمكن توليد فيديو تسويقي حقيقي هنا)؛
 * إن أضاف فريق DLoF ملف فيديو حقيقياً بهذا المسار بالضبط ضمن
 * `app/src/main/assets/` وأعاد بناء التطبيق، سيُشغَّل تلقائياً بدل شعار
 * "قطرة الطين" المتحرك. بدون الملف، يستمر السبلاش الحالي دون أي تغيير.
 */
private const val SPLASH_VIDEO_ASSET_PATH = "slcd_tool_pkg/promo/splash.mp4"

/**
 * ── شاشة البداية الاحترافية لأداة Slime Comics dlof (SLCD) ────────────
 *
 * تُعرض فور دخول المستخدم لأداة SLCD (من قائمة الأدوات) قبل شاشتَي
 * التثبيت/التصفح. تُفضّل فيديو سبلاش تشويقياً حقيقياً إن وُجد مرفقاً ضمن
 * أصول التطبيق ([SPLASH_VIDEO_ASSET_PATH])، وإلا (وهي الحال الافتراضية)
 * تعرض شعار "قطرة طين" مرسوماً عبر Canvas بحركة نبض ناعمة، بنفس روح
 * [org.dlof.reader.ui.screens.BrandedSplashScreen] الخاصة بـ DLoF نفسه.
 */
@Composable
fun SLCDSplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val hasSplashVideo = remember {
        runCatching { context.assets.open(SPLASH_VIDEO_ASSET_PATH).use { } }.isSuccess
    }

    if (hasSplashVideo) {
        SlcdSplashVideo(assetPath = SPLASH_VIDEO_ASSET_PATH, onFinished = onFinished)
    } else {
        SlcdSplashAnimated(onFinished = onFinished)
    }
}

/** يشغّل فيديو السبلاش الحقيقي المرفق ضمن الأصول حتى انتهائه، مع تخطٍّ بالنقر وحماية زمنية احتياطية. */
@OptIn(UnstableApi::class)
@Composable
private fun SlcdSplashVideo(assetPath: String, onFinished: () -> Unit) {
    val context = LocalContext.current
    var finished by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("asset:///$assetPath"))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finishOnce()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // فشل تشغيل الفيديو (ملف تالف مثلاً): لا نُبقي المستخدم عالقاً على شاشة سوداء.
                finishOnce()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // حماية احتياطية: لا يبقى المستخدم أكثر من 12 ثانية حتى لو تعطّل مستمع الحالة.
    LaunchedEffect(Unit) {
        delay(12_000)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { finishOnce() } // نقرة لتخطّي الفيديو مباشرة
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** السبلاش المتحرّك الافتراضي (شعار قطرة الطين) — دون أي فيديو مرفق. */
@Composable
private fun SlcdSplashAnimated(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.8f) }
    val subtitleAlpha = remember { Animatable(0f) }

    val pulse = rememberInfiniteTransition(label = "slcd-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slcd-pulse-scale"
    )

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(500, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, tween(600, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(350)
        subtitleAlpha.animateTo(1f, tween(450, easing = LinearOutSlowInEasing))
        delay(750)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SlcdBackground, SlcdBackground.copy(alpha = 0.92f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SlimeMark(
                modifier = Modifier
                    .size(120.dp)
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value * pulseScale)
            )

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(logoAlpha.value)
            ) {
                Text(
                    "SLCD",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = SlcdInk
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                "Slime Comics dlof",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SlcdGreen,
                modifier = Modifier.alpha(subtitleAlpha.value)
            )
            Text(
                "قصصك المصورة — بمواسم وفصول وأغلفة",
                style = MaterialTheme.typography.bodySmall,
                color = SlcdInk.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .alpha(subtitleAlpha.value)
            )
        }

        Text(
            "تطبيق مصغّر داخل DLoF",
            style = MaterialTheme.typography.labelSmall,
            color = SlcdGold,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .alpha(subtitleAlpha.value)
        )
    }
}

/** شعار "قطرة طين" (slime drop) مرسوم برمجياً — بلا أصل صورة خارجي. */
@Composable
private fun SlimeMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // قطرة الطين: دائرة سفلية أعرض + قمة مدببة ناعمة أعلاها.
        val dropPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            cubicTo(
                w * 0.86f, h * 0.34f,
                w * 0.92f, h * 0.62f,
                w * 0.5f, h * 0.96f
            )
            cubicTo(
                w * 0.08f, h * 0.62f,
                w * 0.14f, h * 0.34f,
                w * 0.5f, h * 0.06f
            )
            close()
        }

        drawPath(
            path = dropPath,
            brush = Brush.linearGradient(
                colors = listOf(SlcdGreen, SlcdSlimePurple),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        drawPath(
            path = dropPath,
            color = SlcdInk.copy(alpha = 0.12f),
            style = Stroke(width = w * 0.02f)
        )

        // عينان بسيطتان تمنحان الشعار طابعاً ودوداً مناسباً لقصص مصورة.
        val eyeRadius = w * 0.045f
        drawCircle(Color.White, radius = eyeRadius, center = Offset(w * 0.4f, h * 0.55f))
        drawCircle(Color.White, radius = eyeRadius, center = Offset(w * 0.6f, h * 0.55f))
        drawCircle(SlcdInk, radius = eyeRadius * 0.5f, center = Offset(w * 0.41f, h * 0.56f))
        drawCircle(SlcdInk, radius = eyeRadius * 0.5f, center = Offset(w * 0.61f, h * 0.56f))

        // بريق علوي صغير يمنح تأثير "لمعان" احترافي.
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = w * 0.05f,
            center = Offset(w * 0.36f, h * 0.28f)
        )
    }
}
