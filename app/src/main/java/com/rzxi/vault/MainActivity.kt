package com.rzxi.vault

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rzxi.vault.auth.VaultBiometricAuthManager
import com.rzxi.vault.crypto.BiometricCryptoEngine
import com.rzxi.vault.data.db.VaultDatabaseHelper
import com.rzxi.vault.data.model.VaultItem
import com.rzxi.vault.ui.screens.*
import com.rzxi.vault.ui.theme.RZXIVaultTheme
import com.rzxi.vault.ui.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import java.util.Arrays
import javax.crypto.Cipher

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private lateinit var cryptoEngine: BiometricCryptoEngine
    private lateinit var authManager: VaultBiometricAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cryptoEngine = BiometricCryptoEngine()
        authManager = VaultBiometricAuthManager(this, cryptoEngine)

        viewModel.checkInitialization(this)

        setContent {
            RZXIVaultTheme {
                val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
                val isInitialized by viewModel.isInitialized.collectAsStateWithLifecycle()
                val filteredItems by viewModel.filteredItems.collectAsStateWithLifecycle()
                val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                var isSettingsOpen by remember { mutableStateOf(false) }
                var showAddEditDialog by remember { mutableStateOf(false) }
                var itemBeingEdited by remember { mutableStateOf<VaultItem?>(null) }
                var itemBeingViewed by remember { mutableStateOf<VaultItem?>(null) }

                LaunchedEffect(statusMessage) {
                    statusMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearStatusMessage()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { padding ->
                    if (!isUnlocked) {
                        LockScreen(
                            isInitialized = isInitialized,
                            isStrongBox = cryptoEngine.isStrongBoxActive(),
                            onUnlockClick = { triggerBiometricAuth(isInitialized) },
                            modifier = Modifier.padding(padding)
                        )
                    } else if (isSettingsOpen) {
                        SettingsScreen(
                            isStrongBox = cryptoEngine.isStrongBoxActive(),
                            onLockNow = {
                                viewModel.lockVault()
                                isSettingsOpen = false
                            },
                            onExportBackup = { passChars ->
                                viewModel.exportOfflineBackup(passChars)
                            },
                            onImportBackup = { bytes, passChars ->
                                viewModel.importOfflineBackup(bytes, passChars)
                            },
                            onMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            modifier = Modifier.padding(padding)
                        )
                    } else {
                        VaultMainScreen(
                            items = filteredItems,
                            selectedType = selectedType,
                            searchQuery = searchQuery,
                            isStrongBox = cryptoEngine.isStrongBoxActive(),
                            onFilterSelect = { viewModel.setFilterType(it) },
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onAddNewItem = {
                                itemBeingEdited = null
                                showAddEditDialog = true
                            },
                            onItemClick = { item -> itemBeingViewed = item },
                            onLockClick = { viewModel.lockVault() },
                            onSettingsClick = { isSettingsOpen = true },
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            modifier = Modifier.padding(padding)
                        )
                    }

                    // Add / Edit Dialog
                    if (showAddEditDialog) {
                        AddEditItemDialog(
                            itemToEdit = itemBeingEdited,
                            onDismiss = {
                                showAddEditDialog = false
                                itemBeingEdited = null
                            },
                            onSave = { id, type, title, username, secret, website, metadata ->
                                viewModel.saveItem(
                                    id = id,
                                    type = type,
                                    title = title,
                                    username = username,
                                    secret = secret,
                                    website = website,
                                    metadata = metadata
                                )
                            }
                        )
                    }

                    // View Detail Dialog
                    if (itemBeingViewed != null) {
                        ViewItemDialog(
                            item = itemBeingViewed!!,
                            onDismiss = { itemBeingViewed = null },
                            onEdit = { item ->
                                itemBeingEdited = item
                                showAddEditDialog = true
                            },
                            onDelete = { uuid ->
                                viewModel.deleteItem(uuid)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun triggerBiometricAuth(isInitialized: Boolean) {
        if (!authManager.canAuthenticate()) {
            Toast.makeText(this, "Biometrics not configured on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (!isInitialized) {
            // First time setup: Generate DBMK and encrypt with Keystore
            authManager.authenticateForEncryption(
                title = "Initialize RZXIVault",
                subtitle = "Biometric required to create hardware master key",
                callback = object : VaultBiometricAuthManager.AuthCallback {
                    override fun onAuthenticated(cipher: Cipher) {
                        val rawDbKey = VaultDatabaseHelper.generateRandomKey(32)
                        val encryptedKeyPayload = cryptoEngine.encryptWithBiometric(cipher, rawDbKey.clone())
                        val keyStorageFile = VaultDatabaseHelper.getKeyStorageFile(this@MainActivity)
                        keyStorageFile.writeBytes(encryptedKeyPayload)

                        viewModel.onUnlockSuccess(this@MainActivity, rawDbKey)
                        Arrays.fill(rawDbKey, 0.toByte())
                        viewModel.setStatusMessage("Vault initialized with hardware encryption.")
                    }

                    override fun onError(errorCode: Int, errString: CharSequence) {
                        viewModel.setStatusMessage("Auth error ($errorCode): $errString")
                    }

                    override fun onFailed() {
                        viewModel.setStatusMessage("Biometric verification rejected")
                    }
                }
            )
        } else {
            // Unlock existing database
            val keyStorageFile = VaultDatabaseHelper.getKeyStorageFile(this)
            if (!keyStorageFile.exists()) {
                viewModel.setStatusMessage("Master key file missing.")
                return
            }

            val encryptedPayload = keyStorageFile.readBytes()
            val iv = cryptoEngine.extractIv(encryptedPayload)

            authManager.authenticateForDecryption(
                iv = iv,
                title = "Unlock RZXIVault",
                subtitle = "Biometric confirmation required",
                callback = object : VaultBiometricAuthManager.AuthCallback {
                    override fun onAuthenticated(cipher: Cipher) {
                        try {
                            val decryptedKey = cryptoEngine.decryptWithBiometric(cipher, encryptedPayload)
                            viewModel.onUnlockSuccess(this@MainActivity, decryptedKey)
                            Arrays.fill(decryptedKey, 0.toByte())
                            viewModel.setStatusMessage("Hardware keys decrypted. Vault unlocked.")
                        } catch (e: Exception) {
                            viewModel.setStatusMessage("Decryption error: ${e.message}")
                        }
                    }

                    override fun onError(errorCode: Int, errString: CharSequence) {
                        viewModel.setStatusMessage("Authentication error ($errorCode): $errString")
                    }

                    override fun onFailed() {
                        viewModel.setStatusMessage("Biometric verification failed")
                    }
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Automatically sanitize RAM and lock on backgrounding
        if (viewModel.isUnlocked.value) {
            viewModel.lockVault()
        }
    }
}
