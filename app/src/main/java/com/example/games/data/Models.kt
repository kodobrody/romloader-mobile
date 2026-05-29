package com.example.games.data

import android.os.Parcelable
import java.io.Serializable

data class FtpFile(
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val fullPath: String
) : Serializable

data class DownloadTask(
    val gameName: String,
    val platformName: String,
    val files: List<FtpFile>,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.QUEUED
) : Serializable

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED
}

data class GameMetadata(
    val igdbId: Long = 0,
    val cleanName: String = "",
    val coverUrl: String? = null,
    val igdbName: String? = null,
    val cacheKey: String = ""  // Store the cache key for matching with SearchScreen
) : Serializable
