package dev.kreal.muzon.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.composed
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
import kotlin.math.max
import kotlin.math.min

// Quick and without a long creeping end: the slower spring kept the whole player sliding for a
// few hundred milliseconds after it seemed to have arrived, which read as a stutter.
private val sheetSpring = spring<Float>(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = 0.001f)

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
    // A new queue (a shuffle of the same list, say) moves to the playing track as well: its pages may
    // carry the same positions as before, and the pager would otherwise stay on the old track's art.
    LaunchedEffect(conn.playOrderPosition, order, conn.current?.id) {
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
        // By position in the queue: the same track queued twice must not give two pages one key.
        // ...and by the order itself, so a new order never keeps an old page by its key.
        key = { page -> (order.getOrNull(page) ?: (-1 - page)).toLong() * 31 + System.identityHashCode(order) },
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
                            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f).compositeOver(on), 0.5f to on, 1f to on, startY = top, endY = top + h),
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

/**
 * Shuffle and repeat: switched on, the icon is full strength on a round backing of its own colour;
 * switched off, it is faint and bare, so the two can never be mistaken for each other.
 */
@Composable
private fun ModeTap(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, on: Color, label: String, onClick: () -> Unit) {
    val k by animateFloatAsState(if (active) 1f else 0f, androidx.compose.animation.core.tween(160), label = "mode")
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).graphicsLayer { alpha = k }.clip(androidx.compose.foundation.shape.CircleShape).background(on.copy(alpha = 0.2f)))
        IconTap(icon, on.copy(alpha = 0.38f + 0.62f * k), 24.dp, label = label, onClick = onClick)
    }
}

@Composable
private fun Controls(conn: PlayerConnection, on: Color, cover: Color, big: Dp = 70.dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ModeTap(Icons.Rounded.Shuffle, conn.shuffle, on, stringResource(R.string.shuffle)) { conn.toggleShuffle() }
        IconTap(Icons.Rounded.SkipPrevious, on, 38.dp, 52.dp, label = stringResource(R.string.previous)) { conn.previous() }
        PlayButton(conn, big, on, cover)
        IconTap(Icons.Rounded.SkipNext, on, 38.dp, 52.dp, label = stringResource(R.string.next)) { conn.next() }
        ModeTap(
            if (conn.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            conn.repeatMode != Player.REPEAT_MODE_OFF, on, stringResource(R.string.repeat),
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

/** Lyrics text. Only over artwork does it get a shadow, and a light one: the shade does the rest. */
private fun lyricStyle(size: androidx.compose.ui.unit.TextUnit, overArt: Boolean = false) = Type.bodyBold.copy(
    fontWeight = androidx.compose.ui.text.font.FontWeight(700),
    fontSize = size,
    lineHeight = size * 1.22f,
    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    shadow = if (overArt) androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1f), 4f) else null,
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
    if (timed.isEmpty() || !vm.prefs.coverLyrics) return
    val lead = vm.lyricLeadMs
    val index by remember(timed, lead) { derivedStateOf { activeLine(timed, position.value.longValue + lead) } }
    val style = lyricStyle(lyricSize(vm.prefs.lyricsSize, big = true), overArt = true)
    Box(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                val shade = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))
                onDrawBehind { drawRect(shade) }
            }
            .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = index,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically(tween(280)) { it / 3 } + androidx.compose.animation.fadeIn(tween(220, delayMillis = 80)))
                    .togetherWith(androidx.compose.animation.slideOutVertically(tween(220)) { -it / 3 } + androidx.compose.animation.fadeOut(tween(120)))
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
            current.isEmpty -> Unit
            else -> LyricsList(current, position, conn, Color.White, style, vm.lyricLeadMs)
        }
    }
}

@Composable
private fun LyricsList(lyrics: Lyrics, position: Position, conn: PlayerConnection, on: Color, style: androidx.compose.ui.text.TextStyle, leadMs: Int) {
    val state = rememberLazyListState()
    val timed = lyrics.timed
    val lead by androidx.compose.runtime.rememberUpdatedState(leadMs)
    // The fade starts a little early, so the line is lit by the time it is sung.
    // Kept as a state, not a value: only the two rows whose highlight changes recompose.
    val active = remember(timed) { derivedStateOf { activeLine(timed, position.value.longValue + lead + HIGHLIGHT_LEAD_MS) } }
    // One coroutine follows the lines in turn, so a new line waits for the glide to end instead of
    // cutting it short halfway.
    // A reader who scrolled the lyrics is left alone for a few seconds.
    var touchedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        state.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction) touchedAt = System.currentTimeMillis() }
    }
    LaunchedEffect(active) {
        var placed = false
        snapshotFlow { active.value }.collect { i ->
            state.followLine(i, first = !placed, readerAway = System.currentTimeMillis() - touchedAt < 4000)
            if (i >= 0) placed = true
        }
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 40.dp),
    ) {
        itemsIndexed(timed, key = { i, _ -> i }, contentType = { _, _ -> 0 }) { i, line ->
            val isActive by remember(i) { derivedStateOf { active.value == i } }
            LyricRow(line, isActive, on, style) {
                conn.seekTo(line.timeMs)
                position.value.longValue = line.timeMs
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
    // As in Apple Music: the sung line brightens and grows over half a second, the last one fades back.
    val shown by animateFloatAsState(
        if (active) 1f else 0f,
        tween(if (active) 300 else 600, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        label = "line",
    )
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
                // Plain text never overlaps itself, so the alpha can go straight on the glyphs
                // instead of drawing every dimmed line into its own offscreen buffer.
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            .clip(Radius8)
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
    vm: AppViewModel,
    conn: PlayerConnection,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    extraBottom: Dp = 0.dp,
    resetKey: Any? = null,
    showHeart: Boolean = false, // the heart sits on the artwork's toolbar; the menu has it too
    state: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = conn.playOrderPosition + 1),
) {
    val p = LocalPalette.current
    val order = conn.playOrder
    val queue = conn.queue
    val position = conn.playOrderPosition
    val current = conn.current
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
                // The artwork is already above; this row is the name, the heart and the menu.
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(current.title.trim(), Type.bodyBold, p.text, maxLines = 2)
                        Txt(listOfNotNull(current.artist, current.album.takeIf { it.isNotBlank() }).joinToString(" · "), Type.small, p.muted)
                    }
                    if (showHeart) FavoriteHeart(vm, current.id, p.muted, 22.dp, 40.dp)
                    TrackMenu(vm, current, p.muted, queueWindow = conn.windowIndex)
                }
            }
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(stringResource(R.string.up_next) + " · " + formatLong(leftMs), Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), p.muted, Modifier.weight(1f))
                Tappable({
                    vm.sheet = 0f
                    vm.queueFull = 0f
                    conn.clearQueue()
                }, Modifier.clip(RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Txt(stringResource(R.string.queue_clear), Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)), p.accent)
                }
            }
        }
        LazyColumn(
            state = state,
            modifier = listModifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = systemBottom + 6.dp + extraBottom),
        ) {
            itemsIndexed(order, key = { _, window -> window }, contentType = { _, _ -> 0 }) { index, window ->
                val track = queue.getOrNull(window) ?: return@itemsIndexed
                QueueRow(track, if (vm.prefs.queueNumbers) index - position else 0, dimmed = index <= position, menu = { TrackMenu(vm, track, LocalPalette.current.muted, queueWindow = window) }) { conn.seekToWindow(window) }
            }
        }
    }
}

/**
 * Keeps a scroll of the queue from running past the current track: a drag or a fling coming from
 * either side halts with it at the top, and only a new gesture goes on beyond it, so the line between
 * what played and what plays next is never passed by accident.
 */
private class StopAtNext(private val state: androidx.compose.foundation.lazy.LazyListState, private val stopIndex: () -> Int) {
    private var halted = false

    /** Part of [available] (positive: towards what already played) the list must not take. */
    fun excess(available: Float): Float {
        if (halted) return available
        val stop = stopIndex()
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == stop }
        val top = state.layoutInfo.viewportStartOffset
        val distance = item?.let { (it.offset - top).toFloat() } // > 0: the line is below the top
        return when {
            // In what plays next, going back: stop when the line reaches the top.
            available > 0 && (state.firstVisibleItemIndex > stop || (distance != null && distance < 0f)) -> {
                if (distance == null) 0f else {
                    val room = -distance
                    if (available > room) { halted = true; available - room } else 0f
                }
            }
            // In what already played, going forward: stop when the line reaches the top.
            available < 0 && distance != null && distance > 0f -> {
                val room = -distance
                if (available < room) { halted = true; available - room } else 0f
            }
            else -> 0f
        }
    }

    fun gestureEnded() {
        halted = false
    }
}

/** A queue row; [offset] is how far it is from the current track, as the classic player numbers it. */
@Composable
private fun QueueRow(track: Track, offset: Int, dimmed: Boolean, menu: @Composable () -> Unit, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            // Same look without an offscreen buffer for each dimmed row.
            .graphicsLayer {
                alpha = if (dimmed) 0.5f else 1f
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            // Distance from the current track, both ways: the 20th back reads 20, like the 20th ahead.
            if (offset != 0) Txt(abs(offset).toString(), Type.tiny, p.muted)
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
        menu()
    }
}

// ---- Compact: mini player that grows into the full one under the finger

/** How much earlier than the line a lyric highlight starts, to cover its own fade. */
const val HIGHLIGHT_LEAD_MS = 150

/** What wireless headphones add before a sound is heard, on top of what the phone reports. */
const val WIRELESS_DELAY_MS = 250

/** Alpha of a part kept drawn while out of sight, so its first drawing is not a stutter. */
private const val WARM_ALPHA = 0.004f

private val MINI_BUTTONS = 118.dp

/** Seek bar and buttons under the artwork of the two-column player. */
private val WIDE_CONTROLS = 112.dp

/**
 * Previous, play and next of the full player, which shrink and fly to their places in the mini player
 * as the sheet comes down, beside the title, the way the artwork flies to its corner. They stay the
 * same buttons all the way, so the mini player's buttons are these. Shuffle and repeat fade out.
 */
@Composable
private fun MorphingControls(
    vm: AppViewModel,
    conn: PlayerConnection,
    on: Color,
    cover: Color,
    g: Geometry,
    sheet: () -> androidx.compose.ui.layout.LayoutCoordinates?,
    fade: Modifier,
) {
    val density = LocalDensity.current
    // Centres of the mini player's buttons, from its right edge.
    val miniY = g.miniHeight / 2
    val nextX = g.width - with(density) { (12.dp + 20.dp).toPx() }
    val playX = nextX - with(density) { (20.dp + 19.dp).toPx() }
    val prevX = playX - with(density) { (19.dp + 20.dp).toPx() }
    fun Modifier.morph(targetX: Float, scale: Float) = this
        .onPlaced { }
        .then(
            Modifier.graphicsLayerWithPlace(sheet) { center ->
                val k = 1f - vm.sheet
                translationX = k * (targetX - center.x)
                translationY = k * (miniY - center.y)
                val s = scale + (1f - scale) * vm.sheet
                scaleX = s
                scaleY = s
            },
        )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box(fade) { ModeTap(Icons.Rounded.Shuffle, conn.shuffle, on, stringResource(R.string.shuffle)) { conn.toggleShuffle() } }
        val step = vm.prefs.seekStepSeconds * 1000L
        Box(Modifier.morph(prevX, 26f / 38f)) { HoldButton(Icons.Rounded.SkipPrevious, on, stringResource(R.string.previous), { conn.previous() }) { conn.seekBy(-step) } }
        Box(Modifier.morph(playX, 38f / 64f)) { PlayButton(conn, 64.dp, on, cover) }
        Box(Modifier.morph(nextX, 26f / 38f)) { HoldButton(Icons.Rounded.SkipNext, on, stringResource(R.string.next), { conn.next() }) { conn.seekBy(step) } }
        Box(fade) {
            ModeTap(
                if (conn.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                conn.repeatMode != Player.REPEAT_MODE_OFF, on, stringResource(R.string.repeat),
            ) { conn.cycleRepeat() }
        }
    }
}

/**
 * Moves the full player's seek bar onto the seek bar slot of the wide mini player as the sheet
 * closes: the slot right of the title, as wide as the mini row gives it (see the mini row's weights).
 */
private fun Modifier.morphSeek(vm: AppViewModel, g: Geometry, density: Density, sheet: () -> androidx.compose.ui.layout.LayoutCoordinates?): Modifier = composed {
    val box = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    this
        .onPlaced { c ->
            val s = sheet() ?: return@onPlaced
            if (s.isAttached && c.isAttached) {
                val at = s.localPositionOf(c, Offset.Zero)
                box.value = androidx.compose.ui.geometry.Rect(at, Size(c.size.width.toFloat(), c.size.height.toFloat()))
            }
        }
        .graphicsLayer {
            val b = box.value
            if (b.width <= 0f) return@graphicsLayer
            with(density) {
                val start = g.miniArtX + g.miniArtSize + 10.dp.toPx()
                val room = g.width - (MINI_BUTTONS + 12.dp).toPx() - start
                val slotStart = start + room / 2.2f + 20.dp.toPx()
                val slotWidth = room * 1.2f / 2.2f - 20.dp.toPx()
                val k = 1f - vm.sheet
                val s = 1f + (slotWidth / b.width - 1f) * k
                translationX = k * (slotStart + slotWidth / 2 - b.center.x)
                translationY = k * (g.miniHeight / 2 - b.center.y)
                scaleX = s
                scaleY = s
            }
        }
}

/** A tap skips; holding jumps within the track, repeating while held. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HoldButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, onTap: () -> Unit, onHold: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onTap, onLongClick = onHold, onClickLabel = label),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(38.dp)) }
}

/**
 * A layer whose block also gets the element's centre in the sheet's coordinates, measured where it
 * is laid out, before its own transform, so the transform never feeds back into the measurement.
 */
private fun Modifier.graphicsLayerWithPlace(
    sheet: () -> androidx.compose.ui.layout.LayoutCoordinates?,
    block: androidx.compose.ui.graphics.GraphicsLayerScope.(Offset) -> Unit,
): Modifier = composed {
    val center = remember { mutableStateOf(Offset.Zero) }
    this
        .onPlaced { c ->
            val s = sheet() ?: return@onPlaced
            if (s.isAttached && c.isAttached) {
                center.value = s.localPositionOf(c, Offset.Zero) + Offset(c.size.width / 2f, c.size.height / 2f)
            }
        }
        .graphicsLayer { block(center.value) }
}

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
    /** Two columns: artwork and controls on the left, the queue on the right. */
    val wide: Boolean,
    val leftWidth: Float,
)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun geometry(density: Density, width: Float, height: Float, tabs: Dp, top: Dp, centred: Float = 0f): Geometry = with(density) {
    val miniHeight = 58.dp.toPx()
    val miniTop = height - tabs.toPx() - miniHeight - 4.dp.toPx()
    val margin = 8.dp.toPx()
    val wide = width >= 600.dp.toPx()
    if (wide) {
        // The open player of a tablet: artwork with its controls on the left, the queue beside them.
        val left = width * 0.5f
        // With lyrics the artwork gives up room: at least half the column is theirs.
        val fullArt = min(left - 48.dp.toPx(), height - top.toPx() - 230.dp.toPx())
        val lyricsArt = min(fullArt, (height - top.toPx()) * 0.5f - 56.dp.toPx() - WIDE_CONTROLS.toPx() - 20.dp.toPx()).coerceAtLeast(120.dp.toPx())
        val artSize = lerp(lyricsArt, fullArt, centred)
        return@with Geometry(
            width, height, miniTop, miniHeight, margin,
            artX = (left - artSize) / 2,
            // With lyrics the artwork stays up and the lyrics fill the column under the controls;
            // without them artwork and controls sit in the middle of the column.
            artY = lerp(
                top.toPx() + 56.dp.toPx(),
                max(top.toPx() + 56.dp.toPx(), top.toPx() + (height - top.toPx() - artSize - WIDE_CONTROLS.toPx()) / 2),
                centred,
            ),
            artSize = artSize,
            miniArtX = margin + 8.dp.toPx(),
            miniArtY = 8.dp.toPx(),
            miniArtSize = 42.dp.toPx(),
            wide = true,
            leftWidth = left,
        )
    }
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
        wide = false,
        leftWidth = width,
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
    val hasLyricsNow = { lyrics?.isEmpty == false }
    // A track without lyrics never shows a lyrics screen, not even an empty one.
    LaunchedEffect(lyrics) { if (lyrics?.isEmpty == true) vm.lyricsOpen = false }

    // Known once the lyrics of a track are read, so a track change does not bounce the artwork.
    var lyricsBelow by remember { mutableStateOf(false) }
    LaunchedEffect(lyrics) { lyrics?.let { lyricsBelow = !it.isEmpty } }
    val centred by animateFloatAsState(if (lyricsBelow) 0f else 1f, sheetSpring, label = "centred")

    var settle by remember { mutableStateOf<Job?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val g = remember(constraints.maxWidth, constraints.maxHeight, tabsHeight, systemTop, centred) {
            geometry(density, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), tabsHeight, systemTop, centred)
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
        // Its own layer, so redrawing it each frame redraws nothing else.
        Box(Modifier.fillMaxSize().graphicsLayer().drawBehind { if (vm.sheet > 0f) drawRect(Color.Black.copy(alpha = 0.6f * vm.sheet)) })
        Box(
            Modifier
                .fillMaxSize()
                .preferredFrameRate(vm.topFrameRate)
                .graphicsLayer { translationY = (1f - vm.sheet) * g.miniTop }
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
            // The sheet's background is drawn in a layer of its own: it changes on every frame, and
            // drawn on the sheet itself it made the whole sheet record its drawing again each time.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .drawBehind {
                        val f = vm.sheet
                        val inset = lerp(g.miniMargin, 0f, f)
                        drawRoundRect(
                            cover.fill.value,
                            Offset(inset, 0f),
                            Size(size.width - inset * 2, lerp(g.miniHeight, size.height, f)),
                            CornerRadius(if (f < 0.9f) lerp(14.dp.toPx(), 28.dp.toPx(), f / 0.9f) else lerp(28.dp.toPx(), 0f, (f - 0.9f) / 0.1f)),
                        )
                    },
            )
            // The page stays whole while it slides and only fades in the last stretch, where the mini
            // player takes over: no moment of an empty coloured block.
            val pageAlpha = Modifier.graphicsLayer {
                // From the very first frame of a move the page is drawn, nearly invisible: drawing it for
                // the first time costs one long frame, which is hidden there instead of mid-way.
                alpha = ((vm.sheet - 0.1f) / 0.2f).coerceIn(if (vm.sheet > 0f) WARM_ALPHA else 0f, 1f)
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                translationY = if (vm.sheet > 0f) 0f else -100000f
            }
            // Controls under the artwork; both stay composed, so nothing is built while the finger moves.
            val controlsBottom = remember { mutableFloatStateOf(g.artSize + with(density) { 140.dp.toPx() }) }
            var sheetCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            Box(Modifier.fillMaxSize().onPlaced { sheetCoords = it })
            // Read through a derived state: reading the sheet here recomposed the whole player on every frame.
            val dialogShown by remember { derivedStateOf { vm.lyricsOpen && vm.sheet > 0.5f } }
            Column(
                Modifier
                    .width(with(density) { g.leftWidth.toDp() })
                    .then(if (g.wide) Modifier.fillMaxHeight() else Modifier)
                    .padding(top = with(density) { (g.artY + g.artSize).toDp() } + if (g.wide) 14.dp else 6.dp, start = if (g.wide) 32.dp else 14.dp, end = if (g.wide) 32.dp else 14.dp)
                    .onPlaced { controlsBottom.floatValue = g.artY + g.artSize + with(density) { 22.dp.toPx() } + it.size.height },
            ) {
                // On a wide screen the seek bar flies into the mini player with the buttons and stays
                // there as its seek bar; on a narrow one the mini player has none, so it fades.
                SeekBar(position, conn, onCover, if (g.wide) Modifier.morphSeek(vm, g, density) { sheetCoords } else pageAlpha)
                MorphingControls(vm, conn, onCover, cover.target, g, { sheetCoords }, pageAlpha)
                val shown = lyrics
                if (g.wide && shown != null && !shown.isEmpty) {
                    // The two-column player has room for all the lyrics under its buttons.
                    Box(
                        pageAlpha
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer { alpha = 1f - centred }
                            .drawWithCache {
                                // Lines fade into the background at both edges. Painting the background colour
                                // over them costs nothing; cutting them out needed a layer of the whole column
                                // on every frame, which made the glide stutter. The brushes are made once per
                                // size and colour, not on every frame of the glide.
                                val bg = cover.fill.value
                                val topFade = Brush.verticalGradient(0f to bg, 1f to bg.copy(alpha = 0f), endY = size.height * 0.12f)
                                val bottomFade = Brush.verticalGradient(0f to bg.copy(alpha = 0f), 1f to bg, startY = size.height * 0.88f, endY = size.height)
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(topFade)
                                    drawRect(bottomFade)
                                }
                            },
                    ) {
                        LyricsList(shown, position, conn, onCover, lyricStyle(lyricSize(vm.prefs.lyricsSize, big = false)), vm.lyricLeadMs)
                    }
                }
            }

            // Mini row.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { g.miniHeight.toDp() })
                    .graphicsLayer {
                        // Drawn faintly through the whole move, so closing has no first-draw frame at the end.
                        val visible = vm.sheet < 1f
                        alpha = (1f - vm.sheet / 0.12f).coerceIn(if (visible) WARM_ALPHA else 0f, 1f)
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                        translationY = if (visible) 0f else -100000f // out of reach of touches
                    }
                    // Room for the three buttons, which arrive here from the full player.
                    .padding(end = MINI_BUTTONS + 12.dp)
                    .padding(start = with(density) { (g.miniArtX + g.miniArtSize).toDp() } + 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { settleTo(1f) },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Txt(track.title.trim(), Type.bodyBold, onCover)
                    Txt(track.artist, Type.small, onCover.copy(alpha = 0.8f))
                }
                // A wide screen has room for the seek bar in the mini player too.
                // Room for the seek bar arriving from the full player.
                if (g.wide) Spacer(Modifier.weight(1.2f))
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
                        shape = RoundedCornerShape(lerp(6f, if (g.wide) 16f else 0f, f).dp * (1f / s.coerceAtLeast(0.05f)))
                        clip = true
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { if (vm.sheet < 0.5f) settleTo(1f) }
                    }
                    .then(if (vm.prefs.swipeArtwork) Modifier.swipeForTrack(conn) else Modifier),
            ) {
                ArtPager(conn, Modifier.fillMaxSize(), { vm.sheet > 0.98f && vm.prefs.swipeArtwork }, 0.dp)
                // The two-column player shows all the lyrics under its buttons instead.
                if (!(g.wide && lyricsBelow)) {
                    CoverLyricLine(vm, position, Modifier.align(Alignment.BottomCenter).graphicsLayer {
                        alpha = ((vm.sheet - 0.7f) / 0.3f).coerceIn(0f, 1f)
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    })
                }
            }

            // Toolbar over the artwork: the way back down.
            Box(
                pageAlpha
                    .fillMaxWidth()
                    .height(systemTop + 52.dp)
                    .drawWithCache {
                        val shade = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent)
                        onDrawBehind { drawRect(shade) }
                    }
                    .padding(top = systemTop, start = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconTap(Icons.Rounded.KeyboardArrowDown, Color.White, 30.dp, 48.dp, label = stringResource(R.string.back)) { settleTo(0f) }
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp + with(density) { (g.width - g.leftWidth).toDp() }), verticalAlignment = Alignment.CenterVertically) {
                    if (lyrics?.isEmpty == false) {
                        IconTap(Icons.Rounded.Lyrics, Color.White, 24.dp, 48.dp, label = stringResource(R.string.lyrics)) { vm.lyricsOpen = true }
                    }
                        FavoriteHeart(vm, track.id, Color.White, 26.dp, 48.dp)
                }
            }

            if (g.wide) {
                // Same stop at the current track as the card of the folded screen, from either side.
                val wideState = rememberLazyListState(initialFirstVisibleItemIndex = conn.playOrderPosition + 1)
                val wideStop = remember(wideState) { StopAtNext(wideState) { conn.playOrderPosition } }
                val wideScroll = remember(wideStop) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource) = Offset(0f, wideStop.excess(available.y))

                        override suspend fun onPreFling(available: Velocity): Velocity =
                            if (wideStop.excess(Math.signum(available.y)) != 0f) available else Velocity.Zero

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            wideStop.gestureEnded()
                            return Velocity.Zero
                        }
                    }
                }
                val reopened by remember { derivedStateOf { vm.sheet < 0.01f } }
                QueueCard(
                    vm,
                    conn,
                    pageAlpha
                        .fillMaxSize()
                        .padding(start = with(density) { g.leftWidth.toDp() }, top = systemTop + 12.dp, end = 16.dp)
                        .nestedScroll(wideScroll),
                    resetKey = reopened,
                    state = wideState,
                )
            } else {
                QueueSheet(vm, g, controlsBottom, sheetScroll, pageAlpha)
            }
            if (dialogShown) LyricsDialog(vm, track, { conn.positionMs }) { vm.lyricsOpen = false }

        }

        if (vm.prefs.lyricPopup) {
            LyricPopup(
                vm.lyricLeadMs,
                lyrics,
                position,
                cover.fill,
                onCover,
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { g.miniTop.toDp() } - 6.dp)
                    .graphicsLayer {
                        alpha = (1f - vm.sheet / 0.12f).coerceIn(if (vm.sheet < 1f) WARM_ALPHA else 0f, 1f)
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                        translationY = if (vm.sheet < 1f) 0f else -100000f
                    },
            ) { settleTo(1f) }
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.connection.playOrderPosition + 1)
    val stop = remember(listState) { StopAtNext(listState) { vm.connection.playOrderPosition } }
    val queueScroll = remember(sheetScroll, stop) {
        object : NestedScrollConnection {
            /** Whether the finger began with the list at its very top; only such a drag may lower the card. */
            var startedAtTop: Boolean? = null

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && startedAtTop == null) startedAtTop = !listState.canScrollBackward
                if (vm.sheet < 1f) return sheetScroll.onPreScroll(available, source)
                if (available.y < 0 && vm.queueFull < 1f && source == NestedScrollSource.UserInput) {
                    vm.queueFull = (vm.queueFull - available.y / travel()).coerceIn(0f, 1f)
                    return Offset(0f, available.y)
                }
                return Offset(0f, stop.excess(available.y))
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                stop.gestureEnded()
                return Velocity.Zero
            }

            // Scrolling back through what played never closes the player, and only lowers a raised card
            // when the drag began at the top of the list: running into the top mid-drag used to pull
            // the whole player down into the mini player.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput && vm.queueFull > 0f && startedAtTop == true) {
                    vm.queueFull = (vm.queueFull - available.y / travel()).coerceIn(0f, 1f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                startedAtTop = null
                if (vm.sheet < 1f) return sheetScroll.onPreFling(available)
                val q = vm.queueFull
                if (q == 0f || q == 1f) {
                    // The drag part of the gesture already stopped at the line: no fling past it.
                    if (stop.excess(Math.signum(available.y)) != 0f) return available
                }
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
    // Reopened from the mini player, the queue goes back to the current track; lowering the card
    // alone leaves it where the finger left it.
    val lowered by remember { derivedStateOf { vm.sheet < 0.01f } }
    Box(
        pageAlpha
            .fillMaxSize()
            .padding(top = with(density) { topPx.toDp() }, start = 12.dp, end = 12.dp)
            .graphicsLayer { translationY = (1f - vm.queueFull) * travel() },
    ) {
        QueueCard(
            vm,
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
            state = listState,
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
            // The bubble grows or shrinks smoothly to the new line while the old one slides up and out;
            // before, its size jumped from one line to the next.
            androidx.compose.animation.AnimatedContent(
                shown,
                transitionSpec = {
                    (androidx.compose.animation.slideInVertically(tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 2 } +
                        androidx.compose.animation.fadeIn(tween(200, delayMillis = 60)))
                        .togetherWith(
                            androidx.compose.animation.slideOutVertically(tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 2 } +
                                androidx.compose.animation.fadeOut(tween(140)),
                        )
                        .using(androidx.compose.animation.SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 1f, stiffness = 500f) })
                },
                contentAlignment = Alignment.BottomStart,
                label = "line",
            ) { text ->
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
    val lyrics by produceState<Lyrics?>(null, track?.id) { value = track?.let { LyricsReader.read(it) } }
    LaunchedEffect(lyrics) { if (lyrics?.isEmpty == true) vm.lyricsOpen = false }
    Box(modifier.fillMaxHeight().drawBehind { drawRect(cover.fill.value) }) {
        Column(Modifier.fillMaxSize().padding(top = systemTop)) {
            if (track == null) return@Column
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(maxWidth)
                        .swipeForTrack(conn),
                ) {
                    ArtPager(conn, Modifier.fillMaxSize(), { true }, 0.dp)
                    CoverLyricLine(vm, position, Modifier.align(Alignment.BottomCenter))
                    if (lyrics?.isEmpty == false) {
                        IconTap(
                            Icons.Rounded.Lyrics, Color.White, 22.dp, 42.dp,
                            Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                            label = stringResource(R.string.lyrics),
                        ) { vm.lyricsOpen = true }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                SeekBar(position, conn, onCover)
                Controls(conn, onCover, cover.target, big = 56.dp)
            }
            QueueCard(vm, conn, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp))
        }
        if (vm.lyricsOpen && track != null) LyricsDialog(vm, track, { conn.positionMs }) { vm.lyricsOpen = false }
    }
}
