package com.example.games

import android.app.Application
import com.example.games.data.MetadataRepository

class GamesApplication : Application() {
    lateinit var ftpRepository: FtpRepository
    lateinit var metadataRepository: MetadataRepository

    override fun onCreate() {
        super.onCreate()
        ftpRepository = FtpRepository(this)
        metadataRepository = MetadataRepository(this)
    }
}
