package dev.kreal.muzon.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kreal.muzon.MuzOnApp
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MuzOnApp
    val prefs = app.prefs
    val repository = app.repository
    val connection = app.connection
    val nav = Navigator()

    private val sheetState = mutableFloatStateOf(0f)

    /**
     * Wireless headphones play the sound a quarter of a second or so after the phone reports it, and
     * the phone does not say how much; lyrics then lag what is heard. While such an output is
     * connected the lyrics go that much earlier, as Apple Music does.
     */
    private val wirelessState = mutableStateOf(false)
    val lyricLeadMs: Int get() = prefs.lyricsLeadMs + if (wirelessState.value) WIRELESS_DELAY_MS else 0
    private val audio = application.getSystemService(android.media.AudioManager::class.java)
    private val outputs = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) = checkOutputs()
        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>?) = checkOutputs()
    }

    private fun checkOutputs() {
        val wireless = setOf(
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
            android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
            android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST,
        )
        wirelessState.value = audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in wireless }
    }

    // Files added or deleted on the phone show up without a restart.
    private val mediaHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val mediaChanged = Runnable { refreshLibrary() }
    private val mediaObserver = object : android.database.ContentObserver(mediaHandler) {
        override fun onChange(selfChange: Boolean) {
            mediaHandler.removeCallbacks(mediaChanged)
            mediaHandler.postDelayed(mediaChanged, 1500)
        }
    }

    init {
        application.contentResolver.registerContentObserver(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        checkOutputs()
        audio.registerAudioDeviceCallback(outputs, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
        audio.unregisterAudioDeviceCallback(outputs)
    }

    /** 0 is the mini player, 1 the full one. Lives here so the fold never closes or opens it. */
    var sheet: Float
        get() = sheetState.floatValue
        set(v) { sheetState.floatValue = v }

    private val queueFullState = mutableFloatStateOf(0f)

    /** 0 is the queue under the controls, 1 the queue over the whole player. */
    var queueFull: Float
        get() = queueFullState.floatValue
        set(v) { queueFullState.floatValue = v }

    /** Tracks of the artist or playlist whose row was held; the menu shows while set. */
    var collectionMenu by mutableStateOf<List<dev.kreal.muzon.data.Track>?>(null)
    /** The playlist the long-press menu belongs to, when it is one. */
    var collectionPlaylist by mutableStateOf<dev.kreal.muzon.data.Playlist?>(null)
    var renamingPlaylist by mutableStateOf<dev.kreal.muzon.data.Playlist?>(null)
    var deletingPlaylist by mutableStateOf<dev.kreal.muzon.data.Playlist?>(null)

    /** The display's highest refresh rate, voted for by everything that moves. */
    var topFrameRate by mutableFloatStateOf(120f)

    var isWide by mutableStateOf(false)

    /** The tab a finger is swiping to, lit below before the pager comes to rest; null otherwise. */
    var swipeTab by mutableStateOf<dev.kreal.muzon.ui.Tab?>(null)

    /** The tab lit in the tab bar: where a swipe is heading, else the open one. */
    val litTab: dev.kreal.muzon.ui.Tab get() = swipeTab ?: nav.tab
    var lyricsOpen by mutableStateOf(false)

    /** Opens a screen from anywhere, the player included, which closes to show it. */
    fun goTo(route: Route) {
        sheet = 0f
        queueFull = 0f
        nav.push(route)
    }

    fun refreshLibrary(force: Boolean = false) {
        viewModelScope.launch {
            repository.refresh(force)
            repository.library.value?.let(connection::onLibrary)
        }
    }

    /** Tracks waiting for the "add to playlist" window. */
    var addToPlaylist by mutableStateOf<List<dev.kreal.muzon.data.Track>?>(null)

    fun createPlaylist(name: String, tracks: List<dev.kreal.muzon.data.Track>, open: Boolean) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name, tracks.map { it.id })
            if (open) goTo(Route.PlaylistRoute(id))
        }
    }

    fun addToPlaylist(id: String, tracks: List<dev.kreal.muzon.data.Track>) {
        viewModelScope.launch { repository.addToPlaylist(id, tracks.map { it.id }) }
    }

    fun removeFromPlaylist(id: String, trackId: String) {
        viewModelScope.launch { repository.removeFromPlaylist(id, trackId) }
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch { repository.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { repository.deletePlaylist(id) }
        // Leave its page if it is open.
        if (nav.stack.lastOrNull() == Route.PlaylistRoute(id)) nav.pop()
    }

    fun toggleFavorite(id: String) {
        val track = repository.library.value?.byId?.get(id) ?: return
        viewModelScope.launch { repository.setFavorite(track, !track.favorite) }
    }

    fun applySkipSilence(on: Boolean) {
        prefs.skipSilence = on
        app.exoPlayer?.skipSilenceEnabled = on
    }

    fun applySpeed(speed: Float) {
        prefs.speed = speed
        connection.setSpeed(speed)
    }
}
