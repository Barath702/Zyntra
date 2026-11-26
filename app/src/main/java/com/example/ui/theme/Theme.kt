package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class StudioTokens(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val padIdle: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val highContrast: Boolean
)

val LocalStudioTokens = staticCompositionLocalOf {
    StudioTokens(
        bg = StudioBackground,
        surface = StudioSurface,
        surfaceElevated = StudioSurfaceElevated,
        padIdle = StudioPadIdle,
        border = StudioBorder,
        textPrimary = StudioTextPrimary,
        textSecondary = StudioTextSecondary,
        textMuted = StudioTextMuted,
        highContrast = false
    )
}

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun NoteCraftTheme(
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val tokens = if (highContrast) {
        StudioTokens(
            bg = HighContrastBackground,
            surface = HighContrastSurface,
            surfaceElevated = Color(0xFF1A1A20),
            padIdle = Color(0xFF222228),
            border = HighContrastBorder,
            textPrimary = Color.White,
            textSecondary = Color(0xFFD4D4DC),
            textMuted = Color(0xFF9898A4),
            highContrast = true
        )
    } else {
        StudioTokens(
            bg = StudioBackground,
            surface = StudioSurface,
            surfaceElevated = StudioSurfaceElevated,
            padIdle = StudioPadIdle,
            border = StudioBorder,
            textPrimary = StudioTextPrimary,
            textSecondary = StudioTextSecondary,
            textMuted = StudioTextMuted,
            highContrast = false
        )
    }

    val scheme = darkColorScheme(
        primary = TrackPalette[1],
        onPrimary = Color.Black,
        secondary = TrackPalette[3],
        onSecondary = Color.Black,
        tertiary = TrackPalette[0],
        background = tokens.bg,
        onBackground = tokens.textPrimary,
        surface = tokens.surface,
        onSurface = tokens.textPrimary,
        surfaceVariant = tokens.surfaceElevated,
        onSurfaceVariant = tokens.textSecondary,
        outline = tokens.border,
        error = StudioDanger
    )

    CompositionLocalProvider(LocalStudioTokens provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NoteCraftTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}
