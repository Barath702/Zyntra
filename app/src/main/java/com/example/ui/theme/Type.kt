package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFont = FontFamily(Font(R.font.space_grotesk, FontWeight.Medium))
val InterFont = FontFamily(Font(R.font.inter, FontWeight.Normal))
val JetBrainsMonoFont = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

val NoteCraftTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp
    )
)
