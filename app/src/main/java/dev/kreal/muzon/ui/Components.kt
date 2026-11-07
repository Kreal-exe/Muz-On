package dev.kreal.muzon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
        val body = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.18f).compositeOver(color),
            0.55f to color,
            1f to Color.White.copy(alpha = 0.30f).compositeOver(color),
        )
        val gloss = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.62f),
            1f to Color.White.copy(alpha = 0.10f),
            endY = size.height * 0.5f,
        )
        val radius = CornerRadius(corner.toPx())
        val inset = 3.dp.toPx()
        onDrawBehind {
            drawRoundRect(body, cornerRadius = radius)
            drawRoundRect(
                gloss,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height * 0.48f),
                cornerRadius = CornerRadius(radius.x - inset),
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
fun TrackRow(track: Track, isCurrent: Boolean, wantsToPlay: Boolean, number: Int? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val available = track.uri != null
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = available, onClick = onClick)
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
@Composable
fun LetterRail(letters: List<Pair<Char, Int>>, state: LazyListState, offset: Int, modifier: Modifier = Modifier) {
    if (letters.size < 2) return
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(1) }
    Column(
        modifier
            .width(22.dp)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .onSizeChanged { heightPx = it.height }
            .pointerInput(letters) {
                detectVerticalDragGestures { change, _ ->
                    val i = ((change.position.y / heightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    scope.launch { state.scrollToItem(letters[i].second + offset) }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shown = with(density) { (heightPx / 14.dp.toPx()).toInt().coerceAtLeast(1) }
        val step = (letters.size / shown).coerceAtLeast(0) + 1
        letters.forEachIndexed { i, (c, _) ->
            if (i % step == 0) Txt(c.toString(), Type.tiny, p.muted)
        }
    }
}

fun lettersOf(names: List<String>): List<Pair<Char, Int>> {
    val out = ArrayList<Pair<Char, Int>>()
    var last: Char? = null
    names.forEachIndexed { i, n ->
        val first = n.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        val c = when {
            first == null -> '#'
            first.isDigit() -> '#'
            else -> first
        }
        if (c != last) {
            if (out.none { it.first == c }) out += c to i
            last = c
        }
    }
    return out
}
