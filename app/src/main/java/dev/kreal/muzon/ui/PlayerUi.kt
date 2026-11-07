package dev.kreal.muzon.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
import androidx.compose.ui.Modifier
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

private val sheetSpring = spring<Float>(dampingRatio = 1f, stiffness = 340f)

/** Saturated flat colour of a track's cover, kept dark enough for its text to read. */
@Composable
private fun coverColor(track: Track?, enabled: Boolean, fallback: Color): androidx.compose.runtime.State<Color> {
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
    return animateColorAsState(target, tween(450), label = "cover")
}

private class Position(val conn: PlayerConnection) {
    val value = mutableLongStateOf(0L)
    var scrubbing = mutableStateOf<Long?>(null)
}

@Composable
private fun rememberPosition(conn: PlayerConnection): Position {
    val position = remember(conn) { Position(conn) }
    val id = conn.current?.id
    LaunchedEffect(conn, id, conn.wantsToPlay) {
        position.value.longValue = conn.positionMs
        while (conn.wantsToPlay) {
            delay(200)
            position.value.longValue = conn.positionMs
        }
        delay(250)
        position.value.longValue = conn.positionMs
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
    LaunchedEffect(pager, order) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val window = order.getOrNull(page)
            if (window != null && page != conn.playOrderPosition) conn.seekToWindow(window)
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
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        val ms = (offset.x / size.width * duration).toLong()
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
        TimeLabels(position, conn, on)
    }
}

@Composable
private fun TimeLabels(position: Position, conn: PlayerConnection, on: Color) {
    val seconds by remember { derivedStateOf { (position.scrubbing.value ?: position.value.longValue) / 1000 } }
    Row(Modifier.fillMaxWidth()) {
        Txt(formatTime(seconds * 1000), Type.tiny, on)
        Spacer(Modifier.weight(1f))
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

@Composable
private fun PanelToggles(vm: AppViewModel, on: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        IconTap(Icons.Rounded.Lyrics, if (vm.panel == PlayerPanel.Lyrics) on else on.copy(alpha = 0.6f), label = stringResource(R.string.lyrics)) {
            vm.panel = if (vm.panel == PlayerPanel.Lyrics) PlayerPanel.None else PlayerPanel.Lyrics
        }
        IconTap(Icons.AutoMirrored.Rounded.QueueMusic, if (vm.panel == PlayerPanel.Queue) on else on.copy(alpha = 0.6f), label = stringResource(R.string.queue)) {
            vm.panel = if (vm.panel == PlayerPanel.Queue) PlayerPanel.None else PlayerPanel.Queue
        }
    }
}

// ---- Lyrics and queue

@Composable
private fun LyricsPanel(vm: AppViewModel, position: Position, on: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
    val conn = vm.connection
    val track = conn.current
    val lyrics by produceState<Lyrics?>(null, track?.id) {
        value = null
        value = track?.let { LyricsReader.read(it) }
    }
    val view = LocalView.current
    val keep = vm.prefs.keepScreenForLyrics
    DisposableEffect(keep) {
        view.keepScreenOn = keep
        onDispose { view.keepScreenOn = false }
    }
    val size = when (vm.prefs.lyricsSize) {
        LyricsSize.Small -> if (compact) 13.sp else 16.sp
        LyricsSize.Medium -> if (compact) 15.sp else 19.sp
        LyricsSize.Large -> if (compact) 17.sp else 23.sp
    }
    val style = Type.tile.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(800), fontSize = size, lineHeight = size * 1.28f)
    val current = lyrics
    Box(modifier.fillMaxWidth()) {
        when {
            current == null -> Unit
            current.isEmpty -> Txt(stringResource(R.string.no_lyrics), Type.body, on.copy(alpha = 0.7f), Modifier.align(Alignment.Center))
            else -> LyricsList(current, position, conn, on, style)
        }
    }
}

@Composable
private fun LyricsList(lyrics: Lyrics, position: Position, conn: PlayerConnection, on: Color, style: androidx.compose.ui.text.TextStyle) {
    val state = rememberLazyListState()
    val timed = lyrics.timed
    val active by remember(timed) {
        derivedStateOf {
            val now = position.value.longValue
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
            found
        }
    }
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (active >= 0 && System.currentTimeMillis() - lastTouch > 3000) {
            state.animateScrollToItem((active - 2).coerceAtLeast(0))
        }
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
        contentPadding = PaddingValues(vertical = 10.dp),
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
                androidx.compose.material3.Text(line, style = style, color = if (timed.isEmpty()) on else on.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, on: Color, style: androidx.compose.ui.text.TextStyle, onTap: () -> Unit) {
    val alpha by animateFloatAsState(if (active) 1f else 0.42f, tween(220), label = "line")
    androidx.compose.material3.Text(
        line.text,
        style = style,
        color = on,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 7.dp),
    )
}

@Composable
private fun QueuePanel(conn: PlayerConnection, on: Color, modifier: Modifier = Modifier) {
    val order = conn.playOrder
    val queue = conn.queue
    val state = rememberLazyListState(initialFirstVisibleItemIndex = conn.playOrderPosition)
    LazyColumn(state = state, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(order, key = { i, w -> "$i:$w" }, contentType = { _, _ -> 0 }) { _, window ->
            val track = queue.getOrNull(window) ?: return@itemsIndexed
            val isCurrent = window == conn.windowIndex
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { conn.seekToWindow(window) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArt(track, Modifier.size(40.dp), Radius6)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Txt(track.title.trim(), if (isCurrent) Type.bodyBold else Type.body, on)
                    Txt(track.artist, Type.small, on.copy(alpha = 0.7f))
                }
                Txt(formatTime(track.durationMs), Type.tiny, on.copy(alpha = 0.7f))
            }
        }
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
    val headArtY: Float,
    val headArtSize: Float,
    val textY: Float,
)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
fun CompactPlayer(vm: AppViewModel, tabsHeight: Dp, modifier: Modifier = Modifier) {
    val conn = vm.connection
    val track = conn.current ?: return
    val p = LocalPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cover = coverColor(track, vm.prefs.coverColor, p.surfaceHigh)
    val onCover by remember { derivedStateOf { Blocks.on(cover.value) } }
    val position = rememberPosition(conn)
    val panelState = remember { mutableFloatStateOf(if (vm.panel == PlayerPanel.None) 0f else 1f) }
    LaunchedEffect(vm.panel) {
        animate(panelState.floatValue, if (vm.panel == PlayerPanel.None) 0f else 1f, animationSpec = sheetSpring) { v, _ -> panelState.floatValue = v }
    }

    var settle by remember { mutableStateOf<Job?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val g = remember(constraints.maxWidth, constraints.maxHeight, tabsHeight, systemTop, systemBottom) {
            geometry(density, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), tabsHeight, systemTop)
        }
        fun settleTo(target: Float, velocity: Float = 0f) {
            settle?.cancel()
            settle = scope.launch {
                animate(vm.sheet, target, velocity, sheetSpring) { v, _ -> vm.sheet = v.coerceIn(0f, 1f) }
            }
        }
        val dragState = rememberDraggableState { delta -> vm.sheet = (vm.sheet - delta / g.miniTop).coerceIn(0f, 1f) }
        val nested = remember(g) {
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

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - vm.sheet) * g.miniTop
                }
                .drawBehind {
                    val f = vm.sheet
                    val inset = lerp(g.miniMargin, 0f, f)
                    drawRoundRect(
                        cover.value,
                        Offset(inset, 0f),
                        Size(size.width - inset * 2, lerp(g.miniHeight, size.height, f)),
                        CornerRadius(lerp(14.dp.toPx(), 0f, f)),
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
            // Full page. Always composed, so opening it never has to build anything.
            FullPageBody(vm, g, position, cover, onCover, panelState, nested, Modifier.graphicsLayer { alpha = ((vm.sheet - 0.2f) / 0.6f).coerceIn(0f, 1f) })

            // Mini row.
            val miniActive by remember { derivedStateOf { vm.sheet < 0.2f } }
            if (miniActive) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { g.miniHeight.toDp() })
                        .graphicsLayer { alpha = (1f - vm.sheet / 0.2f).coerceIn(0f, 1f) }
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { settleTo(1f) }
                        .padding(start = with(density) { (g.miniArtX + g.miniArtSize).toDp() } + 10.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(track.title.trim(), Type.bodyBold, onCover)
                        Txt(track.artist, Type.small, onCover.copy(alpha = 0.8f))
                    }
                    PlayButton(conn, 38.dp, onCover, cover.value)
                    IconTap(Icons.Rounded.SkipNext, onCover, 28.dp, 44.dp) { conn.next() }
                }
            }

            // One artwork travels from the mini player to the full page and to the lyrics header.
            Box(
                Modifier
                    .size(with(density) { g.artSize.toDp() })
                    .graphicsLayer {
                        val f = vm.sheet
                        val q = panelState.floatValue
                        val fullSize = lerp(g.artSize, g.headArtSize, q)
                        val fullX = lerp(g.artX, g.artX, q)
                        val fullY = lerp(g.artY, g.headArtY, q)
                        val s = lerp(g.miniArtSize, fullSize, f) / g.artSize
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = lerp(g.miniArtX, fullX, f)
                        translationY = lerp(g.miniArtY, fullY, f)
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { if (vm.sheet < 0.5f) settleTo(1f) else if (panelState.floatValue > 0.5f) vm.panel = PlayerPanel.None }
                    },
            ) {
                ArtPager(conn, Modifier.fillMaxSize(), { vm.sheet > 0.98f && panelState.floatValue < 0.02f })
            }
        }
    }
}

private fun geometry(density: Density, width: Float, height: Float, tabs: Dp, top: Dp): Geometry = with(density) {
    val miniHeight = 58.dp.toPx()
    val miniTop = height - tabs.toPx() - miniHeight - 4.dp.toPx()
    val margin = 8.dp.toPx()
    val pad = 22.dp.toPx()
    val grabber = top.toPx() + 26.dp.toPx()
    val artSize = min(width - pad * 2, height * 0.45f)
    Geometry(
        width, height, miniTop, miniHeight, margin,
        artX = (width - artSize) / 2,
        artY = grabber,
        artSize = artSize,
        miniArtX = margin + 8.dp.toPx(),
        miniArtY = 8.dp.toPx(),
        miniArtSize = 42.dp.toPx(),
        headArtY = grabber,
        headArtSize = 56.dp.toPx(),
        textY = grabber + artSize + 16.dp.toPx(),
    )
}

@Composable
private fun FullPageBody(
    vm: AppViewModel,
    g: Geometry,
    position: Position,
    cover: androidx.compose.runtime.State<Color>,
    onCover: Color,
    panelState: androidx.compose.runtime.MutableFloatState,
    nested: NestedScrollConnection,
    modifier: Modifier,
) {
    val conn = vm.connection
    val track = conn.current
    val density = LocalDensity.current
    val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize()) {
        // Grabber.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = systemTop + 9.dp)
                .size(38.dp, 5.dp)
                .clip(CircleShape)
                .background(onCover.copy(alpha = 0.45f)),
        )
        // Title moves beside the small artwork when a panel opens.
        val artEnd = with(density) { (g.artX + g.headArtSize).toDp() } + 12.dp
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val q = panelState.floatValue
                    translationY = lerp(g.textY, g.headArtY + 6.dp.toPx(), q)
                    translationX = lerp(g.artX, g.artX + g.headArtSize + 12.dp.toPx(), q)
                }
                .padding(end = 28.dp + artEnd * 0f),
        ) {
            val small by remember { derivedStateOf { panelState.floatValue > 0.5f } }
            Txt(track?.title?.trim().orEmpty(), if (small) Type.tile else Type.heroPlayer, onCover, maxLines = if (small) 1 else 2, modifier = Modifier.padding(end = if (small) artEnd else 24.dp))
            Spacer(Modifier.height(3.dp))
            Txt(listOfNotNull(track?.artist, track?.album?.takeIf { it.isNotBlank() }).joinToString(" · "), Type.body, onCover.copy(alpha = 0.85f), modifier = Modifier.padding(end = if (small) artEnd else 24.dp))
        }
        val panel = vm.panel
        if (panel != PlayerPanel.None) {
            val headBottom = with(density) { (g.headArtY + g.headArtSize).toDp() } + 8.dp
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = headBottom, bottom = 196.dp + systemBottom, start = 22.dp, end = 22.dp)
                    .nestedScroll(nested)
                    .graphicsLayer { alpha = panelState.floatValue },
            ) {
                if (panel == PlayerPanel.Lyrics) LyricsPanel(vm, position, onCover, Modifier.fillMaxSize()) else QueuePanel(conn, onCover, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(start = 22.dp, end = 22.dp, bottom = systemBottom + 4.dp)) {
            SeekBar(position, conn, onCover)
            Spacer(Modifier.height(4.dp))
            CoverReadingControls(conn, onCover, cover)
            PanelToggles(vm, onCover)
        }
    }
}

@Composable
private fun CoverReadingControls(conn: PlayerConnection, on: Color, cover: androidx.compose.runtime.State<Color>) {
    Controls(conn, on, cover.value)
}

// ---- Inner screen: player always open in its own column

@Composable
fun PlayerPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val conn = vm.connection
    val p = LocalPalette.current
    val track = conn.current
    val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cover = coverColor(track, vm.prefs.coverColor, p.surfaceHigh)
    val onCover by remember { derivedStateOf { Blocks.on(cover.value) } }
    val position = rememberPosition(conn)
    Column(
        modifier
            .fillMaxHeight()
            .drawBehind { drawRect(cover.value) }
            .padding(start = 16.dp, end = 16.dp, top = systemTop + 8.dp, bottom = systemBottom + 6.dp),
    ) {
        if (track == null) {
            Spacer(Modifier.weight(1f))
            return@Column
        }
        Box(Modifier.fillMaxWidth().weight(0.46f, fill = true), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val side = min(maxWidth.value, maxHeight.value).dp
                ArtPager(conn, Modifier.size(side), { true }, 14.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt(track.title.trim(), Type.section.copy(fontSize = 17.sp), onCover, maxLines = 2)
        Txt(track.artist, Type.body, onCover.copy(alpha = 0.85f))
        Spacer(Modifier.height(4.dp))
        SeekBar(position, conn, onCover)
        Controls(conn, onCover, cover.value, big = 60.dp)
        PanelToggles(vm, onCover)
        val panel = if (vm.panel == PlayerPanel.None) PlayerPanel.Lyrics else vm.panel
        Box(Modifier.fillMaxWidth().weight(0.54f)) {
            if (panel == PlayerPanel.Queue) QueuePanel(conn, onCover, Modifier.fillMaxSize()) else LyricsPanel(vm, position, onCover, Modifier.fillMaxSize(), compact = true)
        }
    }
}
