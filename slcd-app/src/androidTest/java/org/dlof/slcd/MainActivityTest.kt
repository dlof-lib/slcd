package org.dlof.slcd

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبار دخان (smoke test) للتأكد من أن نقطة الدخول الوحيدة للتطبيق —
 * MainActivity — تُقلع بنجاح إلى الحالة RESUMED دون أي استثناء، بصرف
 * النظر عن كون المستخدم "مثبَّت" (يملك مجلد مكتبة) أم لا. هذا لا يتحقق
 * من محتوى الشاشة، فقط أن دورة حياة الـ Activity ورسم Compose الأولي
 * يعملان دون تعطّل — أول خط دفاع ضد كسر الإقلاع.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun mainActivity_launches_and_reachesResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun mainActivity_survivesRecreate() {
        // يحاكي تدوير الشاشة/تغيير التهيئة — تأكيد أن حالة Compose
        // (route: Splash/Install/Home) لا تُسبّب تعطّلاً عند إعادة الإنشاء.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
