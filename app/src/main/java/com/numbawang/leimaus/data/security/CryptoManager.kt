package com.numbawang.leimaus.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createSecretKey()
    }

    private fun createSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(bytes)

        // Combine IV (12 bytes) + Encrypted Payload
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    fun decrypt(base64Str: String): ByteArray {
        if (base64Str.isBlank()) return ByteArray(0)
        try {
            val combined = Base64.decode(base64Str, Base64.DEFAULT)
            if (combined.size < 12) return ByteArray(0)

            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)

            val encryptedSize = combined.size - 12
            val encrypted = ByteArray(encryptedSize)
            System.arraycopy(combined, 12, encrypted, 0, encryptedSize)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            return cipher.doFinal(encrypted)
        } catch (e: Exception) {
            e.printStackTrace()
            return ByteArray(0)
        }
    }

    fun encryptString(plainText: String): String {
        return encrypt(plainText.toByteArray(Charsets.UTF_8))
    }

    fun decryptString(cipherText: String): String {
        val bytes = decrypt(cipherText)
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        private const val KEY_ALIAS = "tuntivelho_master_key" // Legacy KEY_ALIAS retained to preserve saved credentials
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
