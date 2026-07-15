package org.dlof.reader.lighthouse

/**
 * ══════════════════════════════════════════════════════════════
 * df.kt — Document File Helper (مساعد ملفات DLoF)
 * ══════════════════════════════════════════════════════════════
 *
 * يُدير ملفات DLoF داخل الحزمة:
 * - فحص وتحليل هيكل الحزمة
 * - التحقق من وجود الملفات المطلوبة
 * - قراءة set.txt وتحليله
 * - إدارة المسارات النسبية
 * - التحقق من سلامة الحزمة قبل التثبيت
 *
 * موقع الملف: dlofpkg/Lighthouse/df.kt
 * يُستدعى من التطبيق عند استيراد أو فتح حزمة.
 */

import java.io.File

object DocumentFileHelper {

    // ── قائمة الملفات المطلوبة ──────────────────────────────────────

    private val REQUIRED_FILES = listOf("set.txt")

    private val REQUIRED_FOLDERS = listOf(
        "media", "media/image", "media/video", "media/fonts",
        "setting", "setting/pro", "Lighthouse"
    )

    // ── نموذج فحص الحزمة ────────────────────────────────────────────

    data class PackageCheck(
        val isValid: Boolean,
        val missingRequired: List<String>,
        val missingOptional: List<String>,
        val errors: List<String>,
        val warnings: List<String>,
        val structure: PackageStructure? = null
    )

    data class PackageStructure(
        val hasSetTxt: Boolean,
        val hasTemplate: Boolean,
        val hasCryptoProfile: Boolean,
        val hasMapDlof: Boolean,
        val hasDocumentation: Boolean,
        val hasLicense: Boolean,
        val hasLighthouse: Boolean,
        val mediaFolders: MediaFolders,
        val cryptoEnabled: Boolean,
        val base64Mode: Base64Mode
    )

    data class MediaFolders(
        val hasImages: Boolean,
        val hasVideos: Boolean,
        val hasFonts: Boolean,
        val hasChapters: Boolean,
        val hasEpisodes: Boolean,
        val imageCount: Int,
        val videoCount: Int,
        val fontCount: Int
    )

    enum class Base64Mode { ALWAYS, OPTIONAL, NEVER, UNKNOWN }

    // ── فحص الحزمة ──────────────────────────────────────────────────

    /**
     * يفحص حزمة dlofpkg بالكامل ويُعيد تقريراً مفصلاً.
     */
    fun checkPackage(pkgRoot: File): PackageCheck {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val missingRequired = mutableListOf<String>()
        val missingOptional = mutableListOf<String>()

        // فحص الملفات المطلوبة
        for (file in REQUIRED_FILES) {
            if (!File(pkgRoot, file).exists()) {
                missingRequired.add(file)
                errors.add("الملف المطلوب مفقود: $file")
            }
        }

        // فحص المجلدات المطلوبة
        for (folder in REQUIRED_FOLDERS) {
            if (!File(pkgRoot, folder).exists()) {
                warnings.add("المجلد الموصى به مفقود: $folder")
            }
        }

        // تحليل set.txt إن وُجد
        val setFile = File(pkgRoot, "set.txt")
        val structure = if (setFile.exists()) {
            parseStructure(pkgRoot, setFile)
        } else null

        // فحص المجلدات الاختيارية
        val optionalFiles = listOf(
            "setting/dlotemplate.xml" to "قالب XML",
            "setting/map.dlof" to "خريطة الحلقة",
            "setting/Documentation.dlof" to "التوثيق",
            "setting/license.dlof" to "الترخيص",
            "setting/pro/Best64.xml" to "إعدادات التشفير",
            "setting/pro/WQ.JSON" to "مساعد التشفير"
        )

        for ((path, name) in optionalFiles) {
            if (!File(pkgRoot, path).exists()) {
                missingOptional.add(name)
            }
        }

        val isValid = missingRequired.isEmpty()

        return PackageCheck(
            isValid = isValid,
            missingRequired = missingRequired,
            missingOptional = missingOptional,
            errors = errors,
            warnings = warnings,
            structure = structure
        )
    }

    /**
     * يحلل هيكل الحزمة من set.txt والمجلدات.
     */
    private fun parseStructure(pkgRoot: File, setFile: File): PackageStructure {
        val props = mutableMapOf<String, String>()
        setFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    props[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                }
            }
        }

        val cryptoEnabled = props["crypto.enabled"]?.toBoolean() ?: false
        val base64Mode = when (props["base64.mode"]?.lowercase()) {
            "always" -> Base64Mode.ALWAYS
            "optional" -> Base64Mode.OPTIONAL
            "never" -> Base64Mode.NEVER
            else -> Base64Mode.UNKNOWN
        }

        val imgDir = File(pkgRoot, "media/image")
        val vidDir = File(pkgRoot, "media/video")
        val fontDir = File(pkgRoot, "media/fonts/fonts")

        return PackageStructure(
            hasSetTxt = true,
            hasTemplate = File(pkgRoot, "setting/dlotemplate.xml").exists(),
            hasCryptoProfile = File(pkgRoot, "setting/pro/Best64.xml").exists(),
            hasMapDlof = File(pkgRoot, "setting/map.dlof").exists(),
            hasDocumentation = File(pkgRoot, "setting/Documentation.dlof").exists(),
            hasLicense = File(pkgRoot, "setting/license.dlof").exists(),
            hasLighthouse = File(pkgRoot, "Lighthouse").exists(),
            mediaFolders = MediaFolders(
                hasImages = imgDir.exists(),
                hasVideos = vidDir.exists(),
                hasFonts = fontDir.exists(),
                hasChapters = imgDir.listFiles { f -> f.isDirectory && f.name.startsWith("chapter") }?.isNotEmpty() ?: false,
                hasEpisodes = File(vidDir, "Episodes").exists(),
                imageCount = imgDir.listFiles()?.size ?: 0,
                videoCount = vidDir.listFiles()?.size ?: 0,
                fontCount = fontDir.listFiles { f -> f.extension in listOf("ttf", "otf", "woff", "woff2") }?.size ?: 0
            ),
            cryptoEnabled = cryptoEnabled,
            base64Mode = base64Mode
        )
    }

    /**
     * يُعيد المسار المطلق داخل الحزمة لمسار نسبي.
     */
    fun resolvePath(pkgRoot: File, relativePath: String): File {
        // تنظيف المسار
        val cleanPath = relativePath.replace("..", "").trimStart('/', '\\')
        return File(pkgRoot, cleanPath)
    }

    /**
     * يُعيد قائمة بكل ملفات DLoF (.dlof) داخل الحزمة.
     */
    fun findAllDlofFiles(pkgRoot: File): List<File> {
        return pkgRoot.walkTopDown()
            .filter { it.isFile && it.extension == "dlof" }
            .toList()
    }

    /**
     * يحسب حجم الحزمة بالبايت.
     */
    fun calculatePackageSize(pkgRoot: File): Long {
        return pkgRoot.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /**
     * يُعيد ملخصاً نصياً عن الحزمة.
     */
    fun generateSummary(pkgRoot: File): String {
        val check = checkPackage(pkgRoot)
        val structure = check.structure ?: return "تعذّر تحليل الحزمة"
        val size = calculatePackageSize(pkgRoot)

        return buildString {
            appendLine("═══ ملخص حزمة DLoF_pkg ═══")
            appendLine()
            appendLine("✓ set.txt: ${if (structure.hasSetTxt) "موجود" else "مفقود"}")
            appendLine("✓ القالب: ${if (structure.hasTemplate) "موجود" else "مفقود"}")
            appendLine("✓ خريطة: ${if (structure.hasMapDlof) "موجودة" else "مفقودة"}")
            appendLine("✓ توثيق: ${if (structure.hasDocumentation) "موجود" else "مفقود"}")
            appendLine("✓ ترخيص: ${if (structure.hasLicense) "موجود" else "مفقود"}")
            appendLine()
            appendLine("── الوسائط ──")
            appendLine("  صور: ${structure.mediaFolders.imageCount} ${if (structure.mediaFolders.hasChapters) "(مع فصول)" else ""}")
            appendLine("  فيديو: ${structure.mediaFolders.videoCount} ${if (structure.mediaFolders.hasEpisodes) "(مع حلقات)" else ""}")
            appendLine("  خطوط: ${structure.mediaFolders.fontCount}")
            appendLine()
            appendLine("── التشفير ──")
            appendLine("  مفعل: ${if (structure.cryptoEnabled) "نعم" else "لا"}")
            appendLine("  Base64: ${structure.base64Mode.name}")
            appendLine()
            appendLine("── الحجم ──")
            appendLine("  ${formatSize(size)}")
            if (check.warnings.isNotEmpty()) {
                appendLine()
                appendLine("── تحذيرات ──")
                check.warnings.forEach { appendLine("  ⚠ $it") }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes بايت"
        }
    }
}
