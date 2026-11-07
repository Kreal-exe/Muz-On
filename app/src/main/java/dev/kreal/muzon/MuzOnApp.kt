package dev.kreal.muzon

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import kotlinx.coroutines.launch
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
    val connection by lazy { PlayerConnection(this, prefs) { exoPlayer } }

    /** Set by the service while it runs; settings that have no controller command go through it. */
    var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        // Headphones are watched here, for as long as the app is alive, not only while the playback
        // service runs: after a long pause the service has stopped, and the music must still come on.
        known.addAll(audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).map { it.id })
        audio.registerAudioDeviceCallback(outputs, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    private val audio by lazy { getSystemService(android.media.AudioManager::class.java) }

    /** Outputs already there; registering reports them all as added, which must not start the music. */
    private val known = mutableSetOf<Int>()

    private val headphones = setOf(
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
        android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
    )

    private val outputs = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) {
            val fresh = added.filter { known.add(it.id) }
            if (!prefs.resumeOnConnect || fresh.none { it.type in headphones }) return
            android.util.Log.i("Playback", "Headphones connected: ${fresh.joinToString { it.productName.toString() }}; playing on")
            kotlinx.coroutines.MainScope().launch {
                // The queue is restored against the library, so it has to be there first.
                if (repository.library.value == null) repository.refresh()
                repository.library.value?.let(connection::onLibrary)
                connection.resume()
            }
        }

        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) {
            removed.forEach { known.remove(it.id) }
        }
    }

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
