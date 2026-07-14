plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// ────────────────────────────────────────────────────────────────────────
// وحدة core: محرّك حزم dlof المشترك (تحليل، تحقق، تشفير/توقيع، قوالب،
// ومستودع القراءة/الكتابة عبر SAF). كانت هذه الملفات جزءاً من وحدة app
// فقط، والآن هي وحدة مكتبة مستقلة يعتمد عليها أي تطبيق يحتاج قراءة أو
// كتابة تنسيق dlof — التطبيق الرئيسي (DLoF Reader) وتطبيق SLCD المنفصل
// كلاهما يعتمد عليها بدل تكرار منطق فك التشفير والتحقق من الحزم.
// ────────────────────────────────────────────────────────────────────────

android {
    namespace = "org.dlof.reader.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // org.json.* متوفرة أصلاً ضمن منصّة أندرويد (android.jar) دون الحاجة
    // لإضافتها كاعتمادية خارجية — كما هو الحال في وحدة app.
}
