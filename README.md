<div align="center">

<img src="assets/app_icon.png" width="128" height="128" alt="SLCD app icon" />

# SLCD — قصص سلايم المصورة
### Slime Comics dlof

قارئ قصص مصورة أندرويد مستقل، بصيغة ملفات **`.dlof`** الخاصة به، بلا فهرس أو قاعدة بيانات خارجية.

[![Version](https://img.shields.io/badge/version-1.0.0-10B981?style=flat-square)](#-الإصدار)
[![Platform](https://img.shields.io/badge/platform-Android-38BDF8?style=flat-square)](#️-المتطلبات)
[![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208+)-FBBF24?style=flat-square)](#️-المتطلبات)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-0EA5A4?style=flat-square)](#️-المتطلبات)
[![License](https://img.shields.io/badge/license-Apache%202.0-FB7185?style=flat-square)](./LICENSE)

</div>

---

## 📌 الإصدار

| | |
|---|---|
| **الاسم المعروض** | SLCD — قصص سلايم المصورة |
| **معرّف التطبيق** | `org.dlof.slcd` |
| **رقم الإصدار (versionName)** | `1.0.0` |
| **كود الإصدار (versionCode)** | `1` |
| **الحالة** | مستقر — أول إصدار مستقل بذاته |

---

## 🎨 الهوية البصرية

<div align="center">
<img src="assets/app_icon_tile.png" width="220" alt="SLCD app icon on tile" />
</div>

أيقونة adaptive جديدة بعلامة "المروحة" الملوّنة (زمرّدي، تركواز، كهرماني، مرجاني، أزرق سماوي) على لوحة فاتحة — نفس اللوحة اللونية امتدّت لتوحّد شاشة البداية، شاشة/مؤشر التحميل، وكل الأزرار والشارات وأشرطة التقدّم عبر التطبيق، بدل الأخضر الغامق/الذهبي الباهت القديمين.

| العنصر | الوصف |
|---|---|
| `ic_launcher_background` | لوحة فاتحة `#F7F8F7` |
| `ic_launcher_foreground` | علامة "المروحة" السداسية الشعاعات |
| `SlcdFanMark` (Compose) | نفس العلامة، مرسومة برمجياً، تُستخدم في السبلاش ومؤشر التحميل |
| `SlcdTheme` (Material3) | primary=زمرّدي `#10B981` · secondary=تركواز `#0EA5A4` · tertiary=كهرماني `#FBBF24` · error=مرجاني `#FB7185` |

---

## ✨ الميزات

### 📖 القراءة
- **قارئ ويبتون رأسي** بتمرير سلس وتحميل هيكلي على مرحلتين (أبعاد اللوحة أولاً، ثم محتواها الكامل) — بلا قفزة تخطيط عند اكتمال التحميل.
- **أسلوب قراءة SLCD+** بالتقليب الأفقي، لوحة واحدة تملأ الشاشة، مع حركة تركيز/تكبير خفيفة وتكبير بالنقر المزدوج، وتبديل فوري بينه وبين الويبتون (يُحفظ الاختيار تلقائياً).
- **نافذة ذاكرة محدودة** مع تفريغ فعلي للوحات خارج نطاق العرض في كلا الأسلوبين، لتفادي استهلاك الذاكرة في القصص الطويلة.
- **شريط تقدّم قابل للسحب** بتدرّج لوني ثلاثي (زمرّدي → تركواز → كهرماني) للتنقل السريع بين لوحات الفصل، أو شريط نقاط/عدّاد مضغوط في SLCD+.

### ⚡ الأداء
- **تحميل هيكلي موسّع لكل فصل/موسم**: مانفستات لوحات محفوظة بذاكرة مشتركة، تُحمَّل مسبقاً للفصل التالي/السابق فينتقل القارئ بينها فوراً بلا وميض تحميل.
- **قراءة هيكلية سطحية للمكتبة** (قائمة المواسم فقط) وقراءة هيكلية لموسم واحد عند الطلب، بدل مسح كل فصول كل المواسم دفعة واحدة.
- **تحميل هيكلي (Skeleton) للواجهة**: بطاقات نائبة بشعاع لمعان متحرك أثناء أول قراءة للمكتبة، بدل شاشة فارغة أو رسالة خطأ مؤقتة.

### 🎬 شاشتا البداية والتحميل
- **سبلاش متحرك** بعلامة "المروحة" بنبض ناعم، مع دعم اختياري لفيديو تشويقي حقيقي إن وُجد ضمن الأصول (وإلا يُعرض السبلاش المرسوم برمجياً تلقائياً).
- **مؤشر/شاشة تحميل** بنفس العلامة تدور باستمرار — قابلة للاستخدام كشاشة كاملة أو مؤشر صغير مضمّن داخل أي واجهة.

### 🗂️ المكتبة والملفات
- **مكتبة قابلة للتخصيص**: مواسم وفصول مرتبة بالسحب والإفلات.
- **قالب جاهز (Template Seeder)**: زرع مباشر لمكتبة تجريبية عند أول تشغيل.
- **فتح ملفات `.dlof` خارجياً** عبر `ACTION_VIEW` كجسر خفيف مع تطبيقات أخرى تدعم الصيغة.
- **إعدادات خاصة بالتطبيق**، منفصلة تماماً عن أي تطبيق آخر يستخدم نفس الصيغة.

---

## 🧩 البنية المعمارية

```
SLCD/
├── core/          ← مكتبة مشتركة: تحليل/تحقق/تشفير وتوقيع ملفات dlof + مستودع قراءة عبر SAF
└── slcd-app/       ← التطبيق نفسه: الشاشات، السمة (Theme)، الإعدادات، الموارد
```

| الوحدة | النوع | المسؤولية |
|---|---|---|
| `:core` | `com.android.library` | `DlofParser`, `DlofValidator`, `DlofCrypto`/`DlofCryptoV2`, `DlofRepository`, `TemplatePackageIO` |
| `:slcd-app` | `com.android.application` | `MainActivity`, شاشات Splash/Install/Home/Reader، `SlcdApplication`, `SlcdSettings`, `SlcdTemplateSeeder`, `SlcdReaderRouter`, `SlcdStructuralCache`, `SlcdFanMark`/`SlcdTheme` (الهوية البصرية) |

---

## ⚙️ المتطلبات

| | |
|---|---|
| اللغة | Kotlin مع Jetpack Compose |
| Compile / Target SDK | 35 |
| Min SDK | 26 (Android 8+) — `:core` يدعم حتى 24 |
| JDK | 17 |
| Gradle | 8.7 |
| AGP | 8.5.2 |

**المكتبات الرئيسية:** Compose BOM 2024.06 · Media3/ExoPlayer 1.4.1 (فيديو splash اختياري) · Coil 2.6.0 (أغلفة المواسم) · Coroutines 1.8.1

---

## 🚀 البناء والتشغيل

```bash
# فحوصات Lint
gradle :slcd-app:lintDebug --stacktrace

# اختبارات الوحدة
gradle :slcd-app:testDebugUnitTest :core:testDebugUnitTest --stacktrace

# بناء نسخة Debug
gradle :slcd-app:assembleDebug --stacktrace

# بناء نسخة Release (يتطلب متغيرات بيئة التوقيع)
gradle :slcd-app:assembleRelease --stacktrace
```

> ⚠️ لا يحوي المستودع حالياً `gradlew`/`gradlew.bat`؛ استخدم أمر `gradle` مباشرة، أو أضف الـ wrapper عبر:
> ```bash
> gradle wrapper --gradle-version 8.7
> ```

---

## 📜 الترخيص

هذا المشروع مرخَّص بموجب [Apache License 2.0](./LICENSE).

<div align="center">
<sub>SLCD — Slime Comics dlof · v1.0.0</sub>
</div>
