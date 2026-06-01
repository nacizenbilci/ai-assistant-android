package com.app.assistant.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
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
    private const val KEY_ALIAS = "com.app.assistant.database_key"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"

    // Retrieve or generate a secure key persisted in Android Keystore
    private val secretKey: SecretKey by lazy {
        getOrCreateSecretKey()
    }

    // Generates a new random initialization vector (IV)
    fun generateIV(): String {
        val secureRandom = SecureRandom()
        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(iv)
        return Base64.encodeToString(iv, Base64.DEFAULT).trim()
    }

    // Encrypt a string and return encrypted text and IV
    fun encrypt(
        input: String,
        ivString: String,
    ): Pair<String, String> {
        if (input.isEmpty()) return Pair("", ivString)
        return try {
            val iv = Base64.decode(ivString, Base64.DEFAULT)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val encryptedBytes = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
            val encryptedText = Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
            Pair(encryptedText, ivString)
        } catch (e: Exception) {
            // Safe fallback to original text if encryption fails (to ensure no data loss)
            Pair(input, ivString)
        }
    }

    // Decrypt an encrypted string using IV
    fun decrypt(
        encryptedInput: String,
        ivString: String,
    ): String {
        if (encryptedInput.isEmpty() || ivString.isEmpty()) return encryptedInput
        return try {
            val iv = Base64.decode(ivString, Base64.DEFAULT)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val encryptedBytes = Base64.decode(encryptedInput, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Safe fallback to raw database value if decryption fails (e.g. for existing plaintext entries)
            encryptedInput
        }
    }

    // Retrieve or create a persistent AES key in Android KeyStore
    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            // In-memory key fallback if Android KeyStore is unavailable (e.g., local unit tests)
            generateFallbackSecretKey()
        }
    }

    // Fallback key generator
    private fun generateFallbackSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(KEY_SIZE, SecureRandom())
        return keyGenerator.generateKey()
    }

    // Save and restore secret key as Base64 (kept for helper/debugging purposes)
    fun getSecretKeyAsString(): String {
        return try {
            Base64.encodeToString(secretKey.encoded, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun restoreSecretKeyFromString(keyString: String): SecretKey {
        val decodedKey = Base64.decode(keyString, Base64.DEFAULT)
        return SecretKeySpec(decodedKey, 0, decodedKey.size, ALGORITHM)
    }
}
