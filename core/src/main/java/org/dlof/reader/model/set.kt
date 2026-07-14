package org.dlof.reader.model

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ══════════════════════════════════════════════════════════════
 * set.kt — إعدادات المسلسل / المستند / القصة المصورة
 * ══════════════════════════════════════════════════════════════
 *
 * يُمثّل محتوى ملف [set.txt] الموجود في جذر مجلد السلسلة.
 * يُقرأ تلقائياً عند فتح أي ملف .dlof إن وُجد set.txt في نفس المجلد.
 *
 * يدعم أيضاً مجلد fonts/ داخل مجلد السلسلة:
 *   MySeries/
 *   ├── set.txt           ← الإعدادات
 *   ├── fonts/            ← خطوط مخصصة (.ttf / .otf / .woff / .woff2)
 *   │   ├── Cairo-Bold.ttf
 *   │   └── Amiri-Regular.ttf
 *   ├── characters.dlof   ← تعريف الشخصيات (yime.kt)
 *   ├── series-index.dlof
 *   ├── ep01.dlof
 *   └── media/
 */

// ── نموذج الإعدادات الرئيسي ─────────────────────────────────────────────────

data class SeriesSettings(

    // ── معرّف السلسلة ─────────────────────────────────────
    val seriesId: String = "",
    val seriesTitle: String = "",
    val domain: SeriesSettingsDomain = SeriesSettingsDomain.SERIES,
    val language: String = "ar",
    val rtl: Boolean = true,
    val author: String = "",
    val publisher: String = "",
    val year: Int = 0,
    val version: String = "1.0",

    // ── الخطوط ────────────────────────────────────────────
    val fonts: FontSettings = FontSettings(),

    // ── الألوان والمظهر ───────────────────────────────────
    val theme: ThemeSettings = ThemeSettings(),

    // ── العرض والتخطيط ───────────────────────────────────
    val display: DisplaySettings = DisplaySettings(),

    // ── ترقيم الحلقات ─────────────────────────────────────
    val episodes: EpisodeSettings = EpisodeSettings(),

    // ── القصة المصورة ──────────────────────────────────────
    val comic: ComicSettings = ComicSettings(),

    // ── تجربة القراءة ──────────────────────────────────────
    val reading: ReadingSettings = ReadingSettings(),

    // ── الصوت ──────────────────────────────────────────────
    val audio: AudioSettings = AudioSettings(),

    // ── الترجمة ────────────────────────────────────────────
    val subtitles: SubtitleSettings = SubtitleSettings(),

    // ── الشخصيات ───────────────────────────────────────────
    val characters: CharactersSettings = CharactersSettings(),

    // ── الترخيص وحقوق النشر ─────────────────────────────────
    val license: LicenseSettings = LicenseSettings(),

    // ── التصنيف العمري وتحذيرات المحتوى ────────────────────
    val contentRating: ContentRatingSettings = ContentRatingSettings(),

    // ── إمكانية الوصول (Accessibility) ─────────────────────
    val accessibility: AccessibilitySettings = AccessibilitySettings(),

    // ── روابط خارجية (موقع، مصدر، تواصل اجتماعي) ───────────
    val links: LinksSettings = LinksSettings(),

    // ── ملف خطوط مكتشفة في مجلد fonts/ ──────────────────
    val discoveredFonts: List<DiscoveredFont> = emptyList(),

    // ── تشفير حزمة dlofpkg (setting/pro/) ──────────────────
    val crypto: PackageCryptoSettings = PackageCryptoSettings(),

    // ── مسارات مكوّنات حزمة dlofpkg (setting/ و media/) ─────
    val paths: PackagePathsSettings = PackagePathsSettings(),

    // ── بيانات وصفية ──────────────────────────────────────
    val createdAt: String = "",
    val updatedAt: String = "",
    val notes: String = ""
)

// ── تشفير حزمة dlofpkg ────────────────────────────────────────────────────────

/**
 * حالة تشفير حزمة `dlofpkg` كما يُقرأ من set.txt (crypto.*). عند [enabled] يُتوقَّع
 * وجود setting/pro/Best64.xml (معايير AES-256-GCM) و setting/pro/WQ.JSON
 * (مساعد الاستيراد السريع لنفس المعايير) داخل الحزمة. Base64 يبقى اختيارياً
 * دائماً (خيار إضافي) وليس الاعتماد الافتراضي لتخزين المرفقات.
 */
data class PackageCryptoSettings(
    val enabled: Boolean = false,
    val best64Path: String = "setting/pro/Best64.xml",
    val wqPath: String = "setting/pro/WQ.JSON"
)

// ── مسارات مكوّنات الحزمة ─────────────────────────────────────────────────────

/**
 * مسارات نسبية (داخل جذر حزمة `dlofpkg`) للملفات والمجلدات الاختيارية التي
 * يستدعيها set.txt: قالب التصميم، ملفات setting/ الإضافية، ومسارات الوسائط
 * (بما فيها دعم chapter/ للقصص المصوّرة و Episodes/ للمسلسلات داخل media/).
 */
data class PackagePathsSettings(
    val templatePath: String = "setting/dlotemplate.xml",
    val mapFile: String = "setting/map.dlof",
    val docFile: String = "setting/Documentation.dlof",
    val licenseFile: String = "setting/license.dlof",
    val imagePath: String = "media/image",
    val videoPath: String = "media/video",
    val fontsPath: String = "media/fonts"
)

enum class SeriesSettingsDomain(val key: String, val arabicLabel: String) {
    SERIES("series",     "مسلسل"),
    BOOK("book",         "كتاب"),
    COMIC("comic",       "قصة مصورة"),
    MANGA("manga",       "مانجا"),
    PODCAST("podcast",   "بودكاست"),
    EDUCATION("education","تعليمي"),
    CUSTOM("custom",     "مخصص");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: SERIES
    }
}

// ── إعدادات الخطوط ────────────────────────────────────────────────────────────

data class FontSettings(
    /** اسم ملف الخط المستخدم للعناوين (موجود في fonts/) */
    val titleFontFile: String = "",
    /** اسم ملف الخط المستخدم للنص الأساسي */
    val bodyFontFile: String = "",
    /** خط الكود (فارغ = افتراضي Monospace) */
    val codeFontFile: String = "",
    /** الخط الاحتياطي من Google Fonts أو النظام */
    val fallbackFont: String = "NotoNaskhArabic"
)

/**
 * خط مكتشف في مجلد fonts/ داخل مجلد السلسلة.
 * يُملأ تلقائياً من [SeriesSettingsLoader.discoverFonts].
 */
data class DiscoveredFont(
    val fileName: String,
    val uri: Uri,
    val format: FontFormat
)

enum class FontFormat(val extensions: List<String>) {
    TTF(listOf("ttf")),
    OTF(listOf("otf")),
    WOFF(listOf("woff")),
    WOFF2(listOf("woff2")),
    UNKNOWN(emptyList());

    companion object {
        fun fromExtension(ext: String): FontFormat =
            entries.firstOrNull { ext.lowercase() in it.extensions } ?: UNKNOWN
    }
}

// ── إعدادات المظهر ────────────────────────────────────────────────────────────

data class ThemeSettings(
    val primaryColor: String = "#6200EE",
    val secondaryColor: String = "#03DAC6",
    val backgroundColor: String = "#FFFFFF",
    val textColor: String = "#1C1B1F",
    val darkModeDefault: Boolean = false
)

data class DisplaySettings(
    val layout: String = "standard"   // standard | card | magazine | minimal | comic
)

data class EpisodeSettings(
    val numberingStyle: String = "arabic",  // arabic | latin | roman | custom
    val seasonEnabled: Boolean = true,
    val totalEpisodes: Int = 0,
    val totalSeasons: Int = 1
)

data class ComicSettings(
    val panelDirection: String = "rtl",      // rtl | ltr
    val pageMode: String = "single",          // single | double | scroll
    val backgroundColor: String = "#FFFFFF",
    val borderStyle: String = "solid",        // solid | none | shadow
    val borderColor: String = "#000000",
    val panelGapDp: Int = 8
)

data class ReadingSettings(
    val autoAdvance: Boolean = false,
    val autoAdvanceDelaySecs: Int = 3,
    val showProgress: Boolean = true,
    val nightModeDefault: Boolean = false
)

data class AudioSettings(
    val introFile: String = "",
    val outroFile: String = "",
    val autoPlayIntro: Boolean = false,
    val volumeDefault: Int = 80
)

data class SubtitleSettings(
    val defaultLanguage: String = "ar",
    val fontSize: Int = 16,
    val color: String = "#FFFFFF",
    val backgroundColor: String = "#80000000"
)

data class CharactersSettings(
    val charactersDlofFile: String = "characters.dlof",
    val showInViewer: Boolean = true
)

// ── الترخيص وحقوق النشر ─────────────────────────────────────────────────────

/**
 * معلومات ترخيص الحزمة. تُعرض في شاشة "حول" ضمن التطبيق ولا تؤثر على
 * سلوك القراءة، لكنها ضرورية لأي حزمة يُراد توزيعها أو نشرها.
 */
data class LicenseSettings(
    /** مثال: "All Rights Reserved"، "CC-BY-4.0"، "CC-BY-NC-SA-4.0"، "Public Domain" */
    val licenseType: String = "All Rights Reserved",
    val copyrightHolder: String = "",
    /** رابط نص الترخيص الكامل (إن وُجد) */
    val licenseUrl: String = ""
)

// ── التصنيف العمري وتحذيرات المحتوى ──────────────────────────────────────────

enum class ContentRating(val key: String, val arabicLabel: String) {
    ALL_AGES("all-ages", "لكل الأعمار"),
    TEEN("teen",         "المراهقون (+13)"),
    MATURE("mature",     "للبالغين (+18)");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: ALL_AGES
    }
}

/**
 * تصنيف عمري وتحذيرات محتوى اختيارية. [warnings] تُخزَّن في set.txt كسطر
 * واحد مفصول بفواصل (مثال: "عنف خفيف,لغة قوية") ويُعاد تفكيكها عند التحميل.
 */
data class ContentRatingSettings(
    val rating: ContentRating = ContentRating.ALL_AGES,
    val warnings: List<String> = emptyList()
)

// ── إمكانية الوصول (Accessibility) ──────────────────────────────────────────

/**
 * تفضيلات إمكانية الوصول التي يقترحها منشئ الحزمة كنقطة بداية؛ تبقى قابلة
 * للتعديل من داخل التطبيق نفسه دون التأثير على هذه القيم المخزَّنة.
 */
data class AccessibilitySettings(
    val highContrastAvailable: Boolean = false,
    /** اسم ملف خط صديق لعسر القراءة (Dyslexia-friendly) داخل fonts/، إن وُجد */
    val dyslexiaFriendlyFontFile: String = "",
    val minFontScale: Float = 0.8f,
    val maxFontScale: Float = 2.0f
)

// ── روابط خارجية ─────────────────────────────────────────────────────────────

/**
 * روابط اختيارية تُعرض للمستخدم (موقع رسمي، مصدر الحزمة، حسابات تواصل).
 * [socialLinks] تُخزَّن كسطر واحد مفصول بفواصل ويُعاد تفكيكها عند التحميل.
 */
data class LinksSettings(
    val websiteUrl: String = "",
    val sourceUrl: String = "",
    val socialLinks: List<String> = emptyList()
)

// ── محلّل ومحمّل الإعدادات ────────────────────────────────────────────────────

object SeriesSettingsLoader {

    private const val FONTS_FOLDER = "fonts"

    // ── سجلّ المفاتيح المعروفة ونوعها — أساس الفحص الاحترافي الدقيق ─────────
    private enum class FieldKind { STRING, BOOL, INT, FLOAT, COLOR, CSV }

    private val KNOWN_KEYS: Map<String, FieldKind> = mapOf(
        "series.id" to FieldKind.STRING,
        "series.title" to FieldKind.STRING,
        "series.domain" to FieldKind.STRING,
        "series.language" to FieldKind.STRING,
        "series.rtl" to FieldKind.BOOL,
        "series.author" to FieldKind.STRING,
        "series.publisher" to FieldKind.STRING,
        "series.year" to FieldKind.INT,
        "series.version" to FieldKind.STRING,
        "fonts.title" to FieldKind.STRING,
        "fonts.body" to FieldKind.STRING,
        "fonts.code" to FieldKind.STRING,
        "fonts.fallback" to FieldKind.STRING,
        "theme.primaryColor" to FieldKind.COLOR,
        "theme.secondaryColor" to FieldKind.COLOR,
        "theme.backgroundColor" to FieldKind.COLOR,
        "theme.textColor" to FieldKind.COLOR,
        "theme.darkMode" to FieldKind.BOOL,
        "display.layout" to FieldKind.STRING,
        "episodes.numberingStyle" to FieldKind.STRING,
        "episodes.seasonEnabled" to FieldKind.BOOL,
        "episodes.totalEpisodes" to FieldKind.INT,
        "episodes.totalSeasons" to FieldKind.INT,
        "comic.panelDirection" to FieldKind.STRING,
        "comic.pageMode" to FieldKind.STRING,
        "comic.backgroundColor" to FieldKind.COLOR,
        "comic.borderStyle" to FieldKind.STRING,
        "comic.borderColor" to FieldKind.COLOR,
        "comic.panelGap" to FieldKind.INT,
        "reading.autoAdvance" to FieldKind.BOOL,
        "reading.autoAdvanceDelay" to FieldKind.INT,
        "reading.showProgress" to FieldKind.BOOL,
        "reading.nightModeDefault" to FieldKind.BOOL,
        "audio.introFile" to FieldKind.STRING,
        "audio.outroFile" to FieldKind.STRING,
        "audio.autoPlayIntro" to FieldKind.BOOL,
        "audio.volumeDefault" to FieldKind.INT,
        "subtitles.defaultLanguage" to FieldKind.STRING,
        "subtitles.fontSize" to FieldKind.INT,
        "subtitles.color" to FieldKind.COLOR,
        "subtitles.backgroundColor" to FieldKind.COLOR,
        "characters.file" to FieldKind.STRING,
        "characters.showInViewer" to FieldKind.BOOL,
        "license.type" to FieldKind.STRING,
        "license.copyrightHolder" to FieldKind.STRING,
        "license.url" to FieldKind.STRING,
        "rating.level" to FieldKind.STRING,
        "rating.warnings" to FieldKind.CSV,
        "accessibility.highContrast" to FieldKind.BOOL,
        "accessibility.dyslexiaFont" to FieldKind.STRING,
        "accessibility.minFontScale" to FieldKind.FLOAT,
        "accessibility.maxFontScale" to FieldKind.FLOAT,
        "links.website" to FieldKind.STRING,
        "links.source" to FieldKind.STRING,
        "links.social" to FieldKind.CSV,
        "meta.createdAt" to FieldKind.STRING,
        "meta.updatedAt" to FieldKind.STRING,
        "meta.notes" to FieldKind.STRING,

        // ── حزمة dlofpkg — معرّف الحزمة (اسم بديل لـ series.*) ──
        "package.id" to FieldKind.STRING,
        "package.title" to FieldKind.STRING,
        "package.domain" to FieldKind.STRING,
        "package.language" to FieldKind.STRING,
        "package.rtl" to FieldKind.BOOL,
        "package.author" to FieldKind.STRING,
        "package.publisher" to FieldKind.STRING,
        "package.year" to FieldKind.INT,
        "package.version" to FieldKind.STRING,

        // ── حزمة dlofpkg — التشفير (setting/pro/) ──
        "crypto.enabled" to FieldKind.BOOL,
        "crypto.best64Path" to FieldKind.STRING,
        "crypto.wqPath" to FieldKind.STRING,

        // ── حزمة dlofpkg — القالب ──
        "template.path" to FieldKind.STRING,

        // ── حزمة dlofpkg — ملفات setting/ الإضافية ──
        "setting.mapFile" to FieldKind.STRING,
        "setting.docFile" to FieldKind.STRING,
        "setting.licenseFile" to FieldKind.STRING,

        // ── حزمة dlofpkg — مسارات الوسائط ──
        "media.imagePath" to FieldKind.STRING,
        "media.videoPath" to FieldKind.STRING,
        "media.fontsPath" to FieldKind.STRING
    )

    /** المفاتيح المستحسن وجودها كحدّ أدنى لملف set.txt "مكتمل" — غيابها ليس خطأً قاطعاً، لكنه يُبلَّغ كملاحظة. */
    private val RECOMMENDED_KEYS = listOf("series.id", "series.title", "series.language")

    private val HEX_COLOR_REGEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    /** سطر واحد غير صالح داخل set.txt، مع رقم السطر ونوع المشكلة تحديداً. */
    data class SetTxtIssue(val lineNumber: Int, val rawLine: String, val message: String)

    /**
     * تقرير فحص دقيق واحترافي لمحتوى set.txt: عدد الأزواج الصالحة، الأسطر
     * المشوّهة (بلا "="), المفاتيح غير المعروفة، القيم ذات النوع الخاطئ
     * (مثال: theme.primaryColor يجب أن يكون HEX)، المفاتيح المكرّرة،
     * والمفاتيح الموصى بها المفقودة. يُستخدم من شاشة استيراد الحزم لعرض
     * حالة "جارٍ فحص set.txt" ثم "تم التحقق من الحزمة" بثقة.
     */
    data class SetTxtValidation(
        val present: Boolean,
        val validPairs: Int,
        val malformedLines: Int,
        val unknownKeys: List<String>,
        val typeErrors: List<SetTxtIssue>,
        val duplicateKeys: List<String>,
        val missingRecommended: List<String>,
        val issues: List<SetTxtIssue>
    ) {
        /** لا مشاكل حرجة إطلاقاً — set.txt سليم 100%. */
        val isFullyClean: Boolean
            get() = malformedLines == 0 && typeErrors.isEmpty() && duplicateKeys.isEmpty()

        /** أي ملاحظة غير حرجة (مفاتيح غير معروفة أو مستحسنة مفقودة) تستحق تنبيهاً خفيفاً. */
        val hasSoftWarnings: Boolean
            get() = unknownKeys.isNotEmpty() || missingRecommended.isNotEmpty()
    }

    /**
     * يفحص محتوى set.txt نصّاً خام بدقّة احترافية: صيغة كل سطر (مفتاح=قيمة)،
     * نوع كل قيمة مقارنةً بالمفتاح المعروف، التكرار، والمفاتيح الموصى بها.
     * هذا هو المحلّل الموحّد المستخدم في كل من فتح مجلد سلسلة محلي
     * ([load]) وفحص حزمة `.dlofpkg`/`.dlofSeries` قبل تثبيتها.
     */
    fun validateSetTxtText(raw: String): SetTxtValidation {
        val seenKeys = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        val unknown = mutableListOf<String>()
        val typeErrors = mutableListOf<SetTxtIssue>()
        val allIssues = mutableListOf<SetTxtIssue>()
        var valid = 0
        var malformed = 0
        val props = mutableMapOf<String, String>()

        raw.lineSequence().forEachIndexed { idx, line ->
            val lineNo = idx + 1
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEachIndexed
            val eq = trimmed.indexOf('=')
            if (eq <= 0) {
                malformed++
                allIssues += SetTxtIssue(lineNo, trimmed, "سطر بصيغة غير صحيحة — المتوقّع مفتاح=قيمة")
                return@forEachIndexed
            }
            val key = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            valid++
            props[key] = value

            if (!seenKeys.add(key)) duplicates += key

            val kind = KNOWN_KEYS[key]
            if (kind == null) {
                unknown += key
            } else if (value.isNotEmpty()) {
                val typeOk = when (kind) {
                    FieldKind.BOOL -> value.equals("true", true) || value.equals("false", true)
                    FieldKind.INT -> value.toIntOrNull() != null
                    FieldKind.FLOAT -> value.toFloatOrNull() != null
                    FieldKind.COLOR -> HEX_COLOR_REGEX.matches(value)
                    FieldKind.CSV, FieldKind.STRING -> true
                }
                if (!typeOk) {
                    val issue = SetTxtIssue(
                        lineNo, trimmed,
                        "قيمة \"$key\" من نوع غير متوقَّع (${expectedTypeLabel(kind)}): \"$value\""
                    )
                    typeErrors += issue
                    allIssues += issue
                }
            }
        }

        // حزمة dlofpkg تستخدم بادئة "package." بدل "series."؛ أي مفتاح موصى به
        // متوفّر تحت أي من الاسمين يُعتبر كافياً (لا نطلب الاثنين معاً).
        val missingRecommended = RECOMMENDED_KEYS.filter { key ->
            val packageAlias = "package." + key.removePrefix("series.")
            props[key].isNullOrBlank() && props[packageAlias].isNullOrBlank()
        }

        return SetTxtValidation(
            present = true,
            validPairs = valid,
            malformedLines = malformed,
            unknownKeys = unknown.distinct(),
            typeErrors = typeErrors,
            duplicateKeys = duplicates.toList(),
            missingRecommended = missingRecommended,
            issues = allIssues
        )
    }

    private fun expectedTypeLabel(kind: FieldKind): String = when (kind) {
        FieldKind.BOOL -> "true/false"
        FieldKind.INT -> "رقم صحيح"
        FieldKind.FLOAT -> "رقم عشري"
        FieldKind.COLOR -> "لون HEX مثل #RRGGBB"
        FieldKind.CSV -> "قائمة مفصولة بفواصل"
        FieldKind.STRING -> "نص"
    }

    /**
     * يقرأ [set.txt] من مجلد السلسلة ويُعيد [SeriesSettings] معبّأ.
     *
     * @param context   سياق Android
     * @param folderUri Uri لمجلد السلسلة (من SAF)
     * @return الإعدادات إن وُجد ملف set.txt، أو null إن لم يوجد
     */
    fun load(context: Context, folderUri: Uri): SeriesSettings? {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        val setFile = folder.findFile("set.txt") ?: return null

        val props = mutableMapOf<String, String>()
        try {
            context.contentResolver.openInputStream(setFile.uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEachLine
                    val eq = trimmed.indexOf('=')
                    if (eq > 0) {
                        val key   = trimmed.substring(0, eq).trim()
                        val value = trimmed.substring(eq + 1).trim()
                        props[key] = value
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }

        val fonts    = discoverFonts(context, folder)
        val settings = buildSettings(props, fonts)
        return settings
    }

    /**
     * نفس [load] لكن يُعيد أيضاً تقرير فحص set.txt الكامل (نوع القيم،
     * المفاتيح المكرّرة/غير المعروفة...) إلى جانب الإعدادات المبنية —
     * تُستخدم من شاشة إعدادات السلسلة لعرض تحذيرات دقيقة للمستخدم.
     */
    fun loadWithReport(context: Context, folderUri: Uri): Pair<SeriesSettings, SetTxtValidation>? {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        val setFile = folder.findFile("set.txt") ?: return null

        val text = try {
            context.contentResolver.openInputStream(setFile.uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: return null
        } catch (_: Exception) {
            return null
        }

        val report = validateSetTxtText(text)
        val props = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            val eq = trimmed.indexOf('=')
            if (eq > 0) props[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
        }
        val fonts = discoverFonts(context, folder)
        return buildSettings(props, fonts) to report
    }

    /**
     * يكتشف ملفات الخطوط في مجلد fonts/ داخل مجلد السلسلة.
     */
    fun discoverFonts(context: Context, folder: DocumentFile): List<DiscoveredFont> {
        val fontsFolder = folder.findFile(FONTS_FOLDER) ?: return emptyList()
        if (!fontsFolder.isDirectory) return emptyList()

        return fontsFolder.listFiles().mapNotNull { file ->
            val name = file.name ?: return@mapNotNull null
            val ext  = name.substringAfterLast('.', "")
            val fmt  = FontFormat.fromExtension(ext)
            if (fmt == FontFormat.UNKNOWN) return@mapNotNull null
            DiscoveredFont(fileName = name, uri = file.uri, format = fmt)
        }
    }

    /**
     * يُنشئ [set.txt] جديد ويكتبه في مجلد السلسلة.
     */
    fun save(context: Context, folderUri: Uri, settings: SeriesSettings): Boolean {
        return try {
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return false
            val existing = folder.findFile("set.txt")
            val file = existing ?: folder.createFile("text/plain", "set.txt") ?: return false

            context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                out.write(settings.toSetTxt().toByteArray(Charsets.UTF_8))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── بناء الإعدادات من props ──────────────────────────────────────────────

    /**
     * يفكك قيمة مفصولة بفواصل (مثال: "a, b ,c") إلى قائمة نظيفة بلا مسافات
     * زائدة أو عناصر فارغة. يُعيد قائمة فارغة إن كانت القيمة null أو فارغة.
     */
    private fun parseCsv(raw: String?): List<String> =
        raw?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun buildSettings(
        p: Map<String, String>,
        fonts: List<DiscoveredFont>
    ): SeriesSettings = SeriesSettings(
        // ── حزمة dlofpkg تستخدم "package.*"؛ الصيغة القديمة تستخدم "series.*" ──
        // "series.*" له الأولوية إن وُجد، وإلا يُستخدم "package.*" كبديل.
        seriesId    = p["series.id"]       ?: p["package.id"]       ?: "",
        seriesTitle = p["series.title"]    ?: p["package.title"]    ?: "",
        domain      = SeriesSettingsDomain.fromKey(p["series.domain"] ?: p["package.domain"] ?: "series"),
        language    = p["series.language"] ?: p["package.language"] ?: "ar",
        rtl         = (p["series.rtl"] ?: p["package.rtl"])?.toBooleanStrictOrNull() ?: true,
        author      = p["series.author"]    ?: p["package.author"]    ?: "",
        publisher   = p["series.publisher"] ?: p["package.publisher"] ?: "",
        year        = (p["series.year"] ?: p["package.year"])?.toIntOrNull() ?: 0,
        version     = p["series.version"]   ?: p["package.version"]   ?: "1.0",

        fonts = FontSettings(
            titleFontFile = p["fonts.title"]    ?: "",
            bodyFontFile  = p["fonts.body"]     ?: "",
            codeFontFile  = p["fonts.code"]     ?: "",
            fallbackFont  = p["fonts.fallback"] ?: "NotoNaskhArabic"
        ),

        theme = ThemeSettings(
            primaryColor    = p["theme.primaryColor"]    ?: "#6200EE",
            secondaryColor  = p["theme.secondaryColor"]  ?: "#03DAC6",
            backgroundColor = p["theme.backgroundColor"] ?: "#FFFFFF",
            textColor       = p["theme.textColor"]       ?: "#1C1B1F",
            darkModeDefault = p["theme.darkMode"]?.toBooleanStrictOrNull() ?: false
        ),

        display = DisplaySettings(
            layout = p["display.layout"] ?: "standard"
        ),

        episodes = EpisodeSettings(
            numberingStyle = p["episodes.numberingStyle"]    ?: "arabic",
            seasonEnabled  = p["episodes.seasonEnabled"]?.toBooleanStrictOrNull() ?: true,
            totalEpisodes  = p["episodes.totalEpisodes"]?.toIntOrNull() ?: 0,
            totalSeasons   = p["episodes.totalSeasons"]?.toIntOrNull()  ?: 1
        ),

        comic = ComicSettings(
            panelDirection  = p["comic.panelDirection"]   ?: "rtl",
            pageMode        = p["comic.pageMode"]         ?: "single",
            backgroundColor = p["comic.backgroundColor"] ?: "#FFFFFF",
            borderStyle     = p["comic.borderStyle"]      ?: "solid",
            borderColor     = p["comic.borderColor"]      ?: "#000000",
            panelGapDp      = p["comic.panelGap"]?.toIntOrNull() ?: 8
        ),

        reading = ReadingSettings(
            autoAdvance          = p["reading.autoAdvance"]?.toBooleanStrictOrNull() ?: false,
            autoAdvanceDelaySecs = p["reading.autoAdvanceDelay"]?.toIntOrNull() ?: 3,
            showProgress         = p["reading.showProgress"]?.toBooleanStrictOrNull() ?: true,
            nightModeDefault     = p["reading.nightModeDefault"]?.toBooleanStrictOrNull() ?: false
        ),

        audio = AudioSettings(
            introFile       = p["audio.introFile"]       ?: "",
            outroFile       = p["audio.outroFile"]       ?: "",
            autoPlayIntro   = p["audio.autoPlayIntro"]?.toBooleanStrictOrNull() ?: false,
            volumeDefault   = p["audio.volumeDefault"]?.toIntOrNull() ?: 80
        ),

        subtitles = SubtitleSettings(
            defaultLanguage = p["subtitles.defaultLanguage"] ?: "ar",
            fontSize        = p["subtitles.fontSize"]?.toIntOrNull() ?: 16,
            color           = p["subtitles.color"]           ?: "#FFFFFF",
            backgroundColor = p["subtitles.backgroundColor"] ?: "#80000000"
        ),

        characters = CharactersSettings(
            charactersDlofFile = p["characters.file"]?.ifBlank { "characters.dlof" } ?: "characters.dlof",
            showInViewer       = p["characters.showInViewer"]?.toBooleanStrictOrNull() ?: true
        ),

        license = LicenseSettings(
            licenseType      = p["license.type"]?.ifBlank { "All Rights Reserved" } ?: "All Rights Reserved",
            copyrightHolder  = p["license.copyrightHolder"] ?: "",
            licenseUrl       = p["license.url"] ?: ""
        ),

        contentRating = ContentRatingSettings(
            rating   = ContentRating.fromKey(p["rating.level"] ?: "all-ages"),
            warnings = parseCsv(p["rating.warnings"])
        ),

        accessibility = AccessibilitySettings(
            highContrastAvailable   = p["accessibility.highContrast"]?.toBooleanStrictOrNull() ?: false,
            dyslexiaFriendlyFontFile = p["accessibility.dyslexiaFont"] ?: "",
            minFontScale             = p["accessibility.minFontScale"]?.toFloatOrNull() ?: 0.8f,
            maxFontScale             = p["accessibility.maxFontScale"]?.toFloatOrNull() ?: 2.0f
        ),

        links = LinksSettings(
            websiteUrl  = p["links.website"] ?: "",
            sourceUrl   = p["links.source"]  ?: "",
            socialLinks = parseCsv(p["links.social"])
        ),

        discoveredFonts = fonts,

        crypto = PackageCryptoSettings(
            enabled    = p["crypto.enabled"]?.toBooleanStrictOrNull() ?: false,
            best64Path = p["crypto.best64Path"]?.ifBlank { "setting/pro/Best64.xml" } ?: "setting/pro/Best64.xml",
            wqPath     = p["crypto.wqPath"]?.ifBlank { "setting/pro/WQ.JSON" } ?: "setting/pro/WQ.JSON"
        ),

        paths = PackagePathsSettings(
            templatePath = p["template.path"]?.ifBlank { "setting/dlotemplate.xml" } ?: "setting/dlotemplate.xml",
            mapFile      = p["setting.mapFile"]?.ifBlank { "setting/map.dlof" } ?: "setting/map.dlof",
            docFile      = p["setting.docFile"]?.ifBlank { "setting/Documentation.dlof" } ?: "setting/Documentation.dlof",
            licenseFile  = p["setting.licenseFile"]?.ifBlank { "setting/license.dlof" } ?: "setting/license.dlof",
            imagePath    = p["media.imagePath"]?.ifBlank { "media/image" } ?: "media/image",
            videoPath    = p["media.videoPath"]?.ifBlank { "media/video" } ?: "media/video",
            fontsPath    = p["media.fontsPath"]?.ifBlank { "media/fonts" } ?: "media/fonts"
        ),

        createdAt       = p["meta.createdAt"] ?: "",
        updatedAt       = p["meta.updatedAt"] ?: "",
        notes           = p["meta.notes"]     ?: ""
    )
}

// ── تحويل الإعدادات إلى نص set.txt ─────────────────────────────────────────

fun SeriesSettings.toSetTxt(): String = buildString {
    appendLine("# ══════════════════════════════════════════════════════════════")
    appendLine("# set.txt — إعدادات المسلسل / المستند / القصة المصورة")
    appendLine("# تم الإنشاء/التحديث تلقائياً بواسطة DLoF Reader")
    appendLine("# ══════════════════════════════════════════════════════════════")
    appendLine()
    appendLine("series.id=${seriesId}")
    appendLine("series.title=${seriesTitle}")
    appendLine("series.domain=${domain.key}")
    appendLine("series.language=${language}")
    appendLine("series.rtl=${rtl}")
    appendLine("series.author=${author}")
    appendLine("series.publisher=${publisher}")
    appendLine("series.year=${if (year == 0) "" else year.toString()}")
    appendLine("series.version=${version}")
    appendLine()
    appendLine("# ── الخطوط ────────────────────────────────────────────")
    appendLine("fonts.title=${fonts.titleFontFile}")
    appendLine("fonts.body=${fonts.bodyFontFile}")
    appendLine("fonts.code=${fonts.codeFontFile}")
    appendLine("fonts.fallback=${fonts.fallbackFont}")
    appendLine()
    appendLine("# ── الألوان ────────────────────────────────────────────")
    appendLine("theme.primaryColor=${theme.primaryColor}")
    appendLine("theme.secondaryColor=${theme.secondaryColor}")
    appendLine("theme.backgroundColor=${theme.backgroundColor}")
    appendLine("theme.textColor=${theme.textColor}")
    appendLine("theme.darkMode=${theme.darkModeDefault}")
    appendLine()
    appendLine("# ── التخطيط ────────────────────────────────────────────")
    appendLine("display.layout=${display.layout}")
    appendLine()
    appendLine("# ── الحلقات ────────────────────────────────────────────")
    appendLine("episodes.numberingStyle=${episodes.numberingStyle}")
    appendLine("episodes.seasonEnabled=${episodes.seasonEnabled}")
    appendLine("episodes.totalEpisodes=${episodes.totalEpisodes}")
    appendLine("episodes.totalSeasons=${episodes.totalSeasons}")
    appendLine()
    appendLine("# ── القصة المصورة ──────────────────────────────────────")
    appendLine("comic.panelDirection=${comic.panelDirection}")
    appendLine("comic.pageMode=${comic.pageMode}")
    appendLine("comic.backgroundColor=${comic.backgroundColor}")
    appendLine("comic.borderStyle=${comic.borderStyle}")
    appendLine("comic.borderColor=${comic.borderColor}")
    appendLine("comic.panelGap=${comic.panelGapDp}")
    appendLine()
    appendLine("# ── القراءة ────────────────────────────────────────────")
    appendLine("reading.autoAdvance=${reading.autoAdvance}")
    appendLine("reading.autoAdvanceDelay=${reading.autoAdvanceDelaySecs}")
    appendLine("reading.showProgress=${reading.showProgress}")
    appendLine("reading.nightModeDefault=${reading.nightModeDefault}")
    appendLine()
    appendLine("# ── الصوت ──────────────────────────────────────────────")
    appendLine("audio.introFile=${audio.introFile}")
    appendLine("audio.outroFile=${audio.outroFile}")
    appendLine("audio.autoPlayIntro=${audio.autoPlayIntro}")
    appendLine("audio.volumeDefault=${audio.volumeDefault}")
    appendLine()
    appendLine("# ── الترجمة ────────────────────────────────────────────")
    appendLine("subtitles.defaultLanguage=${subtitles.defaultLanguage}")
    appendLine("subtitles.fontSize=${subtitles.fontSize}")
    appendLine("subtitles.color=${subtitles.color}")
    appendLine("subtitles.backgroundColor=${subtitles.backgroundColor}")
    appendLine()
    appendLine("# ── الشخصيات ───────────────────────────────────────────")
    appendLine("characters.file=${characters.charactersDlofFile}")
    appendLine("characters.showInViewer=${characters.showInViewer}")
    appendLine()
    appendLine("# ── الترخيص وحقوق النشر ─────────────────────────────────")
    appendLine("license.type=${license.licenseType}")
    appendLine("license.copyrightHolder=${license.copyrightHolder}")
    appendLine("license.url=${license.licenseUrl}")
    appendLine()
    appendLine("# ── التصنيف العمري ──────────────────────────────────────")
    appendLine("rating.level=${contentRating.rating.key}")
    appendLine("rating.warnings=${contentRating.warnings.joinToString(",")}")
    appendLine()
    appendLine("# ── إمكانية الوصول ───────────────────────────────────────")
    appendLine("accessibility.highContrast=${accessibility.highContrastAvailable}")
    appendLine("accessibility.dyslexiaFont=${accessibility.dyslexiaFriendlyFontFile}")
    appendLine("accessibility.minFontScale=${accessibility.minFontScale}")
    appendLine("accessibility.maxFontScale=${accessibility.maxFontScale}")
    appendLine()
    appendLine("# ── روابط خارجية ─────────────────────────────────────────")
    appendLine("links.website=${links.websiteUrl}")
    appendLine("links.source=${links.sourceUrl}")
    appendLine("links.social=${links.socialLinks.joinToString(",")}")
    appendLine()
    appendLine("# ── تشفير الحزمة (pro/) ──────────────────────────────")
    appendLine("crypto.enabled=${crypto.enabled}")
    appendLine("crypto.best64Path=${crypto.best64Path}")
    appendLine("crypto.wqPath=${crypto.wqPath}")
    appendLine()
    appendLine("# ── القوالب ──────────────────────────────────────────")
    appendLine("template.path=${paths.templatePath}")
    appendLine()
    appendLine("# ── ملفات الإعدادات الإضافية ─────────────────────────")
    appendLine("setting.mapFile=${paths.mapFile}")
    appendLine("setting.docFile=${paths.docFile}")
    appendLine("setting.licenseFile=${paths.licenseFile}")
    appendLine()
    appendLine("# ── الوسائط ──────────────────────────────────────────")
    appendLine("media.imagePath=${paths.imagePath}")
    appendLine("media.videoPath=${paths.videoPath}")
    appendLine("media.fontsPath=${paths.fontsPath}")
    appendLine()
    appendLine("# ── بيانات وصفية ───────────────────────────────────────")
    appendLine("meta.createdAt=${createdAt}")
    appendLine("meta.updatedAt=${updatedAt}")
    appendLine("meta.notes=${notes}")
}
