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

val Palettes = listOf(
    Palette("Aurora", Color(0xFF8E5BFF), Color(0xFF3DA9FC), Color(0xFF2EE6C5)),
    Palette("Sunset", Color(0xFFFF5F6D), Color(0xFFFF8E53), Color(0xFFFFC371)),
    Palette("Candy", Color(0xFFFF4ECD), Color(0xFF8B5CF6), Color(0xFF22D3EE)),
    Palette("Ocean", Color(0xFF00C6FF), Color(0xFF3B82F6), Color(0xFF6366F1)),
    Palette("Forest", Color(0xFF11D88B), Color(0xFF84E33A), Color(0xFFE3F04B)),
    Palette("Ruby", Color(0xFFFF3D71), Color(0xFFC81D77), Color(0xFF6C3FE0)),
)

object Ui {
    val Bg = Color(0xFF0A0B1E)
    val Surface = Color(0xFF15172E)
    val SurfaceHi = Color(0xFF232649)
    val TextHi = Color(0xFFF5F6FF)
    val TextLo = Color(0xFFA3A9D4)
    val Scrim = Color(0xCC05061A)
}

val LocalPalette = staticCompositionLocalOf { Palettes[0] }

@Composable
fun PrismTheme(palette: Palette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.c1,
                secondary = palette.c2,
                tertiary = palette.c3,
                background = Ui.Bg,
                surface = Ui.Surface,
                onBackground = Ui.TextHi,
                onSurface = Ui.TextHi,
            ),
            content = content,
        )
    }
}

/** Deep navy background with soft colour glows taken from the active palette. */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxSize()
            .background(Ui.Bg)
            .drawBehind {
                val big = size.maxDimension
                drawRect(
                    Brush.radialGradient(
                        listOf(p.c1.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = big * 0.75f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(p.c2.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = big * 0.8f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(p.c3.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = big * 0.5f,
                    )
                )
            }
    )
}
