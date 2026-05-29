package com.example.games.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.games.FtpRepository
import com.example.games.PLATFORM_DEFINITIONS
import com.example.games.data.MetadataRepository
import com.example.games.ui.components.GameCover
import com.example.games.ui.components.MetadataFetchingPill
import com.example.games.util.cleanGameName
import com.example.games.util.formatFileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SearchScreen(
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    val cache by ftpRepository.fileCache.collectAsState()
    val downloadQueue by ftpRepository.downloadQueue.collectAsState()
    val config by ftpRepository.ftpConfigFlow.collectAsState(initial = null)
    val metadata by metadataRepository.metadataCache.collectAsState()
    val fetchingProgress by metadataRepository.fetchingProgress.collectAsState()
    val aggregateProgress = if (fetchingProgress.isEmpty()) null else fetchingProgress.values.average().toFloat()
    val localPath = config?.localPath ?: ""
    val scope = rememberCoroutineScope()
    val adaptiveInfo = androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()
    val searchGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query.length < 2) {
            debouncedQuery = query
            return@LaunchedEffect
        }
        delay(500)
        debouncedQuery = query
    }

    val searchResults = remember(debouncedQuery, cache) {
        if (debouncedQuery.length < 2) emptyList<LibraryStage.Details>()
        else {
            val results = mutableListOf<LibraryStage.Details>()
            val queryTokens = debouncedQuery.lowercase(Locale.ROOT).split(" ").filter { it.isNotEmpty() }
            
            cache.forEach { (path, files) ->
                val platformId = path.substringAfterLast("/").ifEmpty { path.substringBeforeLast("/").substringAfterLast("/") }
                val platform = PLATFORM_DEFINITIONS.find { p ->
                    p.aliases.any { alias -> alias.equals(platformId, ignoreCase = true) }
                }
                if (platform != null) {
                    val gamesInPath = files.filter { file ->
                        !file.isDirectory && platform.extensions.any { ext ->
                            file.name.lowercase(Locale.ROOT).endsWith(".$ext")
                        }
                    }.groupBy { it.name.substringBeforeLast(".") }

                    gamesInPath.forEach { (name, gameFiles) ->
                        val cleanName = cleanGameName(name).lowercase(Locale.ROOT)
                        val fullName = name.lowercase(Locale.ROOT)
                        val isMatch = queryTokens.all { token ->
                            fullName.contains(token) || cleanName.contains(token)
                        }
                        if (isMatch) {
                            results.add(LibraryStage.Details(platform.displayName, platformId, path, name, gameFiles))
                        }
                    }
                }
            }
            results.sortedBy { it.gameName }
        }
    }

    LaunchedEffect(searchResults, config) {
        if (config != null && searchResults.isNotEmpty()) {
            val groups = searchResults.groupBy { it.platformId }
            for ((pid, results) in groups) {
                metadataRepository.fetchMetadataForPlatform(
                    pid,
                    results.map { it.gameName },
                    config!!.twitchClientId,
                    config!!.twitchClientSecret
                )
            }
        }
    }

    // Memoize downloaded files across all platforms found in results
    val downloadedFilesByPlatform = remember(localPath, searchResults, downloadQueue) {
        searchResults.map { it.platformId }.distinct().associateWith { pid ->
            ftpRepository.getDownloadedFiles(localPath, pid)
        }
    }

    var selectedGame by remember { mutableStateOf<LibraryStage.Details?>(null) }
    var isManualMatching by remember { mutableStateOf(false) }

    if (selectedGame != null) {
        val current = selectedGame!!
        if (isManualMatching) {
            ManualMatchScreen(
                gameName = current.gameName,
                platformId = current.platformId,
                metadataRepository = metadataRepository,
                ftpRepository = ftpRepository,
                onMatchSelected = {
                    isManualMatching = false
                }
            )
        } else {
            val isDownloaded = ftpRepository.isGameDownloaded(localPath, current.platformId, current.files)
            val isInQueue = downloadQueue.any { it.gameName == current.gameName && it.platformName == current.platformId }
            val gameMetadata = metadata["${current.platformId}_${current.gameName}"]

            Column(modifier = modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = { 
                        selectedGame = null 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = gameMetadata?.igdbName ?: cleanGameName(current.gameName),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GameDetails(
                    gameName = current.gameName,
                    platformName = current.platformDisplayName,
                    metadata = gameMetadata,
                    files = current.files,
                    isDownloaded = isDownloaded,
                    isInQueue = isInQueue,
                    onDownload = {
                        ftpRepository.enqueueDownload(current.platformId, current.gameName, current.files)
                    },
                    onRemove = {
                        scope.launch {
                            ftpRepository.removeDownload(current.platformId, current.gameName, current.files)
                        }
                    },
                    onCancel = {
                        scope.launch {
                            ftpRepository.removeDownload(current.platformId, current.gameName, current.files)
                        }
                    },
                    onManualMatch = {
                        isManualMatching = true
                    },
                    downloadQueue = downloadQueue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        BackHandler { 
            if (isManualMatching) {
                isManualMatching = false
            } else {
                selectedGame = null
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Games") },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            if (aggregateProgress != null) {
                                MetadataFetchingPill(progress = aggregateProgress)
                            }
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            if (debouncedQuery.length >= 2 && searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results found")
                }
            } else {
                val columns = with(adaptiveInfo) {
                    when (windowSizeClass.windowWidthSizeClass) {
                        androidx.window.core.layout.WindowWidthSizeClass.EXPANDED -> 6
                        androidx.window.core.layout.WindowWidthSizeClass.MEDIUM -> 4
                        else -> 2
                    }
                }

                LazyVerticalGrid(
                    state = searchGridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.platformId + it.gameName }) { result ->
                        val isDownloaded = remember(downloadedFilesByPlatform, result) {
                            val platformFiles = downloadedFilesByPlatform[result.platformId] ?: emptySet()
                            result.files.all { platformFiles.contains(it.name) }
                        }
                        val gameMetadata = metadata["${result.platformId}_${result.gameName}"]
                        val totalSize = remember(result.files) { result.files.sumOf { it.size } }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CardDefaults.shape)
                                .clickable {
                                    selectedGame = result
                                }
                        ) {
                            Box {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)) {
                                        GameCover(
                                            metadata = gameMetadata,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.TopEnd) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                                shape = CircleShape
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = formatFileSize(totalSize),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    if (isDownloaded) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(12.dp),
                                                            tint = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .height(56.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            gameMetadata?.igdbName ?: cleanGameName(result.gameName),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            result.platformDisplayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                    ) {
                                        val downloadingTask = downloadQueue.find { it.gameName == result.gameName && it.platformName == result.platformId && it.status == com.example.games.data.DownloadStatus.DOWNLOADING }
                                        if (downloadingTask != null) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    "DOWNLOADING ${(downloadingTask.progress * 100).toInt()}%",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        } else if (isDownloaded) {
                                            Surface(
                                                color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    "DOWNLOADED",
                                                    color = androidx.compose.ui.graphics.Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        BackHandler {
            onBack()
        }
    }
}