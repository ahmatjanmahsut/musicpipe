package com.musicpipe.player.data.repo

import com.musicpipe.player.data.local.HistoryDao
import com.musicpipe.player.data.local.HistoryTrack
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.remote.AuthTokens
import com.musicpipe.player.util.SongMapper
import kotlinx.coroutines.flow.Flow

/** Local playback history (requirement 2). Survives restarts (Room). */
class HistoryRepository(private val dao: HistoryDao) {

    fun observe(): Flow<List<HistoryTrack>> = dao.observeAll()

    /** Record a play: same songId -> refresh metadata + playedAt (top of list); else insert. */
    suspend fun record(song: Song) {
        val now = System.currentTimeMillis()
        val cover = SongMapper.coverPath(song.id)
        val stream = SongMapper.streamPath(song.id)
        val existing = dao.findBySongId(song.id)
        if (existing != null) {
            dao.update(
                existing.copy(
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    durationMs = song.durationMs,
                    coverUrl = cover,
                    platform = song.platform,
                    streamPath = stream,
                    playedAt = now
                )
            )
        } else {
            dao.insert(
                HistoryTrack(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    durationMs = song.durationMs,
                    coverUrl = cover,
                    platform = song.platform,
                    streamPath = stream,
                    playedAt = now
                )
            )
        }
    }

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.clear()

    /** Cached row -> playable Song, re-signed with the current in-memory token. */
    fun toSong(track: HistoryTrack): Song = SongMapper.fromCached(
        songId = track.songId,
        title = track.title,
        artist = track.artist,
        album = track.album,
        durationMs = track.durationMs,
        rawCover = track.coverUrl,
        rawStream = track.streamPath,
        platform = track.platform,
        token = AuthTokens.get()
    )
}
