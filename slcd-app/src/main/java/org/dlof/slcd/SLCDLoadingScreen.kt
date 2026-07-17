package org.dlof.slcd

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ── مؤشر/شاشة تحميل SLCD ────────────────────────────────────────────
 * تستخدم نفس علامة "المروحة" الملوّنة الموجودة في الأيقونة وشاشة
 * البداية ([SlcdFanMark])، لكن بدوران مستمر لطيف يوحي بأن هناك عملية
 * تحميل جارية (فتح مكتبة، فك تشفير ملف dlof، مزامنة… إلخ).
 *
 * يمكن استخدامها كشاشة كاملة تغطي الشاشة أثناء عملية طويلة:
 * ```
 * if (isLoading) SLCDLoadingScreen()
 * ```
 * أو كمؤشر صغير مضمّن داخل واجهة أخرى (زر، بطاقة، شريط علوي...):
 * ```
 * SLCDLoadingScreen(isFullScreen = false, modifier = Modifier.size(32.dp))
 * ```
 */
@Composable
fun SLCDLoadingScreen(
    modifier: Modifier = Modifier,
    label: String = "جارٍ التحميل…",
    isFullScreen: Boolean = true,
    markSize: Dp = if (isFullScreen) 96.dp else 40.dp
) {
    val transition = rememberInfiniteTransition(label = "slcd-loading-spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slcd-loading-spin-value"
    )

    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SlcdFanMark(
                modifier = Modifier.size(markSize),
                rotationDegrees = rotation
            )
            if (isFullScreen) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = SlcdInk.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (isFullScreen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SlcdBackground),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            content()
        }
    }
}
