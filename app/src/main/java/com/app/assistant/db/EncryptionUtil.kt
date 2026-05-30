package com.app.assistant.db

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtil {
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 12 // Recommended size for GCM is 12 bytes
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128

    // Generate a random encryption key
    private val secretKey: SecretKey = generateSecretKey()

    // Generates a new random initialization vector (IV)
    fun generateIV(): String {
        val secureRandom = SecureRandom()
        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(iv)
        return Base64.encodeToString(iv, Base64.DEFAULT)
    }

    // Encrypt a string and return encrypted text and IV
    fun encrypt(
        input: String,
        ivString: String,
    ): Pair<String, String> {
        val iv = Base64.decode(ivString, Base64.DEFAULT)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val encryptedBytes = cipher.doFinal(input.toByteArray())
        val encryptedText = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        return Pair(encryptedText, ivString)
    }

    // Decrypt an encrypted string using IV
    fun decrypt(
        encryptedInput: String,
        ivString: String,
    ): String {
        val iv = Base64.decode(ivString, Base64.DEFAULT)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val encryptedBytes = Base64.decode(encryptedInput, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }

    // Generate a new secret key
    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(KEY_SIZE, SecureRandom())
        return keyGenerator.generateKey()
    }

    // Save and restore secret key as Base64 (for persistence)
    fun getSecretKeyAsString(): String = Base64.encodeToString(secretKey.encoded, Base64.DEFAULT)

    fun restoreSecretKeyFromString(keyString: String): SecretKey {
        val decodedKey = Base64.decode(keyString, Base64.DEFAULT)
        return SecretKeySpec(decodedKey, 0, decodedKey.size, ALGORITHM)
    }
}
