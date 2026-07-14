package org.dlof.reader.model

/**
 * يمثّل ملف DLoF كاملاً بعد تحليله من XML.
 * Represents a fully parsed .dlof document.
 */
data class DlofDocument(
    val id: String,
    val version: String = "1.0",
    val metadata: Metadata,
    val loopLinks: LoopLinks,
    val content: DlofContent,
    val attachments: List<Attachment> = emptyList(),
    val template: Template? = null,
    val richBlocks: List<RichBlock> = emptyList()   // ← NEW: كتل غنية (YouTube, URL, GitHub, Code, Button)
)

data class Metadata(
    val title: String,
    val domain: Domain,
    val author: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val language: String = "ar",
    val tags: List<String> = emptyList()
)

/**
 * تصنيف "المجال" (Domain) لكل ملف DLoF — يحدّد نوع المحتوى الذي يمثّله الملف
 * ويُستخدم في: منتقي المجال بشاشة التحرير، تلوين خريطة الحلقة، فلاتر مدير
 * الملفات وشاشة الإحصاءات، وتخزينه في meta.json عند تصدير .dlofpkg.
 *
 * ⚠️ عند إضافة قيمة جديدة هنا، يجب أيضاً:
 *   1) إضافتها إلى تعداد DomainType في spec/schema/dlof.xsd
 *   2) إضافة فرع لها في domainColor() بملف LoopMapScreen.kt
 *   3) إضافة أيقونة لها في domainIcon() بملف StatsScreen.kt
 *   4) إضافة مفتاح لها في domainDisplayNames/domainColors بملف DlofFileManagerScreen.kt
 * (كل هذه الأماكن مُحدَّثة بالفعل لتغطية كل القيم أدناه.)
 */
enum class Domain(val xmlValue: String, val arabicLabel: String) {
    EDUCATION("education", "تعليم"),
    BOOK("book", "كتاب"),
    INFO_APP("infoApp", "تطبيق معلومات"),
    INFO_LOOP("infoLoop", "حلقة معلومات"),
    RECIPE("recipe", "وصفة طعام"),
    JOURNAL("journal", "يوميات"),
    SERIES("series", "مسلسل / سلسلة"),
    COMIC("comic", "قصة مصورة"),
    MANGA("manga", "مانجا"),                  // ⑦ مجال جديد: مانجا (تمييزاً عن القصص المصورة الغربية)
    PODCAST("podcast", "بودكاست"),
    MUSIC("music", "موسيقى / ألبوم"),         // ⑧ مجال جديد: محتوى موسيقي (فرق، ألبومات، أغانٍ)
    CHARACTERS("characters", "شخصيات"),
    SOFTWARE("software", "برمجيات وتقنية"),   // ⑨ مجال جديد: توثيق تقني، تنزيلات برامج
    BLOG("blog", "مدونة / مقال"),             // ⑩ مجال جديد: تدوينات ومقالات
    NEWS("news", "أخبار"),                    // ⑪ مجال جديد: محتوى إخباري
    ART("art", "فن وتصميم"),                  // ⑫ مجال جديد: أعمال فنية وتصميم
    GAME("game", "لعبة"),                     // ⑬ مجال جديد: أدلة وقصص الألعاب
    BUSINESS("business", "أعمال"),            // ⑭ مجال جديد: محتوى تجاري ومهني
    CUSTOM("custom", "مخصص");

    companion object {
        fun fromXml(value: String): Domain =
            entries.firstOrNull { it.xmlValue == value } ?: CUSTOM
    }
}

data class LoopLinks(
    val previous: LinkRef? = null,
    val next: LinkRef? = null,
    val loopRoot: Boolean = false
)

data class LinkRef(
    val ref: String,
    val title: String? = null
)

/**
 * المحتوى: نوع مغلق (sealed) يضمن التعامل مع كل الأنواع المعروفة
 * بأمان أثناء البناء (compile-time safety).
 */
sealed class DlofContent {

    data class Generic(
        val type: String,
        val element: String,
        val body: String,
        val customType: String? = null
    ) : DlofContent()

    data class QA(
        val question: String,
        val answer: String,
        val explanation: String? = null,
        val difficulty: String? = null
    ) : DlofContent()

    data class BookChapter(
        val chapterNumber: Int? = null,
        val chapterTitle: String,
        val text: String,
        val summary: String? = null
    ) : DlofContent()

    data class TermDefinition(
        val term: String,
        val definition: String,
        val example: String? = null
    ) : DlofContent()

    data class InfoExplain(
        val topic: String,
        val explanation: String,
        val source: String? = null
    ) : DlofContent()

    // ③ نوع محتوى جديد: وصفة طعام
    data class Recipe(
        val recipeName: String,
        val servings: Int? = null,
        val prepTimeMinutes: Int? = null,
        val cookTimeMinutes: Int? = null,
        val difficulty: String? = null,   // "سهل" / "متوسط" / "صعب"
        val ingredients: List<String> = emptyList(),
        val steps: List<String> = emptyList(),
        val nutritionNotes: String? = null,
        val tips: String? = null
    ) : DlofContent()

    // ④ نوع محتوى جديد: يوميات شخصية
    data class JournalEntry(
        val date: String,            // ISO 8601: "2025-06-27"
        val mood: String? = null,    // "سعيد" / "حزين" / "محايد" …
        val entryText: String,
        val gratitude: String? = null,
        val goals: List<String> = emptyList()
    ) : DlofContent()

    // ⑤ نوع محتوى جديد: حلقة مسلسل (series episode)
    data class EpisodeItem(
        val episodeNumber: Int? = null,
        val seasonNumber: Int? = null,
        val episodeTitle: String,
        val synopsis: String? = null,       // ملخص قصير
        val body: String = "",             // النص الكامل / الوصف التفصيلي
        val durationSeconds: Int? = null,   // مدة الحلقة بالثواني
        val seriesTitle: String? = null,
        val mediaRef: String? = null,       // مسار نسبي للفيديو في media/
        val releaseDate: String? = null,    // ISO 8601: "2026-01-01"
        val rating: String? = null,         // "PG" / "18+" …
        val director: String? = null,
        val writers: List<String> = emptyList(),
        val thumbnailBase64: String? = null // صورة مصغرة للحلقة (مختارة يدوياً أو مستخرجة من الفيديو)
    ) : DlofContent()

    // ⑥ نوع محتوى جديد: لوحة قصة مصورة (comic panel)
    data class ComicPanel(
        val panelNumber: Int? = null,
        val pageNumber: Int? = null,
        val caption: String? = null,        // نص التعليق على اللوحة
        val dialogue: List<PanelDialogue> = emptyList(),  // الحوارات
        val imageAttachmentRef: String? = null,  // معرّف المرفق الصورة
        val altText: String? = null,        // وصف بديل للصورة
        val panelWidth: Int? = null,        // عرض اللوحة بالنسبة للصفحة (1-12 كشبكة)
        val backgroundColor: String? = null
    ) : DlofContent()
}

/**
 * كتل غنية يمكن إضافتها لأي ملف DLoF:
 * YouTube embed, URL link, GitHub link, Code block, Button, Video (حتى 5 جيجابايت).
 */
data class PanelDialogue(
    val characterId: String? = null,    // معرّف الشخصية (من yime.kt)
    val characterName: String? = null,  // اسم الشخصية
    val text: String,                   // نص الحوار
    val type: DialogueType = DialogueType.SPEECH
)

enum class DialogueType(val xmlValue: String, val arabicLabel: String) {
    SPEECH("speech", "كلام"),
    THOUGHT("thought", "تفكير"),
    NARRATION("narration", "راوٍ"),
    WHISPER("whisper", "همس"),
    SHOUT("shout", "صراخ");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: SPEECH
    }
}

sealed class RichBlock {
    abstract val id: String

    /** فيديو يوتيوب مضمَّن (عبر رابط أو معرّف الفيديو) */
    data class YouTubeEmbed(
        override val id: String,
        val videoId: String,       // مثل: dQw4w9WgXcQ
        val caption: String? = null,
        val startSeconds: Int? = null
    ) : RichBlock()

    /** رابط URL عام مع عنوان وأيقونة */
    data class UrlLink(
        override val id: String,
        val url: String,
        val label: String,
        val description: String? = null
    ) : RichBlock()

    /** رابط GitHub (مستودع، ملف، أو Issue) */
    data class GitHubLink(
        override val id: String,
        val repoUrl: String,
        val label: String,
        val description: String? = null
    ) : RichBlock()

    /** كتلة كود مع تمييز لغة */
    data class CodeBlock(
        override val id: String,
        val language: String,      // kotlin, python, js, bash…
        val code: String,
        val caption: String? = null
    ) : RichBlock()

    /** زر قابل للضغط يفتح رابطاً أو يُشغِّل إجراءً */
    data class ButtonBlock(
        override val id: String,
        val label: String,
        val url: String,           // الرابط الذي يُفتَح عند الضغط
        val style: ButtonStyle = ButtonStyle.PRIMARY
    ) : RichBlock()

    /**
     * فيديو خارجي (URI) حتى 5 جيجابايت — لا يُضمَّن base64
     * بل يُرفَق كمسار/رابط خارجي مع بيانات وصفية.
     */
    data class LargeVideoRef(
        override val id: String,
        val uri: String,           // content-uri أو file-uri أو https://
        val fileName: String,
        val sizeBytes: Long? = null,
        val mimeType: String = "video/mp4",
        val caption: String? = null,
        val thumbnailBase64: String? = null,  // صورة مصغرة اختيارية (صغيرة)
        val title: String? = null,  // عنوان معروض للفيديو (مستقل عن اسم الملف)
        val autoPlay: Boolean = false   // تشغيل تلقائي فور فتح الصفحة/الكتلة
    ) : RichBlock()

    // ⑤ ملاحظة ملوّنة (تنبيه / معلومة / تحذير / نجاح)
    data class NoteBlock(
        override val id: String,
        val noteType: NoteType = NoteType.INFO,
        val title: String? = null,
        val body: String
    ) : RichBlock()

    // ⑥ معادلة رياضية (LaTeX / KaTeX)
    data class MathBlock(
        override val id: String,
        val latex: String,
        val caption: String? = null,
        val isInline: Boolean = false     // true = ضمن النص، false = كتلة مستقلة
    ) : RichBlock()

    // ⑦ استطلاع رأي تفاعلي
    data class PollBlock(
        override val id: String,
        val question: String,
        val options: List<String>,
        val allowMultiple: Boolean = false
    ) : RichBlock()

    // ⑧ إشارة مرجعية (Bookmark) مع وصف ومعاينة
    data class BookmarkBlock(
        override val id: String,
        val url: String,
        val title: String,
        val description: String? = null,
        val imageUrl: String? = null,    // صورة معاينة (Open Graph)
        val siteName: String? = null
    ) : RichBlock()

    // ⑨ مؤقت / عداد تنازلي أو تصاعدي
    data class TimerBlock(
        override val id: String,
        val label: String,
        val durationSeconds: Int,
        val countDown: Boolean = true,   // true = تنازلي، false = تصاعدي
        val autoStart: Boolean = false
    ) : RichBlock()

    // ⑩ قائمة تشغيل (فيديو / صوت / ملفات مرتبة)
    data class PlaylistBlock(
        override val id: String,
        val title: String,
        val description: String? = null,
        val items: List<PlaylistItem> = emptyList(),
        val autoPlay: Boolean = false,
        val loopAll: Boolean = false,
        val style: PlaylistStyle = PlaylistStyle.VERTICAL
    ) : RichBlock()

    // ⑪ عنصر مدمج قابل للتنزيل (فيديو، صورة، ملف PDF، صوت…)
    data class DownloadableBlock(
        override val id: String,
        val uri: String,               // content-uri أو https://
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long? = null,
        val caption: String? = null,
        val previewBase64: String? = null,  // معاينة مصغرة (صورة / غلاف)
        val downloadLabel: String = "تنزيل"
    ) : RichBlock()

    // ⑫ كتلة نصّية حرة — تدعم حتى 50 سطراً (فقرات، اقتباسات، عناوين)
    data class TextBlock(
        override val id: String,
        val title: String? = null,
        val lines: List<String> = emptyList(),   // بحد أقصى MAX_LINES سطر
        val style: TextBlockStyle = TextBlockStyle.PARAGRAPH,
        val align: TextBlockAlign = TextBlockAlign.START
    ) : RichBlock() {
        companion object {
            const val MAX_LINES = 50
        }
    }

    // ⑬ قائمة عامة — يمكن أن يحتوي كل عنصر فيها على نص فقط، أو أي كتلة أخرى
    // (زر، رابط، كود، صورة…)، مما يجعلها "قائمة يمكن إضافة أي شيء فيها"
    data class ListBlock(
        override val id: String,
        val title: String? = null,
        val listStyle: ListStyle = ListStyle.BULLET,
        val items: List<ListItem> = emptyList()
    ) : RichBlock()

    // ⑭ كتلة صورة احترافية: تضمين مباشر (base64) أو رابط خارجي، مع دعم
    // أشكال زوايا متعددة، نمط ملء، عرض جزئي مع محاذاة، تسمية توضيحية،
    // ورابط اختياري عند الضغط.
    data class ImageBlock(
        override val id: String,
        val imageBase64: String? = null,
        val uri: String? = null,
        val caption: String? = null,
        val altText: String? = null,
        val linkUrl: String? = null,
        val cornerStyle: ImageCornerStyle = ImageCornerStyle.ROUNDED,
        val fit: ImageFit = ImageFit.COVER,
        val alignment: ImageAlignment = ImageAlignment.CENTER,
        val widthPercent: Int = 100,
        val fullBleed: Boolean = false,
        val aspectRatio: Float? = null
    ) : RichBlock()
}

// ⑭ أشكال زوايا كتلة الصورة
enum class ImageCornerStyle(val xmlValue: String, val arabicLabel: String) {
    SQUARE("square", "حادة"),
    ROUNDED("rounded", "دائرية"),
    CIRCLE("circle", "دائرة كاملة");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: ROUNDED
    }
}

// ⑮ نمط ملء كتلة الصورة
enum class ImageFit(val xmlValue: String, val arabicLabel: String) {
    COVER("cover", "قص وملء"),
    CONTAIN("contain", "احتواء كامل");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: COVER
    }
}

// ⑯ محاذاة كتلة الصورة عند العرض الجزئي
enum class ImageAlignment(val xmlValue: String, val arabicLabel: String) {
    START("start", "بداية"),
    CENTER("center", "وسط"),
    END("end", "نهاية");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: CENTER
    }
}

// ⑫ أنماط عرض كتلة النص
enum class TextBlockStyle(val xmlValue: String, val arabicLabel: String) {
    PARAGRAPH("paragraph", "فقرة"),
    HEADING("heading", "عنوان"),
    QUOTE("quote", "اقتباس"),
    HIGHLIGHT("highlight", "نص مميّز"),
    PLAIN_LINES("plainLines", "أسطر متتالية");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: PARAGRAPH
    }
}

enum class TextBlockAlign(val xmlValue: String, val arabicLabel: String) {
    START("start", "بداية"),
    CENTER("center", "وسط"),
    END("end", "نهاية"),
    JUSTIFY("justify", "ضبط");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: START
    }
}

// ⑬ عنصر داخل ListBlock — يحمل نصاً، وقد يحمل بالإضافة إليه أي RichBlock متداخل
data class ListItem(
    val id: String,
    val text: String = "",
    val icon: String? = null,          // إيموجي/رمز اختياري يظهر بجانب العنصر
    val checked: Boolean? = null,      // غير null فقط في نمط قائمة المهام (CHECKLIST)
    val nestedBlock: RichBlock? = null // يسمح بتضمين أي عنصر آخر (نص/زر/كود/رابط/صورة…)
)

enum class ListStyle(val xmlValue: String, val arabicLabel: String) {
    BULLET("bullet", "نقطية"),
    NUMBERED("numbered", "مرقّمة"),
    CHECKLIST("checklist", "قائمة مهام"),
    TIMELINE("timeline", "خط زمني"),
    CARDS("cards", "بطاقات");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: BULLET
    }
}

data class PlaylistItem(
    val id: String,
    val uri: String,
    val title: String,
    val durationSeconds: Int? = null,
    val mimeType: String = "video/mp4",
    val thumbnailBase64: String? = null
)

enum class PlaylistStyle(val xmlValue: String, val arabicLabel: String) {
    VERTICAL("vertical", "قائمة رأسية"),
    HORIZONTAL("horizontal", "شريط أفقي"),
    GRID("grid", "شبكة");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: VERTICAL
    }
}

// 10 أنماط أزرار احترافية جاهزة للاستخدام في أي كتلة زر
enum class ButtonStyle(val xmlValue: String, val arabicLabel: String) {
    PRIMARY("primary", "أساسي"),
    SECONDARY("secondary", "ثانوي"),
    OUTLINED("outlined", "محدود"),
    TEXT("text", "نصّي"),
    GRADIENT("gradient", "متدرّج"),
    DANGER("danger", "خطر"),
    SUCCESS("success", "نجاح"),
    WARNING("warning", "تحذير"),
    GLASS("glass", "زجاجي"),
    ICON_ROUNDED("iconRounded", "دائري بأيقونة");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: PRIMARY
    }
}

// ⑩ أنواع ملاحظات NoteBlock
enum class NoteType(val xmlValue: String, val arabicLabel: String) {
    INFO("info", "معلومة"),
    WARNING("warning", "تحذير"),
    SUCCESS("success", "نجاح"),
    DANGER("danger", "خطر"),
    TIP("tip", "نصيحة");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: INFO
    }
}

// ⑪ لون موحّد للحلقة كاملة (يُخزَّن في ملف الجذر loopRoot=true)
data class LoopColorTheme(
    val loopId: String,           // معرّف الحلقة (id الملف الجذر)
    val primaryColor: String,
    val accentColor: String? = null,
    val backgroundColor: String? = null,
    val isDark: Boolean = false
)

// ⑫ تتبع تقدم القراءة لكل ملف
data class ReadingProgress(
    val fileId: String,
    val lastOpenedAt: Long,       // timestamp ميلي ثانية
    val scrollPositionPct: Float = 0f,  // 0.0 - 1.0
    val isCompleted: Boolean = false,
    val readingTimeSeconds: Int = 0
)

/**
 * مرفق (صورة / فيديو / أي نوع ملف) مضمَّن داخل ملف الـ DLoF نفسه
 * بصيغة base64 — يحافظ على فلسفة "الملف المستقل بذاته" — أو
 * بإشارة (uri) إلى ملف خارجي إن كان كبيراً جداً للتضمين.
 */
data class Attachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val data: String? = null,      // base64، إن كان مضمّناً
    val uri: String? = null,       // مسار/رابط خارجي، إن لم يُضمَّن
    val sizeBytes: Long? = null,
    val caption: String? = null
)

enum class AttachmentKind(val xmlValue: String, val arabicLabel: String) {
    IMAGE("image", "صورة"),
    VIDEO("video", "فيديو"),
    FILE("file", "ملف"),
    SUBTITLE("subtitle", "ترجمة");

    companion object {
        fun fromXml(value: String): AttachmentKind =
            entries.firstOrNull { it.xmlValue == value } ?: FILE

        fun fromMimeType(mime: String?): AttachmentKind = when {
            mime == null -> FILE
            mime.startsWith("image/") -> IMAGE
            mime.startsWith("video/") -> VIDEO
            mime == "text/srt" || mime == "text/vtt" || mime == "application/x-subrip" -> SUBTITLE
            else -> FILE
        }
    }
}

/**
 * قالب تصميم مخصص لملف DLoF واحد: ألوان، خط، وتخطيط العرض.
 * يسمح بأن يكون لكل ملف في الحلقة هويته البصرية الخاصة.
 */
data class Template(
    val ref: String? = null,       // معرّف قالب مستورد من حزمة .dlofTemplate (إن وُجد)
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val fontFamily: String? = null,
    val layout: TemplateLayout = TemplateLayout.STANDARD,
    val headerAttachmentRef: String? = null
)

enum class TemplateLayout(val xmlValue: String, val arabicLabel: String) {
    STANDARD("standard", "عادي"),
    CARD("card", "بطاقة"),
    MAGAZINE("magazine", "مجلة"),
    MINIMAL("minimal", "مبسّط");

    companion object {
        fun fromXml(value: String): TemplateLayout =
            entries.firstOrNull { it.xmlValue == value } ?: STANDARD
    }
}
