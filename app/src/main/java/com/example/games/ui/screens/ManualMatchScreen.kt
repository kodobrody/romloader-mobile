package com.example.games.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.games.FtpRepository
import com.example.games.PLATFORM_DEFINITIONS
import com.example.games.data.GameMetadata
import com.example.games.data.MetadataRepository
import com.example.games.ui.components.GameCover
import com.example.games.util.cleanGameName
import kotlinx.coroutines.launch

@Composable
fun ManualMatchScreen(
    gameName: String,
    platformId: String,
    metadataRepository: MetadataRepository,
    ftpRepository: FtpRepository,
    onMatchSelected: () -> Unit
) {
    var query by remember { mutableStateOf(cleanGameName(gameName)) }
    var results by remember { mutableStateOf<List<GameMetadata>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val config by ftpRepository.ftpConfigFlow.collectAsState(initial = null)

    val platformDef = remember(platformId) {
        PLATFORM_DEFINITIONS.find { p ->
            p.aliases.any { it.equals(platformId, ignoreCase = true) } || p.displayName.equals(platformId, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        if (config != null) {
            isLoading = true
            val result = metadataRepository.searchIgdb(
                query,
                platformDef?.igdbPlatformIds,
                config!!.twitchClientId,
                config!!.twitchClientSecret
            )
            results = result.getOrDefault(emptyList())
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search IGDB") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            if (config != null) {
                                isLoading = true
                                val result = metadataRepository.searchIgdb(
                                    query,
                                    platformDef?.igdbPlatformIds,
                                    config!!.twitchClientId,
                                    config!!.twitchClientSecret
                                )
                                results = result.getOrDefault(emptyList())
                                isLoading = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(results) { metadata ->
                    ListItem(
                        headlineContent = { Text(metadata.igdbName ?: "") },
                        leadingContent = { GameCover(metadata = metadata, modifier = Modifier.size(50.dp)) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                metadataRepository.updateManualMatch(platformId, gameName, metadata)
                                onMatchSelected()
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
