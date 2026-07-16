package org.dlof.reader.validation

import org.dlof.reader.model.Attachment
import org.dlof.reader.model.AttachmentKind
import org.dlof.reader.model.DlofContent
import org.dlof.reader.model.DlofDocument
import org.dlof.reader.model.Domain
import org.dlof.reader.model.LinkRef
import org.dlof.reader.model.LoopLinks
import org.dlof.reader.model.Metadata
import org.dlof.reader.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DlofValidator] منطق صِرف بلا أي اعتماد على Android، لذا يُختبر هنا
 * كوحدة JVM عادية (بدون جهاز/محاكي) — أسرع بكثير من اختبار instrumented
 * ويجب أن يكون خط الدفاع الأول قبل رفع أي تعديل على قواعد الفحص.
 */
class DlofValidatorTest {

    private fun baseDocument(
        id: String = "doc-1",
        title: String = "عنوان صالح",
        content: DlofContent = DlofContent.QA(question = "سؤال؟", answer = "جواب."),
        attachments: List<Attachment> = emptyList(),
        template: Template? = null,
        loopLinks: LoopLinks = LoopLinks()
    ) = DlofDocument(
        id = id,
        metadata = Metadata(title = title, domain = Domain.CUSTOM),
        loopLinks = loopLinks,
        content = content,
        attachments = attachments,
        template = template
    )

    @Test
    fun validDocument_hasNoIssuesAndIsHealthy() {
        val result = DlofValidator.validateDocument(baseDocument())

        assertTrue(result.isHealthy)
        assertEquals(0, result.errorCount)
        assertEquals(0, result.warningCount)
    }

    @Test
    fun blankTitle_producesError() {
        val result = DlofValidator.validateDocument(baseDocument(title = ""))

        assertTrue(result.issues.any { it.severity == Severity.ERROR })
        // العنوان الفارغ يُستبدل بنص افتراضي في نتيجة الفحص نفسها.
        assertEquals("بلا عنوان", result.documentTitle)
    }

    @Test
    fun blankDocumentId_producesError() {
        val result = DlofValidator.validateDocument(baseDocument(id = ""))

        assertTrue(result.issues.any { it.severity == Severity.ERROR })
    }

    @Test
    fun qaContent_blankQuestionAndAnswer_producesTwoErrors() {
        val result = DlofValidator.validateDocument(
            baseDocument(content = DlofContent.QA(question = "", answer = ""))
        )

        assertEquals(2, result.errorCount)
    }

    @Test
    fun recipeContent_missingIngredientsAndSteps_producesWarningsNotErrors() {
        val result = DlofValidator.validateDocument(
            baseDocument(
                content = DlofContent.Recipe(recipeName = "طبخة", ingredients = emptyList(), steps = emptyList())
            )
        )

        assertEquals(0, result.errorCount)
        assertEquals(2, result.warningCount)
    }

    @Test
    fun duplicateAttachmentIds_producesError() {
        val attachments = listOf(
            Attachment(id = "att-1", fileName = "a.png", mimeType = "image/png", kind = AttachmentKind.IMAGE, data = "AAAA"),
            Attachment(id = "att-1", fileName = "b.png", mimeType = "image/png", kind = AttachmentKind.IMAGE, data = "BBBB")
        )

        val result = DlofValidator.validateDocument(baseDocument(attachments = attachments))

        assertTrue(result.issues.any { it.severity == Severity.ERROR && it.message.contains("مكرر") })
    }

    @Test
    fun attachmentWithoutDataOrUri_producesError() {
        val attachments = listOf(
            Attachment(id = "att-1", fileName = "a.png", mimeType = "image/png", kind = AttachmentKind.IMAGE)
        )

        val result = DlofValidator.validateDocument(baseDocument(attachments = attachments))

        assertTrue(result.issues.any { it.severity == Severity.ERROR })
    }

    @Test
    fun invalidHexColorInTemplate_producesWarning() {
        val template = Template(primaryColor = "not-a-color")

        val result = DlofValidator.validateDocument(baseDocument(template = template))

        assertTrue(result.issues.any { it.severity == Severity.WARNING })
    }

    @Test
    fun matchingBackgroundAndTextColor_producesWarning() {
        val template = Template(backgroundColor = "#FFFFFF", textColor = "#FFFFFF")

        val result = DlofValidator.validateDocument(baseDocument(template = template))

        assertTrue(
            result.issues.any {
                it.severity == Severity.WARNING && it.message.contains("متطابقان")
            }
        )
    }

    @Test
    fun validHexColors_withDifferentValues_produceNoTemplateWarning() {
        val template = Template(backgroundColor = "#000000", textColor = "#FFFFFF")

        val result = DlofValidator.validateDocument(baseDocument(template = template))

        assertTrue(result.isHealthy)
    }

    @Test
    fun selfReferencingNextLink_producesError() {
        val loopLinks = LoopLinks(next = LinkRef(ref = "doc-1"))

        val result = DlofValidator.validateDocument(baseDocument(id = "doc-1", loopLinks = loopLinks))

        assertTrue(result.issues.any { it.severity == Severity.ERROR })
    }

    @Test
    fun sameNextAndPreviousLink_producesWarning() {
        val loopLinks = LoopLinks(
            previous = LinkRef(ref = "other-doc"),
            next = LinkRef(ref = "other-doc")
        )

        val result = DlofValidator.validateDocument(baseDocument(loopLinks = loopLinks))

        assertTrue(result.issues.any { it.severity == Severity.WARNING })
    }
}
