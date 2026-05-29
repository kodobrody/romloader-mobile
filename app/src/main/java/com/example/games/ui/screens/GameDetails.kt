package com.example.games.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.games.data.*
import com.example.games.ui.components.GameCover
import com.example.games.util.cleanGameName
import com.example.games.util.formatFileSize
import com.example.games.util.parseGameInfo

@Composable
fun GameDetails(
    gameName: String,
    platformName: String,
    metadata: GameMetadata?,
    files: List<FtpFile>,
    isDownloaded: Boolean,
    isInQueue: Boolean,
    downloadQueue: List<DownloadTask>,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit = {},
    onManualMatch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 88.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    GameCover(metadata = metadata, modifier = Modifier.width(160.dp).height(212.dp))
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onManualMatch != null) {
                            Surface(
                                onClick = onManualMatch,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Manual Match", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        val info = remember(gameName) { parseGameInfo(gameName) }
                        val totalSize = remember(files) { files.sumOf { it.size } }
                        val badgeColor = MaterialTheme.colorScheme.secondaryContainer
                        
                        BadgeSurface(text = "Size: ${formatFileSize(totalSize)}", color = badgeColor)
                        if (info.regions.isNotEmpty()) {
                            BadgeSurface(text = "Region: ${info.regions.joinToString(", ")}", color = badgeColor)
                        }
                        if (info.languages.isNotEmpty()) {
                            BadgeSurface(text = "Languages: ${info.languages.joinToString(", ")}", color = badgeColor)
                        }
                        info.version?.let { 
                            BadgeSurface(text = "Version: $it", color = badgeColor)
                        }
                        info.disc?.let { 
                            BadgeSurface(text = "Disc: $it", color = badgeColor)
                        }
                        if (info.isKiosk) BadgeSurface(text = "Kiosk", color = badgeColor)
                        if (info.isDemo) BadgeSurface(text = "Demo", color = badgeColor)
                        if (info.isBeta) BadgeSurface(text = "Beta", color = badgeColor)
                        if (info.isSample) BadgeSurface(text = "Sample", color = badgeColor)
                        if (info.isPrototype) BadgeSurface(text = "Prototype", color = badgeColor)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    text = metadata?.igdbName ?: cleanGameName(gameName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(12.dp))
                
                Text("Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isInQueue) {
                    val task = downloadQueue.find { it.gameName == gameName && it.files.firstOrNull()?.fullPath == files.firstOrNull()?.fullPath }
                    if (task != null) {
                        Spacer(Modifier.height(8.dp))
                        if (task.status == DownloadStatus.DOWNLOADING) {
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            val totalSize = task.files.sumOf { it.size }
                            val downloadedBytes = (task.progress * totalSize).toLong()
                            Text(
                                "Downloading... ${formatFileSize(downloadedBytes)} / ${formatFileSize(totalSize)} (${(task.progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else if (task.status == DownloadStatus.QUEUED) {
                            Text(
                                "Waiting in queue...",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            items(files) { file ->
                ListItem(
                    headlineContent = { 
                        Text(
                            text = file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    supportingContent = { 
                        Text(
                            text = file.fullPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, 
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    trailingContent = {
                        Text(
                            text = formatFileSize(file.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        val activeTask = remember(downloadQueue, gameName) {
            downloadQueue.find { 
                it.gameName == gameName && 
                (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING) 
            }
        }
        val isActivelyDownloading = activeTask != null

        ExtendedFloatingActionButton(
            onClick = { 
                if (isActivelyDownloading) onCancel()
                else if (isDownloaded) onRemove() 
                else onDownload() 
            },
            icon = {
                if (isActivelyDownloading) {
                    Icon(Icons.Default.Close, contentDescription = null)
                } else if (isDownloaded) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                }
            },
            text = {
                Text(
                    when {
                        isActivelyDownloading -> "Cancel Download"
                        isDownloaded -> "Remove Download"
                        else -> "Download"
                    }
                )
            },
            containerColor = when {
                isActivelyDownloading -> MaterialTheme.colorScheme.secondaryContainer
                isDownloaded -> MaterialTheme.colorScheme.errorContainer
                else -> Color(0xFF2E7D32)
            },
            contentColor = when {
                isActivelyDownloading -> MaterialTheme.colorScheme.onSecondaryContainer
                isDownloaded -> MaterialTheme.colorScheme.onErrorContainer
                else -> Color.White
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        )
    }
}

@Composable
fun BadgeSurface(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color,
        shape = CircleShape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
