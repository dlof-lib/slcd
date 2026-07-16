package org.dlof.slcd.settings

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SlcdSettings يقرأ ويكتب مباشرة عبر SharedPreferences حقيقي (Context
 * التطبيق)، لذا لا يمكن اختباره كوحدة JVM خالصة — هذا اختبار instrumented
 * يشغّل نفس مسار الإقلاع الحقيقي (init) ثم يتحقق من أن العمليات الأساسية
 * (تثبيت المكتبة، تسجيل آخر قراءة، إلغاء التثبيت) تُطابق ما يُقرأ لاحقاً
 * من التخزين الدائم.
 */
@RunWith(AndroidJUnit4::class)
class SlcdSettingsTest {

    private val context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Before
    fun setUp() {
        // تنظيف أي حالة متبقية من تشغيل سابق قبل كل اختبار.
        SlcdSettings.uninstallSlcd(context)
    }

    @After
    fun tearDown() {
        SlcdSettings.uninstallSlcd(context)
    }

    @Test
    fun markInstalled_persistsRootUriAndInstalledFlag() {
        assertFalse(SlcdSettings.slcdInstalled)

        SlcdSettings.markSlcdInstalled(context, rootTreeUri = "content://fake/tree/root")

        assertTrue(SlcdSettings.slcdInstalled)
        assertEquals("content://fake/tree/root", SlcdSettings.slcdRootUri)
    }

    @Test
    fun uninstall_clearsInstalledFlagAndLastRead() {
        SlcdSettings.markSlcdInstalled(context, rootTreeUri = "content://fake/tree/root")
        SlcdSettings.markSlcdLastRead(context, seasonNumber = 2, chapterNumber = 5)

        SlcdSettings.uninstallSlcd(context)

        assertFalse(SlcdSettings.slcdInstalled)
        assertNull(SlcdSettings.slcdRootUri)
        assertNull(SlcdSettings.slcdLastSeason)
        assertNull(SlcdSettings.slcdLastChapter)
    }

    @Test
    fun markLastRead_updatesSeasonAndChapter() {
        SlcdSettings.markSlcdLastRead(context, seasonNumber = 3, chapterNumber = 7)

        assertEquals(3, SlcdSettings.slcdLastSeason)
        assertEquals(7, SlcdSettings.slcdLastChapter)
    }

    @Test
    fun setReadingStyle_defaultsToWebtoonThenSwitchesToPlus() {
        assertEquals(SlcdSettings.ReadingStyle.WEBTOON, SlcdSettings.readingStyle)

        SlcdSettings.setReadingStyle(context, SlcdSettings.ReadingStyle.PLUS)

        assertEquals(SlcdSettings.ReadingStyle.PLUS, SlcdSettings.readingStyle)

        // إعادة الحالة الافتراضية حتى لا تتسرّب لاختبارات أخرى.
        SlcdSettings.setReadingStyle(context, SlcdSettings.ReadingStyle.WEBTOON)
    }
}
