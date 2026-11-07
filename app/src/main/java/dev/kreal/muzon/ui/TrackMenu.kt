package dev.kreal.muzon.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.kreal.muzon.R
import dev.kreal.muzon.data.Lyrics
import dev.kreal.muzon.data.LyricsReader
import dev.kreal.muzon.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The three dots of a track, with what the classic player offers that fits here: play next, add to
 * the queue, the album and the artist, send the file, its details. In the queue, "remove" replaces
 * "add". Nothing here changes a file or a playlist.
 */
@Composable
fun TrackMenu(
    vm: AppViewModel,
    track: Track,
    tint: Color,
    queueWindow: Int? = null,
    open: Boolean? = null,
    setOpen: ((Boolean) -> Unit)? = null,
) {
    var own by remember { mutableStateOf(false) }
    val isOpen = open ?: own
    val change: (Boolean) -> Unit = setOpen ?: { own = it }
    var details by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val library = vm.repository.library.value
    Box {
        IconTap(Icons.Rounded.MoreVert, tint, 20.dp, 36.dp, label = stringResource(R.string.menu_more)) { change(true) }
        DropdownMenu(isOpen, { change(false) }) {
            @Composable
            fun item(icon: ImageVector, text: Int, enabled: Boolean = true, action: () -> Unit) {
                DropdownMenuItem(
                    text = { Text(stringResource(text), style = Type.body) },
                    leadingIcon = { Icon(icon, null) },
                    enabled = enabled,
                    onClick = {
                        change(false)
                        action()
                    },
                )
            }
            val playable = track.uri != null
            if (queueWindow == null) item(Icons.Rounded.PlayArrow, R.string.play, playable) { vm.connection.play(listOf(track)) }
            item(Icons.AutoMirrored.Rounded.QueueMusic, R.string.menu_play_next, playable) { vm.connection.playNext(track) }
            if (queueWindow == null) {
                item(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.menu_add_queue, playable) { vm.connection.addToQueue(track) }
            } else {
                item(Icons.Rounded.RemoveCircleOutline, R.string.menu_remove_queue, queueWindow != vm.connection.windowIndex) {
                    vm.connection.removeWindow(queueWindow)
                }
            }
            if (library?.albumsByKey?.containsKey(track.albumKey) == true) {
                item(Icons.Rounded.Album, R.string.menu_go_album) { vm.goTo(Route.AlbumRoute(track.albumKey)) }
            }
            if (library?.artistsByKey?.containsKey(track.artistKey) == true) {
                item(Icons.Rounded.Mic, R.string.menu_go_artist) { vm.goTo(Route.ArtistRoute(track.artistKey)) }
            }
            item(Icons.Rounded.Share, R.string.menu_share, playable) {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("audio/*")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse(track.uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, track.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            val favorite = library?.byId?.get(track.id)?.favorite == true
            item(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (favorite) R.string.fav_remove else R.string.fav_add) {
                vm.toggleFavorite(track.id)
            }
            item(Icons.Rounded.Info, R.string.menu_details) { details = true }
        }
    }
    if (details) TrackDetails(track) { details = false }
}

@Composable
private fun TrackDetails(track: Track, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val info by produceState<Pair<String, String>?>(null, track.id) {
        value = withContext(Dispatchers.IO) {
            val size = track.filePath?.let { File(it).length() }?.takeIf { it > 0 }?.let { "%.1f MB".format(it / 1_048_576.0) }.orEmpty()
            val bitrate = runCatching {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(context, Uri.parse(track.uri))
                        extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { "${it / 1000} kbps" }.orEmpty()
                    } finally {
                        release()
                    }
                }
            }.getOrDefault("")
            size to bitrate
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(p.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(track.title.trim(), Type.section, maxLines = 3)
            @Composable
            fun row(label: Int, value: String) {
                if (value.isBlank()) return
                Column {
                    Txt(stringResource(label), Type.tiny, p.muted)
                    SelectionContainer { Text(value, style = Type.body, color = p.text) }
                }
            }
            row(R.string.sort_artist, track.artist)
            row(R.string.sort_album, track.album)
            row(R.string.sort_genre, track.genre)
            row(R.string.sort_duration, formatTime(track.durationMs))
            row(R.string.d_format, track.filePath?.substringAfterLast('.', "")?.uppercase().orEmpty())
            row(R.string.d_bitrate, info?.second.orEmpty())
            row(R.string.d_size, info?.first.orEmpty())
            row(R.string.d_path, track.filePath ?: track.sourcePath)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Tappable(onDismiss, Modifier.clip(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Txt(stringResource(R.string.close), Type.bodyBold, p.accent)
                }
            }
        }
    }
}

/**
 * All the lyrics in a window of their own. Timed lines follow the song and a tap on one jumps there;
 * the text view is plain and selectable, and the copy button takes all of it.
 */
@Composable
fun LyricsDialog(vm: AppViewModel, track: Track, positionMs: () -> Long, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    val lyrics by produceState<Lyrics?>(null, track.id) { value = LyricsReader.read(track) }
    val current = lyrics ?: return
    if (current.isEmpty) return
    val hasTimed = current.timed.isNotEmpty()
    var timedView by remember(track.id) { mutableStateOf(hasTimed) }
    val allText = remember(current) {
        (if (current.timed.isNotEmpty()) current.timed.map { it.text } else current.plain).joinToString("\n")
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(22.dp))
                .background(p.surface)
                .padding(top = 16.dp, start = 18.dp, end = 10.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(track.title.trim(), Type.section, maxLines = 2)
                    Txt(track.artist, Type.small, p.muted)
                }
                IconTap(Icons.Rounded.ContentCopy, p.text, 20.dp, 40.dp, label = stringResource(R.string.copy)) {
                    clipboard.setText(AnnotatedString(allText))
                }
            }
            if (hasTimed && current.plain.isNotEmpty() || hasTimed) {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(true to R.string.lyrics_synced, false to R.string.lyrics_text).forEach { (timed, label) ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (timedView == timed) p.accent else p.surfaceHigh)
                                .clickable { timedView = timed }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) { Txt(stringResource(label), Type.small, if (timedView == timed) Color.White else p.text) }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (timedView && hasTimed) {
                    val timed = current.timed
                    val state = rememberLazyListState()
                    var active by remember { mutableStateOf(-1) }
                    LaunchedEffect(timed) {
                        while (true) {
                            val now = positionMs() + vm.prefs.lyricsLeadMs
                            val i = timed.indexOfLast { it.timeMs <= now }
                            if (i != active) {
                                active = i
                                if (i >= 0) state.animateScrollToItem((i - 3).coerceAtLeast(0))
                            }
                            kotlinx.coroutines.delay(120)
                        }
                    }
                    LazyColumn(state = state, contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(timed, key = { i, _ -> i }) { i, line ->
                            Text(
                                line.text,
                                style = Type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(if (i == active) 700 else 500)),
                                color = if (i == active) p.accent else p.text.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { vm.connection.seekTo(line.timeMs) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                            item { Text(allText, style = Type.body.copy(lineHeight = Type.body.fontSize * 1.5f), color = p.text) }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Tappable(onDismiss, Modifier.clip(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Txt(stringResource(R.string.close), Type.bodyBold, p.accent)
                }
            }
        }
    }
}

/** The heart of a track, filled when it is a favourite; reads the library so it follows every change. */
@Composable
fun FavoriteHeart(vm: AppViewModel, trackId: String, tint: Color, size: androidx.compose.ui.unit.Dp = 24.dp, box: androidx.compose.ui.unit.Dp = 44.dp, modifier: Modifier = Modifier) {
    val library by vm.repository.library.collectAsState()
    val on = library?.byId?.get(trackId)?.favorite == true
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    IconTap(
        if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        if (on) Color(0xFFFF3B5C) else tint,
        size, box, modifier,
        label = stringResource(if (on) R.string.fav_remove else R.string.fav_add),
    ) {
        if (vm.prefs.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        vm.toggleFavorite(trackId)
    }
}

/** Menu for a group of tracks: an artist, an album, a playlist, a folder. */
@Composable
fun CollectionMenu(vm: AppViewModel, tracks: List<Track>, open: Boolean, setOpen: (Boolean) -> Unit) {
    DropdownMenu(open, { setOpen(false) }) {
        @Composable
        fun item(icon: ImageVector, text: Int, action: () -> Unit) {
            DropdownMenuItem(
                text = { Text(stringResource(text), style = Type.body) },
                leadingIcon = { Icon(icon, null) },
                onClick = {
                    setOpen(false)
                    action()
                },
            )
        }
        item(Icons.Rounded.PlayArrow, R.string.play) { vm.connection.play(tracks) }
        item(Icons.Rounded.Shuffle, R.string.shuffle) { vm.connection.play(tracks, shuffled = true) }
        item(Icons.AutoMirrored.Rounded.QueueMusic, R.string.menu_play_next) { vm.connection.playNext(tracks) }
        item(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.menu_add_queue) { vm.connection.addToQueue(tracks) }
    }
}

/** The three dots of a group of tracks, for page headers. */
@Composable
fun CollectionMenuButton(vm: AppViewModel, tracks: List<Track>, tint: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconTap(Icons.Rounded.MoreVert, tint, 22.dp, 44.dp, label = stringResource(R.string.menu_more)) { open = true }
        CollectionMenu(vm, tracks, open) { open = it }
    }
}
