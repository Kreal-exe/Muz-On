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

enum class PlayerPanel { None, Lyrics, Queue }

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

    var panel by mutableStateOf(PlayerPanel.None)

    fun refreshLibrary(force: Boolean = false) {
        viewModelScope.launch {
            repository.refresh(force)
            repository.library.value?.let(connection::onLibrary)
        }
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
