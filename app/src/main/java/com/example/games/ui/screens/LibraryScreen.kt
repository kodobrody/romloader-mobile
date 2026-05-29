package com.example.games.ui.screens

import com.example.games.ui.screens.platforms.PlatformsView
import com.example.games.ui.screens.games.GamesListView

import android.annotation.SuppressLint
import android.os.Parcel
import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.games.*
import com.example.games.data.*
import com.example.games.ui.components.GameCover
import com.example.games.util.cleanGameName
import com.example.games.util.formatFileSize
import com.example.games.util.parseGameInfo
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.util.Locale

@Parcelize
sealed class LibraryStage : Parcelable {
    @SuppressLint("ParcelCreator")
    object Platforms : LibraryStage(), Parcelable {
        override fun describeContents(): Int = 0

    }

    @Parcelize
    data class Games(val platformDisplayName: String, val platformId: String, val remotePath: String) : LibraryStage(), Parcelable {
        override fun describeContents(): Int = 0

    }

    @SuppressLint("ParcelCreator")
    @Parcelize
    data class Details(val platformDisplayName: String, val platformId: String, val remotePath: String, val gameName: String, val files: List<FtpFile>) : LibraryStage(), Parcelable {
        override fun describeContents(): Int = 0

    }

    @Parcelize
    data class ManualMatch(val platformDisplayName: String, val platformId: String, val remotePath: String, val gameName: String, val files: List<FtpFile>) : LibraryStage(), Parcelable {
        override fun describeContents(): Int = 0

    }
}

@Composable
fun LibraryScreen(
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    modifier: Modifier = Modifier,
    onNavigateToSearch: () -> Unit = {}
) {
    var stage by rememberSaveable { mutableStateOf<LibraryStage>(LibraryStage.Platforms) }
    val platformsGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val gamesGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var showDownloadedOnly by remember { mutableStateOf(false) }
    val cache by ftpRepository.fileCache.collectAsState()
    val downloadQueue by ftpRepository.downloadQueue.collectAsState()
    val config by ftpRepository.ftpConfigFlow.collectAsState(initial = null)
    val metadata by metadataRepository.metadataCache.collectAsState()
    
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val adaptiveInfo = androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()

    LaunchedEffect(refreshTrigger, config) {
        val currentConfig = config
        if (currentConfig != null && currentConfig.hasRemoteConnection) {
            isLoading = true
            error = null
            val result = ftpRepository.refreshLibrary(currentConfig)
            if (result.isFailure) {
                val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                if (cache.isNotEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Failed to refresh: $errorMessage. Showing cached library.",
                            duration = SnackbarDuration.Short
                        )
                    }
                } else {
                    error = errorMessage
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(config) {
        if (config != null) {
            metadataRepository.fetchPlatformLogos(config!!.twitchClientId, config!!.twitchClientSecret)
        }
    }

    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedGames by rememberSaveable { mutableStateOf<Set<SelectedGame>>(emptySet()) }

    val navigateBack = {
        when (stage) {
            is LibraryStage.Games -> stage = LibraryStage.Platforms
            is LibraryStage.Details -> {
                val s = stage as LibraryStage.Details
                stage = LibraryStage.Games(s.platformDisplayName, s.platformId, s.remotePath)
            }
            is LibraryStage.ManualMatch -> {
                val s = stage as LibraryStage.ManualMatch
                stage = LibraryStage.Details(s.platformDisplayName, s.platformId, s.remotePath, s.gameName, s.files)
            }
            else -> {}
        }
    }

    BackHandler(enabled = stage != LibraryStage.Platforms || isSelectionMode) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedGames = emptySet()
        } else {
            navigateBack()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            if (stage != LibraryStage.Platforms || isSelectionMode) {
                TextButton(
                    onClick = if (isSelectionMode) { { isSelectionMode = false; selectedGames = emptySet() } } else navigateBack
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        if (!isSelectionMode) {
                            val backLabel = when (val s = stage) {
                                is LibraryStage.Games -> "Platforms"
                                is LibraryStage.Details -> s.platformDisplayName
                                is LibraryStage.ManualMatch -> "Details"
                                else -> ""
                            }
                            if (backLabel.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Text(backLabel)
                            }
                        }
                    }
                }
            }
            Text(
                text = if (isSelectionMode) "${selectedGames.size} Selected" else when (val s = stage) {
                    is LibraryStage.Platforms -> "Platforms"
                    is LibraryStage.Games -> ""
                    is LibraryStage.Details -> ""
                    is LibraryStage.ManualMatch -> "Manual Match"
                    else -> ""
                },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            if (isSelectionMode) {
                TextButton(onClick = {
                    isSelectionMode = false
                    selectedGames = emptySet()
                }) {
                    Text("Cancel")
                }
            } else {
                if (isLoading && stage == LibraryStage.Platforms) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
                }
                if (stage == LibraryStage.Platforms || stage is LibraryStage.Games) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (showDownloadedOnly) "Show All" else "Show Downloaded Only") },
                                onClick = {
                                    showDownloadedOnly = !showDownloadedOnly
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (showDownloadedOnly) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                        contentDescription = null
                                    )
                                }
                            )
                            if (stage == LibraryStage.Platforms) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = {
                                        refreshTrigger++
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                    }
                                )
                            }
                            if (stage is LibraryStage.Games) {
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    onClick = {
                                        isSelectionMode = true
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Checklist, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val currentStage = stage) {
                is LibraryStage.Platforms -> {
                    PlatformsView(
                        config = config,
                        cache = cache,
                        ftpRepository = ftpRepository,
                        showDownloadedOnly = showDownloadedOnly,
                        adaptiveInfo = adaptiveInfo,
                        gridState = platformsGridState,
                        onToggleDownloadedOnly = { showDownloadedOnly = false },
                        onPlatformSelected = { platform, id, path ->
                            stage = LibraryStage.Games(platform.displayName, id, path)
                        }
                    )
                }
                is LibraryStage.Games -> {
                    val platform = PLATFORM_DEFINITIONS.find { it.displayName == currentStage.platformDisplayName }
                    if (platform != null) {
                        GamesListView(
                            platform = platform,
                            platformId = currentStage.platformId,
                            path = currentStage.remotePath,
                            cache = cache,
                            ftpRepository = ftpRepository,
                            metadataRepository = metadataRepository,
                            isSelectionMode = isSelectionMode,
                            selectedGames = selectedGames,
                            showDownloadedOnly = showDownloadedOnly,
                            adaptiveInfo = adaptiveInfo,
                            gridState = gamesGridState,
                            onToggleDownloadedOnly = { showDownloadedOnly = false },
                            onSelectionChanged = { selectedGames = it },
                            onSelectionModeStarted = { isSelectionMode = true },
                            onGameSelected = { name, files ->
                                stage = LibraryStage.Details(currentStage.platformDisplayName, currentStage.platformId, currentStage.remotePath, name, files)
                            }
                        )
                    }
                }
                is LibraryStage.Details -> {
                    val localPath = config?.localPath ?: ""
                    val isDownloaded = ftpRepository.isGameDownloaded(localPath, currentStage.platformId, currentStage.files)
                    val isInQueue = downloadQueue.any { it.gameName == currentStage.gameName && it.platformName == currentStage.platformId }
                    val gameMetadata = metadata["${currentStage.platformId}_${currentStage.gameName}"]

                    GameDetails(
                        gameName = currentStage.gameName,
                        platformName = currentStage.platformDisplayName,
                        metadata = gameMetadata,
                        files = currentStage.files,
                        isDownloaded = isDownloaded,
                        isInQueue = isInQueue,
                        downloadQueue = downloadQueue,
                        onDownload = {
                            ftpRepository.enqueueDownload(currentStage.platformId, currentStage.gameName, currentStage.files)
                        },
                        onRemove = {
                            scope.launch {
                                ftpRepository.removeDownload(currentStage.platformId, currentStage.gameName, currentStage.files)
                            }
                        },
                        onCancel = {
                            scope.launch {
                                ftpRepository.removeDownload(currentStage.platformId, currentStage.gameName, currentStage.files)
                            }
                        },
                        onManualMatch = {
                            stage = LibraryStage.ManualMatch(currentStage.platformDisplayName, currentStage.platformId, currentStage.remotePath, currentStage.gameName, currentStage.files)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is LibraryStage.ManualMatch -> {
                    ManualMatchScreen(
                        gameName = currentStage.gameName,
                        platformId = currentStage.platformId,
                        metadataRepository = metadataRepository,
                        ftpRepository = ftpRepository,
                        onMatchSelected = {
                            stage = LibraryStage.Details(currentStage.platformDisplayName, currentStage.platformId, currentStage.remotePath, currentStage.gameName, currentStage.files)
                        }
                    )
                }
            }

            if (error != null && cache.isEmpty()) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp).align(Alignment.Center)
                )
            }

            if (stage is LibraryStage.Platforms && !isSelectionMode) {
                ExtendedFloatingActionButton(
                    text = { Text("Search") },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    onClick = onNavigateToSearch,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (isSelectionMode && selectedGames.isNotEmpty()) {
            val localPath = config?.localPath ?: ""
            val allDownloaded = selectedGames.all { (platformId, _, _, files) ->
                ftpRepository.isGameDownloaded(localPath, platformId, files)
            }
            
            if (!allDownloaded) {
                Button(
                    onClick = {
                        selectedGames.forEach { (platformId, _, gameName, files) ->
                            ftpRepository.enqueueDownload(platformId, gameName, files)
                        }
                        isSelectionMode = false
                        selectedGames = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download Selected (${selectedGames.size})")
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            selectedGames.forEach { (platformId, _, gameName, files) ->
                                ftpRepository.removeDownload(platformId, gameName, files)
                            }
                            isSelectionMode = false
                            selectedGames = emptySet()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Selected (${selectedGames.size})")
                }
            }
        }
    }
}

data class SelectedGame(
    val platformId: String,
    val platformDisplayName: String,
    val gameName: String,
    val files: List<FtpFile>
)
