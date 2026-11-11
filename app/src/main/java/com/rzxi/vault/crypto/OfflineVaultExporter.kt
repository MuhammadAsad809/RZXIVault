package com.rzxi.vault.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class OfflineVaultExporter {

    companion object {
        private val CONTAINER_MAGIC = byteArrayOf(0x52, 0x5A, 0x58, 0x31) // "RZX1"
        private const val KDF_ITERATIONS = 100_000
        private const val KDF_KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    fun exportContainer(plaintextJsonData: ByteArray, passphraseChars: CharArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(GCM_IV_LENGTH)
        val rng = SecureRandom()
        rng.nextBytes(salt)
        rng.nextBytes(iv)

        val derivedKeyBytes = deriveKey(passphraseChars, salt, KDF_ITERATIONS, KDF_KEY_LENGTH)
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintextJsonData)

        // Memory cleanup
        Arrays.fill(derivedKeyBytes, 0.toByte())
        Arrays.fill(passphraseChars, '\u0000')

        val byteStream = ByteArrayOutputStream()
        val dataOut = DataOutputStream(byteStream)

        dataOut.write(CONTAINER_MAGIC)
        dataOut.writeInt(KDF_ITERATIONS)
        dataOut.write(salt)
        dataOut.write(iv)
        dataOut.writeInt(ciphertext.size)
        dataOut.write(ciphertext)
        dataOut.flush()

        return byteStream.toByteArray()
    }

    fun importContainer(containerBytes: ByteArray, passphraseChars: CharArray): ByteArray {
        val dataIn = DataInputStream(ByteArrayInputStream(containerBytes))

        val magic = ByteArray(CONTAINER_MAGIC.size)
        dataIn.readFully(magic)
        if (!CONTAINER_MAGIC.contentEquals(magic)) {
            throw SecurityException("Invalid backup container format: magic header mismatch")
        }

        val iterations = dataIn.readInt()
        if (iterations < 50_000) {
            throw SecurityException("Insecure backup container: KDF iteration count is too low ($iterations)")
        }

        val salt = ByteArray(SALT_LENGTH)
        dataIn.readFully(salt)

        val iv = ByteArray(GCM_IV_LENGTH)
        dataIn.readFully(iv)

        val ciphertextSize = dataIn.readInt()
        val ciphertext = ByteArray(ciphertextSize)
        dataIn.readFully(ciphertext)

        val derivedKeyBytes = deriveKey(passphraseChars, salt, iterations, KDF_KEY_LENGTH)
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plaintext = cipher.doFinal(ciphertext)

        // Memory cleanup
        Arrays.fill(derivedKeyBytes, 0.toByte())
        Arrays.fill(passphraseChars, '\u0000')

        return plaintext
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, keyLengthBits)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }
}
