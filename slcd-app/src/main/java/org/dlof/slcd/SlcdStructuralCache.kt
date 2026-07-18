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
 * [maxChapterEntries] فصلاً (يُفرَّغ الأقدم تلقائياً بعده، وقابل للتعديل عبر [applyCacheLimit]).
 */
object SlcdStructuralCache {

    // إعداد متقدّم/مخفي (راجع SlcdSettings.structuralCacheLimit) — قيمة افتراضية
    // متطابقة مع السلوك القديم؛ يمكن تعديلها من شاشة الإعدادات المتقدّمة.
    private var maxChapterEntries = 16

    /** يُستدعى عند الإقلاع (أو بعد تعديل المستخدم للإعداد) لمزامنة السقف الفعلي مع [SlcdSettings]. */
    @Synchronized
    fun applyCacheLimit(limit: Int) {
        maxChapterEntries = limit.coerceIn(4, 64)
        while (chapterPagesCache.size > maxChapterEntries) {
            val oldest = chapterPagesCache.keys.firstOrNull() ?: break
            chapterPagesCache.remove(oldest)
        }
    }

    private data class ChapterKey(val seasonNumber: Int, val chapterNumber: Int, val wingNumber: Int? = null)

    // LinkedHashMap بترتيب الدخول يسمح بتفريغ الأقدم بسهولة عند تجاوز السقف.
    private val chapterPagesCache = LinkedHashMap<ChapterKey, List<DocumentFile>>()
    private val seasonChaptersCache = LinkedHashMap<Int, List<SlcdChapter>>()

    // ───────────────────────── مانفست لوحات فصل ─────────────────────────

    /** القائمة الهيكلية للوحات فصل (أو جناح واحد منه إن مُرِّر [wingNumber])، إن كانت محفوظة سلفاً في الذاكرة، أو null. */
    @Synchronized
    fun cachedPages(seasonNumber: Int, chapterNumber: Int, wingNumber: Int? = null): List<DocumentFile>? =
        chapterPagesCache[ChapterKey(seasonNumber, chapterNumber, wingNumber)]

    @Synchronized
    private fun putPages(seasonNumber: Int, chapterNumber: Int, wingNumber: Int?, pages: List<DocumentFile>) {
        val key = ChapterKey(seasonNumber, chapterNumber, wingNumber)
        chapterPagesCache.remove(key) // نعيد إدراجه في النهاية (الأحدث استخداماً) إن كان موجوداً
        chapterPagesCache[key] = pages
        while (chapterPagesCache.size > maxChapterEntries) {
            val oldest = chapterPagesCache.keys.firstOrNull() ?: break
            chapterPagesCache.remove(oldest)
        }
    }

    /**
     * يعيد القائمة الهيكلية للوحات فصل من الذاكرة إن وُجدت، وإلا يقرأها من
     * التخزين (خلفية `Dispatchers.IO`) ويحفظها للمرة القادمة. هذه هي نقطة
     * الدخول التي يجب أن تستخدمها شاشات القراءة بدل مناداة
     * [SlimeComicsRepository.listChapterPages] مباشرة.
     *
     * إن مُرِّر [wingNumber] تُحمَّل صفحات ذلك الجناح فقط (وحدة درامية
     * مستقلة ضمن نظام الأجنحة) بدل صفحات الفصل المباشرة كاملة.
     */
    suspend fun ensurePages(
        repository: SlimeComicsRepository,
        root: DocumentFile,
        seasonNumber: Int,
        chapterNumber: Int,
        wingNumber: Int? = null
    ): List<DocumentFile> {
        cachedPages(seasonNumber, chapterNumber, wingNumber)?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            val chapterDir = repository.chapterFolder(root, seasonNumber, chapterNumber)
            if (chapterDir == null) {
                emptyList()
            } else if (wingNumber != null) {
                repository.wingFolder(chapterDir, wingNumber)?.let { repository.listWingPages(it) } ?: emptyList()
            } else {
                repository.listChapterPages(chapterDir)
            }
        }
        putPages(seasonNumber, chapterNumber, wingNumber, loaded)
        return loaded
    }

    /**
     * تحميل مسبق **صامت** (بلا انتظار من المستدعي) لمانفست فصل أو جناح مجاور —
     * يُستدعى عادة عند اقتراب القارئ من حافة الفصل/الجناح الحالي. لا يفعل
     * شيئاً إن كان المانفست محفوظاً أصلاً.
     */
    suspend fun prefetchPages(
        repository: SlimeComicsRepository,
        root: DocumentFile,
        seasonNumber: Int,
        chapterNumber: Int,
        wingNumber: Int? = null
    ) {
        if (cachedPages(seasonNumber, chapterNumber, wingNumber) != null) return
        ensurePages(repository, root, seasonNumber, chapterNumber, wingNumber)
    }

    /** يُستدعى بعد أي تعديل فعلي على ملفات فصل (إضافة/حذف لوحة) لإبطال مانفستها القديم (بكل أجنحته أيضاً). */
    @Synchronized
    fun invalidateChapter(seasonNumber: Int, chapterNumber: Int) {
        chapterPagesCache.keys
            .filter { it.seasonNumber == seasonNumber && it.chapterNumber == chapterNumber }
            .forEach { chapterPagesCache.remove(it) }
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
