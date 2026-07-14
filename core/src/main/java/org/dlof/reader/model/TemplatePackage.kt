package org.dlof.reader.model

/**
 * يمثّل "حزمة قالب" مستوردة أو جاهزة للتصدير: ملف مضغوط (.dlofTemplate)
 * يحتوي على وصف XML (template.xml) ونسخة Kotlin مرجعية (Template.kt)
 * لتعريف نفس القالب — بحيث يمكن استرداد القالب نفسه في تطبيقات/مشاريع أخرى.
 *
 * هذه الحزمة مستقلة عن أي ملف dlof معيّن: يمكن استيرادها مرة واحدة
 * ثم تطبيقها (ref) على عدد غير محدود من ملفات .dlof.
 */
data class TemplatePackage(
    val id: String,
    val name: String,
    val author: String? = null,
    val version: String = "1.0",
    val template: Template
)
