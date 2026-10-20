package com.musicpipe.player.data.remote

import com.musicpipe.player.data.remote.dto.AuthUserDto
import com.musicpipe.player.data.remote.dto.BulkAck
import com.musicpipe.player.data.remote.dto.CreateUserRequest
import com.musicpipe.player.data.remote.dto.DownloadAck
import com.musicpipe.player.data.remote.dto.DownloadRequest
import com.musicpipe.player.data.remote.dto.IdsRequest
import com.musicpipe.player.data.remote.dto.NetSearchResponse
import com.musicpipe.player.data.remote.dto.NoBody
import com.musicpipe.player.data.remote.dto.ShareCommitAck
import com.musicpipe.player.data.remote.dto.ShareCommitRequest
import com.musicpipe.player.data.remote.dto.ShareCreateRequest
import com.musicpipe.player.data.remote.dto.ShareLinkDto
import com.musicpipe.player.data.remote.dto.ShareListResponse
import com.musicpipe.player.data.remote.dto.ShareMergeAck
import com.musicpipe.player.data.remote.dto.ShareMergeRequest
import com.musicpipe.player.data.remote.dto.SharePreviewResponse
import com.musicpipe.player.data.remote.dto.ShareSongsResponse
import com.musicpipe.player.data.remote.dto.ShareSyncRequest
import com.musicpipe.player.data.remote.dto.ShareSyncResult
import com.musicpipe.player.data.remote.dto.SystemDto
import com.musicpipe.player.data.remote.dto.TaskListResponse
import com.musicpipe.player.data.remote.dto.TracksBulkAck
import com.musicpipe.player.data.remote.dto.LatestReleaseDto
import com.musicpipe.player.data.remote.dto.LoginDto
import com.musicpipe.player.data.remote.dto.LoginRequest
import com.musicpipe.player.data.remote.dto.LyricsDto
import com.musicpipe.player.data.remote.dto.OkDto
import com.musicpipe.player.data.remote.dto.PagedUsers
import com.musicpipe.player.data.remote.dto.RegisterRequest
import com.musicpipe.player.data.remote.dto.TrackDto
import com.musicpipe.player.data.remote.dto.TracksResponse
import com.musicpipe.player.data.remote.dto.UpdateUserRequest
import com.musicpipe.player.data.remote.dto.UserRowDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Router-merged backend (/api/v1/...). Bearer token comes from TokenInterceptor;
 * media/lyrics URLs may also be hit with ?token= by media3 / Coil (see AuthTokens).
 */
interface MusicPipeApi {

    // ---- accounts ----
    @POST("api/v1/accounts/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthUserDto>

    @POST("api/v1/accounts/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginDto>

    @POST("api/v1/accounts/logout")
    suspend fun logout(): Response<OkDto>

    @GET("api/v1/accounts/me")
    suspend fun me(): Response<AuthUserDto>

    // ---- library ----
    @GET("api/v1/library")
    suspend fun library(
        @Query("q") q: String?,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): Response<TracksResponse>

    @GET("api/v1/library/{id}")
    suspend fun track(@Path("id") id: String): Response<TrackDto>

    // ---- lyrics ----
    @GET("api/v1/lyrics/{id}")
    suspend fun lyrics(@Path("id") id: String): Response<LyricsDto>

    // ---- users (admin only; 403 forbidden otherwise) ----
    @GET("api/v1/users")
    suspend fun listUsers(@Query("page") page: Int, @Query("limit") limit: Int): Response<PagedUsers>

    @POST("api/v1/users")
    suspend fun createUser(@Body body: CreateUserRequest): Response<AuthUserDto>

    @PUT("api/v1/users/{id}")
    suspend fun updateUser(@Path("id") id: Long, @Body body: UpdateUserRequest): Response<OkDto>

    @DELETE("api/v1/users/{id}")
    suspend fun deleteUser(@Path("id") id: Long): Response<OkDto>

    // ---- app update ----
    @GET("api/v1/app/update/latest")
    suspend fun latestRelease(): Response<LatestReleaseDto>

    // ---- v1.2.0: net search / download queue (gateway.js) ----
    @GET("api/v1/search")
    suspend fun netSearch(
        @Query("q") q: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("platforms") platforms: String?
    ): Response<NetSearchResponse>

    @POST("api/v1/download")
    suspend fun enqueueDownload(@Body body: DownloadRequest): Response<DownloadAck>

    @GET("api/v1/tasks")
    suspend fun tasks(): Response<TaskListResponse>

    @POST("api/v1/tasks/bulk-cancel")
    suspend fun cancelTasks(@Body body: IdsRequest): Response<BulkAck>

    @POST("api/v1/tasks/bulk-delete")
    suspend fun deleteTasks(@Body body: IdsRequest): Response<BulkAck>

    @POST("api/v1/tracks/bulk-delete")
    suspend fun deleteTracks(@Body body: IdsRequest): Response<TracksBulkAck>

    // ---- v1.2.0: share import ----
    @POST("api/v1/shares")
    suspend fun createShare(@Body body: ShareCreateRequest): Response<ShareLinkDto>

    @GET("api/v1/shares")
    suspend fun shareList(): Response<ShareListResponse>

    @GET("api/v1/shares/{id}/preview")
    suspend fun sharePreview(@Path("id") id: String): Response<SharePreviewResponse>

    @POST("api/v1/shares/{id}/commit")
    suspend fun commitShare(@Path("id") id: String, @Body body: ShareCommitRequest): Response<ShareCommitAck>

    @POST("api/v1/shares/{id}/sync")
    suspend fun syncShare(@Path("id") id: String, @Body body: ShareSyncRequest): Response<ShareSyncResult>

    @DELETE("api/v1/shares/{id}")
    suspend fun deleteShare(@Path("id") id: String): Response<OkDto>

    @GET("api/v1/shares/{id}/songs")
    suspend fun shareSongs(@Path("id") id: String): Response<ShareSongsResponse>

    /** v1.6.0：把 source_id 歌单并入 :id 歌单，源歌单随后被删除（曲库音频不动）。 */
    @POST("api/v1/shares/{id}/merge")
    suspend fun mergeShare(@Path("id") id: String, @Body body: ShareMergeRequest): Response<ShareMergeAck>

    // ---- v1.2.0: system status / navidrome scan ----
    @GET("api/v1/system")
    suspend fun system(): Response<SystemDto>

    // NoBody serializes to {} — router Fastify 415s a content-type-less POST.
    @POST("api/v1/scan")
    suspend fun scan(@Body body: NoBody): Response<SystemDto>
}