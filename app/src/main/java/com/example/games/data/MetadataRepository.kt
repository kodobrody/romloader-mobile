package com.example.games.data

import android.content.Context
import com.example.games.PLATFORM_DEFINITIONS
import com.example.games.util.cleanGameName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.*

class MetadataRepository(private val context: Context) {
    private val _metadataCache = MutableStateFlow<Map<String, GameMetadata>>(emptyMap())
    val metadataCache: StateFlow<Map<String, GameMetadata>> = _metadataCache

    private val _fetchingProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val fetchingProgress: StateFlow<Map<String, Float>> = _fetchingProgress

    private val _platformLogos = MutableStateFlow<Map<Int, String>>(emptyMap())
    val platformLogos: StateFlow<Map<Int, String>> = _platformLogos

    private val cacheFile = File(context.cacheDir, "metadata_cache.bin")
    private val platformLogosFile = File(context.cacheDir, "platform_logos_cache.bin")
    
    private val rateLimiter = object {
        private val requestQueue = mutableListOf<Long>()
        private val maxRequestsPerSecond = 4
        private val minimumIntervalMs = 250L
        
        suspend fun checkRateLimit() {
            val now = System.currentTimeMillis()
            requestQueue.removeIf { now - it >= minimumIntervalMs }
            
            while (requestQueue.size >= maxRequestsPerSecond) {
                val oldestRequestTime = requestQueue.first()
                val timeUntilNextRequest = oldestRequestTime + minimumIntervalMs - now
                if (timeUntilNextRequest > 0) {
                    kotlinx.coroutines.delay(timeUntilNextRequest)
                }
                val refreshedNow = System.currentTimeMillis()
                requestQueue.removeIf { refreshedNow - it >= minimumIntervalMs }
            }
            
            requestQueue.add(now)
        }
    }
    
    private val twitchApi = Retrofit.Builder()
        .baseUrl("https://id.twitch.tv/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TwitchApi::class.java)

    private val igdbApi = Retrofit.Builder()
        .baseUrl("https://api.igdb.com/v4/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(IgdbApi::class.java)

    init {
        loadCache()
        loadPlatformLogosCache()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPlatformLogosCache() {
        if (platformLogosFile.exists()) {
            try {
                ObjectInputStream(FileInputStream(platformLogosFile)).use { ois ->
                    _platformLogos.value = ois.readObject() as Map<Int, String>
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun savePlatformLogosCache() {
        try {
            ObjectOutputStream(FileOutputStream(platformLogosFile)).use { oos ->
                oos.writeObject(_platformLogos.value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCache() {
        if (cacheFile.exists()) {
            try {
                ObjectInputStream(FileInputStream(cacheFile)).use { ois ->
                    _metadataCache.value = ois.readObject() as Map<String, GameMetadata>
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveCache() {
        try {
            ObjectOutputStream(FileOutputStream(cacheFile)).use { oos ->
                oos.writeObject(_metadataCache.value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun searchIgdb(
        queryText: String,
        platformIds: List<Int>?,
        clientId: String,
        clientSecret: String
    ): Result<List<GameMetadata>> = withContext(Dispatchers.IO) {
        try {
            val tokenResponse = twitchApi.getAccessToken(clientId, clientSecret)
            val accessToken = "Bearer ${tokenResponse.access_token}"

            val platformFilter = platformIds?.let { ids ->
                "where platforms = (${ids.joinToString(",")});"
            } ?: ""

            val query = "fields name, cover.image_id, cover.url; search \"$queryText\"; $platformFilter limit 10;"
            val body = query.toRequestBody("text/plain".toMediaType())
            val games = igdbApi.searchGames(clientId, accessToken, body)

            val results = games.map { game ->
                val coverUrl = game.cover?.let { cover ->
                    if (!cover.image_id.isNullOrEmpty()) {
                        val extension = if (cover.alpha_channel == true) "png" else "jpg"
                        val url = "https://images.igdb.com/igdb/image/upload/t_cover_big/${cover.image_id}.$extension"
                        url
                    } else {
                        cover.url?.let { "https:" + it.replace("t_thumb", "t_cover_big") }
                    }
                }
                GameMetadata(
                    igdbId = game.id,
                    cleanName = queryText,
                    coverUrl = coverUrl,
                    igdbName = game.name,
                    cacheKey = queryText  // Use queryText as cache key for matching with SearchScreen
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateManualMatch(platformId: String, rawName: String, metadata: GameMetadata) {
        val newCache = _metadataCache.value.toMutableMap()
        newCache["${platformId}_$rawName"] = metadata
        _metadataCache.value = newCache
        saveCache()
    }

    suspend fun fetchMetadataForPlatform(
        platformId: String,
        gameNames: List<String>,
        clientId: String,
        clientSecret: String
    ) = withContext(Dispatchers.IO) {
        if (clientId.isEmpty() || clientSecret.isEmpty()) return@withContext
        if (_fetchingProgress.value.containsKey(platformId)) return@withContext

        var missing = gameNames.filter { !metadataCache.value.containsKey("${platformId}_$it") }
        if (missing.isEmpty()) return@withContext

        val totalToFetch = missing.size
        var fetchedCount = 0

        try {
            _fetchingProgress.update { it + (platformId to 0f) }
            val tokenResponse = twitchApi.getAccessToken(clientId, clientSecret)
            val accessToken = "Bearer ${tokenResponse.access_token}"

            while (missing.isNotEmpty()) {
                val batchSize = minOf(4, missing.size)
                val currentBatch = missing.take(batchSize)
                
                missing = missing.drop(batchSize)

                try {
                    rateLimiter.checkRateLimit()
                    
                    val platformFilter = PLATFORM_DEFINITIONS.find { p ->
                        p.aliases.any { it.equals(platformId, ignoreCase = true) } || 
                        p.displayName.equals(platformId, ignoreCase = true)
                    }?.igdbPlatformIds?.let { ids ->
                        "where platforms = (${ids.joinToString(",")});"
                    } ?: ""

                    val batchResults = currentBatch.mapNotNull { rawName ->
                        try {
                            if (metadataCache.value.containsKey("${platformId}_$rawName")) return@mapNotNull null
                            
                            val clean = cleanGameName(rawName)
                            val query = "fields name, cover.image_id, cover.url; search \"$clean\"; $platformFilter limit 1;"
                            val body = query.toRequestBody("text/plain".toMediaType())
                            val games = igdbApi.searchGames(clientId, accessToken, body)

                            if (games.isNotEmpty()) {
                                val game = games.first()
                                val coverUrl = game.cover?.let { cover ->
                                    if (!cover.image_id.isNullOrEmpty()) {
                                        val extension = if (cover.alpha_channel == true) "png" else "jpg"
                                        "https://images.igdb.com/igdb/image/upload/t_cover_big/${cover.image_id}.$extension"
                                    } else {
                                        cover.url?.let { "https:" + it.replace("t_thumb", "t_cover_big") }
                                    }
                                }
                                GameMetadata(
                                    igdbId = game.id,
                                    cleanName = clean,
                                    coverUrl = coverUrl,
                                    igdbName = game.name,
                                    cacheKey = rawName
                                )
                            } else {
                                GameMetadata(cleanName = clean, cacheKey = rawName)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }

                    if (batchResults.isNotEmpty()) {
                        val newEntries = batchResults.associateBy { "${platformId}_${it.cacheKey}" }
                        _metadataCache.update { it + newEntries }
                    }
                    
                    fetchedCount += batchSize
                    _fetchingProgress.update { it + (platformId to (fetchedCount.toFloat() / totalToFetch)) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            saveCache()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _fetchingProgress.update { it - platformId }
        }
    }

    suspend fun testIgdbConnection(clientId: String, clientSecret: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            twitchApi.getAccessToken(clientId, clientSecret)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPlatformLogos(
        clientId: String,
        clientSecret: String
    ) = withContext(Dispatchers.IO) {
        if (clientId.isEmpty() || clientSecret.isEmpty()) return@withContext

        val allPlatformIds = PLATFORM_DEFINITIONS.flatMap { it.igdbPlatformIds }.distinct()
        val missingIds = allPlatformIds.filter { !_platformLogos.value.containsKey(it) }
        if (missingIds.isEmpty()) return@withContext

        try {
            val tokenResponse = twitchApi.getAccessToken(clientId, clientSecret)
            val accessToken = "Bearer ${tokenResponse.access_token}"

            val query = "fields name, platform_logo.image_id, platform_logo.alpha_channel, platform_logo.url; where id = (${missingIds.joinToString(",")}); limit 100;"
            val body = query.toRequestBody("text/plain".toMediaType())
            val platforms = igdbApi.getPlatforms(clientId, accessToken, body)

            val newLogos = platforms.mapNotNull { platform ->
                val logo = platform.platform_logo
                if (logo != null && !logo.image_id.isNullOrEmpty()) {
                    val extension = if (logo.alpha_channel == true) "png" else "jpg"
                    val logoUrl = "https://images.igdb.com/igdb/image/upload/t_logo_med/${logo.image_id}.$extension"
                    platform.id.toInt() to logoUrl
                } else {
                    val logoUrl = logo?.url?.let { "https:" + it.replace("t_thumb", "t_logo_med") }
                    if (logoUrl != null) {
                        platform.id.toInt() to logoUrl
                    } else null
                }
            }.toMap()

            if (newLogos.isNotEmpty()) {
                _platformLogos.update { it + newLogos }
                savePlatformLogosCache()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}