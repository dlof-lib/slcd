package org.dlof.slcd

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.dlof.slcd.settings.SlcdSettings
import org.dlof.slcd.ui.theme.SlcdTheme

/**
 * ── نقطة الدخول الوحيدة لتطبيق SLCD المستقل ─────────────────────────
 *
 * تدفّق الإقلاع: splash خاص بـ SLCD ← (إن لم تُثبَّت مكتبة القصص بعد)
 * شاشة تثبيت حقيقية (اختيار/إنشاء مجلد عبر SAF ومنح صلاحية دائمة) ←
 * المكتبة الرئيسية. عكس النسخة القديمة المدمجة داخل DLoF، لا يوجد هنا
 * "رجوع" لتطبيق آخر — SLCD يقف بذاته من الإقلاع حتى القراءة.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SlcdSettings.init(this)

        setContent {
            SlcdTheme {
                Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SlcdRootFlow(
                        onOpenExternalDlof = { uri -> openWithExternalDlofViewer(uri) }
                    )
                }
            }
        }
    }

    /**
     * جسر بين التطبيقين: يفتح ملف `.dlof` بنيّة ACTION_VIEW عامة بدل
     * استدعاء داخلي مباشر لتطبيق DLoF الرئيسي (لم يعودا نفس العملية).
     * إن كان DLoF Reader مثبَّتاً على الجهاز يفتحه مباشرة، وإلا يعرض
     * النظام قائمة التطبيقات المتاحة لفتح هذا النوع من الملفات.
     */
    private fun openWithExternalDlofViewer(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }
}

private sealed class SlcdBootRoute {
    data object Splash : SlcdBootRoute()
    data object Install : SlcdBootRoute()
    data object Home : SlcdBootRoute()
}

@Composable
private fun SlcdRootFlow(onOpenExternalDlof: (Uri) -> Unit) {
    var route by remember {
        mutableStateOf<SlcdBootRoute>(SlcdBootRoute.Splash)
    }

    when (route) {
        SlcdBootRoute.Splash -> SLCDSplashScreen(
            onFinished = {
                route = if (SlcdSettings.slcdInstalled) SlcdBootRoute.Home else SlcdBootRoute.Install
            }
        )

        SlcdBootRoute.Install -> {
            val context = LocalContext.current
            SLCDInstallScreen(
                onBack = { (context as? Activity)?.finish() },
                onInstalled = { route = SlcdBootRoute.Home }
            )
        }

        SlcdBootRoute.Home -> {
            val context = LocalContext.current
            SLCDHomeScreen(
                onBack = {
                    if (SlcdSettings.slcdInstalled) {
                        // زر الرجوع من جذر المكتبة (لا وجهة أعلى في تطبيق مستقل) → إغلاق التطبيق.
                        (context as? Activity)?.finish()
                    } else {
                        // وصلنا onBack() هنا فعلياً بعد onUninstall (انظر SLCDHomeScreen) —
                        // الحالة الآن slcdInstalled == false، فنعيد المستخدم لشاشة التثبيت.
                        route = SlcdBootRoute.Install
                    }
                },
                onOpenDlof = onOpenExternalDlof
            )
        }
    }
}
