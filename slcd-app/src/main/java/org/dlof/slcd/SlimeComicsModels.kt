package org.dlof.slcd

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * ── نماذج بيانات Slime Comics dlof (SLCD) ─────────────────────────────
 *
 * تعكس هيكل مكتبة SLCD الموصوف في spec/PACKAGE_FORMATS.md
 * («Slime Comics dlof (SLCD)»):
 *
 *   SlimeComicsLibrary/
 *   ├── story_cover.*  banner.*  ad_banner.*  ad_video.*  publisher.txt  country.txt ...
 *   ├── covers/cover1/ ...
 *   ├── rights/rights.(html|md|txt|jpg...)
 *   └── seasons/season1/ (season_icon.* banner.* description.txt genre.txt)
 *                └── chapters/chapter1/ (chapter_cover.* banner.* genre.txt external_link.txt)
 *                              └── wings/wing1/ wing2/ ... (لفصل طويل مقسّم لأجزاء)
 */

/** غلاف مفرد داخل مجلد covers/coverN/. */
data class SlcdCover(
    val number: Int,
    val folder: DocumentFile,
    val imageUri: Uri?,
    /** عنوان مخصّص اختياري يُحفظ في ملف عنوان مخفي داخل مجلد الغلاف. */
    val title: String? = null
)

/**
 * جناح واحد داخل فصل طويل: seasons/seasonN/chapters/chapterM/wings/wingK/.
 * يسمح بتقسيم فصل واحد طويل إلى أجزاء منفصلة الصفحات (مثال: الجناح 1 = 10
 * صفحات، الجناح 2 = 9 صفحات...)، كل جناح يُقرأ بشكل مستقل ضمن نفس الفصل
 * ويمثّل — سردياً — وحدة درامية متكاملة (نظام الأجنحة): تبدأ بحدث، تتصاعد،
 * وتنتهي عند [isCliffhanger] بلحظة تشويق تُبقي القارئ متشوّقاً للجناح التالي.
 */
data class SlcdWing(
    val number: Int,
    val folder: DocumentFile,
    val pageCount: Int,
    val title: String? = null,
    /**
     * هل ينتهي هذا الجناح بلحظة تشويق (Cliffhanger)؟ عند true تُعرض بطاقة
     * ختامية مختلفة في القارئ (لون/أيقونة مميّزة) بدل بطاقة "نهاية الفصل"
     * العادية، لتحفيز الانتقال الفوري للجناح التالي.
     */
    val isCliffhanger: Boolean = false,
    /** ملاحظة تشويقية قصيرة اختيارية تُعرض في بطاقة ختام الجناح (مثال: "من سينجو؟!"). */
    val cliffhangerNote: String? = null
)

/** فصل مفرد داخل seasons/seasonN/chapters/chapterM/. */
data class SlcdChapter(
    val seasonNumber: Int,
    val chapterNumber: Int,
    val folder: DocumentFile,
    val pageCount: Int,
    /** اسم حزمة .slcdpkg الحصرية المقترح لهذا الفصل عند التصدير. */
    val suggestedPackageName: String = "Season$seasonNumber-Chapter$chapterNumber.slcdpkg",
    /** يكون true إن كان الفصل قد صُدِّر مسبقاً كحزمة (.slcdpkg أو .dlofpkg قديمة) داخل نفس مجلده. */
    val alreadyPackaged: Boolean = false,
    /** عنوان الفصل كما أدخله المستخدم عند الإنشاء (أو أعاد تسميته لاحقاً). */
    val title: String? = null,
    /** مفضّل: يظهر بنجمة مميّزة ويمكن تصفية القائمة عليه. */
    val isFavorite: Boolean = false,
    /** مقروء بالكامل: علامة يدوية أو تُضبط تلقائياً عند فتح الفصل للقراءة. */
    val isRead: Boolean = false,
    /** تصنيف اختياري خاص بهذا الفصل وحده (قد يختلف عن تصنيف الموسم/القصة). */
    val genre: String? = null,
    /** غلاف مخصّص لهذا الفصل وحده (chapter_cover.*)، أو null لاستخدام غلاف الموسم ثم القصة تلقائياً. */
    val customCoverUri: Uri? = null,
    /** أجنحة الفصل (لفصل طويل مقسّم لأجزاء) — فارغة لفصل عادي بصفحات مباشرة. */
    val wings: List<SlcdWing> = emptyList(),
    /** رابط خارجي يجعل القارئ يُحوَّل لموقع خارجي بدل القراءة داخل SLCD؛ null = لا يوجد تحويل خاص بهذا الفصل. */
    val externalUrl: String? = null
) {
    /** إجمالي صفحات الفصل: صفحاته المباشرة + كل صفحات أجنحته. */
    val totalPageCount: Int get() = pageCount + wings.sumOf { it.pageCount }
}

/**
 * معلومات مُستخلَصة من ترويسة حزمة `.slcdpkg` الحصرية دون الحاجة لفكّها بالكامل
 * — تُعرض للمستخدم في حوار تأكيد الاستيراد قبل نسخ الصفحات فعلياً.
 */
data class SlcdPackageInfo(
    val originSeasonNumber: Int,
    val originChapterNumber: Int,
    val title: String?,
    val pageCount: Int,
    val createdAtMillis: Long
)

/** موسم كامل: seasons/seasonN/ بكل فصوله. */
data class SlcdSeason(
    val number: Int,
    val folder: DocumentFile,
    val chapters: List<SlcdChapter>,
    /** عنوان مخصّص اختياري للموسم. */
    val title: String? = null,
    /** وصف مختصر لقصة هذا الموسم تحديداً. */
    val description: String? = null,
    /** تصنيف/نوع هذا الموسم (نص حر، مثال: "أكشن، مغامرة"). */
    val genre: String? = null,
    /** أيقونة الموسم (season_icon.*) — رمز صغير مميّز للموسم، مختلف عن الغلاف/البانر الكاملين. */
    val iconUri: Uri? = null
)

/** لقطة كاملة لمكتبة SLCD المثبَّتة، تُستخدم لعرض شاشة التصفح الرئيسية. */
data class SlcdLibrary(
    val root: DocumentFile,
    val covers: List<SlcdCover>,
    val seasons: List<SlcdSeason>,
    /** وصف عام للقصة كاملة (كل المواسم)، يظهر في بطاقة الغلاف الرئيسية. */
    val description: String? = null,
    /** تصنيف/نوع القصة كاملة (نص حر). */
    val genre: String? = null,
    /** اسم/عنوان القصة كاملة (منفصل عن اسم التطبيق نفسه). */
    val title: String? = null,
    /** الغلاف الكامل الرئيسي للقصة (story_cover.*). */
    val coverUri: Uri? = null,
    /** اسم/تفاصيل جهة النشر. */
    val publisher: String? = null,
    /** الدولة المرتبطة بالقصة. */
    val country: String? = null,
    /** رابط خارجي افتراضي لكل القصة — يُستخدم إن لم يوفّر الفصل رابطه الخاص. */
    val externalUrl: String? = null
) {
    /** إجمالي عدد الفصول عبر كل المواسم — يُحسب مرة واحدة هنا لتفادي تكراره في الواجهة. */
    val totalChapterCount: Int get() = seasons.sumOf { it.chapters.size }
}

// ───────────────────────── فريق العمل وحقوق النشر (credits/) ─────────────────────────

/** الأدوار الثابتة المدعومة في شاشة "فريق العمل" — يمكن لعدة أشخاص مشاركة نفس الدور. */
enum class SlcdCreditRole(val displayName: String) {
    AUTHOR("المؤلف"),
    DIRECTOR("المخرج"),
    SUPERVISOR("المشرف"),
    WRITER("الكاتب"),
    ARTIST("الرسام"),
    COLORIST("التلوين"),
    TRANSLATOR("الترجمة")
}

/** فرد واحد ضمن فريق العمل: دور + اسم + صورة شخصية اختيارية (محفوظة داخل credits/photos/). */
data class SlcdCreditPerson(
    val id: String,
    val role: SlcdCreditRole,
    val name: String,
    /** اسم ملف الصورة داخل credits/photos/ (بلا مسار)، أو null إن لم تُضف صورة. */
    val photoFileName: String? = null,
    /** Uri فعلي للصورة المحفوظة، يُملأ عند القراءة من التخزين لعرضها مباشرة. */
    val photoUri: Uri? = null
)

/** رابط تواصل اجتماعي واحد (منصّة + رابط)، مثال: ("إنستغرام", "https://instagram.com/..."). */
data class SlcdSocialLink(
    val platform: String,
    val url: String
)

/**
 * كامل معلومات "فريق العمل والنشر" لمكتبة SLCD — تُحفظ في credits/credits.json
 * بجذر المكتبة، وتُحمَّل بشكل منفصل تماماً عن [SlcdLibrary] (كسول، عند فتح
 * شاشة فريق العمل فقط) لأنها لا تلزم لتصفّح المواسم والفصول اليومي.
 */
data class SlcdCredits(
    val people: List<SlcdCreditPerson> = emptyList(),
    val company: String? = null,
    val copyright: String? = null,
    val socialLinks: List<SlcdSocialLink> = emptyList()
)

// ───────────────────────── قاموس الحقوق (rights/) ─────────────────────────

/** الصيغ المدعومة لصفحة "قاموس الحقوق" — نص منسّق (HTML)، Markdown، نص عادي، أو صورة واحدة. */
enum class SlcdRightsFormat(val extension: String, val displayName: String) {
    HTML("html", "HTML"),
    MARKDOWN("md", "Markdown"),
    TEXT("txt", "نص عادي"),
    IMAGE("img", "صورة")
}

/** صفحة "قاموس الحقوق" المحفوظة — ملف واحد فقط بجذر المكتبة (rights/rights.*). */
data class SlcdRightsDoc(
    val format: SlcdRightsFormat,
    val fileUri: Uri,
    /** المحتوى النصي إن كان التنسيق نصياً (html/md/txt)؛ null لتنسيق الصورة. */
    val textContent: String? = null
)
