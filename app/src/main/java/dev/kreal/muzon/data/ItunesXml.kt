package dev.kreal.muzon.data

import android.util.Base64
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.URLDecoder

/**
 * Reader for the XML library of iTunes ("iTunes Music Library.xml", which iTunes on Windows writes
 * when "Share iTunes Library XML with other applications" is on) and of Music on the Mac (File >
 * Library > Export Library). Same tracks, persistent ids, hearts and playlists as Library.musicdb,
 * so the phone works the same with a Windows PC.
 */
object ItunesXml {
    fun parse(input: InputStream): MusicDb.Parsed {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        // The plist turned into maps and lists, values as strings, ints, booleans or byte arrays.
        val root = readValue(parser.apply { nextTag(); require(XmlPullParser.START_TAG, null, "plist"); nextTag() }) as? Map<*, *>
            ?: throw MusicDbFormatException("Not an iTunes library")
        val rawTracks = root["Tracks"] as? Map<*, *> ?: throw MusicDbFormatException("No tracks")
        val byNumber = HashMap<String, String>()
        val tracks = rawTracks.values.mapNotNull { v ->
            val t = v as? Map<*, *> ?: return@mapNotNull null
            val id = (t["Persistent ID"] as? String)?.uppercase() ?: return@mapNotNull null
            byNumber[t["Track ID"].toString()] = id
            Track(
                id = id,
                title = t.text("Name"),
                artist = t.text("Artist"),
                albumArtist = t.text("Album Artist"),
                album = t.text("Album"),
                genre = t.text("Genre"),
                trackNumber = t.int("Track Number"),
                discNumber = t.int("Disc Number"),
                year = t.int("Year"),
                durationMs = t.int("Total Time").toLong(),
                dateAddedMs = (t["Date Added"] as? String)?.let(::parseDate) ?: 0L,
                sourcePath = fileUrlToPath(t.text("Location")),
                favorite = t["Loved"] == true || t["Favorited"] == true,
                playCount = t.int("Play Count"),
            )
        }
        val playlists = (root["Playlists"] as? List<*>).orEmpty().mapNotNull { v ->
            val p = v as? Map<*, *> ?: return@mapNotNull null
            // The library itself, Music, Movies, Podcasts and the like, folders and hidden lists.
            if (p["Master"] == true || p.containsKey("Distinguished Kind") || p["Visible"] == false || p["Folder"] == true) return@mapNotNull null
            val id = (p["Playlist Persistent ID"] as? String)?.uppercase() ?: return@mapNotNull null
            val items = (p["Playlist Items"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("Track ID")?.toString()?.let(byNumber::get) }
            Playlist(id, p.text("Name").trim(), items, hasExactOrder = p["Smart Criteria"] == null, rules = p["Smart Criteria"] as? ByteArray, mac = true)
        }
        return MusicDb.Parsed(tracks, playlists)
    }

    private fun Map<*, *>.text(key: String) = this[key] as? String ?: ""
    private fun Map<*, *>.int(key: String) = (this[key] as? Long)?.toInt() ?: 0

    private fun readValue(p: XmlPullParser): Any? = when (p.name) {
        "dict" -> {
            val map = HashMap<String, Any?>()
            var key: String? = null
            while (p.nextTag() == XmlPullParser.START_TAG) {
                if (p.name == "key") key = p.nextText() else map[key ?: ""] = readValue(p)
            }
            map
        }
        "array" -> {
            val list = ArrayList<Any?>()
            while (p.nextTag() == XmlPullParser.START_TAG) list += readValue(p)
            list
        }
        "true" -> true.also { p.nextTag() }
        "false" -> false.also { p.nextTag() }
        "integer" -> p.nextText().trim().toLongOrNull()
        "data" -> runCatching { Base64.decode(p.nextText().replace(Regex("\\s"), ""), Base64.DEFAULT) }.getOrNull()
        else -> p.nextText() // string, date, real
    }

    private fun parseDate(s: String): Long =
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    /** "file://localhost/D:/Music/x.mp3" to "D:/Music/x.mp3", "file:///Users/…" to "/Users/…". */
    private fun fileUrlToPath(url: String): String {
        if (url.isEmpty()) return ""
        val path = url.removePrefix("file://").removePrefix("localhost")
        val decoded = try {
            URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            path
        }
        // A Windows drive: the slash before it is not part of the path.
        return if (Regex("^/[A-Za-z]:/").containsMatchIn(decoded)) decoded.drop(1) else decoded
    }
}
