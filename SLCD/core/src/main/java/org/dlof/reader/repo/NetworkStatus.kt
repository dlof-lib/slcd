package org.dlof.reader.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * ══════════════════════════════════════════════════════════════
 * NetworkStatus — كشف وتصنيف حالات الشبكة أثناء استرداد/فتح الحزم
 * ══════════════════════════════════════════════════════════════
 *
 * ملفات `.dlofpkg`/`.dlofSeries` قد تُفتح عبر Uri من مزوّد محتوى (SAF)
 * مدعوم بخدمة سحابية (Google Drive، OneDrive، بريد إلكتروني مرفق...).
 * قراءة البايتات من هذه الـ Uris تمرّ فعلياً عبر الشبكة، حتى لو بدت
 * محلية على السطح. هذا الكائن يساعد في:
 *  1) معرفة إن كان الجهاز متصلاً بالإنترنت أصلاً (لتمييز خطأ الشبكة).
 *  2) تخمين إن كان مصدر الملف على الأرجح "بعيداً" (سحابياً) لعرض حالة
 *     "التحميل عبر الشبكة" وتلميح "قد يستغرق هذا بعض الوقت".
 *  3) تصنيف استثناء وقع أثناء القراءة كـ "خطأ شبكة" أو "خطأ حزمة" عادي.
 */
object NetworkStatus {

    /** أسماء مزوّدي المحتوى المحليين المعروفين — القراءة منهم فورية ولا تحتاج شبكة. */
    private val LOCAL_AUTHORITIES = setOf(
        "com.android.externalstorage.documents",
        "com.android.providers.downloads.documents",
        "com.android.providers.media.documents",
        "com.android.providers.downloads",
        "com.android.providers.media",
        "media",
        "downloads"
    )

    /** هل الجهاز متصل حالياً بالإنترنت (Wi-Fi أو بيانات خلوية أو إيثرنت)؟ */
    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true // تعذّر الاستعلام — لا نمنع المتابعة بلا داعٍ
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * تخمين إن كان [uri] على الأرجح مصدراً "بعيداً" (سحابياً) بدل تخزين محلي
     * مباشر — يعتمد على "authority" الخاص بالـ Uri. تخمين وليس يقيناً
     * (بعض تطبيقات التخزين السحابي تُظهر Authority محلياً ظاهرياً)، لكنه
     * كافٍ لقرار عرض "تحميل عبر الشبكة" بدل "تحميل" عادي.
     */
    fun isLikelyRemoteSource(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return true
        if (scheme != "content") return false
        val authority = uri.authority?.lowercase() ?: return false
        if (authority in LOCAL_AUTHORITIES) return false
        // أي مزوّد محتوى آخر (Drive، OneDrive، بريد، تطبيقات مشاركة...) نعامله كبعيد محتمل
        return true
    }

    /** تصنيف فشل تقني وقع أثناء قراءة/تثبيت حزمة — شبكة أم خطأ حزمة عادي. */
    enum class FailureKind { NETWORK, PACKAGE, VERIFICATION }

    /**
     * يصنّف [error] بالاستناد إلى نوعه ورسالته: أخطاء IO المرتبطة بالمهلة
     * أو تعذّر الاستضافة تُصنَّف شبكة، وأي شيء آخر (ZipException، JSON...)
     * يبقى "خطأ حزمة" عادياً.
     */
    fun classify(error: Throwable, context: Context? = null): FailureKind {
        val isTimeoutOrHost = error is SocketTimeoutException || error is UnknownHostException
        val messageSuggestsNetwork = error.message?.let { msg ->
            val m = msg.lowercase()
            "timeout" in m || "network" in m || "host" in m || "connection" in m
        } ?: false
        val offline = context != null && !isOnline(context)

        return when {
            isTimeoutOrHost || messageSuggestsNetwork -> FailureKind.NETWORK
            offline && error is IOException -> FailureKind.NETWORK
            else -> FailureKind.PACKAGE
        }
    }
}
