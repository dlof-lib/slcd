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
 *   ├── covers/cover1/ ...
 *   └── seasons/season1/chapters/chapter1/ ...
 */

/** غلاف مفرد داخل مجلد covers/coverN/. */
data class SlcdCover(
    val number: Int,
    val folder: DocumentFile,
    val imageUri: Uri?,
    /** عنوان مخصّص اختياري يُحفظ في ملف عنوان مخفي داخل مجلد الغلاف. */
    val title: String? = null
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
    val isRead: Boolean = false
)

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
    val genre: String? = null
)

/** لقطة كاملة لمكتبة SLCD المثبَّتة، تُستخدم لعرض شاشة التصفح الرئيسية. */
data class SlcdLibrary(
    val root: DocumentFile,
    val covers: List<SlcdCover>,
    val seasons: List<SlcdSeason>,
    /** وصف عام للقصة كاملة (كل المواسم)، يظهر في بطاقة الغلاف الرئيسية. */
    val description: String? = null,
    /** تصنيف/نوع القصة كاملة (نص حر). */
    val genre: String? = null
) {
    /** إجمالي عدد الفصول عبر كل المواسم — يُحسب مرة واحدة هنا لتفادي تكراره في الواجهة. */
    val totalChapterCount: Int get() = seasons.sumOf { it.chapters.size }
}
