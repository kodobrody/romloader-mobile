package com.example.games.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.games.FtpRepository
import com.example.games.data.DownloadStatus
import com.example.games.data.MetadataRepository
import com.example.games.getPlatformDisplayName
import com.example.games.ui.components.GameCover
import com.example.games.util.cleanGameName

@Composable
fun DownloadsScreen(
    ftpRepository: FtpRepository,
    metadataRepository: MetadataRepository,
    modifier: Modifier = Modifier
) {
    val downloadQueue by ftpRepository.downloadQueue.collectAsState()
    val metadata by metadataRepository.metadataCache.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            if (downloadQueue.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }) {
                TextButton(onClick = { ftpRepository.clearDownloads() }) {
                    Text("Clear Finished")
                }
            }
        }
        
        if (downloadQueue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active downloads")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(downloadQueue) { task ->
                    val gameMetadata = metadata["${task.platformName}_${task.gameName}"]
                    ListItem(
                        headlineContent = { Text(gameMetadata?.igdbName ?: cleanGameName(task.gameName)) },
                        supportingContent = {
                            Column {
                                Text("${getPlatformDisplayName(task.platformName)} • ${task.status}")
                                if (task.status == DownloadStatus.DOWNLOADING) {
                                    val totalSize = task.files.sumOf { it.size }
                                    val downloadedBytes = (task.progress * totalSize).toLong()
                                    
                                    LinearProgressIndicator(
                                        progress = { task.progress },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    )
                                    Text(
                                        "${com.example.games.util.formatFileSize(downloadedBytes)} / ${com.example.games.util.formatFileSize(totalSize)} (${(task.progress * 100).toInt()}%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            GameCover(metadata = gameMetadata, modifier = Modifier.size(50.dp))
                        },
                        trailingContent = {
                            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                                IconButton(onClick = { ftpRepository.cancelDownload(task.platformName, task.gameName) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                }
                            } else {
                                Icon(
                                    imageVector = when (task.status) {
                                        DownloadStatus.QUEUED -> Icons.Default.Schedule
                                        DownloadStatus.DOWNLOADING -> Icons.Default.Refresh
                                        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                                        DownloadStatus.FAILED -> Icons.Default.Error
                                    },
                                    contentDescription = null,
                                    tint = when (task.status) {
                                        DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
