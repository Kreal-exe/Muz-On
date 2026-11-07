package dev.kreal.muzon.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.SuccessResult
import coil.size.Size as CoilSize
import dev.kreal.muzon.R
import dev.kreal.muzon.data.AppTheme
import dev.kreal.muzon.data.LyricLine
import dev.kreal.muzon.data.Lyrics
import dev.kreal.muzon.data.LyricsReader
import dev.kreal.muzon.data.LyricsSize
import dev.kreal.muzon.data.Track
import dev.kreal.muzon.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

private val sheetSpring = spring<Float>(dampingRatio = 1f, stiffness = 230f)

/** Saturated flat colour of a track's cover, kept dark enough for its text to read. */
@Composable
private fun coverColor(track: Track?, enabled: Boolean, fallback: Color): CoverColors {
    val context = LocalContext.current
    val target by produceState(fallback, track?.id, enabled) {
        val key = track?.takeIf { enabled }?.let { Art.key(it, false) }
        if (key == null) {
            value = fallback
            return@produceState
        }
        val result = withContext(Dispatchers.IO) {
            val request = Art.request(context, key, allowHardware = false).newBuilder().size(CoilSize(128, 128)).build()
            val drawable = (Coil.imageLoader(context).execute(request) as? SuccessResult)?.drawable
            val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@withContext null
            val palette = Palette.from(bitmap).generate()
            val swatch = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch ?: return@withContext null
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(swatch.rgb, hsl)
            hsl[1] = hsl[1].coerceAtLeast(0.45f)
            hsl[2] = hsl[2].coerceIn(0.24f, 0.46f)
            Color(ColorUtils.HSLToColor(hsl))
        }
        value = result ?: fallback
    }
    return CoverColors(target, animateColorAsState(target, tween(450), label = "cover"))
}

/** [fill] animates and is read only while drawing; [target] is for icons, so a change recomposes once. */
private class CoverColors(val target: Color, val fill: androidx.compose.runtime.State<Color>)

private class Position(val conn: PlayerConnection) {
    val value = mutableLongStateOf(0L)
    var scrubbing = mutableStateOf<Long?>(null)
}

@Composable
private fun rememberPosition(conn: PlayerConnection): Position {
    val position = remember(conn) { Position(conn) }
    LaunchedEffect(conn) {
        while (true) {
            conn.refresh()
            position.value.longValue = conn.positionMs
            delay(if (conn.wantsToPlay) 80 else 300)
        }
    }
    return position
}

// ---- Pieces shared by both layouts

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtPager(conn: PlayerConnection, modifier: Modifier, swipeEnabled: () -> Boolean, corner: Dp = 16.dp) {
    val order = conn.playOrder
    val queue = conn.queue
    val pager = rememberPagerState(initialPage = conn.playOrderPosition) { order.size.coerceAtLeast(1) }
    LaunchedEffect(conn.playOrderPosition, order.size) {
        val target = conn.playOrderPosition
        if (pager.currentPage != target && !pager.isScrollInProgress && target < pager.pageCount) {
            if (abs(pager.currentPage - target) > 1) pager.scrollToPage(target) else pager.animateScrollToPage(target)
        }
    }
    // Only a swipe of the finger changes the track. Reacting to the page settling instead would
    // jump to another track whenever the order changes, for instance when shuffle is switched.
    val latestOrder by androidx.compose.runtime.rememberUpdatedState(order)
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    var swiped by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) swiped = true }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { moving ->
            if (!moving && swiped) {
                swiped = false
                val page = pager.currentPage
                if (page != conn.playOrderPosition) latestOrder.getOrNull(page)?.let(conn::seekToWindow)
            }
        }
    }
    // A resize or an interrupted swipe must not leave the artwork resting between two pages.
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.currentPageOffsetFraction }.collect { (moving, offset) ->
            if (!moving && offset != 0f) pager.scrollToPage(pager.currentPage)
        }
    }
    val canSwipe by remember { derivedStateOf(swipeEnabled) }
    val shape = RoundedCornerShape(corner)
    HorizontalPager(
        state = pager,
        modifier = modifier,
        userScrollEnabled = canSwipe,
        beyondViewportPageCount = 1,
        pageSpacing = 14.dp,
        key = { page -> order.getOrNull(page)?.let { queue.getOrNull(it)?.id } ?: page },
    ) { page ->
        val track = order.getOrNull(page)?.let { queue.getOrNull(it) } ?: conn.current
        TrackArt(track, Modifier.fillMaxSize(), shape, large = true)
    }
}

@Composable
private fun SeekBar(position: Position, conn: PlayerConnection, on: Color, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val duration = conn.durationMs.coerceAtLeast(1L)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Elapsed(position, on)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(30.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        val ms = (offset.x / size.width * duration).toLong().coerceIn(0, duration)
                        conn.seekTo(ms)
                        position.value.longValue = ms
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { position.scrubbing.value = (it.x / size.width * duration).toLong() },
                        onDragEnd = {
                            position.scrubbing.value?.let {
                                conn.seekTo(it)
                                position.value.longValue = it
                            }
                            position.scrubbing.value = null
                        },
                        onDragCancel = { position.scrubbing.value = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            position.scrubbing.value = (change.position.x / size.width * duration).toLong().coerceIn(0, duration)
                        },
                    )
                }
                .drawBehind {
                    val shown = position.scrubbing.value ?: position.value.longValue
                    val fraction = (shown.toFloat() / duration).coerceIn(0f, 1f)
                    val h = if (position.scrubbing.value != null) 11.dp.toPx() else 8.dp.toPx()
                    val top = (size.height - h) / 2
                    val r = CornerRadius(h / 2)
                    drawRoundRect(on.copy(alpha = 0.26f), Offset(0f, top), Size(size.width, h), r)
                    if (theme == AppTheme.Aero) {
                        drawRoundRect(
                            Brush.verticalGradient(0f to Color.White, 0.5f to on, 1f to on.copy(alpha = 0.7f), startY = top, endY = top + h),
                            Offset(0f, top), Size(size.width * fraction, h), r,
                        )
                    } else {
                        drawRoundRect(on, Offset(0f, top), Size((size.width * fraction).coerceAtLeast(h), h), r)
                    }
                },
        )
        Remaining(position, conn, on)
    }
}

@Composable
private fun Elapsed(position: Position, on: Color) {
    val seconds by remember { derivedStateOf { (position.scrubbing.value ?: position.value.longValue) / 1000 } }
    Txt(formatTime(seconds * 1000), Type.tiny, on, Modifier.width(40.dp))
}

@Composable
private fun Remaining(position: Position, conn: PlayerConnection, on: Color) {
    val seconds by remember { derivedStateOf { (position.scrubbing.value ?: position.value.longValue) / 1000 } }
    Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
        Txt("−" + formatTime((conn.durationMs - seconds * 1000).coerceAtLeast(0)), Type.tiny, on)
    }
}

@Composable
private fun PlayButton(conn: PlayerConnection, size: Dp, background: Color, foreground: Color) {
    val theme = LocalAppTheme.current
    val playing = conn.wantsToPlay
    val pause by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "play")
    val pressed = remember { mutableFloatStateOf(1f) }
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pressed.floatValue
                scaleY = pressed.floatValue
            }
            .block(background, theme, size / 2)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed.floatValue = 0.92f
                        tryAwaitRelease()
                        pressed.floatValue = 1f
                    },
                    onTap = { conn.togglePlay() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Pause, stringResource(R.string.pause), tint = foreground,
            modifier = Modifier.size(size * 0.52f).graphicsLayer {
                alpha = pause
                scaleX = 0.6f + 0.4f * pause
                scaleY = 0.6f + 0.4f * pause
            },
        )
        Icon(
            Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = foreground,
            modifier = Modifier.size(size * 0.58f).graphicsLayer {
                alpha = 1f - pause
                scaleX = 1f - 0.4f * pause
                scaleY = 1f - 0.4f * pause
            },
        )
    }
}

@Composable
private fun Controls(conn: PlayerConnection, on: Color, cover: Color, big: Dp = 70.dp, modifier: Modifier = Modifier) {
    val dim = on.copy(alpha = 0.55f)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconTap(Icons.Rounded.Shuffle, if (conn.shuffle) on else dim, 24.dp, label = stringResource(R.string.shuffle)) { conn.toggleShuffle() }
        IconTap(Icons.Rounded.SkipPrevious, on, 38.dp, 52.dp, label = stringResource(R.string.previous)) { conn.previous() }
        PlayButton(conn, big, on, cover)
        IconTap(Icons.Rounded.SkipNext, on, 38.dp, 52.dp, label = stringResource(R.string.next)) { conn.next() }
        IconTap(
            if (conn.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            if (conn.repeatMode == Player.REPEAT_MODE_OFF) dim else on, 24.dp, label = stringResource(R.string.repeat),
        ) { conn.cycleRepeat() }
    }
}

/** Lyrics lie over part of the artwork; a sideways swipe across them changes the track all the same. */
private fun Modifier.swipeForTrack(conn: PlayerConnection): Modifier = pointerInput(conn) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total < -64.dp.toPx()) conn.next() else if (total > 64.dp.toPx()) conn.previous()
        },
        onHorizontalDrag = { change, amount ->
            change.consume()
            total += amount
        },
    )
}

// ---- Lyrics

private fun lyricSize(size: LyricsSize, big: Boolean) = when (size) {
    LyricsSize.Small -> if (big) 18.sp else 16.sp
    LyricsSize.Medium -> if (big) 22.sp else 19.sp
    LyricsSize.Large -> if (big) 26.sp else 23.sp
}

private fun lyricStyle(size: androidx.compose.ui.unit.TextUnit) = Type.bodyBold.copy(
    fontWeight = androidx.compose.ui.text.font.FontWeight(700),
    fontSize = size,
    lineHeight = size * 1.22f,
    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 1f), 8f),
)

/**
 * The current line at the bottom of the artwork, over a shade, the next one sliding in from below.
 * Tracks without timed lyrics show only their artwork.
 */
@Composable
private fun CoverLyricLine(vm: AppViewModel, position: Position, modifier: Modifier) {
    val track = vm.connection.current
    val lyrics by produceState<Lyrics?>(null, track?.id) { value = track?.let { LyricsReader.read(it) } }
    val timed = lyrics?.timed.orEmpty()
    if (timed.isEmpty()) return
    val lead = vm.prefs.lyricsLeadMs
    val index by remember(timed, lead) { derivedStateOf { activeLine(timed, position.value.longValue + lead) } }
    val style = lyricStyle(lyricSize(vm.prefs.lyricsSize, big = true))
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))) }
            .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = index,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically(tween(260)) { it / 2 } + androidx.compose.animation.fadeIn(tween(260)))
                    .togetherWith(androidx.compose.animation.slideOutVertically(tween(260)) { -it / 2 } + androidx.compose.animation.fadeOut(tween(200)))
                    .using(androidx.compose.animation.SizeTransform(clip = false))
            },
            label = "line",
        ) { i ->
            androidx.compose.material3.Text(timed.getOrNull(i)?.text.orEmpty(), style = style, color = Color.White, maxLines = 3, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** All the lyrics, opened by tapping the artwork; a tap on a timed line jumps to it. */
@Composable
private fun FullLyrics(vm: AppViewModel, position: Position, modifier: Modifier) {
    val conn = vm.connection
    val track = conn.current
    val lyrics by produceState<Lyrics?>(null, track?.id) { value = track?.let { LyricsReader.read(it) } }
    val view = LocalView.current
    val keep = vm.prefs.keepScreenForLyrics
    DisposableEffect(keep) {
        view.keepScreenOn = keep
        onDispose { view.keepScreenOn = false }
    }
    val style = lyricStyle(lyricSize(vm.prefs.lyricsSize, big = false))
    val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { vm.lyricsOpen = false }
            .padding(top = systemTop + 8.dp, bottom = systemBottom + 8.dp, start = 16.dp, end = 16.dp),
    ) {
        val current = lyrics
        when {
            current == null -> Unit
            current.isEmpty -> Txt(stringResource(R.string.no_lyrics), Type.body, Color.White.copy(alpha = 0.7f), Modifier.align(Alignment.Center))
            else -> LyricsList(current, position, conn, Color.White, style, vm.prefs.lyricsLeadMs)
        }
    }
}

@Composable
private fun LyricsList(lyrics: Lyrics, position: Position, conn: PlayerConnection, on: Color, style: androidx.compose.ui.text.TextStyle, leadMs: Int) {
    val state = rememberLazyListState()
    val timed = lyrics.timed
    val active by remember(timed, leadMs) { derivedStateOf { activeLine(timed, position.value.longValue + leadMs) } }
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (active >= 0 && System.currentTimeMillis() - lastTouch > 3000) state.animateScrollToItem((active - 3).coerceAtLeast(0))
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            // Any touch pauses following the song for a moment, so the list does not fight the finger.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    lastTouch = System.currentTimeMillis()
                }
            }
        },
        contentPadding = PaddingValues(vertical = 40.dp),
    ) {
        itemsIndexed(timed, key = { i, _ -> i }, contentType = { _, _ -> 0 }) { i, line ->
            LyricRow(line, i == active, on, style) {
                conn.seekTo(line.timeMs)
                position.value.longValue = line.timeMs
                lastTouch = 0L
            }
        }
        if (lyrics.plain.isNotEmpty()) {
            if (timed.isNotEmpty()) item { Spacer(Modifier.height(28.dp)) }
            itemsIndexed(lyrics.plain, key = { i, _ -> "p$i" }, contentType = { _, _ -> 1 }) { _, line ->
                androidx.compose.material3.Text(line, style = style, color = on.copy(alpha = if (timed.isEmpty()) 1f else 0.6f), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, on: Color, style: androidx.compose.ui.text.TextStyle, onTap: () -> Unit) {
    val shown by animateFloatAsState(if (active) 1f else 0f, tween(140), label = "line")
    androidx.compose.material3.Text(
        line.text,
        style = style,
        color = on,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 0.45f + 0.55f * shown
                val scale = 0.94f + 0.06f * shown
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 6.dp),
    )
}

private fun activeLine(timed: List<LyricLine>, now: Long): Int {
    var low = 0
    var high = timed.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) / 2
        if (timed[mid].timeMs <= now) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

// ---- Queue card

/**
 * The card of the classic player: the current track on top, "Up next" with the time left, then the
 * queue. The list opens on what plays next; what already played is above it, dimmed.
 */
@Composable
private fun QueueCard(
    conn: PlayerConnection,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    extraBottom: Dp = 0.dp,
    resetKey: Any? = null,
) {
    val p = LocalPalette.current
    val order = conn.playOrder
    val queue = conn.queue
    val position = conn.playOrderPosition
    val current = conn.current
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = position + 1)
    LaunchedEffect(position, order, resetKey) {
        if (order.isEmpty()) return@LaunchedEffect
        val target = (position + 1).coerceAtMost(order.lastIndex)
        if (abs(state.firstVisibleItemIndex - target) > 12) state.scrollToItem(target) else state.animateScrollToItem(target)
    }
    val leftMs = remember(order, queue, position) {
        var sum = 0L
        for (i in position + 1 until order.size) sum += queue.getOrNull(order[i])?.durationMs ?: 0L
        sum
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(p.surface)) {
        Column(headerModifier.fillMaxWidth()) {
            if (current != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrackArt(current, Modifier.size(48.dp), Radius6)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(current.title.trim(), Type.bodyBold, p.text, maxLines = 2)
                        Txt(listOfNotNull(current.artist, current.album.takeIf { it.isNotBlank() }).joinToString(" · "), Type.small, p.muted)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(stringResource(R.string.up_next) + " · " + formatLong(leftMs), Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), p.muted)
            }
        }
        LazyColumn(
            state = state,
            modifier = listModifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = systemBottom + 6.dp + extraBottom),
        ) {
            itemsIndexed(order, key = { _, window -> window }, contentType = { _, _ -> 0 }) { index, window ->
                val track = queue.getOrNull(window) ?: return@itemsIndexed
                QueueRow(track, index - position, dimmed = index <= position) { conn.seekToWindow(window) }
            }
        }
    }
}

/** A queue row; [offset] is how far it is from the current track, as the classic player numbers it. */
@Composable
private fun QueueRow(track: Track, offset: Int, dimmed: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer { alpha = if (dimmed) 0.5f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            Txt(if (offset > 0) "+$offset" else offset.toString(), Type.tiny, p.muted)
        }
        Spacer(Modifier.width(6.dp))
        TrackArt(track, Modifier.size(42.dp), Radius6)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(track.title.trim(), Type.body, p.text, maxLines = 3)
            Txt(track.artist, Type.small, p.muted)
        }
        Spacer(Modifier.width(6.dp))
        Txt(formatTime(track.durationMs), Type.tiny, p.muted)
    }
}

// ---- Compact: mini player that grows into the full one under the finger

private class Geometry(
    val width: Float,
    val height: Float,
    val miniTop: Float,
    val miniHeight: Float,
    val miniMargin: Float,
    val artX: Float,
    val artY: Float,
    val artSize: Float,
    val miniArtX: Float,
    val miniArtY: Float,
    val miniArtSize: Float,
)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun geometry(density: Density, width: Float, height: Float, tabs: Dp): Geometry = with(density) {
    val miniHeight = 58.dp.toPx()
    val miniTop = height - tabs.toPx() - miniHeight - 4.dp.toPx()
    val margin = 8.dp.toPx()
    // The artwork spans the width, as in the classic player, unless the screen is too short for it.
    val artSize = min(width, height * 0.5f)
    Geometry(
        width, height, miniTop, miniHeight, margin,
        artX = (width - artSize) / 2,
        artY = 0f,
        artSize = artSize,
        miniArtX = margin + 8.dp.toPx(),
        miniArtY = 8.dp.toPx(),
        miniArtSize = 42.dp.toPx(),
    )
}

@Composable
fun CompactPlayer(vm: AppViewModel, tabsHeight: Dp, modifier: Modifier = Modifier) {
    val conn = vm.connection
    val track = conn.current ?: return
    val p = LocalPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cover = coverColor(track, vm.prefs.coverColor, p.surfaceHigh)
    val onCover = Blocks.on(cover.target)
    val position = rememberPosition(conn)
    val lyrics by produceState<Lyrics?>(null, track.id) { value = LyricsReader.read(track) }

    var settle by remember { mutableStateOf<Job?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val g = remember(constraints.maxWidth, constraints.maxHeight, tabsHeight) {
            geometry(density, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), tabsHeight)
        }
        fun settleTo(target: Float, velocity: Float = 0f) {
            settle?.cancel()
            settle = scope.launch {
                // A hard flick should not cross the screen in a few frames.
                animate(vm.sheet, target, velocity.coerceIn(-2.5f, 2.5f), sheetSpring) { v, _ -> vm.sheet = v.coerceIn(0f, 1f) }
                if (target == 0f) vm.queueFull = 0f
            }
        }
        val dragState = rememberDraggableState { delta -> vm.sheet = (vm.sheet - delta / g.miniTop).coerceIn(0f, 1f) }
        val sheetScroll = remember(g) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0 && vm.sheet < 1f) {
                        vm.sheet = (vm.sheet - available.y / g.miniTop).coerceIn(0f, 1f)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0 && source == NestedScrollSource.UserInput) {
                        vm.sheet = (vm.sheet - available.y / g.miniTop).coerceIn(0f, 1f)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (vm.sheet < 1f) {
                        settleTo(if (vm.sheet > 0.7f && available.y < 1500f) 1f else 0f, -available.y / g.miniTop)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        // The page behind dims as the player rises, which gives the sheet its depth.
        Box(Modifier.fillMaxSize().drawBehind { if (vm.sheet > 0f) drawRect(Color.Black.copy(alpha = 0.6f * vm.sheet)) })
        Box(
            Modifier
                .fillMaxSize()
                .preferredFrameRate(FrameRateCategory.High)
                .graphicsLayer { translationY = (1f - vm.sheet) * g.miniTop }
                .drawBehind {
                    val f = vm.sheet
                    val inset = lerp(g.miniMargin, 0f, f)
                    drawRoundRect(
                        cover.fill.value,
                        Offset(inset, 0f),
                        Size(size.width - inset * 2, lerp(g.miniHeight, size.height, f)),
                        CornerRadius(if (f < 0.9f) lerp(14.dp.toPx(), 28.dp.toPx(), f / 0.9f) else lerp(28.dp.toPx(), 0f, (f - 0.9f) / 0.1f)),
                    )
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { settle?.cancel() },
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity > 900f -> 0f
                            velocity < -900f -> 1f
                            vm.sheet > 0.5f -> 1f
                            else -> 0f
                        }
                        settleTo(target, -velocity / g.miniTop)
                    },
                ),
        ) {
            val pageAlpha = Modifier.graphicsLayer {
                alpha = ((vm.sheet - 0.35f) / 0.25f).coerceIn(0f, 1f)
                translationY = if (vm.sheet > 0.05f) 0f else -100000f
            }
            // Controls under the artwork; both stay composed, so nothing is built while the finger moves.
            val controlsBottom = remember { mutableFloatStateOf(g.artSize + with(density) { 140.dp.toPx() }) }
            Column(
                pageAlpha
                    .fillMaxWidth()
                    .padding(top = with(density) { (g.artY + g.artSize).toDp() } + 6.dp, start = 14.dp, end = 14.dp)
                    .onPlaced { controlsBottom.floatValue = g.artY + g.artSize + with(density) { 6.dp.toPx() } + it.size.height },
            ) {
                SeekBar(position, conn, onCover)
                Controls(conn, onCover, cover.target, big = 64.dp)
            }

            // Mini row.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { g.miniHeight.toDp() })
                    .graphicsLayer {
                        val visible = vm.sheet < 0.15f
                        alpha = (1f - vm.sheet / 0.15f).coerceIn(0f, 1f)
                        translationY = if (visible) 0f else -100000f // out of reach of touches
                    }
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { settleTo(1f) }
                    .padding(start = with(density) { (g.miniArtX + g.miniArtSize).toDp() } + 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Txt(track.title.trim(), Type.bodyBold, onCover)
                    Txt(track.artist, Type.small, onCover.copy(alpha = 0.8f))
                }
                IconTap(Icons.Rounded.SkipPrevious, onCover, 26.dp, 40.dp, label = stringResource(R.string.previous)) { conn.previous() }
                PlayButton(conn, 38.dp, onCover, cover.target)
                IconTap(Icons.Rounded.SkipNext, onCover, 26.dp, 40.dp, label = stringResource(R.string.next)) { conn.next() }
            }

            // One artwork travels from the mini player to the top of the full page.
            Box(
                Modifier
                    .size(with(density) { g.artSize.toDp() })
                    .graphicsLayer {
                        val f = vm.sheet
                        val s = lerp(g.miniArtSize, g.artSize, f) / g.artSize
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = lerp(g.miniArtX, g.artX, f)
                        translationY = lerp(g.miniArtY, g.artY, f)
                        shape = RoundedCornerShape(lerp(6f, 0f, f).dp * (1f / s.coerceAtLeast(0.05f)))
                        clip = true
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { if (vm.sheet < 0.5f) settleTo(1f) else vm.lyricsOpen = true }
                    }
                    .swipeForTrack(conn),
            ) {
                ArtPager(conn, Modifier.fillMaxSize(), { vm.sheet > 0.98f }, 0.dp)
                CoverLyricLine(vm, position, Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = ((vm.sheet - 0.7f) / 0.3f).coerceIn(0f, 1f) })
            }

            // Toolbar over the artwork: the way back down.
            Box(
                pageAlpha
                    .fillMaxWidth()
                    .height(systemTop + 52.dp)
                    .drawBehind { drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent)) }
                    .padding(top = systemTop, start = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconTap(Icons.Rounded.KeyboardArrowDown, Color.White, 30.dp, 48.dp, label = stringResource(R.string.back)) { settleTo(0f) }
            }

            QueueSheet(vm, g, controlsBottom, sheetScroll, pageAlpha)

            if (vm.lyricsOpen && vm.sheet > 0.5f) FullLyrics(vm, position, Modifier.fillMaxSize())
        }

        if (vm.prefs.lyricPopup) {
            LyricPopup(
                vm.prefs.lyricsLeadMs,
                lyrics,
                position,
                cover.fill,
                onCover,
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { g.miniTop.toDp() } - 6.dp)
                    .graphicsLayer {
                        alpha = (1f - vm.sheet / 0.12f).coerceIn(0f, 1f)
                        translationY = if (vm.sheet < 0.12f) 0f else -100000f
                    },
            ) {
                vm.lyricsOpen = true
                settleTo(1f)
            }
        }
    }
}

/**
 * The queue card rests under the controls and is pulled up over the artwork. A finger going up first
 * raises it, then scrolls it; going down scrolls back to what already played, then lowers the card,
 * then lowers the player.
 */
@Composable
private fun QueueSheet(vm: AppViewModel, g: Geometry, restY: androidx.compose.runtime.MutableFloatState, sheetScroll: NestedScrollConnection, pageAlpha: Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPx = with(density) { (systemTop + 8.dp).toPx() }
    val travel = { (restY.floatValue - topPx).coerceAtLeast(1f) }
    fun settleQueue(target: Float, velocity: Float = 0f) {
        scope.launch {
            animate(vm.queueFull, target, velocity.coerceIn(-2.5f, 2.5f), sheetSpring) { v, _ -> vm.queueFull = v.coerceIn(0f, 1f) }
        }
    }
    val queueScroll = remember(sheetScroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (vm.sheet < 1f) return sheetScroll.onPreScroll(available, source)
                if (available.y < 0 && vm.queueFull < 1f && source == NestedScrollSource.UserInput) {
                    vm.queueFull = (vm.queueFull - available.y / travel()).coerceIn(0f, 1f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    if (vm.queueFull > 0f) {
                        vm.queueFull = (vm.queueFull - available.y / travel()).coerceIn(0f, 1f)
                        return Offset(0f, available.y)
                    }
                    return sheetScroll.onPostScroll(consumed, available, source)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (vm.sheet < 1f) return sheetScroll.onPreFling(available)
                val q = vm.queueFull
                if (q > 0f && q < 1f) {
                    val target = if (available.y < -500f) 1f else if (available.y > 500f) 0f else if (q > 0.5f) 1f else 0f
                    settleQueue(target, -available.y / travel())
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val headerDrag = rememberDraggableState { delta -> vm.queueFull = (vm.queueFull - delta / travel()).coerceIn(0f, 1f) }
    // Lowered again, the queue goes back to what plays next.
    val lowered by remember { derivedStateOf { vm.queueFull == 0f } }
    Box(
        pageAlpha
            .fillMaxSize()
            .padding(top = with(density) { topPx.toDp() }, start = 12.dp, end = 12.dp)
            .graphicsLayer { translationY = (1f - vm.queueFull) * travel() },
    ) {
        QueueCard(
            vm.connection,
            Modifier.fillMaxSize(),
            headerModifier = Modifier
                .draggable(
                    headerDrag,
                    Orientation.Vertical,
                    onDragStopped = { v ->
                        val q = vm.queueFull
                        val target = if (v < -500f) 1f else if (v > 500f) 0f else if (q > 0.5f) 1f else 0f
                        settleQueue(target, -v / travel())
                    },
                )
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                    settleQueue(if (vm.queueFull > 0.5f) 0f else 1f)
                },
            listModifier = Modifier.nestedScroll(queueScroll),
            extraBottom = with(density) { travel().toDp() },
            resetKey = lowered,
        )
    }
}

/** The current line, floating over the mini player. Absent when the track has no timed lyrics. */
@Composable
private fun LyricPopup(
    leadMs: Int,
    lyrics: Lyrics?,
    position: Position,
    fill: androidx.compose.runtime.State<Color>,
    on: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val timed = lyrics?.timed.orEmpty()
    val line by remember(timed, leadMs) { derivedStateOf { timed.getOrNull(activeLine(timed, position.value.longValue + leadMs))?.text } }
    val shown = line ?: return
    // Anchored at the bottom: a long line grows upward, away from the mini player.
    Box(modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.BottomStart) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .drawBehind { drawRect(fill.value) }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            androidx.compose.animation.Crossfade(shown, animationSpec = tween(180), label = "line") { text ->
                Txt(text, Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), on, maxLines = 4)
            }
        }
    }
}

// ---- Inner screen: player always open in its own column

@Composable
fun PlayerPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val conn = vm.connection
    val p = LocalPalette.current
    val track = conn.current
    val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cover = coverColor(track, vm.prefs.coverColor, p.surfaceHigh)
    val onCover = Blocks.on(cover.target)
    val position = rememberPosition(conn)
    Box(modifier.fillMaxHeight().drawBehind { drawRect(cover.fill.value) }) {
        Column(Modifier.fillMaxSize().padding(top = systemTop)) {
            if (track == null) return@Column
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(maxWidth)
                        .pointerInput(Unit) { detectTapGestures { vm.lyricsOpen = true } }
                        .swipeForTrack(conn),
                ) {
                    ArtPager(conn, Modifier.fillMaxSize(), { true }, 0.dp)
                    CoverLyricLine(vm, position, Modifier.align(Alignment.BottomCenter))
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                SeekBar(position, conn, onCover)
                Controls(conn, onCover, cover.target, big = 56.dp)
            }
            QueueCard(conn, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp))
        }
        if (vm.lyricsOpen && track != null) FullLyrics(vm, position, Modifier.fillMaxSize())
    }
}
