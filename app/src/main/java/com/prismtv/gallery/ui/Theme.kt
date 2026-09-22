package com.prismtv.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class Palette(val name: String, val c1: Color, val c2: Color, val c3: Color) {
    val diagonal: Brush get() = Brush.linearGradient(listOf(c1, c2, c3))
    val horizontal: Brush get() = Brush.horizontalGradient(listOf(c1, c2, c3))
    val vertical: Brush get() = Brush.verticalGradient(listOf(c1, c2, c3))
}

@Immutable
data class DarkThemeStyle(
    val name: String,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val border: Color,
    val textHi: Color,
    val textLo: Color,
    val scrim: Color,
)

val DarkThemeStyles = listOf(
    DarkThemeStyle(
        name = "OLED Pitch Black",
        bg = Color(0xFF000000),
        surface = Color(0xFF111215),
        surfaceHi = Color(0xFF1C1E24),
        border = Color(0xFF282A33),
        textHi = Color(0xFFF6F7FA),
        textLo = Color(0xFF9095A2),
        scrim = Color(0xEE000000),
    ),
    DarkThemeStyle(
        name = "Obsidian Charcoal",
        bg = Color(0xFF0C0D10),
        surface = Color(0xFF15171E),
        surfaceHi = Color(0xFF20232E),
        border = Color(0xFF2C303E),
        textHi = Color(0xFFF6F7FA),
        textLo = Color(0xFF969BB0),
        scrim = Color(0xEE0C0D10),
    ),
    DarkThemeStyle(
        name = "Midnight Sky",
        bg = Color(0xFF090C15),
        surface = Color(0xFF121624),
        surfaceHi = Color(0xFF1C2237),
        border = Color(0xFF28304D),
        textHi = Color(0xFFF6F8FF),
        textLo = Color(0xFF959EB8),
        scrim = Color(0xEE090C15),
    ),
    DarkThemeStyle(
        name = "Deep Slate",
        bg = Color(0xFF0F1418),
        surface = Color(0xFF172026),
        surfaceHi = Color(0xFF222E37),
        border = Color(0xFF2D3C48),
        textHi = Color(0xFFF4F7F9),
        textLo = Color(0xFF8FA1AF),
        scrim = Color(0xEE0F1418),
    ),
    DarkThemeStyle(
        name = "Carbon Neutral",
        bg = Color(0xFF141416),
        surface = Color(0xFF1E1E22),
        surfaceHi = Color(0xFF2B2B31),
        border = Color(0xFF383842),
        textHi = Color(0xFFF8F8FA),
        textLo = Color(0xFF9C9CA6),
        scrim = Color(0xEE141416),
    ),
)

val Palettes = listOf(
    Palette("Cyber Cyan", Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF00C6FF)),
    Palette("Titanium", Color(0xFFFFFFFF), Color(0xFFCBD5E1), Color(0xFF94A3B8)),
    Palette("Electric Emerald", Color(0xFF00F5A0), Color(0xFF00D9F5), Color(0xFF10B981)),
    Palette("Solar Gold", Color(0xFFFFB300), Color(0xFFF59E0B), Color(0xFFFFD54F)),
    Palette("Hyper Crimson", Color(0xFFFF3366), Color(0xFFFF5252), Color(0xFFE11D48)),
    Palette("Sunset Glow", Color(0xFFFF5F6D), Color(0xFFFF8E53), Color(0xFFFFC371)),
    Palette("Royal Azure", Color(0xFF3B82F6), Color(0xFF2563EB), Color(0xFF60A5FA)),
    Palette("Neon Violet", Color(0xFF8E5BFF), Color(0xFFA855F7), Color(0xFFC084FC)),
)

val LocalPalette = staticCompositionLocalOf { Palettes[0] }
val LocalThemeStyle = staticCompositionLocalOf { DarkThemeStyles[0] }

object Ui {
    val Bg: Color @Composable get() = LocalThemeStyle.current.bg
    val Surface: Color @Composable get() = LocalThemeStyle.current.surface
    val SurfaceHi: Color @Composable get() = LocalThemeStyle.current.surfaceHi
    val Border: Color @Composable get() = LocalThemeStyle.current.border
    val TextHi: Color @Composable get() = LocalThemeStyle.current.textHi
    val TextLo: Color @Composable get() = LocalThemeStyle.current.textLo
    val Scrim: Color @Composable get() = LocalThemeStyle.current.scrim
}

@Composable
fun PrismTheme(
    palette: Palette = Palettes[0],
    style: DarkThemeStyle = DarkThemeStyles[0],
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalThemeStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.c1,
                secondary = palette.c2,
                tertiary = palette.c3,
                background = style.bg,
                surface = style.surface,
                onBackground = style.textHi,
                onSurface = style.textHi,
            ),
            content = content,
        )
    }
}

/**
 * Premium dark background with refined subtle ambient vignette from the active palette,
 * completely eliminating heavy purple tints while preserving true blacks on OLED TVs.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val style = LocalThemeStyle.current
    Box(
        modifier
            .fillMaxSize()
            .background(style.bg)
            .drawBehind {
                val big = size.maxDimension
                // Subtle corner vignettes
                drawRect(
                    Brush.radialGradient(
                        listOf(p.c1.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = big * 0.70f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(p.c2.copy(alpha = 0.06f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = big * 0.75f,
                    )
                )
            }
    )
}
