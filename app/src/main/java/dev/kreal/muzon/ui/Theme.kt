package dev.kreal.muzon.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.kreal.muzon.R
import dev.kreal.muzon.data.AppTheme
import dev.kreal.muzon.data.ColorMode
import dev.kreal.muzon.data.Place

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Display = FontFamily(variable(R.font.unbounded, 600), variable(R.font.unbounded, 800), variable(R.font.unbounded, 900))
val Body = FontFamily(variable(R.font.golos, 400), variable(R.font.golos, 500), variable(R.font.golos, 600), variable(R.font.golos, 700))

@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val isDark: Boolean,
)

private val DarkPalette = Palette(
    background = Color(0xFF0F0F12),
    surface = Color(0xFF1B1B21),
    surfaceHigh = Color(0xFF28282F),
    text = Color(0xFFF7F7F9),
    muted = Color(0xFF9B9BA6),
    line = Color(0x1FFFFFFF),
    accent = Color(0xFFFF5A1F),
    isDark = true,
)
private val BlackPalette = DarkPalette.copy(background = Color.Black, surface = Color(0xFF131316), surfaceHigh = Color(0xFF1F1F24))
private val LightPalette = Palette(
    background = Color(0xFFF6F3EE),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEAE6DF),
    text = Color(0xFF15120E),
    muted = Color(0xFF6F6A62),
    line = Color(0x1F000000),
    accent = Color(0xFFE94A10),
    isDark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val LocalAppTheme = staticCompositionLocalOf { AppTheme.Poster }

object Type {
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight(900), fontSize = 25.sp, lineHeight = 28.sp)
    val heroPlayer = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 22.sp, lineHeight = 26.sp)
    val tile = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 14.sp, lineHeight = 17.sp)
    val section = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 16.sp, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 15.sp, lineHeight = 19.sp)
    val bodyBold = TextStyle(fontFamily = Body, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 19.sp)
    val small = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 16.sp)
    val tiny = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 11.sp, lineHeight = 14.sp)
}

/** Colours of the poster blocks, each with the text colour that reads on it. */
object Blocks {
    val Orange = Color(0xFFFF5A1F)
    val Violet = Color(0xFF5B2EFF)
    val Lime = Color(0xFFC6F135)
    val Pink = Color(0xFFFF2E74)
    val Yellow = Color(0xFFFFC400)
    val Teal = Color(0xFF00BFA6)

    private val cycle = listOf(Orange, Violet, Lime, Pink, Teal, Yellow)
    fun forIndex(i: Int) = cycle[i % cycle.size]
    fun on(color: Color) = if (color.luminance() > 0.42f) Color(0xFF14130F) else Color.White

    fun forPlace(place: Place) = when (place) {
        Place.Playlists -> Orange
        Place.Artists -> Violet
        Place.Albums -> Lime
        Place.Songs -> Pink
        Place.Recent -> Yellow
        Place.Genres -> Teal
        Place.History -> Color(0xFF2F80FF)
        Place.MostPlayed -> Color(0xFFFF4D4D)
        Place.Folders -> Color(0xFF8B7CF6)
    }

    /** Accent colours offered in the settings; the first is the default. */
    val accents = listOf(Color(0xFFFF5A1F), Violet, Pink, Teal, Color(0xFF2F80FF), Color(0xFF9BC21A), Yellow)
}

@Composable
fun AppThemeProvider(theme: AppTheme, mode: ColorMode, accent: Int = 0, content: @Composable () -> Unit) {
    val base = when (mode) {
        ColorMode.Dark -> DarkPalette
        ColorMode.Black -> BlackPalette
        ColorMode.Light -> LightPalette
        ColorMode.System -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
    }
    val palette = if (accent == 0) base else base.copy(accent = Blocks.accents.getOrElse(accent) { base.accent })
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent, background = palette.background, surface = palette.surface,
            onSurface = palette.text, onBackground = palette.text, surfaceContainer = palette.surfaceHigh,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, background = palette.background, surface = palette.surface,
            onSurface = palette.text, onBackground = palette.text, surfaceContainer = palette.surface,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette, LocalAppTheme provides theme) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
