package com.rzxi.vault.data.model

data class VaultItem(
    val id: Long = 0,
    val uuid: String,
    val type: VaultType,
    val title: String,
    val username: String = "",
    val secret: String = "",      // Password string or Note content or Base64 file
    val website: String = "",
    val metadata: String = "",    // File size / extra tags / notes
    val updatedAt: Long = System.currentTimeMillis()
)
