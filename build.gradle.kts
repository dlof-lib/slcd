plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ────────────────────────────────────────────────────────────────────────
// تطبيق SLCD (Slime Comics dlof) — تطبيق مستقل بحزمته وأيقونته وواجهته
// الخاصة، لم يعد "أداة مصغّرة" مدمجة داخل DLoF Reader. يعتمد فقط على
// وحدة :core لقراءة/كتابة تنسيق ملفات dlof (كل صفحة قصة مصورة هنا هي
// ملف .dlof مصغّر يحمل صورة الصفحة كمرفق)، دون أي اعتماد على وحدة app.
// ────────────────────────────────────────────────────────────────────────

android {
    namespace = "org.dlof.slcd"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.dlof.slcd"
        minSdk = 26            // Android 8+ (يكفي لأيقونة adaptive بلا PNG احتياطية)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("APP_KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("APP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("APP_KEY_ALIAS")
                keyPassword = System.getenv("APP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("APP_KEYSTORE_FILE") != null) {
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
        kotlinCompilerExtensionVersion = "1.5.14"
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

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")

    implementation("androidx.documentfile:documentfile:1.0.1")

    // ── Media3 / ExoPlayer — فيديو splash SLCD الاختياري ──────────
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // ── Coil — تحميل صور أغلفة المواسم ─────────────────────────────
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
