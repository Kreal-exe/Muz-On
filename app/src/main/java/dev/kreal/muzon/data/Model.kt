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
) {
    override fun equals(other: Any?) = other is Playlist && other.id == id && other.trackIds == trackIds && other.name == name
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
) {
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
        tracks.filter { it.genre.isNotBlank() }.groupBy { it.genre.trim() }
            .map { it.key to it.value.sortedWith(Sorting.byTitle) }
            .sortedWith(compareBy(Sorting.collator) { it.first })
    }

    val recentlyAdded: List<Track> by lazy { tracks.sortedByDescending { it.dateAddedMs }.take(100) }

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
        }
        return if (reversed) sorted.asReversed() else sorted
    }
}

enum class PlaylistSort { AsInMusic, Artist, Album, Title, Genre, DateAdded, Duration }
