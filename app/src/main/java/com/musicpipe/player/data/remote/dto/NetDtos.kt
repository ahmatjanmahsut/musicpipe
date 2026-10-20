package com.musicpipe.player.data.remote.dto

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * DTOs for the remaining /api/v1 gateway surface brought into the app in v1.2.0:
 * net search, download queue, share import, system status / scan.
 * Raw gateway song objects are kept as JsonObject so download can round-trip
 * them losslessly (the downloader expects the exact search payload shape).
 */

// ---- net search (GET /api/v1/search) ----
data class NetPlatformResult(
    val platform: String = "",
    val name: String = "",
    val ok: Boolean = false,
    val error: String? = null,
    val count: Int = 0,
    val songs: List<JsonObject> = emptyList()
)

data class NetSearchResponse(
    val query: String = "",
    val page: Int = 1,
    val results: List<NetPlatformResult> = emptyList()
)

// ---- download & tasks ----
data class DownloadRequest(val songs: List<JsonObject>, val quality: String)

data class DownloadAck(
    val ok: Boolean = false,
    val taskIds: List<String> = emptyList(),
    val quality: String = ""
)

data class TaskDto(
    val id: String = "",
    val status: String = "",
    val progress: Int = 0,
    val message: String? = null,
    val error: String? = null,
    @SerializedName("track_id") val trackId: String? = null,
    val quality: String? = null,
    val songName: String? = null,
    val songSource: String? = null
)

data class TaskListResponse(val list: List<TaskDto> = emptyList())

data class IdsRequest(val ids: List<String>)

data class BulkAck(
    val ok: Boolean = false,
    val cancelled: Int = 0,
    val deleted: Int = 0
)

data class TracksBulkAck(
    val ok: Boolean = false,
    val deleted: Int = 0,
    val errors: List<String> = emptyList()
)

// ---- share import ----
data class ShareCreateRequest(
    val url: String,
    val quality: String? = null,
    @SerializedName("share_user") val shareUser: String? = null
)

data class ShareLinkDto(
    val id: String = "",
    val title: String? = null,
    @SerializedName("share_user") val shareUser: String? = null,
    val creator: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    val platform: String? = null,
    val count: Int = 0,
    val status: String? = null
)

data class ShareSongDto(
    val no: Int = 0,
    @SerializedName("ext_key") val extKey: String = "",
    val title: String? = null,
    val artist: String? = null,
    val state: String? = null,
    @SerializedName("in_lib") val inLib: Int = 0,
    @SerializedName("task_id") val taskId: String? = null,
    @SerializedName("track_id") val trackId: String? = null,
    @SerializedName("added_at") val addedAt: Long? = null
)

data class ShareLinkDetail(
    val id: String = "",
    val title: String? = null,
    @SerializedName("share_user") val shareUser: String? = null,
    val creator: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    val platform: String? = null,
    val status: String? = null,
    val quality: String? = null,
    @SerializedName("song_count") val songCount: Int = 0,
    @SerializedName("last_result") val lastResult: String? = null
)

data class SharePreviewResponse(
    val link: ShareLinkDetail? = null,
    val songs: List<ShareSongDto> = emptyList()
)

data class ShareCommitRequest(
    val quality: String? = null,
    val keys: List<String>? = null,
    @SerializedName("share_user") val shareUser: String? = null
)

data class ShareCommitAck(
    val ok: Boolean = false,
    val queued: Int = 0,
    val quality: String? = null,
    @SerializedName("share_user") val shareUser: String? = null
)

data class ShareSyncRequest(val force: Boolean = false)

data class ShareSyncResult(
    val ok: Boolean = false,
    val added: Int = 0,
    val exists: Int = 0,
    val total: Int = 0,
    val title: String? = null,
    val error: String? = null,
    @SerializedName("fail_count") val failCount: Int = 0,
    val status: String? = null,
    @SerializedName("share_user") val shareUser: String? = null
)

data class ShareListItem(
    val id: String = "",
    val url: String? = null,
    val platform: String? = null,
    val title: String? = null,
    @SerializedName("share_user") val shareUser: String? = null,
    val quality: String? = null,
    val status: String? = null,
    @SerializedName("song_count") val songCount: Int = 0,
    @SerializedName("in_library") val inLibrary: Int = 0,
    @SerializedName("added_at") val addedAt: Long? = null,
    @SerializedName("last_sync_at") val lastSyncAt: Long? = null,
    @SerializedName("last_result") val lastResult: String? = null
)

data class ShareListResponse(val list: List<ShareListItem> = emptyList())

/** v1.6.0：POST /api/v1/shares/{id}/merge 的请求体（:id 是保留的目标歌单）。 */
data class ShareMergeRequest(@SerializedName("source_id") val sourceId: String)

/**
 * 合并结果。inserted = 目标里新增的曲目数；skipped = 目标本来就有、被跳过的曲目数；
 * inherited = 目标那条 track_id 为空而源条目已入库、于是把 track_id 继承过来的条数。
 */
data class ShareMergeAck(
    val ok: Boolean = false,
    @SerializedName("target_id") val targetId: String = "",
    @SerializedName("source_id") val sourceId: String = "",
    val inserted: Int = 0,
    val skipped: Int = 0,
    val inherited: Int = 0,
    val total: Int = 0
)

data class ShareSongsResponse(val list: List<ShareSongDto> = emptyList())

// ---- system ----
data class SystemCountsDto(val tracks: Int = 0, val queue: Int = 0, val imported: Int = 0)

data class SystemDto(
    val ok: Boolean = false,
    val api: String? = null,
    val counts: SystemCountsDto? = null,
    val scan: JsonObject? = null
)

/** Serializes to {} — the router Fastify has no empty-POST content-type tolerance hook. */
class NoBody