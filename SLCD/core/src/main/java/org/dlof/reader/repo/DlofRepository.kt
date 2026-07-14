package org.dlof.reader.repo

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import org.dlof.reader.crypto.DlofCrypto
import org.dlof.reader.model.Attachment
import org.dlof.reader.model.AttachmentKind
import org.dlof.reader.model.DlofContent
import org.dlof.reader.model.DlofDocument
import org.dlof.reader.model.Domain
import org.dlof.reader.model.FileEntry
import org.dlof.reader.model.LinkRef
import org.dlof.reader.model.LoopLinks
import org.dlof.reader.model.Metadata
import org.dlof.reader.model.RichBlock
import org.dlof.reader.model.Template
import org.dlof.reader.model.TemplatePackage
import org.dlof.reader.parser.DlofParser
import org.dlof.reader.template.TemplatePackageIO
import org.dlof.reader.validation.DiagnosticIssue
import org.dlof.reader.validation.DlofValidator
import org.dlof.reader.validation.LoopDiagnostics
import org.dlof.reader.validation.LoopNodeDiagnostics
import org.dlof.reader.validation.Severity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DlofRepository(private val context: Context) {

    data class LoadedDocument(val uri: Uri, val document: DlofDocument)

    /** أقصى حجم نسمح بتضمينه كـ base64 داخل ملف الـ DLoF نفسه (15 ميجابايت). */
    private val maxInlineSizeBytes = 15L * 1024 * 1024

    /**
     * يحوّل ملفاً اختاره المستخدم (صورة، فيديو، أو أي نوع آخر) إلى [Attachment]
     * مضمَّن بصيغة base64 داخل ملف الـ DLoF، حفاظاً على فلسفة "الملف المستقل بذاته".
     */
    fun createAttachmentFromUri(uri: Uri): Attachment {
        val resolver: ContentResolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val (fileName, size) = queryNameAndSize(uri)
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("تعذّر قراءة الملف المحدد")

        if (bytes.size.toLong() > maxInlineSizeBytes) {
            throw IllegalStateException("الملف أكبر من الحد المسموح به للتضمين (15 ميجابايت)")
        }

        val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return Attachment(
            id = "att-${UUID.randomUUID().toString().take(8)}",
            fileName = fileName,
            mimeType = mimeType,
            kind = AttachmentKind.fromMimeType(mimeType),
            data = base64Data,
            sizeBytes = size ?: bytes.size.toLong()
        )
    }

    /** يفكّ ترميز بيانات مرفق base64 إلى bytes خام (لعرض الصور أو حفظ الملفات مؤقتاً). */
    fun decodeAttachmentBytes(attachment: Attachment): ByteArray? =
        attachment.data?.let { Base64.decode(it, Base64.NO_WRAP) }

    /**
     * يستخرج صورة مصغرة (إطار) من فيديو عبر [Uri] ويعيدها كنص base64 مصغّر.
     * يُستخدم تلقائياً حين لا يختار المستخدم صورة مصغرة بنفسه عند إنشاء حلقة.
     * يعيد null إن تعذّر استخراج إطار (مزوّد لا يدعم القراءة العشوائية، فيديو تالف، إلخ).
     */
    fun extractVideoThumbnailBase64(uri: Uri, timeUs: Long = 1_000_000L): String? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            bitmapToThumbnailBase64(frame)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { /* تجاهل */ }
        }
    }

    /**
     * يتحقق من أن URI فيديو لا يزال قابلاً للوصول، ويُحاول تجديد الصلاحية إن أمكن.
     * يُستخدم قبل تشغيل الفيديو لتجنّب خطأ "Source error".
     * @return true إن كان الوصول ممكناً، false إن كانت الصلاحية منتهية أو الملف غير موجود.
     */
    fun checkAndRefreshVideoUri(uri: Uri): Boolean {
        return try {
            // محاولة تجديد الصلاحية الدائمة إن كانت content URI
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { /* ليست كل content URIs قابلة للـ persist */ }
            }
            // التحقق الفعلي عبر فتح stream
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** يحوّل صورة اختارها المستخدم (غلاف/صورة مصغرة) إلى base64 مصغّر ومضغوط بنفس قياس صور الفيديو. */
    fun encodePickedThumbnailBase64(uri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bitmapToThumbnailBase64(bitmap)
        } catch (_: Exception) {
            null
        }
    }

    /** يقيس الصورة إلى عرض أقصى 320px ويضغطها JPEG لتبقى الصورة المصغرة خفيفة داخل ملف الـ DLoF. */
    private fun bitmapToThumbnailBase64(bitmap: android.graphics.Bitmap): String {
        val maxDim = 320
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true
            )
        } else bitmap
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * ترتيب طبيعي (natural sort) لأسماء الملفات، بحيث تُرتَّب "حلقة2" قبل "حلقة10"
     * بدل الترتيب الأبجدي الحرفي الذي يضع "10" قبل "2". يُستخدم لترتيب حلقات
     * المسلسل تلقائياً حسب اسم ملف الفيديو عند رفعها.
     */
    val naturalOrderComparator: Comparator<String> = Comparator { a, b ->
        val regex = Regex("\\d+|\\D+")
        val tokensA = regex.findAll(a).map { it.value }.toList()
        val tokensB = regex.findAll(b).map { it.value }.toList()
        val size = maxOf(tokensA.size, tokensB.size)
        for (i in 0 until size) {
            val ta = tokensA.getOrNull(i) ?: return@Comparator -1
            val tb = tokensB.getOrNull(i) ?: return@Comparator 1
            val na = ta.toLongOrNull()
            val nb = tb.toLongOrNull()
            val cmp = if (na != null && nb != null) {
                na.compareTo(nb)
            } else {
                ta.lowercase().compareTo(tb.lowercase())
            }
            if (cmp != 0) return@Comparator cmp
        }
        0
    }

    /**
     * يعيد اسم العرض الحقيقي للملف (كما يراه المستخدم)، بغض النظر عن شكل الـ Uri.
     * أوثق من الاعتماد على نص الـ Uri نفسه، إذ بعض مزوّدي المحتوى (Google Drive، إلخ)
     * يستخدمون معرّفات رقمية معتمة في المسار لا تتضمن اسم الملف أو امتداده إطلاقاً.
     */
    fun queryDisplayName(uri: Uri): String? = queryNameAndSize(uri).first.takeIf { it != "ملف" }

    /** حجم الملف بالبايت كما يبلّغ عنه مزوّد المحتوى، إن توفّر (يُستخدم في شاشة الفحص/التنزيل). */
    fun queryFileSize(uri: Uri): Long? = queryNameAndSize(uri).second

    private fun queryNameAndSize(uri: Uri): Pair<String, Long?> {
        var name = "ملف"
        var size: Long? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    /** مجلد الوسائط الداخلي للتطبيق (فيديوهات/صور/ملفات محمّلة من قِبل المستخدم). */
    private val mediaDir: File by lazy {
        File(context.filesDir, "media").apply { mkdirs() }
    }

    /**
     * ينسخ ملفاً اختاره المستخدم (عبر Uri خارجي من content://) إلى مجلد media/
     * الداخلي للتطبيق، ويعيد Uri جديداً (عبر FileProvider) واسم الملف المحفوظ.
     * هذا يضمن بقاء الملف متاحاً حتى لو فقد الرابط الأصلي صلاحيته أو حُذف مصدره.
     * يعيد null في حال فشل النسخ (مثلاً تعذّر فتح الملف المصدر).
     */
    fun copyToMediaFolder(sourceUri: Uri, desiredName: String): Pair<Uri, String>? {
        return try {
            val safeName = desiredName.ifBlank { "ملف" }
            val destFile = uniqueMediaFile(safeName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", destFile
            )
            uri to destFile.name
        } catch (e: Exception) {
            null
        }
    }

    /** يولّد اسم ملف فريداً داخل مجلد media/ لتفادي الكتابة فوق ملف موجود بنفس الاسم. */
    private fun uniqueMediaFile(desiredName: String): File {
        var candidate = File(mediaDir, desiredName)
        if (!candidate.exists()) return candidate
        val dotIdx = desiredName.lastIndexOf('.')
        val base = if (dotIdx > 0) desiredName.substring(0, dotIdx) else desiredName
        val ext = if (dotIdx > 0) desiredName.substring(dotIdx) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(mediaDir, "$base-$i$ext")
            i++
        }
        return candidate
    }

    /** حجم ملف محفوظ في مجلد الوسائط الداخلي (بالبايت)، أو 0L إن تعذّر تحديده. */
    fun mediaFileSize(uri: Uri): Long =
        queryFileSize(uri) ?: run {
            val name = queryDisplayName(uri)
            val file = if (name != null) File(mediaDir, name) else null
            file?.takeIf { it.exists() }?.length() ?: 0L
        }

    // ───────────────────── حزم القوالب (.dlofTemplate) ─────────────────────

    /** مجلد مكتبة القوالب المستوردة محلياً، لاستردادها بسرعة دون استيراد الملف كل مرة. */
    private val templatesDir: File by lazy {
        File(context.filesDir, "templates").apply { mkdirs() }
    }

    /** يستورد حزمة قالب (.dlofTemplate) من Uri، ويحفظ نسخة منها في مكتبة القوالب المحلية. */
    fun importTemplatePackage(uri: Uri): TemplatePackage {
        val pkg = context.contentResolver.openInputStream(uri)?.use { input ->
            TemplatePackageIO.import(input)
        } ?: throw IllegalStateException("تعذّر فتح حزمة القالب")
        saveTemplatePackageLocally(pkg)
        return pkg
    }

    /** يحفظ حزمة قالب في المكتبة المحلية (templates/) لاستردادها لاحقاً من قائمة القوالب. */
    fun saveTemplatePackageLocally(pkg: TemplatePackage) {
        val file = File(templatesDir, "${pkg.id}.dlofTemplate")
        file.outputStream().use { TemplatePackageIO.export(pkg, it) }
    }

    /** يصدّر حزمة قالب إلى وجهة يحددها المستخدم (للمشاركة خارج التطبيق). */
    fun exportTemplatePackage(pkg: TemplatePackage, destinationUri: Uri) {
        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
            TemplatePackageIO.export(pkg, out)
        } ?: throw IllegalStateException("تعذّر تصدير حزمة القالب")
    }

    /** يسرد كل القوالب المحفوظة محلياً (المستوردة أو المُصدَّرة سابقاً) للاختيار منها مباشرة. */
    fun listSavedTemplatePackages(): List<TemplatePackage> =
        templatesDir.listFiles { f -> f.extension == "dlofTemplate" }
            ?.mapNotNull { file ->
                runCatching { file.inputStream().use { TemplatePackageIO.import(it) } }.getOrNull()
            }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun deleteSavedTemplatePackage(id: String) {
        File(templatesDir, "$id.dlofTemplate").delete()
    }

    // ───────────────────── فهرس الملفات (ملفاتي / المميز / المهملات) ─────────────────────

    private val fileIndexFile: File by lazy { File(context.filesDir, "file_index.json") }

    /** يُسجَّل عند فتح أو حفظ ملف بنجاح، ليظهر في شاشة "ملفاتي". */
    fun recordFileAccess(uri: Uri, title: String, domain: String, docId: String = "") {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // بعض المزوّدين لا يدعمون الصلاحية الدائمة — نتجاهل ونستمر
        }

        val entries = readFileIndex().toMutableList()
        val uriStr = uri.toString()
        val existing = entries.indexOfFirst { it.uriString == uriStr }
        val updated = FileEntry(
            uriString = uriStr,
            title = title.ifBlank { "بلا عنوان" },
            domain = domain,
            lastOpenedAt = System.currentTimeMillis(),
            isFavorite = entries.getOrNull(existing)?.isFavorite ?: false,
            isDeleted = false,
            deletedAt = null,
            docId = docId.ifBlank { entries.getOrNull(existing)?.docId ?: "" }
        )
        if (existing >= 0) entries[existing] = updated else entries.add(updated)
        writeFileIndex(entries)
    }

    /**
     * يبحث في فهرس الملفات المحلي عن ملف بمعرّف مستند مطابق (docId) —
     * يُستخدم عند استيراد رمز QR ممسوح (سواء بالكاميرا أو من صورة في المعرض)
     * لإيجاد الملف المطابق على الجهاز وفتحه مباشرة.
     */
    fun findFileByDocId(docId: String): FileEntry? {
        if (docId.isBlank()) return null
        return readFileIndex().firstOrNull { !it.isDeleted && it.docId == docId }
    }

    fun listFiles(): List<FileEntry> = readFileIndex().filter { !it.isDeleted }.sortedByDescending { it.lastOpenedAt }
    fun listFavorites(): List<FileEntry> = listFiles().filter { it.isFavorite }
    fun listTrash(): List<FileEntry> = readFileIndex().filter { it.isDeleted }.sortedByDescending { it.deletedAt ?: 0L }

    fun toggleFavorite(uriString: String) {
        updateEntry(uriString) { it.copy(isFavorite = !it.isFavorite) }
    }

    /** نقل إلى المهملات: لا يحذف الملف الفعلي، فقط يخفيه من القوائم الرئيسية ويُتيح استرجاعه. */
    fun moveToTrash(uriString: String) {
        updateEntry(uriString) { it.copy(isDeleted = true, deletedAt = System.currentTimeMillis(), isFavorite = false) }
    }

    fun restoreFromTrash(uriString: String) {
        updateEntry(uriString) { it.copy(isDeleted = false, deletedAt = null) }
    }

    /** حذف نهائي: يحاول حذف الملف الفعلي من التخزين، ثم يزيله من الفهرس بأي حال. */
    fun deletePermanently(uriString: String) {
        try {
            DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.delete()
        } catch (_: Exception) {
            // الملف قد يكون محذوفاً مسبقاً أو غير قابل للحذف من هنا — يكفي إزالته من الفهرس
        }
        val entries = readFileIndex().filterNot { it.uriString == uriString }
        writeFileIndex(entries)
    }

    private fun updateEntry(uriString: String, transform: (FileEntry) -> FileEntry) {
        val entries = readFileIndex().toMutableList()
        val idx = entries.indexOfFirst { it.uriString == uriString }
        if (idx >= 0) {
            entries[idx] = transform(entries[idx])
            writeFileIndex(entries)
        }
    }

    private fun readFileIndex(): List<FileEntry> {
        if (!fileIndexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(fileIndexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FileEntry(
                    uriString = o.getString("uri"),
                    title = o.optString("title", "بلا عنوان"),
                    domain = o.optString("domain", ""),
                    lastOpenedAt = o.optLong("lastOpenedAt", 0L),
                    isFavorite = o.optBoolean("isFavorite", false),
                    isDeleted = o.optBoolean("isDeleted", false),
                    deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.optLong("deletedAt") else null,
                    docId = o.optString("docId", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeFileIndex(entries: List<FileEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            val o = JSONObject()
            o.put("uri", e.uriString)
            o.put("title", e.title)
            o.put("domain", e.domain)
            o.put("lastOpenedAt", e.lastOpenedAt)
            o.put("isFavorite", e.isFavorite)
            o.put("isDeleted", e.isDeleted)
            if (e.deletedAt != null) o.put("deletedAt", e.deletedAt) else o.put("deletedAt", JSONObject.NULL)
            o.put("docId", e.docId)
            arr.put(o)
        }
        fileIndexFile.writeText(arr.toString())
    }

    fun openDocument(uri: Uri): LoadedDocument {
        context.contentResolver.openInputStream(uri)?.use { input ->
            return LoadedDocument(uri, DlofParser.parse(input))
        } ?: throw IllegalStateException("تعذّر فتح الملف")
    }

    fun parseXml(xml: String, uri: Uri): DlofDocument =
        DlofParser.parse(xml.byteInputStream(Charsets.UTF_8))

    fun saveDocument(uri: Uri, document: DlofDocument) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            DlofParser.write(document, output)
        } ?: throw IllegalStateException("تعذّر حفظ الملف")
    }

    fun saveEncryptedDocument(uri: Uri, document: DlofDocument, password: String) {
        val xmlBuffer = java.io.ByteArrayOutputStream()
        DlofParser.write(document, xmlBuffer)
        val encryptedBytes = DlofCrypto.encrypt(xmlBuffer.toString("UTF-8"), password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(encryptedBytes)
        } ?: throw IllegalStateException("تعذّر كتابة الملف المشفر")
    }

    fun createDocument(parentFolderUri: Uri, fileName: String, document: DlofDocument): Uri {
        val parent = DocumentFile.fromTreeUri(context, parentFolderUri)
            ?: throw IllegalStateException("مجلد الوجهة غير صالح")
        val finalName = if (fileName.endsWith(".dlof")) fileName else "$fileName.dlof"
        val newFile = parent.createFile("application/octet-stream", finalName)
            ?: throw IllegalStateException("تعذّر إنشاء الملف")
        saveDocument(newFile.uri, document)
        return newFile.uri
    }

    // ───────────────────── أداة: دمج المستندات في حلقة واحدة ─────────────────────

    /**
     * يدمج مجموعة ملفات .dlof مفتوحة مسبقاً (بأي ترتيب) في حلقة واحدة متصلة:
     * يضبط previous/next لكل ملف بحيث يشير لاسم عرض الملف الذي يسبقه/يليه،
     * ويعلّم أول ملف بـ loopRoot=true. يكتب التعديلات مباشرة فوق كل ملف
     * بمكانه الأصلي (لا حاجة لنقلها لمجلد واحد لأن resolveSibling يبحث في
     * الفهرس العام بحسب اسم العرض).
     */
    fun mergeDocumentsIntoLoop(uris: List<Uri>): List<LoadedDocument> {
        if (uris.size < 2) throw IllegalStateException("يلزم اختيار ملفين على الأقل للدمج")

        val loaded = uris.map { openDocument(it) }
        val names = loaded.map { l -> DocumentFile.fromSingleUri(context, l.uri)?.name ?: "${l.document.id}.dlof" }

        val merged = loaded.mapIndexed { index, l ->
            val prevRef = if (index > 0) LinkRef(ref = names[index - 1], title = loaded[index - 1].document.metadata.title) else null
            val nextRef = if (index < loaded.size - 1) LinkRef(ref = names[index + 1], title = loaded[index + 1].document.metadata.title) else null
            val newLinks = LoopLinks(previous = prevRef, next = nextRef, loopRoot = index == 0)
            l.copy(document = l.document.copy(loopLinks = newLinks))
        }

        merged.forEach { l -> saveDocument(l.uri, l.document) }
        return merged
    }

    // ───────────────────── أداة: تثبيت مستند كتطبيق (تصدير قالبه) ─────────────────────

    /**
     * "يثبّت" تصميم مستند حالي كتطبيق/قالب مستقل: يستخرج الألوان والخط
     * والتخطيط من [document.template] (أو تخطيط افتراضي إن لم يضبط المستخدم
     * تصميماً مسبقاً) ويحفظه كحزمة .dlofTemplate في مكتبة القوالب المحلية،
     * بحيث يصبح متاحاً فوراً من معرض القوالب لتطبيقه على أي ملف آخر.
     */
    fun pinDocumentAsTemplate(document: DlofDocument, appName: String): TemplatePackage {
        val baseTemplate = document.template ?: Template()
        val id = "pinned-${document.id.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "doc" }}-${System.currentTimeMillis().toString().takeLast(5)}"
        val pkg = TemplatePackage(
            id = id,
            name = appName.ifBlank { document.metadata.title },
            author = document.metadata.author,
            version = "1.0",
            template = baseTemplate.copy(ref = id)
        )
        saveTemplatePackageLocally(pkg)
        return pkg
    }

    // ───────────────────── أداة: تحويل صور إلى قصة مصورة (حلقة DLoF) ─────────────────────

    /**
     * يحوّل قائمة صور إلى "قصة مصورة": ملف .dlof واحد لكل صورة (مرفقة base64)
     * مع نص تعليق اختياري لكل لوحة، متصلة جميعاً كحلقة واحدة بالترتيب المُعطى
     * داخل مجلد الوجهة الذي يحدده المستخدم.
     */
    fun createComicFromImages(
        parentFolderUri: Uri,
        seriesTitle: String,
        imageUris: List<Uri>,
        captions: List<String>
    ): List<Uri> {
        if (imageUris.isEmpty()) throw IllegalStateException("يلزم اختيار صورة واحدة على الأقل")
        val parent = DocumentFile.fromTreeUri(context, parentFolderUri)
            ?: throw IllegalStateException("مجلد الوجهة غير صالح")

        val baseId = "comic-${System.currentTimeMillis()}"
        // امتداد .dlofcomic مخصص: يظهر بأيقونة واسم فرعي "DLoF — قصص مصورة"
        // في مربع "فتح باستخدام"، مع بقاء البنية الداخلية ملف .dlof عادياً.
        val fileNames = imageUris.indices.map { i -> "${baseId}-${i + 1}.dlofcomic" }

        val createdUris = mutableListOf<Uri>()
        imageUris.forEachIndexed { index, imgUri ->
            val attachment = createAttachmentFromUri(imgUri)
            val caption = captions.getOrNull(index).orEmpty()
            val doc = DlofDocument(
                id = "$baseId-${index + 1}",
                metadata = Metadata(
                    title = "$seriesTitle — لوحة ${index + 1}",
                    domain = Domain.CUSTOM,
                    tags = listOf("قصة مصورة")
                ),
                loopLinks = LoopLinks(
                    previous = if (index > 0) LinkRef(fileNames[index - 1]) else null,
                    next = if (index < imageUris.size - 1) LinkRef(fileNames[index + 1]) else null,
                    loopRoot = index == 0
                ),
                content = DlofContent.Generic(type = "comicPanel", element = "panel", body = caption),
                attachments = listOf(attachment.copy(caption = caption.ifBlank { null }))
            )
            val newFile = parent.createFile("application/octet-stream", fileNames[index])
                ?: throw IllegalStateException("تعذّر إنشاء ملف اللوحة ${index + 1}")
            saveDocument(newFile.uri, doc)
            createdUris.add(newFile.uri)
        }
        return createdUris
    }

    // ───────────────────── أداة: تحويل فيديوهات إلى مسلسل (حلقة DLoF) ─────────────────────

    /**
     * يحوّل قائمة فيديوهات إلى "مسلسل": ملف .dlof واحد لكل حلقة، يحمل
     * الفيديو كـ RichBlock.LargeVideoRef (بإشارة URI خارجية وليس base64،
     * لأن الفيديوهات قد تتجاوز حد التضمين)، متصلة كحلقة واحدة بالترتيب
     * المُعطى داخل مجلد الوجهة.
     */
    fun createSeriesFromVideos(
        parentFolderUri: Uri,
        seriesTitle: String,
        videoUris: List<Uri>,
        episodeTitles: List<String>,
        thumbnailUris: List<Uri?> = emptyList()
    ): List<Uri> {
        if (videoUris.isEmpty()) throw IllegalStateException("يلزم اختيار فيديو واحد على الأقل")
        val parent = DocumentFile.fromTreeUri(context, parentFolderUri)
            ?: throw IllegalStateException("مجلد الوجهة غير صالح")

        val baseId = "series-${System.currentTimeMillis()}"
        // امتداد .dlofvideo مخصص: يظهر بأيقونة واسم فرعي "DLoF — مسلسلات"
        // في مربع "فتح باستخدام"، مع بقاء البنية الداخلية ملف .dlof عادياً.
        val fileNames = videoUris.indices.map { i -> "${baseId}-ep${i + 1}.dlofvideo" }

        val createdUris = mutableListOf<Uri>()
        videoUris.forEachIndexed { index, videoUri ->
            val (fileName, size) = queryNameAndSize(videoUri)
            try {
                context.contentResolver.takePersistableUriPermission(
                    videoUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* بعض المزوّدين لا يدعمون صلاحية دائمة */ }

            // صورة مصغرة: إن اختار المستخدم صورة نستخدمها، وإلا نستخرج إطاراً من الفيديو تلقائياً.
            val pickedThumb = thumbnailUris.getOrNull(index)
            val thumbnailBase64 = pickedThumb?.let { encodePickedThumbnailBase64(it) }
                ?: extractVideoThumbnailBase64(videoUri)

            val episodeTitle = episodeTitles.getOrNull(index)?.ifBlank { null } ?: "الحلقة ${index + 1}"
            val videoBlock = RichBlock.LargeVideoRef(
                id = "vid-${index + 1}",
                uri = videoUri.toString(),
                fileName = fileName,
                sizeBytes = size,
                mimeType = context.contentResolver.getType(videoUri) ?: "video/mp4",
                caption = episodeTitle,
                thumbnailBase64 = thumbnailBase64
            )
            val doc = DlofDocument(
                id = "$baseId-ep${index + 1}",
                metadata = Metadata(
                    title = "$seriesTitle — $episodeTitle",
                    domain = Domain.CUSTOM,
                    tags = listOf("مسلسل")
                ),
                loopLinks = LoopLinks(
                    previous = if (index > 0) LinkRef(fileNames[index - 1]) else null,
                    next = if (index < videoUris.size - 1) LinkRef(fileNames[index + 1]) else null,
                    loopRoot = index == 0
                ),
                content = DlofContent.Generic(type = "episode", element = "video", body = episodeTitle),
                richBlocks = listOf(videoBlock)
            )
            val newFile = parent.createFile("application/octet-stream", fileNames[index])
                ?: throw IllegalStateException("تعذّر إنشاء ملف الحلقة ${index + 1}")
            saveDocument(newFile.uri, doc)
            createdUris.add(newFile.uri)
        }
        return createdUris
    }

    fun collectFullLoop(startUri: Uri): List<LoadedDocument> {
        val visited = LinkedHashMap<Uri, LoadedDocument>()
        val backward = mutableListOf<LoadedDocument>()
        var cursor: Uri? = startUri
        val backwardVisited = mutableSetOf<Uri>()
        while (cursor != null && cursor !in backwardVisited) {
            backwardVisited.add(cursor)
            val loaded = openDocument(cursor)
            backward.add(loaded)
            cursor = loaded.document.loopLinks.previous?.ref?.let { resolveSibling(loaded.uri, it) }
        }
        backward.reverse()
        backward.forEach { visited[it.uri] = it }
        val lastBackward = backward.last()
        var forwardCursor: Uri? = lastBackward.document.loopLinks.next?.let { resolveSibling(lastBackward.uri, it.ref) }
        while (forwardCursor != null && forwardCursor !in visited) {
            val loaded = openDocument(forwardCursor)
            visited[loaded.uri] = loaded
            forwardCursor = loaded.document.loopLinks.next?.let { resolveSibling(loaded.uri, it.ref) }
        }
        return visited.values.toList()
    }

    fun exportLoopAsBundle(startUri: Uri, destinationUri: Uri) {
        val documents = collectFullLoop(startUri)
        if (documents.isEmpty()) throw IllegalStateException("لم يُعثر على أي مستندات في الحلقة")
        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { rawOut ->
            java.util.zip.ZipOutputStream(rawOut).use { zipOut ->
                zipOut.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
                documents.forEach { loaded ->
                    val entryName = DocumentFile.fromSingleUri(context, loaded.uri)?.name ?: "${loaded.document.id}.dlof"
                    zipOut.putNextEntry(java.util.zip.ZipEntry(entryName))
                    DlofParser.write(loaded.document, zipOut)
                    zipOut.closeEntry()
                }
            }
        } ?: throw IllegalStateException("تعذّر إنشاء أرشيف الحلقة")
    }

    /**
     * يفحص ملفاً واحداً فقط (دون تتبّع الحلقة): بنية المحتوى، الألوان، المرفقات.
     * يُستخدم لزر "فحص هذا الملف" السريع داخل شاشة العرض.
     */
    fun validateSingleDocument(document: DlofDocument) = DlofValidator.validateDocument(document)

    /**
     * يفحص الحلقة كاملة بدءاً من [startUri]: يتتبّع previous ثم next عبر
     * [resolveSibling]، يفحص كل ملف على طريقه بـ [DlofValidator]، ويكتشف:
     * - الروابط المكسورة: ref يُشير لملف تعذّر العثور عليه (ولا يوجد في الفهرس).
     * - الحلقة الدائرية: نعود إلى URI زُرناه سابقاً قبل انتهاء السلسلة طبيعياً.
     *
     * بعكس [collectFullLoop] (المستخدمة في التصدير)، هذه الدالة لا تتوقف بصمت
     * عند أول رابط مكسور أو حلقة دائرية، بل تُسجّلها في النتيجة لعرضها للمستخدم.
     */
    fun validateLoop(startUri: Uri): LoopDiagnostics {
        val nodes = mutableListOf<LoopNodeDiagnostics>()
        val brokenLinks = mutableListOf<String>()
        var hasCycle = false

        // ── المرحلة 1: نرجع للخلف حتى بداية الحلقة (أو نكتشف دائرية) ──
        val backwardVisited = LinkedHashSet<Uri>()
        val backwardDocs = mutableListOf<LoadedDocument>()
        var cursor: Uri? = startUri
        while (cursor != null) {
            if (cursor in backwardVisited) {
                hasCycle = true
                break
            }
            backwardVisited.add(cursor)
            val loaded = try {
                openDocument(cursor)
            } catch (e: Exception) {
                brokenLinks.add("تعذّر فتح ملف في الحلقة: ${e.message ?: "خطأ غير معروف"}")
                break
            }
            backwardDocs.add(loaded)
            val prevRef = loaded.document.loopLinks.previous?.ref
            cursor = if (prevRef == null) null else {
                val resolved = resolveSibling(loaded.uri, prevRef)
                if (resolved == null) brokenLinks.add(prevRef)
                resolved
            }
        }
        backwardDocs.reverse()

        // ── المرحلة 2: من بداية الحلقة، نمضي للأمام ونفحص كل ملف ──
        val forwardVisited = LinkedHashSet<Uri>()
        backwardDocs.forEach { loaded ->
            if (loaded.uri in forwardVisited) {
                hasCycle = true
                return@forEach
            }
            forwardVisited.add(loaded.uri)
            nodes.add(buildNodeDiagnostics(loaded, brokenLinks))
        }

        var forwardCursor: Uri? = backwardDocs.lastOrNull()?.let { last ->
            val nextRef = last.document.loopLinks.next?.ref
            if (nextRef == null) null else {
                val resolved = resolveSibling(last.uri, nextRef)
                if (resolved == null) brokenLinks.add(nextRef)
                resolved
            }
        }
        while (forwardCursor != null) {
            if (forwardCursor in forwardVisited) {
                hasCycle = true
                break
            }
            val loaded = try {
                openDocument(forwardCursor)
            } catch (e: Exception) {
                brokenLinks.add("تعذّر فتح ملف في الحلقة: ${e.message ?: "خطأ غير معروف"}")
                break
            }
            forwardVisited.add(loaded.uri)
            nodes.add(buildNodeDiagnostics(loaded, brokenLinks))
            val nextRef = loaded.document.loopLinks.next?.ref
            forwardCursor = if (nextRef == null) null else {
                val resolved = resolveSibling(loaded.uri, nextRef)
                if (resolved == null) brokenLinks.add(nextRef)
                resolved
            }
        }

        return LoopDiagnostics(
            nodes = nodes,
            hasCycle = hasCycle,
            brokenLinks = brokenLinks.distinct()
        )
    }

    private fun buildNodeDiagnostics(loaded: LoadedDocument, brokenLinksSink: MutableList<String>): LoopNodeDiagnostics {
        val diagnostics = DlofValidator.validateDocument(loaded.document)
        val nextRef = loaded.document.loopLinks.next?.ref
        val prevRef = loaded.document.loopLinks.previous?.ref
        val nextResolved = nextRef == null || resolveSibling(loaded.uri, nextRef) != null
        val previousResolved = prevRef == null || resolveSibling(loaded.uri, prevRef) != null
        return LoopNodeDiagnostics(
            uriString = loaded.uri.toString(),
            diagnostics = diagnostics,
            nextResolved = nextResolved,
            previousResolved = previousResolved
        )
    }

    /**
     * يبحث عن ملف .dlof "شقيق" مرتبط بـ [relativeRef] (مثل "howto-02.dlof" الواردة
     * في loopLinks.next/previous)، انطلاقاً من الملف الحالي [currentUri].
     *
     * المحاولة الأصلية كانت تبني الـ URI الشقيق بالتخمين عبر
     * DocumentsContract.buildDocumentUriUsingTree، وهي دالة مخصّصة لـ Tree URIs
     * (القادمة من OpenDocumentTree فقط). بما أن التطبيق يفتح الملفات عبر
     * OpenDocument() — التي تُرجع Single-Document URI، لا Tree URI — كانت هذه
     * المحاولة تفشل بنيوياً دائماً مع أي ملف فُتح من الشاشة الرئيسية، وهو
     * ما تسبب بخطأ "لم يُعثر على الملف المرتبط" حتى مع وجود الملفات في نفس المجلد.
     *
     * الحل: مطابقة اسم العرض (displayName) لكل ملف مسجَّل مسبقاً في فهرس
     * الملفات المحلي (الذي يحتفظ بصلاحية قراءة دائمة عبر takePersistableUriPermission
     * لكل ملف فُتح أو أُنشئ من قبل) — وهذا يعمل بغض النظر عن نوع مزوّد الملفات.
     */
    fun resolveSibling(currentUri: Uri, relativeRef: String): Uri? {
        val targetName = relativeRef.substringAfterLast('/')

        // 1) المحاولة الأساسية والموثوقة: مطابقة اسم العرض داخل فهرس الملفات
        //    المسجَّلة (كل الملفات المفتوحة سابقاً تحتفظ بصلاحية قراءة دائمة).
        readFileIndex().forEach { entry ->
            try {
                val entryUri = Uri.parse(entry.uriString)
                val displayName = DocumentFile.fromSingleUri(context, entryUri)?.name
                if (displayName != null && displayName.equals(targetName, ignoreCase = true)) {
                    context.contentResolver.openInputStream(entryUri)?.use { return entryUri }
                }
            } catch (_: Exception) {
                // إدخال فهرس تالف أو ملف لم يعد متاحاً — نتجاوزه ونتابع البحث
            }
        }

        // 2) محاولة احتياطية: البحث ضمن "ملفاتي" غير المسجّلة بعد (نادر، لكن غير مكلف)
        //    عبر استنتاج مجلد أصل الملف الحالي إن كان متاحاً كـ Tree URI فعلياً.
        try {
            val docId = DocumentFile.fromSingleUri(context, currentUri)?.uri?.lastPathSegment
            if (docId != null && docId.contains('/')) {
                val siblingDocId = "${docId.substringBeforeLast('/')}/$targetName"
                val siblingUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(currentUri, siblingDocId)
                context.contentResolver.openInputStream(siblingUri)?.use { return siblingUri }
            }
        } catch (_: Exception) {}

        return null
    }

    // ───────────────────── مجلدات مدير الملفات (Tree URIs محفوظة) ─────────────────────

    private val deviceFoldersIndexFile: File by lazy { File(context.filesDir, "device_folders.json") }

    /**
     * يحفظ مجلداً اختاره المستخدم عبر منتقي النظام (OpenDocumentTree) ضمن قائمة
     * المجلدات المختصرة في مدير الملفات، مع أخذ صلاحية وصول دائمة له.
     */
    fun addDeviceFolder(treeUri: Uri, label: String) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // بعض المزوّدين لا يدعمون صلاحية دائمة على Tree URI
        }
        val entries = readDeviceFolders().toMutableList()
        val uriStr = treeUri.toString()
        if (entries.none { it.uriString == uriStr }) {
            entries.add(0, DeviceFolderBookmark(uriStr, label))
            writeDeviceFolders(entries)
        }
    }

    fun listDeviceFolders(): List<DeviceFolderBookmark> = readDeviceFolders()

    fun removeDeviceFolder(uriString: String) {
        writeDeviceFolders(readDeviceFolders().filterNot { it.uriString == uriString })
    }

    private fun readDeviceFolders(): List<DeviceFolderBookmark> {
        if (!deviceFoldersIndexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(deviceFoldersIndexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DeviceFolderBookmark(o.getString("uriString"), o.getString("label"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeDeviceFolders(entries: List<DeviceFolderBookmark>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("uriString", e.uriString)
                put("label", e.label)
            })
        }
        deviceFoldersIndexFile.writeText(arr.toString())
    }

    // ══════════════════════════════════════════════════════════════
    // حزمة سلسلة  .dlofSeries  —  تثبيت وتصدير
    // ══════════════════════════════════════════════════════════════

    /**
     * يثبّت حزمة `.dlofSeries` المستلمة عبر [uri].
     *
     * هيكل الحزمة الداخلي (ZIP):
     *   SeriesName/
     *   ├── series-index.dlof
     *   ├── ep01.dlof
     *   ├── ep02.dlof
     *   ├── ...
     *   ├── set.txt            (اختياري)
     *   ├── characters.dlof    (اختياري)
     *   ├── fonts/             (اختياري)
     *   └── media/             (اختياري)
     *       ├── images/
     *       ├── videos/
     *       ├── audio/
     *       └── subtitles/
     *
     * يُستخرج المحتوى إلى:
     *   Context.filesDir/series/<SeriesName>/
     */
    /** نوع الحزمة المكتشَف من محتوى أرشيف ZIP نفسه، بمعزل عن امتداد اسم الملف. */
    enum class PackageKind { SERIES, SINGLE, UNKNOWN }

    /**
     * "فاحص فكّ الضغط" — يتحقق أولاً من توقيع ZIP الثنائي (PK\x03\x04)، ثم يتصفّح
     * أسماء مدخلات الأرشيف (دون فك ضغط كامل المحتوى) ليقرر نوع الحزمة:
     *   - وجود series-index.dlof  → حزمة سلسلة كاملة (.dlofSeries)
     *   - وجود package.dlof/meta.json → حزمة ملف فردي (.dlofpkg)
     *   - أي محتوى ZIP آخر يحوي ملفات .dlof → يُعامَل كسلسلة افتراضياً
     * يُستخدم هذا كخط دفاع أخير حين لا يمكن الوثوق بامتداد اسم الملف القادم
     * من مدير ملفات خارجي (حالة شائعة تجعل حزم dlof "لا تُفتح" رغم صحتها).
     */
    fun sniffPackageKind(bytes: ByteArray): PackageKind {
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
            return PackageKind.UNKNOWN
        }
        return try {
            var hasSeriesIndex = false
            var hasSingleMarker = false
            var hasAnyDlof = false
            java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val leaf = entry.name.substringAfterLast('/')
                    when {
                        leaf.equals("series-index.dlof", ignoreCase = true) -> hasSeriesIndex = true
                        leaf.equals("package.dlof", ignoreCase = true) -> hasSingleMarker = true
                        leaf.equals("meta.json", ignoreCase = true) -> hasSingleMarker = true
                        leaf.endsWith(".dlof", ignoreCase = true) -> hasAnyDlof = true
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            when {
                hasSeriesIndex -> PackageKind.SERIES
                hasSingleMarker -> PackageKind.SINGLE
                hasAnyDlof -> PackageKind.SERIES
                else -> PackageKind.UNKNOWN
            }
        } catch (_: Exception) {
            PackageKind.UNKNOWN
        }
    }

    fun installSeriesPackage(uri: Uri): InstallResult {
        val resolver = context.contentResolver
        val zipBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return InstallResult.Failure("تعذّر قراءة ملف الحزمة")

        return try {
            val zipStream = java.util.zip.ZipInputStream(zipBytes.inputStream())
            val installedFiles = mutableListOf<String>()
            var mediaCount = 0

            // استنتج اسم السلسلة من أول مدخل في الـ ZIP
            var seriesName: String? = null
            val seriesRoot = java.io.File(context.filesDir, "series")

            val allEntries = mutableListOf<Pair<String, ByteArray>>()
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val data = zipStream.readBytes()
                    allEntries.add(entry.name to data)
                    if (seriesName == null) {
                        // أول مكوّن من المسار هو اسم السلسلة
                        seriesName = entry.name.split("/").firstOrNull()?.takeIf { it.isNotBlank() }
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

            val dirName = seriesName ?: "UnknownSeries"
            val installDir = java.io.File(seriesRoot, dirName).also { it.mkdirs() }

            for ((name, data) in allEntries) {
                // أزل مكوّن اسم السلسلة من المسار (نُثبّت داخل installDir)
                val relativePath = if (name.startsWith("$dirName/"))
                    name.removePrefix("$dirName/") else name
                val outFile = java.io.File(installDir, relativePath)
                outFile.parentFile?.mkdirs()
                outFile.writeBytes(data)
                installedFiles.add(relativePath)
                val lower = relativePath.lowercase()
                if (lower.startsWith("media/")) mediaCount++
            }

            InstallResult.Success(
                installedFiles,
                mediaCount,
                installDir,
                structureReport = try {
                    org.dlof.reader.lighthouse.DocumentFileHelper.checkPackage(installDir)
                } catch (_: Exception) {
                    null
                }
            )
        } catch (e: Exception) {
            InstallResult.Failure("خطأ أثناء تثبيت الحزمة: ${e.message}")
        }
    }

    /**
     * يصدّر حلقة كاملة كحزمة `.dlofSeries` (ZIP).
     *
     * يجمع كل ملفات الحلقة + ملفات set.txt/characters.dlof/fonts/media
     * إن كانت موجودة بجانب ملف البداية، ويحزمها داخل ZIP بنفس الهيكل.
     */
    /**
     * يصدّر مجلد سلسلة كاملاً كحزمة `.dlofSeries` واحدة (ZIP).
     *
     * ── الخطأ السابق ──
     * كانت هذه الدالة تستقبل uri لملف dlof واحد فقط (عادة series-index.dlof)
     * ثم تعيد بناء قائمة الملفات عبر تتبّع loopLinks.previous/next
     * (collectFullLoop) ومطابقة أسماء الأشقاء (resolveSibling) لِـ
     * set.txt و characters.dlof حصراً. هذه الآلية لا تعرف شيئاً عن
     * media/images|videos|audio|subtitles ولا عن fonts/ — فكانت هذه
     * المجلدات تُستبعد دائماً من الحزمة المُصدَّرة رغم أنها موثّقة في
     * docs/SPECIFICATION.md كجزء أساسي من بنية السلسلة.
     *
     * ── الحل ──
     * الشاشة المستدعية (DeviceFileBrowserScreen) تملك أصلاً [folder] كـ
     * DocumentFile كامل الصلاحية (Tree URI عبر SAF)، لذا نستقبله مباشرة
     * ونمشي عليه تكرارياً (recursive) فننسخ كل ملف بمساره النسبي كما هو
     * — بما يشمل media/ وfonts/ وseries-index.dlof وset.txt وcharacters.dlof
     * وأي ملفات أخرى، بدل الاعتماد على تتبّع الحلقة المنطقية.
     */
    fun exportAsSeriesPackage(folder: DocumentFile, destinationUri: Uri, seriesName: String = "MySeries") {
        val allFiles = mutableListOf<Pair<String, DocumentFile>>()

        fun walk(dir: DocumentFile, relativePath: String) {
            dir.listFiles().forEach { child ->
                val childName = child.name ?: return@forEach
                val childRelative = if (relativePath.isEmpty()) childName else "$relativePath/$childName"
                if (child.isDirectory) {
                    walk(child, childRelative)
                } else {
                    allFiles.add(childRelative to child)
                }
            }
        }
        walk(folder, "")

        if (allFiles.isEmpty()) {
            throw IllegalStateException("المجلد \"${folder.name ?: seriesName}\" فارغ، لا توجد ملفات لتصديرها")
        }

        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { rawOut ->
            java.util.zip.ZipOutputStream(rawOut).use { zip ->
                zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
                allFiles.forEach { (relativePath, docFile) ->
                    zip.putNextEntry(java.util.zip.ZipEntry("$seriesName/$relativePath"))
                    context.contentResolver.openInputStream(docFile.uri)?.use { ins ->
                        ins.copyTo(zip)
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("تعذّر إنشاء ملف الحزمة")
    }

    // ══════════════════════════════════════════════════════════════
    // حزمة ملف فردي  .dlofpkg  —  تثبيت وتصدير
    // ══════════════════════════════════════════════════════════════

    /**
     * يثبّت حزمة `.dlofpkg` — ملف dlof واحد مع مرفقاته.
     *
     * هيكل الحزمة (ZIP):
     *   package.dlof          ← ملف المحتوى الرئيسي
     *   meta.json             ← بيانات وصفية (id، title، domain، version)
     *   attachments/          ← ملفات الوسائط المرفقة (اختياري)
     *     image.png
     *     audio.mp3
     *     ...
     *
     * يُثبَّت في:
     *   Context.filesDir/packages/<id>/
     */
    // ══════════════════════════════════════════════════════════════
    // مرحلة الفحص (Scan) — تُنفَّذ قبل أي كتابة على القرص
    // ══════════════════════════════════════════════════════════════

    /** ملف واحد داخل الأرشيف بعد قراءته والتحقق من سلامته (CRC) بالكامل في الذاكرة. */
    data class ScannedEntry(val name: String, val sizeBytes: Long, val data: ByteArray)

    /** نتيجة فحص محتوى set.txt (إن وُجد داخل الحزمة) — لا يُعتبر خطأً قاطعاً لأنه اختياري. */
    data class SetTxtCheck(val present: Boolean, val validPairs: Int, val malformedLines: Int)

    /** نتيجة مرحلة فحص الحزمة بالكامل، قبل الانتقال لمرحلة التثبيت (التنزيل الفعلي على القرص). */
    sealed class PackageScanResult {
        data class Success(
            val kind: PackageKind,
            val entries: List<ScannedEntry>,
            val totalBytes: Long,
            val setTxt: SetTxtCheck?,
            val warnings: List<String>,
            /** تقرير فحص set.txt الدقيق (نوع القيم، مفاتيح مكرّرة/غير معروفة...) — null إن لم يوجد set.txt. */
            val setTxtValidation: org.dlof.reader.model.SeriesSettingsLoader.SetTxtValidation? = null,
            /** اسم المدخل المشفَّر (package.dlof أو series-index.dlof) إن كانت الحزمة محمية بكلمة مرور، أو null إن لم تكن محمية. */
            val protectedEntryName: String? = null
        ) : PackageScanResult() {
            /** هل هذه الحزمة محمية أو مشفّرة وتحتاج كلمة مرور قبل التثبيت والفتح؟ */
            val isProtected: Boolean get() = protectedEntryName != null
        }

        data class Failure(val reason: String, val isNetworkError: Boolean = false) : PackageScanResult()
    }

    /**
     * يفك تشفير المدخل المحمي داخل [scan] (المُشار إليه بـ [PackageScanResult.Success.protectedEntryName])
     * باستخدام [password] ويُعيد نسخة جديدة من نتيجة الفحص بعد استبدال محتوى ذلك المدخل
     * بالـ XML الصريح فك تشفيره، بحيث تتابع مراحل التثبيت والفتح عملها بشكل طبيعي.
     *
     * @throws org.dlof.reader.crypto.DlofCrypto.CryptoException إن كانت كلمة المرور خاطئة
     */
    fun decryptProtectedScan(
        scan: PackageScanResult.Success,
        password: String
    ): PackageScanResult.Success {
        val targetName = scan.protectedEntryName
            ?: return scan // ليست محمية أصلاً — لا شيء لفعله
        val targetEntry = scan.entries.first { it.name == targetName }
        val decryptedXml = org.dlof.reader.crypto.DlofCrypto.decrypt(targetEntry.data, password)
        val decryptedBytes = decryptedXml.toByteArray(Charsets.UTF_8)
        val newEntries = scan.entries.map { entry ->
            if (entry.name == targetName) entry.copy(sizeBytes = decryptedBytes.size.toLong(), data = decryptedBytes)
            else entry
        }
        val newTotal = newEntries.sumOf { it.sizeBytes }
        return scan.copy(entries = newEntries, totalBytes = newTotal, protectedEntryName = null)
    }

    /**
     * يفحص حزمة `.dlofpkg` أو `.dlofSeries` بالكامل *قبل* تثبيتها:
     *  1) يقرأ محتوى الملف الخام من الـ Uri المصدر.
     *  2) يتحقق من توقيع ZIP، ثم يمشي على كل مدخل ويقرأه بالكامل — ما يجبر
     *     [java.util.zip.ZipInputStream] على التحقق من CRC32 الخاص بكل ملف
     *     داخلياً، فيكتشف أي تلف حقيقي في البيانات (وليس فحصاً شكلياً فقط).
     *  3) يتحقق من وجود الملفات الجوهرية المطلوبة حسب نوع الحزمة.
     *  4) يفحص محتوى `set.txt` إن وُجد (صيغة مفتاح=قيمة سطراً بسطر) ويُبلّغ
     *     عن أي أسطر مشوّهة، ويتحقق من صلاحية `meta.json` كـ JSON إن وُجد.
     * لا يكتب أي بايت على القرص في هذه المرحلة إطلاقاً.
     */
    fun scanPackage(uri: Uri): PackageScanResult {
        val resolver = context.contentResolver
        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return PackageScanResult.Failure("تعذّر فتح مجرى قراءة الملف")
        } catch (e: Exception) {
            val kind = NetworkStatus.classify(e, context)
            return PackageScanResult.Failure(
                "تعذّر قراءة الملف من المصدر: ${e.message ?: "خطأ غير معروف"}",
                isNetworkError = kind == NetworkStatus.FailureKind.NETWORK
            )
        }

        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
            return PackageScanResult.Failure("الملف ليس أرشيف حزمة dlof صالحاً (توقيع ZIP مفقود)")
        }

        val entries = mutableListOf<ScannedEntry>()
        var hasSeriesIndex = false
        var hasSingleMarker = false
        var hasAnyDlof = false
        var setTxtBytes: ByteArray? = null
        var protectedEntryName: String? = null

        try {
            java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        // readBytes() يقرأ حتى EOF، فيفرض على ZipInputStream مطابقة
                        // CRC32 المُخزَّن في رأس الإدخال — أي تلف حقيقي بالبيانات
                        // يُفجّر ZipException هنا وليس لاحقاً أثناء الكتابة على القرص.
                        val data = zis.readBytes()
                        entries.add(ScannedEntry(entry.name, data.size.toLong(), data))
                        val leaf = entry.name.substringAfterLast('/')
                        when {
                            leaf.equals("series-index.dlof", ignoreCase = true) -> {
                                hasSeriesIndex = true
                                if (org.dlof.reader.crypto.DlofCrypto.isEncrypted(data)) protectedEntryName = entry.name
                            }
                            leaf.equals("package.dlof", ignoreCase = true) -> {
                                hasSingleMarker = true
                                if (org.dlof.reader.crypto.DlofCrypto.isEncrypted(data)) protectedEntryName = entry.name
                            }
                            leaf.equals("meta.json", ignoreCase = true) -> hasSingleMarker = true
                            leaf.endsWith(".dlof", ignoreCase = true) -> hasAnyDlof = true
                            leaf.equals("set.txt", ignoreCase = true) -> setTxtBytes = data
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: java.util.zip.ZipException) {
            return PackageScanResult.Failure("الحزمة تالفة: فشل التحقق من سلامة أحد الملفات الداخلية (${e.message ?: "CRC غير مطابق"})")
        } catch (e: Exception) {
            val kind = NetworkStatus.classify(e, context)
            return PackageScanResult.Failure(
                "تعذّر فحص محتوى الحزمة: ${e.message ?: "خطأ غير معروف"}",
                isNetworkError = kind == NetworkStatus.FailureKind.NETWORK
            )
        }

        if (entries.isEmpty()) {
            return PackageScanResult.Failure("الحزمة فارغة، لا تحتوي على أي ملفات")
        }

        val kind = when {
            hasSeriesIndex -> PackageKind.SERIES
            hasSingleMarker -> PackageKind.SINGLE
            hasAnyDlof -> PackageKind.SERIES
            else -> PackageKind.UNKNOWN
        }
        if (kind == PackageKind.UNKNOWN) {
            return PackageScanResult.Failure("لم يُعثر على أي ملف dlof صالح داخل الحزمة")
        }

        val warnings = mutableListOf<String>()

        // ── فحص set.txt الدقيق (اختياري حسب المواصفة، لكن نُبلّغ عن أي تشوّه في صيغته أو أنواع قيمه) ──
        var setTxtValidation: org.dlof.reader.model.SeriesSettingsLoader.SetTxtValidation? = null
        val setTxtCheck: SetTxtCheck? = setTxtBytes?.let { raw ->
            try {
                val text = String(raw, Charsets.UTF_8)
                val report = org.dlof.reader.model.SeriesSettingsLoader.validateSetTxtText(text)
                setTxtValidation = report

                if (report.malformedLines > 0) {
                    warnings += "set.txt يحتوي على ${report.malformedLines} سطر بصيغة غير صحيحة (المتوقّع: مفتاح=قيمة)"
                }
                if (report.duplicateKeys.isNotEmpty()) {
                    warnings += "set.txt يحتوي على مفاتيح مكرّرة: ${report.duplicateKeys.joinToString("، ")}"
                }
                if (report.typeErrors.isNotEmpty()) {
                    warnings += "set.txt يحتوي على ${report.typeErrors.size} قيمة من نوع غير متوقَّع"
                }
                if (report.unknownKeys.isNotEmpty()) {
                    warnings += "set.txt يحتوي على ${report.unknownKeys.size} مفتاح غير معروف (سيُتجاهل بأمان)"
                }
                if (report.missingRecommended.isNotEmpty()) {
                    warnings += "set.txt لا يحدّد: ${report.missingRecommended.joinToString("، ")}"
                }
                if (report.validPairs == 0 && report.malformedLines == 0) {
                    warnings += "set.txt موجود لكنه فارغ من أي إعدادات فعلية"
                }
                SetTxtCheck(present = true, validPairs = report.validPairs, malformedLines = report.malformedLines)
            } catch (e: Exception) {
                warnings += "تعذّرت قراءة محتوى set.txt كنص UTF-8 صالح"
                SetTxtCheck(present = true, validPairs = 0, malformedLines = 0)
            }
        }
        if (setTxtCheck == null && kind == PackageKind.SERIES) {
            warnings += "لا يحتوي على set.txt (اختياري) — سيُستخدم المظهر والإعدادات الافتراضية"
        }

        // ── فحص meta.json إن وُجد ──
        entries.firstOrNull { it.name.substringAfterLast('/').equals("meta.json", ignoreCase = true) }
            ?.let { metaEntry ->
                try {
                    JSONObject(String(metaEntry.data, Charsets.UTF_8))
                } catch (e: Exception) {
                    warnings += "ملف meta.json غير صالح كبيانات JSON: ${e.message ?: "صيغة تالفة"}"
                }
            }

        val totalBytes = entries.sumOf { it.sizeBytes }
        return PackageScanResult.Success(
            kind, entries, totalBytes, setTxtCheck, warnings,
            setTxtValidation = setTxtValidation,
            protectedEntryName = protectedEntryName
        )
    }

    /**
     * يكتب مدخلات حزمة `.dlofpkg` (المفحوصة مسبقاً عبر [scanPackage]) إلى القرص فعلياً،
     * على كُتل حقيقية 128KB لكل مدخل، ويُبلّغ [onProgress] بعدد البايتات المكتوبة
     * فعلياً تراكمياً مقابل الإجمالي — تقدّم حقيقي وليس وهمياً.
     */
    fun installScannedDlofPackage(
        scan: PackageScanResult.Success,
        onProgress: (bytesDone: Long, bytesTotal: Long) -> Unit,
        /** يُبلَّغ باسم كل ملف يُسترجع/يُثبَّت حالياً وموضعه (1-based) من إجمالي عدد الملفات — لحالة "الاسترداد". */
        onFileProgress: (fileName: String, fileIndex: Int, fileTotal: Int) -> Unit = { _, _, _ -> }
    ): InstallResult {
        return try {
            val metaEntry = scan.entries.firstOrNull { it.name == "meta.json" }
            val pkgId = metaEntry?.let {
                runCatching { JSONObject(String(it.data, Charsets.UTF_8)).optString("id", "") }
                    .getOrNull()?.takeIf { id -> id.isNotBlank() }
            }
            val dirName = pkgId ?: "pkg-${System.currentTimeMillis()}"

            val pkgRoot = File(context.filesDir, "packages")
            val installDir = File(pkgRoot, dirName).also { it.mkdirs() }

            val installedFiles = mutableListOf<String>()
            var mediaCount = 0
            var written = 0L
            val total = scan.totalBytes
            val fileTotal = scan.entries.size
            scan.entries.forEachIndexed { idx, entry ->
                onFileProgress(entry.name, idx + 1, fileTotal)
                val outFile = File(installDir, entry.name)
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { stream ->
                    written = writeChunked(stream, entry.data, written, total, onProgress)
                }
                installedFiles.add(entry.name)
                if (entry.name.startsWith("attachments/") || entry.name.startsWith("media/")) mediaCount++
            }

            InstallResult.Success(
                installedFiles,
                mediaCount,
                installDir,
                structureReport = try {
                    org.dlof.reader.lighthouse.DocumentFileHelper.checkPackage(installDir)
                } catch (_: Exception) {
                    null
                }
            )
        } catch (e: Exception) {
            InstallResult.Failure("خطأ أثناء تنزيل/تثبيت الحزمة: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    /**
     * يكتب مدخلات حزمة `.dlofSeries` (المفحوصة مسبقاً) إلى القرص فعلياً، بنفس منطق
     * التقدّم الحقيقي بالكُتل المستخدم في [installScannedDlofPackage].
     */
    fun installScannedSeriesPackage(
        scan: PackageScanResult.Success,
        onProgress: (bytesDone: Long, bytesTotal: Long) -> Unit,
        /** يُبلَّغ باسم كل ملف يُسترجع/يُثبَّت حالياً وموضعه (1-based) من إجمالي عدد الملفات — لحالة "الاسترداد". */
        onFileProgress: (fileName: String, fileIndex: Int, fileTotal: Int) -> Unit = { _, _, _ -> }
    ): InstallResult {
        return try {
            val seriesName = scan.entries.firstOrNull()?.name?.split("/")?.firstOrNull()?.takeIf { it.isNotBlank() }
            val dirName = seriesName ?: "UnknownSeries"
            val seriesRoot = File(context.filesDir, "series")
            val installDir = File(seriesRoot, dirName).also { it.mkdirs() }

            val installedFiles = mutableListOf<String>()
            var mediaCount = 0
            var written = 0L
            val total = scan.totalBytes
            val fileTotal = scan.entries.size
            scan.entries.forEachIndexed { idx, entry ->
                val relativePath = if (entry.name.startsWith("$dirName/"))
                    entry.name.removePrefix("$dirName/") else entry.name
                onFileProgress(relativePath, idx + 1, fileTotal)
                val outFile = File(installDir, relativePath)
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { stream ->
                    written = writeChunked(stream, entry.data, written, total, onProgress)
                }
                installedFiles.add(relativePath)
                if (relativePath.lowercase().startsWith("media/")) mediaCount++
            }

            InstallResult.Success(
                installedFiles,
                mediaCount,
                installDir,
                structureReport = try {
                    org.dlof.reader.lighthouse.DocumentFileHelper.checkPackage(installDir)
                } catch (_: Exception) {
                    null
                }
            )
        } catch (e: Exception) {
            InstallResult.Failure("خطأ أثناء تنزيل/تثبيت الحزمة: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    /** يكتب [data] على كُتل 128KB مضيفاً إلى [alreadyWritten] التراكمي، ويُبلّغ [onProgress] بعد كل كتلة حقيقية. */
    private fun writeChunked(
        stream: java.io.OutputStream,
        data: ByteArray,
        alreadyWritten: Long,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ): Long {
        var written = alreadyWritten
        var offset = 0
        val chunk = 128 * 1024
        while (offset < data.size) {
            val len = minOf(chunk, data.size - offset)
            stream.write(data, offset, len)
            offset += len
            written += len
            onProgress(written, total)
        }
        stream.flush()
        return written
    }

    fun installDlofPackage(uri: Uri): InstallResult {
        val resolver = context.contentResolver
        val zipBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return InstallResult.Failure("تعذّر قراءة ملف الحزمة")

        return try {
            val zipStream = java.util.zip.ZipInputStream(zipBytes.inputStream())
            val allEntries = mutableListOf<Pair<String, ByteArray>>()

            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    allEntries.add(entry.name to zipStream.readBytes())
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

            // اقرأ meta.json لاستخراج id الحزمة
            val metaBytes = allEntries.firstOrNull { it.first == "meta.json" }?.second
            val pkgId = if (metaBytes != null) {
                runCatching {
                    JSONObject(String(metaBytes)).optString("id", null.toString())
                }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
            } else null
            val dirName = pkgId ?: "pkg-${System.currentTimeMillis()}"

            val pkgRoot = java.io.File(context.filesDir, "packages")
            val installDir = java.io.File(pkgRoot, dirName).also { it.mkdirs() }

            val installedFiles = mutableListOf<String>()
            var mediaCount = 0
            for ((name, data) in allEntries) {
                val outFile = java.io.File(installDir, name)
                outFile.parentFile?.mkdirs()
                outFile.writeBytes(data)
                installedFiles.add(name)
                if (name.startsWith("attachments/") || name.startsWith("media/")) mediaCount++
            }

            InstallResult.Success(
                installedFiles,
                mediaCount,
                installDir,
                structureReport = try {
                    org.dlof.reader.lighthouse.DocumentFileHelper.checkPackage(installDir)
                } catch (_: Exception) {
                    null
                }
            )
        } catch (e: Exception) {
            InstallResult.Failure("خطأ أثناء تثبيت الحزمة: ${e.message}")
        }
    }

    /**
     * يصدّر مستنداً واحداً كحزمة `.dlofpkg` — الهيكل الكامل v2.0 (انظر
     * spec/PACKAGE_FORMATS.md):
     *
     *  - `set.txt`           : الإعدادات الرئيسية (مطلوب)
     *  - `package.dlof`      : المستند المُسلسَل كـ XML
     *  - `meta.json`         : بيانات وصفية سريعة
     *  - `media/image/…`     : الصور (مع chapter1/ وهكذا إن كان المجال قصة مصورة/مانجا)
     *  - `media/video/…`     : الفيديوهات (مع Episodes/episode1/ وهكذا إن كان المجال مسلسلاً)
     *  - `media/fonts/…`     : الخطوط + font.dlof
     *  - `setting/…`         : dlotemplate.xml, map.dlof, Documentation.dlof, license.dlof
     *  - `setting/pro/…`     : Best64.xml, WQ.JSON (إعدادات التشفير)
     *  - `Lighthouse/…`      : ep.kt, df.kt, cr.kt (أدوات مرجعية)
     *
     * @param embedBase64 عندما تكون false (الافتراضي) لا يُضمَّن أي مرفق كـ base64 داخل
     * `package.dlof`؛ بدلاً من ذلك تُكتب المرفقات كملفات فعلية داخل `media/` ويُشار
     * إليها بمسار نسبي (`uri`). عندما تكون true يُحافَظ على السلوك القديم (تضمين
     * base64 كاملاً داخل package.dlof) بالإضافة إلى نسخ media/ — مفيد لحزمة
     * "مكتفية بذاتها" بملف واحد، على حساب الحجم.
     */
    fun exportAsDlofPackage(document: DlofDocument, destinationUri: Uri, embedBase64: Boolean = false) {
        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { rawOut ->
            writeDlofPackageZip(document, rawOut, embedBase64)
        } ?: throw IllegalStateException("تعذّر إنشاء ملف الحزمة")
    }

    /**
     * تنزيل مباشر وسريع لحزمة `.dlofpkg` — بلا أي نافذة اختيار مسار (SAF)، على
     * عكس [exportAsDlofPackage] الذي يترك للمستخدم اختيار الموقع والاسم يدوياً
     * في كل مرة. يُحفظ هنا مباشرة داخل مجلد التنزيلات العام تحت مسار ثابت
     * موحّد `Download/dlofpkg/` (يطابق تسمية الحزمة نفسها)، بنفس منطق
     * [exportAsDlofPackage] الداخلي (الهيكل الكامل v2.0).
     *
     * @return الـ Uri الناتج للملف المحفوظ، أو null إن تعذّر إنشاؤه.
     */
    fun exportAsDlofPackageDirect(document: DlofDocument, embedBase64: Boolean = false): Uri? {
        val safeName = (document.metadata.title.ifBlank { document.id })
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
        val fileName = "$safeName.dlofpkg"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/dlofpkg")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { writeDlofPackageZip(document, it, embedBase64) }
                    ?: return null
                uri
            } else {
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "dlofpkg")
                dir.mkdirs()
                val outFile = java.io.File(dir, fileName)
                java.io.FileOutputStream(outFile).use { writeDlofPackageZip(document, it, embedBase64) }
                Uri.fromFile(outFile)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** يقرأ ملفاً نصياً مرفقاً كأصل (asset) داخل التطبيق، تحت `dlofpkg_template/`. */
    private fun readTemplateAssetText(relativePath: String): String? = try {
        context.assets.open("dlofpkg_template/$relativePath").use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) {
        null
    }

    /** يقرأ ملفاً ثنائياً مرفقاً كأصل (asset) داخل التطبيق، تحت `dlofpkg_template/`. */
    private fun readTemplateAssetBytes(relativePath: String): ByteArray? = try {
        context.assets.open("dlofpkg_template/$relativePath").use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    /** ينسخ أصلاً (asset) ثابتاً من dlofpkg_template/ إلى مدخل ZIP بنفس المسار النسبي، إن وُجد. */
    private fun copyTemplateAssetToZip(zip: java.util.zip.ZipOutputStream, relativePath: String, zipEntryName: String = relativePath) {
        val bytes = readTemplateAssetBytes(relativePath) ?: return
        zip.putNextEntry(java.util.zip.ZipEntry(zipEntryName))
        zip.write(bytes)
        zip.closeEntry()
    }

    /**
     * يبني نص `set.txt` انطلاقاً من القالب الافتراضي المرفق بالتطبيق (dlofpkg_template/set.txt.template)،
     * مع استبدال مفاتيح `package.*` وحقول meta.* بالقيم الفعلية للمستند المُصدَّر.
     */
    private fun buildSetTxt(document: DlofDocument, embedBase64: Boolean): String {
        val template = readTemplateAssetText("set.txt.template")
        val overrides = linkedMapOf(
            "package.id" to document.id,
            "package.title" to document.metadata.title,
            "package.domain" to document.metadata.domain.xmlValue,
            "package.language" to document.metadata.language,
            "package.rtl" to (document.metadata.language == "ar").toString(),
            "package.author" to (document.metadata.author ?: ""),
            "package.version" to document.version,
            "meta.createdAt" to (document.metadata.createdAt ?: ""),
            "meta.updatedAt" to (document.metadata.updatedAt ?: ""),
            "base64.mode" to if (embedBase64) "always" else "optional",
            "crypto.enabled" to "false"
        )

        if (template == null) {
            // احتياط: لا يوجد أصل مرفق (نادراً) — نبني set.txt أساسياً يفي بالحد الأدنى المطلوب
            return buildString {
                appendLine("# set.txt — تم إنشاؤه تلقائياً")
                overrides.forEach { (k, v) -> appendLine("$k=$v") }
                appendLine("template.path=setting/dlotemplate.xml")
                appendLine("setting.mapFile=setting/map.dlof")
                appendLine("setting.docFile=setting/Documentation.dlof")
                appendLine("setting.licenseFile=setting/license.dlof")
                appendLine("crypto.best64Path=setting/pro/Best64.xml")
                appendLine("crypto.wqPath=setting/pro/WQ.JSON")
                appendLine("media.imagePath=media/image")
                appendLine("media.videoPath=media/video")
                appendLine("media.fontsPath=media/fonts")
            }
        }

        val remaining = overrides.toMutableMap()
        val lines = template.lines().map { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@map line
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@map line
            val key = trimmed.substring(0, eq).trim()
            val override = remaining.remove(key) ?: return@map line
            "$key=$override"
        }.toMutableList()

        // أي مفتاح لم يظهر أصلاً في القالب (نادر) يُضاف في النهاية
        if (remaining.isNotEmpty()) {
            lines += ""
            remaining.forEach { (k, v) -> lines += "$k=$v" }
        }
        return lines.joinToString("\n")
    }

    /** اسم ملف آمن (بلا أحرف قد تكسر مساراً داخل ZIP) لملف وسائط واحد. */
    private fun safeMediaFileName(name: String, fallback: String): String =
        name.trim().ifBlank { fallback }.replace(Regex("[\\\\/]"), "_")

    /**
     * يكتب محتوى حزمة `.dlofpkg` بالهيكل الكامل v2.0 إلى أي [OutputStream]:
     * set.txt + package.dlof + meta.json + media/ + setting/ + Lighthouse/.
     */
    private fun writeDlofPackageZip(document: DlofDocument, rawOut: java.io.OutputStream, embedBase64: Boolean) {
        val domain = document.metadata.domain
        val isComicLike = domain == Domain.COMIC || domain == Domain.MANGA
        val isSeriesLike = domain == Domain.SERIES

        java.util.zip.ZipOutputStream(rawOut).use { zip ->
            // أعلى مستوى ضغط ممكن (9) بدل المستوى الافتراضي.
            zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)

            // ── 1) media/image و media/video و media/fonts — نكتبها أولاً لنعرف
            //       المسار النسبي النهائي لكل مرفق قبل تسلسل package.dlof ──
            val imageAttachments = document.attachments.filter { it.kind == AttachmentKind.IMAGE }
            val videoAttachments = document.attachments.filter { it.kind == AttachmentKind.VIDEO }
            val subtitleAttachments = document.attachments.filter { it.kind == AttachmentKind.SUBTITLE }
            val otherAttachments = document.attachments.filter {
                it.kind != AttachmentKind.IMAGE && it.kind != AttachmentKind.VIDEO && it.kind != AttachmentKind.SUBTITLE
            }
            val largeVideoRefs = document.richBlocks.filterIsInstance<RichBlock.LargeVideoRef>()

            // خريطة: معرّف المرفق ← مساره النسبي داخل الحزمة (لإعادة توجيه package.dlof إليه لاحقاً)
            val attachmentRelativePaths = mutableMapOf<String, String>()

            imageAttachments.forEachIndexed { idx, att ->
                val fileName = safeMediaFileName(att.fileName, "image_${idx + 1}")
                val relPath = if (isComicLike) "media/image/chapter1/$fileName" else "media/image/$fileName"
                attachmentRelativePaths[att.id] = relPath
                writeAttachmentBytes(zip, att, relPath)
            }
            (videoAttachments + subtitleAttachments).forEachIndexed { idx, att ->
                val fileName = safeMediaFileName(att.fileName, "video_${idx + 1}")
                val relPath = if (isSeriesLike) "media/video/Episodes/episode1/$fileName" else "media/video/$fileName"
                attachmentRelativePaths[att.id] = relPath
                writeAttachmentBytes(zip, att, relPath)
            }
            otherAttachments.forEach { att ->
                val fileName = safeMediaFileName(att.fileName, "file_${att.id}")
                val relPath = "media/other/$fileName"
                attachmentRelativePaths[att.id] = relPath
                writeAttachmentBytes(zip, att, relPath)
            }
            largeVideoRefs.forEach { ref ->
                val fileName = safeMediaFileName(ref.fileName, "video_${ref.id}")
                val relPath = if (isSeriesLike) "media/video/Episodes/episode1/$fileName" else "media/video/$fileName"
                try {
                    context.contentResolver.openInputStream(Uri.parse(ref.uri))?.use { ins ->
                        zip.putNextEntry(java.util.zip.ZipEntry(relPath))
                        ins.copyTo(zip)
                        zip.closeEntry()
                    }
                } catch (_: Exception) {
                    // تعذّر الوصول لملف الفيديو الخارجي (قد يكون حُذف) — نتجاهله بأمان،
                    // يبقى المرجع في package.dlof (uri/thumbnail) كما هو.
                }
            }

            // media/fonts/dlof/font.dlof (من الأصل الافتراضي المرفق بالتطبيق)
            copyTemplateAssetToZip(zip, "media/fonts/dlof/font.dlof")

            // ── 2) package.dlof ──
            zip.putNextEntry(java.util.zip.ZipEntry("package.dlof"))
            val docToWrite = if (embedBase64) {
                document
            } else {
                // بدون base64: كل مرفق يُشار إليه بمساره النسبي داخل media/ بدل تضمين بياناته
                document.copy(
                    attachments = document.attachments.map { att ->
                        val relPath = attachmentRelativePaths[att.id]
                        if (relPath != null) att.copy(data = null, uri = relPath) else att
                    }
                )
            }
            DlofParser.write(docToWrite, zip)
            zip.closeEntry()

            // ── 3) meta.json ──
            val meta = JSONObject().apply {
                put("id",      document.id)
                put("title",   document.metadata.title)
                put("domain",  document.metadata.domain.xmlValue)
                put("version", document.version)
                put("author",  document.metadata.author ?: "")
                put("language",document.metadata.language)
                put("createdAt", document.metadata.createdAt ?: "")
                put("dlofpkg_version", "2.0")
                put("base64Mode", if (embedBase64) "always" else "optional")
            }
            zip.putNextEntry(java.util.zip.ZipEntry("meta.json"))
            zip.write(meta.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // ── 4) set.txt ──
            zip.putNextEntry(java.util.zip.ZipEntry("set.txt"))
            zip.write(buildSetTxt(document, embedBase64).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // ── 5) setting/ — إمّا مبني من قالب المستند نفسه (إن وُجد) أو الأصل الافتراضي ──
            val docTemplate = document.template
            if (docTemplate != null) {
                zip.putNextEntry(java.util.zip.ZipEntry("setting/dlotemplate.xml"))
                zip.write(buildDlotemplateXml(document).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            } else {
                copyTemplateAssetToZip(zip, "setting/dlotemplate.xml")
            }
            copyTemplateAssetToZip(zip, "setting/map.dlof")
            copyTemplateAssetToZip(zip, "setting/Documentation.dlof")
            copyTemplateAssetToZip(zip, "setting/license.dlof")
            copyTemplateAssetToZip(zip, "setting/pro/Best64.xml")
            copyTemplateAssetToZip(zip, "setting/pro/WQ.JSON")

            // ── 6) Lighthouse/ — أدوات مرجعية (نسخ من كود التطبيق نفسه) ──
            copyTemplateAssetToZip(zip, "Lighthouse/ep.kt")
            copyTemplateAssetToZip(zip, "Lighthouse/df.kt")
            copyTemplateAssetToZip(zip, "Lighthouse/cr.kt")
        }
    }

    /** يكتب بايتات مرفق واحد ([Attachment.data] base64 أو [Attachment.uri] خارجي) إلى مدخل ZIP بمسار [relPath]. */
    private fun writeAttachmentBytes(zip: java.util.zip.ZipOutputStream, att: Attachment, relPath: String) {
        val bytes: ByteArray? = when {
            att.data != null -> try {
                Base64.decode(att.data, Base64.DEFAULT)
            } catch (_: Exception) {
                null
            }
            att.uri != null -> try {
                context.contentResolver.openInputStream(Uri.parse(att.uri))?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
            else -> null
        } ?: return

        zip.putNextEntry(java.util.zip.ZipEntry(relPath))
        zip.write(bytes)
        zip.closeEntry()
    }

    /** يبني setting/dlotemplate.xml من قالب [DlofDocument.template] الفعلي للمستند، بدل الأصل الافتراضي. */
    private fun buildDlotemplateXml(document: DlofDocument): String {
        val t = document.template ?: return readTemplateAssetText("setting/dlotemplate.xml") ?: ""
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<dlofTemplate xmlns=\"https://dlof.org/schema/template/1.0\" ")
        sb.append("id=\"${esc(document.id)}\" name=\"${esc(document.metadata.title)}\" version=\"2.0\">\n")
        sb.append("  <design")
        t.primaryColor?.let { sb.append(" primaryColor=\"${esc(it)}\"") }
        t.secondaryColor?.let { sb.append(" secondaryColor=\"${esc(it)}\"") }
        t.backgroundColor?.let { sb.append(" backgroundColor=\"${esc(it)}\"") }
        t.textColor?.let { sb.append(" textColor=\"${esc(it)}\"") }
        t.fontFamily?.let { sb.append(" fontFamily=\"${esc(it)}\"") }
        sb.append(" layout=\"${esc(t.layout.xmlValue)}\"/>\n")
        sb.append("</dlofTemplate>\n")
        return sb.toString()
    }

    /**
     * يعيد قائمة السلاسل المثبّتة في filesDir/series/
     * كل عنصر: اسم المجلد → مسار series-index.dlof أو أول dlof.
     */
    fun listInstalledSeries(): List<Pair<String, java.io.File>> {
        val seriesRoot = java.io.File(context.filesDir, "series")
        if (!seriesRoot.exists()) return emptyList()
        return seriesRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val indexFile = java.io.File(dir, "series-index.dlof")
                    .takeIf { it.exists() }
                    ?: dir.listFiles { f -> f.extension == "dlof" }?.firstOrNull()
                    ?: return@mapNotNull null
                dir.name to indexFile
            } ?: emptyList()
    }

    /**
     * يعيد قائمة الحزم الفردية المثبّتة في filesDir/packages/
     */
    fun listInstalledPackages(): List<Pair<String, java.io.File>> {
        val pkgRoot = java.io.File(context.filesDir, "packages")
        if (!pkgRoot.exists()) return emptyList()
        return pkgRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val dlofFile = java.io.File(dir, "package.dlof").takeIf { it.exists() }
                    ?: return@mapNotNull null
                dir.name to dlofFile
            } ?: emptyList()
    }
}

/** اختصار محفوظ لمجلد على الجهاز اختاره المستخدم ليتصفّحه مدير الملفات. */
data class DeviceFolderBookmark(val uriString: String, val label: String)

// ══════════════════════════════════════════════════════════════════
// نتائج تثبيت الحزم
// ══════════════════════════════════════════════════════════════════

/** نتيجة تثبيت حزمة `.dlofSeries` أو `.dlofpkg` */
sealed class InstallResult {
    /** تثبيت ناجح — قائمة الملفات المثبّتة وعدد الوسائط */
    data class Success(
        val installedFiles: List<String>,
        val mediaCount: Int,
        val installDir: java.io.File,
        /** تقرير فحص هيكل حزمة dlofpkg (media/setting/Lighthouse..) عبر Lighthouse.DocumentFileHelper، أو null إن تعذّر. */
        val structureReport: org.dlof.reader.lighthouse.DocumentFileHelper.PackageCheck? = null
    ) : InstallResult()

    /** فشل التثبيت */
    data class Failure(val reason: String) : InstallResult()
}
