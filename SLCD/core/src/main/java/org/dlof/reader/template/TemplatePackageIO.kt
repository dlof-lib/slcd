package org.dlof.reader.template

import org.dlof.reader.model.Template
import org.dlof.reader.model.TemplateLayout
import org.dlof.reader.model.TemplatePackage
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * يقرأ ويكتب "حزم القوالب" المحمولة: ملف مضغوط بامتداد .dlofTemplate
 * يحتوي على:
 *   - template.xml   : الوصف الرسمي (يُحقَّق به التطبيق، حسب dlof-template.xsd)
 *   - Template.kt    : نسخة Kotlin مرجعية لنفس القالب (تعريف كائن قابل للنسخ
 *                       مباشرة داخل مشروع Kotlin/Compose آخر)
 *
 * هذا يسمح "باسترداد" القالب: استيراده مرة، تطبيقه (ref) على أي عدد من
 * ملفات .dlof، أو نسخ Template.kt مباشرة في كود مشروع آخر.
 */
object TemplatePackageIO {

    class TemplatePackageException(message: String) : Exception(message)

    private const val ENTRY_XML = "template.xml"
    private const val ENTRY_KT = "Template.kt"

    // ───────────────────────── التصدير ─────────────────────────

    fun export(pkg: TemplatePackage, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_XML))
            zip.write(buildTemplateXml(pkg).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_KT))
            zip.write(buildTemplateKt(pkg).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun buildTemplateXml(pkg: TemplatePackage): String {
        val t = pkg.template
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<dlofTemplate xmlns="https://dlof.org/schema/template/1.0" """)
        sb.append("""id="${esc(pkg.id)}" name="${esc(pkg.name)}" """)
        pkg.author?.let { sb.append("""author="${esc(it)}" """) }
        sb.append("""version="${esc(pkg.version)}">""").append('\n')
        sb.append("  <design")
        t.primaryColor?.let { sb.append(""" primaryColor="${esc(it)}"""") }
        t.secondaryColor?.let { sb.append(""" secondaryColor="${esc(it)}"""") }
        t.backgroundColor?.let { sb.append(""" backgroundColor="${esc(it)}"""") }
        t.textColor?.let { sb.append(""" textColor="${esc(it)}"""") }
        t.fontFamily?.let { sb.append(""" fontFamily="${esc(it)}"""") }
        sb.append(""" layout="${esc(t.layout.xmlValue)}"""")
        t.headerAttachmentRef?.let { sb.append(""" headerAttachmentRef="${esc(it)}"""") }
        sb.append("/>\n")
        sb.append("</dlofTemplate>\n")
        return sb.toString()
    }

    /** نسخة Kotlin مرجعية يمكن لصقها مباشرة في مشروع يستخدم نموذج DLoF نفسه. */
    private fun buildTemplateKt(pkg: TemplatePackage): String {
        val t = pkg.template
        fun str(v: String?): String = if (v == null) "null" else "\"${v.replace("\"", "\\\"")}\""
        return """
            |// تم إنشاؤه تلقائياً من تطبيق DLoF Reader & Editor
            |// قالب: ${pkg.name} (${pkg.id})${pkg.author?.let { " — بواسطة $it" } ?: ""}
            |
            |import org.dlof.reader.model.Template
            |import org.dlof.reader.model.TemplateLayout
            |
            |val ${toIdentifier(pkg.id)}Template = Template(
            |    ref = "${pkg.id}",
            |    primaryColor = ${str(t.primaryColor)},
            |    secondaryColor = ${str(t.secondaryColor)},
            |    backgroundColor = ${str(t.backgroundColor)},
            |    textColor = ${str(t.textColor)},
            |    fontFamily = ${str(t.fontFamily)},
            |    layout = TemplateLayout.${t.layout.name},
            |    headerAttachmentRef = ${str(t.headerAttachmentRef)}
            |)
            |
        """.trimMargin()
    }

    private fun toIdentifier(id: String): String =
        id.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
            .mapIndexed { i, part -> if (i == 0) part.lowercase() else part.lowercase().replaceFirstChar { c -> c.uppercase() } }
            .joinToString("")
            .ifEmpty { "custom" }

    // ───────────────────────── الاستيراد ─────────────────────────

    fun import(input: InputStream): TemplatePackage {
        var xmlContent: String? = null
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == ENTRY_XML) {
                    xmlContent = zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        val xml = xmlContent
            ?: throw TemplatePackageException("الملف لا يحتوي على template.xml — تأكد أنه حزمة قالب صالحة (.dlofTemplate)")
        return parseTemplateXml(xml)
    }

    /** يفسّر نص XML خام (مكتوب يدوياً أو من ملف) إلى [TemplatePackage]، يرمي [TemplatePackageException] عند الخطأ. */
    fun parseTemplateXml(xml: String): TemplatePackage = try {
        parseTemplateXmlInternal(xml)
    } catch (e: TemplatePackageException) {
        throw e
    } catch (e: Exception) {
        throw TemplatePackageException("تعذّر تفسير XML: ${e.message ?: "صياغة غير صحيحة"}")
    }

    private fun parseTemplateXmlInternal(xml: String): TemplatePackage {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(xml.byteInputStream(Charsets.UTF_8), "UTF-8")

        var id: String? = null
        var name: String? = null
        var author: String? = null
        var version = "1.0"
        var template = Template()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "dlofTemplate" -> {
                        id = parser.getAttributeValue(null, "id")
                        name = parser.getAttributeValue(null, "name")
                        author = parser.getAttributeValue(null, "author")
                        version = parser.getAttributeValue(null, "version") ?: "1.0"
                    }
                    "design" -> {
                        template = Template(
                            ref = id,
                            primaryColor = parser.getAttributeValue(null, "primaryColor"),
                            secondaryColor = parser.getAttributeValue(null, "secondaryColor"),
                            backgroundColor = parser.getAttributeValue(null, "backgroundColor"),
                            textColor = parser.getAttributeValue(null, "textColor"),
                            fontFamily = parser.getAttributeValue(null, "fontFamily"),
                            layout = TemplateLayout.fromXml(parser.getAttributeValue(null, "layout") ?: "standard"),
                            headerAttachmentRef = parser.getAttributeValue(null, "headerAttachmentRef")
                        )
                    }
                }
            }
            eventType = parser.next()
        }

        val finalId = id ?: throw TemplatePackageException("template.xml لا يحتوي على معرّف (id) صالح")
        val finalName = name ?: finalId

        return TemplatePackage(
            id = finalId,
            name = finalName,
            author = author,
            version = version,
            template = template.copy(ref = finalId)
        )
    }

    /** نص XML أولي فارغ ليبدأ منه المستخدم كتابة قالبه من الصفر يدوياً. */
    fun blankTemplateXmlSkeleton(): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<dlofTemplate xmlns="https://dlof.org/schema/template/1.0"
        |              id="my-template" name="قالبي الخاص" author="" version="1.0">
        |  <design primaryColor="#6750A4" secondaryColor="#1E88E5"
        |          backgroundColor="#FFFFFF" textColor="#212121"
        |          fontFamily="serif" layout="standard"/>
        |</dlofTemplate>
        |
    """.trimMargin()

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
