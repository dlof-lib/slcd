package org.dlof.slcd

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.dlof.reader.repo.DlofRepository
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ── مستودع Slime Comics dlof (SLCD) ───────────────────────────────────
 *
 * لا يعيد اختراع منطق حزم dlof: يبني فوق [DlofRepository] الموجود أصلاً
 * (createComicFromImages لإنشاء لوحات الفصل، exportAsSeriesPackage لتعبئة
 * مجلد كامل كحزمة ZIP) ويضيف فقط طبقة التنظيم الخاصة بـ SLCD: مجلدات
 * covers/coverN و seasons/seasonN/chapters/chapterM كما هي موصوفة في
 * spec/PACKAGE_FORMATS.md.
 *
 * إضافات هذا التحديث:
 *  - عناوين مخصّصة (title.txt داخل كل مجلد غلاف/موسم/فصل) تُقرأ وتُعرض
 *    وتُعاد تسميتها دون المساس بترقيم المجلدات (coverN/seasonN/chapterM
 *    يبقى ثابتاً لأنه أساس تسمية حزم .dlofpkg الناتجة).
 *  - حذف غلاف/موسم/فصل بالكامل.
 *  - إعادة ترتيب الأغلفة أو المواسم أو فصول موسم عبر السحب (يعيد ترقيم
 *    المجلدات فعلياً بحيث يبقى الترقيم مطابقاً للترتيب المعروض).
 *  - إيجاد أول صفحة `.dlof` في مجلد فصل لفتحها للقراءة مباشرة.
 */
class SlimeComicsRepository(private val context: Context) {

    private val dlofRepository = DlofRepository(context)

    companion object {
        const val DIR_COVERS = "covers"
        const val DIR_SEASONS = "seasons"
        const val DIR_CHAPTERS = "chapters"
        private const val TITLE_FILE = "title.txt"
        private const val DESCRIPTION_FILE = "description.txt"
        private const val GENRE_FILE = "genre.txt"
        private const val FAVORITE_FILE = ".slcd_favorite"
        private const val READ_FILE = ".slcd_read"

        /**
         * توقيع ثابت في بداية كل حزمة `.slcdpkg` — 8 بايت ASCII لا تطابق توقيع
         * ZIP العادي (`PK\u0003\u0004`) ولا أي توقيع آخر يعرفه [DlofRepository]
         * الرئيسي أو أي أداة فكّ ضغط عامة. هذا هو ما يجعل الحزمة "حصرية SLCD":
         * أي محاولة لفتحها كملف dlof/dlofpkg عادي أو كـ ZIP عادي تفشل فوراً منذ
         * أول 8 بايت، ولا تُفهم إلا عبر [packageChapterExclusive]/[importExclusivePackage]
         * هنا بالتحديد.
         */
        private val SLCD_MAGIC = byteArrayOf('S'.code.toByte(), 'L'.code.toByte(), 'C'.code.toByte(), 'D'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), 'G'.code.toByte(), '1'.code.toByte())
    }

    // ───────────────────────── تثبيت المكتبة ─────────────────────────

    /**
     * "تثبيت" SLCD: يأخذ صلاحية دائمة على مجلد الجذر الذي اختاره المستخدم
     * عبر منتقي المجلدات (SAF)، وينشئ بداخله covers/ و seasons/ إن لم
     * تكونا موجودتين. هذه هي خطوة التثبيت الحقيقية للأداة المصغّرة.
     */
    fun installLibrary(rootTreeUri: Uri): DocumentFile {
        try {
            context.contentResolver.takePersistableUriPermission(
                rootTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // بعض مزوّدي SAF لا يدعمون صلاحية دائمة؛ نتابع دون تعطيل التثبيت.
        }
        val root = DocumentFile.fromTreeUri(context, rootTreeUri)
            ?: throw IllegalStateException("مجلد المكتبة غير صالح")
        root.findFile(DIR_COVERS) ?: root.createDirectory(DIR_COVERS)
        root.findFile(DIR_SEASONS) ?: root.createDirectory(DIR_SEASONS)
        return root
    }

    fun openRoot(rootTreeUri: Uri): DocumentFile? = DocumentFile.fromTreeUri(context, rootTreeUri)

    // ───────────────────────── بانر الموسم وفيديو المقدّمة ─────────────────────────
    // ملفات حقيقية اختيارية يضعها المستخدم بنفسه داخل مجلد الموسم عبر أي تطبيق ملفات
    // (أو عبر مستكشف الملفات)؛ لا تُنشأ هذه الملفات تلقائياً، بل تُكتشَف فقط إن وُجدت.

    private const val INTRO_SEEN_FILE = ".slcd_intro_seen"

    /** أول ملف بأحد الامتدادات المُعطاة يطابق `baseName.*` داخل مجلد، أو null إن لم يوجد. */
    private fun findMediaFile(folder: DocumentFile, baseName: String, extensions: List<String>): DocumentFile? {
        return extensions.firstNotNullOfOrNull { ext -> folder.findFile("$baseName.$ext") }
    }

    /** بانر صورة/GIF ثابت للموسم (`banner.jpg`/`.png`/`.webp`/`.gif` داخل مجلد الموسم)، أو null. */
    fun seasonBannerImage(seasonFolder: DocumentFile): DocumentFile? =
        findMediaFile(seasonFolder, "banner", listOf("jpg", "jpeg", "png", "webp", "gif"))

    /** بانر فيديو متحرّك للموسم (`banner.mp4`/`.webm`)، له الأولوية على البانر الثابت إن وُجد. */
    fun seasonBannerVideo(seasonFolder: DocumentFile): DocumentFile? =
        findMediaFile(seasonFolder, "banner", listOf("mp4", "webm"))

    /** فيديو مقدّمة (تشويقي) يُعرض تلقائياً أول مرة يُفتح فيها الموسم (`intro.mp4`/`.webm`). */
    fun seasonIntroVideo(seasonFolder: DocumentFile): DocumentFile? =
        findMediaFile(seasonFolder, "intro", listOf("mp4", "webm"))

    /** هل شاهد المستخدم فيديو مقدّمة هذا الموسم من قبل؟ (علامة محفوظة داخل مجلد الموسم نفسه). */
    fun hasSeenSeasonIntro(seasonFolder: DocumentFile): Boolean = hasFlag(seasonFolder, INTRO_SEEN_FILE)

    /** يُسجَّل بعد انتهاء/تخطّي فيديو المقدّمة حتى لا يُعرض مجدداً في الزيارات القادمة. */
    fun markSeasonIntroSeen(seasonFolder: DocumentFile) = setFlag(seasonFolder, INTRO_SEEN_FILE, true)

    // ───────────────────────── عناوين مخصّصة (title.txt) ─────────────────────────

    /** قراءة عامة لأي ملف نصي مخفي داخل مجلد (عنوان/وصف/تصنيف...). */
    private fun readTextFile(folder: DocumentFile, fileName: String): String? {
        val file = folder.findFile(fileName) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** كتابة عامة لأي ملف نصي مخفي داخل مجلد؛ تحذف الملف إن كانت القيمة فارغة. */
    private fun writeTextFile(folder: DocumentFile, fileName: String, value: String) {
        val trimmed = value.trim()
        val existing = folder.findFile(fileName)
        if (trimmed.isBlank()) {
            existing?.delete()
            return
        }
        val target = existing ?: folder.createFile("text/plain", fileName) ?: return
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
            out.write(trimmed.toByteArray(Charsets.UTF_8))
        }
    }

    private fun readTitle(folder: DocumentFile): String? = readTextFile(folder, TITLE_FILE)

    private fun writeTitle(folder: DocumentFile, title: String) = writeTextFile(folder, TITLE_FILE, title)

    private fun readDescription(folder: DocumentFile): String? = readTextFile(folder, DESCRIPTION_FILE)

    private fun writeDescription(folder: DocumentFile, value: String) = writeTextFile(folder, DESCRIPTION_FILE, value)

    private fun readGenre(folder: DocumentFile): String? = readTextFile(folder, GENRE_FILE)

    private fun writeGenre(folder: DocumentFile, value: String) = writeTextFile(folder, GENRE_FILE, value)

    // ───────────────────────── علامات المفضّلة والمقروء ─────────────────────────

    private fun hasFlag(folder: DocumentFile, fileName: String): Boolean = folder.findFile(fileName) != null

    private fun setFlag(folder: DocumentFile, fileName: String, value: Boolean) {
        val existing = folder.findFile(fileName)
        if (value) {
            if (existing == null) folder.createFile("text/plain", fileName)
        } else {
            existing?.delete()
        }
    }

    /** يبدّل حالة "مفضّل" لفصل معيّن. */
    fun toggleChapterFavorite(root: DocumentFile, seasonNumber: Int, chapterNumber: Int) {
        val chapterDir = findChapterFolder(root, seasonNumber, chapterNumber) ?: return
        setFlag(chapterDir, FAVORITE_FILE, !hasFlag(chapterDir, FAVORITE_FILE))
    }

    /** يبدّل حالة "مقروء" لفصل معيّن (تُستخدم أيضاً تلقائياً عند فتح الفصل للقراءة). */
    fun toggleChapterRead(root: DocumentFile, seasonNumber: Int, chapterNumber: Int) {
        val chapterDir = findChapterFolder(root, seasonNumber, chapterNumber) ?: return
        setFlag(chapterDir, READ_FILE, !hasFlag(chapterDir, READ_FILE))
    }

    /** يضبط فصلاً كـ"مقروء" صراحة (بلا تبديل) — يُستدعى عند فتحه للقراءة أول مرة. */
    fun markChapterRead(root: DocumentFile, seasonNumber: Int, chapterNumber: Int) {
        val chapterDir = findChapterFolder(root, seasonNumber, chapterNumber) ?: return
        setFlag(chapterDir, READ_FILE, true)
    }

    private fun findChapterFolder(root: DocumentFile, seasonNumber: Int, chapterNumber: Int): DocumentFile? {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return null
        val seasonDir = seasonsDir.findFile("season$seasonNumber") ?: return null
        val chaptersDir = seasonDir.findFile(DIR_CHAPTERS) ?: return null
        return chaptersDir.findFile("chapter$chapterNumber")
    }

    /** نسخة عامة من [findChapterFolder] — تُستخدم من شاشة القراءة الجديدة [SlcdComicReaderScreen]. */
    fun chapterFolder(root: DocumentFile, seasonNumber: Int, chapterNumber: Int): DocumentFile? =
        findChapterFolder(root, seasonNumber, chapterNumber)

    /**
     * ── قراءة هيكلية للوحات الفصل ────────────────────────────────────
     *
     * يعيد كل ملفات `.dlofcomic` داخل مجلد الفصل مرتّبة أبجدياً (نفس ترتيب
     * page01, page02, ...)، كقائمة [DocumentFile] فقط — دون فتح أو فكّ أي
     * محتوى بعد. شاشة القراءة [SlcdComicReaderScreen] هي من تفتح كل صفحة
     * (عبر [openPageDocument]) بشكل كسول، صفحة بصفحة عند اقترابها من العرض
     * فقط، بدل تحميل كل لوحات الفصل دفعة واحدة في الذاكرة.
     */
    fun listChapterPages(chapterFolder: DocumentFile): List<DocumentFile> =
        chapterFolder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".dlofcomic") == true }
            .sortedBy { it.name }

    /** يفتح صفحة `.dlofcomic` واحدة ويحلّلها إلى مستند dlof (يحمل مرفق الصورة + النص المصاحب). */
    fun openPageDocument(uri: Uri) = dlofRepository.openDocument(uri)

    /** يفكّ بايتات المرفق المضمَّن base64 داخل صفحة — تُستخدم لبناء الـ Bitmap. */
    fun decodePageAttachmentBytes(attachment: org.dlof.reader.model.Attachment): ByteArray? =
        dlofRepository.decodeAttachmentBytes(attachment)

    // ───────────────────────── الأغلفة ─────────────────────────

    fun createCover(root: DocumentFile, number: Int, imageUri: Uri?): SlcdCover {
        val coversDir = root.findFile(DIR_COVERS) ?: root.createDirectory(DIR_COVERS)!!
        val coverDir = coversDir.findFile("cover$number") ?: coversDir.createDirectory("cover$number")!!
        var savedUri: Uri? = null
        if (imageUri != null) {
            val mime = context.contentResolver.getType(imageUri) ?: "image/jpeg"
            val ext = if (mime.contains("png")) "png" else "jpg"
            val target = coverDir.findFile("cover.$ext") ?: coverDir.createFile(mime, "cover.$ext")
            if (target != null) {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                        input.copyTo(output)
                    }
                }
                savedUri = target.uri
            }
        }
        return SlcdCover(number = number, folder = coverDir, imageUri = savedUri, title = readTitle(coverDir))
    }

    fun listCovers(root: DocumentFile): List<SlcdCover> {
        val coversDir = root.findFile(DIR_COVERS) ?: return emptyList()
        return coversDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith("cover") == true }
            .mapNotNull { dir ->
                val number = dir.name?.removePrefix("cover")?.toIntOrNull() ?: return@mapNotNull null
                val image = dir.listFiles().firstOrNull { it.name?.startsWith("cover.") == true }
                SlcdCover(number = number, folder = dir, imageUri = image?.uri, title = readTitle(dir))
            }
            .sortedBy { it.number }
    }

    fun renameCover(root: DocumentFile, coverNumber: Int, title: String) {
        val coversDir = root.findFile(DIR_COVERS) ?: return
        val coverDir = coversDir.findFile("cover$coverNumber") ?: return
        writeTitle(coverDir, title)
    }

    fun deleteCover(root: DocumentFile, coverNumber: Int) {
        val coversDir = root.findFile(DIR_COVERS) ?: return
        coversDir.findFile("cover$coverNumber")?.delete()
    }

    /**
     * يعيد ترتيب الأغلفة: [orderedCurrentNumbers] هي أرقام الأغلفة الحالية
     * مرتّبة بالترتيب الجديد المطلوب — يعيد ترقيم مجلداتها إلى 1..N تِبعاً
     * لهذا الترتيب (لضمان بقاء cover1 هو أول غلاف يُعرض دائماً).
     */
    fun reorderCovers(root: DocumentFile, orderedCurrentNumbers: List<Int>) {
        val coversDir = root.findFile(DIR_COVERS) ?: return
        renumberDirectories(coversDir, "cover", orderedCurrentNumbers)
    }

    // ───────────────────────── المواسم والفصول ─────────────────────────

    fun createSeason(root: DocumentFile, number: Int): DocumentFile {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: root.createDirectory(DIR_SEASONS)!!
        val seasonDir = seasonsDir.findFile("season$number") ?: seasonsDir.createDirectory("season$number")!!
        seasonDir.findFile(DIR_CHAPTERS) ?: seasonDir.createDirectory(DIR_CHAPTERS)
        return seasonDir
    }

    fun renameSeason(root: DocumentFile, seasonNumber: Int, title: String) {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return
        val seasonDir = seasonsDir.findFile("season$seasonNumber") ?: return
        writeTitle(seasonDir, title)
    }

    private fun findSeasonFolder(root: DocumentFile, seasonNumber: Int): DocumentFile? {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return null
        return seasonsDir.findFile("season$seasonNumber")
    }

    /** يحدّث وصف موسم معيّن (description.txt داخل مجلده). */
    fun setSeasonDescription(root: DocumentFile, seasonNumber: Int, description: String) {
        val seasonDir = findSeasonFolder(root, seasonNumber) ?: return
        writeDescription(seasonDir, description)
    }

    /** يحدّث تصنيف/نوع موسم معيّن (genre.txt داخل مجلده). */
    fun setSeasonGenre(root: DocumentFile, seasonNumber: Int, genre: String) {
        val seasonDir = findSeasonFolder(root, seasonNumber) ?: return
        writeGenre(seasonDir, genre)
    }

    // ───────────────────────── وصف وتصنيف القصة كاملة (مستوى المكتبة) ─────────────────────────

    /** وصف عام للقصة كاملة، محفوظ في description.txt بجذر المكتبة مباشرة. */
    fun libraryDescription(root: DocumentFile): String? = readDescription(root)

    /** تصنيف/نوع عام للقصة كاملة، محفوظ في genre.txt بجذر المكتبة مباشرة. */
    fun libraryGenre(root: DocumentFile): String? = readGenre(root)

    fun setLibraryDescription(root: DocumentFile, description: String) = writeDescription(root, description)

    fun setLibraryGenre(root: DocumentFile, genre: String) = writeGenre(root, genre)

    fun deleteSeason(root: DocumentFile, seasonNumber: Int) {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return
        seasonsDir.findFile("season$seasonNumber")?.delete()
    }

    fun reorderSeasons(root: DocumentFile, orderedCurrentNumbers: List<Int>) {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return
        renumberDirectories(seasonsDir, "season", orderedCurrentNumbers)
    }

    fun createChapterFolder(root: DocumentFile, seasonNumber: Int, chapterNumber: Int): DocumentFile {
        val seasonDir = createSeason(root, seasonNumber)
        val chaptersDir = seasonDir.findFile(DIR_CHAPTERS) ?: seasonDir.createDirectory(DIR_CHAPTERS)!!
        return chaptersDir.findFile("chapter$chapterNumber")
            ?: chaptersDir.createDirectory("chapter$chapterNumber")!!
    }

    fun renameChapter(root: DocumentFile, seasonNumber: Int, chapterNumber: Int, title: String) {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return
        val seasonDir = seasonsDir.findFile("season$seasonNumber") ?: return
        val chaptersDir = seasonDir.findFile(DIR_CHAPTERS) ?: return
        val chapterDir = chaptersDir.findFile("chapter$chapterNumber") ?: return
        writeTitle(chapterDir, title)
    }

    fun deleteChapter(root: DocumentFile, seasonNumber: Int, chapterNumber: Int) {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return
        val seasonDir = seasonsDir.findFile("season$seasonNumber") ?: return
        val chaptersDir = seasonDir.findFile(DIR_CHAPTERS) ?: return
        chaptersDir.findFile("chapter$chapterNumber")?.delete()
    }

    fun reorderChapters(root: DocumentFile, seasonNumber: Int, orderedCurrentNumbers: List<Int>) {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return
        val seasonDir = seasonsDir.findFile("season$seasonNumber") ?: return
        val chaptersDir = seasonDir.findFile(DIR_CHAPTERS) ?: return
        renumberDirectories(chaptersDir, "chapter", orderedCurrentNumbers)
    }

    /**
     * يعيد تسمية مجلدات فرعية مرقّمة بادئتها [prefix] (cover/season/chapter)
     * إلى ترقيم 1..N جديد يطابق [orderedCurrentNumbers] (أرقامها الحالية
     * مرتّبة بالترتيب الجديد المطلوب عرضه). يمرّ بخطوة تسمية مؤقتة أولاً
     * لتفادي تعارض الأسماء عند تبديل موضعين (مثال: تبديل season1↔season2).
     */
    private fun renumberDirectories(parentDir: DocumentFile, prefix: String, orderedCurrentNumbers: List<Int>) {
        data class Item(val dir: DocumentFile, val newNumber: Int)
        val items = orderedCurrentNumbers.mapIndexedNotNull { idx, num ->
            parentDir.findFile("$prefix$num")?.let { Item(it, idx + 1) }
        }
        if (items.isEmpty()) return
        val stamp = System.nanoTime()
        items.forEachIndexed { i, item -> item.dir.renameTo("${prefix}_tmp_${stamp}_$i") }
        items.forEach { item -> item.dir.renameTo("$prefix${item.newNumber}") }
    }

    /**
     * يضيف لوحات (صفحات) فصل من مجموعة صور، معتمداً بالكامل على
     * [DlofRepository.createComicFromImages] الموجودة أصلاً — فقط يوجّهها
     * إلى مجلد الفصل seasons/seasonN/chapters/chapterM بدل مجلد عام، ويحفظ
     * عنوان الفصل في title.txt بدل أن يُفقد بعد الإنشاء.
     */
    fun addChapterPages(
        root: DocumentFile,
        seasonNumber: Int,
        chapterNumber: Int,
        chapterTitle: String,
        pageImageUris: List<Uri>,
        captions: List<String> = emptyList()
    ): List<Uri> {
        val chapterFolder = createChapterFolder(root, seasonNumber, chapterNumber)
        val pages = dlofRepository.createComicFromImages(
            parentFolderUri = chapterFolder.uri,
            seriesTitle = chapterTitle,
            imageUris = pageImageUris,
            captions = captions
        )
        if (chapterTitle.isNotBlank()) {
            writeTitle(chapterFolder, chapterTitle)
        }
        return pages
    }

    /**
     * يعبّئ مجلد فصل كامل كحزمة `.dlofpkg` مستقلة (اسمها المقترح
     * Season<N>-Chapter<M>.dlofpkg) — كل فصل يبقى ملفاً واحداً قابلاً
     * للمشاركة، عبر [DlofRepository.exportAsSeriesPackage] الموجودة أصلاً.
     */
    fun packageChapter(
        chapterFolder: DocumentFile,
        seasonNumber: Int,
        chapterNumber: Int,
        destinationUri: Uri
    ) {
        dlofRepository.exportAsSeriesPackage(
            folder = chapterFolder,
            destinationUri = destinationUri,
            seriesName = "Season$seasonNumber-Chapter$chapterNumber"
        )
    }

    /**
     * ── تصدير حصري بصيغة `.slcdpkg` ────────────────────────────────────
     *
     * على عكس [packageChapter] (حزمة `.dlofpkg` عادية يفهمها عارض DLoF
     * الرئيسي)، هذه الدالة تكتب صيغة SLCD الحصرية: 8 بايت توقيع [SLCD_MAGIC]،
     * ثم طول ترويسة JSON (4 بايت big-endian)، ثم الترويسة نفسها (تحمل رقم
     * الموسم/الفصل الأصلي والعنوان وعدد الصفحات وتاريخ الإنشاء)، ثم محتوى
     * مجلد الفصل بالكامل مضغوطاً بصيغة ZIP قياسية.
     *
     * بما أن الملف الناتج لا يبدأ بتوقيع ZIP القياسي (`PK\u0003\u0004`) ولا
     * بأي توقيع يعرفه [DlofRepository.sniffPackageKind]، فإن عارض DLoF
     * الرئيسي — وأي أداة فكّ ضغط عامة — يفشل في التعرّف عليه من أول 8 بايت؛
     * الفتح والاستيراد الفعليان لا يحدثان إلا عبر [importExclusivePackage]
     * أو [peekExclusivePackage] هنا بالتحديد، أي حصراً من داخل SLCD.
     */
    fun packageChapterExclusive(
        chapterFolder: DocumentFile,
        seasonNumber: Int,
        chapterNumber: Int,
        title: String?,
        destinationUri: Uri
    ) {
        val allFiles = mutableListOf<Pair<String, DocumentFile>>()
        fun walk(dir: DocumentFile, relativePath: String) {
            dir.listFiles().forEach { child ->
                val childName = child.name ?: return@forEach
                val childRelative = if (relativePath.isEmpty()) childName else "$relativePath/$childName"
                if (child.isDirectory) walk(child, childRelative) else allFiles.add(childRelative to child)
            }
        }
        walk(chapterFolder, "")
        if (allFiles.isEmpty()) {
            throw IllegalStateException("هذا الفصل فارغ، لا توجد صفحات لتصديرها")
        }
        val pageCount = allFiles.count { it.first.endsWith(".dlofcomic") }

        val header = JSONObject().apply {
            put("origin_season", seasonNumber)
            put("origin_chapter", chapterNumber)
            put("title", title ?: JSONObject.NULL)
            put("page_count", pageCount)
            put("created_at", System.currentTimeMillis())
        }
        val headerBytes = header.toString().toByteArray(Charsets.UTF_8)

        val zipBytes = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
                allFiles.forEach { (relativePath, docFile) ->
                    zip.putNextEntry(ZipEntry(relativePath))
                    context.contentResolver.openInputStream(docFile.uri)?.use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }

        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
            out.write(SLCD_MAGIC)
            out.write(intToBigEndianBytes(headerBytes.size))
            out.write(headerBytes)
            out.write(zipBytes)
        } ?: throw IllegalStateException("تعذّر إنشاء ملف الحزمة")
    }

    private fun intToBigEndianBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
    )

    /** يقرأ ويتحقّق من ترويسة ملف مُختار دون فكّه بالكامل، أو null إن لم يكن ملف .slcdpkg صالحاً. */
    fun peekExclusivePackage(uri: Uri): SlcdPackageInfo? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val magic = ByteArray(8)
                if (input.read(magic) != 8 || !magic.contentEquals(SLCD_MAGIC)) return null
                val lenBytes = ByteArray(4)
                if (input.read(lenBytes) != 4) return null
                val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or ((lenBytes[1].toInt() and 0xFF) shl 16) or
                    ((lenBytes[2].toInt() and 0xFF) shl 8) or (lenBytes[3].toInt() and 0xFF)
                if (len <= 0 || len > 1_000_000) return null
                val headerBytes = ByteArray(len)
                if (input.read(headerBytes) != len) return null
                val json = JSONObject(String(headerBytes, Charsets.UTF_8))
                SlcdPackageInfo(
                    originSeasonNumber = json.optInt("origin_season", 0),
                    originChapterNumber = json.optInt("origin_chapter", 0),
                    title = if (json.isNull("title")) null else json.optString("title", null),
                    pageCount = json.optInt("page_count", 0),
                    createdAtMillis = json.optLong("created_at", 0L)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * يستورد حزمة `.slcdpkg` حصرية إلى داخل موسم [targetSeasonNumber] في
     * المكتبة الحالية، كفصل جديد برقم تلقائي تالٍ لأكبر فصل موجود في ذلك
     * الموسم. يرفض أي ملف لا يحمل توقيع SLCD الصحيح (يعيد null في هذه الحالة).
     */
    fun importExclusivePackage(uri: Uri, root: DocumentFile, targetSeasonNumber: Int): SlcdChapter? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size < 12) return null
        val magic = bytes.copyOfRange(0, 8)
        if (!magic.contentEquals(SLCD_MAGIC)) return null
        val lenBytes = bytes.copyOfRange(8, 12)
        val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or ((lenBytes[1].toInt() and 0xFF) shl 16) or
            ((lenBytes[2].toInt() and 0xFF) shl 8) or (lenBytes[3].toInt() and 0xFF)
        if (len <= 0 || 12 + len > bytes.size) return null
        val headerBytes = bytes.copyOfRange(12, 12 + len)
        val json = JSONObject(String(headerBytes, Charsets.UTF_8))
        val title = if (json.isNull("title")) null else json.optString("title", null)
        val zipBytes = bytes.copyOfRange(12 + len, bytes.size)

        val existingSeason = listSeasons(root).firstOrNull { it.number == targetSeasonNumber }
        val nextChapterNumber = (existingSeason?.chapters?.maxOfOrNull { it.chapterNumber } ?: 0) + 1
        val chapterFolder = createChapterFolder(root, targetSeasonNumber, nextChapterNumber)

        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.substringAfterLast('/')
                    if (name.isNotBlank()) {
                        val target = chapterFolder.findFile(name) ?: chapterFolder.createFile(
                            guessMimeFromName(name), name
                        )
                        target?.let { docFile ->
                            context.contentResolver.openOutputStream(docFile.uri, "wt")?.use { out ->
                                zip.copyTo(out)
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (!title.isNullOrBlank()) writeTitle(chapterFolder, title)

        val pageCount = chapterFolder.listFiles().count { it.name?.endsWith(".dlofcomic") == true }
        return SlcdChapter(
            seasonNumber = targetSeasonNumber,
            chapterNumber = nextChapterNumber,
            folder = chapterFolder,
            pageCount = pageCount,
            title = title
        )
    }

    private fun guessMimeFromName(name: String): String = when {
        name.endsWith(".dlofcomic") -> "application/octet-stream"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        name.endsWith(".json") || name.endsWith(".txt") -> "text/plain"
        else -> "application/octet-stream"
    }

    /** أول صفحة `.dlof` في مجلد فصل (مرتّبة أبجدياً: page01, page02, ...) لفتحها للقراءة مباشرة. */
    fun firstPageUri(chapterFolder: DocumentFile): Uri? {
        return chapterFolder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".dlofcomic") == true }
            .sortedBy { it.name }
            .firstOrNull()
            ?.uri
    }

    fun listSeasons(root: DocumentFile): List<SlcdSeason> {
        val seasonsDir = root.findFile(DIR_SEASONS) ?: return emptyList()
        return seasonsDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith("season") == true }
            .mapNotNull { seasonDir ->
                val seasonNumber = seasonDir.name?.removePrefix("season")?.toIntOrNull()
                    ?: return@mapNotNull null
                val chaptersDir = seasonDir.findFile(DIR_CHAPTERS)
                val chapters = chaptersDir?.listFiles()
                    ?.filter { it.isDirectory && it.name?.startsWith("chapter") == true }
                    ?.mapNotNull { chapterDir ->
                        val chapterNumber = chapterDir.name?.removePrefix("chapter")?.toIntOrNull()
                            ?: return@mapNotNull null
                        val pages = chapterDir.listFiles().count { it.name?.endsWith(".dlofcomic") == true }
                        val packaged = chapterDir.listFiles().any {
                            it.name?.endsWith(".slcdpkg") == true || it.name?.endsWith(".dlofpkg") == true
                        }
                        SlcdChapter(
                            seasonNumber = seasonNumber,
                            chapterNumber = chapterNumber,
                            folder = chapterDir,
                            pageCount = pages,
                            alreadyPackaged = packaged,
                            title = readTitle(chapterDir),
                            isFavorite = hasFlag(chapterDir, FAVORITE_FILE),
                            isRead = hasFlag(chapterDir, READ_FILE)
                        )
                    }
                    ?.sortedBy { it.chapterNumber }
                    ?: emptyList()
                SlcdSeason(
                    number = seasonNumber,
                    folder = seasonDir,
                    chapters = chapters,
                    title = readTitle(seasonDir),
                    description = readDescription(seasonDir),
                    genre = readGenre(seasonDir)
                )
            }
            .sortedBy { it.number }
    }

    fun loadLibrary(rootTreeUri: Uri): SlcdLibrary? {
        val root = openRoot(rootTreeUri) ?: return null
        return SlcdLibrary(
            root = root,
            covers = listCovers(root),
            seasons = listSeasons(root),
            description = libraryDescription(root),
            genre = libraryGenre(root)
        )
    }
}
