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

    /** The display's highest refresh rate, voted for by everything that moves. */
    var topFrameRate by mutableFloatStateOf(120f)

    var isWide by mutableStateOf(false)
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
