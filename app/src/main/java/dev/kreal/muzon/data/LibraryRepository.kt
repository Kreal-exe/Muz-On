package dev.kreal.muzon.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

private const val TAG = "Library"

class LibraryRepository(private val context: Context, private val prefs: Prefs) {
    private val state = MutableStateFlow<Library?>(null)
    val library: StateFlow<Library?> = state
    private val mutex = Mutex()
    private var loadedStamp: String? = null

    private class DeviceFile(
        val id: Long,
        val relative: String,
        val path: String?,
        val title: String,
        val artist: String,
        val albumArtist: String,
        val album: String,
        val track: Int,
        val year: Int,
        val duration: Long,
        val added: Long,
    ) {
        val uri: String get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
    }

    private class DbFiles(val db: Uri, val stamp: String, val order: Uri?)

    /** Reads again when the database changed or [force] is set; otherwise keeps what is loaded. */
    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = runCatching { findDatabase() }.onFailure { Log.w(TAG, "No folder access", it) }.getOrNull()
            val stamp = db?.stamp ?: "device"
            if (!force && stamp == loadedStamp && state.value != null) return@withLock
            val files = queryDevice()
            val library = if (db != null) {
                try {
                    fromDatabase(db, files)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot read the database", e)
                    fromDevice(files, e.message ?: e.javaClass.simpleName)
                }
            } else {
                fromDevice(files, null)
            }
            library.prepare()
            loadedStamp = stamp
            state.value = library
        }
    }

    private fun fromDatabase(db: DbFiles, files: List<DeviceFile>): Library {
        val bytes = context.contentResolver.openInputStream(db.db)!!.use { it.readBytes() }
        val parsed = MusicDb.parse(bytes)
        val resolver = PathResolver(files.map { PathResolver.Entry(it.relative, it.title, it) })
        val tracks = parsed.tracks.map { track ->
            val file = resolver.resolve(track.sourcePath, track.title)
            if (file == null) track else track.copy(uri = file.uri, filePath = file.path)
        }
        val order = db.order?.let { uri ->
            runCatching { context.contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() } }.getOrNull()
        }
        val playlists = if (order != null) MusicDb.applyOrder(parsed.playlists, order) else parsed.playlists
        return Library(LibrarySource.MusicDatabase, tracks, playlists, db.stamp.substringBefore('/').toLongOrNull() ?: 0L)
    }

    private fun fromDevice(files: List<DeviceFile>, failure: String?): Library {
        val tracks = files.map { file ->
            var artist = file.artist.takeUnless(::isUnknown).orEmpty()
            var albumArtist = file.albumArtist.takeUnless(::isUnknown).orEmpty()
            var title = file.title
            if (artist.isEmpty() && albumArtist.isEmpty() && file.path != null) {
                runCatching {
                    val tag = AudioFileIO.read(File(file.path)).tag
                    artist = tag.getFirst(FieldKey.ARTIST).orEmpty()
                    albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).orEmpty()
                    tag.getFirst(FieldKey.TITLE).takeIf { it.isNotBlank() }?.let { title = it }
                }
            }
            Track(
                id = "ms${file.id}",
                title = title,
                artist = artist,
                albumArtist = albumArtist,
                album = file.album.takeUnless(::isUnknown).orEmpty(),
                genre = "",
                trackNumber = file.track % 1000,
                discNumber = file.track / 1000,
                year = file.year,
                durationMs = file.duration,
                dateAddedMs = file.added * 1000,
                sourcePath = file.relative,
                uri = file.uri,
                filePath = file.path,
            )
        }
        val byName = PathResolver(tracks.map { PathResolver.Entry(it.sourcePath, it.title, it.id) })
        return Library(LibrarySource.DeviceFiles, tracks, readM3uPlaylists(byName), failure = failure)
    }

    private fun isUnknown(s: String) = s.isBlank() || s == "<unknown>"

    private fun queryDevice(): List<DeviceFile> {
        val out = ArrayList<DeviceFile>(4000)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("Music/%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val folder = c.getString(1).orEmpty().removePrefix("Music/")
                out += DeviceFile(
                    id = c.getLong(0),
                    relative = folder + c.getString(2).orEmpty(),
                    path = c.getString(3),
                    title = c.getString(4).orEmpty(),
                    artist = c.getString(5).orEmpty(),
                    albumArtist = c.getString(6).orEmpty(),
                    album = c.getString(7).orEmpty(),
                    track = c.getInt(8),
                    year = c.getInt(9),
                    duration = c.getLong(10),
                    added = c.getLong(11),
                )
            }
        }
        return out
    }

    private fun treeRoot(): Pair<Uri, String>? {
        val uri = prefs.treeUri?.let(Uri::parse) ?: return null
        return uri to DocumentsContract.getTreeDocumentId(uri)
    }

    private class Doc(val id: String, val name: String, val modified: Long, val size: Long)

    private fun children(tree: Uri, parent: String): List<Doc> {
        val out = ArrayList<Doc>()
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent),
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) out += Doc(c.getString(0), c.getString(1).orEmpty(), c.getLong(2), c.getLong(3))
        }
        return out
    }

    private fun findDatabase(): DbFiles? {
        val (tree, root) = treeRoot() ?: return null
        val folders = listOf(
            listOf("Music", "Music Library.musiclibrary"),
            listOf("Music Library.musiclibrary"),
            emptyList(),
        )
        for (path in folders) {
            var parent: String? = root
            for (name in path) {
                parent = parent?.let { p -> children(tree, p).firstOrNull { it.name == name }?.id }
            }
            val id = parent ?: continue
            val list = children(tree, id)
            val db = list.firstOrNull { it.name == "Library.musicdb" } ?: continue
            val order = list.firstOrNull { it.name == "playlist-order.txt" }
            return DbFiles(
                DocumentsContract.buildDocumentUriUsingTree(tree, db.id),
                "${db.modified}/${db.size}/${order?.modified}/${order?.size}",
                order?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) },
            )
        }
        return null
    }

    private fun readM3uPlaylists(byName: PathResolver<String>): List<Playlist> {
        val (tree, root) = treeRoot() ?: return emptyList()
        return runCatching {
            children(tree, root).filter { it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true) }
                .sortedBy { it.name.lowercase() }
                .map { doc ->
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, doc.id)
                    val lines = context.contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readLines() }
                    val ids = lines.map { it.trim().removePrefix("﻿") }.filter { it.isNotEmpty() && !it.startsWith("#") }
                        .mapNotNull { byName.resolve(it, "") }
                    Playlist(doc.id, doc.name.substringBeforeLast('.'), ids)
                }
        }.getOrDefault(emptyList())
    }
}
