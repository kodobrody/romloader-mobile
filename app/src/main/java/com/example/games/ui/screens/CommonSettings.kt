package com.example.games.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.games.ConnectionProtocol
import com.example.games.FileServiceType
import androidx.window.core.layout.WindowWidthSizeClass
import com.example.games.FtpConfig
import com.example.games.FtpRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberRevealLastCharTransformation(text: String): VisualTransformation {
    var revealLast by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(text) {
        revealLast = true
        scope.launch {
            delay(500)
            revealLast = false
        }
    }
    return remember(text, revealLast) {
        VisualTransformation { annotated ->
            val mask = '\u2022'
            val transformed = buildString {
                annotated.text.forEachIndexed { i, _ ->
                    if (revealLast && i == annotated.text.lastIndex) append(annotated.text[i])
                    else append(mask)
                }
            }
            TransformedText(
                androidx.compose.ui.text.AnnotatedString(transformed),
                androidx.compose.ui.text.input.OffsetMapping.Identity
            )
        }
    }
}

@Composable
fun AddServiceButton(label: String, onClick: () -> Unit) {
    val stroke = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
    val color = MaterialTheme.colorScheme.outline
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .drawBehind {
                drawRoundRect(
                    color = color,
                    style = stroke,
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, tint = color)
            Spacer(Modifier.width(8.dp))
            Text(label, color = color)
        }
    }
}

@Composable
fun ServiceTile(
    title: String,
    subtitle: String? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Dns
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionModal(
    initialConfig: FtpConfig,
    onDismiss: () -> Unit,
    onSave: suspend (FtpConfig) -> Result<Unit>
) {
    var serviceType by remember { mutableStateOf(initialConfig.fileServiceType.name) }
    var protocol by remember { mutableStateOf(initialConfig.protocol.name) }
    var hostname by remember { mutableStateOf(initialConfig.hostname) }
    var port by remember { mutableStateOf(initialConfig.port) }
    var path by remember { mutableStateOf(initialConfig.path) }
    var username by remember { mutableStateOf(initialConfig.username) }
    var password by remember { mutableStateOf(initialConfig.password) }
    var apiToken by remember { mutableStateOf(initialConfig.apiToken) }

    var expanded by remember { mutableStateOf(false) }
    var protocolExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val passwordTransformation = rememberRevealLastCharTransformation(password)
    val apiTokenTransformation = rememberRevealLastCharTransformation(apiToken)
    val isRomm = serviceType == FileServiceType.ROMM.name
    val isProtocolVisible = isRomm || serviceType == FileServiceType.NEXTCLOUD.name
    
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isHorizontal = adaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Connection Details",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isHorizontal) {
                        // Row 1: Service Type and Hostname
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f)) {
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded }
                                ) {
                                    OutlinedTextField(
                                        value = serviceType,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Service Type") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                                            .fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("FTP") },
                                            onClick = {
                                                serviceType = "FTP"
                                                if (port.isBlank() || port == FileServiceType.SFTP.defaultPort || port == FileServiceType.NEXTCLOUD.defaultPort || port == FileServiceType.ROMM.defaultPort) {
                                                    port = FileServiceType.FTP.defaultPort
                                                }
                                                expanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("SFTP") },
                                            onClick = {
                                                serviceType = "SFTP"
                                                if (port.isBlank() || port == FileServiceType.FTP.defaultPort || port == FileServiceType.NEXTCLOUD.defaultPort || port == FileServiceType.ROMM.defaultPort) {
                                                    port = FileServiceType.SFTP.defaultPort
                                                }
                                                expanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Nextcloud") },
                                            onClick = {
                                                serviceType = "NEXTCLOUD"
                                                if (port.isBlank() || port == FileServiceType.FTP.defaultPort || port == FileServiceType.SFTP.defaultPort || port == FileServiceType.ROMM.defaultPort) {
                                                    port = FileServiceType.NEXTCLOUD.defaultPort
                                                }
                                                expanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("RomM") },
                                            onClick = {
                                                serviceType = "ROMM"
                                                if (port.isBlank() || port == FileServiceType.FTP.defaultPort || port == FileServiceType.SFTP.defaultPort || port == FileServiceType.NEXTCLOUD.defaultPort) {
                                                    port = FileServiceType.ROMM.defaultPort
                                                }
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = hostname,
                                onValueChange = { hostname = it },
                                label = { Text("Hostname") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))

                        // Row 2: Protocol and Port/Path
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (isProtocolVisible) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ExposedDropdownMenuBox(
                                        expanded = protocolExpanded,
                                        onExpandedChange = { protocolExpanded = !protocolExpanded }
                                    ) {
                                        OutlinedTextField(
                                            value = protocol,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Protocol") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                                            modifier = Modifier
                                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                                                .fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = protocolExpanded,
                                            onDismissRequest = { protocolExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("HTTP") },
                                                onClick = {
                                                    protocol = ConnectionProtocol.HTTP.name
                                                    protocolExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("HTTPS") },
                                                onClick = {
                                                    protocol = ConnectionProtocol.HTTPS.name
                                                    protocolExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!isRomm) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = path,
                                onValueChange = { path = it },
                                label = { Text("Remote Path") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))

                        // Row 3: Credentials
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = if (isRomm) apiToken else username,
                                onValueChange = {
                                    if (isRomm) apiToken = it else username = it
                                },
                                label = { Text(if (isRomm) "API Token" else "Username") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            if (isRomm) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = Modifier.weight(1f),
                                    visualTransformation = passwordTransformation
                                )
                            }
                        }
                    } else {
                        // Vertical layout for compact screens
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = serviceType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Service Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("FTP") },
                                    onClick = {
                                        serviceType = "FTP"
                                        if (port.isBlank() || port == FileServiceType.SFTP.defaultPort || port == FileServiceType.NEXTCLOUD.defaultPort || port == FileServiceType.ROMM.defaultPort) {
                                            port = FileServiceType.FTP.defaultPort
                                        }
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("SFTP") },
                                    onClick = {
                                        serviceType = "SFTP"
                                        if (port.isBlank() || port == FileServiceType.FTP.defaultPort || port == FileServiceType.NEXTCLOUD.defaultPort || port == FileServiceType.ROMM.defaultPort) {
                                            port = FileServiceType.SFTP.defaultPort
                                        }
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nextcloud") },
                                    onClick = {
                                        serviceType = "NEXTCLOUD"
                                        if (port.isBlank() || port == FileServiceType.FTP.defaultPort || port == FileServiceType.SFTP.defaultPort || port == FileServiceType.ROMM.defaultPort) {
                                            port = FileServiceType.NEXTCLOUD.defaultPort
                                        }
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("RomM") },
                                    onClick = {
                                        serviceType = "ROMM"
                                        if (port.isBlank() || port == FileServiceType.FTP.defaultPort || port == FileServiceType.SFTP.defaultPort || port == FileServiceType.NEXTCLOUD.defaultPort) {
                                            port = FileServiceType.ROMM.defaultPort
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (isProtocolVisible) {
                            ExposedDropdownMenuBox(
                                expanded = protocolExpanded,
                                onExpandedChange = { protocolExpanded = !protocolExpanded }
                            ) {
                                OutlinedTextField(
                                    value = protocol,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Protocol") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = protocolExpanded,
                                    onDismissRequest = { protocolExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("HTTP") },
                                        onClick = {
                                            protocol = ConnectionProtocol.HTTP.name
                                            protocolExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("HTTPS") },
                                        onClick = {
                                            protocol = ConnectionProtocol.HTTPS.name
                                            protocolExpanded = false
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                        }

                        OutlinedTextField(value = hostname, onValueChange = { hostname = it }, label = { Text("Hostname") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), modifier = Modifier.fillMaxWidth())
                        if (!isRomm) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Remote Path") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = if (isRomm) apiToken else username,
                            onValueChange = {
                                if (isRomm) apiToken = it else username = it
                            },
                            label = { Text(if (isRomm) "API Token" else "Username") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = if (isRomm) ImeAction.Done else ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                onDone = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (isRomm) apiTokenTransformation else VisualTransformation.None
                        )
                        if (!isRomm) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), modifier = Modifier.fillMaxWidth(), visualTransformation = passwordTransformation)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isLoading) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val selectedType = FileServiceType.valueOf(serviceType)
                            val config = FtpConfig(
                                fileServiceType = selectedType,
                                protocol = ConnectionProtocol.valueOf(protocol),
                                hostname = hostname,
                                port = port.ifBlank { selectedType.defaultPort },
                                path = if (selectedType == FileServiceType.ROMM) "/" else path,
                                username = if (selectedType == FileServiceType.ROMM) "" else username,
                                password = if (selectedType == FileServiceType.ROMM) "" else password,
                                apiToken = if (selectedType == FileServiceType.ROMM) apiToken else ""
                            )
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                val result = onSave(config)
                                isLoading = false
                                if (result.isFailure) {
                                    errorMessage = result.exceptionOrNull()?.message ?: "Connection failed"
                                }
                            }
                        },
                        enabled = hostname.isNotEmpty() && (!isRomm && true || apiToken.isNotEmpty()) && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Save & Connect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IgdbModal(
    initialClientId: String,
    initialClientSecret: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var clientId by remember { mutableStateOf(initialClientId) }
    var clientSecret by remember { mutableStateOf(initialClientSecret) }
    val clientSecretTransformation = rememberRevealLastCharTransformation(clientSecret)
    
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isHorizontal = adaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "IGDB (Twitch API) Details",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isHorizontal) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = { Text("Client ID") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = clientSecret,
                                onValueChange = { clientSecret = it },
                                label = { Text("Client Secret") },
                                modifier = Modifier.weight(1f),
                                visualTransformation = clientSecretTransformation
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Client ID") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = clientSecret,
                            onValueChange = { clientSecret = it },
                            label = { Text("Client Secret") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = clientSecretTransformation
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(clientId, clientSecret) },
                        enabled = clientId.isNotEmpty() && clientSecret.isNotEmpty()
                    ) {
                        Text("Save & Connect")
                    }
                }
            }
        }
    }
}

@Composable
fun LocalStorageSection(
    config: FtpConfig,
    ftpRepository: FtpRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    var localPath by remember(config) { mutableStateOf(config.localPath) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            localPath = uri.toString()
            scope.launch {
                ftpRepository.saveFtpConfig(config.copy(localPath = localPath))
            }
        }
    }

    Column {
        Text("Local Storage", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = localPath,
            onValueChange = { },
            label = { Text("Local Games Path") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { launcher.launch(null) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Select Folder")
                }
            },
            readOnly = true
        )

        Button(
            onClick = {
                scope.launch {
                    val result = ftpRepository.testLocalPath(localPath)
                    snackbarHostState.showSnackbar(if (result.isSuccess) "Local Folder Access Successful!" else "Local Folder Access Failed")
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Test Local Folder")
        }
    }
}
