package org.dlof.slcd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── هوية بصرية خاصة بـ SLCD (سلايم) — أخضر/نعناعي بدل نحاسي DLoF ──────────
val SlcdSlimeGreen = Color(0xFF34D399)
val SlcdSlimeGreenDark = Color(0xFF10B981)
val SlcdInk = Color(0xFF0B0F12)
val SlcdSurfaceDark = Color(0xFF15191D)

private val SlcdDarkColors = darkColorScheme(
    primary = SlcdSlimeGreen,
    onPrimary = Color(0xFF00201A),
    secondary = SlcdSlimeGreenDark,
    background = SlcdInk,
    surface = SlcdSurfaceDark,
)

private val SlcdLightColors = lightColorScheme(
    primary = SlcdSlimeGreenDark,
    onPrimary = Color.White,
    secondary = SlcdSlimeGreen,
)

/**
 * سمة تطبيق SLCD المستقل — مستقلة تماماً عن DlofTheme الخاص بتطبيق DLoF
 * الرئيسي. معظم شاشات SLCD (القارئ، الرئيسية) تستخدم أصلاً ألوانها الغامقة
 * الخاصة مباشرة (تجربة قراءة قصص مصورة غامرة)، لذا هذه السمة تُستخدم
 * أساساً لعناصر Material3 العامة (splash التثبيت، الحوارات، الأزرار).
 */
@Composable
fun SlcdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) SlcdDarkColors else SlcdLightColors,
        content = content
    )
}
