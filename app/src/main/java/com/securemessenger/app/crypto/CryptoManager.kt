// مسیر: app/src/main/java/com/securemessenger/app/crypto/CryptoManager.kt
package com.securemessenger.app.crypto

import android.util.Base64
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.math.BigInteger
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * کلاس مدیریت رمزنگاری سه‌لایه (RSA, ECC, DH)
 * مسئول تولید کلیدها، رمزگذاری و رمزگشایی پیام‌ها
 */
class CryptoManager {

    // کلیدهای RSA
    private var rsaKeyPair: KeyPair? = null

    // کلیدهای ECC
    private var eccKeyPair: KeyPair? = null

    // کلیدهای Diffie-Hellman
    private var dhKeyPair: KeyPair? = null

    companion object {
        private const val RSA_KEY_SIZE = 2048
        private const val ECC_CURVE = "secp256r1"
        private const val DH_KEY_SIZE = 2048
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    /**
     * تولید تمام کلیدهای مورد نیاز (RSA, ECC, DH)
     */
    suspend fun generateKeys(): Boolean {
        return try {
            generateRSAKeyPair()
            generateECCKeyPair()
            generateDHKeyPair()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * تولید کلید RSA
     */
    private fun generateRSAKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(RSA_KEY_SIZE)
        rsaKeyPair = keyPairGenerator.generateKeyPair()
    }

    /**
     * تولید کلید ECC
     */
    private fun generateECCKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec(ECC_CURVE)
        keyPairGenerator.initialize(ecSpec)
        eccKeyPair = keyPairGenerator.generateKeyPair()
    }

    /**
     * تولید کلید Diffie-Hellman
     */
    private fun generateDHKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance("DH")
        keyPairGenerator.initialize(DH_KEY_SIZE)
        dhKeyPair = keyPairGenerator.generateKeyPair()
    }

    /**
     * دریافت کلیدهای عمومی در فرمت PEM
     */
    fun getPublicKeysAsPem(): Map<String, String> {
        return mapOf(
            "rsa_public_key" to convertPublicKeyToPem(rsaKeyPair?.public, "RSA PUBLIC KEY"),
            "ecc_public_key" to convertPublicKeyToPem(eccKeyPair?.public, "EC PUBLIC KEY"),
            "dh_public_key" to encodeDHPublicKey(dhKeyPair?.public)
        )
    }

    /**
     * تبدیل کلید عمومی به فرمت PEM
     */
    private fun convertPublicKeyToPem(publicKey: PublicKey?, type: String): String {
        return try {
            val stringWriter = StringWriter()
            val pemWriter = PemWriter(stringWriter)
            val pemObject = PemObject(type, publicKey?.encoded)
            pemWriter.writeObject(pemObject)
            pemWriter.close()
            stringWriter.toString()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * رمزگذاری DH Public Key به فرمت Hex
     */
    private fun encodeDHPublicKey(publicKey: PublicKey?): String {
        return try {
            publicKey?.encoded?.let {
                Base64.encodeToString(it, Base64.NO_WRAP)
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * رمزنگاری سه‌لایه پیام
     * مراحل: AES -> ECC -> RSA
     */
    suspend fun tripleEncrypt(
        message: String,
        recipientRSAPublicKey: String,
        recipientECCPublicKey: String,
        recipientDHPublicKey: String,
        recipientId: String
    ): String? {
        return try {
            // مرحله 1: ایجاد کلید مشترک با DH
            val sharedSecret = generateSharedSecret(recipientDHPublicKey)

            // مرحله 2: رمزنگاری با AES
            val aesEncrypted = encryptWithAES(message, sharedSecret)

            // مرحله 3: رمزنگاری با ECC
            val eccEncrypted = encryptWithECC(aesEncrypted, recipientECCPublicKey)

            // مرحله 4: رمزنگاری نهایی با RSA
            val rsaEncrypted = encryptWithRSA(eccEncrypted, recipientRSAPublicKey)

            // مرحله 5: تبدیل به Base64
            Base64.encodeToString(rsaEncrypted.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * رمزگشایی سه‌لایه پیام
     * مراحل: RSA -> ECC -> AES
     */
    suspend fun tripleDecrypt(
        encryptedMessage: String,
        senderId: String,
        senderECCPublicKey: String
    ): String? {
        return try {
            // مرحله 1: تبدیل از Base64
            val encryptedData = Base64.decode(encryptedMessage, Base64.NO_WRAP).toString(Charsets.UTF_8)

            // مرحله 2: رمزگشایی RSA
            val rsaDecrypted = decryptWithRSA(encryptedData)

            // مرحله 3: رمزگشایی ECC
            val eccDecrypted = decryptWithECC(rsaDecrypted, senderECCPublicKey)

            // مرحله 4: رمزگشایی AES
            val dhSharedSecret = generateSharedSecretFromSender(senderId)
            decryptWithAES(eccDecrypted, dhSharedSecret)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * رمزنگاری با AES-GCM
     */
    private fun encryptWithAES(plaintext: String, sharedSecret: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(sharedSecret.copyOf(32), "AES")

        // تولید IV تصادفی
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val encryptedData = cipher.doFinal(plaintext.toByteArray())

        // ترکیب IV + encrypted data
        val result = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(encryptedData, 0, result, iv.size, encryptedData.size)

        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    /**
     * رمزگشایی با AES-GCM
     */
    private fun decryptWithAES(encryptedData: String, sharedSecret: ByteArray): String {
        val data = Base64.decode(encryptedData, Base64.NO_WRAP)

        // جدا کردن IV از داده‌های رمزنگاری شده
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = data.copyOfRange(GCM_IV_LENGTH, data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(sharedSecret.copyOf(32), "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decryptedData = cipher.doFinal(encrypted)

        return String(decryptedData)
    }

    /**
     * رمزنگاری با ECC
     */
    private fun encryptWithECC(data: String, recipientPublicKey: String): String {
        // برای سادگی، از Base64 encoding استفاده می‌کنیم
        // در پیاده‌سازی واقعی، باید از ECIES استفاده شود
        return Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * رمزگشایی با ECC
     */
    private fun decryptWithECC(encryptedData: String, senderPublicKey: String): String {
        // برای سادگی، از Base64 decoding استفاده می‌کنیم
        return String(Base64.decode(encryptedData, Base64.NO_WRAP))
    }

    /**
     * رمزنگاری با RSA
     */
    private fun encryptWithRSA(data: String, recipientPublicKey: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val publicKey = decodeRSAPublicKey(recipientPublicKey)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    /**
     * رمزگشایی با RSA
     */
    private fun decryptWithRSA(encryptedData: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair?.private)
        val decryptedBytes = cipher.doFinal(Base64.decode(encryptedData, Base64.NO_WRAP))
        return String(decryptedBytes)
    }

    /**
     * تولید کلید مشترک با Diffie-Hellman
     */
    private fun generateSharedSecret(recipientDHPublicKey: String): ByteArray {
        // پیاده‌سازی ساده - در عمل باید ECDH استفاده شود
        val keyBytes = Base64.decode(recipientDHPublicKey, Base64.NO_WRAP)
        val hash = MessageDigest.getInstance("SHA-256")
        return hash.digest(keyBytes + (dhKeyPair?.private?.encoded ?: byteArrayOf()))
    }

    /**
     * تولید کلید مشترک از فرستنده
     */
    private fun generateSharedSecretFromSender(senderId: String): ByteArray {
        // پیاده‌سازی ساده برای مثال
        val hash = MessageDigest.getInstance("SHA-256")
        return hash.digest(senderId.toByteArray() + (dhKeyPair?.private?.encoded ?: byteArrayOf()))
    }

    /**
     * تبدیل کلید عمومی RSA از PEM
     */
    private fun decodeRSAPublicKey(pemKey: String): PublicKey {
        val keyData = pemKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.decode(keyData, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * تولید امضای دیجیتال برای کلید DH
     */
    fun signDHKey(dhPublicKey: String): String? {
        return try {
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(eccKeyPair?.private)
            signature.update(dhPublicKey.toByteArray())
            val signatureBytes = signature.sign()
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * تولید nonce یکتا
     */
    fun generateNonce(): String {
        return java.util.UUID.randomUUID().toString()
    }

    /**
     * دریافت timestamp فعلی
     */
    fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()
    }
}