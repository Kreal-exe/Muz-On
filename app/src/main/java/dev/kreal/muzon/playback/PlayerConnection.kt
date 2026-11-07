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
import androidx.media3.exoplayer.ExoPlayer
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
    /** Commands given before the player was reachable, run in order once it is. */
    private val pending = mutableListOf<(Player) -> Unit>()
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
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) mediaItem?.mediaId?.let(prefs::recordPlay)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) || events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                prefs.saveModes(player.shuffleModeEnabled, player.repeatMode)
            }
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) && player.mediaItemCount > 0) {
                // The saved queue follows every insert and removal, so a restart brings back exactly this.
                prefs.saveQueue((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }, player.currentMediaItemIndex, player.currentPosition)
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                prefs.savePosition(player.currentMediaItemIndex, player.currentPosition)
            }
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                // The service can stop while the app stays alive: a dead controller must not stay in use.
                override fun onDisconnected(controller: MediaController) {
                    if (this@PlayerConnection.controller === controller) this@PlayerConnection.controller = null
                }
            })
            .buildAsync()
        future.addListener(
            {
                connecting = false
                val c = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = c
                c.addListener(listener)
                // A command already waiting decides what plays; the saved queue would only be replaced.
                if (pending.isEmpty()) adoptOrRestore(c)
                val waiting = pending.toList()
                pending.clear()
                waiting.forEach { it(player() ?: c) }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun onLibrary(library: Library) {
        this.library = library
        controller?.let(::adoptOrRestore)
    }

    /**
     * Brings the UI in line with the player, whatever was missed. Called several times a second while
     * the player is on screen, so a lost event can never leave a stale button, clock or lyric line.
     */
    fun refresh() {
        val c = controller
        if (c == null || !c.isConnected) {
            controller = null
            connect()
        }
        val player: Player = localPlayer() ?: c?.takeIf { it.isConnected } ?: return
        if (player.mediaItemCount == 0) return
        if (queue.size != player.mediaItemCount) {
            library?.let { lib -> queue = (0 until player.mediaItemCount).mapNotNull { lib.byId[player.getMediaItemAt(it).mediaId] } }
        }
        sync(player)
    }

    private var orderedTimeline: Timeline? = null
    private var orderedShuffle = false

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
        // The saved index counts the saved list; files gone since then must not shift it.
        val start = tracks.indexOfFirst { it.id == ids.getOrNull(index) }.takeIf { it >= 0 } ?: 0
        queue = tracks
        val p: Player = player() ?: c
        p.setMediaItems(tracks.map(::toItem), start, position)
        p.shuffleModeEnabled = prefs.savedShuffle()
        if (p.shuffleModeEnabled) shuffleFrom(start)
        p.repeatMode = prefs.savedRepeat()
        p.prepare()
        sync(p)
    }

    private fun sync(p: Player) {
        windowIndex = p.currentMediaItemIndex
        current = queue.getOrNull(windowIndex)?.takeIf { it.id == p.currentMediaItem?.mediaId }
            ?: p.currentMediaItem?.mediaId?.let { library?.byId?.get(it) }
        wantsToPlay = p.playWhenReady && p.playbackState != Player.STATE_ENDED
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: (current?.durationMs ?: 0L)
        val timeline = p.currentTimeline
        if (timeline !== orderedTimeline || p.shuffleModeEnabled != orderedShuffle) {
            orderedTimeline = timeline
            orderedShuffle = p.shuffleModeEnabled
            val order = orderOf(timeline, p.shuffleModeEnabled)
            if (order != playOrder) playOrder = order
        }
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
                .setArtworkUri("${ArtBitmapLoader.SCHEME}:${t.id}".toUri())
                .build(),
        )
        .build()

    /** The player in this process when the service runs, so a change is there at once. */
    private fun player(): ExoPlayer? = localPlayer() as? ExoPlayer

    /**
     * Runs [block] on the player: the one in this process when it exists, else the controller; with
     * neither yet, after connecting, in the order the commands were given.
     */
    private fun withController(block: (Player) -> Unit) {
        val p: Player? = player() ?: controller
        if (p != null) block(p) else {
            pending += block
            connect()
        }
    }

    /**
     * A shuffled order that starts at [first] and plays everything else after it. The player's own
     * order is random as a whole, so the current track could land in the middle and half the queue
     * would count as played.
     */
    private fun shuffleFrom(first: Int) {
        val exo = player() ?: return
        val n = exo.mediaItemCount
        if (first !in 0 until n) return
        val rest = (0 until n).filter { it != first }.shuffled()
        exo.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder((listOf(first) + rest).toIntArray(), System.nanoTime()))
    }

    fun play(list: List<Track>, start: Int = 0, shuffled: Boolean = false) {
        val tracks = list.filter { it.uri != null }
        if (tracks.isEmpty()) return
        val startTrack = list.getOrNull(start)
        // Shuffle starts on a random track. Passing no start index would keep the current position,
        // so "shuffle" used to go on playing whatever was playing.
        val index = when {
            shuffled -> tracks.indices.random()
            startTrack?.uri != null -> tracks.indexOf(startTrack).coerceAtLeast(0)
            else -> 0
        }
        withController { c ->
            queue = tracks
            c.setMediaItems(tracks.map(::toItem), index, 0L)
            c.shuffleModeEnabled = shuffled
            if (shuffled) shuffleFrom(index)
            c.prepare()
            c.play()
            prefs.saveQueue(tracks.map { it.id }, index, 0)
        }
        main.post { current = tracks[index]; wantsToPlay = true }
    }

    /** Plays on from where the queue stopped, unless it is playing already or there is nothing to play. */
    fun resume() = withController { c ->
        if (c.isPlaying || c.mediaItemCount == 0) return@withController
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun togglePlay() = withController { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) {
                // After the end: on to what was added since, else from the first track of the order.
                if (c.hasNextMediaItem()) c.seekToNextMediaItem() else c.seekToDefaultPosition(c.currentTimeline.getFirstWindowIndex(c.shuffleModeEnabled))
            }
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    /** Puts [track] right after the current one. */
    fun playNext(track: Track) = playNext(listOf(track))

    /**
     * Right after the current track, in the order things play. With shuffle on the player would put
     * new items at random places of its shuffled order; here they go next there too.
     */
    fun playNext(tracks: List<Track>) = insert(tracks, next = true)

    fun addToQueue(track: Track) = addToQueue(listOf(track))

    /** At the end of what plays, shuffled or not. */
    fun addToQueue(tracks: List<Track>) = insert(tracks, next = false)

    private fun insert(tracks: List<Track>, next: Boolean) {
        val items = tracks.filter { it.uri != null }
        if (items.isEmpty()) return
        val exo = localPlayer() as? ExoPlayer
        if (exo != null && exo.mediaItemCount > 0) {
            val before = orderOf(exo.currentTimeline, exo.shuffleModeEnabled)
            val current = exo.currentMediaItemIndex
            val at = if (next) current + 1 else exo.mediaItemCount
            exo.addMediaItems(at, items.map(::toItem))
            if (exo.shuffleModeEnabled) {
                val n = items.size
                val shifted = before.map { if (it >= at) it + n else it }
                val added = (at until at + n).toList()
                val cut = shifted.indexOf(current) + 1
                val order = if (next) shifted.take(cut) + added + shifted.drop(cut) else shifted + added
                exo.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
            }
            refresh()
            return
        }
        withController { c ->
            when {
                c.mediaItemCount == 0 -> play(items)
                // Connected meanwhile: the in-process player keeps the shuffled order right.
                player() != null -> insert(items, next)
                else -> {
                    c.addMediaItems(if (next) c.currentMediaItemIndex + 1 else c.mediaItemCount, items.map(::toItem))
                    refresh()
                }
            }
        }
    }

    fun seekBy(ms: Long) = withController { it.seekTo((it.currentPosition + ms).coerceAtLeast(0)) }


    /** Stops and empties the queue, as the classic player's "clear queue": the mini player goes away. */
    fun clearQueue() = withController { c ->
        c.stop()
        c.clearMediaItems()
        queue = emptyList()
        playOrder = emptyList()
        current = null
        wantsToPlay = false
        prefs.clearSavedQueue()
    }

    fun removeWindow(window: Int) = withController { c ->
        if (window in 0 until c.mediaItemCount && window != c.currentMediaItemIndex) {
            c.removeMediaItem(window)
            queue = queue.toMutableList().also { if (window < it.size) it.removeAt(window) }
            refresh()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun seekToWindow(index: Int) = withController { if (index != it.currentMediaItemIndex) it.seekTo(index, 0) }
    fun toggleShuffle() = withController {
        it.shuffleModeEnabled = !it.shuffleModeEnabled
        // Shuffle on: everything but the current track is still to come.
        if (it.shuffleModeEnabled) shuffleFrom(it.currentMediaItemIndex)
    }
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
        (localPlayer() ?: controller)?.pause()
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
