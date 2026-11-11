package com.rzxi.vault.data.db

import com.rzxi.vault.data.model.VaultItem
import com.rzxi.vault.data.model.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VaultRepository {

    private val _itemsFlow = MutableStateFlow<List<VaultItem>>(emptyList())
    val itemsFlow: StateFlow<List<VaultItem>> = _itemsFlow.asStateFlow()

    suspend fun refreshItems() = withContext(Dispatchers.IO) {
        if (VaultDatabaseHelper.isUnlocked()) {
            val list = VaultDatabaseHelper.get().getAllItems()
            _itemsFlow.value = list
        } else {
            _itemsFlow.value = emptyList()
        }
    }

    suspend fun addItem(item: VaultItem) = withContext(Dispatchers.IO) {
        VaultDatabaseHelper.get().insertItem(item)
        refreshItems()
    }

    suspend fun updateItem(item: VaultItem) = withContext(Dispatchers.IO) {
        VaultDatabaseHelper.get().updateItem(item)
        refreshItems()
    }

    suspend fun deleteItem(uuid: String) = withContext(Dispatchers.IO) {
        VaultDatabaseHelper.get().deleteItem(uuid)
        refreshItems()
    }

    suspend fun exportJsonBytes(): ByteArray = withContext(Dispatchers.IO) {
        val items = VaultDatabaseHelper.get().getAllItems()
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("uuid", item.uuid)
                put("type", item.type.name)
                put("title", item.title)
                put("username", item.username)
                put("secret", item.secret)
                put("website", item.website)
                put("metadata", item.metadata)
                put("updatedAt", item.updatedAt)
            }
            jsonArray.put(obj)
        }
        jsonArray.toString(2).toByteArray(Charsets.UTF_8)
    }

    suspend fun importJsonBytes(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val jsonStr = String(bytes, Charsets.UTF_8)
        val jsonArray = JSONArray(jsonStr)
        var count = 0
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val type = try {
                VaultType.valueOf(obj.getString("type"))
            } catch (e: Exception) {
                VaultType.PASSWORD
            }
            val item = VaultItem(
                uuid = obj.getString("uuid"),
                type = type,
                title = obj.getString("title"),
                username = obj.optString("username", ""),
                secret = obj.getString("secret"),
                website = obj.optString("website", ""),
                metadata = obj.optString("metadata", ""),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
            VaultDatabaseHelper.get().insertItem(item)
            count++
        }
        refreshItems()
        count
    }
}
