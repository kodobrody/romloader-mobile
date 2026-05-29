package com.example.games.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.games.FtpConfig
import com.example.games.FtpRepository
import com.example.games.data.MetadataRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    modifier: Modifier = Modifier,
) {
    val config by ftpRepository.ftpConfigFlow.collectAsState(initial = FtpConfig())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showConnectionModal by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isEditingConnection by remember { mutableStateOf(false) }

    var showIgdbModal by remember { mutableStateOf(false) }
    var showIgdbDeleteConfirmation by remember { mutableStateOf(false) }
    var isEditingIgdb by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Service Connections", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            // File Service Connection Section
            if (!config.hasRemoteConnection) {
                AddServiceButton(label = "Add File Service Connection", onClick = {
                    isEditingConnection = false
                    showConnectionModal = true 
                })
            } else {
                ServiceTile(
                    title = config.hostname,
                    subtitle = "${config.serviceLabel} Connection",
                    onEdit = { 
                        isEditingConnection = true
                        showConnectionModal = true 
                    },
                    onDelete = { showDeleteConfirmation = true }
                )
            }

            Spacer(Modifier.height(8.dp))

            // IGDB Connection Section
            if (config.twitchClientId.isEmpty()) {
                AddServiceButton(label = "Add IGDB Connection", onClick = { 
                    isEditingIgdb = false
                    showIgdbModal = true 
                })
            } else {
                ServiceTile(
                    title = "IGDB (Twitch API)",
                    onEdit = { 
                        isEditingIgdb = true
                        showIgdbModal = true 
                    },
                    onDelete = { showIgdbDeleteConfirmation = true },
                    icon = Icons.Default.Cloud
                )
            }

            Spacer(Modifier.height(32.dp))
            Text("Global Settings", style = MaterialTheme.typography.titleLarge)
            
            Spacer(Modifier.height(16.dp))
            LocalStorageSection(config, ftpRepository, scope, snackbarHostState)
        }
    }

    // Connection Modal
    if (showConnectionModal) {
        ConnectionModal(
            initialConfig = if (isEditingConnection) config else FtpConfig(),
            onDismiss = { showConnectionModal = false },
            onSave = { newConfig ->
                val result = ftpRepository.testConnection(newConfig)
                if (result.isSuccess) {
                    ftpRepository.saveFtpConfig(config.copy(
                        fileServiceType = newConfig.fileServiceType,
                        protocol = newConfig.protocol,
                        hostname = newConfig.hostname,
                        port = newConfig.port,
                        path = newConfig.path,
                        username = newConfig.username,
                        password = newConfig.password,
                        apiToken = newConfig.apiToken
                    ))
                    showConnectionModal = false
                    snackbarHostState.showSnackbar("Connection saved successfully")
                }
                result
            }
        )
    }

    // IGDB Modal
    if (showIgdbModal) {
        IgdbModal(
            initialClientId = if (isEditingIgdb) config.twitchClientId else "",
            initialClientSecret = if (isEditingIgdb) config.twitchClientSecret else "",
            onDismiss = { showIgdbModal = false },
            onSave = { clientId, clientSecret ->
                scope.launch {
                    val result = metadataRepository.testIgdbConnection(clientId, clientSecret)
                    if (result.isSuccess) {
                        ftpRepository.saveFtpConfig(config.copy(
                            twitchClientId = clientId,
                            twitchClientSecret = clientSecret
                        ))
                        showIgdbModal = false
                        snackbarHostState.showSnackbar("IGDB Settings saved successfully")
                    } else {
                        snackbarHostState.showSnackbar("IGDB Connection failed")
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialogs
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Connection") },
            text = { Text("Are you sure you want to delete this connection? This will clear the remote library cache.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        ftpRepository.saveFtpConfig(config.clearedRemoteConnection())
                        showDeleteConfirmation = false
                        snackbarHostState.showSnackbar("Connection deleted")
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIgdbDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showIgdbDeleteConfirmation = false },
            title = { Text("Delete IGDB Connection") },
            text = { Text("Are you sure you want to delete your IGDB credentials?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        ftpRepository.saveFtpConfig(config.copy(
                            twitchClientId = "",
                            twitchClientSecret = ""
                        ))
                        showIgdbDeleteConfirmation = false
                        snackbarHostState.showSnackbar("IGDB Credentials deleted")
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIgdbDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
