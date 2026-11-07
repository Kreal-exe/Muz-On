package dev.kreal.muzon.data

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a picture for a track that has none: the release of that track, else a photo of its artist.
 * Only artist and title leave the phone. [Outcome.Unavailable] means the network failed and it is
 * worth asking again later; [Outcome.NotFound] means the services answered and have nothing.
 */
object OnlineArt {
    sealed interface Outcome {
        class Found(val bytes: ByteArray) : Outcome
        data object NotFound : Outcome
        data object Unavailable : Outcome
    }

    private val gate = Mutex()
    private var lastCall = 0L
    private val artistPhotos = ConcurrentHashMap<String, String>()

    suspend fun find(artist: String, title: String): Outcome {
        val name = cleanArtist(artist)
        val song = cleanTitle(title, name)
        if (name.isEmpty()) return Outcome.NotFound
        return try {
            val url = deezerTrack(name, song) ?: itunesTrack(name, song) ?: artistPhoto(name)
            if (url == null) Outcome.NotFound else Outcome.Found(download(url))
        } catch (e: java.io.IOException) {
            Log.i("OnlineArt", "Not reachable: ${e.message}")
            Outcome.Unavailable
        } catch (e: org.json.JSONException) {
            Outcome.Unavailable
        }
    }

    private suspend fun deezerTrack(artist: String, title: String): String? {
        if (title.isEmpty()) return null
        val query = enc("artist:\"$artist\" track:\"$title\"")
        val data = getJson("https://api.deezer.com/search?limit=8&q=$query").optJSONArray("data") ?: return null
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            if (same(item.getJSONObject("artist").optString("name"), artist)) {
                item.optJSONObject("album")?.optString("cover_xl")?.takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    private suspend fun itunesTrack(artist: String, title: String): String? {
        if (title.isEmpty()) return null
        val query = enc("$artist $title")
        val results = getJson("https://itunes.apple.com/search?entity=song&limit=8&term=$query").optJSONArray("results") ?: return null
        val wanted = norm(title)
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            if (same(item.optString("artistName"), artist) && norm(item.optString("trackName")).contains(wanted)) {
                item.optString("artworkUrl100").takeIf { it.isNotEmpty() }?.let { return it.replace("100x100", "1000x1000") }
            }
        }
        return null
    }

    private suspend fun artistPhoto(artist: String): String? {
        val key = norm(artist)
        artistPhotos[key]?.let { return it.ifEmpty { null } }
        val data = getJson("https://api.deezer.com/search/artist?limit=5&q=${enc(artist)}").optJSONArray("data") ?: return null
        var found = ""
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            if (same(item.optString("name"), artist)) {
                // Deezer answers with a blank silhouette for an artist without a photo.
                found = item.optString("picture_xl").takeIf { it.isNotEmpty() && !it.contains("/artist//") }.orEmpty()
                break
            }
        }
        artistPhotos[key] = found
        return found.ifEmpty { null }
    }

    private suspend fun getJson(url: String): JSONObject {
        // Both services limit requests; a list of untagged tracks must not hit them all at once.
        gate.withLock {
            val wait = 220 - (System.currentTimeMillis() - lastCall)
            if (wait > 0) delay(wait)
            lastCall = System.currentTimeMillis()
        }
        return JSONObject(String(open(url)))
    }

    private fun download(url: String): ByteArray = open(url)

    private fun open(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "MuzOn/1.0")
        try {
            if (connection.responseCode !in 200..299) throw java.io.IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private val BRACKETS = Regex("""\([^)]*\)|\[[^]]*]""")
    private val PROD = Regex("""(?i)\s(prod\.?|feat\.?|ft\.?)\s.*$""")
    private fun norm(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), "")
    private fun same(a: String, b: String) = norm(a).let { it.isNotEmpty() && it == norm(b) }

    private fun cleanArtist(artist: String) =
        artist.split(",", "&", " feat", " ft.", " x ").first().trim()

    /** "Кишлак - Ростов" and "ЛАУД, Кишлак - Привидение (prod. X)" name just "Ростов" and "Привидение". */
    fun cleanTitle(title: String, artist: String): String {
        var t = title.replace(BRACKETS, " ").replace(PROD, "").trim()
        val dash = t.indexOf(" - ")
        if (dash > 0 && norm(t.substring(0, dash)).contains(norm(artist))) t = t.substring(dash + 3)
        return t.trim()
    }
}
