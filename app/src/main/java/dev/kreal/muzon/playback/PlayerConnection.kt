package dev.kreal.muzon.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.kreal.muzon.data.Library
import dev.kreal.muzon.data.Prefs
import dev.kreal.muzon.data.Track

/** The UI's view of the player. Every field follows the player's own events, never a guess. */
class PlayerConnection(private val context: Context, private val prefs: Prefs, private val localPlayer: () -> Player?) {
    private var controller: MediaController? = null
    private var connecting = false
    private val main = Handler(Looper.getMainLooper())
    private var pending: ((MediaController) -> Unit)? = null
    private var library: Library? = null

    /** Tracks by window index, in the order they were queued. */
    var queue: List<Track> by mutableStateOf(emptyList())
        private set

    /** Window indices in play order, which differs from the queue order when shuffled. */
    var playOrder: List<Int> by mutableStateOf(emptyList())
        private set

    var windowIndex by mutableIntStateOf(0)
        private set
    var current: Track? by mutableStateOf(null)
        private set

    /** What the play button shows: the user's intent to play, as the player reports it. */
    var wantsToPlay by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    val playOrderPosition: Int get() = playOrder.indexOf(windowIndex).coerceAtLeast(0)

    /** Read from the player itself: a controller only estimates between updates from the session. */
    val positionMs: Long get() = localPlayer()?.currentPosition ?: controller?.currentPosition ?: 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                prefs.savePosition(player.currentMediaItemIndex, player.currentPosition)
            }
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val c = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = c
                c.addListener(listener)
                adoptOrRestore(c)
                pending?.invoke(c)
                pending = null
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun onLibrary(library: Library) {
        this.library = library
        controller?.let(::adoptOrRestore)
    }

    private fun adoptOrRestore(c: MediaController) {
        val lib = library ?: return
        if (c.mediaItemCount > 0) {
            queue = (0 until c.mediaItemCount).mapNotNull { lib.byId[c.getMediaItemAt(it).mediaId] }
            sync(c)
            return
        }
        val (ids, index, position) = prefs.savedQueue() ?: return
        val tracks = ids.mapNotNull(lib.byId::get).filter { it.uri != null }
        if (tracks.isEmpty()) return
        queue = tracks
        c.setMediaItems(tracks.map(::toItem), index.coerceIn(0, tracks.lastIndex), position)
        c.prepare()
        sync(c)
    }

    private fun sync(p: Player) {
        windowIndex = p.currentMediaItemIndex
        current = queue.getOrNull(windowIndex)?.takeIf { it.id == p.currentMediaItem?.mediaId }
            ?: p.currentMediaItem?.mediaId?.let { library?.byId?.get(it) }
        wantsToPlay = p.playWhenReady && p.playbackState != Player.STATE_ENDED
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: (current?.durationMs ?: 0L)
        val order = orderOf(p.currentTimeline, p.shuffleModeEnabled)
        if (order != playOrder) playOrder = order
    }

    private fun orderOf(timeline: Timeline, shuffled: Boolean): List<Int> {
        if (timeline.isEmpty) return emptyList()
        val out = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffled)
        while (i != C.INDEX_UNSET) {
            out += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffled)
        }
        return out
    }

    private fun toItem(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.id)
        .setUri(t.uri!!.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist.ifBlank { t.albumArtist })
                .setAlbumTitle(t.album)
                .setAlbumArtist(t.albumArtist)
                .build(),
        )
        .build()

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending = block
            connect()
        }
    }

    fun play(list: List<Track>, start: Int = 0, shuffled: Boolean = false) {
        val tracks = list.filter { it.uri != null }
        if (tracks.isEmpty()) return
        val startTrack = list.getOrNull(start)
        val index = if (startTrack?.uri != null) tracks.indexOf(startTrack).coerceAtLeast(0) else 0
        queue = tracks
        withController { c ->
            c.shuffleModeEnabled = shuffled
            c.setMediaItems(tracks.map(::toItem), if (shuffled) C.INDEX_UNSET else index, C.TIME_UNSET)
            c.prepare()
            c.play()
            prefs.saveQueue(tracks.map { it.id }, index, 0)
        }
        main.post { current = tracks[index]; wantsToPlay = true }
    }

    fun togglePlay() = withController { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun seekToWindow(index: Int) = withController { if (index != it.currentMediaItemIndex) it.seekTo(index, 0) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    private var sleepAt = 0L
    var sleepEndsAt by mutableLongStateOf(0L)
        private set
    private val sleep = Runnable {
        controller?.pause()
        sleepEndsAt = 0L
    }

    fun setSleepTimer(minutes: Int) {
        main.removeCallbacks(sleep)
        if (minutes <= 0) {
            sleepEndsAt = 0L
            return
        }
        sleepAt = minutes * 60_000L
        sleepEndsAt = System.currentTimeMillis() + sleepAt
        main.postDelayed(sleep, sleepAt)
    }
}
