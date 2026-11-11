package com.rzxi.vault.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricCryptoEngine(
    private val keyAlias: String = "RZXIVault_Hardware_Master_Key"
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private var isStrongBoxProtected: Boolean = false

    init {
        ensureMasterKeyExists()
    }

    fun isStrongBoxActive(): Boolean = isStrongBoxProtected

    private fun ensureMasterKeyExists() {
        if (!keyStore.containsAlias(keyAlias)) {
            generateHardwareKey(useStrongBox = true)
        } else {
            // Check if key is present
            isStrongBoxProtected = true
        }
    }

    private fun generateHardwareKey(useStrongBox: Boolean) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0, // 0 = every operation requires authenticating
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        try {
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
            isStrongBoxProtected = useStrongBox
        } catch (e: Exception) {
            if (useStrongBox) {
                // StrongBox not supported on this device/SoC; fallback to TEE
                generateHardwareKey(useStrongBox = false)
            } else {
                throw SecurityException("Failed to generate Android Keystore key in TEE", e)
            }
        }
    }

    private fun getSecretKey(): SecretKey {
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Key $keyAlias not found in AndroidKeyStore")
        return entry.secretKey
    }

    fun initEncryptionCryptoObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun initDecryptionCryptoObject(iv: ByteArray): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun encryptWithBiometric(cipher: Cipher, plaintext: ByteArray): ByteArray {
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val outputStream = ByteArrayOutputStream()
        outputStream.write(iv)
        outputStream.write(ciphertext)
        val combined = outputStream.toByteArray()

        // Zero original plaintext buffer immediately
        Arrays.fill(plaintext, 0.toByte())
        return combined
    }

    fun decryptWithBiometric(cipher: Cipher, combinedData: ByteArray): ByteArray {
        if (combinedData.size < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            throw IllegalArgumentException("Payload too short for GCM IV + Auth Tag")
        }
        val ciphertext = combinedData.copyOfRange(GCM_IV_LENGTH_BYTES, combinedData.size)
        return cipher.doFinal(ciphertext)
    }

    fun extractIv(combinedData: ByteArray): ByteArray {
        if (combinedData.size < GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Payload too short to extract IV")
        }
        return combinedData.copyOfRange(0, GCM_IV_LENGTH_BYTES)
    }
}
