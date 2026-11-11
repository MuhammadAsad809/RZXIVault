package com.rzxi.vault.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rzxi.vault.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isStrongBox: Boolean,
    onLockNow: () -> Unit,
    onExportBackup: suspend (CharArray) -> ByteArray,
    onImportBackup: suspend (ByteArray, CharArray) -> Int,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }

    // File picker launcher for importing backup
    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    pendingImportBytes = bytes
                    showImportDialog = true
                }
            } catch (e: Exception) {
                onMessage("Failed to read selected backup file: ${e.message}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Hardware Security & Backup",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Security Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SECURITY ATTRIBUTES",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                StatusRow(
                    label = "Key Store Provider",
                    value = if (isStrongBox) "Hardware StrongBox Keymaster" else "ARM TrustZone TEE",
                    isSecure = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Storage Cryptography",
                    value = "SQLCipher 256-bit AES + HMAC-SHA512",
                    isSecure = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Biometric Invalidation",
                    value = "Revoke on New Biometric Enrollment",
                    isSecure = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Network Leak Surface",
                    value = "Zero Internet Permission (Air-Gapped)",
                    isSecure = true
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "OFFLINE BACKUP & RESTORE",
            color = TextMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Export Button
        Button(
            onClick = { showExportDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = TextPrimary),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = NeonCyan)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Export Encrypted Backup (.ovc)", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Import Button
        Button(
            onClick = { importFilePicker.launch("*/*") },
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = TextPrimary),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = NeonGreen)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Import Encrypted Backup", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Lock Vault Button
        Button(
            onClick = onLockNow,
            colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.2f), contentColor = NeonRed),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("WIPE RAM & LOCK VAULT NOW", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }

    // Export Password Prompt Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportDialog = false
                backupPassword = ""
            },
            title = { Text("Set Backup Passphrase", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Your backup will be encrypted with PBKDF2-HMAC-SHA256 (100k rounds) + AES-256-GCM. Do not lose this passphrase.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Backup Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (backupPassword.isNotBlank()) {
                            val passChars = backupPassword.toCharArray()
                            backupPassword = ""
                            showExportDialog = false
                            coroutineScope.launch {
                                try {
                                    val backupBytes = onExportBackup(passChars)
                                    val fileName = "rzxi_vault_${System.currentTimeMillis()}.ovc"
                                    val file = java.io.File(context.cacheDir, fileName)
                                    file.writeBytes(backupBytes)

                                    val shareUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Save Vault Backup"))
                                    onMessage("Backup container created ($fileName)")
                                } catch (e: Exception) {
                                    onMessage("Export failed: ${e.message}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack)
                ) {
                    Text("Export", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    backupPassword = ""
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Import Password Prompt Dialog
    if (showImportDialog && pendingImportBytes != null) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                backupPassword = ""
                pendingImportBytes = null
            },
            title = { Text("Enter Backup Passphrase", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Enter the passphrase used when generating this encrypted backup container.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Backup Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bytes = pendingImportBytes
                        if (bytes != null && backupPassword.isNotBlank()) {
                            val passChars = backupPassword.toCharArray()
                            backupPassword = ""
                            showImportDialog = false
                            coroutineScope.launch {
                                try {
                                    val count = onImportBackup(bytes, passChars)
                                    onMessage("Successfully restored $count records into vault")
                                } catch (e: Exception) {
                                    onMessage("Restore failed: ${e.message}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberBlack)
                ) {
                    Text("Decrypt & Import", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    backupPassword = ""
                    pendingImportBytes = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun StatusRow(label: String, value: String, isSecure: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isSecure) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isSecure) NeonGreen else NeonOrange,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
