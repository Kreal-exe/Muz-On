package dev.kreal.muzon.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.ImageRequest
import coil.request.Options
import dev.kreal.muzon.MuzOnApp
import dev.kreal.muzon.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File

data class ArtKey(val trackId: String, val uri: String, val albumKey: String, val large: Boolean)

class ArtKeyer : Keyer<ArtKey> {
    override fun key(data: ArtKey, options: Options) = "art:${data.trackId}:${data.large}"
}

/**
 * The picture embedded in each track, not one picture per album. A track without one borrows from
 * its album. Decoded once, at the size needed, then kept as a small file: reading an embedded picture
 * is far too slow to repeat while a list scrolls.
 */
class ArtFetcher(private val key: ArtKey, private val app: MuzOnApp) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Art.io) {
        val dir = File(app.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, "${key.trackId}_${if (key.large) "l" else "s"}.jpg")
        if (!file.exists() || file.length() == 0L) {
            val empty = File(dir, "${key.trackId}.none")
            if (empty.exists()) return@withContext null
            val bytes = embedded(key.uri) ?: borrow()
            if (bytes == null) {
                empty.createNewFile()
                return@withContext null
            }
            val bitmap = decode(bytes, if (key.large) 1080 else 320) ?: return@withContext null
            val temp = File(dir, "${file.name}.tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            temp.renameTo(file)
        }
        SourceResult(ImageSource(file.toOkioPath(), okio.FileSystem.SYSTEM), "image/jpeg", DataSource.DISK)
    }

    private fun borrow(): ByteArray? {
        val album = app.repository.library.value?.albumsByKey?.get(key.albumKey) ?: return null
        for (other in album.tracks.take(6)) {
            if (other.id == key.trackId) continue
            val uri = other.uri ?: continue
            embedded(uri)?.let { return it }
        }
        return null
    }

    private fun embedded(uri: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(app, Uri.parse(uri))
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decode(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    class Factory(private val app: MuzOnApp) : Fetcher.Factory<ArtKey> {
        override fun create(data: ArtKey, options: Options, imageLoader: ImageLoader): Fetcher = ArtFetcher(data, app)
    }
}

object Art {
    val io = Dispatchers.IO.limitedParallelism(4)

    fun key(track: Track, large: Boolean) =
        track.uri?.let { ArtKey(track.id, it, track.albumKey, large) }

    /** A flat placeholder whose colour stays the same for a track. */
    fun placeholder(id: String): Color {
        val hue = (id.hashCode() and 0xFFFF) % 360
        return Color.hsl(hue.toFloat(), 0.35f, 0.26f)
    }

    fun request(context: Context, key: ArtKey, allowHardware: Boolean = true) =
        ImageRequest.Builder(context).data(key).allowHardware(allowHardware).build()
}

@Composable
fun TrackArt(track: Track?, modifier: Modifier = Modifier, shape: Shape, large: Boolean = false) {
    val color = remember(track?.id) { track?.let { Art.placeholder(it.id) } ?: Color(0xFF26262B) }
    val context = LocalContext.current
    val model = remember(track?.id, track?.uri, large) { track?.let { Art.key(it, large) }?.let { Art.request(context, it) } }
    Box(modifier.clip(shape).background(color)) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
