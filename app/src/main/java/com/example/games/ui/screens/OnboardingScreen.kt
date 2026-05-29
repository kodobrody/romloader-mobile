package com.example.games.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.example.games.FtpConfig
import com.example.games.FtpRepository
import com.example.games.data.MetadataRepository
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    onComplete: () -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 2
    val scope = rememberCoroutineScope()
    val config by ftpRepository.ftpConfigFlow.collectAsState(initial = FtpConfig())
    val snackbarHostState = remember { SnackbarHostState() }
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isHorizontal = adaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isHorizontal) {
            HorizontalOnboarding(
                padding = padding,
                currentStep = currentStep,
                totalSteps = totalSteps,
                config = config,
                ftpRepository = ftpRepository,
                metadataRepository = metadataRepository,
                scope = scope,
                snackbarHostState = snackbarHostState,
                onNext = { 
                    if (currentStep < totalSteps) currentStep++ 
                    else {
                        scope.launch {
                            ftpRepository.setOnboardingCompleted(true)
                            onComplete()
                        }
                    }
                }
            )
        } else {
            VerticalOnboarding(
                padding = padding,
                currentStep = currentStep,
                totalSteps = totalSteps,
                config = config,
                ftpRepository = ftpRepository,
                metadataRepository = metadataRepository,
                scope = scope,
                snackbarHostState = snackbarHostState,
                onNext = { 
                    if (currentStep < totalSteps) currentStep++ 
                    else {
                        scope.launch {
                            ftpRepository.setOnboardingCompleted(true)
                            onComplete()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun VerticalOnboarding(
    padding: PaddingValues,
    currentStep: Int,
    totalSteps: Int,
    config: FtpConfig,
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (currentStep) {
                1 -> WelcomeStep()
                2 -> SettingsStep(
                    config = config,
                    ftpRepository = ftpRepository,
                    metadataRepository = metadataRepository,
                    scope = scope,
                    snackbarHostState = snackbarHostState
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        OnboardingControls(
            currentStep = currentStep,
            totalSteps = totalSteps,
            isComplete = currentStep == 1 || (config.hasRemoteConnection && config.localPath.isNotEmpty()),
            onNext = onNext,
            fullWidth = true
        )
    }
}

@Composable
fun HorizontalOnboarding(
    padding: PaddingValues,
    currentStep: Int,
    totalSteps: Int,
    config: FtpConfig,
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier
            .weight(1.5f)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
        ) {
            when (currentStep) {
                1 -> WelcomeStep()
                2 -> SettingsStep(
                    config = config,
                    ftpRepository = ftpRepository,
                    metadataRepository = metadataRepository,
                    scope = scope,
                    snackbarHostState = snackbarHostState
                )
            }
        }

        Spacer(Modifier.width(32.dp))

        // Right side: Controls
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OnboardingControls(
                currentStep = currentStep,
                totalSteps = totalSteps,
                isComplete = currentStep == 1 || (config.hasRemoteConnection && config.localPath.isNotEmpty()),
                onNext = onNext,
                fullWidth = false
            )
        }
    }
}

@Composable
fun OnboardingControls(
    currentStep: Int,
    totalSteps: Int,
    isComplete: Boolean,
    onNext: () -> Unit,
    fullWidth: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Dot Indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { index ->
                val isSelected = index + 1 == currentStep
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = if (fullWidth) Modifier.fillMaxWidth().height(56.dp) else Modifier.widthIn(min = 200.dp).height(56.dp),
            enabled = isComplete
        ) {
            Text(if (currentStep == totalSteps) "Get Started" else "Next")
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Dns,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            text = "Welcome to Romloader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "Connect to your remote game collection and manage your downloads with ease.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsStep(
    config: FtpConfig,
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    var showConnectionModal by remember { mutableStateOf(false) }
    var isEditingConnection by remember { mutableStateOf(false) }
    var showIgdbModal by remember { mutableStateOf(false) }
    var isEditingIgdb by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Setup your library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Configure your server and local storage to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(24.dp))

        Text("Remote File Service (Mandatory)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
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
                onDelete = {
                    scope.launch {
                        ftpRepository.saveFtpConfig(config.clearedRemoteConnection())
                    }
                }
            )
        }

        Spacer(Modifier.height(24.dp))

        LocalStorageSection(config, ftpRepository, scope, snackbarHostState)

        Spacer(Modifier.height(24.dp))

        Text("Metadata Service (Optional)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
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
                onDelete = {
                    scope.launch {
                        ftpRepository.saveFtpConfig(config.copy(twitchClientId = "", twitchClientSecret = ""))
                    }
                },
                icon = Icons.Default.Cloud
            )
        }
    }

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
                    snackbarHostState.showSnackbar("Connection successful")
                }
                result
            }
        )
    }

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
}
