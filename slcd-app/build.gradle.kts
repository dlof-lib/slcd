plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

import java.util.Properties

// ────────────────────────────────────────────────────────────────────────
// تطبيق SLCD (Slime Comics dlof) — تطبيق مستقل بحزمته وأيقونته وواجهته
// الخاصة، لم يعد "أداة مصغّرة" مدمجة داخل DLoF Reader. يعتمد فقط على
// وحدة :core لقراءة/كتابة تنسيق ملفات dlof (كل صفحة قصة مصورة هنا هي
// ملف .dlof مصغّر يحمل صورة الصفحة كمرفق)، دون أي اعتماد على وحدة app.
// ────────────────────────────────────────────────────────────────────────

// ── توقيع الإصدار (release) — محلياً عبر keystore.properties (غير مرفوع
// للريبو، انظر .gitignore و keystore.properties.sample)، أو في CI عبر
// متغيرات البيئة APP_KEYSTORE_*. متغيرات البيئة لها الأولوية دائماً.
val keystoreProperties = Properties().apply {
    val propsFile = project.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "org.dlof.slcd"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.dlof.slcd"
        minSdk = 26            // Android 8+ (يكفي لأيقونة adaptive بلا PNG احتياطية)
        targetSdk = 35          // Android 15: edge-to-edge إلزامي + predictive back
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("APP_KEYSTORE_FILE") ?: keystoreProperties.getProperty("storeFile")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("APP_KEYSTORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
                keyAlias = System.getenv("APP_KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
                keyPassword = System.getenv("APP_KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val hasSigningConfig = System.getenv("APP_KEYSTORE_FILE") != null ||
                keystoreProperties.getProperty("storeFile") != null
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ── محرّك حزم dlof المشترك (تحليل/تحقق/تشفير/مستودع القراءة) ──
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)

    implementation(libs.androidx.documentfile)

    // ── Media3 / ExoPlayer — فيديو splash SLCD الاختياري ──────────
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // ── Coil — تحميل صور أغلفة المواسم ─────────────────────────────
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    // ── اختبارات Instrumented (تعمل على جهاز/محاكي حقيقي) ──────────
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
