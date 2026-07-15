package org.dlof.reader.lighthouse

/**
 * ══════════════════════════════════════════════════════════════
 * ep.kt — Episode Helper (مساعد الحلقات)
 * ══════════════════════════════════════════════════════════════
 *
 * يُدير حلقات المسلسلات داخل حزمة DLoF_pkg:
 * - إنشاء/قراءة هيكل Episodes/episode{N}
 * - ترقيم الحلقات (عربي، لاتيني، روماني)
 * - ربط الحلقات بالحلقة السابقة والتالية
 * - التحقق من صحة تسلسل الحلقات
 *
 * موقع الملف: dlofpkg/Lighthouse/ep.kt
 * يُستدعى من التطبيق عند فتح حزمة مسلسل.
 */

import java.io.File

object EpisodeHelper {

    private const val EPISODES_FOLDER = "media/video/Episodes"
    private const val EPISODE_PREFIX = "episode"

    // ── نموذج حلقة ──────────────────────────────────────────────────

    data class Episode(
        val number: Int,
        val title: String = "",
        val videoFile: String = "",
        val subtitleFile: String? = null,
        val thumbnailPath: String? = null,
        val durationSeconds: Int? = null,
        val previousEpisode: Int? = null,
        val nextEpisode: Int? = null
    )

    // ── إنشاء هيكل الحلقات ──────────────────────────────────────────

    /**
     * ينشئ هيكل مجلدات الحلقات فارغاً داخل media/video/Episodes/
     *
     * @param pkgRoot جذر حزمة dlofpkg
     * @param count عدد الحلقات
     * @return قائمة المسارات المنشأة
     */
    fun createEpisodesStructure(pkgRoot: File, count: Int): List<String> {
        val episodesDir = File(pkgRoot, EPISODES_FOLDER)
        val created = mutableListOf<String>()

        for (i in 1..count) {
            val epDir = File(episodesDir, "$EPISODE_PREFIX$i")
            epDir.mkdirs()

            // أنشئ ملف وصف الحلقة
            val descFile = File(epDir, "episode$i.dlof")
            descFile.writeText(buildEpisodeDlof(i))

            created.add(epDir.path)
        }

        return created
    }

    /**
     * يقرأ كل الحلقات الموجودة في الهيكل.
     */
    fun readEpisodes(pkgRoot: File): List<Episode> {
        val episodesDir = File(pkgRoot, EPISODES_FOLDER)
        if (!episodesDir.exists()) return emptyList()

        return episodesDir.listFiles { f -> f.isDirectory && f.name.startsWith(EPISODE_PREFIX) }
            ?.sortedBy { it.name.removePrefix(EPISODE_PREFIX).toIntOrNull() ?: 0 }
            ?.mapNotNull { dir ->
                val num = dir.name.removePrefix(EPISODE_PREFIX).toIntOrNull() ?: return@mapNotNull null
                val videoFile = dir.listFiles { f -> f.extension in listOf("mp4", "mkv", "webm") }?.firstOrNull()
                val dlofFile = File(dir, "episode$num.dlof")

                Episode(
                    number = num,
                    videoFile = videoFile?.name ?: "",
                    subtitleFile = dir.listFiles { f -> f.extension in listOf("srt", "vtt") }?.firstOrNull()?.name,
                    thumbnailPath = dir.listFiles { f -> f.name.contains("thumb") }?.firstOrNull()?.path
                )
            } ?: emptyList()
    }

    /**
     * يربط كل حلقة بالسابقة والتالية تلقائياً.
     */
    fun linkEpisodes(episodes: List<Episode>): List<Episode> {
        return episodes.mapIndexed { index, ep ->
            ep.copy(
                previousEpisode = if (index > 0) episodes[index - 1].number else null,
                nextEpisode = if (index < episodes.size - 1) episodes[index + 1].number else null
            )
        }
    }

    /**
     * يُرقّم الحلقة بالنمط المطلوب.
     */
    fun formatEpisodeNumber(number: Int, style: NumberingStyle = NumberingStyle.ARABIC): String {
        return when (style) {
            NumberingStyle.ARABIC -> number.toString()
            NumberingStyle.LATIN -> number.toString()
            NumberingStyle.ROMAN -> number.toRoman()
            NumberingStyle.CUSTOM -> "EP${String.format("%03d", number)}"
        }
    }

    /**
     * يتحقق من تسلسل الحلقات (لا ثغرات في الترقيم).
     */
    fun validateSequence(episodes: List<Episode>): ValidationResult {
        if (episodes.isEmpty()) return ValidationResult(false, "لا توجد حلقات")

        val numbers = episodes.map { it.number }.sorted()
        val expected = (1..numbers.size).toList()

        return if (numbers == expected) {
            ValidationResult(true, "تسلسل الحلقات صحيح: ${numbers.size} حلقة")
        } else {
            val missing = expected - numbers.toSet()
            ValidationResult(false, "حلقات مفقودة: ${missing.joinToString(", ")}")
        }
    }

    // ── بناء ملف dlof للحلقة ────────────────────────────────────────

    private fun buildEpisodeDlof(number: Int): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<dlof xmlns="https://dlof.org/schema/1.0"
        |      id="episode$number" domain="series" version="2.0">
        |  <metadata>
        |    <title>الحلقة ${toArabicNumber(number)}</title>
        |    <language>ar</language>
        |  </metadata>
        |  <loop>
        |    <this ref="episode$number" title="الحلقة ${toArabicNumber(number)}"/>
        |    <previous ref="episode${number - 1}"/> <!-- أو @loopRoot -->
        |    <next ref="episode${number + 1}"/> <!-- أو @end -->
        |  </loop>
        |  <content type="episodeItem">
        |    <episodeNumber>$number</episodeNumber>
        |    <episodeTitle>الحلقة ${toArabicNumber(number)}</episodeTitle>
        |    <body>وصف الحلقة $number...</body>
        |  </content>
        |</dlof>
    """.trimMargin()

    // ── مساعدات ─────────────────────────────────────────────────────

    private fun Int.toRoman(): String {
        val values = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var n = this
        return buildString {
            for ((v, s) in values) {
                while (n >= v) { append(s); n -= v }
            }
        }
    }

    private fun toArabicNumber(n: Int): String = n.toString()
        .replace("0", "٠").replace("1", "١").replace("2", "٢")
        .replace("3", "٣").replace("4", "٤").replace("5", "٥")
        .replace("6", "٦").replace("7", "٧").replace("8", "٨").replace("9", "٩")

    enum class NumberingStyle { ARABIC, LATIN, ROMAN, CUSTOM }

    data class ValidationResult(val valid: Boolean, val message: String)
}
