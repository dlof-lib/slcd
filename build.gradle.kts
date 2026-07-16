plugins {
    // AGP 8.7.x يدعم Android 15 (API 35) بشكل مستقر: compileSdk/targetSdk = 35،
    // محاذاة المكتبات الأصلية بحجم صفحة 16 كيلوبايت تلقائياً، ودعم كامل لواجهات
    // البرمجة الجديدة (predictive back، edge-to-edge الإلزامي).
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
