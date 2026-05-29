package com.example.games.ui.screens.platforms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.games.FtpConfig
import com.example.games.FtpRepository
import com.example.games.PLATFORM_DEFINITIONS
import com.example.games.PlatformDefinition
import com.example.games.data.FtpFile
import com.example.games.util.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.draw.clip
import java.util.Locale

@Composable
fun PlatformsView(
    config: FtpConfig?,
    cache: Map<String, List<FtpFile>>,
    ftpRepository: FtpRepository,
    showDownloadedOnly: Boolean,
    adaptiveInfo: androidx.compose.material3.adaptive.WindowAdaptiveInfo,
    gridState: LazyGridState = rememberLazyGridState(),
    onToggleDownloadedOnly: () -> Unit = {},
    onPlatformSelected: (PlatformDefinition, String, String) -> Unit
) {
    if (config == null) return

    val localPath = config.localPath
    val downloadQueue by ftpRepository.downloadQueue.collectAsState()

    val platformsFound = remember(cache, config.path, showDownloadedOnly, localPath, downloadQueue) {
        val rootFiles = cache[config.path] ?: emptyList()
        rootFiles.filter { it.isDirectory }.mapNotNull { dir ->
            val platform = PLATFORM_DEFINITIONS.find { p ->
                p.aliases.any { it.equals(dir.name, ignoreCase = true) }
            }
            if (platform != null && cache.containsKey(dir.fullPath)) {
                if (showDownloadedOnly) {
                    val downloadedFiles = ftpRepository.getDownloadedFiles(localPath, dir.name)
                    if (downloadedFiles.isEmpty()) return@mapNotNull null
                }
                Triple(platform, dir.name, dir.fullPath)
            } else null
        }.sortedBy { it.first.displayName }
    }

    if (platformsFound.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (showDownloadedOnly) "No downloads yet" else "No platforms found")
                if (showDownloadedOnly) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onToggleDownloadedOnly) {
                        Text("Show all platforms")
                    }
                }
            }
        }
    } else {
        val columns = with(adaptiveInfo) {
            val baseColumns = when (windowSizeClass.windowWidthSizeClass) {
                androidx.window.core.layout.WindowWidthSizeClass.EXPANDED -> 6
                androidx.window.core.layout.WindowWidthSizeClass.MEDIUM -> 4
                else -> 2
            }
            if (windowSizeClass.windowWidthSizeClass == androidx.window.core.layout.WindowWidthSizeClass.COMPACT && 
                windowSizeClass.windowHeightSizeClass == androidx.window.core.layout.WindowHeightSizeClass.COMPACT) {
                1
            } else {
                baseColumns
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(platformsFound) { (platform, id, path) ->
                val gameCount = remember(cache, path, platform) {
                    val platformFiles = cache[path] ?: emptyList()
                    val games = platformFiles.filter { file ->
                        !file.isDirectory && platform.extensions.any { ext ->
                            file.name.lowercase(Locale.ROOT).endsWith(".$ext")
                        }
                    }
                    if (games.isNotEmpty()) {
                        games.distinctBy { it.name.substringBeforeLast(".") }.size
                    } else {
                        platformFiles.count { it.isDirectory }
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardDefaults.shape)
                        .clickable { onPlatformSelected(platform, id, path) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.height(48.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                platform.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "$gameCount games",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
