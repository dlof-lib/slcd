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
    private const val KEY_THEME_MODE = "slcd_theme_mode"
    private const val KEY_READING_STYLE = "slcd_reading_style"

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

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** أسلوب القراءة الحالي — افتراضياً [ReadingStyle.WEBTOON] حفاظاً على السلوك القديم. */
    var readingStyle by mutableStateOf(ReadingStyle.WEBTOON)
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
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        readingStyle = runCatching {
            ReadingStyle.valueOf(prefs.getString(KEY_READING_STYLE, ReadingStyle.WEBTOON.name) ?: ReadingStyle.WEBTOON.name)
        }.getOrDefault(ReadingStyle.WEBTOON)
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
        prefs(context).edit()
            .putBoolean(KEY_INSTALLED, false)
            .remove(KEY_ROOT_URI)
            .remove(KEY_LAST_SEASON)
            .remove(KEY_LAST_CHAPTER)
            .apply()
    }

    /** يُستدعى في كل مرة يُفتح فيها فصل للقراءة. */
    fun markSlcdLastRead(context: Context, seasonNumber: Int, chapterNumber: Int) {
        slcdLastSeason = seasonNumber
        slcdLastChapter = chapterNumber
        prefs(context).edit()
            .putInt(KEY_LAST_SEASON, seasonNumber)
            .putInt(KEY_LAST_CHAPTER, chapterNumber)
            .apply()
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        themeMode = mode
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    /** يبدّل أسلوب القراءة ويحفظه — يُستدعى من زر التبديل داخل شاشة القراءة نفسها. */
    fun setReadingStyle(context: Context, style: ReadingStyle) {
        readingStyle = style
        prefs(context).edit().putString(KEY_READING_STYLE, style.name).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
