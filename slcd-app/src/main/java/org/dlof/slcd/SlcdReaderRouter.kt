package org.dlof.slcd

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import org.dlof.slcd.settings.SlcdSettings

/**
 * ── موجّه شاشة القراءة ─────────────────────────────────────────────────
 *
 * نقطة الدخول الوحيدة التي يجب أن تستدعيها شاشات التصفح (مثل
 * [SLCDHomeScreen]) لفتح فصل للقراءة. يختار الأسلوب الفعلي بحسب
 * [SlcdSettings.readingStyle] المحفوظ:
 *
 *  • [SlcdSettings.ReadingStyle.WEBTOON] → [SlcdComicReaderScreen] (التمرير
 *    الرأسي الكلاسيكي).
 *  • [SlcdSettings.ReadingStyle.PLUS] → [SlcdPlusReaderScreen] (التقليب
 *    الجديد بحركة تركيز الدخول والتكبير بالنقر المزدوج).
 *
 * كلا القارئين يحمل زر تبديل صغيراً في شريطه العلوي يستدعي
 * [SlcdSettings.setReadingStyle] فيعيد بناء هذا الموجّه فوراً بالأسلوب
 * الآخر، دون فقدان مكان القراءة (نفس `seasonNumber`/`chapterNumber`)
 * ودون أي إعادة تحميل هيكلي لمانفست الفصل (محفوظ أصلاً في
 * [SlcdStructuralCache]).
 */
@Composable
fun SlcdReaderRouter(
    repository: SlimeComicsRepository,
    root: DocumentFile,
    seasonNumber: Int,
    chapterNumber: Int,
    seasonTitle: String?,
    chapterTitle: String?,
    hasNextChapter: Boolean = false,
    hasPreviousChapter: Boolean = false,
    onBack: () -> Unit,
    onNextChapter: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
) {
    val context = LocalContext.current

    when (SlcdSettings.readingStyle) {
        SlcdSettings.ReadingStyle.WEBTOON -> SlcdComicReaderScreen(
            repository = repository,
            root = root,
            seasonNumber = seasonNumber,
            chapterNumber = chapterNumber,
            seasonTitle = seasonTitle,
            chapterTitle = chapterTitle,
            hasNextChapter = hasNextChapter,
            hasPreviousChapter = hasPreviousChapter,
            onBack = onBack,
            onNextChapter = onNextChapter,
            onPreviousChapter = onPreviousChapter,
            onSwitchStyle = { SlcdSettings.setReadingStyle(context, SlcdSettings.ReadingStyle.PLUS) }
        )

        SlcdSettings.ReadingStyle.PLUS -> SlcdPlusReaderScreen(
            repository = repository,
            root = root,
            seasonNumber = seasonNumber,
            chapterNumber = chapterNumber,
            seasonTitle = seasonTitle,
            chapterTitle = chapterTitle,
            hasNextChapter = hasNextChapter,
            hasPreviousChapter = hasPreviousChapter,
            onBack = onBack,
            onNextChapter = onNextChapter,
            onPreviousChapter = onPreviousChapter,
            onSwitchToWebtoon = { SlcdSettings.setReadingStyle(context, SlcdSettings.ReadingStyle.WEBTOON) }
        )
    }
}
