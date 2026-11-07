package dev.kreal.muzon.data

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MusicDbFormatException(message: String) : Exception(message)

/**
 * Reader for the `Library.musicdb` file of a Mac music library. The format is undocumented and may
 * change with an update of the Mac app, so everything unexpected raises [MusicDbFormatException] and
 * the caller falls back to the device's own files.
 *
 * Layout: `hfma` file header, then AES-128-ECB over the start of the body, then zlib. The result is a
 * chain of `hsma` sections: type 1 holds tracks (`itma`), type 2 playlists (`lpma`). Records carry
 * `boma` children: strings, numbers and playlist items (`ipfa`).
 */
object MusicDb {
    private val KEY = "BHUILuilfghuila3".toByteArray()
    private const val SECONDS_1904_TO_1970 = 2082844800L

    class Parsed(val tracks: List<Track>, val playlists: List<Playlist>)

    fun parse(file: ByteArray): Parsed {
        val data = decrypt(file)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var tracks: List<Track>? = null
        var playlists: List<Playlist>? = null
        var offset = 0
        while (offset + 16 <= data.size) {
            buffer.requireTag(offset, "hsma")
            val total = buffer.getInt(offset + 8)
            val kind = buffer.getInt(offset + 12)
            if (total <= 0) throw MusicDbFormatException("Bad section size")
            val bodyStart = offset + buffer.getInt(offset + 4)
            when (kind) {
                1 -> tracks = readTracks(buffer, bodyStart, offset + total)
                2 -> playlists = readPlaylists(buffer, bodyStart, offset + total)
            }
            offset += total
        }
        return Parsed(
            tracks ?: throw MusicDbFormatException("No track section"),
            playlists ?: throw MusicDbFormatException("No playlist section"),
        )
    }

    private fun decrypt(file: ByteArray): ByteArray {
        if (file.size < 160 || String(file, 0, 4, Charsets.ISO_8859_1) != "hfma") {
            throw MusicDbFormatException("Not a music database")
        }
        val header = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN)
        val headerSize = header.getInt(4)
        val bodySize = file.size - headerSize
        val encrypted = minOf(header.getInt(84), bodySize).let { it - it % 16 }
        if (headerSize <= 0 || encrypted < 0) throw MusicDbFormatException("Bad header")
        val body = file.copyOfRange(headerSize, file.size)
        Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"))
            doFinal(body, 0, encrypted, body, 0)
        }
        val inflater = Inflater()
        try {
            inflater.setInput(body)
            val out = ByteArrayOutputStream(body.size * 6)
            val chunk = ByteArray(256 * 1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(chunk)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(chunk, 0, count)
            }
            return out.toByteArray()
        } catch (e: java.util.zip.DataFormatException) {
            throw MusicDbFormatException("Cannot unpack: ${e.message}")
        } finally {
            inflater.end()
        }
    }

    private fun readTracks(b: ByteBuffer, start: Int, end: Int): List<Track> {
        b.requireTag(start, "ltma")
        val declared = b.getInt(start + 8)
        val tracks = ArrayList<Track>(declared)
        var p = start + b.getInt(start + 4)
        while (p < end && p + 8 < b.capacity()) {
            b.requireTag(p, "itma")
            val headerSize = b.getInt(p + 4)
            val total = b.getInt(p + 8)
            var title = ""; var album = ""; var artist = ""; var albumArtist = ""; var genre = ""; var url = ""
            var duration = 0L; var added = 0L
            var q = p + headerSize
            while (q < p + total) {
                b.requireTag(q, "boma")
                val size = b.getInt(q + 8)
                when (b.getInt(q + 12)) {
                    0x02 -> title = b.text(q, size)
                    0x03 -> album = b.text(q, size)
                    0x04 -> artist = b.text(q, size)
                    0x05 -> genre = b.text(q, size)
                    0x0B -> url = b.text(q, size)
                    0x1B -> albumArtist = b.text(q, size)
                    0x01 -> {
                        duration = b.getInt(q + 20 + 156).toLong() and 0xFFFFFFFFL
                        val raw = b.getInt(q + 20 + 92).toLong() and 0xFFFFFFFFL
                        added = if (raw == 0L) 0L else (raw - SECONDS_1904_TO_1970) * 1000
                    }
                }
                q += size
            }
            tracks += Track(
                id = b.persistentId(p + 16),
                title = title,
                artist = artist,
                albumArtist = albumArtist,
                album = album,
                genre = genre,
                trackNumber = b.getInt(p + 160),
                discNumber = b.getInt(p + 84),
                year = b.getInt(p + 168),
                durationMs = duration,
                dateAddedMs = added,
                sourcePath = fileUrlToPath(url),
            )
            p += total
        }
        if (tracks.size != declared) {
            throw MusicDbFormatException("Expected $declared tracks, read ${tracks.size}")
        }
        return tracks
    }

    private fun readPlaylists(b: ByteBuffer, start: Int, end: Int): List<Playlist> {
        b.requireTag(start, "lPma")
        val result = ArrayList<Playlist>()
        var p = start + b.getInt(start + 4)
        while (p < end) {
            b.requireTag(p, "lpma")
            val headerSize = b.getInt(p + 4)
            val total = b.getInt(p + 8)
            val isMaster = b.get(p + 44).toInt() != 0
            val builtInKind = b.getShort(p + 78).toInt()
            var name = ""
            val ids = ArrayList<String>(b.getInt(p + 16).coerceIn(0, 100_000))
            var q = p + headerSize
            while (q < p + total) {
                b.requireTag(q, "boma")
                val size = b.getInt(q + 8)
                when (b.getInt(q + 12)) {
                    0xC8 -> name = b.text(q, size)
                    0xCE -> {
                        b.requireTag(q + 20, "ipfa")
                        ids += b.persistentId(q + 20 + 20)
                    }
                }
                q += size
            }
            // Library, Music, Downloaded, Genius and the like have a built-in kind; they are not the user's.
            if (!isMaster && builtInKind == 0) {
                result += Playlist(b.persistentId(p + 30), name.trim(), ids)
            }
            p += total
        }
        return result
    }

    /** Applies an order file: `#<playlist id>` lines, each followed by that playlist's track ids. */
    fun applyOrder(playlists: List<Playlist>, orderFile: String): List<Playlist> {
        val orders = HashMap<String, MutableList<String>>()
        var current: MutableList<String>? = null
        orderFile.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#") -> current = ArrayList<String>().also { orders[line.drop(1).uppercase()] = it }
                else -> current?.add(line.uppercase())
            }
        }
        return playlists.map { playlist ->
            val order = orders[playlist.id]
            // An order written before the last library change no longer fits: keep the database's.
            if (order != null && order.size == playlist.trackIds.size && order.toSet() == playlist.trackIds.toSet()) {
                playlist.copy(trackIds = order, hasExactOrder = true)
            } else {
                playlist
            }
        }
    }

    private fun fileUrlToPath(url: String): String {
        val path = url.removePrefix("file://").removePrefix("localhost")
        return try {
            URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            path
        }
    }

    private fun ByteBuffer.requireTag(offset: Int, tag: String) {
        if (offset < 0 || offset + 4 > capacity()) throw MusicDbFormatException("Ran past the end at $offset")
        for (i in 0 until 4) {
            if (get(offset + i).toInt() != tag[i].code) throw MusicDbFormatException("Expected $tag at $offset")
        }
    }

    private fun ByteBuffer.persistentId(offset: Int): String {
        val out = StringBuilder(16)
        for (i in 7 downTo 0) {
            val v = get(offset + i).toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 15])
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"

    private fun ByteBuffer.text(boma: Int, size: Int): String {
        val encoding = getInt(boma + 20)
        val length = getInt(boma + 24).coerceIn(0, size - 36)
        val bytes = ByteArray(length)
        val copy = duplicate()
        copy.position(boma + 36)
        copy.get(bytes)
        return String(bytes, if (encoding == 1) Charsets.UTF_16LE else Charsets.UTF_8)
    }
}
