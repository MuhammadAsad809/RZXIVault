package com.rzxi.vault.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzxi.vault.crypto.BiometricCryptoEngine
import com.rzxi.vault.crypto.OfflineVaultExporter
import com.rzxi.vault.data.db.VaultDatabaseHelper
import com.rzxi.vault.data.db.VaultRepository
import com.rzxi.vault.data.model.VaultItem
import com.rzxi.vault.data.model.VaultType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class VaultViewModel : ViewModel() {

    private val repository = VaultRepository()
    private val exporter = OfflineVaultExporter()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _selectedType = MutableStateFlow<VaultType?>(null)
    val selectedType: StateFlow<VaultType?> = _selectedType.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val filteredItems: StateFlow<List<VaultItem>> = combine(
        repository.itemsFlow,
        _selectedType,
        _searchQuery
    ) { items, type, query ->
        items.filter { item ->
            val matchesType = type == null || item.type == type
            val matchesQuery = query.isEmpty() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.username.contains(query, ignoreCase = true) ||
                    item.website.contains(query, ignoreCase = true)
            matchesType && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun checkInitialization(context: Context) {
        _isInitialized.value = VaultDatabaseHelper.isInitialized(context)
        _isUnlocked.value = VaultDatabaseHelper.isUnlocked()
        if (_isUnlocked.value) {
            viewModelScope.launch {
                repository.refreshItems()
            }
        }
    }

    fun onUnlockSuccess(context: Context, rawKey: ByteArray) {
        VaultDatabaseHelper.init(context, rawKey)
        _isUnlocked.value = true
        _isInitialized.value = true
        viewModelScope.launch {
            repository.refreshItems()
        }
    }

    fun lockVault() {
        VaultDatabaseHelper.get().lockAndWipe()
        _isUnlocked.value = false
        _statusMessage.value = "Vault memory sanitized and locked."
    }

    fun setFilterType(type: VaultType?) {
        _selectedType.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun saveItem(
        id: Long = 0,
        uuid: String = UUID.randomUUID().toString(),
        type: VaultType,
        title: String,
        username: String,
        secret: String,
        website: String,
        metadata: String
    ) {
        viewModelScope.launch {
            val item = VaultItem(
                id = id,
                uuid = uuid,
                type = type,
                title = title,
                username = username,
                secret = secret,
                website = website,
                metadata = metadata,
                updatedAt = System.currentTimeMillis()
            )
            if (id == 0L) {
                repository.addItem(item)
                _statusMessage.value = "Record secured in hardware vault"
            } else {
                repository.updateItem(item)
                _statusMessage.value = "Record updated"
            }
        }
    }

    fun deleteItem(uuid: String) {
        viewModelScope.launch {
            repository.deleteItem(uuid)
            _statusMessage.value = "Record permanently deleted"
        }
    }

    suspend fun exportOfflineBackup(password: CharArray): ByteArray {
        val jsonBytes = repository.exportJsonBytes()
        return exporter.exportContainer(jsonBytes, password)
    }

    suspend fun importOfflineBackup(containerBytes: ByteArray, password: CharArray): Int {
        val decryptedJson = exporter.importContainer(containerBytes, password)
        val importedCount = repository.importJsonBytes(decryptedJson)
        _statusMessage.value = "Imported $importedCount records successfully"
        return importedCount
    }
}
