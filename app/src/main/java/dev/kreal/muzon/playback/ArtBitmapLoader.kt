package dev.kreal.muzon.playback

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.kreal.muzon.MuzOnApp
import dev.kreal.muzon.ui.Art
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gives the system's media surfaces (notification, lock screen, the phone's island) the same picture
 * the app shows, and always one: the player alone finds only pictures embedded in the current file,
 * and only once it has read them, so a surface asking early got nothing.
 */
@UnstableApi
class ArtBitmapLoader(private val app: MuzOnApp) : BitmapLoader {
    private val fallback = DataSourceBitmapLoader(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun supportsMimeType(mimeType: String) = fallback.supportsMimeType(mimeType)
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme != SCHEME) return fallback.loadBitmap(uri)
        val result = SettableFuture.create<Bitmap>()
        val id = uri.schemeSpecificPart
        scope.launch {
            val track = app.repository.library.value?.byId?.get(id)
            val key = track?.let { Art.key(it, large = false) }
            val bitmap = key?.let {
                val request = ImageRequest.Builder(app).data(it).allowHardware(false).size(Size(512, 512)).build()
                ((Coil.imageLoader(app).execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }
            if (bitmap != null) result.set(bitmap) else result.setException(IllegalStateException("No artwork for $id"))
        }
        return result
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? =
        metadata.artworkUri?.let(::loadBitmap)
            ?: metadata.artworkData?.let(::decodeBitmap)

    companion object {
        const val SCHEME = "muzon-art"
    }
}
