package org.dlof.reader.model

/**
 * ══════════════════════════════════════════════════════════════
 * yime.kt — تعريف الشخصيات (Character / Profile Definition)
 * ══════════════════════════════════════════════════════════════
 *
 * يُستخدم لتعريف شخصيات المسلسلات، المستندات السردية، القصص المصورة،
 * والمحتوى الدرامي أو الإبداعي في صيغة DLoF.
 *
 * ملف .dlof من نوع "characters" يحمل قائمة Yime داخل <content>.
 *
 * مثال XML للمحتوى:
 * <characters>
 *   <character id="sara" name="سارة" role="protagonist" age="28" gender="female">
 *     <alias>بطلة القصة</alias>
 *     <description>مهندسة ذكاء اصطناعي في مدينة النور...</description>
 *     <appearance>شعر أسود قصير، عيون بنية، ترتدي معطف أبيض دائماً</appearance>
 *     <personality>شجاعة، فضولية، تثق بالمنطق قبل العواطف</personality>
 *     <backstory>نشأت في أسرة علمية، فقدت والدها في حادثة غامضة</backstory>
 *     <goals>الكشف عن حقيقة اختفاء والدها</goals>
 *     <relationships>
 *       <relation targetId="omar" type="ally" label="رفيق المهمة"/>
 *     </relationships>
 *     <appearsIn>ep01.dlof ep02.dlof ep03.dlof</appearsIn>
 *     <avatarAttachmentRef>char-sara-avatar</avatarAttachmentRef>
 *   </character>
 * </characters>
 */

// ── نموذج البيانات الأساسي ────────────────────────────────────────────────────

/**
 * شخصية واحدة (Character) داخل حلقة سردية.
 * تُجمَع قائمة من [Yime] داخل [YimeRoster] لتمثيل عالم القصة كاملاً.
 */
data class Yime(
    /** معرّف فريد للشخصية داخل الحلقة (يُستخدم في العلاقات والإشارات) */
    val id: String,

    /** الاسم الكامل للشخصية */
    val name: String,

    /** دور الشخصية في السرد */
    val role: CharacterRole = CharacterRole.SUPPORTING,

    /** الاسم المستعار أو اللقب */
    val alias: String? = null,

    /** العمر (اختياري — قد يكون غير معروف في بعض القصص) */
    val age: Int? = null,

    /** الجنس */
    val gender: CharacterGender = CharacterGender.UNSPECIFIED,

    /** وصف عام للشخصية وطبيعتها */
    val description: String = "",

    /** المظهر الخارجي (شكل الوجه، الملابس، السمات البصرية) */
    val appearance: String? = null,

    /** السمات الشخصية والنفسية */
    val personality: String? = null,

    /** القصة الخلفية / تاريخ الشخصية قبل أحداث الحلقة */
    val backstory: String? = null,

    /** الأهداف والدوافع */
    val goals: String? = null,

    /** الصراعات الداخلية أو الخارجية */
    val conflicts: String? = null,

    /** العلاقات مع شخصيات أخرى */
    val relationships: List<CharacterRelation> = emptyList(),

    /**
     * ملفات .dlof التي تظهر فيها الشخصية
     * (قائمة أسماء ملفات نسبية مثل: "ep01.dlof", "ep03.dlof")
     */
    val appearsIn: List<String> = emptyList(),

    /** معرّف المرفق الخاص بصورة الشخصية (Avatar/Portrait) */
    val avatarAttachmentRef: String? = null,

    /** بيانات الصورة المصغرة بصيغة base64 (مدمجة مباشرة) */
    val avatarBase64: String? = null,

    /** وسوم إضافية لتصنيف الشخصية */
    val tags: List<String> = emptyList(),

    /** ملاحظات للمؤلف (غير ظاهرة للقارئ) */
    val authorNotes: String? = null,

    /**
     * الحالة الدرامية للشخصية:
     * حية / ميتة / مجهولة / مختفية / خارج الحلقة
     */
    val status: CharacterStatus = CharacterStatus.ALIVE
)

// ── أنواع مساعدة ────────────────────────────────────────────────────────────

enum class CharacterRole(val xmlValue: String, val arabicLabel: String) {
    PROTAGONIST("protagonist", "بطل رئيسي"),
    ANTAGONIST("antagonist",   "شرير / خصم"),
    SUPPORTING("supporting",   "شخصية داعمة"),
    MENTOR("mentor",           "المرشد"),
    COMIC_RELIEF("comicRelief","الشخصية الكوميدية"),
    LOVE_INTEREST("loveInterest", "الاهتمام العاطفي"),
    MYSTERY("mystery",         "شخصية غامضة"),
    NARRATOR("narrator",       "الراوي"),
    MINOR("minor",             "شخصية ثانوية");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: SUPPORTING
    }
}

enum class CharacterGender(val xmlValue: String, val arabicLabel: String) {
    MALE("male",           "ذكر"),
    FEMALE("female",       "أنثى"),
    NON_BINARY("nonBinary","غير ثنائي"),
    UNSPECIFIED("unspecified", "غير محدد");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: UNSPECIFIED
    }
}

enum class CharacterStatus(val xmlValue: String, val arabicLabel: String) {
    ALIVE("alive",         "حي"),
    DECEASED("deceased",   "متوفى"),
    MISSING("missing",     "مفقود"),
    UNKNOWN("unknown",     "مجهول"),
    INACTIVE("inactive",   "خارج الأحداث");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: ALIVE
    }
}

/**
 * علاقة بين شخصيتين.
 * [targetId] يشير لـ [Yime.id] شخصية أخرى في نفس الحلقة.
 */
data class CharacterRelation(
    val targetId: String,
    val type: RelationType = RelationType.ACQUAINTANCE,
    val label: String? = null   // وصف حر للعلاقة: "رفيق المهمة", "العدو القديم" ...
)

enum class RelationType(val xmlValue: String, val arabicLabel: String) {
    ALLY("ally",           "حليف"),
    ENEMY("enemy",         "عدو"),
    FAMILY("family",       "عائلة"),
    FRIEND("friend",       "صديق"),
    RIVAL("rival",         "منافس"),
    MENTOR("mentor",       "مرشد"),
    STUDENT("student",     "تلميذ"),
    LOVE("love",           "علاقة عاطفية"),
    ACQUAINTANCE("acquaintance", "معرفة");

    companion object {
        fun fromXml(v: String) = entries.firstOrNull { it.xmlValue == v } ?: ACQUAINTANCE
    }
}

/**
 * فهرس الشخصيات الكامل للحلقة / السلسلة.
 * يُخزَّن عادةً في ملف .dlof منفصل من نوع domain="characters"
 * مرتبط بملف الجذر (loopRoot) عبر <loopLinks>.
 */
data class YimeRoster(
    /** معرّف الحلقة / السلسلة التي تنتمي إليها هذه الشخصيات */
    val loopId: String,

    /** عنوان السلسلة */
    val seriesTitle: String,

    /** قائمة الشخصيات */
    val characters: List<Yime> = emptyList(),

    /** رسم خريطة علاقات كنص حر (اختياري، للمؤلف) */
    val relationshipMapNotes: String? = null
)

// ── امتداد نوع المحتوى ────────────────────────────────────────────────────────

/**
 * نوع محتوى جديد يُضاف إلى [DlofContent] لدعم ملفات تعريف الشخصيات.
 *
 * يُستخدم في ملف .dlof من domain="characters":
 * <content>
 *   <characters> ... </characters>
 * </content>
 */
// ملاحظة: لا يمكن إضافة case جديد لـ sealed class خارج نفس الملف في Kotlin،
// لذا نستخدم [DlofContent.Generic] بـ customType="characters" في ملف الـ XML،
// ونحتفظ بـ YimeRoster كنموذج مستقل يُعبأ من البيانات الخام عند التحليل.

/**
 * أداة مساعدة: تحوّل [DlofContent.Generic] بـ customType="characters"
 * إلى [YimeRoster] قابل للاستخدام.
 *
 * استخدام:
 *   val roster = YimeParser.fromGenericContent(doc.content, doc.id, doc.metadata.title)
 */
object YimeParser {

    fun fromGenericContent(
        content: DlofContent,
        loopId: String,
        seriesTitle: String
    ): YimeRoster? {
        if (content !is DlofContent.Generic) return null
        if (content.customType != "characters") return null
        // التحليل الفعلي يتم في DlofParser.kt عبر parseCharactersBlock()
        // هنا نعيد roster فارغ كـ placeholder — يُعبأ من DlofParser
        return YimeRoster(loopId = loopId, seriesTitle = seriesTitle)
    }
}
