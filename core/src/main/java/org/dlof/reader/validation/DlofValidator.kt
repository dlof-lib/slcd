package org.dlof.reader.validation

import org.dlof.reader.model.Attachment
import org.dlof.reader.model.DlofContent
import org.dlof.reader.model.DlofDocument
import org.dlof.reader.model.Template

/** درجة خطورة نتيجة الفحص — تحدد كيف تُعرض في الواجهة (لون/أيقونة). */
enum class Severity { ERROR, WARNING, INFO }

/** نتيجة فحص واحدة، برسالة مفهومة للمستخدم العادي (بلا تفاصيل تقنية مفرطة). */
data class DiagnosticIssue(
    val severity: Severity,
    val message: String
)

/** نتائج فحص ملف .dlof واحد بمفرده (بنيته، ألوانه، مرفقاته). */
data class DocumentDiagnostics(
    val documentTitle: String,
    val issues: List<DiagnosticIssue>
) {
    val errorCount get() = issues.count { it.severity == Severity.ERROR }
    val warningCount get() = issues.count { it.severity == Severity.WARNING }
    val isHealthy get() = errorCount == 0 && warningCount == 0
}

/**
 * نتيجة فحصة واحدة لملف ضمن حلقة كاملة، مع معرفة هل تمكّنا من
 * فتح الملف "التالي" منه بنجاح أم لا — هذا ما يُبنى عليه تتبّع
 * بقية الحلقة في [DlofRepository.validateLoop].
 */
data class LoopNodeDiagnostics(
    val uriString: String,
    val diagnostics: DocumentDiagnostics,
    val nextResolved: Boolean,
    val previousResolved: Boolean
)

/** نتيجة فحص حلقة كاملة من ملفات .dlof متتابعة. */
data class LoopDiagnostics(
    val nodes: List<LoopNodeDiagnostics>,
    val hasCycle: Boolean,
    val brokenLinks: List<String>   // أسماء ملفات لم يُعثر عليها أثناء التتبع
) {
    val totalErrors get() = nodes.sumOf { it.diagnostics.errorCount } + (if (hasCycle) 1 else 0)
    val totalWarnings get() = nodes.sumOf { it.diagnostics.warningCount }
}

/**
 * منطق فحص صِرف (لا يعتمد على Android أو واجهة المستخدم) لملفات DLoF.
 * يُستخدم من DlofRepository لفحص ملف واحد، ومن ميزة "فحص الحلقة الكاملة"
 * لفحص كل ملف أثناء تتبّع previous/next.
 */
object DlofValidator {

    /** الحد الأقصى المعقول لمرفق مضمَّن داخل الملف نفسه قبل التحذير (5 ميجابايت). */
    private const val LARGE_ATTACHMENT_WARNING_BYTES = 5L * 1024 * 1024

    private val hexColorRegex = Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$")

    fun validateDocument(document: DlofDocument): DocumentDiagnostics {
        val issues = mutableListOf<DiagnosticIssue>()

        validateMetadata(document, issues)
        validateContent(document.content, issues)
        validateTemplate(document.template, issues)
        validateAttachments(document.attachments, issues)
        validateLoopLinksSelfConsistency(document, issues)

        return DocumentDiagnostics(
            documentTitle = document.metadata.title.ifBlank { "بلا عنوان" },
            issues = issues
        )
    }

    private fun validateMetadata(document: DlofDocument, issues: MutableList<DiagnosticIssue>) {
        if (document.metadata.title.isBlank()) {
            issues += DiagnosticIssue(Severity.ERROR, "عنوان المستند فارغ — يُفضّل إعطاء عنوان واضح.")
        }
        if (document.id.isBlank()) {
            issues += DiagnosticIssue(Severity.ERROR, "معرّف المستند (id) فارغ.")
        }
    }

    private fun validateContent(content: DlofContent, issues: MutableList<DiagnosticIssue>) {
        when (content) {
            is DlofContent.QA -> {
                if (content.question.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "السؤال فارغ.")
                if (content.answer.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "الإجابة فارغة.")
            }
            is DlofContent.BookChapter -> {
                if (content.chapterTitle.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "عنوان الفصل فارغ.")
                if (content.text.isBlank()) issues += DiagnosticIssue(Severity.WARNING, "نص الفصل فارغ.")
            }
            is DlofContent.TermDefinition -> {
                if (content.term.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "المصطلح فارغ.")
                if (content.definition.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "التعريف فارغ.")
            }
            is DlofContent.InfoExplain -> {
                if (content.topic.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "عنوان الموضوع فارغ.")
                if (content.explanation.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "نص الشرح فارغ.")
            }
            is DlofContent.Generic -> {
                if (content.element.isBlank() && content.body.isBlank()) {
                    issues += DiagnosticIssue(Severity.WARNING, "المحتوى العام (generic) لا يحتوي عنصراً أو نصاً.")
                }
            }
            is DlofContent.Recipe -> {
                if (content.recipeName.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "اسم الوصفة فارغ.")
                if (content.ingredients.isEmpty()) issues += DiagnosticIssue(Severity.WARNING, "الوصفة بلا مكوّنات.")
                if (content.steps.isEmpty()) issues += DiagnosticIssue(Severity.WARNING, "الوصفة بلا خطوات تحضير.")
            }
            is DlofContent.JournalEntry -> {
                if (content.date.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "تاريخ اليومية فارغ.")
                if (content.entryText.isBlank()) issues += DiagnosticIssue(Severity.WARNING, "نص اليومية فارغ.")
            }
            is DlofContent.EpisodeItem -> {
                if (content.episodeTitle.isBlank()) issues += DiagnosticIssue(Severity.ERROR, "عنوان الحلقة فارغ.")
            }
            is DlofContent.ComicPanel -> {
                if (content.caption.isNullOrBlank() && content.dialogue.isEmpty()) {
                    issues += DiagnosticIssue(Severity.WARNING, "اللوحة المصورة لا تحتوي تعليقاً أو حواراً.")
                }
            }
        }
    }

    private fun validateTemplate(template: Template?, issues: MutableList<DiagnosticIssue>) {
        if (template == null) return
        val colorFields = listOf(
            "اللون الأساسي" to template.primaryColor,
            "اللون الثانوي" to template.secondaryColor,
            "لون الخلفية" to template.backgroundColor,
            "لون النص" to template.textColor
        )
        colorFields.forEach { (label, value) ->
            if (!value.isNullOrBlank() && !hexColorRegex.matches(value)) {
                issues += DiagnosticIssue(
                    Severity.WARNING,
                    "$label غير صالح: \"$value\" — يجب أن يكون كود HEX مثل #RRGGBB."
                )
            }
        }
        // تباين أساسي بين الخلفية والنص لتفادي نص غير قابل للقراءة
        val bg = template.backgroundColor?.takeIf { hexColorRegex.matches(it) }
        val text = template.textColor?.takeIf { hexColorRegex.matches(it) }
        if (bg != null && text != null && bg.equals(text, ignoreCase = true)) {
            issues += DiagnosticIssue(Severity.WARNING, "لون الخلفية ولون النص متطابقان — قد يصبح النص غير مقروء.")
        }
    }

    private fun validateAttachments(attachments: List<Attachment>, issues: MutableList<DiagnosticIssue>) {
        val seenIds = mutableSetOf<String>()
        attachments.forEach { att ->
            if (att.data.isNullOrBlank() && att.uri.isNullOrBlank()) {
                issues += DiagnosticIssue(Severity.ERROR, "المرفق \"${att.fileName}\" بلا بيانات (data) وبلا رابط (uri).")
            }
            val size = att.sizeBytes
            if (size != null && size > LARGE_ATTACHMENT_WARNING_BYTES && !att.data.isNullOrBlank()) {
                val mb = size / (1024.0 * 1024.0)
                issues += DiagnosticIssue(
                    Severity.WARNING,
                    "المرفق \"${att.fileName}\" مضمَّن بحجم %.1f ميجابايت — قد يُبطئ فتح الملف.".format(mb)
                )
            }
            if (!seenIds.add(att.id)) {
                issues += DiagnosticIssue(Severity.ERROR, "معرّف مرفق مكرر: \"${att.id}\".")
            }
        }
    }

    private fun validateLoopLinksSelfConsistency(document: DlofDocument, issues: MutableList<DiagnosticIssue>) {
        val prevRef = document.loopLinks.previous?.ref
        val nextRef = document.loopLinks.next?.ref
        if (prevRef != null && prevRef == nextRef) {
            issues += DiagnosticIssue(Severity.WARNING, "الملف السابق والتالي يشيران لاسم الملف نفسه (\"$prevRef\").")
        }
        if (prevRef != null && prevRef.equals(document.id, ignoreCase = true)) {
            issues += DiagnosticIssue(Severity.ERROR, "الملف السابق يشير إلى هذا الملف نفسه.")
        }
        if (nextRef != null && nextRef.equals(document.id, ignoreCase = true)) {
            issues += DiagnosticIssue(Severity.ERROR, "الملف التالي يشير إلى هذا الملف نفسه.")
        }
    }
}
