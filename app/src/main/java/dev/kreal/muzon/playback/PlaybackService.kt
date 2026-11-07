package dev.kreal.muzon.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.kreal.muzon.MainActivity
import dev.kreal.muzon.MuzOnApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as MuzOnApp
    private val favorite = SessionCommand("dev.kreal.muzon.FAVORITE", Bundle.EMPTY)

    private fun audioAttributes(pause: Boolean) = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(if (pause) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes(app.prefs.pauseOnInterrupt), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.skipSilenceEnabled = app.prefs.skipSilence
        // The player ducks music for a short sound and pauses speech; the setting picks which it does.
        scope.launch {
            androidx.compose.runtime.snapshotFlow { app.prefs.pauseOnInterrupt }.collect { player.setAudioAttributes(audioAttributes(it), true) }
        }
        player.setPlaybackSpeed(app.prefs.speed)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // A file that cannot be played is passed over instead of stopping everything.
                if (app.prefs.autoSkip && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateHeart()
        })
        app.exoPlayer = player
        session = MediaSession.Builder(this, player)
            .setBitmapLoader(ArtBitmapLoader(app))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(favorite).build(),
                        )
                        .setMediaButtonPreferences(listOf(heartButton()))
                        .build()

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == favorite.customAction) {
                        val id = session.player.currentMediaItem?.mediaId
                        val track = id?.let { app.repository.library.value?.byId?.get(it) }
                        if (track != null) scope.launch { app.repository.setFavorite(track, !track.favorite) }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        // The heart in the notification follows changes made anywhere.
        scope.launch { app.repository.library.collect { updateHeart() } }
    }

    private fun currentIsFavorite(): Boolean {
        val id = session?.player?.currentMediaItem?.mediaId ?: return false
        return app.repository.library.value?.byId?.get(id)?.favorite == true
    }

    private fun heartButton(): CommandButton =
        CommandButton.Builder(if (currentIsFavorite()) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName("Favourite")
            .setSessionCommand(favorite)
            .build()

    private fun updateHeart() {
        session?.setMediaButtonPreferences(listOf(heartButton()))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (app.prefs.keepPlayingWhenRemoved) super.onTaskRemoved(rootIntent) else pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        app.exoPlayer = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
