package org.dlof.reader.model

/**
 * مدخلة في فهرس الملفات المحلي للتطبيق — يسجَّل فيه كل ملف .dlof
 * فتحه أو أنشأه المستخدم، لعرضه في شاشة "ملفاتي" (الكل / المميز / المهملات).
 *
 * هذا فهرس محلي فقط (لا يغيّر الملف نفسه) لأن التطبيق يتعامل مع
 * الملفات عبر Storage Access Framework ولا يملك مجلداً خاصاً به.
 */
data class FileEntry(
    val uriString: String,
    val title: String,
    val domain: String,
    val lastOpenedAt: Long,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val docId: String = ""   // معرّف المستند (DlofDocument.id) — يُستخدم لمطابقة رمز QR الممسوح بملف محلي
)
