package com.musicpipe.player.ui.home

import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.remote.dto.ShareListItem
import com.musicpipe.player.data.remote.dto.ShareSongDto

/** One share playlist rendered as a home section (v1.4.0 requirement 3). */
data class HomeCollection(
    val id: String,
    val title: String,
    /** Playlist items already present in the router library, ordered by track no. */
    val songs: List<Song>,
    /** Items not (yet) linked to a library track: pending download / unknown. */
    val missing: Int
)

/**
 * Pure mapping logic for home sections (unit-testable without Android):
 * share playlists -> ordered in-library songs + count of not-yet-imported items.
 *
 * The gateway links most rows through track_id, but rows whose state is "exists"
 * (detected as already present during the share scan) carry an empty track_id while
 * still being in the library. Those are recovered with a normalized title/artist
 * match so a share section shows everything that is really playable.
 */
object HomeSections {

    /** Section name = share link title, falling back to the raw URL. */
    fun collectionTitle(share: ShareListItem): String =
        share.title?.takeIf { it.isNotBlank() }
            ?: share.url?.takeIf { it.isNotBlank() }
            ?: "分享歌单"

    /** Lower-cased, whitespace-free key used for the title fallback match. */
    fun matchKey(title: String?, artist: String?): String {
        fun norm(s: String?) = (s ?: "").lowercase().replace(Regex("\\s+"), "")
        return norm(title) + "|" + norm(artist)
    }

    fun buildCollections(
        shares: List<ShareListItem>,
        songsByShare: Map<String, List<ShareSongDto>>,
        library: List<Song>
    ): List<HomeCollection> {
        val byId = library.associateBy { it.id }
        val byKey: Map<String, List<Song>> = library.groupBy { matchKey(it.title, it.artist) }
        return shares.map { share ->
            val items = (songsByShare[share.id] ?: emptyList()).sortedBy { it.no }
            val resolved = items.mapNotNull { item -> resolveSong(item, byId, byKey) }
            HomeCollection(
                id = share.id,
                title = collectionTitle(share),
                songs = resolved.distinctBy { it.id },
                missing = items.size - resolved.size
            )
        }
    }

    /**
     * Direct track_id link first; otherwise (rows saved as "exists" without a link)
     * match title+artist, then title alone when unambiguous.
     */
    private fun resolveSong(
        item: ShareSongDto,
        byId: Map<String, Song>,
        byKey: Map<String, List<Song>>
    ): Song? {
        val id = item.trackId?.takeIf { it.isNotBlank() }
        if (id != null) return byId[id]
        byKey[matchKey(item.title, item.artist)]?.singleOrNull()?.let { return it }
        val titleKey = (item.title ?: "").lowercase().replace(Regex("\\s+"), "")
        if (titleKey.isBlank()) return null
        return byKey.entries
            .filter { (key, _) -> key.substringBefore('|') == titleKey }
            .flatMap { it.value }
            .distinctBy { it.id }
            .singleOrNull()
    }
}
