package org.dlof.slcd

import android.app.Application
import org.dlof.slcd.settings.SlcdSettings

/**
 * نقطة إقلاع تطبيق SLCD المستقل. مسؤولة فقط عن تهيئة [SlcdSettings] مرة
 * واحدة عند بدء العملية — لا علاقة لها بـ DlofApplication الخاص بتطبيق
 * DLoF الرئيسي، فهذا تطبيق منفصل بعملية Android خاصة به بالكامل.
 */
class SlcdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SlcdSettings.init(this)
    }
}
