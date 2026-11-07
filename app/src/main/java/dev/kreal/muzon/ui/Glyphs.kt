package dev.kreal.muzon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import dev.kreal.muzon.data.AppTheme

enum class GlyphKind { Playlists, Artists, Albums, Songs, Recent, Genres, Search, Settings, History, MostPlayed, Folders }

private fun GlyphKind.vector(): ImageVector = when (this) {
    GlyphKind.Playlists -> Icons.AutoMirrored.Rounded.PlaylistPlay
    GlyphKind.Artists -> Icons.Rounded.Mic
    GlyphKind.Albums -> Icons.Rounded.LibraryMusic
    GlyphKind.Songs -> Icons.Rounded.MusicNote
    GlyphKind.Recent -> Icons.Rounded.Schedule
    GlyphKind.Genres -> Icons.Rounded.Category
    GlyphKind.Search -> Icons.Rounded.Search
    GlyphKind.Settings -> Icons.Rounded.Settings
    GlyphKind.History -> Icons.Rounded.History
    GlyphKind.MostPlayed -> Icons.Rounded.Whatshot
    GlyphKind.Folders -> Icons.Rounded.Folder
}

/** The poster theme draws a plain symbol; the other two draw the same subject with tone and depth. */
@Composable
fun Glyph(kind: GlyphKind, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    if (LocalAppTheme.current == AppTheme.Poster || kind == GlyphKind.History || kind == GlyphKind.MostPlayed || kind == GlyphKind.Folders) {
        Icon(kind.vector(), null, tint = tint, modifier = modifier.size(size))
    } else {
        Canvas(modifier.size(size)) { drawDetailed(kind, tint) }
    }
}

private val darkMetal = Brush.verticalGradient(
    0f to Color(0xFF6E7884), 0.4f to Color(0xFF4A535E), 0.52f to Color(0xFF2B323A), 0.6f to Color(0xFF5B6570), 1f to Color(0xFF39414B),
)
private val brightMetal = Brush.verticalGradient(
    0f to Color.White, 0.4f to Color(0xFFD6DCE3), 0.52f to Color(0xFF8C96A3), 0.6f to Color(0xFFEDF1F5), 1f to Color(0xFFA9B2BD),
)
private val paper = Brush.verticalGradient(0f to Color.White, 1f to Color(0xFFD9E3EC))
private val ink = Color(0xFF1C2530)

/** The subject keeps its own colours; [tint] is what contrasts with the background it sits on. */
private fun DrawScope.drawDetailed(kind: GlyphKind, tint: Color) {
    val u = size.minDimension / 24f
    val onLight = tint.luminance() < 0.3f
    val metal = if (onLight) darkMetal else brightMetal
    val paper = if (onLight) Brush.verticalGradient(0f to Color(0xFFFFFFFF), 1f to Color(0xFFC4CFD9)) else paper
    when (kind) {
        GlyphKind.Playlists -> {
            drawRoundRect(paper, Offset(3 * u, 3 * u), Size(18 * u, 18 * u), CornerRadius(3 * u))
            listOf(7.5f, 12f, 16.5f).forEachIndexed { i, y ->
                drawCircle(listOf(Blocks.Orange, Blocks.Pink, Blocks.Violet)[i], 1.4f * u, Offset(7 * u, y * u))
                drawLine(ink, Offset(10 * u, y * u), Offset(18 * u, y * u), 1.6f * u, StrokeCap.Round)
            }
        }
        GlyphKind.Artists -> {
            drawRoundRect(metal, Offset(10.5f * u, 13 * u), Size(3 * u, 8 * u), CornerRadius(1 * u))
            drawRoundRect(metal, Offset(7 * u, 20 * u), Size(10 * u, 2 * u), CornerRadius(1 * u))
            drawRoundRect(metal, Offset(7 * u, 2 * u), Size(10 * u, 13 * u), CornerRadius(5 * u))
            for (x in 0..3) for (y in 0..4) {
                drawCircle(ink, 0.55f * u, Offset((8.5f + x * 2.3f) * u, (4.6f + y * 2.1f) * u))
            }
            drawOval(Color.White.copy(alpha = 0.75f), Offset(8.4f * u, 2.8f * u), Size(4.5f * u, 2.2f * u))
        }
        GlyphKind.Albums -> {
            drawRoundRect(Blocks.Pink, Offset(7 * u, 2.5f * u), Size(14.5f * u, 14.5f * u), CornerRadius(2 * u))
            drawRoundRect(Blocks.Violet, Offset(4.8f * u, 4.8f * u), Size(14.5f * u, 14.5f * u), CornerRadius(2 * u))
            drawRoundRect(paper, Offset(2.5f * u, 7 * u), Size(14.5f * u, 14.5f * u), CornerRadius(2 * u))
            drawNote(u, Offset(6.5f * u, 10 * u), 0.62f, ink)
        }
        GlyphKind.Songs -> {
            drawNote(u, Offset(3 * u, 2 * u), 1f, tint)
            drawLine(Color.White.copy(alpha = if (onLight) 0.5f else 0.8f), Offset(10.2f * u, 7.4f * u), Offset(20 * u, 4.7f * u), 0.9f * u, StrokeCap.Round)
        }
        GlyphKind.Recent -> {
            drawCircle(metal, 10.5f * u, center)
            drawCircle(paper, 8.2f * u, center)
            drawLine(ink, center, Offset(12 * u, 6.5f * u), 1.7f * u, StrokeCap.Round)
            drawLine(ink, center, Offset(16 * u, 14.5f * u), 1.7f * u, StrokeCap.Round)
            drawCircle(Blocks.Orange, 1.2f * u, center)
        }
        GlyphKind.Genres -> {
            drawRoundRect(paper, Offset(3 * u, 3 * u), Size(8 * u, 8 * u), CornerRadius(2 * u))
            drawCircle(Blocks.Pink, 4.2f * u, Offset(17 * u, 7 * u))
            val tri = Path().apply {
                moveTo(7 * u, 13 * u); lineTo(11.5f * u, 21 * u); lineTo(2.5f * u, 21 * u); close()
            }
            drawPath(tri, Brush.verticalGradient(listOf(Blocks.Yellow, Blocks.Orange)))
            drawRoundRect(Blocks.Violet, Offset(13 * u, 13 * u), Size(8 * u, 8 * u), CornerRadius(2 * u))
        }
        GlyphKind.Search -> {
            drawLine(metal, Offset(15 * u, 15 * u), Offset(21 * u, 21 * u), 3.2f * u, StrokeCap.Round)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9BD8FF), Color(0xFF3C9BE0)), Offset(8 * u, 7 * u), 11 * u), 7.6f * u, Offset(10 * u, 10 * u))
            drawCircle(metal, 7.6f * u, Offset(10 * u, 10 * u), style = Stroke(2.2f * u))
            drawOval(Color.White.copy(alpha = 0.85f), Offset(6.2f * u, 5.6f * u), Size(4.2f * u, 2.2f * u))
        }
        GlyphKind.History, GlyphKind.MostPlayed, GlyphKind.Folders -> Unit
        GlyphKind.Settings -> {
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val from = Offset(center.x + (6.6f * u * Math.cos(a)).toFloat(), center.y + (6.6f * u * Math.sin(a)).toFloat())
                val to = Offset(center.x + (10.4f * u * Math.cos(a)).toFloat(), center.y + (10.4f * u * Math.sin(a)).toFloat())
                drawLine(metal, from, to, 3.2f * u, StrokeCap.Round)
            }
            drawCircle(metal, 7.8f * u, center)
            drawCircle(ink, 3.2f * u, center)
            drawCircle(metal, 1.8f * u, center)
        }
    }
}

private fun DrawScope.drawNote(u: Float, at: Offset, scale: Float, solid: Color?) {
    val s = u * scale
    val brush = if (solid != null) androidx.compose.ui.graphics.SolidColor(solid) else paper
    fun o(x: Float, y: Float) = Offset(at.x + x * s, at.y + y * s)
    drawCircle(brush, 3.2f * s, o(4.5f, 16.5f))
    drawCircle(brush, 3.2f * s, o(15.5f, 14.5f))
    val bar = Path().apply {
        moveTo(o(6.6f, 16.5f).x, o(6.6f, 16.5f).y)
        lineTo(o(6.6f, 4.5f).x, o(6.6f, 4.5f).y)
        lineTo(o(17.8f, 1.5f).x, o(17.8f, 1.5f).y)
        lineTo(o(17.8f, 14.5f).x, o(17.8f, 14.5f).y)
        lineTo(o(16.2f, 14.5f).x, o(16.2f, 14.5f).y)
        lineTo(o(16.2f, 6.5f).x, o(16.2f, 6.5f).y)
        lineTo(o(8.2f, 8.5f).x, o(8.2f, 8.5f).y)
        lineTo(o(8.2f, 16.5f).x, o(8.2f, 16.5f).y)
        close()
    }
    drawPath(bar, brush)
}
