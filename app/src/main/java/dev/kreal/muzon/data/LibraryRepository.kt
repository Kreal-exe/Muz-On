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

/** The phone's own Favourites playlist, shown when there is no Mac library. */
const val FAVOURITES_ID = "phone-favourites"

/** Folder in the music folder for playlist files. */
const val PLAYLIST_FOLDER = "Playlists"

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

    /** The library as read, before the hearts set on this phone are laid over it. */
    private var base: Library? = null

    // ---- Playlists made in the app, kept in its own folder beside the list of hearts.

    private val ownFile get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "playlists.json")
    private var own: List<Playlist>? = null

    private fun ownPlaylists(): List<Playlist> = own ?: runCatching {
        val array = org.json.JSONArray(ownFile.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val ids = o.getJSONArray("tracks")
            Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getString(it) }, hasExactOrder = true, own = true)
        }
    }.getOrDefault(emptyList()).also { own = it }

    private fun saveOwn(list: List<Playlist>) {
        own = list
        val array = org.json.JSONArray()
        list.forEach { pl ->
            array.put(org.json.JSONObject().put("id", pl.id).put("name", pl.name).put("tracks", org.json.JSONArray(pl.trackIds)))
        }
        val tmp = File(ownFile.path + ".tmp")
        tmp.writeText(array.toString(1))
        tmp.renameTo(ownFile)
    }

    /** The library as shown: hearts laid over, then the app's own playlists after those of Music. */
    private fun present(read: Library): Library {
        val shown = withoutMissing(withoutShort(withFavorites(read)))
        // Without a Mac library there is no Favourites playlist to follow; the phone keeps one of its
        // own, holding every song with a heart.
        val favourites = if (shown.source == LibrarySource.MusicDatabase) emptyList() else listOf(
            Playlist(FAVOURITES_ID, context.getString(dev.kreal.muzon.R.string.favourites), shown.tracks.filter { it.favorite }.map { it.id }, hasExactOrder = true),
        )
        val mine = ownPlaylists()
        if (mine.isEmpty() && favourites.isEmpty()) return shown
        return Library(shown.source, shown.tracks, favourites + shown.playlists + mine, shown.databaseTime, shown.failure).also { it.prepare() }
    }

    /**
     * Leaves out tracks shorter than the setting asks, ringtones and voice notes in the music folder.
     * Only the library as shown: hearts set on such a track are still written down for the Mac.
     * A track whose length is unknown stays.
     */
    private fun withoutShort(read: Library): Library {
        val min = prefs.minTrackSeconds * 1000L
        if (min <= 0) return read
        val kept = read.tracks.filter { it.durationMs <= 0 || it.durationMs >= min }
        if (kept.size == read.tracks.size) return read
        return Library(read.source, kept, read.playlists, read.databaseTime, read.failure).also { it.prepare() }
    }

    /**
     * Leaves out tracks of the Mac library whose file is not on the phone: deleted here, or never
     * copied. A setting shows them again, greyed out as now.
     */
    private fun withoutMissing(read: Library): Library {
        if (prefs.showMissing || read.source != LibrarySource.MusicDatabase) return read
        val kept = read.tracks.filter { it.uri != null }
        if (kept.size == read.tracks.size) return read
        return Library(read.source, kept, read.playlists, read.databaseTime, read.failure).also { it.prepare() }
    }

    /**
     * Writes the phone's own playlists, and Favourites when there is no Mac library, as M3U8 files to
     * Download/Muz On, the playlist format Music on the Mac and iTunes import (File > Library > Import
     * Playlist). With a Mac library each line is the track's path on the Mac, so Music finds it at once.
     * Returns the number of files written.
     */
    suspend fun exportPlaylists(): Int = withContext(Dispatchers.IO) {
        val library = state.value ?: return@withContext 0
        val lists = library.playlists.filter { it.own || it.id == FAVOURITES_ID }
        val resolver = context.contentResolver
        val folder = android.os.Environment.DIRECTORY_DOWNLOADS + "/Muz On"
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        var written = 0
        for (playlist in lists) {
            val name = playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".m3u8"
            // Replaces an earlier export of the same playlist rather than piling up copies.
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf("$folder/", name),
            )
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl")
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            }
            val uri = resolver.insert(collection, values) ?: continue
            resolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                out.write("#EXTM3U\n")
                for (track in library.tracksOf(playlist)) {
                    out.write("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}\n")
                    val path = if (library.source == LibrarySource.MusicDatabase) track.sourcePath else track.filePath ?: track.sourcePath
                    out.write(path + "\n")
                }
            }
            written++
        }
        written
    }

    // ---- Playlist files in the music folder, as the classic player keeps its favourites: plain M3U8
    // files in Music/Playlists that any player or computer can open, and that outlive the app.

    private fun writableTree(): Pair<Uri, String>? {
        val (tree, root) = treeRoot() ?: return null
        val writable = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        return if (writable) tree to root else null
    }

    /** True when the music folder may be written, so playlists are saved there. */
    fun canWritePlaylists(): Boolean = writableTree() != null

    private fun playlistFolder(tree: Uri, root: String, create: Boolean): String? =
        children(tree, root).firstOrNull { it.name == PLAYLIST_FOLDER }?.id
            ?: if (!create) null else DocumentsContract.createDocument(
                context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, root), DocumentsContract.Document.MIME_TYPE_DIR, PLAYLIST_FOLDER,
            )?.let(DocumentsContract::getDocumentId)

    /** The folder on disk the music folder grant stands for, e.g. /storage/emulated/0/Music. */
    private fun rootPath(root: String): String? {
        val volume = root.substringBefore(':')
        val inside = root.substringAfter(':', "")
        val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return if (inside.isEmpty()) base else "$base/$inside"
    }

    private fun fileName(playlist: Playlist) =
        if (playlist.id == FAVOURITES_ID) "Favourites.m3u8" else playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".m3u8"

    private fun writePlaylistFiles(library: Library) {
        val (tree, root) = writableTree() ?: return
        runCatching {
            val dir = playlistFolder(tree, root, create = true) ?: return
            val base = rootPath(root)
            val existing = children(tree, dir).associateBy { it.name }
            val written = mutableSetOf<String>()
            for (playlist in library.playlists.filter { it.own || it.id == FAVOURITES_ID }) {
                val name = fileName(playlist)
                written += name
                val text = buildString {
                    append("#EXTM3U\n")
                    for (track in library.tracksOf(playlist)) {
                        val path = track.filePath ?: continue
                        append("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}\n")
                        // Relative to the Playlists folder, so the folder can move to a computer as it is.
                        append(if (base != null && path.startsWith("$base/")) "../" + path.removePrefix("$base/") else path).append('\n')
                    }
                }
                val id = existing[name]?.id ?: DocumentsContract.createDocument(
                    context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, dir), "audio/x-mpegurl", name,
                )?.let(DocumentsContract::getDocumentId) ?: continue
                context.contentResolver.openOutputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id), "wt")?.use { it.write(text.toByteArray()) }
            }
            // A playlist deleted here takes its file along; files this app did not write stay.
            for (gone in prefs.writtenPlaylistFiles() - written) {
                existing[gone]?.let { DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.id)) }
            }
            prefs.setWrittenPlaylistFiles(written)
        }.onFailure { Log.w(TAG, "Cannot write playlists", it) }
    }

    /**
     * After a fresh install the hearts and playlists come back from the files in Music/Playlists:
     * Favourites gives the hearts (when there is no Mac library), the other files the own playlists.
     */
    private fun restorePlaylistFiles(read: Library) {
        val (tree, root) = treeRoot() ?: return
        if (ownFile.exists() && prefs.restoredPlaylists) return
        runCatching {
            val dir = playlistFolder(tree, root, create = false) ?: return
            val base = rootPath(root) ?: return
            val byPath = read.tracks.mapNotNull { t -> t.filePath?.let { it to t.id } }.toMap()
            fun ids(docId: String): List<String> {
                val lines = context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, docId))!!.use { it.bufferedReader().readLines() }
                return lines.map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { line ->
                    val path = when {
                        line.startsWith("/") -> line
                        line.startsWith("../") -> base + "/" + line.removePrefix("../")
                        else -> "$base/$PLAYLIST_FOLDER/$line"
                    }
                    byPath[path]
                }
            }
            val files = children(tree, dir).filter { it.name.endsWith(".m3u8", true) || it.name.endsWith(".m3u", true) }
            if (read.source != LibrarySource.MusicDatabase && prefs.favoriteOverrides().isEmpty()) {
                files.firstOrNull { it.name.equals("Favourites.m3u8", true) }?.let { f -> ids(f.id).forEach { prefs.setFavoriteOverride(it, true) } }
            }
            if (!ownFile.exists()) {
                val restored = files.filterNot { it.name.equals("Favourites.m3u8", true) }.map { f ->
                    Playlist("own-" + java.util.UUID.randomUUID(), f.name.substringBeforeLast('.'), ids(f.id).distinct(), hasExactOrder = true, own = true)
                }
                if (restored.isNotEmpty()) saveOwn(restored)
                prefs.setWrittenPlaylistFiles(files.map { it.name }.toSet())
            }
            prefs.restoredPlaylists = true
        }.onFailure { Log.w(TAG, "Cannot read playlists", it) }
    }

    /** Shows the library again after the shortest length changed, without reading it anew. */
    suspend fun reapplyFilters() = withContext(Dispatchers.Default) {
        mutex.withLock { base?.let { state.value = present(it) } }
    }

    private suspend fun changeOwn(change: (List<Playlist>) -> List<Playlist>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            saveOwn(change(ownPlaylists()))
            base?.let { state.value = present(it) }
            state.value?.let(::writePlaylistFiles)
        }
    }

    suspend fun createPlaylist(name: String, trackIds: List<String>): String {
        val id = "own-" + java.util.UUID.randomUUID().toString()
        changeOwn { it + Playlist(id, name.trim(), trackIds.distinct(), hasExactOrder = true, own = true) }
        return id
    }

    /** Adds tracks at the end, leaving out those already in the playlist. */
    suspend fun addToPlaylist(id: String, trackIds: List<String>) = changeOwn { list ->
        list.map { pl -> if (pl.id == id) pl.copy(trackIds = pl.trackIds + trackIds.distinct().filter { it !in pl.trackIds }) else pl }
    }

    suspend fun removeFromPlaylist(id: String, trackId: String) = changeOwn { list ->
        list.map { pl -> if (pl.id == id) pl.copy(trackIds = pl.trackIds - trackId) else pl }
    }

    suspend fun renamePlaylist(id: String, name: String) = changeOwn { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }

    suspend fun deletePlaylist(id: String) = changeOwn { list -> list.filter { it.id != id } }

    /**
     * Sets or clears the heart of [track]. The change shows at once, smart playlists that depend on
     * it follow, and it is written down for the Mac, where tools/sync-library.sh applies it.
     */
    suspend fun setFavorite(track: Track, favorite: Boolean) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val read = base ?: return@withLock
            prefs.setFavoriteOverride(track.id, favorite.takeIf { it != read.byId[track.id]?.favorite })
            writeFavoriteChanges(read)
            state.value = present(read)
            state.value?.let(::writePlaylistFiles)
        }
    }

    /** One line per pending change, "<persistent id> true|false", in the app's own folder. */
    private fun writeFavoriteChanges(read: Library) {
        val dir = context.getExternalFilesDir(null) ?: return
        val lines = prefs.favoriteOverrides().filterKeys { read.byId.containsKey(it) }.map { (id, on) -> "$id $on" }
        val file = File(dir, "favourites-changes.txt")
        if (lines.isEmpty()) file.delete() else file.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun withFavorites(read: Library): Library {
        val overrides = prefs.favoriteOverrides()
        if (overrides.isEmpty()) return read
        val tracks = read.tracks.map { t -> overrides[t.id]?.let { t.copy(favorite = it) } ?: t }
        val byId = tracks.associateBy { it.id }
        val changed = overrides.keys.filter { read.byId.containsKey(it) }.toSet()
        val playlists = read.playlists.map { playlist ->
            val rules = playlist.rules ?: return@map playlist
            // Follows its rules only if they give exactly what the Mac listed for the library as read.
            val trusted = runCatching { read.tracks.filter { SmartRules.evaluate(rules, it) }.map { it.id }.toSet() == playlist.trackIds.toSet() }
                .getOrDefault(false)
            if (!trusted) return@map playlist
            val kept = playlist.trackIds.filter { id -> id !in changed || SmartRules.evaluate(rules, byId.getValue(id)) }
            val added = changed.filter { id -> id !in playlist.trackIds && SmartRules.evaluate(rules, byId.getValue(id)) }
            playlist.copy(trackIds = kept + added)
        }
        return Library(read.source, tracks, playlists, read.databaseTime, read.failure).also { it.prepare() }
    }

    /** Reads again when the database changed or [force] is set; otherwise keeps what is loaded. */
    suspend fun refresh(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = runCatching { findDatabase() }.onFailure { Log.w(TAG, "No folder access", it) }.getOrNull()
            val stamp = (db?.stamp ?: "device") + "/" + deviceStamp()
            if (!force && stamp == loadedStamp && state.value != null) return@withLock
            val started = System.currentTimeMillis()
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
            base = library
            // A heart the Mac already took over needs no override any more.
            prefs.favoriteOverrides().forEach { (id, on) -> if (library.byId[id]?.favorite == on) prefs.setFavoriteOverride(id, null) }
            restorePlaylistFiles(library)
            writeFavoriteChanges(library)
            state.value = present(library)
            state.value?.let(::writePlaylistFiles)
            Log.i(TAG, "Library ready in ${System.currentTimeMillis() - started} ms")
        }
        keepUpWithDisk()
    }

    /** One thread, lowest priority, for everything that looks at the music folder on disk. */
    private val diskWork = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "muzon-disk").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
    }
    @Volatile private var diskQueued = false

    /**
     * After the library is on screen, never before: squares the system's list of songs with the disk
     * (files copied in by a computer are often missing from it or half-registered, deleted ones can
     * linger) and watches the folders for changes. Light work in the background, once per
     * start of the app; the library follows by itself when the scanner changes anything.
     */
    private fun keepUpWithDisk() {
        if (diskQueued) return
        diskQueued = true
        // Not while the app opens: covers are read from the same disk, and the first screen must
        // come up drawn in full, so the look at the folder waits until things are quiet.
        diskWork.schedule(Runnable {
            try {
                // Once per start: after that the folder watch sees every change as it happens.
                if (watchers.isNotEmpty()) return@Runnable
                val walk = walkMusic() ?: return@Runnable
                watchFolders(walk.dirs)
                reconcileFiles(queryDevice(), walk.audio)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot look at the music folder", e)
            } finally {
                diskQueued = false
            }
        }, 10, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun fromDatabase(db: DbFiles, files: List<DeviceFile>): Library {
        val bytes = context.contentResolver.openInputStream(db.db)!!.use { it.readBytes() }
        val parsed = MusicDb.parse(bytes)
        val resolver = PathResolver(files.map { PathResolver.Entry(it.relative, it.title, it) })
        val claimed = HashSet<Long>()
        val fromMac = parsed.tracks.map { track ->
            val file = resolver.resolve(track.sourcePath, track.title)
            if (file == null) track else {
                claimed += file.id
                track.copy(uri = file.uri, filePath = file.path)
            }
        }
        // Songs on the phone the Mac library does not know yet (copied over since the last sync) are
        // in the library too, read from their own tags, until the next sync brings them in properly.
        // A second copy of a song the Mac already has (another album, another format) is not a new song.
        val known = parsed.tracks.mapTo(HashSet()) { it.title.trim().lowercase() }
        val extra = files.filter { it.id !in claimed }.map(::deviceTrack).filter { it.title.trim().lowercase() !in known }
        if (extra.isNotEmpty()) Log.i(TAG, "${extra.size} songs on the phone are not in the Mac library")
        val tracks = fromMac + extra
        val order = db.order?.let { uri ->
            runCatching { context.contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() } }.getOrNull()
        }
        val playlists = if (order != null) MusicDb.applyOrder(parsed.playlists, order) else parsed.playlists
        return Library(LibrarySource.MusicDatabase, tracks, playlists, db.stamp.substringBefore('/').toLongOrNull() ?: 0L)
    }

    private fun fromDevice(files: List<DeviceFile>, failure: String?): Library {
        val tracks = files.map(::deviceTrack)
        val byName = PathResolver(tracks.map { PathResolver.Entry(it.sourcePath, it.title, it.id) })
        return Library(LibrarySource.DeviceFiles, tracks, readM3uPlaylists(byName), failure = failure)
    }

    /** A song as the phone knows it: its tags, read again from the file when the system's list has none. */
    private fun deviceTrack(file: DeviceFile): Track = file.let { file ->
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

    private fun isUnknown(s: String) = s.isBlank() || s == "<unknown>"

    // ---- Keeping up with the music folder on disk

    private val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "aiff", "aif", "alac", "wma", "mka", "ape", "dsf")

    private fun musicRoot(): File = File(android.os.Environment.getExternalStorageDirectory(), "Music")

    private class Walk(val dirs: List<File>, val audio: Set<String>)

    /**
     * Lists the folders and audio files under Music in one pass, by name only: a file is told from a
     * folder by its extension and by listing it, never by asking the disk about each entry, which on
     * the phone's shared storage costs a round trip apiece.
     */
    private fun walkMusic(): Walk? {
        val root = musicRoot()
        val started = System.currentTimeMillis()
        val dirs = ArrayList<File>()
        val audio = HashSet<String>()
        val queue = ArrayDeque<File>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val names = dir.list() ?: continue
            dirs += dir
            for (name in names) {
                if (name.startsWith(".")) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                when {
                    ext in audioExtensions -> audio += File(dir, name).absolutePath
                    // Anything else may be a folder; covers, lyrics and playlists are known by name.
                    ext !in notFolders -> queue.add(File(dir, name))
                }
            }
        }
        if (dirs.isEmpty()) return null
        Log.i(TAG, "${dirs.size} folders, ${audio.size} audio files on disk in ${System.currentTimeMillis() - started} ms")
        return Walk(dirs, audio)
    }

    private val notFolders = setOf("jpg", "jpeg", "png", "webp", "gif", "lrc", "txt", "m3u", "m3u8", "pls", "cue", "log", "nfo", "json", "xml", "db", "plist", "musicdb", "itl")

    /**
     * Asks the system's media scanner about every audio file on disk that its list does not have, or
     * has but is no longer on disk. The scan updates the list, which the app watches and reloads from.
     */
    private fun reconcileFiles(listed: List<DeviceFile>, onDisk: Set<String>) {
        val root = musicRoot().absolutePath
        val known = listed.mapNotNull { it.path }.toHashSet()
        val missing = onDisk - known
        val gone = known.filter { it.startsWith(root) && it !in onDisk && !File(it).exists() }
        val toScan = (missing + gone).toTypedArray()
        if (toScan.isEmpty()) return
        Log.i(TAG, "Scanning ${missing.size} new and ${gone.size} removed files")
        android.media.MediaScannerConnection.scanFile(context, toScan, null, null)
    }

    private val watchers = mutableListOf<android.os.FileObserver>()
    private val watchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pendingScan = mutableSetOf<String>()
    private val flushScan = Runnable {
        val paths = synchronized(pendingScan) { pendingScan.toTypedArray().also { pendingScan.clear() } }
        if (paths.isNotEmpty()) android.media.MediaScannerConnection.scanFile(context, paths, null, null)
    }

    /**
     * Watches every folder under Music while the app runs, with one observer: a file written, moved
     * in, moved out or deleted is handed to the media scanner a moment later, and the library follows
     * by itself. A new folder is added to the watch from the background thread.
     */
    private fun watchFolders(dirs: List<File>) {
        if (watchers.isNotEmpty() && dirs.size == watchedCount) return
        val started = System.currentTimeMillis()
        watchers.forEach { it.stopWatching() }
        watchers.clear()
        watchedCount = dirs.size
        val mask = android.os.FileObserver.CLOSE_WRITE or android.os.FileObserver.MOVED_TO or android.os.FileObserver.MOVED_FROM or
            android.os.FileObserver.DELETE or android.os.FileObserver.CREATE
        // The observer reports a name, not its folder, so each folder keeps its own small observer;
        // they are cheap once the folders are known.
        for (dir in dirs) {
            val observer = object : android.os.FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) {
                    val name = path ?: return
                    val file = File(dir, name)
                    if (name.substringAfterLast('.', "").lowercase() in audioExtensions) {
                        synchronized(pendingScan) { pendingScan += file.absolutePath }
                        watchHandler.removeCallbacks(flushScan)
                        watchHandler.postDelayed(flushScan, 1500)
                    } else if (event and CREATE != 0) {
                        diskWork.execute { if (file.isDirectory) walkMusic()?.let { watchFolders(it.dirs) } }
                    }
                }
            }
            observer.startWatching()
            watchers += observer
        }
        Log.i(TAG, "Watching ${dirs.size} folders, set up in ${System.currentTimeMillis() - started} ms")
    }

    @Volatile private var watchedCount = -1

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

    /** Changes nobody can see in the database: files added or deleted on the phone. */
    private fun deviceStamp(): String = runCatching {
        "${MediaStore.getVersion(context)}:${MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)}"
    }.getOrDefault("")

    private fun findDatabase(): DbFiles? {
        // A library folder picked in settings comes first.
        prefs.libraryTree?.let(Uri::parse)?.let { tree ->
            runCatching {
                val list = children(tree, DocumentsContract.getTreeDocumentId(tree))
                list.firstOrNull { it.name == "Library.musicdb" }?.let { db ->
                    val order = list.firstOrNull { it.name == "playlist-order.txt" }
                    return DbFiles(
                        DocumentsContract.buildDocumentUriUsingTree(tree, db.id),
                        "${db.modified}/${db.size}/${order?.modified}/${order?.size}",
                        order?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) },
                    )
                }
            }
        }
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
