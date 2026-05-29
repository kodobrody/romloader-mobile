package com.example.games.data

import retrofit2.http.*
import okhttp3.RequestBody

interface IgdbApi {
    @POST("games")
    suspend fun searchGames(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") accessToken: String,
        @Body body: RequestBody
    ): List<IgdbGame>

    @POST("platforms")
    suspend fun getPlatforms(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") accessToken: String,
        @Body body: RequestBody
    ): List<IgdbPlatform>
}

data class IgdbPlatform(
    val id: Long,
    val name: String,
    val platform_logo: IgdbPlatformLogo? = null
)

data class IgdbPlatformLogo(
    val id: Long = 0,
    val url: String = "",
    val image_id: String? = null,
    val alpha_channel: Boolean? = null
)

data class IgdbGame(
    val id: Long,
    val name: String,
    val cover: IgdbCover? = null,
    val platforms: List<Int>? = null
)

data class IgdbCover(
    val id: Long = 0,
    val url: String = "",
    val image_id: String? = null,
    val alpha_channel: Boolean? = null
)

interface TwitchApi {
    @POST("https://id.twitch.tv/oauth2/token")
    suspend fun getAccessToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("grant_type") grantType: String = "client_credentials"
    ): TwitchTokenResponse
}

data class TwitchTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val token_type: String
)
