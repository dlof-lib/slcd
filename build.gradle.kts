plugins {
    // AGP 8.7.x يدعم Android 15 (API 35) بشكل مستقر: compileSdk/targetSdk = 35،
    // محاذاة المكتبات الأصلية بحجم صفحة 16 كيلوبايت تلقائياً، ودعم كامل لواجهات
    // البرمجة الجديدة (predictive back، edge-to-edge الإلزامي).
    // الإصدارات نفسها مُعرَّفة مركزياً في gradle/libs.versions.toml.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}
