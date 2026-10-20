package com.musicpipe.player.data.repo

import com.google.gson.JsonObject
import com.musicpipe.player.data.remote.MusicPipeApi
import com.musicpipe.player.data.remote.dto.BulkAck
import com.musicpipe.player.data.remote.dto.DownloadAck
import com.musicpipe.player.data.remote.dto.DownloadRequest
import com.musicpipe.player.data.remote.dto.IdsRequest
import com.musicpipe.player.data.remote.dto.NetSearchResponse
import com.musicpipe.player.data.remote.dto.NoBody
import com.musicpipe.player.data.remote.dto.ShareCommitAck
import com.musicpipe.player.data.remote.dto.ShareCommitRequest
import com.musicpipe.player.data.remote.dto.ShareCreateRequest
import com.musicpipe.player.data.remote.dto.ShareLinkDto
import com.musicpipe.player.data.remote.dto.ShareListItem
import com.musicpipe.player.data.remote.dto.ShareMergeAck
import com.musicpipe.player.data.remote.dto.ShareMergeRequest
import com.musicpipe.player.data.remote.dto.SharePreviewResponse
import com.musicpipe.player.data.remote.dto.ShareSongDto
import com.musicpipe.player.data.remote.dto.ShareSyncRequest
import com.musicpipe.player.data.remote.dto.ShareSyncResult
import com.musicpipe.player.data.remote.dto.SystemDto
import com.musicpipe.player.data.remote.dto.TaskDto
import com.musicpipe.player.data.remote.dto.TracksBulkAck
import com.musicpipe.player.data.remote.unwrap

/**
 * v1.2.0 gateway features: net search, download queue, share import, system status/scan.
 * All calls go through the shared Bearer-authenticated MusicPipeApi.
 */
class NetRepository(private val api: MusicPipeApi) {

    suspend fun netSearch(
        q: String,
        page: Int = 1,
        limit: Int = 20,
        platforms: List<String> = emptyList()
    ): NetSearchResponse = api.netSearch(
        q.trim(),
        page.coerceAtLeast(1),
        limit.coerceIn(1, 50),
        platforms.joinToString(",").ifBlank { null }
    ).unwrap()

    /**
     * v1.6.0 需求2：入队下载。
     *
     * 连续下载时闪退/重复下单的两个来源都在这里：
     * 1. 客户端侧：请求超时（服务端其实已经建好任务）时旧实现直接抛异常，用户重试 → 队列里
     *    同一首出现两次，并发下载把路由器打满。这里对 io 类异常做**一次**退避重试，并在重试
     *    前先查一次任务队列，若已存在同名且处于活动状态的任务就直接返回"已入队"，不再下单。
     * 2. 服务端侧：同一首在同一时间窗口内被多次入队时，网关会复用已有任务（见
     *    gateway.js 的 download 端点），所以重复入队不会真的重复下载，但返回的仍是合法 ack。
     */
    suspend fun download(songs: List<JsonObject>, quality: String): DownloadAck {
        val names = songs.mapNotNull { it.get("name")?.asString }.toSet()
        val first = try {
            api.enqueueDownload(DownloadRequest(songs, quality)).unwrap()
        } catch (io: java.io.IOException) {
            // 超时/连接被重置：服务端可能已经建好任务，先确认再决定是否重发。
            val already = runCatching {
                tasks().filter { it.songName?.let { n -> n in names } == true && it.status in ACTIVE_STATUSES }
            }.getOrDefault(emptyList())
            if (already.isNotEmpty()) {
                return DownloadAck(ok = true, taskIds = already.mapNotNull { it.id }, quality = quality)
            }
            kotlinx.coroutines.delay(800)
            api.enqueueDownload(DownloadRequest(songs, quality)).unwrap()
        }
        return first
    }

    private companion object {
        val ACTIVE_STATUSES = setOf("pending", "queued", "downloading", "organizing", "tagging")
    }

    suspend fun tasks(): List<TaskDto> = api.tasks().unwrap().list

    suspend fun cancelTasks(ids: List<String>): BulkAck = api.cancelTasks(IdsRequest(ids)).unwrap()

    suspend fun deleteTasks(ids: List<String>): BulkAck = api.deleteTasks(IdsRequest(ids)).unwrap()

    suspend fun deleteTracks(ids: List<String>): TracksBulkAck =
        api.deleteTracks(IdsRequest(ids)).unwrap()

    suspend fun createShare(url: String, quality: String? = null): ShareLinkDto =
        api.createShare(ShareCreateRequest(url.trim(), quality)).unwrap()

    suspend fun shareList(): List<ShareListItem> = api.shareList().unwrap().list

    suspend fun sharePreview(id: String): SharePreviewResponse = api.sharePreview(id).unwrap()

    suspend fun commitShare(id: String, keys: List<String>?, quality: String?): ShareCommitAck =
        api.commitShare(id, ShareCommitRequest(quality, keys?.takeIf { it.isNotEmpty() })).unwrap()

    suspend fun syncShare(id: String, force: Boolean = false): ShareSyncResult =
        api.syncShare(id, ShareSyncRequest(force)).unwrap()

    suspend fun deleteShare(id: String) {
        api.deleteShare(id).unwrap()
    }

    /** v1.6.0：把 sourceId 歌单并入 targetId 歌单，源歌单在服务端随后被删除。 */
    suspend fun mergeShare(targetId: String, sourceId: String): ShareMergeAck =
        api.mergeShare(targetId, ShareMergeRequest(sourceId)).unwrap()

    suspend fun shareSongs(id: String): List<ShareSongDto> = api.shareSongs(id).unwrap().list

    suspend fun system(): SystemDto = api.system().unwrap()

    suspend fun scan() {
        api.scan(NoBody()).unwrap()
    }
}