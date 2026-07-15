package org.dlof.reader.lighthouse

/**
 * ══════════════════════════════════════════════════════════════
 * cr.kt — Crypto Helper (مساعد التشفير)
 * ══════════════════════════════════════════════════════════════
 *
 * يُساعد في إدارة التشفير داخل حزمة DLoF_pkg:
 * - قراءة Best64.xml وتحميل إعدادات التشفير
 * - مساعدة في تشفير/فك تشفير الملفات
 * - التحقق من صحة ملف مشفر
 * - دعم Base64 الاختياري
 * - تحسين القوى (تعزيز التشفير)
 *
 * موقع الملف: dlofpkg/Lighthouse/cr.kt
 * يُستدعى من التطبيق عند التعامل مع التشفير.
 */

import java.io.File
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.spec.IvParameterSpec

object CryptoHelper {

    private const val MAGIC = "DLOF"
    private const val VERSION: Byte = 2
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    // ── نموذج إعدادات التشفير ──────────────────────────────────────

    data class CryptoProfile(
        val id: String,
        val name: String,
        val algorithm: String = "AES-256-GCM",
        val keySize: Int = 256,
        val pbkdf2Iterations: Int = 310_000,
        val saltLength: Int = 16,
        val ivLength: Int = 12,
        val tagLength: Int = 128,
        val useArgon2id: Boolean = true,
        val base64Mode: Base64Mode = Base64Mode.OPTIONAL,
        val compressionBeforeEncrypt: Boolean = true
    )

    enum class Base64Mode { ALWAYS, OPTIONAL, NEVER }

    data class EncryptionResult(
        val success: Boolean,
        val data: ByteArray? = null,
        val error: String? = null,
        val usedProfile: String = ""
    )

    // ── تحميل إعدادات التشفير ──────────────────────────────────────

    /**
     * يقرأ ملف Best64.xml ويُعيد إعدادات التشفير.
     */
    fun loadProfile(best64File: File): CryptoProfile {
        // تحليل XML مبسط (في التطبيق الفعلي يُستخدم XmlPullParser)
        val content = best64File.readText()

        return CryptoProfile(
            id = extractAttr(content, "id") ?: "Best64",
            name = extractAttr(content, "name") ?: "Best64-AES-256",
            algorithm = extractValue(content, "algorithm") ?: "AES-256-GCM",
            keySize = extractValue(content, "keySize")?.toIntOrNull() ?: 256,
            pbkdf2Iterations = extractNestedValue(content, "pbkdf2", "iterations")?.toIntOrNull() ?: 310_000,
            useArgon2id = extractAttr(content, "enabled", "argon2id")?.toBoolean() ?: true,
            base64Mode = when (extractValue(content, "base64Mode")?.lowercase()) {
                "always" -> Base64Mode.ALWAYS
                "never" -> Base64Mode.NEVER
                else -> Base64Mode.OPTIONAL
            },
            compressionBeforeEncrypt = extractValue(content, "compressionBeforeEncrypt")?.toBoolean() ?: true
        )
    }

    /**
     * يقرأ ملف WQ.JSON المساعد.
     */
    fun loadWQJson(wqFile: File): WQConfig {
        val content = wqFile.readText()
        return WQConfig(
            autoLoad = content.contains("\"autoLoad\": true"),
            mergeWithExisting = content.contains("\"mergeWithExisting\": true"),
            backupBeforeApply = content.contains("\"backupBeforeApply\": true"),
            primaryKdf = extractJsonValue(content, "primary") ?: "argon2id",
            fallbackKdf = extractJsonValue(content, "fallback") ?: "pbkdf2-sha256"
        )
    }

    data class WQConfig(
        val autoLoad: Boolean,
        val mergeWithExisting: Boolean,
        val backupBeforeApply: Boolean,
        val primaryKdf: String,
        val fallbackKdf: String
    )

    // ── تشفير محسّن v2 ──────────────────────────────────────────────

    /**
     * تشفير محتوى نصي بتنسيق DLoF v2 (محسّن).
     * يدعم AES-256-GCM + PBKDF2/Argon2.
     */
    fun encryptV2(plainText: String, password: String, profile: CryptoProfile = defaultProfile()): EncryptionResult {
        return try {
            val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }

            // اشتقاق المفتاح
            val key = if (profile.useArgon2id) {
                deriveKeyArgon2id(password, salt, profile)
            } else {
                deriveKeyPBKDF2(password, salt, profile.pbkdf2Iterations)
            }

            // تشفير AES-256-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            val plaintext = if (profile.compressionBeforeEncrypt) {
                compress(plainText.toByteArray(Charsets.UTF_8))
            } else {
                plainText.toByteArray(Charsets.UTF_8)
            }

            val ciphertext = cipher.doFinal(plaintext)

            // بناء الهيكل: [magic][version][salt][iv][ciphertext]
            val result = ByteArray(4 + 1 + SALT_LEN + IV_LEN + ciphertext.size)
            var offset = 0
            MAGIC.toByteArray(Charsets.UTF_8).copyInto(result, offset); offset += 4
            result[offset] = VERSION; offset += 1
            salt.copyInto(result, offset); offset += SALT_LEN
            iv.copyInto(result, offset); offset += IV_LEN
            ciphertext.copyInto(result, offset)

            EncryptionResult(success = true, data = result, usedProfile = profile.name)
        } catch (e: Exception) {
            EncryptionResult(success = false, error = "فشل التشفير: ${e.message}")
        }
    }

    /**
     * فك تشفير ملف DLoF v2.
     */
    fun decryptV2(encryptedData: ByteArray, password: String, profile: CryptoProfile = defaultProfile()): EncryptionResult {
        return try {
            if (encryptedData.size < 4 + 1 + SALT_LEN + IV_LEN + 1) {
                return EncryptionResult(success = false, error = "الملف قصير جداً أو تالف")
            }

            var offset = 0
            val magic = String(encryptedData, offset, 4, Charsets.UTF_8); offset += 4
            if (magic != MAGIC) return EncryptionResult(success = false, error = "ليس ملف DLoF مشفراً صالحاً")

            val version = encryptedData[offset]; offset += 1
            if (version != VERSION) return EncryptionResult(success = false, error = "إصدار غير مدعوم: $version")

            val salt = encryptedData.copyOfRange(offset, offset + SALT_LEN); offset += SALT_LEN
            val iv = encryptedData.copyOfRange(offset, offset + IV_LEN); offset += IV_LEN
            val data = encryptedData.copyOfRange(offset, encryptedData.size)

            val key = if (profile.useArgon2id) {
                deriveKeyArgon2id(password, salt, profile)
            } else {
                deriveKeyPBKDF2(password, salt, profile.pbkdf2Iterations)
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            val decrypted = cipher.doFinal(data)

            val result = if (profile.compressionBeforeEncrypt) {
                decompress(decrypted)
            } else {
                decrypted
            }

            EncryptionResult(success = true, data = result, usedProfile = profile.name)
        } catch (e: Exception) {
            EncryptionResult(success = false, error = "كلمة المرور خاطئة أو الملف تالف")
        }
    }

    /**
     * التحقق السريع هل الملف مشفر بتنسيق DLoF.
     */
    fun isEncryptedFile(data: ByteArray): Boolean {
        return data.size >= 4 && String(data, 0, 4, Charsets.UTF_8) == MAGIC
    }

    /**
     * تحويل Base64 اختياري — يُستخدم فقط عند الحاجة.
     */
    fun optionalBase64(data: ByteArray, encode: Boolean): ByteArray {
        return if (encode) {
            java.util.Base64.getEncoder().encode(data)
        } else {
            data
        }
    }

    // ── تحسين القوى ─────────────────────────────────────────────────

    /**
     * يُعيد ملفاً مشفراً بتعزيز إضافي (HMAC للتحقق من السلامة).
     * يُستخدم للملفات الحساسة جداً.
     */
    fun encryptEnhanced(plainText: String, password: String, profile: CryptoProfile = defaultProfile()): EncryptionResult {
        return try {
            val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
            val hmacSalt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }

            val key = deriveKeyPBKDF2(password, salt, profile.pbkdf2Iterations)
            val hmacKey = deriveKeyPBKDF2(password + "hmac", hmacSalt, profile.pbkdf2Iterations)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // حساب HMAC
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(hmacKey)
            val hmac = mac.doFinal(ciphertext)

            // [magic][v2][salt][iv][hmacSalt][hmac][ciphertext]
            val totalSize = 4 + 1 + SALT_LEN + IV_LEN + SALT_LEN + hmac.size + ciphertext.size
            val result = ByteArray(totalSize)
            var offset = 0
            MAGIC.toByteArray(Charsets.UTF_8).copyInto(result, offset); offset += 4
            result[offset] = (VERSION + 1).toByte(); offset += 1 // v3 = enhanced
            salt.copyInto(result, offset); offset += SALT_LEN
            iv.copyInto(result, offset); offset += IV_LEN
            hmacSalt.copyInto(result, offset); offset += SALT_LEN
            hmac.copyInto(result, offset); offset += hmac.size
            ciphertext.copyInto(result, offset)

            EncryptionResult(success = true, data = result, usedProfile = "${profile.name}-Enhanced")
        } catch (e: Exception) {
            EncryptionResult(success = false, error = "فشل التشفير المعزز: ${e.message}")
        }
    }

    // ── مساعدات خاصة ──────────────────────────────────────────────

    private fun deriveKeyPBKDF2(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun deriveKeyArgon2id(password: String, salt: ByteArray, profile: CryptoProfile): SecretKeySpec {
        // في التطبيق الفعلي: استخدام مكتبة Argon2 (مثل BouncyCastle)
        // هنا نستخدم PBKDF2 كبديل مع عدد تكرارات أعلى
        return deriveKeyPBKDF2(password, salt, profile.pbkdf2Iterations * 2)
    }

    private fun compress(data: ByteArray): ByteArray {
        // في التطبيق الفعلي: استخدام Deflater/Inflater
        return data // placeholder
    }

    private fun decompress(data: ByteArray): ByteArray {
        // في التطبيق الفعلي: استخدام Deflater/Inflater
        return data // placeholder
    }

    private fun defaultProfile() = CryptoProfile(
        id = "Best64",
        name = "Best64-AES-256",
        pbkdf2Iterations = 310_000,
        useArgon2id = true,
        base64Mode = Base64Mode.OPTIONAL
    )

    // ── استخراج من XML/JSON بسيط ──────────────────────────────────

    private fun extractAttr(xml: String, attr: String, tag: String? = null): String? {
        val pattern = if (tag != null) {
            "$tag[^>]*$attr=\"([^\"]*)\"".toRegex()
        } else {
            "$attr=\"([^\"]*)\"".toRegex()
        }
        return pattern.find(xml)?.groupValues?.get(1)
    }

    private fun extractValue(xml: String, tag: String): String? {
        val pattern = "<$tag>([^<]*)</$tag>".toRegex()
        return pattern.find(xml)?.groupValues?.get(1)
    }

    private fun extractNestedValue(xml: String, parent: String, child: String): String? {
        val pattern = "<$parent[^>]*>.*?<$child>([^<]*)</$child>.*?</$parent>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return pattern.find(xml)?.groupValues?.get(1)
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }
}
