package com.example.games.ui.screens.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.example.games.PlatformDefinition
import com.example.games.FtpRepository
import com.example.games.data.FtpFile
import com.example.games.data.MetadataRepository
import com.example.games.data.DownloadStatus
import com.example.games.ui.screens.SelectedGame
import com.example.games.ui.components.GameCover
import com.example.games.ui.components.MetadataFetchingPill
import com.example.games.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesListView(
    platform: PlatformDefinition,
    platformId: String,
    path: String,
    cache: Map<String, List<FtpFile>>,
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    isSelectionMode: Boolean,
    selectedGames: Set<SelectedGame>,
    showDownloadedOnly: Boolean,
    adaptiveInfo: androidx.compose.material3.adaptive.WindowAdaptiveInfo,
    gridState: LazyGridState = rememberLazyGridState(),
    onToggleDownloadedOnly: () -> Unit = {},
    onSelectionChanged: (Set<SelectedGame>) -> Unit,
    onSelectionModeStarted: () -> Unit,
    onGameSelected: (String, List<FtpFile>) -> Unit
) {
    val groupedGames = remember(cache, path, platform, showDownloadedOnly) {
        val files = cache[path] ?: emptyList()
        // Grouping logic remains the same
        files.filter { file ->
            platform.extensions.any { ext ->
                file.name.lowercase(java.util.Locale.ROOT).endsWith(".$ext")
            }
        }
        .groupBy { file ->
            file.name.substringBeforeLast(".")
        }.toList().sortedBy { it.first }
    }

    val config by ftpRepository.ftpConfigFlow.collectAsState(initial = null)
    val downloadQueue by ftpRepository.downloadQueue.collectAsState()
    val localPath = config?.localPath ?: ""
    val metadata by metadataRepository.metadataCache.collectAsState()
    val fetchingProgress by metadataRepository.fetchingProgress.collectAsState()
    val platformProgress = fetchingProgress[platformId]

    LaunchedEffect(groupedGames, config) {
        if (config != null && groupedGames.isNotEmpty()) {
            metadataRepository.fetchMetadataForPlatform(
                platformId,
                groupedGames.map { it.first },
                config!!.twitchClientId,
                config!!.twitchClientSecret
            )
        }
    }

    val downloadedFiles = remember(localPath, platformId, cache, downloadQueue) {
        ftpRepository.getDownloadedFiles(localPath, platformId)
    }

    // Display filtering logic remains the same
    val displayGames = remember(groupedGames, selectedGames, downloadedFiles, showDownloadedOnly) {
        val base = if (selectedGames.isNotEmpty()) {
            val firstSelected = selectedGames.first()
            val firstIsDownloaded = firstSelected.files.all {
                ftpRepository.isFileDownloaded(localPath, firstSelected.platformId, it.name)
            }

            groupedGames.filter { (_, files) ->
                val gameIsDownloaded = files.all { downloadedFiles.contains(it.name) }
                gameIsDownloaded == firstIsDownloaded
            }
        } else {
            groupedGames
        }

        if (showDownloadedOnly) {
            base.filter { (_, files) ->
                files.all { downloadedFiles.contains(it.name) }
            }
        } else {
            base
        }
    }

    if (displayGames.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (showDownloadedOnly) "No downloads yet" else "No games found")
                if (showDownloadedOnly) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onToggleDownloadedOnly) {
                        Text("Show all games")
                    }
                }
            }
        }
    } else {
        // Using adaptiveInfo directly to fix the compile error
        val columns = when {
            adaptiveInfo.windowSizeClass.windowWidthSizeClass == androidx.window.core.layout.WindowWidthSizeClass.EXPANDED -> 6
            adaptiveInfo.windowSizeClass.windowWidthSizeClass == androidx.window.core.layout.WindowWidthSizeClass.MEDIUM -> 4
            else -> 2
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = platform.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (platformProgress != null) {
                        Spacer(Modifier.width(12.dp))
                        MetadataFetchingPill(progress = platformProgress)
                    }
                }
            }
            items(displayGames, key = { it.first }) { (name, files) ->
                val isDownloaded = remember(downloadedFiles, files) {
                    files.all { downloadedFiles.contains(it.name) }
                }
                val isSelected = selectedGames.any { it.gameName == name && it.platformId == platformId }
                val gameMetadata = metadata["${platformId}_$name"]
                val totalSize = remember(files) { files.sumOf { it.size } }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardDefaults.shape)
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) {
                                    val newSelection = if (!isSelected) {
                                        selectedGames + SelectedGame(platformId, platform.displayName, name, files)
                                    } else {
                                        selectedGames.filterNot { it.gameName == name && it.platformId == platformId }.toSet()
                                    }
                                    onSelectionChanged(newSelection)
                                } else {
                                    onGameSelected(name, files)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    onSelectionModeStarted()
                                    onSelectionChanged(setOf(SelectedGame(platformId, platform.displayName, name, files)))
                                }
                            }
                        ),
                    colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                ) {
                    Box {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)) {
                                GameCover(
                                    metadata = gameMetadata,
                                    modifier = Modifier.fillMaxSize(),
                                    roundedBottom = false
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
                                    gameMetadata?.igdbName ?: cleanGameName(name),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            ) {
                                val downloadingTask = downloadQueue.find { it.gameName == name && it.platformName == platformId && it.status == DownloadStatus.DOWNLOADING }
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
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                } else if (isDownloaded) {
                                    Surface(
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            "DOWNLOADED",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        if (isSelectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
