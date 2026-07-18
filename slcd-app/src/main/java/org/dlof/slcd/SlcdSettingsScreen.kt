@file:OptIn(ExperimentalMaterial3Api::class)

package org.dlof.slcd

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dlof.slcd.settings.SlcdSettings

private val SlcdSettingsGreen = Color(0xFF10B981)
private val SlcdSettingsGold = Color(0xFFFBBF24)

/**
 * ── شاشة الإعدادات ────────────────────────────────────────────────────
 *
 * إعدادات عادية (الثيم، إبقاء الشاشة مضاءة، تكبير النقر المزدوج، عدد
 * صفحات الجناح الافتراضي) مرئية دائماً، وقسم "إعدادات متقدّمة" **مخفي
 * بشكل مقصود** — يظهر فقط بعد لمس رقم إصدار التطبيق 7 مرات متتالية (نفس
 * تقليد "وضع المطوّر" في أندرويد نفسه)، تجنّباً لإرباك القارئ العادي
 * بخيارات ضبط أداء تقنية لا يحتاجها. القسم المتقدّم يتحكّم مباشرة
 * بمعاملات التحميل الهيكلي والتخزين المؤقت التي تُحسّن عرض SLCD Comics
 * على الأجهزة الضعيفة أو المكتبات الضخمة.
 */
@Composable
fun SlcdSettingsScreen(library: SlcdLibrary?, onBack: () -> Unit) {
    val context = LocalContext.current
    var versionTapCount by remember { mutableStateOf(0) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { SlcdSettingsSectionTitle("العرض والقراءة") }
            item {
                SlcdSettingsCard {
                    ThemeModeRow()
                    SlcdSettingsDivider()
                    SwitchSettingRow(
                        icon = Icons.Filled.ScreenLockPortrait,
                        title = "إبقاء الشاشة مضاءة أثناء القراءة",
                        subtitle = "يمنع قفل الشاشة تلقائياً في منتصف فصل طويل",
                        checked = SlcdSettings.keepScreenOnWhileReading,
                        onCheckedChange = { SlcdSettings.setKeepScreenOnWhileReading(context, it) }
                    )
                    SlcdSettingsDivider()
                    SliderSettingRow(
                        icon = Icons.Filled.PhoneAndroid,
                        title = "تكبير النقر المزدوج (أسلوب SLCD+)",
                        valueLabel = "×${"%.1f".format(SlcdSettings.doubleTapZoomFactor)}",
                        value = SlcdSettings.doubleTapZoomFactor,
                        range = 1.5f..3.0f,
                        steps = 2,
                        onValueChange = { SlcdSettings.setDoubleTapZoomFactor(context, it) }
                    )
                    SlcdSettingsDivider()
                    SliderSettingRow(
                        icon = Icons.Filled.ViewCarousel,
                        title = "عدد صفحات الجناح الافتراضي",
                        subtitle = "المدى الدرامي الموصى به لنظام الأجنحة — وحدة سردية متكاملة تنتهي بتشويق",
                        valueLabel = "${SlcdSettings.wingDefaultPageCount} صفحات",
                        value = SlcdSettings.wingDefaultPageCount.toFloat(),
                        range = 9f..10f,
                        steps = 0,
                        onValueChange = { SlcdSettings.setWingDefaultPageCount(context, it.toInt()) }
                    )
                }
            }

            if (library != null) {
                val allChapters = remember(library) { library.seasons.flatMap { it.chapters } }
                val chaptersWithWings = remember(allChapters) { allChapters.count { it.wings.isNotEmpty() } }
                val totalWings = remember(allChapters) { allChapters.sumOf { it.wings.size } }
                val totalCliffhangers = remember(allChapters) { allChapters.sumOf { c -> c.wings.count { it.isCliffhanger } } }
                if (totalWings > 0) {
                    item { SlcdSettingsSectionTitle("نظام الأجنحة — إحصائيات المكتبة") }
                    item {
                        SlcdSettingsCard {
                            SlcdStatRow(Icons.Filled.ViewCarousel, "فصول مقسّمة لأجنحة", "$chaptersWithWings من ${allChapters.size} فصل")
                            SlcdSettingsDivider()
                            SlcdStatRow(Icons.Filled.ViewCarousel, "إجمالي الأجنحة", "$totalWings جناح")
                            if (totalCliffhangers > 0) {
                                SlcdSettingsDivider()
                                SlcdStatRow(Icons.Filled.Bolt, "لحظات تشويق مفعّلة", "$totalCliffhangers", accent = Color(0xFFFB7185))
                            }
                        }
                    }
                }
            }

            item { SlcdSettingsSectionTitle("حول") }
            item {
                SlcdSettingsCard {
                    Surface(
                        onClick = {
                            versionTapCount++
                            when {
                                versionTapCount >= 7 && !SlcdSettings.developerModeEnabled -> {
                                    SlcdSettings.setDeveloperMode(context, true)
                                    scope.launch { snackbarHostState.showSnackbar("تم تفعيل وضع المطوّر — الإعدادات المتقدّمة الآن ظاهرة") }
                                }
                                versionTapCount in 3..6 -> {
                                    scope.launch { snackbarHostState.showSnackbar("باقي ${7 - versionTapCount} لمسات لتفعيل وضع المطوّر") }
                                }
                            }
                        },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 10.dp)) {
                            Text("SLCD Comics", fontWeight = FontWeight.Bold)
                            Text(
                                "إصدار التطبيق",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (SlcdSettings.developerModeEnabled) {
                item { SlcdSettingsSectionTitle("إعدادات متقدّمة (وضع المطوّر)") }
                item {
                    SlcdSettingsCard {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SlcdSettingsGold.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.BugReport, contentDescription = null, tint = SlcdSettingsGold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "إعدادات تقنية تتحكّم بأداء التحميل الهيكلي مباشرة. القيم الافتراضية مناسبة لمعظم الأجهزة — عدّلها فقط إن واجهت بطئاً أو استهلاك ذاكرة مرتفعاً.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        SliderSettingRow(
                            icon = Icons.Filled.Memory,
                            title = "سقف الذاكرة الهيكلية (فصول محفوظة)",
                            subtitle = "أكبر = تنقّل أسرع بين الفصول المُزارة سابقاً، لكن استهلاك ذاكرة أعلى",
                            valueLabel = "${SlcdSettings.structuralCacheLimit} فصل",
                            value = SlcdSettings.structuralCacheLimit.toFloat(),
                            range = 4f..64f,
                            steps = 11,
                            onValueChange = {
                                SlcdSettings.setStructuralCacheLimit(context, it.toInt())
                                SlcdStructuralCache.applyCacheLimit(it.toInt())
                            }
                        )
                        SlcdSettingsDivider()
                        SliderSettingRow(
                            icon = Icons.Filled.Tune,
                            title = "نافذة صور اللوحات المحمّلة",
                            subtitle = "عدد اللوحات المحمّلة كصور فعلية حول موضع القراءة الحالي (± القيمة)",
                            valueLabel = "±${SlcdSettings.pageBitmapWindow}",
                            value = SlcdSettings.pageBitmapWindow.toFloat(),
                            range = 1f..10f,
                            steps = 8,
                            onValueChange = { SlcdSettings.setPageBitmapWindow(context, it.toInt()) }
                        )
                        SlcdSettingsDivider()
                        Surface(
                            onClick = { showClearCacheConfirm = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = SlcdSettingsGreen)
                                Spacer(Modifier.width(10.dp))
                                Text("تفريغ الذاكرة الهيكلية الآن")
                            }
                        }
                        TextButton(onClick = { SlcdSettings.setDeveloperMode(context, false); versionTapCount = 0 }) {
                            Text("إخفاء الإعدادات المتقدّمة")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("تفريغ الذاكرة الهيكلية؟") },
            text = { Text("سيُعاد تحميل قوائم صفحات الفصول من التخزين عند فتحها التالي (لا يحذف أي ملفات، فقط ذاكرة مؤقتة).") },
            confirmButton = {
                TextButton(onClick = {
                    SlcdStructuralCache.clear()
                    showClearCacheConfirm = false
                    scope.launch { snackbarHostState.showSnackbar("تم تفريغ الذاكرة الهيكلية") }
                }) { Text("تفريغ") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun SlcdStatRow(icon: ImageVector, title: String, value: String, accent: Color = SlcdSettingsGreen) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent)
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SlcdSettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = SlcdSettingsGreen,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun SlcdSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(horizontal = 14.dp), content = content)
    }
}

@Composable
private fun SlcdSettingsDivider() {
    Divider(modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun ThemeModeRow() {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DarkMode, contentDescription = null, tint = SlcdSettingsGreen)
            Spacer(Modifier.width(10.dp))
            Text("مظهر التطبيق", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                Triple(SlcdSettings.ThemeMode.SYSTEM, "النظام", Icons.Filled.PhoneAndroid),
                Triple(SlcdSettings.ThemeMode.LIGHT, "فاتح", Icons.Filled.LightMode),
                Triple(SlcdSettings.ThemeMode.DARK, "غامق", Icons.Filled.DarkMode),
            )
            options.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    selected = SlcdSettings.themeMode == mode,
                    onClick = { SlcdSettings.setThemeMode(context, mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = SlcdSettingsGreen)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SlcdSettingsGreen)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(valueLabel, color = SlcdSettingsGold, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}
