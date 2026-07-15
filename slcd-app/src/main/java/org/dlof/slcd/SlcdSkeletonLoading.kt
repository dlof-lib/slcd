package org.dlof.slcd

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * ══════════════════════════════════════════════════════════════════════════
 * تحميل هيكلي (Skeleton Loading) بشكل شعار الثعبان — SLCD
 * ══════════════════════════════════════════════════════════════════════════
 *
 * نفس فلسفة [SlcdStructuralCache] («هيكل أولاً، محتوى لاحقاً») لكن على
 * مستوى الواجهة: بدل شاشة فارغة أو رسالة خطأ مؤقتة أثناء أول قراءة هيكلية
 * لمكتبة SLCD (`repo.loadLibrary`)، تُعرض بطاقات/صفوف "هيكلية" بنفس مقاس
 * العناصر الحقيقية، تحمل أيقونة الثعبان الرسمية (`ic_launcher_foreground`)
 * وسط شعاع لمعان (shimmer) متحرّك، مع سطرين نائبين (title/subtitle) —
 * فتختفي فوراً بمجرد وصول البيانات الحقيقية دون أي وميض أو قفزة تخطيط.
 */

private val SlcdSkeletonBase = Color(0xFF1B2320)
private val SlcdSkeletonHighlight = Color(0xFF34D399).copy(alpha = 0.22f)

/** شعاع لمعان قطري متحرّك يُستخدم كخلفية لكل عناصر التحميل الهيكلي هنا. */
@Composable
private fun slcdShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "slcd-skeleton-shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slcd-skeleton-shimmer-progress"
    )
    val start = Offset(progress * 600f - 300f, 0f)
    val end = Offset(start.x + 300f, 300f)
    return Brush.linearGradient(
        colors = listOf(SlcdSkeletonBase, SlcdSkeletonHighlight, SlcdSkeletonBase),
        start = start,
        end = end
    )
}

/** سطر نائب (شريط رمادي بعرض معيّن) يمثّل سطر عنوان أو نص أثناء التحميل الهيكلي. */
@Composable
private fun SlcdSkeletonBar(widthFraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(slcdShimmerBrush())
    )
}

/**
 * بطاقة هيكلية مربّعة — بنفس هيئة بطاقات الأغلفة/المواسم أثناء التحميل:
 * مربّع بزوايا دائرية بلمعان متحرّك، وشعار الثعبان في وسطه، وسطران نائبان
 * أسفله (عنوان قصير + عنوان فرعي أقصر)، تماماً بروح معاينة أيقونة SLCD.
 */
@Composable
fun SlcdIconSkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(slcdShimmerBrush()),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B0F12)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SlcdSkeletonBar(widthFraction = 0.75f)
        Spacer(Modifier.height(6.dp))
        SlcdSkeletonBar(widthFraction = 0.45f)
    }
}

/** صفّ هيكلي أفقي — بنفس هيئة صف موسم واحد ([SlcdSeasonRow]) أثناء التحميل. */
@Composable
fun SlcdRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF0B0F12)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f)),
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            SlcdSkeletonBar(widthFraction = 0.55f)
            Spacer(Modifier.height(8.dp))
            SlcdSkeletonBar(widthFraction = 0.3f)
        }
    }
}

/**
 * الشاشة الهيكلية الكاملة لمكتبة SLCD — تُعرض بدل [SlcdLibraryScreen] أثناء
 * أول تحميل هيكلي لجذر المكتبة (`repo.loadLibrary`) قبل وصول أي بيانات
 * فعلية: صفّ أغلفة أفقي (بطاقات مربّعة) ثم قائمة مواسم رأسية (صفوف)، بنفس
 * التخطيط والمقاسات الحقيقية تماماً لتفادي أي قفزة تخطيط عند اكتمال التحميل.
 */
@Composable
fun SlcdLibrarySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) {
                SlcdIconSkeletonCard(modifier = Modifier.width(84.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(5) {
                SlcdRowSkeleton()
            }
        }
    }
}
