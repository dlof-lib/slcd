package org.dlof.reader.parser

import org.dlof.reader.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * يحوّل تيار بيانات XML إلى DlofDocument والعكس.
 * Parses and serializes .dlof XML to/from the in-memory model.
 *
 * نستخدم XmlPullParser (مدمج في أندرويد، لا يحتاج مكتبات خارجية)
 * بدل DOM لأنه أخف على الذاكرة وكافٍ تماماً لبنية DLoF المسطحة نسبياً.
 */
object DlofParser {

    class ParseException(message: String) : Exception(message)

    fun parse(input: InputStream): DlofDocument {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        var id: String? = null
        var version: String = "1.0"
        var metadata: Metadata? = null
        var loopLinks: LoopLinks = LoopLinks()
        var content: DlofContent? = null
        var attachments: List<Attachment> = emptyList()
        var template: Template? = null
        var richBlocks: List<RichBlock> = emptyList()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "documentLoop" -> {
                        id = parser.getAttributeValue(null, "id")
                        version = parser.getAttributeValue(null, "version") ?: "1.0"
                    }
                    "metadata" -> metadata = parseMetadata(parser)
                    "loopLinks" -> loopLinks = parseLoopLinks(parser)
                    "content" -> content = parseContent(parser)
                    "attachments" -> attachments = parseAttachments(parser)
                    "template" -> template = parseTemplate(parser)
                    "richBlocks" -> richBlocks = parseRichBlocks(parser)
                }
            }
            eventType = parser.next()
        }

        val finalId = id ?: throw ParseException("الملف لا يحتوي على معرّف (id) صالح")
        val finalMeta = metadata ?: throw ParseException("الملف لا يحتوي على عنصر metadata")
        val finalContent = content ?: throw ParseException("الملف لا يحتوي على عنصر content")

        return DlofDocument(
            id = finalId,
            version = version,
            metadata = finalMeta,
            loopLinks = loopLinks,
            content = finalContent,
            attachments = attachments,
            template = template,
            richBlocks = richBlocks
        )
    }

    private fun parseMetadata(parser: XmlPullParser): Metadata {
        var title = ""
        var domain = Domain.CUSTOM
        var author: String? = null
        var createdAt: String? = null
        var updatedAt: String? = null
        var language = "ar"
        val tags = mutableListOf<String>()

        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> title = parser.nextText().trim()
                    "domain" -> domain = Domain.fromXml(parser.nextText().trim())
                    "author" -> author = parser.nextText().trim()
                    "createdAt" -> createdAt = parser.nextText().trim()
                    "updatedAt" -> updatedAt = parser.nextText().trim()
                    "language" -> language = parser.nextText().trim()
                    "tag" -> tags.add(parser.nextText().trim())
                }
            }
            eventType = parser.next()
        }

        return Metadata(title, domain, author, createdAt, updatedAt, language, tags)
    }

    private fun parseLoopLinks(parser: XmlPullParser): LoopLinks {
        var previous: LinkRef? = null
        var next: LinkRef? = null
        var loopRoot = false

        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "previous" -> {
                        val ref = parser.getAttributeValue(null, "ref") ?: ""
                        val title = parser.getAttributeValue(null, "title")
                        previous = LinkRef(ref, title)
                    }
                    "next" -> {
                        val ref = parser.getAttributeValue(null, "ref") ?: ""
                        val title = parser.getAttributeValue(null, "title")
                        next = LinkRef(ref, title)
                    }
                    "loopRoot" -> loopRoot = parser.nextText().trim().toBoolean()
                }
            }
            eventType = parser.next()
        }
        return LoopLinks(previous, next, loopRoot)
    }

    private fun parseContent(parser: XmlPullParser): DlofContent {
        val depth = parser.depth
        var eventType = parser.next()
        var result: DlofContent? = null

        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                result = when (parser.name) {
                    "qaItem" -> parseQA(parser)
                    "bookChapter" -> parseBookChapter(parser)
                    "termDefinition" -> parseTermDefinition(parser)
                    "infoExplain" -> parseInfoExplain(parser)
                    "genericItem" -> parseGeneric(parser)
                    "recipe" -> parseRecipe(parser)
                    "journalEntry" -> parseJournalEntry(parser)
                    "episodeItem" -> parseEpisodeItem(parser)
                    "comicPanel" -> parseComicPanel(parser)
                    else -> result
                }
            }
            eventType = parser.next()
        }

        return result ?: throw ParseException("عنصر content لا يحتوي على عنصر محتوى معروف")
    }

    private fun parseQA(parser: XmlPullParser): DlofContent.QA {
        var question = ""; var answer = ""; var explanation: String? = null; var difficulty: String? = null
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "question" -> question = parser.nextText().trim()
                    "answer" -> answer = parser.nextText().trim()
                    "explanation" -> explanation = parser.nextText().trim()
                    "difficulty" -> difficulty = parser.nextText().trim()
                }
            }
            eventType = parser.next()
        }
        return DlofContent.QA(question, answer, explanation, difficulty)
    }

    private fun parseBookChapter(parser: XmlPullParser): DlofContent.BookChapter {
        var chapterNumber: Int? = null; var chapterTitle = ""; var text = ""; var summary: String? = null
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "chapterNumber" -> chapterNumber = parser.nextText().trim().toIntOrNull()
                    "chapterTitle" -> chapterTitle = parser.nextText().trim()
                    "text" -> text = parser.nextText().trim()
                    "summary" -> summary = parser.nextText().trim()
                }
            }
            eventType = parser.next()
        }
        return DlofContent.BookChapter(chapterNumber, chapterTitle, text, summary)
    }

    private fun parseTermDefinition(parser: XmlPullParser): DlofContent.TermDefinition {
        var term = ""; var definition = ""; var example: String? = null
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "term" -> term = parser.nextText().trim()
                    "definition" -> definition = parser.nextText().trim()
                    "example" -> example = parser.nextText().trim()
                }
            }
            eventType = parser.next()
        }
        return DlofContent.TermDefinition(term, definition, example)
    }

    private fun parseInfoExplain(parser: XmlPullParser): DlofContent.InfoExplain {
        var topic = ""; var explanation = ""; var source: String? = null
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "topic" -> topic = parser.nextText().trim()
                    "explanation" -> explanation = parser.nextText().trim()
                    "source" -> source = parser.nextText().trim()
                }
            }
            eventType = parser.next()
        }
        return DlofContent.InfoExplain(topic, explanation, source)
    }

    private fun parseAttachments(parser: XmlPullParser): List<Attachment> {
        val list = mutableListOf<Attachment>()
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "attachment") {
                list.add(parseAttachment(parser))
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseAttachment(parser: XmlPullParser): Attachment {
        val id = parser.getAttributeValue(null, "id") ?: ""
        val fileName = parser.getAttributeValue(null, "fileName") ?: ""
        val mimeType = parser.getAttributeValue(null, "mimeType") ?: "application/octet-stream"
        val kind = AttachmentKind.fromXml(parser.getAttributeValue(null, "kind") ?: "file")
        val sizeBytes = parser.getAttributeValue(null, "sizeBytes")?.toLongOrNull()

        var data: String? = null
        var uri: String? = null
        var caption: String? = null

        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "data" -> data = parser.nextText().trim()
                    "uri" -> uri = parser.nextText().trim()
                    "caption" -> caption = parser.nextText().trim()
                }
            }
            eventType = parser.next()
        }
        return Attachment(id, fileName, mimeType, kind, data, uri, sizeBytes, caption)
    }

    private fun parseTemplate(parser: XmlPullParser): Template {
        val ref = parser.getAttributeValue(null, "ref")
        val primaryColor = parser.getAttributeValue(null, "primaryColor")
        val secondaryColor = parser.getAttributeValue(null, "secondaryColor")
        val backgroundColor = parser.getAttributeValue(null, "backgroundColor")
        val textColor = parser.getAttributeValue(null, "textColor")
        val fontFamily = parser.getAttributeValue(null, "fontFamily")
        val layout = TemplateLayout.fromXml(parser.getAttributeValue(null, "layout") ?: "standard")
        val headerAttachmentRef = parser.getAttributeValue(null, "headerAttachmentRef")

        // عنصر <template/> قد يكون فارغاً (self-closing)، لذا نتخطى حتى إغلاقه بأمان
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            eventType = parser.next()
        }

        return Template(ref, primaryColor, secondaryColor, backgroundColor, textColor, fontFamily, layout, headerAttachmentRef)
    }

    private fun parseRecipe(parser: XmlPullParser): DlofContent.Recipe {
        var recipeName = ""; var servings: Int? = null
        var prepTimeMinutes: Int? = null; var cookTimeMinutes: Int? = null
        var difficulty: String? = null
        val ingredients = mutableListOf<String>()
        val steps = mutableListOf<String>()
        var nutritionNotes: String? = null; var tips: String? = null

        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "recipeName" -> recipeName = parser.nextText().trim()
                    "servings" -> servings = parser.nextText().trim().toIntOrNull()
                    "prepTimeMinutes" -> prepTimeMinutes = parser.nextText().trim().toIntOrNull()
                    "cookTimeMinutes" -> cookTimeMinutes = parser.nextText().trim().toIntOrNull()
                    "difficulty" -> difficulty = parser.nextText().trim()
                    "ingredients" -> ingredients.addAll(parseStringList(parser, "ingredient"))
                    "steps" -> steps.addAll(parseStringList(parser, "step"))
                    "nutritionNotes" -> nutritionNotes = parser.nextText().trim()
                    "tips" -> tips = parser.nextText().trim()
                }
            }
            eventType = parser.next()
        }
        return DlofContent.Recipe(
            recipeName = recipeName,
            servings = servings,
            prepTimeMinutes = prepTimeMinutes,
            cookTimeMinutes = cookTimeMinutes,
            difficulty = difficulty,
            ingredients = ingredients,
            steps = steps,
            nutritionNotes = nutritionNotes,
            tips = tips
        )
    }

    private fun parseJournalEntry(parser: XmlPullParser): DlofContent.JournalEntry {
        var date = ""; var mood: String? = null; var entryText = ""
        var gratitude: String? = null
        val goals = mutableListOf<String>()

        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "date" -> date = parser.nextText().trim()
                    "mood" -> mood = parser.nextText().trim()
                    "entryText" -> entryText = parser.nextText().trim()
                    "gratitude" -> gratitude = parser.nextText().trim()
                    "goals" -> goals.addAll(parseStringList(parser, "goal"))
                }
            }
            eventType = parser.next()
        }
        return DlofContent.JournalEntry(date, mood, entryText, gratitude, goals)
    }

    /** يقرأ عنصراً يحتوي قائمة عناصر فرعية نصية بسيطة (مثل <ingredients><ingredient>...) */
    private fun parseStringList(parser: XmlPullParser, childTag: String): List<String> {
        val list = mutableListOf<String>()
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.name == childTag) {
                list.add(parser.nextText().trim())
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseGeneric(parser: XmlPullParser): DlofContent.Generic {
        var type = ""; var element = ""; var body = ""
        val customType = parser.getAttributeValue(null, "customType")
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "type" -> type = parser.nextText().trim()
                    "element" -> element = parser.nextText().trim()
                    "body" -> body = parser.nextText().trim()
                }
            }
            eventType = parser.next()
        }
        return DlofContent.Generic(type, element, body, customType)
    }

    // ============================================================
    // تحليل محتوى حلقة المسلسل (EpisodeItem)
    // ============================================================

    private fun parseEpisodeItem(parser: XmlPullParser): DlofContent.EpisodeItem {
        var episodeNumber: Int? = null
        var seasonNumber: Int? = null
        var episodeTitle = ""
        var synopsis: String? = null
        var body = ""
        var durationSeconds: Int? = null
        var seriesTitle: String? = null
        var mediaRef: String? = null
        var releaseDate: String? = null
        var rating: String? = null
        var director: String? = null
        val writers = mutableListOf<String>()
        var thumbnailBase64: String? = null

        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "episodeNumber"   -> episodeNumber = parser.nextText().trim().toIntOrNull()
                    "seasonNumber"    -> seasonNumber  = parser.nextText().trim().toIntOrNull()
                    "episodeTitle"    -> episodeTitle  = parser.nextText().trim()
                    "synopsis"        -> synopsis      = parser.nextText().trim()
                    "body"            -> body          = parser.nextText().trim()
                    "durationSeconds" -> durationSeconds = parser.nextText().trim().toIntOrNull()
                    "duration"        -> durationSeconds = parser.nextText().trim().toIntOrNull()
                    "seriesTitle"     -> seriesTitle   = parser.nextText().trim()
                    "mediaRef"        -> mediaRef      = parser.nextText().trim()
                    "releaseDate"     -> releaseDate   = parser.nextText().trim()
                    "rating"          -> rating        = parser.nextText().trim()
                    "director"        -> director      = parser.nextText().trim()
                    "writers"         -> writers.addAll(parseStringList(parser, "writer"))
                    "thumbnailBase64" -> thumbnailBase64 = parser.nextText().trim()
                }
            }
            eventType = parser.next()
        }
        return DlofContent.EpisodeItem(
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            episodeTitle = episodeTitle,
            synopsis = synopsis,
            body = body,
            durationSeconds = durationSeconds,
            seriesTitle = seriesTitle,
            mediaRef = mediaRef,
            releaseDate = releaseDate,
            rating = rating,
            director = director,
            writers = writers,
            thumbnailBase64 = thumbnailBase64
        )
    }

    // ============================================================
    // تحليل لوحة القصة المصورة (ComicPanel)
    // ============================================================

    private fun parseComicPanel(parser: XmlPullParser): DlofContent.ComicPanel {
        var panelNumber: Int? = null
        var pageNumber: Int? = null
        var caption: String? = null
        var imageAttachmentRef: String? = null
        var altText: String? = null
        var panelWidth: Int? = null
        var backgroundColor: String? = null
        val dialogue = mutableListOf<org.dlof.reader.model.PanelDialogue>()

        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "panelNumber"         -> panelNumber = parser.nextText().trim().toIntOrNull()
                    "pageNumber"          -> pageNumber  = parser.nextText().trim().toIntOrNull()
                    "caption"             -> caption     = parser.nextText().trim()
                    "imageAttachmentRef"  -> imageAttachmentRef = parser.nextText().trim()
                    "altText"             -> altText     = parser.nextText().trim()
                    "panelWidth"          -> panelWidth  = parser.nextText().trim().toIntOrNull()
                    "backgroundColor"     -> backgroundColor = parser.nextText().trim()
                    "dialogue"            -> dialogue.addAll(parseDialogue(parser))
                }
            }
            eventType = parser.next()
        }
        return DlofContent.ComicPanel(
            panelNumber = panelNumber,
            pageNumber = pageNumber,
            caption = caption,
            dialogue = dialogue,
            imageAttachmentRef = imageAttachmentRef,
            altText = altText,
            panelWidth = panelWidth,
            backgroundColor = backgroundColor
        )
    }

    private fun parseDialogue(parser: XmlPullParser): List<org.dlof.reader.model.PanelDialogue> {
        val items = mutableListOf<org.dlof.reader.model.PanelDialogue>()
        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "line") {
                val typeVal = parser.getAttributeValue(null, "type") ?: "speech"
                val characterId = parser.getAttributeValue(null, "characterId")
                val characterName = parser.getAttributeValue(null, "characterName")
                val text = parser.nextText().trim()
                items += org.dlof.reader.model.PanelDialogue(
                    characterId = characterId,
                    characterName = characterName,
                    text = text,
                    type = org.dlof.reader.model.DialogueType.fromXml(typeVal)
                )
            }
            eventType = parser.next()
        }
        return items
    }

    // ============================================================
    // الكتابة: تحويل DlofDocument إلى XML
    // ============================================================

    fun write(doc: DlofDocument, output: OutputStream) {
        val writer = OutputStreamWriter(output, "UTF-8")
        writer.write("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
        writer.write("""<documentLoop xmlns="https://dlof.org/schema/1.0" version="${esc(doc.version)}" id="${esc(doc.id)}">""" + "\n")

        // metadata
        writer.write("  <metadata>\n")
        writer.write("    <title>${esc(doc.metadata.title)}</title>\n")
        writer.write("    <domain>${esc(doc.metadata.domain.xmlValue)}</domain>\n")
        doc.metadata.author?.let { writer.write("    <author>${esc(it)}</author>\n") }
        doc.metadata.createdAt?.let { writer.write("    <createdAt>${esc(it)}</createdAt>\n") }
        doc.metadata.updatedAt?.let { writer.write("    <updatedAt>${esc(it)}</updatedAt>\n") }
        writer.write("    <language>${esc(doc.metadata.language)}</language>\n")
        if (doc.metadata.tags.isNotEmpty()) {
            writer.write("    <tags>\n")
            doc.metadata.tags.forEach { writer.write("      <tag>${esc(it)}</tag>\n") }
            writer.write("    </tags>\n")
        }
        writer.write("  </metadata>\n")

        // loopLinks
        writer.write("  <loopLinks>\n")
        doc.loopLinks.previous?.let {
            writer.write("    <previous ref=\"${esc(it.ref)}\"${it.title?.let { t -> " title=\"${esc(t)}\"" } ?: ""}/>\n")
        }
        doc.loopLinks.next?.let {
            writer.write("    <next ref=\"${esc(it.ref)}\"${it.title?.let { t -> " title=\"${esc(t)}\"" } ?: ""}/>\n")
        }
        if (doc.loopLinks.loopRoot) {
            writer.write("    <loopRoot>true</loopRoot>\n")
        }
        writer.write("  </loopLinks>\n")

        // content
        writer.write("  <content>\n")
        writeContent(writer, doc.content)
        writer.write("  </content>\n")

        // attachments
        if (doc.attachments.isNotEmpty()) {
            writer.write("  <attachments>\n")
            doc.attachments.forEach { att ->
                writer.write("    <attachment id=\"${esc(att.id)}\" fileName=\"${esc(att.fileName)}\" mimeType=\"${esc(att.mimeType)}\" kind=\"${esc(att.kind.xmlValue)}\"${att.sizeBytes?.let { " sizeBytes=\"$it\"" } ?: ""}>\n")
                att.data?.let { writer.write("      <data>$it</data>\n") }
                att.uri?.let { writer.write("      <uri>${esc(it)}</uri>\n") }
                att.caption?.let { writer.write("      <caption>${esc(it)}</caption>\n") }
                writer.write("    </attachment>\n")
            }
            writer.write("  </attachments>\n")
        }

        // template
        doc.template?.let { t ->
            writer.write("  <template")
            t.ref?.let { writer.write(" ref=\"${esc(it)}\"") }
            t.primaryColor?.let { writer.write(" primaryColor=\"${esc(it)}\"") }
            t.secondaryColor?.let { writer.write(" secondaryColor=\"${esc(it)}\"") }
            t.backgroundColor?.let { writer.write(" backgroundColor=\"${esc(it)}\"") }
            t.textColor?.let { writer.write(" textColor=\"${esc(it)}\"") }
            t.fontFamily?.let { writer.write(" fontFamily=\"${esc(it)}\"") }
            writer.write(" layout=\"${esc(t.layout.xmlValue)}\"")
            t.headerAttachmentRef?.let { writer.write(" headerAttachmentRef=\"${esc(it)}\"") }
            writer.write("/>\n")
        }

        // richBlocks (كتل غنية)
        serializeRichBlocks(doc.richBlocks, writer)

        writer.write("</documentLoop>\n")
        writer.flush()
    }

    private fun writeContent(writer: OutputStreamWriter, content: DlofContent) {
        when (content) {
            is DlofContent.QA -> {
                writer.write("    <qaItem>\n")
                writer.write("      <question>${esc(content.question)}</question>\n")
                writer.write("      <answer>${esc(content.answer)}</answer>\n")
                content.explanation?.let { writer.write("      <explanation>${esc(it)}</explanation>\n") }
                content.difficulty?.let { writer.write("      <difficulty>${esc(it)}</difficulty>\n") }
                writer.write("    </qaItem>\n")
            }
            is DlofContent.BookChapter -> {
                writer.write("    <bookChapter>\n")
                content.chapterNumber?.let { writer.write("      <chapterNumber>$it</chapterNumber>\n") }
                writer.write("      <chapterTitle>${esc(content.chapterTitle)}</chapterTitle>\n")
                writer.write("      <text>${esc(content.text)}</text>\n")
                content.summary?.let { writer.write("      <summary>${esc(it)}</summary>\n") }
                writer.write("    </bookChapter>\n")
            }
            is DlofContent.TermDefinition -> {
                writer.write("    <termDefinition>\n")
                writer.write("      <term>${esc(content.term)}</term>\n")
                writer.write("      <definition>${esc(content.definition)}</definition>\n")
                content.example?.let { writer.write("      <example>${esc(it)}</example>\n") }
                writer.write("    </termDefinition>\n")
            }
            is DlofContent.InfoExplain -> {
                writer.write("    <infoExplain>\n")
                writer.write("      <topic>${esc(content.topic)}</topic>\n")
                writer.write("      <explanation>${esc(content.explanation)}</explanation>\n")
                content.source?.let { writer.write("      <source>${esc(it)}</source>\n") }
                writer.write("    </infoExplain>\n")
            }
            is DlofContent.Generic -> {
                val typeAttr = content.customType?.let { " customType=\"${esc(it)}\"" } ?: ""
                writer.write("    <genericItem$typeAttr>\n")
                writer.write("      <type>${esc(content.type)}</type>\n")
                writer.write("      <element>${esc(content.element)}</element>\n")
                writer.write("      <body>${esc(content.body)}</body>\n")
                writer.write("    </genericItem>\n")
            }
            is DlofContent.Recipe -> {
                writer.write("    <recipe>\n")
                writer.write("      <recipeName>${esc(content.recipeName)}</recipeName>\n")
                content.servings?.let { writer.write("      <servings>$it</servings>\n") }
                content.prepTimeMinutes?.let { writer.write("      <prepTimeMinutes>$it</prepTimeMinutes>\n") }
                content.cookTimeMinutes?.let { writer.write("      <cookTimeMinutes>$it</cookTimeMinutes>\n") }
                content.difficulty?.let { writer.write("      <difficulty>${esc(it)}</difficulty>\n") }
                if (content.ingredients.isNotEmpty()) {
                    writer.write("      <ingredients>\n")
                    content.ingredients.forEach { writer.write("        <ingredient>${esc(it)}</ingredient>\n") }
                    writer.write("      </ingredients>\n")
                }
                if (content.steps.isNotEmpty()) {
                    writer.write("      <steps>\n")
                    content.steps.forEach { writer.write("        <step>${esc(it)}</step>\n") }
                    writer.write("      </steps>\n")
                }
                content.nutritionNotes?.let { writer.write("      <nutritionNotes>${esc(it)}</nutritionNotes>\n") }
                content.tips?.let { writer.write("      <tips>${esc(it)}</tips>\n") }
                writer.write("    </recipe>\n")
            }
            is DlofContent.JournalEntry -> {
                writer.write("    <journalEntry>\n")
                writer.write("      <date>${esc(content.date)}</date>\n")
                content.mood?.let { writer.write("      <mood>${esc(it)}</mood>\n") }
                writer.write("      <entryText>${esc(content.entryText)}</entryText>\n")
                content.gratitude?.let { writer.write("      <gratitude>${esc(it)}</gratitude>\n") }
                if (content.goals.isNotEmpty()) {
                    writer.write("      <goals>\n")
                    content.goals.forEach { writer.write("        <goal>${esc(it)}</goal>\n") }
                    writer.write("      </goals>\n")
                }
                writer.write("    </journalEntry>\n")
            }
            is DlofContent.EpisodeItem -> {
                writer.write("    <episodeItem>\n")
                content.episodeNumber?.let { writer.write("      <episodeNumber>$it</episodeNumber>\n") }
                content.seasonNumber?.let { writer.write("      <seasonNumber>$it</seasonNumber>\n") }
                writer.write("      <episodeTitle>${esc(content.episodeTitle)}</episodeTitle>\n")
                content.synopsis?.let { writer.write("      <synopsis>${esc(it)}</synopsis>\n") }
                if (content.body.isNotEmpty()) writer.write("      <body>${esc(content.body)}</body>\n")
                content.durationSeconds?.let { writer.write("      <durationSeconds>$it</durationSeconds>\n") }
                content.seriesTitle?.let { writer.write("      <seriesTitle>${esc(it)}</seriesTitle>\n") }
                content.mediaRef?.let { writer.write("      <mediaRef>${esc(it)}</mediaRef>\n") }
                content.releaseDate?.let { writer.write("      <releaseDate>${esc(it)}</releaseDate>\n") }
                content.rating?.let { writer.write("      <rating>${esc(it)}</rating>\n") }
                content.director?.let { writer.write("      <director>${esc(it)}</director>\n") }
                if (content.writers.isNotEmpty()) {
                    writer.write("      <writers>\n")
                    content.writers.forEach { writer.write("        <writer>${esc(it)}</writer>\n") }
                    writer.write("      </writers>\n")
                }
                content.thumbnailBase64?.let { writer.write("      <thumbnailBase64>${it}</thumbnailBase64>\n") }
                writer.write("    </episodeItem>\n")
            }
            is DlofContent.ComicPanel -> {
                writer.write("    <comicPanel>\n")
                content.panelNumber?.let { writer.write("      <panelNumber>$it</panelNumber>\n") }
                content.pageNumber?.let { writer.write("      <pageNumber>$it</pageNumber>\n") }
                content.caption?.let { writer.write("      <caption>${esc(it)}</caption>\n") }
                content.imageAttachmentRef?.let { writer.write("      <imageAttachmentRef>${esc(it)}</imageAttachmentRef>\n") }
                content.altText?.let { writer.write("      <altText>${esc(it)}</altText>\n") }
                content.panelWidth?.let { writer.write("      <panelWidth>$it</panelWidth>\n") }
                content.backgroundColor?.let { writer.write("      <backgroundColor>${esc(it)}</backgroundColor>\n") }
                if (content.dialogue.isNotEmpty()) {
                    writer.write("      <dialogue>\n")
                    content.dialogue.forEach { d ->
                        writer.write("        <line type=\"${esc(d.type.xmlValue)}\"")
                        d.characterId?.let { writer.write(" characterId=\"${esc(it)}\"") }
                        d.characterName?.let { writer.write(" characterName=\"${esc(it)}\"") }
                        writer.write(">${esc(d.text)}</line>\n")
                    }
                    writer.write("      </dialogue>\n")
                }
                writer.write("    </comicPanel>\n")
            }
        }
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

// ══════════════════════════════════════════════════════════════
// امتداد DlofParser لدعم الكتل الغنية (RichBlocks)
// ══════════════════════════════════════════════════════════════

/**
 * يُحلِّل عنصر <richBlocks> ويُعيد قائمة RichBlock.
 * يُستدعى من parse() عند مصادفة <richBlocks>.
 */
fun DlofParser.parseRichBlocks(parser: org.xmlpull.v1.XmlPullParser): List<org.dlof.reader.model.RichBlock> {
    val blocks = mutableListOf<org.dlof.reader.model.RichBlock>()
    val depth = parser.depth
    var eventType = parser.next()
    while (!(eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
            parseOneRichBlock(parser, parser.name)?.let { blocks.add(it) }
        }
        eventType = parser.next()
    }
    return blocks
}

/**
 * يُحلِّل كتلة غنية واحدة حسب اسم الوسم — يُستخدم من parseRichBlocks()
 * ومن parseListBlock() لدعم التداخل (أي كتلة يمكن أن تُضمَّن داخل عنصر قائمة).
 */
fun parseOneRichBlock(parser: org.xmlpull.v1.XmlPullParser, tag: String): org.dlof.reader.model.RichBlock? {
    return when (tag) {
        "youtubeEmbed"  -> parseYouTubeBlock(parser)
        "urlLink"       -> parseUrlLinkBlock(parser)
        "githubLink"    -> parseGitHubLinkBlock(parser)
        "codeBlock"     -> parseCodeBlock(parser)
        "buttonBlock"   -> parseButtonBlock(parser)
        "largeVideoRef" -> parseLargeVideoBlock(parser)
        "noteBlock"     -> parseNoteBlock(parser)
        "mathBlock"     -> parseMathBlock(parser)
        "pollBlock"     -> parsePollBlock(parser)
        "bookmarkBlock" -> parseBookmarkBlock(parser)
        "timerBlock"    -> parseTimerBlock(parser)
        "playlistBlock"     -> parsePlaylistBlock(parser)
        "downloadableBlock" -> parseDownloadableBlock(parser)
        "textBlock"     -> parseTextBlock(parser)
        "listBlock"     -> parseListBlock(parser)
        "imageBlock"    -> parseImageBlock(parser)
        else -> null
    }
}

private fun parseYouTubeBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.YouTubeEmbed {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var videoId = ""; var caption: String? = null; var startSec: Int? = null
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "videoId" -> videoId = parser.nextText().trim()
            "caption" -> caption = parser.nextText().trim()
            "startSeconds" -> startSec = parser.nextText().trim().toIntOrNull()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.YouTubeEmbed(id, videoId, caption, startSec)
}

private fun parseUrlLinkBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.UrlLink {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var url = ""; var label = "رابط"; var desc: String? = null
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "url" -> url = parser.nextText().trim()
            "label" -> label = parser.nextText().trim()
            "description" -> desc = parser.nextText().trim()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.UrlLink(id, url, label, desc)
}

private fun parseGitHubLinkBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.GitHubLink {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var url = ""; var label = "مستودع"; var desc: String? = null
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "repoUrl" -> url = parser.nextText().trim()
            "label" -> label = parser.nextText().trim()
            "description" -> desc = parser.nextText().trim()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.GitHubLink(id, url, label, desc)
}

private fun parseCodeBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.CodeBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var lang = "text"; var code = ""; var caption: String? = null
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "language" -> lang = parser.nextText().trim()
            "code" -> code = parser.nextText()          // لا trim لأن المسافات في الكود مهمة
            "caption" -> caption = parser.nextText().trim()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.CodeBlock(id, lang, code, caption)
}

private fun parseButtonBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.ButtonBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var label = "زر"; var url = ""; var style = org.dlof.reader.model.ButtonStyle.PRIMARY
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "label" -> label = parser.nextText().trim()
            "url" -> url = parser.nextText().trim()
            "style" -> style = org.dlof.reader.model.ButtonStyle.fromXml(parser.nextText().trim())
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.ButtonBlock(id, label, url, style)
}

private fun parseLargeVideoBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.LargeVideoRef {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var uri = ""; var fileName = "فيديو"; var sizeBytes: Long? = null
    var mimeType = "video/mp4"; var caption: String? = null; var thumb: String? = null
    var title: String? = null; var autoPlay = false
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "uri" -> uri = parser.nextText().trim()
            "fileName" -> fileName = parser.nextText().trim()
            "sizeBytes" -> sizeBytes = parser.nextText().trim().toLongOrNull()
            "mimeType" -> mimeType = parser.nextText().trim()
            "caption" -> caption = parser.nextText().trim()
            "thumbnailBase64" -> thumb = parser.nextText().trim()
            "title" -> title = parser.nextText().trim()
            "autoPlay" -> autoPlay = parser.nextText().trim().toBoolean()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.LargeVideoRef(id, uri, fileName, sizeBytes, mimeType, caption, thumb, title, autoPlay)
}

private fun parseNoteBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.NoteBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var noteType = org.dlof.reader.model.NoteType.INFO
    var title: String? = null; var body = ""
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "noteType" -> noteType = org.dlof.reader.model.NoteType.fromXml(parser.nextText().trim())
            "title" -> title = parser.nextText().trim()
            "body" -> body = parser.nextText().trim()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.NoteBlock(id, noteType, title, body)
}

private fun parseMathBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.MathBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var latex = ""; var caption: String? = null; var isInline = false
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "latex" -> latex = parser.nextText()
            "caption" -> caption = parser.nextText().trim()
            "isInline" -> isInline = parser.nextText().trim().toBoolean()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.MathBlock(id, latex, caption, isInline)
}

private fun parsePollBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.PollBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var question = ""; val options = mutableListOf<String>(); var allowMultiple = false
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "question" -> question = parser.nextText().trim()
            "options" -> {
                val optDepth = parser.depth; var oet = parser.next()
                while (!(oet == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == optDepth)) {
                    if (oet == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "option") {
                        options.add(parser.nextText().trim())
                    }
                    oet = parser.next()
                }
            }
            "allowMultiple" -> allowMultiple = parser.nextText().trim().toBoolean()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.PollBlock(id, question, options, allowMultiple)
}

private fun parseBookmarkBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.BookmarkBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var url = ""; var title = ""; var desc: String? = null
    var imageUrl: String? = null; var siteName: String? = null
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "url" -> url = parser.nextText().trim()
            "title" -> title = parser.nextText().trim()
            "description" -> desc = parser.nextText().trim()
            "imageUrl" -> imageUrl = parser.nextText().trim()
            "siteName" -> siteName = parser.nextText().trim()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.BookmarkBlock(id, url, title, desc, imageUrl, siteName)
}

private fun parseTimerBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.TimerBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var label = "مؤقت"; var durationSeconds = 60; var countDown = true; var autoStart = false
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "label" -> label = parser.nextText().trim()
            "durationSeconds" -> durationSeconds = parser.nextText().trim().toIntOrNull() ?: 60
            "countDown" -> countDown = parser.nextText().trim().toBoolean()
            "autoStart" -> autoStart = parser.nextText().trim().toBoolean()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.TimerBlock(id, label, durationSeconds, countDown, autoStart)
}

private fun parsePlaylistBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.PlaylistBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var title = ""; var description: String? = null
    val items = mutableListOf<org.dlof.reader.model.PlaylistItem>()
    var autoPlay = false; var loopAll = false
    var style = org.dlof.reader.model.PlaylistStyle.VERTICAL
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "title" -> title = parser.nextText().trim()
            "description" -> description = parser.nextText().trim()
            "items" -> {
                val itemsDepth = parser.depth; var itemsEt = parser.next()
                while (!(itemsEt == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == itemsDepth)) {
                    if (itemsEt == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                        items.add(parsePlaylistItem(parser))
                    }
                    itemsEt = parser.next()
                }
            }
            "autoPlay" -> autoPlay = parser.nextText().trim().toBoolean()
            "loopAll" -> loopAll = parser.nextText().trim().toBoolean()
            "style" -> style = org.dlof.reader.model.PlaylistStyle.fromXml(parser.nextText().trim())
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.PlaylistBlock(id, title, description, items, autoPlay, loopAll, style)
}

private fun parsePlaylistItem(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.PlaylistItem {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var uri = ""; var itemTitle = ""; var durationSeconds: Int? = null
    var mimeType = "video/mp4"; var thumb: String? = null
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "uri" -> uri = parser.nextText().trim()
            "title" -> itemTitle = parser.nextText().trim()
            "durationSeconds" -> durationSeconds = parser.nextText().trim().toIntOrNull()
            "mimeType" -> mimeType = parser.nextText().trim()
            "thumbnailBase64" -> thumb = parser.nextText().trim()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.PlaylistItem(id, uri, itemTitle, durationSeconds, mimeType, thumb)
}

private fun parseDownloadableBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.DownloadableBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var uri = ""; var fileName = "ملف"; var mimeType = "application/octet-stream"
    var sizeBytes: Long? = null; var caption: String? = null; var preview: String? = null
    var downloadLabel = "تنزيل"
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "uri" -> uri = parser.nextText().trim()
            "fileName" -> fileName = parser.nextText().trim()
            "mimeType" -> mimeType = parser.nextText().trim()
            "sizeBytes" -> sizeBytes = parser.nextText().trim().toLongOrNull()
            "caption" -> caption = parser.nextText().trim()
            "previewBase64" -> preview = parser.nextText().trim()
            "downloadLabel" -> downloadLabel = parser.nextText().trim()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.DownloadableBlock(id, uri, fileName, mimeType, sizeBytes, caption, preview, downloadLabel)
}

private fun parseTextBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.TextBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var title: String? = null
    val lines = mutableListOf<String>()
    var style = org.dlof.reader.model.TextBlockStyle.PARAGRAPH
    var align = org.dlof.reader.model.TextBlockAlign.START
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "title" -> title = parser.nextText().trim()
            "style" -> style = org.dlof.reader.model.TextBlockStyle.fromXml(parser.nextText().trim())
            "align" -> align = org.dlof.reader.model.TextBlockAlign.fromXml(parser.nextText().trim())
            "lines" -> {
                val linesDepth = parser.depth; var linesEt = parser.next()
                while (!(linesEt == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == linesDepth)) {
                    if (linesEt == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "line") {
                        if (lines.size < org.dlof.reader.model.RichBlock.TextBlock.MAX_LINES) {
                            lines.add(parser.nextText())
                        } else {
                            parser.nextText()
                        }
                    }
                    linesEt = parser.next()
                }
            }
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.TextBlock(id, title, lines, style, align)
}

private fun parseImageBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.ImageBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var imageBase64: String? = null
    var uri: String? = null
    var caption: String? = null
    var altText: String? = null
    var linkUrl: String? = null
    var cornerStyle = org.dlof.reader.model.ImageCornerStyle.ROUNDED
    var fit = org.dlof.reader.model.ImageFit.COVER
    var alignment = org.dlof.reader.model.ImageAlignment.CENTER
    var widthPercent = 100
    var fullBleed = false
    var aspectRatio: Float? = null
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "imageBase64"  -> imageBase64 = parser.nextText().trim()
            "uri"          -> uri = parser.nextText().trim()
            "caption"      -> caption = parser.nextText().trim()
            "altText"      -> altText = parser.nextText().trim()
            "linkUrl"      -> linkUrl = parser.nextText().trim()
            "cornerStyle"  -> cornerStyle = org.dlof.reader.model.ImageCornerStyle.fromXml(parser.nextText().trim())
            "fit"          -> fit = org.dlof.reader.model.ImageFit.fromXml(parser.nextText().trim())
            "alignment"    -> alignment = org.dlof.reader.model.ImageAlignment.fromXml(parser.nextText().trim())
            "widthPercent" -> widthPercent = parser.nextText().trim().toIntOrNull() ?: 100
            "fullBleed"    -> fullBleed = parser.nextText().trim().toBoolean()
            "aspectRatio"  -> aspectRatio = parser.nextText().trim().toFloatOrNull()
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.ImageBlock(
        id = id, imageBase64 = imageBase64, uri = uri, caption = caption, altText = altText,
        linkUrl = linkUrl, cornerStyle = cornerStyle, fit = fit, alignment = alignment,
        widthPercent = widthPercent, fullBleed = fullBleed, aspectRatio = aspectRatio
    )
}

private fun parseListBlock(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.RichBlock.ListBlock {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var title: String? = null
    var listStyle = org.dlof.reader.model.ListStyle.BULLET
    val items = mutableListOf<org.dlof.reader.model.ListItem>()
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "title" -> title = parser.nextText().trim()
            "listStyle" -> listStyle = org.dlof.reader.model.ListStyle.fromXml(parser.nextText().trim())
            "items" -> {
                val itemsDepth = parser.depth; var itemsEt = parser.next()
                while (!(itemsEt == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == itemsDepth)) {
                    if (itemsEt == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                        items.add(parseListItem(parser))
                    }
                    itemsEt = parser.next()
                }
            }
        }
        et = parser.next()
    }
    return org.dlof.reader.model.RichBlock.ListBlock(id, title, listStyle, items)
}

private fun parseListItem(parser: org.xmlpull.v1.XmlPullParser): org.dlof.reader.model.ListItem {
    val id = parser.getAttributeValue(null, "id") ?: newBlockId()
    var text = ""; var icon: String? = null; var checked: Boolean? = null
    var nested: org.dlof.reader.model.RichBlock? = null
    val depth = parser.depth; var et = parser.next()
    while (!(et == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth)) {
        if (et == org.xmlpull.v1.XmlPullParser.START_TAG) when (parser.name) {
            "text" -> text = parser.nextText()
            "icon" -> icon = parser.nextText().trim()
            "checked" -> checked = parser.nextText().trim().toBoolean()
            // أي وسم آخر داخل <item> غير الحقول أعلاه يُعامَل كـ"أي شيء" —
            // كتلة غنية متداخلة (نص، زر، كود، رابط، صورة قابلة للتنزيل…)
            else -> nested = parseOneRichBlock(parser, parser.name) ?: nested
        }
        et = parser.next()
    }
    return org.dlof.reader.model.ListItem(id, text, icon, checked, nested)
}

private fun newBlockId() = "rb-" + java.util.UUID.randomUUID().toString().take(8)

/**
 * تسلسل (serialize) قائمة RichBlocks إلى XML.
 * يُستخدم من DlofParser.serialize().
 */
fun serializeRichBlocks(blocks: List<org.dlof.reader.model.RichBlock>, writer: java.io.OutputStreamWriter) {
    if (blocks.isEmpty()) return
    writer.write("  <richBlocks>\n")
    blocks.forEach { block ->
        when (block) {
            is org.dlof.reader.model.RichBlock.YouTubeEmbed -> {
                writer.write("    <youtubeEmbed id=\"${block.id}\">\n")
                writer.write("      <videoId>${escape(block.videoId)}</videoId>\n")
                block.caption?.let { writer.write("      <caption>${escape(it)}</caption>\n") }
                block.startSeconds?.let { writer.write("      <startSeconds>$it</startSeconds>\n") }
                writer.write("    </youtubeEmbed>\n")
            }
            is org.dlof.reader.model.RichBlock.UrlLink -> {
                writer.write("    <urlLink id=\"${block.id}\">\n")
                writer.write("      <url>${escape(block.url)}</url>\n")
                writer.write("      <label>${escape(block.label)}</label>\n")
                block.description?.let { writer.write("      <description>${escape(it)}</description>\n") }
                writer.write("    </urlLink>\n")
            }
            is org.dlof.reader.model.RichBlock.GitHubLink -> {
                writer.write("    <githubLink id=\"${block.id}\">\n")
                writer.write("      <repoUrl>${escape(block.repoUrl)}</repoUrl>\n")
                writer.write("      <label>${escape(block.label)}</label>\n")
                block.description?.let { writer.write("      <description>${escape(it)}</description>\n") }
                writer.write("    </githubLink>\n")
            }
            is org.dlof.reader.model.RichBlock.CodeBlock -> {
                writer.write("    <codeBlock id=\"${block.id}\">\n")
                writer.write("      <language>${escape(block.language)}</language>\n")
                writer.write("      <code><![CDATA[${block.code}]]></code>\n")
                block.caption?.let { writer.write("      <caption>${escape(it)}</caption>\n") }
                writer.write("    </codeBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.ButtonBlock -> {
                writer.write("    <buttonBlock id=\"${block.id}\">\n")
                writer.write("      <label>${escape(block.label)}</label>\n")
                writer.write("      <url>${escape(block.url)}</url>\n")
                writer.write("      <style>${block.style.xmlValue}</style>\n")
                writer.write("    </buttonBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.LargeVideoRef -> {
                writer.write("    <largeVideoRef id=\"${block.id}\">\n")
                writer.write("      <uri>${escape(block.uri)}</uri>\n")
                writer.write("      <fileName>${escape(block.fileName)}</fileName>\n")
                block.title?.let { writer.write("      <title>${escape(it)}</title>\n") }
                block.sizeBytes?.let { writer.write("      <sizeBytes>$it</sizeBytes>\n") }
                writer.write("      <mimeType>${escape(block.mimeType)}</mimeType>\n")
                block.caption?.let { writer.write("      <caption>${escape(it)}</caption>\n") }
                block.thumbnailBase64?.let { writer.write("      <thumbnailBase64>${it}</thumbnailBase64>\n") }
                writer.write("      <autoPlay>${block.autoPlay}</autoPlay>\n")
                writer.write("    </largeVideoRef>\n")
            }
            is org.dlof.reader.model.RichBlock.NoteBlock -> {
                writer.write("    <noteBlock id=\"${block.id}\">\n")
                writer.write("      <noteType>${block.noteType.xmlValue}</noteType>\n")
                block.title?.let { writer.write("      <title>${escape(it)}</title>\n") }
                writer.write("      <body>${escape(block.body)}</body>\n")
                writer.write("    </noteBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.MathBlock -> {
                writer.write("    <mathBlock id=\"${block.id}\">\n")
                writer.write("      <latex><![CDATA[${block.latex}]]></latex>\n")
                block.caption?.let { writer.write("      <caption>${escape(it)}</caption>\n") }
                writer.write("      <isInline>${block.isInline}</isInline>\n")
                writer.write("    </mathBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.PollBlock -> {
                writer.write("    <pollBlock id=\"${block.id}\">\n")
                writer.write("      <question>${escape(block.question)}</question>\n")
                if (block.options.isNotEmpty()) {
                    writer.write("      <options>\n")
                    block.options.forEach { writer.write("        <option>${escape(it)}</option>\n") }
                    writer.write("      </options>\n")
                }
                writer.write("      <allowMultiple>${block.allowMultiple}</allowMultiple>\n")
                writer.write("    </pollBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.BookmarkBlock -> {
                writer.write("    <bookmarkBlock id=\"${block.id}\">\n")
                writer.write("      <url>${escape(block.url)}</url>\n")
                writer.write("      <title>${escape(block.title)}</title>\n")
                block.description?.let { writer.write("      <description>${escape(it)}</description>\n") }
                block.imageUrl?.let { writer.write("      <imageUrl>${escape(it)}</imageUrl>\n") }
                block.siteName?.let { writer.write("      <siteName>${escape(it)}</siteName>\n") }
                writer.write("    </bookmarkBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.TimerBlock -> {
                writer.write("    <timerBlock id=\"${block.id}\">\n")
                writer.write("      <label>${escape(block.label)}</label>\n")
                writer.write("      <durationSeconds>${block.durationSeconds}</durationSeconds>\n")
                writer.write("      <countDown>${block.countDown}</countDown>\n")
                writer.write("      <autoStart>${block.autoStart}</autoStart>\n")
                writer.write("    </timerBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.PlaylistBlock -> {
                writer.write("    <playlistBlock id=\"${block.id}\">\n")
                writer.write("      <title>${escape(block.title)}</title>\n")
                block.description?.let { writer.write("      <description>${escape(it)}</description>\n") }
                if (block.items.isNotEmpty()) {
                    writer.write("      <items>\n")
                    block.items.forEach { item ->
                        writer.write("        <item id=\"${item.id}\">\n")
                        writer.write("          <uri>${escape(item.uri)}</uri>\n")
                        writer.write("          <title>${escape(item.title)}</title>\n")
                        item.durationSeconds?.let { writer.write("          <durationSeconds>$it</durationSeconds>\n") }
                        writer.write("          <mimeType>${escape(item.mimeType)}</mimeType>\n")
                        item.thumbnailBase64?.let { writer.write("          <thumbnailBase64>${it}</thumbnailBase64>\n") }
                        writer.write("        </item>\n")
                    }
                    writer.write("      </items>\n")
                }
                writer.write("      <autoPlay>${block.autoPlay}</autoPlay>\n")
                writer.write("      <loopAll>${block.loopAll}</loopAll>\n")
                writer.write("      <style>${block.style.xmlValue}</style>\n")
                writer.write("    </playlistBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.DownloadableBlock -> {
                writer.write("    <downloadableBlock id=\"${block.id}\">\n")
                writer.write("      <uri>${escape(block.uri)}</uri>\n")
                writer.write("      <fileName>${escape(block.fileName)}</fileName>\n")
                writer.write("      <mimeType>${escape(block.mimeType)}</mimeType>\n")
                block.sizeBytes?.let { writer.write("      <sizeBytes>$it</sizeBytes>\n") }
                block.caption?.let { writer.write("      <caption>${escape(it)}</caption>\n") }
                block.previewBase64?.let { writer.write("      <previewBase64>${it}</previewBase64>\n") }
                writer.write("      <downloadLabel>${escape(block.downloadLabel)}</downloadLabel>\n")
                writer.write("    </downloadableBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.TextBlock -> {
                writer.write("    <textBlock id=\"${block.id}\">\n")
                block.title?.let { writer.write("      <title>${escape(it)}</title>\n") }
                writer.write("      <style>${block.style.xmlValue}</style>\n")
                writer.write("      <align>${block.align.xmlValue}</align>\n")
                if (block.lines.isNotEmpty()) {
                    writer.write("      <lines>\n")
                    block.lines.take(org.dlof.reader.model.RichBlock.TextBlock.MAX_LINES).forEach {
                        writer.write("        <line><![CDATA[${it}]]></line>\n")
                    }
                    writer.write("      </lines>\n")
                }
                writer.write("    </textBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.ListBlock -> {
                writer.write("    <listBlock id=\"${block.id}\">\n")
                block.title?.let { writer.write("      <title>${escape(it)}</title>\n") }
                writer.write("      <listStyle>${block.listStyle.xmlValue}</listStyle>\n")
                if (block.items.isNotEmpty()) {
                    writer.write("      <items>\n")
                    block.items.forEach { item -> serializeListItem(item, writer) }
                    writer.write("      </items>\n")
                }
                writer.write("    </listBlock>\n")
            }
            is org.dlof.reader.model.RichBlock.ImageBlock -> {
                writer.write("    <imageBlock id=\"${block.id}\">\n")
                block.imageBase64?.let { writer.write("      <imageBase64>${it}</imageBase64>\n") }
                block.uri?.let { writer.write("      <uri>${escape(it)}</uri>\n") }
                block.caption?.let { writer.write("      <caption>${escape(it)}</caption>\n") }
                block.altText?.let { writer.write("      <altText>${escape(it)}</altText>\n") }
                block.linkUrl?.let { writer.write("      <linkUrl>${escape(it)}</linkUrl>\n") }
                writer.write("      <cornerStyle>${block.cornerStyle.xmlValue}</cornerStyle>\n")
                writer.write("      <fit>${block.fit.xmlValue}</fit>\n")
                writer.write("      <alignment>${block.alignment.xmlValue}</alignment>\n")
                writer.write("      <widthPercent>${block.widthPercent}</widthPercent>\n")
                writer.write("      <fullBleed>${block.fullBleed}</fullBleed>\n")
                block.aspectRatio?.let { writer.write("      <aspectRatio>${it}</aspectRatio>\n") }
                writer.write("    </imageBlock>\n")
            }
        }
    }
    writer.write("  </richBlocks>\n")
}

/**
 * يسلسل عنصر قائمة واحد، بما فيه أي كتلة متداخلة داخله ("أي شيء").
 */
private fun serializeListItem(item: org.dlof.reader.model.ListItem, writer: java.io.OutputStreamWriter) {
    writer.write("        <item id=\"${item.id}\">\n")
    if (item.text.isNotEmpty()) writer.write("          <text><![CDATA[${item.text}]]></text>\n")
    item.icon?.let { writer.write("          <icon>${escape(it)}</icon>\n") }
    item.checked?.let { writer.write("          <checked>$it</checked>\n") }
    item.nestedBlock?.let { nested ->
        // نعيد استخدام نفس منطق تسلسل RichBlock لكتلة واحدة، بمسافة بادئة إضافية
        val nestedList = listOf(nested)
        val buffer = java.io.ByteArrayOutputStream()
        val nestedWriter = java.io.OutputStreamWriter(buffer, Charsets.UTF_8)
        serializeRichBlocks(nestedList, nestedWriter)
        nestedWriter.flush()
        val nestedXml = buffer.toString(Charsets.UTF_8.name())
            .lineSequence()
            .filter { it.isNotBlank() && it.trim() != "<richBlocks>" && it.trim() != "</richBlocks>" }
            .joinToString("\n") { "  $it" }
        writer.write(nestedXml)
        writer.write("\n")
    }
    writer.write("        </item>\n")
}

private fun escape(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&apos;")
