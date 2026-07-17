package org.dlof.slcd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── هوية SLCD البصرية — مأخوذة من نفس لوحة ألوان "المروحة" في الأيقونة ────
// (انظر SlcdFanMark.kt): زمرّدي أساسي، تركواز ثانوي، كهرماني للتمييز،
// ومرجاني للتنبيهات/الأخطاء — بدل الأخضر/الذهبي الباهتين القديمين.
val SlcdSlimeGreen = Color(0xFF34D399)      // زمرّدي فاتح (حالات hover/تمييز خفيف)
val SlcdSlimeGreenDark = Color(0xFF10B981)  // زمرّدي أساسي (نفس لون الشعاع العلوي بالأيقونة)
val SlcdTealAccent = Color(0xFF0EA5A4)
val SlcdAmberAccent = Color(0xFFFBBF24)
val SlcdCoralAccent = Color(0xFFFB7185)
val SlcdSkyAccent = Color(0xFF38BDF8)
val SlcdInk = Color(0xFF0B0F12)
val SlcdSurfaceDark = Color(0xFF15191D)
val SlcdPlate = Color(0xFFF7F8F7)           // نفس لون لوحة الأيقونة الفاتحة

private val SlcdDarkColors = darkColorScheme(
    primary = SlcdSlimeGreen,
    onPrimary = Color(0xFF00201A),
    primaryContainer = Color(0xFF0B3B2C),
    onPrimaryContainer = SlcdSlimeGreen,
    secondary = SlcdTealAccent,
    onSecondary = Color(0xFF00201F),
    tertiary = SlcdAmberAccent,
    onTertiary = Color(0xFF2A1D00),
    error = SlcdCoralAccent,
    onError = Color(0xFF3A0A12),
    background = SlcdInk,
    onBackground = Color(0xFFEAEFEC),
    surface = SlcdSurfaceDark,
    onSurface = Color(0xFFEAEFEC),
)

private val SlcdLightColors = lightColorScheme(
    primary = SlcdSlimeGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF014737),
    secondary = SlcdTealAccent,
    onSecondary = Color.White,
    tertiary = SlcdAmberAccent,
    onTertiary = Color(0xFF2A1D00),
    error = SlcdCoralAccent,
    onError = Color.White,
    background = SlcdPlate,
    onBackground = SlcdInk,
    surface = Color.White,
    onSurface = SlcdInk,
)

/**
 * سمة تطبيق SLCD المستقل — مستقلة تماماً عن DlofTheme الخاص بتطبيق DLoF
 * الرئيسي، وموحّدة الآن مع نفس لوحة ألوان "المروحة" المستخدمة في أيقونة
 * التطبيق وشاشتَي البداية/التحميل (انظر SlcdFanMark.kt)، حتى تبدو الهوية
 * البصرية متّسقة من الأيقونة إلى داخل التطبيق. معظم شاشات SLCD (القارئ،
 * الرئيسية) تستخدم أصلاً ألوانها الغامقة الخاصة مباشرة لتجربة قراءة قصص
 * مصورة غامرة، وقد أُعيد توحيدها كذلك على نفس الزمرّدي/الكهرماني/التركواز؛
 * هذه السمة تبقى المصدر الأساسي لعناصر Material3 العامة (splash التثبيت،
 * الحوارات، الأزرار).
 */
@Composable
fun SlcdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) SlcdDarkColors else SlcdLightColors,
        content = content
    )
}
