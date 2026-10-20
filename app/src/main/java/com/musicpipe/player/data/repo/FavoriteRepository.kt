package com.musicpipe.player.data.repo

import com.musicpipe.player.data.local.FavoriteDao
import com.musicpipe.player.data.local.FavoriteTrack
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.remote.AuthTokens
import com.musicpipe.player.util.SongMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 「我喜欢」local favourites (requirement 4). Fully offline-capable, Room-backed;
 * the router gateway has no star/unstar endpoint so there is no server sync any more.
 * Only gateway-relative paths are cached; ?token= is re-added whenever a Song is rebuilt.
 */
class FavoriteRepository(private val dao: FavoriteDao) {

    fun observeAll(): Flow<List<FavoriteTrack>> = dao.observeAll()

    fun observeIsFavorite(songId: String): Flow<Boolean> = dao.observeIsFavorite(songId)

    fun observeFavoriteIds(): Flow<Set<String>> = dao.observeIds().map { it.toSet() }

    suspend fun add(song: Song) {
        dao.upsert(
            FavoriteTrack(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                coverUrl = SongMapper.coverPath(song.id),
                platform = song.platform,
                streamPath = SongMapper.streamPath(song.id),
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(songId: String) {
        dao.deleteBySongId(songId)
    }

    suspend fun toggle(song: Song, currentlyFavorite: Boolean): Boolean =
        if (currentlyFavorite) {
            remove(song.id)
            false
        } else {
            add(song)
            true
        }

    /** Cached row -> playable Song, re-signed with the current in-memory token. */
    fun toSong(track: FavoriteTrack): Song = SongMapper.fromCached(
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
