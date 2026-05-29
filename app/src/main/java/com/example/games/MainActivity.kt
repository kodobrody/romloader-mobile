package com.example.games

import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.games.FtpRepository
import com.example.games.data.MetadataRepository
import com.example.games.ui.screens.*
import com.example.games.ui.theme.GamesTheme

class MainActivity : ComponentActivity() {
    private val ftpRepository: FtpRepository get() = (application as GamesApplication).ftpRepository
    private val metadataRepository: MetadataRepository get() = (application as GamesApplication).metadataRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            GamesTheme {
                MainContent(ftpRepository, metadataRepository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Suppress("DEPRECATION")
@Composable
fun MainContent(ftpRepository: FtpRepository, metadataRepository: MetadataRepository) {
    val ftpConfig by ftpRepository.ftpConfigFlow.collectAsState(initial = null)
    
    if (ftpConfig == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!ftpConfig!!.isOnboardingCompleted) {
        OnboardingScreen(
            ftpRepository = ftpRepository,
            metadataRepository = metadataRepository,
            onComplete = { /* Flow will update via ftpConfigFlow */ }
        )
        return
    }

    var currentDestination by remember { mutableStateOf("library") }
    var isConnectionValid by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(ftpConfig) {
        if (ftpConfig != null && ftpConfig!!.hasRemoteConnection) {
            isConnectionValid = ftpRepository.testConnection(ftpConfig!!).isSuccess
        } else {
            isConnectionValid = false
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val customNavSuiteType = with(adaptiveInfo) {
        if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
            NavigationSuiteType.NavigationDrawer
        } else if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM ||
            windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteType.NavigationBar
        }
    }

    val destinations = listOf(
        Triple("library", "Library", Icons.AutoMirrored.Filled.LibraryBooks),
        Triple("downloads", "Downloads", Icons.Default.Download),
        Triple("settings", "Settings", Icons.Default.Settings)
    )

    NavigationSuiteScaffoldLayout(
        navigationSuite = {
            when (customNavSuiteType) {
                NavigationSuiteType.NavigationBar -> {
                    NavigationBar {
                        destinations.forEach { (id, label, icon) ->
                            NavigationBarItem(
                                selected = currentDestination == id,
                                onClick = { currentDestination = id },
                                icon = { NavIcon(id, icon, label, ftpRepository) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
                NavigationSuiteType.NavigationRail -> {
                    NavigationRail(
                        header = { Spacer(Modifier.weight(1f)) },
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        destinations.forEach { (id, label, icon) ->
                            NavigationRailItem(
                                selected = currentDestination == id,
                                onClick = { currentDestination = id },
                                icon = { NavIcon(id, icon, label, ftpRepository) },
                                label = { 
                                    Text(
                                        label, 
                                        maxLines = 1, 
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    ) 
                                },
                                alwaysShowLabel = true
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
                NavigationSuiteType.NavigationDrawer -> {
                    PermanentDrawerSheet(modifier = Modifier.width(240.dp)) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            destinations.forEach { (id, label, icon) ->
                                NavigationDrawerItem(
                                    selected = currentDestination == id,
                                    onClick = { currentDestination = id },
                                    icon = { NavIcon(id, icon, label, ftpRepository) },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                }
            }
        },
        layoutType = customNavSuiteType
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                when (currentDestination) {
                    "library" -> {
                        if (isConnectionValid == false) {
                            ConnectionErrorScreen(onNavigateToSettings = { currentDestination = "settings" })
                        } else if (isConnectionValid == true) {
                            LibraryScreen(
                                ftpRepository, 
                                metadataRepository, 
                                Modifier.fillMaxSize(),
                                onNavigateToSearch = { currentDestination = "search" }
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    "search" -> {
                        if (isConnectionValid == false) {
                            ConnectionErrorScreen(onNavigateToSettings = { currentDestination = "settings" })
                        } else {
                            SearchScreen(
                                ftpRepository, 
                                metadataRepository, 
                                Modifier.fillMaxSize(),
                                onBack = { currentDestination = "library" }
                            )
                        }
                    }
                    "downloads" -> DownloadsScreen(ftpRepository, metadataRepository, Modifier.fillMaxSize())
                    "settings" -> SettingsScreen(ftpRepository, metadataRepository, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun NavIcon(id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ftpRepository: FtpRepository) {
    if (id == "downloads") {
        BadgedBox(badge = {
            val queue by ftpRepository.downloadQueue.collectAsState()
            val activeCount = queue.count { it.status == com.example.games.data.DownloadStatus.QUEUED || it.status == com.example.games.data.DownloadStatus.DOWNLOADING }
            if (activeCount > 0) {
                Badge { Text(activeCount.toString()) }
            }
        }) {
            Icon(icon, contentDescription = label)
        }
    } else {
        Icon(icon, contentDescription = label)
    }
}

@Composable
fun ConnectionErrorScreen(onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Invalid connection.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Go to settings to update connection credentials",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateToSettings) {
            Text("Go to Settings")
        }
    }
}
