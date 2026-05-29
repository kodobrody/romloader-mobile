package com.example.games

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.apache.commons.net.ftp.FTPClient
import org.xmlpull.v1.XmlPullParserFactory
import java.util.Locale
import java.util.Vector
import java.util.concurrent.TimeUnit
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

import com.example.games.data.FtpFile
import com.example.games.data.DownloadTask
import com.example.games.data.DownloadStatus
import androidx.datastore.preferences.core.booleanPreferencesKey

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ftp_settings")

enum class FileServiceType(val defaultPort: String) {
    FTP("21"),
    SFTP("22"),
    NEXTCLOUD("443"),
    ROMM("443")
}

enum class ConnectionProtocol {
    HTTP,
    HTTPS
}

fun parseFileServiceType(value: String?): FileServiceType {
    return FileServiceType.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: FileServiceType.FTP
}

data class FtpConfig(
    val fileServiceType: FileServiceType = FileServiceType.FTP,
    val protocol: ConnectionProtocol = ConnectionProtocol.HTTP,
    val hostname: String = "",
    val port: String = fileServiceType.defaultPort,
    val path: String = "/",
    val username: String = "",
    val password: String = "",
    val apiToken: String = "",
    val localPath: String = "",
    val twitchClientId: String = "",
    val twitchClientSecret: String = "",
    val isOnboardingCompleted: Boolean = false
) {
    val serviceLabel: String
        get() = when (fileServiceType) {
            FileServiceType.FTP -> "FTP"
            FileServiceType.SFTP -> "SFTP"
            FileServiceType.NEXTCLOUD -> "Nextcloud"
            FileServiceType.ROMM -> "RomM"
        }

    val hasRemoteConnection: Boolean
        get() = hostname.isNotBlank()

    fun clearedRemoteConnection(): FtpConfig {
        return copy(
            hostname = "",
            port = fileServiceType.defaultPort,
            path = "/",
            username = "",
            password = "",
            apiToken = ""
        )
    }
}

class FtpRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val _fileCache = MutableStateFlow<Map<String, List<FtpFile>>>(emptyMap())
    val fileCache: StateFlow<Map<String, List<FtpFile>>> = _fileCache.asStateFlow()

    private val _downloadQueue = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadQueue: StateFlow<List<DownloadTask>> = _downloadQueue.asStateFlow()

    private val cacheFile = File(context.cacheDir, "library_cache.bin")
    private val queueFile = File(context.filesDir, "download_queue.bin")
    private val defaultGamesDir = File(context.getExternalFilesDir(null), "Games")

    private object PreferencesKeys {
        val FILE_SERVICE_TYPE = stringPreferencesKey("file_service_type")
        val PROTOCOL = stringPreferencesKey("protocol")
        val HOSTNAME = stringPreferencesKey("hostname")
        val PORT = stringPreferencesKey("port")
        val PATH = stringPreferencesKey("path")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val API_TOKEN = stringPreferencesKey("api_token")
        val LOCAL_PATH = stringPreferencesKey("local_path")
        val TWITCH_CLIENT_ID = stringPreferencesKey("twitch_client_id")
        val TWITCH_CLIENT_SECRET = stringPreferencesKey("twitch_client_secret")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val ftpConfigFlow: Flow<FtpConfig> = context.dataStore.data.map { preferences ->
        val serviceType = parseFileServiceType(preferences[PreferencesKeys.FILE_SERVICE_TYPE])
        FtpConfig(
            fileServiceType = serviceType,
            protocol = preferences[PreferencesKeys.PROTOCOL]?.let {
                ConnectionProtocol.entries.firstOrNull { protocol -> protocol.name.equals(it, ignoreCase = true) }
            } ?: if (serviceType == FileServiceType.NEXTCLOUD || serviceType == FileServiceType.ROMM) ConnectionProtocol.HTTPS else ConnectionProtocol.HTTP,
            hostname = preferences[PreferencesKeys.HOSTNAME] ?: "",
            port = preferences[PreferencesKeys.PORT] ?: serviceType.defaultPort,
            path = preferences[PreferencesKeys.PATH] ?: "/",
            username = preferences[PreferencesKeys.USERNAME] ?: "",
            password = preferences[PreferencesKeys.PASSWORD] ?: "",
            apiToken = preferences[PreferencesKeys.API_TOKEN] ?: "",
            localPath = preferences[PreferencesKeys.LOCAL_PATH] ?: "",
            twitchClientId = preferences[PreferencesKeys.TWITCH_CLIENT_ID] ?: "",
            twitchClientSecret = preferences[PreferencesKeys.TWITCH_CLIENT_SECRET] ?: "",
            isOnboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        )
    }

    init {
        loadCacheFromDisk()
        loadQueueFromDisk()
        if (!defaultGamesDir.exists()) defaultGamesDir.mkdirs()
    }


    fun isGameDownloaded(localPath: String, platformId: String, files: List<FtpFile>): Boolean {
        return files.all { isFileDownloaded(localPath, platformId, it.name) }
    }

    fun isFileDownloaded(localPath: String, platformId: String, fileName: String): Boolean {
        if (localPath.isEmpty()) {
            return File(File(defaultGamesDir, platformId), fileName).exists()
        } else {
            val rootUri = Uri.parse(localPath)
            if (rootUri.scheme == "content") {
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return false
                val platformDoc = rootDoc.findFile(platformId) ?: return false
                return platformDoc.findFile(fileName)?.exists() == true
            } else {
                val path = if (rootUri.scheme == "file") rootUri.path ?: "" else localPath
                return File(File(File(path), platformId), fileName).exists()
            }
        }
    }

    fun getDownloadedFiles(localPath: String, platformId: String): Set<String> {
        if (localPath.isEmpty()) {
            val platformDir = File(defaultGamesDir, platformId)
            if (!platformDir.exists()) return emptySet()
            return platformDir.list()?.toSet() ?: emptySet()
        } else {
            return try {
                val rootUri = Uri.parse(localPath)
                if (rootUri.scheme == "content") {
                    val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return emptySet()
                    val platformDoc = rootDoc.findFile(platformId) ?: return emptySet()
                    if (!platformDoc.isDirectory) return emptySet()
                    platformDoc.listFiles().mapNotNull { it.name }.toSet()
                } else {
                    val path = if (rootUri.scheme == "file") rootUri.path ?: "" else localPath
                    val platformDir = File(File(path), platformId)
                    if (!platformDir.exists() || !platformDir.isDirectory) return emptySet()
                    platformDir.list()?.toSet() ?: emptySet()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptySet()
            }
        }
    }

    fun enqueueDownload(platformId: String, gameName: String, files: List<FtpFile>) {
        if (_downloadQueue.value.any { it.gameName == gameName && it.platformName == platformId }) return
        val task = DownloadTask(gameName, platformId, files)
        _downloadQueue.value += task
        saveQueueToDisk()
        DownloadService.start(context)
        processQueue()
    }

    fun clearDownloads() {
        _downloadQueue.value = _downloadQueue.value.filter { 
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING 
        }
        saveQueueToDisk()
    }

    private var isDownloading = false
    private var downloadJob: kotlinx.coroutines.Job? = null

    private fun processQueue() {
        if (isDownloading) return
        val nextTask = _downloadQueue.value.find { it.status == DownloadStatus.QUEUED } ?: return
        
        isDownloading = true
        downloadJob = scope.launch {
            try {
                downloadGame(nextTask)
            } finally {
                isDownloading = false
                downloadJob = null
                processQueue()
            }
        }
    }

    fun cancelDownload(platformId: String, gameName: String) {
        val taskToDelete = _downloadQueue.value.find { it.gameName == gameName && it.platformName == platformId }
        if (taskToDelete != null) {
            scope.launch {
                removeDownload(platformId, gameName, taskToDelete.files)
            }
        }
    }

    suspend fun removeDownload(platformId: String, gameName: String, files: List<FtpFile>) {
        val config = ftpConfigFlow.first()
        
        // If this is currently downloading, cancel it first
        val currentTask = _downloadQueue.value.find { it.status == DownloadStatus.DOWNLOADING }
        if (currentTask?.gameName == gameName && currentTask?.platformName == platformId) {
            downloadJob?.cancel()
            try {
                downloadJob?.join()
            } catch (e: Exception) {
                // Ignore cancellation exception if any
            }
        }

        // Remove from disk
        if (config.localPath.isEmpty()) {
            val platformDir = File(defaultGamesDir, platformId)
            if (platformDir.exists()) {
                files.forEach { file ->
                    val localFile = File(platformDir, file.name)
                    if (localFile.exists()) {
                        localFile.delete()
                    }
                }
            }
        } else {
            try {
                val rootUri = Uri.parse(config.localPath)
                if (rootUri.scheme == "content") {
                    val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                    val platformDoc = rootDoc?.findFile(platformId)
                    if (platformDoc != null && platformDoc.isDirectory) {
                        files.forEach { file ->
                            val fileDoc = platformDoc.findFile(file.name)
                            fileDoc?.delete()
                        }
                    }
                } else {
                    val path = if (rootUri.scheme == "file") rootUri.path ?: "" else config.localPath
                    val platformDir = File(File(path), platformId)
                    if (platformDir.exists()) {
                        files.forEach { file ->
                            val localFile = File(platformDir, file.name)
                            if (localFile.exists()) {
                                localFile.delete()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Remove from queue
        _downloadQueue.value = _downloadQueue.value.filterNot { it.gameName == gameName && it.platformName == platformId }
        saveQueueToDisk()
    }

    private suspend fun downloadGame(task: DownloadTask) {
        updateTaskStatus(task, DownloadStatus.DOWNLOADING)
        val config = ftpConfigFlow.first()
        try {
            val totalSize = task.files.sumOf { it.size }.coerceAtLeast(1L)
            var downloadedSize = 0L

            val onChunkCopied: (Int) -> Unit = { bytesCopied ->
                downloadedSize += bytesCopied
                updateTaskProgress(task, (downloadedSize.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f))
            }

            when (config.fileServiceType) {
                FileServiceType.FTP -> downloadTaskViaFtp(task, config, onChunkCopied)
                FileServiceType.SFTP -> downloadTaskViaSftp(task, config, onChunkCopied)
                FileServiceType.NEXTCLOUD -> downloadTaskViaWebDav(task, config, onChunkCopied)
                FileServiceType.ROMM -> downloadTaskViaRomm(task, config, onChunkCopied)
            }
            updateTaskStatus(task, DownloadStatus.COMPLETED)
        } catch (e: Exception) {
            e.printStackTrace()
            updateTaskStatus(task, DownloadStatus.FAILED)
        }
    }

    private suspend fun downloadTaskViaFtp(task: DownloadTask, config: FtpConfig, onChunkCopied: (Int) -> Unit) {
        val ftpClient = FTPClient()
        try {
            ftpClient.connect(config.hostname, config.port.toIntOrNull() ?: FileServiceType.FTP.defaultPort.toInt())
            if (!ftpClient.login(config.username, config.password)) {
                throw Exception("FTP login failed")
            }

            ftpClient.enterLocalPassiveMode()
            ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)

            writeTaskFilesToLocal(task, config) { file, output ->
                val inputStream = ftpClient.retrieveFileStream(file.fullPath)
                    ?: throw Exception("Failed to open remote file: ${file.fullPath}")

                inputStream.use { input ->
                    copyStreamWithProgress(input, output, onChunkCopied)
                }
                if (!ftpClient.completePendingCommand()) {
                    throw Exception("FTP transfer did not complete for ${file.name}")
                }
            }

            ftpClient.logout()
        } finally {
            if (ftpClient.isConnected) {
                try { ftpClient.disconnect() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun downloadTaskViaSftp(task: DownloadTask, config: FtpConfig, onChunkCopied: (Int) -> Unit) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(config.username, config.hostname, config.port.toIntOrNull() ?: FileServiceType.SFTP.defaultPort.toInt())
            session.setPassword(config.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.timeout = 5000
            session.connect(5000)

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(5000)

            writeTaskFilesToLocal(task, config) { file, output ->
                channel.get(file.fullPath).use { input ->
                    copyStreamWithProgress(input, output, onChunkCopied)
                }
            }
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    private suspend fun writeTaskFilesToLocal(
        task: DownloadTask,
        config: FtpConfig,
        writer: suspend (FtpFile, OutputStream) -> Unit
    ) {
        if (config.localPath.isEmpty()) {
            val platformDir = File(defaultGamesDir, task.platformName)
            if (!platformDir.exists()) platformDir.mkdirs()

            for (file in task.files) {
                val localFile = File(platformDir, file.name)
                FileOutputStream(localFile).use { output ->
                    writer(file, output)
                }
            }
            return
        }

        val rootUri = Uri.parse(config.localPath)
        if (rootUri.scheme == "content") {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: throw Exception("Cannot access local path")
            var platformDoc = rootDoc.findFile(task.platformName)
            if (platformDoc == null || !platformDoc.isDirectory) {
                platformDoc = rootDoc.createDirectory(task.platformName)
            }
            if (platformDoc == null) throw Exception("Cannot create platform directory")

            for (file in task.files) {
                var localFileDoc = platformDoc.findFile(file.name)
                if (localFileDoc == null) {
                    localFileDoc = platformDoc.createFile("application/octet-stream", file.name)
                }
                if (localFileDoc == null) throw Exception("Cannot create local file: ${file.name}")

                context.contentResolver.openOutputStream(localFileDoc.uri)?.use { output ->
                    writer(file, output)
                } ?: throw Exception("Cannot open local file stream: ${file.name}")
            }
            return
        }

        val path = if (rootUri.scheme == "file") rootUri.path ?: "" else config.localPath
        val platformDir = File(File(path), task.platformName)
        if (!platformDir.exists()) platformDir.mkdirs()

        for (file in task.files) {
            val localFile = File(platformDir, file.name)
            FileOutputStream(localFile).use { output ->
                writer(file, output)
            }
        }
    }

    private suspend fun copyStreamWithProgress(input: InputStream, output: OutputStream, onChunkCopied: (Int) -> Unit) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            downloadJob?.ensureActive()
            output.write(buffer, 0, bytesRead)
            onChunkCopied(bytesRead)
        }
    }

    private fun updateTaskStatus(task: DownloadTask, status: DownloadStatus) {
        _downloadQueue.value = _downloadQueue.value.map {
            if (it.gameName == task.gameName && it.platformName == task.platformName) it.copy(status = status) else it
        }
        saveQueueToDisk()
    }

    private fun updateTaskProgress(task: DownloadTask, progress: Float) {
        _downloadQueue.value = _downloadQueue.value.map {
            if (it.gameName == task.gameName && it.platformName == task.platformName) it.copy(progress = progress) else it
        }
    }

    private fun loadCacheFromDisk() {
        if (cacheFile.exists()) {
            try {
                ObjectInputStream(FileInputStream(cacheFile)).use { ois ->
                    @Suppress("UNCHECKED_CAST")
                    val loaded = ois.readObject() as Map<String, List<FtpFile>>
                    _fileCache.value = loaded
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveCacheToDisk(cache: Map<String, List<FtpFile>>) {
        try {
            ObjectOutputStream(FileOutputStream(cacheFile)).use { oos ->
                oos.writeObject(cache)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadQueueFromDisk() {
        if (queueFile.exists()) {
            try {
                ObjectInputStream(FileInputStream(queueFile)).use { ois ->
                    @Suppress("UNCHECKED_CAST")
                    val loaded = ois.readObject() as List<DownloadTask>
                    // Reset downloading status to queued if app was killed
                    _downloadQueue.value = loaded.map { 
                        if (it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.QUEUED, progress = 0f) else it
                    }
                    if (_downloadQueue.value.any { it.status == DownloadStatus.QUEUED }) {
                        DownloadService.start(context)
                    }
                    processQueue()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveQueueToDisk() {
        try {
            ObjectOutputStream(FileOutputStream(queueFile)).use { oos ->
                oos.writeObject(_downloadQueue.value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveFtpConfig(config: FtpConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FILE_SERVICE_TYPE] = config.fileServiceType.name
            preferences[PreferencesKeys.PROTOCOL] = config.protocol.name
            preferences[PreferencesKeys.HOSTNAME] = config.hostname
            preferences[PreferencesKeys.PORT] = config.port
            preferences[PreferencesKeys.PATH] = config.path
            preferences[PreferencesKeys.USERNAME] = config.username
            preferences[PreferencesKeys.PASSWORD] = config.password
            preferences[PreferencesKeys.API_TOKEN] = config.apiToken
            preferences[PreferencesKeys.LOCAL_PATH] = config.localPath
            preferences[PreferencesKeys.TWITCH_CLIENT_ID] = config.twitchClientId
            preferences[PreferencesKeys.TWITCH_CLIENT_SECRET] = config.twitchClientSecret
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = config.isOnboardingCompleted
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun testConnection(config: FtpConfig): Result<Unit> = withContext(Dispatchers.IO) {
        if (config.hostname.isEmpty()) return@withContext Result.failure(Exception("Hostname is empty"))
        if (config.fileServiceType == FileServiceType.ROMM && config.apiToken.isBlank()) {
            return@withContext Result.failure(Exception("API token is empty"))
        }
        when (config.fileServiceType) {
            FileServiceType.FTP -> {
                val ftpClient = FTPClient()
                try {
                    ftpClient.connectTimeout = 5000
                    ftpClient.defaultTimeout = 5000
                    ftpClient.connect(config.hostname, config.port.toIntOrNull() ?: FileServiceType.FTP.defaultPort.toInt())
                    ftpClient.soTimeout = 5000
                    val loginSuccess = ftpClient.login(config.username, config.password)
                    if (loginSuccess) {
                        ftpClient.logout()
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Login failed"))
                    }
                } catch (e: Exception) {
                    Result.failure(Exception(describeConnectionFailure(config, e), e))
                } finally {
                    if (ftpClient.isConnected) {
                        try { ftpClient.disconnect() } catch (e: Exception) {}
                    }
                }
            }
            FileServiceType.SFTP -> {
                var session: Session? = null
                var channel: ChannelSftp? = null
                try {
                    val jsch = JSch()
                    session = jsch.getSession(config.username, config.hostname, config.port.toIntOrNull() ?: FileServiceType.SFTP.defaultPort.toInt())
                    session.setPassword(config.password)
                    session.setConfig("StrictHostKeyChecking", "no")
                    session.timeout = 5000
                    session.connect(5000)

                    channel = session.openChannel("sftp") as ChannelSftp
                    channel.connect(5000)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception(describeConnectionFailure(config, e), e))
                } finally {
                    try { channel?.disconnect() } catch (_: Exception) {}
                    try { session?.disconnect() } catch (_: Exception) {}
                }
            }
            FileServiceType.NEXTCLOUD -> {
                try {
                    val client = buildWebDavClient()
                    val url = buildWebDavUrl(config, config.path)
                    val request = Request.Builder()
                        .url(url)
                        .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
                        .header("Authorization", Credentials.basic(config.username, config.password))
                        .header("Depth", "0")
                        .build()
                    val response = client.newCall(request).execute()
                    response.close()
                    if (response.isSuccessful || response.code == 207) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Nextcloud responded with HTTP ${response.code}"))
                    }
                } catch (e: Exception) {
                    Result.failure(Exception(describeConnectionFailure(config, e), e))
                }
            }
            FileServiceType.ROMM -> {
                try {
                    fetchRommPlatforms(config)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception(describeConnectionFailure(config, e), e))
                }
            }
        }
    }

    suspend fun testLocalPath(localPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (localPath.isEmpty()) {
                val testFile = File(defaultGamesDir, ".test_connection")
                testFile.writeText("test")
                if (testFile.readText() != "test") throw Exception("Read back failed")
                testFile.delete()
                Result.success(Unit)
            } else {
                val rootUri = Uri.parse(localPath)
                if (rootUri.scheme == "content") {
                    val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: throw Exception("Cannot access local path")
                    if (!rootDoc.canWrite()) throw Exception("No write permission")
                    
                    val testFileDoc = rootDoc.createFile("text/plain", ".test_connection") ?: throw Exception("Failed to create test file")
                    
                    context.contentResolver.openOutputStream(testFileDoc.uri)?.use { 
                        it.write("test".toByteArray())
                    } ?: throw Exception("Failed to open output stream")

                    val readBack = context.contentResolver.openInputStream(testFileDoc.uri)?.use {
                        it.readBytes().decodeToString()
                    } ?: throw Exception("Failed to open input stream")

                    if (readBack != "test") throw Exception("Read back failed: $readBack")
                    
                    testFileDoc.delete()
                    Result.success(Unit)
                } else {
                    val path = if (rootUri.scheme == "file") rootUri.path ?: "" else localPath
                    val testFile = File(File(path), ".test_connection")
                    testFile.writeText("test")
                    if (testFile.readText() != "test") throw Exception("Read back failed")
                    testFile.delete()
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshLibrary(config: FtpConfig): Result<Unit> = withContext(Dispatchers.IO) {
        val testResult = testConnection(config)
        if (testResult.isFailure) return@withContext Result.failure(testResult.exceptionOrNull()!!)

        val rootFilesResult = listFiles(config, config.path)
        if (rootFilesResult.isFailure) {
            return@withContext Result.failure(rootFilesResult.exceptionOrNull()!!)
        }

        try {
            val newCache = mutableMapOf<String, List<FtpFile>>()
            val rootFiles = rootFilesResult.getOrThrow()
            newCache[config.path] = rootFiles

            // 2. Identify platforms and list their contents
            val potentialPlatforms = rootFiles.filter { it.isDirectory }.mapNotNull { dir ->
                val platform = PLATFORM_DEFINITIONS.find { p ->
                    p.aliases.any { it.equals(dir.name, ignoreCase = true) }
                }
                if (platform != null) (platform to dir.name) to dir.fullPath else null
            }

            for ((platformInfo, path) in potentialPlatforms) {
                val (platform, _) = platformInfo
                val filesResult = listFiles(config, path)
                if (filesResult.isFailure) continue

                val files = filesResult.getOrThrow()
                val hasGames = files.any { file ->
                    !file.isDirectory && platform.extensions.any { ext ->
                        file.name.lowercase(Locale.ROOT).endsWith(".$ext")
                    }
                }
                if (hasGames) {
                    newCache[path] = files
                }
            }

            _fileCache.value = newCache
            saveCacheToDisk(newCache)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(config: FtpConfig, path: String): Result<List<FtpFile>> = withContext(Dispatchers.IO) {
        when (config.fileServiceType) {
            FileServiceType.FTP -> {
                val ftpClient = FTPClient()
                try {
                    ftpClient.connectTimeout = 5000
                    ftpClient.defaultTimeout = 5000
                    ftpClient.connect(config.hostname, config.port.toIntOrNull() ?: FileServiceType.FTP.defaultPort.toInt())
                    ftpClient.soTimeout = 5000
                    if (ftpClient.login(config.username, config.password)) {
                        ftpClient.enterLocalPassiveMode()
                        val files = ftpClient.listFiles(path).map { file ->
                            FtpFile(
                                name = file.name,
                                size = file.size,
                                isDirectory = file.isDirectory,
                                fullPath = joinRemotePath(path, file.name)
                            )
                        }
                        ftpClient.logout()
                        Result.success(files)
                    } else {
                        Result.failure(Exception("Login failed"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    if (ftpClient.isConnected) {
                        try { ftpClient.disconnect() } catch (e: Exception) {}
                    }
                }
            }
            FileServiceType.SFTP -> {
                var session: Session? = null
                var channel: ChannelSftp? = null
                try {
                    val jsch = JSch()
                    session = jsch.getSession(config.username, config.hostname, config.port.toIntOrNull() ?: FileServiceType.SFTP.defaultPort.toInt())
                    session.setPassword(config.password)
                    session.setConfig("StrictHostKeyChecking", "no")
                    session.timeout = 5000
                    session.connect(5000)

                    channel = session.openChannel("sftp") as ChannelSftp
                    channel.connect(5000)

                    @Suppress("UNCHECKED_CAST")
                    val entries = channel.ls(path) as Vector<ChannelSftp.LsEntry>
                    val files = entries
                        .asSequence()
                        .filter { it.filename != "." && it.filename != ".." }
                        .map { entry ->
                            FtpFile(
                                name = entry.filename,
                                size = entry.attrs.size,
                                isDirectory = entry.attrs.isDir,
                                fullPath = joinRemotePath(path, entry.filename)
                            )
                        }
                        .toList()

                    Result.success(files)
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    try { channel?.disconnect() } catch (_: Exception) {}
                    try { session?.disconnect() } catch (_: Exception) {}
                }
            }
            FileServiceType.NEXTCLOUD -> {
                try {
                    val client = buildWebDavClient()
                    val url = buildWebDavUrl(config, path)
                    val request = Request.Builder()
                        .url(url)
                        .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
                        .header("Authorization", Credentials.basic(config.username, config.password))
                        .header("Depth", "1")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    response.close()
                    if (!response.isSuccessful && response.code != 207) {
                        return@withContext Result.failure(Exception("WebDAV PROPFIND failed: HTTP ${response.code}"))
                    }
                    val files = parseWebDavListing(body, path, config)
                    Result.success(files)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            FileServiceType.ROMM -> {
                try {
                    if (path == config.path) {
                        val platforms = fetchRommPlatforms(config).map { platform ->
                            FtpFile(
                                name = platform.fsSlug,
                                size = platform.romCount.toLong(),
                                isDirectory = true,
                                fullPath = buildRommPlatformPath(platform.id, platform.fsSlug)
                            )
                        }
                        Result.success(platforms)
                    } else {
                        val platformRef = parseRommPlatformPath(path)
                            ?: return@withContext Result.failure(Exception("Invalid RomM platform path: $path"))
                        val files = fetchRommRoms(config, platformRef.platformId)
                            .flatMap { rom ->
                                rom.files.map { file ->
                                    FtpFile(
                                        name = file.fileName,
                                        size = file.fileSizeBytes,
                                        isDirectory = false,
                                        fullPath = buildRommFilePath(platformRef.platformSlug, file.id, file.fileName)
                                    )
                                }
                            }
                        Result.success(files)
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    private fun joinRemotePath(basePath: String, name: String): String {
        return if (basePath.endsWith("/")) "$basePath$name" else "$basePath/$name"
    }

    private fun buildWebDavClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }  // Trust all hostnames
            .build()
    }

    private fun buildWebDavUrl(config: FtpConfig, remotePath: String): String {
        val scheme = config.protocol.name.lowercase(Locale.ROOT)
        val port = config.port.toIntOrNull() ?: 443
        val base = "$scheme://${normalizeHostname(config.hostname)}:$port/remote.php/dav/files/${config.username}"
        val cleanPath = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        return "$base$cleanPath".trimEnd('/') + if (cleanPath.endsWith("/") || cleanPath == "/") "/" else ""
    }

    private fun buildRommApiBaseUrl(config: FtpConfig): String {
        val scheme = config.protocol.name.lowercase(Locale.ROOT)
        val port = config.port.toIntOrNull() ?: FileServiceType.ROMM.defaultPort.toInt()
        return "$scheme://${normalizeHostname(config.hostname)}:$port/api/"
    }

    private fun buildHttpServiceBaseUrl(config: FtpConfig): String {
        val scheme = config.protocol.name.lowercase(Locale.ROOT)
        val port = config.port.toIntOrNull() ?: config.fileServiceType.defaultPort.toInt()
        return "$scheme://${normalizeHostname(config.hostname)}:$port"
    }

    private fun normalizeHostname(hostname: String): String {
        return hostname
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
    }

    private fun describeConnectionFailure(config: FtpConfig, error: Exception): String {
        val rootCause = rootCause(error)
        val target = when (config.fileServiceType) {
            FileServiceType.FTP,
            FileServiceType.SFTP -> "${normalizeHostname(config.hostname)}:${config.port.ifBlank { config.fileServiceType.defaultPort }}"
            FileServiceType.NEXTCLOUD,
            FileServiceType.ROMM -> buildHttpServiceBaseUrl(config)
        }

        return when (rootCause) {
            is UnknownHostException -> "Cannot resolve host ${config.hostname}. Check the hostname or IP address."
            is ConnectException -> "Cannot reach ${config.serviceLabel} at $target. Check the protocol, host, port, that the service is running, and that this device can reach it on your network."
            is SocketTimeoutException -> "Timed out connecting to ${config.serviceLabel} at $target. Check that the service is reachable and responding."
            is SSLException -> "TLS/SSL failed while connecting to $target. If the server is HTTP-only, switch the protocol to HTTP."
            else -> rootCause.message ?: error.message ?: "Connection failed"
        }
    }

    private tailrec fun rootCause(throwable: Throwable): Throwable {
        val cause = throwable.cause ?: return throwable
        return rootCause(cause)
    }

    private fun buildRommPlatformPath(platformId: Int, platformSlug: String): String {
        return "/romm/platform/$platformId/$platformSlug"
    }

    private fun parseRommPlatformPath(path: String): RommPlatformReference? {
        val match = Regex("^/romm/platform/(\\d+)/([^/]+)$").matchEntire(path) ?: return null
        return RommPlatformReference(
            platformId = match.groupValues[1].toInt(),
            platformSlug = match.groupValues[2]
        )
    }

    private fun buildRommFilePath(platformSlug: String, fileId: Int, fileName: String): String {
        return "/romm/file/$platformSlug/$fileId/$fileName"
    }

    private fun parseRommFilePath(path: String): RommFileReference? {
        val match = Regex("^/romm/file/([^/]+)/(\\d+)/(.*)$").matchEntire(path) ?: return null
        val fileName = match.groupValues[3]
        if (fileName.isEmpty()) return null
        return RommFileReference(
            platformSlug = match.groupValues[1],
            fileId = match.groupValues[2].toInt(),
            fileName = fileName
        )
    }

    private fun fetchRommPlatforms(config: FtpConfig): List<RommPlatform> {
        val client = buildWebDavClient()
        val url = buildRommApiBaseUrl(config).toHttpUrl()
            .newBuilder()
            .addPathSegment("platforms")
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", buildRommAuthorizationHeader(config))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("RomM responded with HTTP ${response.code}")
            }
            return gson.fromJson(body, Array<RommPlatform>::class.java).toList()
        }
    }

    private fun fetchRommRoms(config: FtpConfig, platformId: Int): List<RommRom> {
        val client = buildWebDavClient()
        val roms = mutableListOf<RommRom>()
        val pageSize = 500
        var offset = 0

        while (true) {
            val url = buildRommApiBaseUrl(config).toHttpUrl()
                .newBuilder()
                .addPathSegment("roms")
                .addQueryParameter("platform_ids", platformId.toString())
                .addQueryParameter("limit", pageSize.toString())
                .addQueryParameter("offset", offset.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", buildRommAuthorizationHeader(config))
                .build()

            val page = client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw Exception("RomM responded with HTTP ${response.code}")
                }
                gson.fromJson(body, RommRomsPage::class.java)
            }

            if (page.items.isEmpty()) break
            roms += page.items
            offset += page.items.size

            if (page.total > 0 && roms.size >= page.total) break
            val currentLimit = if (page.limit > 0) page.limit else pageSize
            if (page.items.size < currentLimit) break
        }

        return roms
    }

    private fun parseWebDavListing(xml: String, requestedPath: String, config: FtpConfig): List<FtpFile> {
        val files = mutableListOf<FtpFile>()
        val basePrefix = "/remote.php/dav/files/${config.username}"
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())

            var currentHref: String? = null
            var isCollection = false
            var contentLength = 0L
            var inProp = false
            var inResponse = false
            var event = parser.eventType

            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        val name = parser.name
                        when {
                            name == "response" -> {
                                inResponse = true
                                currentHref = null
                                isCollection = false
                                contentLength = 0L
                            }
                            name == "href" && inResponse -> currentHref = parser.nextText().trim()
                            name == "prop" -> inProp = true
                            name == "collection" && inProp -> isCollection = true
                            name == "getcontentlength" && inProp -> {
                                contentLength = parser.nextText().trim().toLongOrNull() ?: 0L
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name == "prop") inProp = false
                        if (name == "response" && inResponse) {
                            inResponse = false
                            val href = currentHref ?: ""
                            val decoded = java.net.URLDecoder.decode(href, "UTF-8")
                            val fullPath = if (decoded.startsWith(basePrefix)) {
                                decoded.removePrefix(basePrefix).trimEnd('/')
                            } else {
                                decoded.trimEnd('/')
                            }
                            val itemName = fullPath.substringAfterLast("/")
                            val normalizedRequested = requestedPath.trimEnd('/')
                            if (fullPath != normalizedRequested && itemName.isNotEmpty()) {
                                files.add(
                                    FtpFile(
                                        name = itemName,
                                        size = contentLength,
                                        isDirectory = isCollection,
                                        fullPath = fullPath
                                    )
                                )
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    private suspend fun downloadTaskViaWebDav(task: DownloadTask, config: FtpConfig, onChunkCopied: (Int) -> Unit) {
        val client = buildWebDavClient()
        writeTaskFilesToLocal(task, config) { file, output ->
            val url = buildWebDavUrl(config, file.fullPath)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", Credentials.basic(config.username, config.password))
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw Exception("WebDAV GET failed: HTTP ${response.code} for ${file.name}")
            }
            response.body?.byteStream()?.use { input ->
                copyStreamWithProgress(input, output, onChunkCopied)
            } ?: throw Exception("Empty response body for ${file.name}")
            response.close()
        }
    }

    private suspend fun downloadTaskViaRomm(task: DownloadTask, config: FtpConfig, onChunkCopied: (Int) -> Unit) {
        val client = buildWebDavClient()
        writeTaskFilesToLocal(task, config) { file, output ->
            val fileRef = parseRommFilePath(file.fullPath)
                ?: throw Exception("Invalid RomM file path: ${file.fullPath}")
            val url = buildRommApiBaseUrl(config).toHttpUrl()
                .newBuilder()
                .addPathSegment("roms")
                .addPathSegment(fileRef.fileId.toString())
                .addPathSegment("files")
                .addPathSegment("content")
                .addPathSegment(fileRef.fileName)
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", buildRommAuthorizationHeader(config))
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw Exception("RomM download failed: HTTP ${response.code} for ${file.name}")
            }
            response.body?.byteStream()?.use { input ->
                copyStreamWithProgress(input, output, onChunkCopied)
            } ?: throw Exception("Empty response body for ${file.name}")
            response.close()
        }
    }

    private fun buildRommAuthorizationHeader(config: FtpConfig): String {
        return "Bearer ${config.apiToken.trim()}"
    }

    private data class RommPlatform(
        val id: Int,
        @SerializedName("fs_slug") val fsSlug: String,
        @SerializedName("rom_count") val romCount: Int = 0
    )

    private data class RommPlatformReference(
        val platformId: Int,
        val platformSlug: String
    )

    private data class RommFileReference(
        val platformSlug: String,
        val fileId: Int,
        val fileName: String
    )

    private data class RommFile(
        val id: Int,
        @SerializedName("file_name") val fileName: String,
        @SerializedName("file_size_bytes") val fileSizeBytes: Long = 0
    )

    private data class RommRom(
        val id: Int,
        @SerializedName("fs_name") val fsName: String,
        val files: List<RommFile> = emptyList()
    )

    private data class RommRomsPage(
        val items: List<RommRom> = emptyList(),
        val total: Int = 0,
        val limit: Int = 0,
        val offset: Int = 0
    )
}
