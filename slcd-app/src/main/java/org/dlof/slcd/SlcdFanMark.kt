package org.dlof.slcd

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.sin

/**
 * ── هوية SLCD البصرية المشتركة ──────────────────────────────────────
 * نفس علامة "المروحة" الملوّنة المستخدمة في أيقونة التطبيق: ست شعاعات
 * دائرية الأطراف بستة ألوان مبهجة، تلتقي في مركز واحد. تُستخدم هذه
 * الملفات كمصدر وحيد للألوان والشعار حتى تبقى شاشة البداية ومؤشر
 * التحميل والأيقونة متطابقين بصرياً.
 */

/** لوحة ألوان "المروحة" — بنفس ترتيب الشعاعات في أيقونة التطبيق. */
internal val SlcdFanColors = listOf(
    Color(0xFFFBBF24), // أعلى        — كهرماني
    Color(0xFF10B981), // أعلى-يمين   — زمرّدي
    Color(0xFF0EA5A4), // أسفل-يمين   — تركواز
    Color(0xFF059669), // أسفل        — أخضر غامق
    Color(0xFFFB7185), // أسفل-يسار   — مرجاني
    Color(0xFF38BDF8), // أعلى-يسار   — أزرق سماوي
)

internal val SlcdInk = Color(0xFF0B0F12)
internal val SlcdBackground = Color(0xFFF7F8F7)
internal val SlcdGreen = Color(0xFF10B981)
internal val SlcdGold = Color(0xFFFBBF24)

/**
 * يرسم علامة "المروحة" بحجم [modifier] المُعطى.
 *
 * @param rotationDegrees زاوية دوران اختيارية (تُستخدم لتحريك العلامة أثناء
 * التحميل)؛ صفر يعني علامة ثابتة كما تظهر في شاشة البداية والأيقونة.
 */
@Composable
fun SlcdFanMark(modifier: Modifier = Modifier, rotationDegrees: Float = 0f) {
    Canvas(modifier = modifier.rotate(rotationDegrees)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.34f
        val barWidth = minOf(w, h) * 0.17f

        for (i in 0 until 6) {
            val angleDeg = -90f + i * 60f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val ex = cx + r * cos(angleRad).toFloat()
            val ey = cy + r * sin(angleRad).toFloat()
            drawLine(
                color = SlcdFanColors[i],
                start = Offset(cx, cy),
                end = Offset(ex, ey),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // بريق مركزي صغير يوحّد نقطة التقاء الشعاعات، بنفس روح الأيقونة.
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = barWidth * 0.28f,
            center = Offset(cx, cy)
        )
    }
}
