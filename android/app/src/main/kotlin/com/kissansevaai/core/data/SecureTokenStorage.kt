package com.kissansevaai.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed AES-256 GCM encryption provider.
 * Implements hardware/Keystore-backed encryption directly using standard javax.crypto
 * and AndroidKeyStore APIs (per Android official guidance replacing deprecated androidx.security:security-crypto).
 */
@Singleton
class SecureTokenStorage @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Output format: [1 byte: IV length N][N bytes: IV][Ciphertext + 16-byte Auth Tag]
     */
    fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(1 + iv.size + ciphertext.size)
        buffer.put(iv.size.toByte())
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    /**
     * Decrypts AES-256-GCM encrypted bytes back to plaintext string.
     * Returns null if decryption fails (e.g. data corrupted or tampered).
     */
    fun decrypt(encryptedBytes: ByteArray): String? {
        return try {
            if (encryptedBytes.size < 2) return null
            val buffer = ByteBuffer.wrap(encryptedBytes)
            val ivLength = buffer.get().toInt()
            if (ivLength <= 0 || ivLength > buffer.remaining()) return null

            val iv = ByteArray(ivLength)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "kissan_jwt_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
