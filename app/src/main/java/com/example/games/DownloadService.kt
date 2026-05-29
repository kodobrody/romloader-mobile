package com.example.games

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.games.data.DownloadStatus
import com.example.games.data.DownloadTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "ACTION_START"
        
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startForeground(NOTIFICATION_ID, createNotification("Starting downloads..."))
            observeQueue()
        }
        return START_STICKY
    }

    private fun observeQueue() {
        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            val ftpRepository = (application as GamesApplication).ftpRepository
            ftpRepository.downloadQueue.collectLatest { queue ->
                val activeTasks = queue.filter { 
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED 
                }
                
                if (activeTasks.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val currentTask = activeTasks.find { it.status == DownloadStatus.DOWNLOADING } 
                        ?: activeTasks.firstOrNull()
                    
                    if (currentTask != null) {
                        val progress = (currentTask.progress * 100).toInt()
                        val content = if (currentTask.status == DownloadStatus.DOWNLOADING) {
                            "Downloading ${currentTask.gameName} ($progress%)"
                        } else {
                            "Queued: ${currentTask.gameName}"
                        }
                        updateNotification(content, progress)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Int = -1): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading Games")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(content: String, progress: Int) {
        val notification = createNotification(content, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
