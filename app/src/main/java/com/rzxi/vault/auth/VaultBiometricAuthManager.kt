package com.rzxi.vault.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.rzxi.vault.crypto.BiometricCryptoEngine
import javax.crypto.Cipher

class VaultBiometricAuthManager(
    private val activity: FragmentActivity,
    private val cryptoEngine: BiometricCryptoEngine
) {
    interface AuthCallback {
        fun onAuthenticated(cipher: Cipher)
        fun onError(errorCode: Int, errString: CharSequence)
        fun onFailed()
    }

    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateForEncryption(
        title: String = "Unlock RZXIVault",
        subtitle: String = "Authenticate to encrypt master keys",
        callback: AuthCallback
    ) {
        val cryptoObject = cryptoEngine.initEncryptionCryptoObject()
        startBiometricPrompt(cryptoObject, title, subtitle, callback)
    }

    fun authenticateForDecryption(
        iv: ByteArray,
        title: String = "Unlock RZXIVault",
        subtitle: String = "Confirm biometric to unlock encrypted storage",
        callback: AuthCallback
    ) {
        val cryptoObject = cryptoEngine.initDecryptionCryptoObject(iv)
        startBiometricPrompt(cryptoObject, title, subtitle, callback)
    }

    private fun startBiometricPrompt(
        cryptoObject: BiometricPrompt.CryptoObject,
        title: String,
        subtitle: String,
        callback: AuthCallback
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(true)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher != null) {
                        callback.onAuthenticated(authenticatedCipher)
                    } else {
                        callback.onError(-1, "Hardware cipher missing in biometric result")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    callback.onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    callback.onFailed()
                }
            }
        )

        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }
}
