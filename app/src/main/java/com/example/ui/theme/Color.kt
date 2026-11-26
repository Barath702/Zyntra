package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Studio Charcoal & Near-Black Surfaces
val StudioBackground = Color(0xFF0E0E10)
val StudioSurface = Color(0xFF17171A)
val StudioSurfaceElevated = Color(0xFF222227)
val StudioPadIdle = Color(0xFF2B2B31)
val StudioBorder = Color(0xFF2C2C33)
val StudioTextPrimary = Color(0xFFF2F2F5)
val StudioTextSecondary = Color(0xFF9A9AA4)
val StudioTextMuted = Color(0xFF63636E)
val StudioDanger = Color(0xFFEF4444)
val StudioRecordRed = Color(0xFFE53935)

// High Contrast Surfaces
val HighContrastBackground = Color(0xFF000000)
val HighContrastSurface = Color(0xFF0C0C0E)
val HighContrastBorder = Color(0xFF6E6E78)

// Distinctive Track & Clip Accent Palette (inspired by studio hardware)
val TrackPalette: List<Color> = listOf(
    Color(0xFFD94F8A), // 0: Magenta / Drum Pink
    Color(0xFF22D3EE), // 1: Electric Cyan
    Color(0xFF9CD326), // 2: Studio Lime
    Color(0xFFF58220), // 3: Tangerine Orange
    Color(0xFF5C9EFF), // 4: Cobalt Sky
    Color(0xFF9F7AEA), // 5: Ultraviolet
    Color(0xFF2ED47A), // 6: Emerald Mint
    Color(0xFFF5C542)  // 7: Warm Amber
)

fun trackAccentColor(index: Int): Color = TrackPalette[index.mod(TrackPalette.size).let { if (it < 0) it + TrackPalette.size else it }]
