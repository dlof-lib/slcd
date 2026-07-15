package org.dlof.slcd

import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ذاكرة هيكلية مؤقتة على مستوى العملية (Process) — لا تُحفظ على القرص
 * ══════════════════════════════════════════════════════════════════════════
 *
 * الفكرة: "التحميل الهيكلي" في شاشات القراءة (انظر [SlcdComicReaderScreen]
 * و[SlcdPlusReaderScreen]) يقرأ أولاً **قائمة أسماء اللوحات فقط** لفصل
 * معيّن (سريع وخفيف جداً: عملية `listFiles` واحدة، دون فتح أو فكّ أي صورة)
 * قبل أن يُفكّ أي محتوى فعلياً. هذا الكائن يذهب خطوة أبعد: يحفظ تلك
 * القوائم الهيكلية (ولوائح فصول كل موسم) في ذاكرة مشتركة تعيش طوال عمر
 * التطبيق، بحيث:
 *
 *  • عند العودة لفصل سبق فتحه، أو عند التنقّل بين فصول متتالية أثناء
 *    القراءة، لا يُعاد المسح الهيكلي لمجلد الفصل من جديد إن كان محفوظاً
 *    بالفعل — انتقال فوري بلا وميض تحميل.
 *  • تُتيح **التحميل المسبق (Prefetch)** الهيكلي البحت للفصل التالي/السابق
 *    أثناء قراءة الفصل الحالي (اقتراب القارئ من أول/آخر لوحة)، بحيث تكون
 *    قائمة لوحات الفصل القادم جاهزة فعلاً لحظة الانتقال إليه، رغم أن
 *    الصور نفسها (Bitmaps) تبقى غير مفكوكة حتى الحاجة الفعلية إليها —
 *    نفس فلسفة "هيكل أولاً، محتوى لاحقاً" لكن ممتدة الآن عبر حدود الفصل
 *    الواحد إلى الفصل/الموسم بأكمله.
 *
 * هذه الذاكرة **لا** تخزّن أي Bitmap — فقط قوائم [DocumentFile] ونماذج
 * [SlcdChapter] خفيفة، لذا استهلاكها للذاكرة ضئيل جداً ومحدود بسقف
 * [MAX_CHAPTER_ENTRIES] فصلاً (يُفرَّغ الأقدم تلقائياً بعده).
 */
object SlcdStructuralCache {

    private const val MAX_CHAPTER_ENTRIES = 16

    private data class ChapterKey(val seasonNumber: Int, val chapterNumber: Int)

    // LinkedHashMap بترتيب الدخول يسمح بتفريغ الأقدم بسهولة عند تجاوز السقف.
    private val chapterPagesCache = LinkedHashMap<ChapterKey, List<DocumentFile>>()
    private val seasonChaptersCache = LinkedHashMap<Int, List<SlcdChapter>>()

    // ───────────────────────── مانفست لوحات فصل ─────────────────────────

    /** القائمة الهيكلية للوحات فصل، إن كانت محفوظة سلفاً في الذاكرة، أو null. */
    @Synchronized
    fun cachedPages(seasonNumber: Int, chapterNumber: Int): List<DocumentFile>? =
        chapterPagesCache[ChapterKey(seasonNumber, chapterNumber)]

    @Synchronized
    private fun putPages(seasonNumber: Int, chapterNumber: Int, pages: List<DocumentFile>) {
        val key = ChapterKey(seasonNumber, chapterNumber)
        chapterPagesCache.remove(key) // نعيد إدراجه في النهاية (الأحدث استخداماً) إن كان موجوداً
        chapterPagesCache[key] = pages
        while (chapterPagesCache.size > MAX_CHAPTER_ENTRIES) {
            val oldest = chapterPagesCache.keys.firstOrNull() ?: break
            chapterPagesCache.remove(oldest)
        }
    }

    /**
     * يعيد القائمة الهيكلية للوحات فصل من الذاكرة إن وُجدت، وإلا يقرأها من
     * التخزين (خلفية `Dispatchers.IO`) ويحفظها للمرة القادمة. هذه هي نقطة
     * الدخول التي يجب أن تستخدمها شاشات القراءة بدل مناداة
     * [SlimeComicsRepository.listChapterPages] مباشرة.
     */
    suspend fun ensurePages(
        repository: SlimeComicsRepository,
        root: DocumentFile,
        seasonNumber: Int,
        chapterNumber: Int
    ): List<DocumentFile> {
        cachedPages(seasonNumber, chapterNumber)?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            repository.chapterFolder(root, seasonNumber, chapterNumber)
                ?.let { repository.listChapterPages(it) }
                ?: emptyList()
        }
        putPages(seasonNumber, chapterNumber, loaded)
        return loaded
    }

    /**
     * تحميل مسبق **صامت** (بلا انتظار من المستدعي) لمانفست فصل مجاور —
     * يُستدعى عادة عند اقتراب القارئ من حافة الفصل الحالي. لا يفعل شيئاً
     * إن كان المانفست محفوظاً أصلاً.
     */
    suspend fun prefetchPages(
        repository: SlimeComicsRepository,
        root: DocumentFile,
        seasonNumber: Int,
        chapterNumber: Int
    ) {
        if (cachedPages(seasonNumber, chapterNumber) != null) return
        ensurePages(repository, root, seasonNumber, chapterNumber)
    }

    /** يُستدعى بعد أي تعديل فعلي على ملفات فصل (إضافة/حذف لوحة) لإبطال مانفستها القديم. */
    @Synchronized
    fun invalidateChapter(seasonNumber: Int, chapterNumber: Int) {
        chapterPagesCache.remove(ChapterKey(seasonNumber, chapterNumber))
    }

    // ───────────────────────── فصول موسم كامل ─────────────────────────

    /** قائمة فصول موسم، إن كانت محفوظة سلفاً في الذاكرة، أو null. */
    @Synchronized
    fun cachedChapters(seasonNumber: Int): List<SlcdChapter>? = seasonChaptersCache[seasonNumber]

    /**
     * يعيد فصول موسم واحد من الذاكرة إن وُجدت، وإلا يحمّلها هيكلياً عبر
     * [SlimeComicsRepository.loadChaptersForSeason] (موسم واحد فقط، لا
     * المكتبة كاملة) ويحفظها. تُستخدم عند فتح بطاقة موسم في شاشة المكتبة.
     */
    suspend fun ensureChaptersForSeason(
        repository: SlimeComicsRepository,
        root: DocumentFile,
        seasonNumber: Int
    ): List<SlcdChapter> {
        cachedChapters(seasonNumber)?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            repository.loadChaptersForSeason(root, seasonNumber)
        }
        synchronized(this) { seasonChaptersCache[seasonNumber] = loaded }
        return loaded
    }

    /** يُستدعى بعد أي تعديل فعلي على فصول موسم (إضافة/حذف/إعادة ترتيب) لإبطال قائمته القديمة. */
    @Synchronized
    fun invalidateSeason(seasonNumber: Int) {
        seasonChaptersCache.remove(seasonNumber)
        // فصول ذلك الموسم لم تعد موثوقة بالضرورة (أرقام قد تكون أُعيد ترقيمها).
        chapterPagesCache.keys.filter { it.seasonNumber == seasonNumber }.forEach { chapterPagesCache.remove(it) }
    }

    /** يفرّغ الذاكرة الهيكلية بالكامل — عند تبديل/إلغاء تثبيت مكتبة SLCD مثلاً. */
    @Synchronized
    fun clear() {
        chapterPagesCache.clear()
        seasonChaptersCache.clear()
    }
}
