package dev.kreal.muzon.ui

import kotlinx.coroutines.flow.first

import androidx.compose.runtime.mutableStateOf

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.requiredSize

import androidx.compose.runtime.derivedStateOf

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.foundation.gestures.awaitFirstDown

import androidx.compose.foundation.gestures.awaitEachGesture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kreal.muzon.data.AppTheme
import dev.kreal.muzon.data.Track
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

val Radius12 = RoundedCornerShape(12.dp)
val Radius8 = RoundedCornerShape(8.dp)
val Radius6 = RoundedCornerShape(6.dp)
val Radius16 = RoundedCornerShape(16.dp)

/**
 * Fills a block in the colour of the current theme: flat for the poster, with a soft tone for the
 * detailed one, and with a highlight across the top for the glossy one. Everything is painted from
 * cached brushes, so a tile costs the same to draw as a flat one.
 */
fun Modifier.block(color: Color, theme: AppTheme, corner: Dp): Modifier = when (theme) {
    AppTheme.Poster -> this.clip(RoundedCornerShape(corner)).background(color)
    AppTheme.Detailed -> this.clip(RoundedCornerShape(corner)).drawWithCache {
        val brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.16f).compositeOver(color),
            1f to Color.Black.copy(alpha = 0.10f).compositeOver(color),
        )
        val radius = CornerRadius(corner.toPx())
        onDrawBehind { drawRoundRect(brush, cornerRadius = radius) }
    }
    AppTheme.Aero -> this.clip(RoundedCornerShape(corner)).drawWithCache {
        // Glass: the colour itself under a highlight on the top half and a faint glow at the bottom.
        // Nothing is darkened at the top edge, so no dark rim shows above the highlight, whatever the
        // colour, a near-black play button included.
        val body = Brush.verticalGradient(
            0f to color,
            0.6f to color,
            1f to Color.White.copy(alpha = 0.22f).compositeOver(color),
        )
        // The highlight fades out to nothing at its lower edge: a hard edge there reads as a dark band
        // across the block, worst on a dark one.
        val glossHeight = size.height * 0.55f
        val gloss = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.42f),
            0.6f to Color.White.copy(alpha = 0.14f),
            1f to Color.White.copy(alpha = 0f),
            endY = glossHeight,
        )
        val radius = CornerRadius(corner.toPx().coerceAtMost(minOf(size.width, size.height) / 2))
        val inset = 0f
        onDrawBehind {
            drawRoundRect(body, cornerRadius = radius)
            // The highlight follows the block's own outline at the top and ends in a soft curve.
            drawRoundRect(
                gloss,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, glossHeight - inset),
                cornerRadius = (radius.x - inset).coerceAtLeast(0f).let { r -> CornerRadius(r, minOf(r, (glossHeight - inset) / 2)) },
            )
        }
    }
}

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color = LocalPalette.current.text,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Text(text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun Tappable(onClick: () -> Unit, modifier: Modifier = Modifier, bounded: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.clickable(interactionSource = source, indication = ripple(bounded = bounded), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun IconTap(icon: ImageVector, tint: Color, size: Dp = 24.dp, box: Dp = 44.dp, modifier: Modifier = Modifier, label: String? = null, onClick: () -> Unit) {
    Tappable(onClick, modifier.size(box), bounded = false) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    wantsToPlay: Boolean,
    number: Int? = null,
    menu: (@Composable (open: Boolean, setOpen: (Boolean) -> Unit) -> Unit)? = null,
    onClick: () -> Unit,
) {
    var menuOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    val p = LocalPalette.current
    val available = track.uri != null
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(enabled = available || menu != null, onClick = { if (available) onClick() }, onLongClick = { if (menu != null) menuOpen = true })
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Txt(number.toString(), Type.small, p.muted, Modifier.width(26.dp))
        } else {
            TrackArt(track, Modifier.size(42.dp), Radius6)
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(track.title.trim(), if (isCurrent) Type.bodyBold else Type.body, if (!available) p.muted else if (isCurrent) p.accent else p.text, maxLines = 3)
            Txt(track.artist.ifBlank { track.shownArtist }, Type.small, p.muted)
        }
        if (isCurrent) {
            Equalizer(wantsToPlay, p.accent)
            Spacer(Modifier.width(8.dp))
        }
        Txt(formatTime(track.durationMs), Type.tiny, p.muted)
        if (menu != null) menu(menuOpen) { menuOpen = it }
    }
}

/** Three still bars; they say "this one" without a looping animation in every list. */
@Composable
private fun Equalizer(playing: Boolean, color: Color) {
    Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(0.6f, 1f, 0.75f).forEach { h ->
            Box(Modifier.width(3.dp).fillMaxHeight(if (playing) h else 0.3f).clip(CircleShape).background(color))
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatLong(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60) else "%d:%02d".format(total / 60, total % 60)
}

fun formatHours(tracks: List<Track>): Int = (tracks.sumOf { it.durationMs } / 3_600_000L).toInt()

/** The letter rail of the iPod song list: slide a finger to jump. Letters come from the list itself. */
/**
 * The alphabet along the right edge of a list sorted by name. A touch jumps to its letter at once;
 * sliding the finger runs through the letters with a light tick at each, a large bubble beside the
 * finger shows the letter, and the letter of what is on screen stays lit.
 */
@Composable
fun LetterRail(letters: List<Pair<Char, Int>>, state: LazyListState, offset: Int, modifier: Modifier = Modifier) {
    if (letters.size < 2) return
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val density = LocalDensity.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val prefs = (androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.kreal.muzon.MuzOnApp).prefs
    var heightPx by remember { mutableIntStateOf(1) }
    var touched by remember { mutableIntStateOf(-1) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // The letter of the first row on screen, lit while nobody touches the rail.
    val visible by remember(letters) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex - offset
            letters.indexOfLast { it.second <= first }.coerceAtLeast(0)
        }
    }
    val lit = if (touched >= 0) touched else visible
    // Letters shown: all when they fit, otherwise every second or third, evenly. Worked out from the
    // rail's current height on each call: the touch handler outlives the first, unmeasured layout.
    fun stepFor(height: Int) = with(density) { (height / 10.dp.toPx()).toInt().coerceAtLeast(1) }.let { fit -> (letters.size + fit - 1) / fit }
    val step = stepFor(heightPx)
    val shown = (letters.size + step - 1) / step
    fun jump(y: Float) {
        touchY = y
        val step = stepFor(heightPx)
        val shown = (letters.size + step - 1) / step
        // The touch picks the letter drawn under the finger, not a neighbour of it.
        val i = (((y / heightPx) * shown).toInt().coerceIn(0, shown - 1) * step).coerceAtMost(letters.lastIndex)
        if (i != touched) {
            touched = i
            if (prefs.haptics) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            // A short glide to the letter; the next letter cancels it, so a fast slide never lags behind.
            scrollJob?.cancel()
            scrollJob = scope.launch {
                // The first letter means the very top, header included.
                val target = if (i == 0) 0 else letters[i].second + offset
                if (kotlin.math.abs(state.firstVisibleItemIndex - target) > 60) state.scrollToItem(target + if (target > state.firstVisibleItemIndex) -8 else 8)
                state.animateScrollToItem(target)
            }
        }
    }
    Box(modifier.width(24.dp).fillMaxHeight().padding(vertical = 6.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        jump(down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                jump(change.position.y)
                            }
                        } while (change.pressed)
                        touched = -1
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // While a finger is on the rail the letters near it grow and lean out towards the list, as
            // in the Android contacts scroller, easing back when it lifts.
            val press by androidx.compose.animation.core.animateFloatAsState(if (touched >= 0) 1f else 0f, androidx.compose.animation.core.tween(160), label = "press")
            letters.forEachIndexed { i, (c, _) ->
                val on = i == lit || (step > 1 && lit in i until i + step)
                if (i % step == 0) {
                    // Every letter gets a place; on a short screen they are just set closer and smaller.
                    val cell = with(density) { (heightPx.toFloat() / shown).toDp() }.coerceIn(10.dp, 18.dp)
                    Box(
                        Modifier
                            .size(cell)
                            .graphicsLayer {
                                val centre = (i / step + 0.5f) / shown * heightPx
                                val near = (1f - kotlin.math.abs(centre - touchY) / (heightPx * 0.18f)).coerceIn(0f, 1f) * press
                                val grow = 1f + 0.9f * near * near
                                scaleX = grow
                                scaleY = grow
                                translationX = -26.dp.toPx() * near * near
                            }
                            .clip(CircleShape)
                            .background(if (on) p.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        val size = (cell.value * 0.62f).coerceIn(8f, 11f)
                        Txt(
                            c.toString(),
                            Type.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(700), fontSize = androidx.compose.ui.unit.TextUnit(size, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(size, androidx.compose.ui.unit.TextUnitType.Sp)),
                            if (on) Blocks.on(p.accent) else p.muted,
                        )
                    }
                }
            }
        }
        if (touched >= 0) {
            val bubble = 64.dp
            Box(
                Modifier
                    // Next to the finger, but never past the ends of the rail.
                    .offset {
                        val y = (touchY - bubble.toPx() / 2).coerceIn(0f, (heightPx - bubble.toPx()).coerceAtLeast(0f))
                        androidx.compose.ui.unit.IntOffset(-(bubble + 14.dp).roundToPx(), y.toInt())
                    }
                    .requiredSize(bubble)
                    .clip(CircleShape)
                    .background(p.accent),
                contentAlignment = Alignment.Center,
            ) { Txt(letters[touched].first.toString(), Type.hero, Blocks.on(p.accent)) }
        }
    }
}

fun lettersOf(names: List<String>): List<Pair<Char, Int>> {
    val out = ArrayList<Pair<Char, Int>>()
    var last: Char? = null
    names.forEachIndexed { i, n ->
        // A title that starts with a sign or a digit ("$HRIMP", "'70s", "#1") sorts before the letters,
        // so it belongs to '#', not to the letter after the sign: that letter would appear out of order.
        val first = n.trim().firstOrNull()?.uppercaseChar()
        val c = if (first == null || !first.isLetter()) '#' else first
        if (c != last) {
            if (out.none { it.first == c }) out += c to i
            last = c
        }
    }
    return out
}

/**
 * Follows the sung line without fighting the reader, by two marks in the visible part of the list:
 *  - the line's top never goes above [TOP_MARK] of the height, so it never slides under the faded edge;
 *  - under it there are always at least two lines, and never less than [ROOM_BELOW] of the height.
 * While the line sits between the marks nothing moves. When it reaches the lower mark the list glides
 * until the line's top is back on the upper one. A line the reader scrolled away from is left alone.
 * [first] places the line on the upper mark once, when the list opens.
 */
suspend fun androidx.compose.foundation.lazy.LazyListState.followLine(index: Int, first: Boolean, readerAway: Boolean = false) {
    if (index < 0) return
    // The list must be laid out first, or the line lands at the top edge instead of on the mark.
    if (layoutInfo.viewportSize.height <= 0) androidx.compose.runtime.snapshotFlow { layoutInfo.viewportSize.height }.first { it > 0 }
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val viewTop = info.viewportStartOffset
    val viewEnd = info.viewportEndOffset
    val height = (viewEnd - viewTop).toFloat()
    if (first || visible.isEmpty()) {
        scrollToItem(index)
        // The line now starts where the content does; move it down to the upper mark.
        scrollBy(-(height * TOP_MARK - info.beforeContentPadding))
        return
    }
    if (isScrollInProgress || readerAway) return
    val line = visible.sumOf { it.size } / visible.size
    val item = visible.firstOrNull { it.index == index }
    val topMark0 = viewTop + height * TOP_MARK
    val glide = androidx.compose.animation.core.tween<Float>(650, easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))
    // The sung line is out of sight (after a seek, or the list was opened mid-song): bring it back to
    // the mark, gliding when it is near, jumping when it is far.
    val below = visible.firstOrNull { it.index == index - 1 }?.let { it.offset + it.size }
    if ((item == null && below == null) || (item != null && item.offset + item.size <= viewTop)) {
        val first = visible.first().index
        if (kotlin.math.abs(index - first) <= 8) {
            val estimate = (item?.offset ?: (visible.first().offset + (index - first) * line)).toFloat()
            animateScrollBy(estimate - topMark0, glide)
        } else {
            scrollToItem(index)
            scrollBy(-(height * TOP_MARK - info.beforeContentPadding))
        }
        return
    }
    val top = item?.offset ?: below ?: return
    val bottom = top + (item?.size ?: line)
    val roomBelow = maxOf(2f * line, height * ROOM_BELOW)
    val topMark = viewTop + height * TOP_MARK
    if (bottom > viewEnd - roomBelow) {
        animateScrollBy(top - topMark, glide)
    }
}

/** Highest point of the sung line, as a share of the lyrics' height. */
const val TOP_MARK = 0.2f

/** Least room under the sung line, as a share of the lyrics' height (and never under two lines). */
const val ROOM_BELOW = 0.25f
