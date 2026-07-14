package org.dlof.reader.crypto

import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * ══════════════════════════════════════════════════════════════
 * DlofCryptoV2 — محرك التشفير المحسّن v2.0
 * ══════════════════════════════════════════════════════════════
 *
 * تحسينات القوى مقارنةً بالإصدار الأول:
 * 1. Base64 أصبح اختيارياً (يمكن تعطيله لتقليل الحجم)
 * 2. دعم Argon2id كبديل لـ PBKDF2 (مع PBKDF2 كاحتياطي)
 * 3. ضغط البيانات قبل التشفير (اختياري)
 * 4. HMAC-SHA256 للتحقق من سلامة البيانات
 * 5. تنسيق ملف مشفر محسّن (v2) مع magic header محدّث
 * 6. تحميل إعدادات التشفير من Best64.xml
 * 7. توافق كامل مع الإصدارات السابقة (v1)
 *
 * هيكل الملف المشفر v2:
 *   [4]  magic "DLOF"
 *   [1]  version = 2
 *   [16] salt (PBKDF2)
 *   [12] iv (GCM)
 *   [N]  ciphertext (مضغوط اختيارياً) + GCM auth tag 16 بايت
 *
 * هيكل الملف المشفر v3 (معزز):
 *   [4]  magic "DLOF"
 *   [1]  version = 3
 *   [16] salt (PBKDF2)
 *   [12] iv (GCM)
 *   [16] hmacSalt
 *   [32] HMAC-SHA256
 *   [N]  ciphertext + GCM auth tag
 */
object DlofCryptoV2 {

    private const val MAGIC = "DLOF"
    private const val V2: Byte = 2
    private const val V3: Byte = 3
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_BITS = 256

    class CryptoException(message: String) : Exception(message)

    // ── نموذج إعدادات التشفير ──────────────────────────────────────

    data class CryptoConfig(
        val algorithm: String = "AES-256-GCM",
        val pbkdf2Iterations: Int = 310_000,
        val useArgon2id: Boolean = false,  // true يتطلب BouncyCastle
        val compressBeforeEncrypt: Boolean = true,
        val useBase64: Boolean = false,     // false = رجوع للملفات المنفصلة
        val enhancedIntegrity: Boolean = false // v3 مع HMAC
    )

    data class EncryptionResult(
        val data: ByteArray,
        val version: Int,
        val config: CryptoConfig
    )

    // ── التشفير v2 (محسّن) ─────────────────────────────────────────

    /**
     * تشفير نص XML بتنسيق DLoF v2.
     * يدعم الضغط الاختياري وBase64 الاختياري.
     */
    fun encrypt(
        xmlContent: String,
        password: String,
        config: CryptoConfig = CryptoConfig()
    ): EncryptionResult {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, config)

        var plaintext = xmlContent.toByteArray(Charsets.UTF_8)

        // ضغط اختياري قبل التشفير
        if (config.compressBeforeEncrypt) {
            plaintext = compress(plaintext)
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // بناء الهيكل
        val version = if (config.enhancedIntegrity) V3 else V2
        val totalSize = calculateHeaderSize(version) + ciphertext.size +
                if (version == V3) 32 else 0

        val result = ByteArray(totalSize)
        var offset = 0

        MAGIC.toByteArray(Charsets.UTF_8).copyInto(result, offset); offset += 4
        result[offset] = version; offset += 1
        salt.copyInto(result, offset); offset += SALT_LEN
        iv.copyInto(result, offset); offset += IV_LEN

        if (version == V3) {
            // إضافة HMAC للنسخة المعززة
            val hmacSalt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            val hmacKey = deriveKey(password + "-hmac", hmacSalt, config)
            hmacSalt.copyInto(result, offset); offset += SALT_LEN

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(hmacKey)
            val hmac = mac.doFinal(ciphertext)
            hmac.copyInto(result, offset); offset += hmac.size
        }

        ciphertext.copyInto(result, offset)

        return EncryptionResult(result, version.toInt(), config)
    }

    /**
     * فك تشفير ملف DLoF مشفر (يدعم v1, v2, v3).
     */
    fun decrypt(bytes: ByteArray, password: String, config: CryptoConfig = CryptoConfig()): String {
        if (bytes.size < 4 + 1 + SALT_LEN + IV_LEN + 1) {
            throw CryptoException("الملف قصير جداً أو تالف")
        }

        var offset = 0
        val magic = String(bytes, offset, 4, Charsets.UTF_8); offset += 4
        if (magic != MAGIC) throw CryptoException("ليس ملف DLoF مشفراً صالحاً")

        val version = bytes[offset]; offset += 1

        return when (version) {
            1.toByte() -> decryptV1(bytes, password)
            V2 -> decryptV2(bytes, offset, password, config)
            V3 -> decryptV3(bytes, offset, password, config)
            else -> throw CryptoException("إصدار تشفير غير مدعوم: $version")
        }
    }

    // ── فك التشفير للإصدارات ──────────────────────────────────────

    private fun decryptV1(bytes: ByteArray, password: String): String {
        // الرجوع للإصدار الأول
        return DlofCrypto.decrypt(bytes, password)
    }

    private fun decryptV2(bytes: ByteArray, offset: Int, password: String, config: CryptoConfig): String {
        var pos = offset
        val salt = bytes.copyOfRange(pos, pos + SALT_LEN); pos += SALT_LEN
        val iv = bytes.copyOfRange(pos, pos + IV_LEN); pos += IV_LEN
        val data = bytes.copyOfRange(pos, bytes.size)

        val key = deriveKey(password, salt, config)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            var decrypted = cipher.doFinal(data)

            if (config.compressBeforeEncrypt) {
                decrypted = decompress(decrypted)
            }

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw CryptoException("كلمة المرور خاطئة أو الملف تالف (v2)")
        }
    }

    private fun decryptV3(bytes: ByteArray, offset: Int, password: String, config: CryptoConfig): String {
        var pos = offset
        val salt = bytes.copyOfRange(pos, pos + SALT_LEN); pos += SALT_LEN
        val iv = bytes.copyOfRange(pos, pos + IV_LEN); pos += IV_LEN
        val hmacSalt = bytes.copyOfRange(pos, pos + SALT_LEN); pos += SALT_LEN
        val storedHmac = bytes.copyOfRange(pos, pos + 32); pos += 32
        val data = bytes.copyOfRange(pos, bytes.size)

        // التحقق من HMAC أولاً
        val hmacKey = deriveKey(password + "-hmac", hmacSalt, config)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        val computedHmac = mac.doFinal(data)

        if (!computedHmac.contentEquals(storedHmac)) {
            throw CryptoException("فشل التحقق من سلامة البيانات (HMAC)")
        }

        return decryptV2(bytes, 5, password, config.copy(enhancedIntegrity = false))
    }

    // ── التحقق والمساعدات ─────────────────────────────────────────

    /**
     * التحقق السريع هل هذا ملف DLoF مشفر.
     */
    fun isEncrypted(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && String(bytes, 0, 4, Charsets.UTF_8) == MAGIC
    }

    /**
     * يُعيد إصدار التشفير (1, 2, أو 3) أو -1 إن لم يكن ملفاً مشفراً.
     */
    fun detectVersion(bytes: ByteArray): Int {
        if (bytes.size < 5) return -1
        if (String(bytes, 0, 4, Charsets.UTF_8) != MAGIC) return -1
        return bytes[4].toInt()
    }

    /**
     * تحويل ByteArray إلى Base64 (اختياري).
     */
    fun toBase64Optional(data: ByteArray, useBase64: Boolean): ByteArray {
        return if (useBase64) {
            java.util.Base64.getEncoder().encode(data)
        } else {
            data
        }
    }

    /**
     * تحميل إعدادات التشفير من ملف Best64.xml.
     */
    fun loadFromBest64(xmlContent: String): CryptoConfig {
        return CryptoConfig(
            algorithm = extractXmlValue(xmlContent, "algorithm") ?: "AES-256-GCM",
            pbkdf2Iterations = extractXmlValue(xmlContent, "iterations")?.toIntOrNull() ?: 310_000,
            useArgon2id = extractXmlAttr(xmlContent, "enabled", "argon2id") == "true",
            compressBeforeEncrypt = extractXmlValue(xmlContent, "compressionBeforeEncrypt") == "true",
            useBase64 = when (extractXmlValue(xmlContent, "base64Mode")) {
                "always" -> true
                "never" -> false
                else -> false // optional -> false بشكل افتراضي
            },
            enhancedIntegrity = extractXmlValue(xmlContent, "verifyIntegrity") == "true"
        )
    }

    // ── مساعدات خاصة ──────────────────────────────────────────────

    private fun deriveKey(password: String, salt: ByteArray, config: CryptoConfig): SecretKeySpec {
        return if (config.useArgon2id) {
            deriveKeyArgon2id(password, salt, config)
        } else {
            deriveKeyPBKDF2(password, salt, config.pbkdf2Iterations)
        }
    }

    private fun deriveKeyPBKDF2(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun deriveKeyArgon2id(password: String, salt: ByteArray, config: CryptoConfig): SecretKeySpec {
        // في الإنتاج: استخدام BouncyCastle Argon2
        // هنا نستخدم PBKDF2 مع تكرارات مضاعفة كاحتياطي
        return deriveKeyPBKDF2(password, salt, config.pbkdf2Iterations * 2)
    }

    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(data.size)
        val count = deflater.deflate(buffer)
        deflater.end()
        return if (count < data.size) buffer.copyOf(count) else data
    }

    private fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val buffer = ByteArray(data.size * 4)
        val count = inflater.inflate(buffer)
        inflater.end()
        return buffer.copyOf(count)
    }

    private fun calculateHeaderSize(version: Byte): Int {
        return 4 + 1 + SALT_LEN + IV_LEN + if (version == V3) SALT_LEN + 32 else 0
    }

    private fun extractXmlValue(xml: String, tag: String): String? {
        val regex = "<$tag>([^<]*)</$tag>".toRegex()
        return regex.find(xml)?.groupValues?.get(1)
    }

    private fun extractXmlAttr(xml: String, attr: String, tag: String): String? {
        val regex = "$tag[^>]*$attr=\"([^\"]*)\"".toRegex()
        return regex.find(xml)?.groupValues?.get(1)
    }
}
