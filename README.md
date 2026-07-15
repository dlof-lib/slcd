# SLCD — قصص سلايم المصورة

تطبيق أندرويد مستقل لقراءة القصص المصورة بصيغة **`.dlof`**، مبني بالكامل بلغة Kotlin وواجهة Jetpack Compose. كل صفحة من القصة المصورة هي ملف `.dlof` مصغّر يحمل صورة اللوحة كمرفق داخلي، دون الحاجة لفهرس أو قاعدة بيانات خارجية.

---

## ✨ المميزات

- **قارئ ويبتون رأسي** بتمرير سلس وتحميل هيكلي على مرحلتين (فكّ أبعاد اللوحة أولاً `inJustDecodeBounds`، ثم محتواها الكامل) — يحجز المساحة بالنسبة الحقيقية للوحة فلا تحدث قفزة تخطيط عند اكتمال التحميل.
- **أسلوب قراءة جديد: SLCD+** (`SlcdPlusReaderScreen`) — قراءة بالتقليب الأفقي، لوحة واحدة تملأ الشاشة، مع حركة تركيز/تكبير خفيفة عند دخول كل لوحة وتكبير بالنقر المزدوج. يمكن التبديل بينه وبين الويبتون في أي لحظة من زر صغير داخل شريط القراءة نفسه (`SlcdReaderRouter`)، والاختيار يُحفظ تلقائياً (`SlcdSettings.readingStyle`).
- **نافذة ذاكرة محدودة** مع تفريغ فعلي (`recycle()`) للوحات خارج نطاق العرض، لتفادي استهلاك الذاكرة في القصص الطويلة (في كلا أسلوبي القراءة).
- **تحميل هيكلي موسّع لكل فصل/موسم** (`SlcdStructuralCache`): مانفستات لوحات الفصول (أسماء الملفات فقط، بلا صور) تُحفظ في ذاكرة مشتركة تعيش طوال عمر التطبيق وتُحمَّل مسبقاً للفصل التالي/السابق بمجرد الاقتراب من حافة الفصل الحالي، فينتقل القارئ بين الفصول فوراً بلا وميض تحميل. كذلك تتوفر قراءة هيكلية "سطحية" للمكتبة (`listSeasonsShallow`/`loadLibraryShallow`) تحمّل قائمة المواسم فقط دون المرور على فصولها، وقراءة هيكلية لموسم واحد عند الطلب (`loadChaptersForSeason`) بدل المرور على كل فصول كل مواسم المكتبة دفعة واحدة.
- **شريط تقدّم قابل للسحب** للتنقل السريع بين لوحات الفصل (في أسلوب الويبتون)، أو شريط نقاط/عدّاد مضغوط (في SLCD+).
- **مكتبة قابلة للتخصيص**: مواسم وفصول مرتبة بالسحب والإفلات (Drag & Reorder).
- **قالب جاهز (Template Seeder)**: زرع مباشر لمكتبة تجريبية من `assets/library_template` عند أول تشغيل.
- **فتح ملفات `.dlof` خارجياً** عبر `ACTION_VIEW` كجسر خفيف مع تطبيقات أخرى تدعم الصيغة.
- **إعدادات خاصة** بالتطبيق (`SlcdSettings`) منفصلة تماماً عن أي تطبيق آخر يستخدم نفس الصيغة.

---

## 🧩 البنية المعمارية

المشروع مقسّم إلى وحدتي Gradle فقط:

```
SLCD/
├── core/          ← مكتبة مشتركة: تحليل/تحقق/تشفير وتوقيع ملفات dlof + مستودع قراءة عبر SAF
└── slcd-app/       ← التطبيق نفسه: الشاشات، السمة (Theme)، الإعدادات، الموارد
```

| الوحدة | النوع | المسؤولية |
|---|---|---|
| `:core` | `com.android.library` | `DlofParser`, `DlofValidator`, `DlofCrypto`/`DlofCryptoV2`, `DlofRepository`, `TemplatePackageIO` |
| `:slcd-app` | `com.android.application` | `MainActivity`, شاشات Splash/Install/Home/Reader، `SlcdApplication`, `SlcdSettings`, `SlcdTemplateSeeder`، `SlcdReaderRouter` (يختار بين `SlcdComicReaderScreen` الويبتون و`SlcdPlusReaderScreen` الجديد)، `SlcdStructuralCache` (ذاكرة مانفستات الفصول/المواسم الهيكلية) |

`slcd-app` يعتمد على `core` فقط عبر `implementation(project(":core"))`، دون أي ارتباط بتطبيقات أخرى.

---

## 📄 صيغة DLoF باختصار

ملف XML واحد يمثّل مستنداً ضمن "حلقة" مستندات مترابطة ذاتياً (بلا فهرس مركزي):

```xml
<documentLoop version="1.0" id="ch02">
  <metadata>
    <title>الفصل الثاني</title>
    <domain>book</domain>
  </metadata>
  <loopLinks>
    <previous ref="ch01.dlof" title="الفصل الأول"/>
    <next ref="ch03.dlof" title="الفصل الثالث"/>
  </loopLinks>
  <content> ... </content>
  <attachments> ... </attachments>
</documentLoop>
```

كل مستند يحمل بداخله إشارته إلى الجارين (`previous`/`next`)، مما يسمح بتصفح الحلقة كاملةً دون قاعدة بيانات خارجية.

---

## ⚙️ المتطلبات

| | |
|---|---|
| Kotlin | مع Jetpack Compose |
| Compile / Target SDK | 34 |
| Min SDK | 26 (Android 8+) — `:core` يدعم حتى 24 |
| JDK | 17 |
| Gradle | 8.7 |
| AGP | 8.5.2 |

**المكتبات الرئيسية:** Compose BOM 2024.06, Media3/ExoPlayer 1.4.1 (فيديو splash اختياري)، Coil 2.6.0 (أغلفة المواسم)، Coroutines 1.8.1.

---

## 🚀 البناء والتشغيل

```bash
# فحوصات Lint
gradle :slcd-app:lintDebug --stacktrace

# اختبارات الوحدة
gradle :slcd-app:testDebugUnitTest :core:testDebugUnitTest --stacktrace

# بناء نسخة Debug
gradle :slcd-app:assembleDebug --stacktrace

# بناء نسخة Release (يتطلب متغيرات بيئة التوقيع أدناه)
gradle :slcd-app:assembleRelease --stacktrace
```

> ⚠️ لا يحوي المستودع حالياً `gradlew`/`gradlew.bat`؛ استخدم أمر `gradle` مباشرة (يتطلب تثبيت Gradle 8.7 محلياً)، أو أضف الـ wrapper عبر:
> ```bash
> gradle wrapper --gradle-version 8.7
> ```

### متغيرات التوقيع (نسخة Release فقط)

| المتغيّر | الوصف |
|---|---|
| `APP_KEYSTORE_FILE` | مسار ملف الـ keystore |
| `APP_KEYSTORE_PASSWORD` | كلمة مرور الـ keystore |
| `APP_KEY_ALIAS` | اسم المفتاح |
| `APP_KEY_PASSWORD` | كلمة مرور المفتاح |

---

## 🔄 التكامل المستمر (CI)

يشغّل `.github/workflows/build_SLCD.yml` تلقائياً عند الدفع أو طلبات السحب على المسارات ذات الصلة (`slcd-app/**`, `core/**`, ملفات Gradle الجذرية)، وكذلك يدوياً عبر `workflow_dispatch` مع اختيار نوع البناء (`debug`/`release`). يشمل: Lint، اختبارات الوحدة، بناء الـ APK، ورفعه كـ Artifact مع تقرير Lint.

---

## 📁 هيكل المشروع الكامل

```
SLCD/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── .github/workflows/build_SLCD.yml
├── core/
│   ├── build.gradle.kts
│   └── src/main/java/org/dlof/reader/
│       ├── crypto/       (DlofCrypto, DlofCryptoV2)
│       ├── model/        (DlofDocument, FileEntry, TemplatePackage ...)
│       ├── parser/       (DlofParser)
│       ├── repo/         (DlofRepository, NetworkStatus)
│       ├── template/     (TemplatePackageIO)
│       └── validation/   (DlofValidator)
└── slcd-app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/library_template/   (مكتبة تجريبية جاهزة)
        ├── java/org/dlof/slcd/        (الشاشات والمنطق)
        │   ├── SlcdReaderRouter.kt        (يختار أسلوب القراءة: ويبتون أو SLCD+)
        │   ├── SlcdComicReaderScreen.kt   (أسلوب الويبتون الكلاسيكي)
        │   ├── SlcdPlusReaderScreen.kt    (أسلوب SLCD+ الجديد بالتقليب)
        │   └── SlcdStructuralCache.kt     (ذاكرة هيكلية مشتركة لمانفستات الفصول/المواسم)
        └── res/                       (الأيقونة، السمة، الموارد)
```

---

## 🤝 المساهمة

المساهمات مُرحَّب بها! راجع [CONTRIBUTING.md](./CONTRIBUTING.md) لتفاصيل إعداد بيئة التطوير، معايير الكود، وطريقة تقديم Pull Requests.

## 📜 الترخيص

هذا المشروع مرخَّص بموجب [Apache License 2.0](./LICENSE).
