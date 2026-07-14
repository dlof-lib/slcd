package org.dlof.slcd

import android.content.Context
import android.content.res.AssetManager
import androidx.documentfile.provider.DocumentFile

/**
 * ── زرع قالب المكتبة الابتدائي ────────────────────────────────────────
 *
 * نسخة مبسّطة من مثبّت الأداة القديم (الذي كان يستخرج "حزمة أداة" منفصلة
 * إلى تخزين الجهاز أولاً). في التطبيق المستقل، القالب الابتدائي جزء
 * أصيل من أصول APK نفسه (`assets/library_template/`) — غلاف وموسم
 * وفصل تجريبيّان — فلا حاجة لخطوة استخراج وسيطة: يُنسخ مباشرة من
 * الأصول إلى مجلد مكتبة SLCD الذي اختاره المستخدم عبر SAF، فقط إن كان
 * ذلك المجلد فارغاً تماماً (أول تثبيت، لا يكرَّر الزرع ولا يطغى على
 * مكتبة موجودة أصلاً).
 */
object SlcdTemplateSeeder {

    private const val ASSET_ROOT = "library_template"

    fun seedLibraryIfEmpty(context: Context, root: DocumentFile) {
        val seasonsDir = root.findFile(SlimeComicsRepository.DIR_SEASONS) ?: return
        val coversDir = root.findFile(SlimeComicsRepository.DIR_COVERS) ?: return
        if (seasonsDir.listFiles().isNotEmpty() || coversDir.listFiles().isNotEmpty()) return // مكتبة قائمة أصلاً

        val am = context.assets
        copyAssetDirRecursive(context, am, "$ASSET_ROOT/${SlimeComicsRepository.DIR_COVERS}", coversDir)
        copyAssetDirRecursive(context, am, "$ASSET_ROOT/${SlimeComicsRepository.DIR_SEASONS}", seasonsDir)
    }

    private fun copyAssetDirRecursive(context: Context, am: AssetManager, assetPath: String, destination: DocumentFile) {
        val children = am.list(assetPath) ?: return
        if (children.isEmpty()) return
        for (name in children) {
            val childAssetPath = "$assetPath/$name"
            val grandChildren = am.list(childAssetPath) ?: emptyArray()
            if (grandChildren.isNotEmpty()) {
                val childDoc = destination.findFile(name) ?: destination.createDirectory(name) ?: continue
                copyAssetDirRecursive(context, am, childAssetPath, childDoc)
            } else {
                val mime = if (name.endsWith(".json")) "application/json" else "text/plain"
                val childDoc = destination.findFile(name) ?: destination.createFile(mime, name) ?: continue
                context.contentResolver.openOutputStream(childDoc.uri, "wt")?.use { out ->
                    am.open(childAssetPath).use { it.copyTo(out) }
                }
            }
        }
    }
}
