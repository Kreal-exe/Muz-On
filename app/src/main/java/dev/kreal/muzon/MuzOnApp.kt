package dev.kreal.muzon

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dev.kreal.muzon.data.LibraryRepository
import dev.kreal.muzon.data.Prefs
import dev.kreal.muzon.playback.PlayerConnection
import dev.kreal.muzon.ui.ArtFetcher
import dev.kreal.muzon.ui.ArtKeyer

class MuzOnApp : Application(), ImageLoaderFactory {
    val prefs by lazy { Prefs(this) }
    val repository by lazy { LibraryRepository(this, prefs) }
    val connection by lazy { PlayerConnection(this, prefs) }

    /** Set by the service while it runs; settings that have no controller command go through it. */
    var exoPlayer: ExoPlayer? = null

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ArtFetcher.Factory(this@MuzOnApp))
            add(ArtKeyer())
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache(null)
        .crossfade(110)
        .build()
}
