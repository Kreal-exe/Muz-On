package dev.kreal.muzon.data

import androidx.compose.runtime.Immutable

/** One entry of the library. [uri] is null when the file is not on this device. */
@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val genre: String,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val durationMs: Long,
    val dateAddedMs: Long,
    val sourcePath: String,
    val uri: String? = null,
    val filePath: String? = null,
    /** Marked as a favourite (the heart). */
    val favorite: Boolean = false,
    val playCount: Int = 0,
) {
    /** The artist the track is listed under: album artist, else artist, as Music does. */
    val shownArtist: String get() = albumArtist.ifBlank { artist }.trim()
    val artistKey: String get() = shownArtist.lowercase()
    val albumKey: String get() = album.trim().lowercase() + "\u0000" + artistKey
}

@Immutable
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    /** True when [trackIds] comes from the order file, the exact order shown by Music. */
    val hasExactOrder: Boolean = false,
    /** The rules of a smart playlist, to follow changes made on this phone. */
    val rules: ByteArray? = null,
    /** Made in this app and not yet in Music on the Mac. */
    val own: Boolean = false,
    /** A playlist of Music on the Mac. */
    val mac: Boolean = false,
    /** Changes made on the phone are waiting for tools/sync-library.sh to reach the Mac. */
    val pending: Boolean = false,
) {
    val smart: Boolean get() = rules != null

    /**
     * Songs can be added, removed and reordered here: the app's own playlists, and the regular (not
     * smart) playlists of Music, whose changes go to the Mac with the next sync. A smart playlist
     * follows its rules only.
     */
    val editable: Boolean get() = own || (mac && !smart)

    override fun equals(other: Any?) = other is Playlist && other.id == id && other.trackIds == trackIds && other.name == name && other.pending == pending
    override fun hashCode() = id.hashCode() * 31 + trackIds.hashCode()
}

@Immutable
data class Album(val key: String, val title: String, val artist: String, val year: Int, val tracks: List<Track>)

@Immutable
data class Artist(val key: String, val name: String, val albums: List<Album>, val tracks: List<Track>)

enum class LibrarySource { MusicDatabase, DeviceFiles }

@Immutable
data class Library(
    val source: LibrarySource,
    val tracks: List<Track>,
    val playlists: List<Playlist>,
    val databaseTime: Long = 0L,
    val failure: String? = null,
    /** Ids of playlists made here that now live in Music, to the id Music gave them. */
    val aliases: Map<String, String> = emptyMap(),
) {
    fun playlist(id: String): Playlist? = playlists.firstOrNull { it.id == id } ?: aliases[id]?.let { a -> playlists.firstOrNull { it.id == a } }

    val byId: Map<String, Track> = tracks.associateBy { it.id }
    val songs: List<Track> by lazy { tracks.sortedWith(Sorting.byTitle) }

    val albums: List<Album> by lazy {
        tracks.groupBy { it.albumKey }.map { (key, list) ->
            val sorted = list.sortedWith(Sorting.albumOrder)
            Album(
                key = key,
                title = sorted.first().album.trim().ifBlank { "—" },
                artist = sorted.first().shownArtist,
                year = sorted.maxOf { it.year },
                tracks = sorted,
            )
        }.sortedWith(compareBy(Sorting.collator) { it.title })
    }
    val albumsByKey: Map<String, Album> by lazy { albums.associateBy { it.key } }

    /** Same list as Music: one artist per album artist, whatever the case, never one per guest. */
    val artists: List<Artist> by lazy {
        tracks.groupBy { it.artistKey }.map { (key, list) ->
            val name = list.groupingBy { it.shownArtist }.eachCount().maxBy { it.value }.key
            val own = albums.filter { it.tracks.first().artistKey == key }.sortedByDescending { it.year }
            Artist(key, name.ifBlank { "—" }, own, list.sortedWith(Sorting.byTitle))
        }.sortedWith(compareBy(Sorting.collator) { it.name })
    }
    val artistsByKey: Map<String, Artist> by lazy { artists.associateBy { it.key } }

    val genres: List<Pair<String, List<Track>>> by lazy {
        // "Hip-Hop", "hip-hop" and "Hip-hop" are one genre, under its most common spelling.
        tracks.filter { it.genre.isNotBlank() }.groupBy { Search.normalize(it.genre) }
            .map { (_, list) -> list.groupingBy { it.genre.trim() }.eachCount().maxBy { it.value }.key to list.sortedWith(Sorting.byTitle) }
            .sortedWith(compareBy(Sorting.collator) { it.first })
    }

    private val newestFirst: List<Track> by lazy { tracks.sortedByDescending { it.dateAddedMs } }

    /** The 100 newest tracks. */
    val recentlyAdded: List<Track> by lazy { newestFirst.take(100) }

    /** Tracks added in the last [days] days; 0 means the 100 newest. */
    fun recentlyAdded(days: Int): List<Track> {
        if (days <= 0) return recentlyAdded
        val since = System.currentTimeMillis() - days * 86_400_000L
        return newestFirst.takeWhile { it.dateAddedMs >= since }
    }

    /** Folder (relative to the music folder, "" for its root) to the tracks directly in it. */
    val folders: Map<String, List<Track>> by lazy {
        tracks.filter { it.filePath != null }.groupBy { t ->
            val path = t.filePath!!
            val inMusic = path.substringAfter("/Music/", path)
            inMusic.substringBeforeLast('/', "")
        }.mapValues { (_, list) -> list.sortedBy { it.filePath!!.substringAfterLast('/').lowercase() } }
    }

    /** Subfolders directly under [folder], each with how many tracks it holds in all. */
    fun subfolders(folder: String): List<Pair<String, Int>> {
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        return folders.entries.filter { it.key.startsWith(prefix) && it.key.length > prefix.length }
            .groupBy { prefix + it.key.removePrefix(prefix).substringBefore('/') }
            .map { (name, entries) -> name to entries.sumOf { it.value.size } }
            .sortedWith(compareBy(Sorting.collator) { it.first.substringAfterLast('/') })
    }

    private val playlistTracks: Map<String, List<Track>> by lazy {
        playlists.associate { p -> p.id to p.trackIds.mapNotNull(byId::get) }
    }

    fun tracksOf(playlist: Playlist): List<Track> = playlistTracks[playlist.id].orEmpty()

    /** Each track's title, artists, album and genre, prepared once for [Search]. */
    private val searchTexts: Map<String, String> by lazy { tracks.associate { it.id to Search.haystack(it) } }

    fun searchText(track: Track): String = searchTexts[track.id] ?: Search.haystack(track)

    /** Fills every derived list, so no screen sorts thousands of tracks while it opens. */
    fun prepare() {
        songs
        albums
        albumsByKey
        artists
        artistsByKey
        genres
        recentlyAdded
        playlistTracks
        searchTexts
    }
}

object Sorting {
    val collator: java.text.Collator = java.text.Collator.getInstance().apply {
        strength = java.text.Collator.PRIMARY
    }
    val byTitle: Comparator<Track> = compareBy(collator) { it.title.trim() }
    val albumOrder: Comparator<Track> =
        compareBy<Track>({ it.discNumber }, { it.trackNumber }).thenComparing(byTitle)
    private val byArtist: Comparator<Track> = compareBy(collator) { it.shownArtist }
    private val byAlbum: Comparator<Track> = compareBy(collator) { it.album.trim() }

    fun apply(mode: PlaylistSort, reversed: Boolean, tracks: List<Track>): List<Track> {
        val sorted = when (mode) {
            PlaylistSort.AsInMusic -> tracks
            PlaylistSort.Artist -> tracks.sortedWith(byArtist.thenComparing(byAlbum).thenComparing(albumOrder))
            PlaylistSort.Album -> tracks.sortedWith(byAlbum.thenComparing(albumOrder))
            PlaylistSort.Title -> tracks.sortedWith(byTitle)
            PlaylistSort.Genre -> tracks.sortedWith(
                compareBy<Track, String>(collator) { it.genre.trim() }.thenComparing(byArtist).thenComparing(byAlbum),
            )
            PlaylistSort.DateAdded -> tracks.sortedByDescending { it.dateAddedMs }
            PlaylistSort.Duration -> tracks.sortedBy { it.durationMs }
            PlaylistSort.Year -> tracks.sortedWith(compareByDescending<Track> { it.year }.thenComparing(byArtist).thenComparing(byAlbum).thenComparing(albumOrder))
        }
        return if (reversed) sorted.asReversed() else sorted
    }
}

enum class PlaylistSort { AsInMusic, Artist, Album, Title, Genre, DateAdded, Duration, Year }
