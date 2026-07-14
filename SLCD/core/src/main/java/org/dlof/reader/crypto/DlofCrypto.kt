package org.dlof.reader.crypto

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * محرك التشفير DLoF — AES-256-GCM + PBKDF2-SHA256
 *
 * متوافق 100% مع web-signature (نفس المعاملات بالضبط):
 *   - PBKDF2-SHA256 · 310,000 تكرار
 *   - ملح عشوائي 128 بت (16 بايت)
 *   - IV عشوائي 96 بت (12 بايت)
 *   - AES-256-GCM + وسم توثيق 128 بت
 *
 * بنية الملف المشفر:
 *   [4]  magic "DLOF"
 *   [1]  version = 1
 *   [16] salt
 *   [12] iv
 *   [N]  ciphertext (XML) + GCM auth tag 16 بايت
 */
object DlofCrypto {

    private const val MAGIC = "DLOF"
    private const val VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_BITS = 256

    class CryptoException(message: String) : Exception(message)

    /**
     * تشفير نص XML إلى مصفوفة بايت بتنسيق .dlof المشفر
     */
    fun encrypt(xmlContent: String, password: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key  = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(xmlContent.toByteArray(Charsets.UTF_8))

        val magicBytes = MAGIC.toByteArray(Charsets.UTF_8)
        val total = magicBytes.size + 1 + SALT_LEN + IV_LEN + ciphertext.size
        val result = ByteArray(total)
        var offset = 0
        magicBytes.copyInto(result, offset); offset += magicBytes.size
        result[offset] = VERSION;             offset += 1
        salt.copyInto(result, offset);        offset += SALT_LEN
        iv.copyInto(result, offset);          offset += IV_LEN
        ciphertext.copyInto(result, offset)
        return result
    }

    /**
     * فك تشفير ملف .dlof مشفر وإرجاع XML الأصلي
     */
    fun decrypt(bytes: ByteArray, password: String): String {
        var offset = 0

        // التحقق من الـ magic header
        if (bytes.size < 4 + 1 + SALT_LEN + IV_LEN + 1) {
            throw CryptoException("الملف قصير جداً أو تالف")
        }
        val magic = String(bytes, offset, 4, Charsets.UTF_8); offset += 4
        if (magic != MAGIC) throw CryptoException("ليس ملف DLoF مشفراً صالحاً")

        val version = bytes[offset]; offset += 1
        if (version != VERSION) throw CryptoException("إصدار تشفير غير مدعوم: $version")

        val salt = bytes.copyOfRange(offset, offset + SALT_LEN); offset += SALT_LEN
        val iv   = bytes.copyOfRange(offset, offset + IV_LEN);   offset += IV_LEN
        val data = bytes.copyOfRange(offset, bytes.size)

        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            throw CryptoException("كلمة المرور خاطئة أو الملف تالف")
        }
    }

    /**
     * التحقق السريع هل هذا ملف .dlof مشفر (يحمل magic header)؟
     */
    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return String(bytes, 0, 4, Charsets.UTF_8) == MAGIC
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
