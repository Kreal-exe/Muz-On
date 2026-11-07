package dev.kreal.muzon.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppTheme { Poster, Detailed, Aero }
enum class ColorMode { System, Dark, Black, Light }
enum class LyricsSize { Small, Medium, Large }
enum class Place { Playlists, Artists, Albums, Songs, Recent, Genres }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("muzon", Context.MODE_PRIVATE)

    private fun <E : Enum<E>> enumState(key: String, default: E, all: Array<E>) =
        mutableStateOf(all.firstOrNull { it.name == sp.getString(key, null) } ?: default)

    private val themeState = enumState("theme", AppTheme.Poster, AppTheme.entries.toTypedArray())
    var theme: AppTheme
        get() = themeState.value
        set(v) { themeState.value = v; sp.edit().putString("theme", v.name).apply() }

    private val modeState = enumState("mode", ColorMode.Dark, ColorMode.entries.toTypedArray())
    var colorMode: ColorMode
        get() = modeState.value
        set(v) { modeState.value = v; sp.edit().putString("mode", v.name).apply() }

    private val lyricsSizeState = enumState("lyricsSize", LyricsSize.Medium, LyricsSize.entries.toTypedArray())
    var lyricsSize: LyricsSize
        get() = lyricsSizeState.value
        set(v) { lyricsSizeState.value = v; sp.edit().putString("lyricsSize", v.name).apply() }

    private val coverColorState = mutableStateOf(sp.getBoolean("coverColor", true))
    var coverColor: Boolean
        get() = coverColorState.value
        set(v) { coverColorState.value = v; sp.edit().putBoolean("coverColor", v).apply() }

    private val keepScreenState = mutableStateOf(sp.getBoolean("keepScreen", true))
    var keepScreenForLyrics: Boolean
        get() = keepScreenState.value
        set(v) { keepScreenState.value = v; sp.edit().putBoolean("keepScreen", v).apply() }

    private val skipSilenceState = mutableStateOf(sp.getBoolean("skipSilence", false))
    var skipSilence: Boolean
        get() = skipSilenceState.value
        set(v) { skipSilenceState.value = v; sp.edit().putBoolean("skipSilence", v).apply() }

    private val speedState = mutableStateOf(sp.getFloat("speed", 1f))
    var speed: Float
        get() = speedState.value
        set(v) { speedState.value = v; sp.edit().putFloat("speed", v).apply() }

    var treeUri: String? by mutableStateOf(sp.getString("tree", null))
        private set

    fun setTree(uri: String?) {
        treeUri = uri
        sp.edit().putString("tree", uri).apply()
    }

    var folderSkipped: Boolean by mutableStateOf(sp.getBoolean("folderSkipped", false))
        private set

    fun skipFolder() {
        folderSkipped = true
        sp.edit().putBoolean("folderSkipped", true).apply()
    }

    private val placesDefault = listOf(Place.Playlists, Place.Artists, Place.Albums, Place.Songs, Place.Recent)

    /** Shown places in order. */
    var places: List<Place> by mutableStateOf(
        sp.getString("places", null)?.split(',')?.mapNotNull { n -> Place.entries.firstOrNull { it.name == n } }
            ?: placesDefault,
    )
        private set

    fun updatePlaces(list: List<Place>) {
        places = list
        sp.edit().putString("places", list.joinToString(",") { it.name }).apply()
    }

    fun sortOf(playlistId: String): Pair<PlaylistSort, Boolean> {
        val mode = PlaylistSort.entries.firstOrNull { it.name == sp.getString("sort_$playlistId", null) }
            ?: PlaylistSort.AsInMusic
        return mode to sp.getBoolean("sortRev_$playlistId", false)
    }

    fun setSort(playlistId: String, mode: PlaylistSort, reversed: Boolean) {
        sp.edit().putString("sort_$playlistId", mode.name).putBoolean("sortRev_$playlistId", reversed).apply()
    }

    fun saveQueue(ids: List<String>, index: Int, position: Long) {
        sp.edit().putString("queue", ids.joinToString(",")).putInt("queueIndex", index).putLong("queuePos", position).apply()
    }

    fun savePosition(index: Int, position: Long) {
        sp.edit().putInt("queueIndex", index).putLong("queuePos", position).apply()
    }

    fun savedQueue(): Triple<List<String>, Int, Long>? {
        val ids = sp.getString("queue", null)?.split(',')?.filter { it.isNotEmpty() } ?: return null
        if (ids.isEmpty()) return null
        return Triple(ids, sp.getInt("queueIndex", 0), sp.getLong("queuePos", 0))
    }
}
