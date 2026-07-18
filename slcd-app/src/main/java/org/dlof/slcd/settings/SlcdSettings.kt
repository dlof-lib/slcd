package org.dlof.slcd.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * ── إعدادات تطبيق SLCD المستقل ──────────────────────────────────────
 *
 * نسخة مستقلة بالكامل عن AppSettings الخاص بتطبيق DLoF الرئيسي — لا
 * ترتبط به بأي شكل، ولا تتشارك معه SharedPreferences ولا حالة التشغيل.
 * كانت SLCD سابقاً "تطبيقاً مصغّراً" يعيش داخل DLoF ويقرأ حالته من
 * AppSettings العامة؛ الآن هي تطبيق مستقل تماماً (حزمته الخاصة،
 * أيقونته الخاصة، ودورة حياته الخاصة)، وهذا الكائن هو مصدر الحقيقة
 * الوحيد لحالتها.
 */
object SlcdSettings {

    private const val PREF_NAME = "slcd_app_settings"
    private const val KEY_INSTALLED = "slcd_installed"
    private const val KEY_ROOT_URI = "slcd_root_uri"
    private const val KEY_LAST_SEASON = "slcd_last_season"
    private const val KEY_LAST_CHAPTER = "slcd_last_chapter"
    private const val KEY_LAST_WING = "slcd_last_wing"
    private const val KEY_WING_PROGRESS_PREFIX = "slcd_wing_progress_"
    private const val KEY_THEME_MODE = "slcd_theme_mode"
    private const val KEY_READING_STYLE = "slcd_reading_style"
    private const val KEY_DEVELOPER_MODE = "slcd_developer_mode"
    private const val KEY_STRUCTURAL_CACHE_LIMIT = "slcd_structural_cache_limit"
    private const val KEY_PAGE_BITMAP_WINDOW = "slcd_page_bitmap_window"
    private const val KEY_WING_DEFAULT_PAGES = "slcd_wing_default_pages"
    private const val KEY_KEEP_SCREEN_ON = "slcd_keep_screen_on"
    private const val KEY_DOUBLE_TAP_ZOOM = "slcd_double_tap_zoom_factor"

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    /**
     * ── أسلوب القراءة ────────────────────────────────────────────────
     *
     * [WEBTOON] الأسلوب الكلاسيكي: تمرير رأسي متواصل بلا فواصل صفحات
     * (انظر [org.dlof.slcd.SlcdComicReaderScreen]).
     *
     * [PLUS] أسلوب **SLCD+** الجديد: قراءة بالتقليب، لوحة واحدة تملأ
     * الشاشة في كل مرة، مع حركة تركيز/تكبير دخول لكل لوحة وشريط مصغّرات
     * أسفل الشاشة (انظر [org.dlof.slcd.SlcdPlusReaderScreen]). يُحفظ
     * اختيار المستخدم لهذا الأسلوب ويُطبَّق تلقائياً في المرة القادمة.
     */
    enum class ReadingStyle { WEBTOON, PLUS }

    /** هل اختار المستخدم مجلد مكتبة القصص المصورة بالفعل عبر SAF؟ */
    var slcdInstalled by mutableStateOf(false)
        private set

    /** مسار (Tree URI) مجلد مكتبة SLCD بعد التثبيت، أو null قبل ذلك. */
    var slcdRootUri by mutableStateOf<String?>(null)
        private set

    /** آخر موسم/فصل فُتح للقراءة — تُستخدم لبطاقة "متابعة القراءة". */
    var slcdLastSeason by mutableStateOf<Int?>(null)
        private set
    var slcdLastChapter by mutableStateOf<Int?>(null)
        private set
    /**
     * آخر جناح كان مفتوحاً ضمن [slcdLastChapter] (نظام الأجنحة) — يسمح
     * لبطاقة "متابعة القراءة" بالعودة للجناح **بالضبط** بدل بداية الفصل
     * دائماً. null = فصل بلا أجنحة أو لم يُفتح جناح بعد.
     */
    var slcdLastWing by mutableStateOf<Int?>(null)
        private set

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** أسلوب القراءة الحالي — افتراضياً [ReadingStyle.WEBTOON] حفاظاً على السلوك القديم. */
    var readingStyle by mutableStateOf(ReadingStyle.WEBTOON)
        private set

    // ── إعدادات متقدّمة/مخفية ────────────────────────────────────────────
    // تُفعَّل "وضع المطوّر" بلمس رقم الإصدار 7 مرات (نفس تقليد أندرويد
    // المعروف) — تظهر عندها بطاقة الإعدادات المتقدّمة في شاشة الإعدادات.
    // القيم الافتراضية هنا محفوظة بعناية لتطابق سلوك التطبيق قبل إضافتها
    // تماماً، فتفعيلها اختياري بالكامل ولا يغيّر شيئاً بالتحميل الهيكلي
    // الحالي إلا إن عدّله المستخدم صراحة.

    /** وضع المطوّر: يكشف عدّادات التحميل الهيكلي وضبط الأداء اليدوي. */
    var developerModeEnabled by mutableStateOf(false)
        private set

    /** سقف عدد الفصول المحفوظة بالذاكرة الهيكلية المشتركة ([org.dlof.slcd.SlcdStructuralCache]) — افتراضياً 16. */
    var structuralCacheLimit by mutableStateOf(16)
        private set

    /** نافذة تفريغ/حفظ صور اللوحات حول الموضع الحالي أثناء القراءة (± القيمة) — افتراضياً 3. */
    var pageBitmapWindow by mutableStateOf(3)
        private set

    /** عدد الصفحات الافتراضي المقترح لكل جناح جديد (نظام الأجنحة) — افتراضياً 10، ضمن المدى الدرامي الموصى به. */
    var wingDefaultPageCount by mutableStateOf(10)
        private set

    /** إبقاء الشاشة مضاءة أثناء القراءة (يمنع القفل التلقائي في منتصف فصل طويل). */
    var keepScreenOnWhileReading by mutableStateOf(false)
        private set

    /** عامل التكبير عند النقر المزدوج في أسلوب SLCD+ — افتراضياً 2.0×. */
    var doubleTapZoomFactor by mutableStateOf(2.0f)
        private set

    private var initialized = false

    /** يُستدعى مرة واحدة عند إقلاع التطبيق (انظر SlcdApplication.onCreate). */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = prefs(context)
        slcdInstalled = prefs.getBoolean(KEY_INSTALLED, false)
        slcdRootUri = prefs.getString(KEY_ROOT_URI, null)
        slcdLastSeason = prefs.getInt(KEY_LAST_SEASON, -1).takeIf { it >= 0 }
        slcdLastChapter = prefs.getInt(KEY_LAST_CHAPTER, -1).takeIf { it >= 0 }
        slcdLastWing = prefs.getInt(KEY_LAST_WING, -1).takeIf { it >= 0 }
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        readingStyle = runCatching {
            ReadingStyle.valueOf(prefs.getString(KEY_READING_STYLE, ReadingStyle.WEBTOON.name) ?: ReadingStyle.WEBTOON.name)
        }.getOrDefault(ReadingStyle.WEBTOON)
        developerModeEnabled = prefs.getBoolean(KEY_DEVELOPER_MODE, false)
        structuralCacheLimit = prefs.getInt(KEY_STRUCTURAL_CACHE_LIMIT, 16).coerceIn(4, 64)
        pageBitmapWindow = prefs.getInt(KEY_PAGE_BITMAP_WINDOW, 3).coerceIn(1, 10)
        wingDefaultPageCount = prefs.getInt(KEY_WING_DEFAULT_PAGES, 10).coerceIn(9, 10)
        keepScreenOnWhileReading = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        doubleTapZoomFactor = prefs.getFloat(KEY_DOUBLE_TAP_ZOOM, 2.0f).coerceIn(1.5f, 3.0f)
    }

    /** يُستدعى بعد أن يختار المستخدم مجلد مكتبة SLCD ويمنحه صلاحية دائمة. */
    fun markSlcdInstalled(context: Context, rootTreeUri: String) {
        slcdInstalled = true
        slcdRootUri = rootTreeUri
        prefs(context).edit()
            .putBoolean(KEY_INSTALLED, true)
            .putString(KEY_ROOT_URI, rootTreeUri)
            .apply()
    }

    /** يزيل تثبيت SLCD (لا يحذف الملفات نفسها، فقط يعيد عرض شاشة التثبيت لاحقاً). */
    fun uninstallSlcd(context: Context) {
        slcdInstalled = false
        slcdRootUri = null
        slcdLastSeason = null
        slcdLastChapter = null
        slcdLastWing = null
        prefs(context).edit()
            .putBoolean(KEY_INSTALLED, false)
            .remove(KEY_ROOT_URI)
            .remove(KEY_LAST_SEASON)
            .remove(KEY_LAST_CHAPTER)
            .remove(KEY_LAST_WING)
            .apply()
    }

    /**
     * يُستدعى في كل مرة يُفتح فيها فصل للقراءة. إن مُرِّر [wingNumber] (نظام
     * الأجنحة) يُحفظ أيضاً ليُستأنف عنده تحديداً لاحقاً — راجع
     * [recordWingRead] للتحديث المستمر أثناء تنقّل القارئ بين الأجنحة داخل
     * نفس جلسة القراءة (بدل مرة واحدة عند الفتح فقط).
     */
    fun markSlcdLastRead(context: Context, seasonNumber: Int, chapterNumber: Int, wingNumber: Int? = null) {
        slcdLastSeason = seasonNumber
        slcdLastChapter = chapterNumber
        slcdLastWing = wingNumber
        val editor = prefs(context).edit()
            .putInt(KEY_LAST_SEASON, seasonNumber)
            .putInt(KEY_LAST_CHAPTER, chapterNumber)
        if (wingNumber != null) editor.putInt(KEY_LAST_WING, wingNumber) else editor.remove(KEY_LAST_WING)
        editor.apply()
    }

    /**
     * تقدّم الأجنحة **لكل فصل على حدة** (وليس فقط "آخر فصل" في
     * [slcdLastWing]) — يسمح لبطاقات الفصول في شاشة الموسم بعرض "الجناح 3
     * من 5" لأي فصل زاره القارئ سابقاً، لا آخر فصل فتحه فقط فحسب. يتصل هذا
     * مباشرة بنظام الأجنحة والتشويق: القارئ يستطيع رؤية أين توقّف بالضبط في
     * كل فصل عبر المكتبة، بما فيها هل توقّف عند لحظة تشويق تحديداً.
     */
    fun recordWingRead(context: Context, seasonNumber: Int, chapterNumber: Int, wingNumber: Int) {
        prefs(context).edit()
            .putInt("$KEY_WING_PROGRESS_PREFIX${seasonNumber}_$chapterNumber", wingNumber)
            .apply()
    }

    /** آخر جناح وصل إليه القارئ في فصل مُعطى، أو null إن لم يُفتح جناح فيه بعد. */
    fun lastReadWing(context: Context, seasonNumber: Int, chapterNumber: Int): Int? =
        prefs(context).getInt("$KEY_WING_PROGRESS_PREFIX${seasonNumber}_$chapterNumber", -1).takeIf { it >= 0 }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        themeMode = mode
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    /** يبدّل أسلوب القراءة ويحفظه — يُستدعى من زر التبديل داخل شاشة القراءة نفسها. */
    fun setReadingStyle(context: Context, style: ReadingStyle) {
        readingStyle = style
        prefs(context).edit().putString(KEY_READING_STYLE, style.name).apply()
    }

    /** يبدّل وضع المطوّر — يُستدعى عادة بعد 7 لمسات متتالية على رقم الإصدار في شاشة الإعدادات. */
    fun setDeveloperMode(context: Context, enabled: Boolean) {
        developerModeEnabled = enabled
        prefs(context).edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply()
    }

    /** يضبط سقف الذاكرة الهيكلية المشتركة (فصول)، ويُطبَّق فوراً على [org.dlof.slcd.SlcdStructuralCache]. */
    fun setStructuralCacheLimit(context: Context, limit: Int) {
        val clamped = limit.coerceIn(4, 64)
        structuralCacheLimit = clamped
        prefs(context).edit().putInt(KEY_STRUCTURAL_CACHE_LIMIT, clamped).apply()
    }

    /** يضبط نافذة تخزين/تفريغ صور اللوحات أثناء القراءة (± القيمة حول الموضع الحالي). */
    fun setPageBitmapWindow(context: Context, window: Int) {
        val clamped = window.coerceIn(1, 10)
        pageBitmapWindow = clamped
        prefs(context).edit().putInt(KEY_PAGE_BITMAP_WINDOW, clamped).apply()
    }

    /** يضبط عدد الصفحات الافتراضي المقترح لجناح جديد (9 أو 10، المدى الدرامي الموصى به). */
    fun setWingDefaultPageCount(context: Context, count: Int) {
        val clamped = count.coerceIn(9, 10)
        wingDefaultPageCount = clamped
        prefs(context).edit().putInt(KEY_WING_DEFAULT_PAGES, clamped).apply()
    }

    fun setKeepScreenOnWhileReading(context: Context, enabled: Boolean) {
        keepScreenOnWhileReading = enabled
        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun setDoubleTapZoomFactor(context: Context, factor: Float) {
        val clamped = factor.coerceIn(1.5f, 3.0f)
        doubleTapZoomFactor = clamped
        prefs(context).edit().putFloat(KEY_DOUBLE_TAP_ZOOM, clamped).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
