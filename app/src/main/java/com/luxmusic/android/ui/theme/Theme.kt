package com.luxmusic.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LuxDarkColors = darkColorScheme(
    primary = ArcticBlue,
    onPrimary = MidnightBlue,
    secondary = ElectricMint,
    onSecondary = MidnightBlue,
    tertiary = SunsetOrange,
    background = MidnightBlue,
    onBackground = CloudWhite,
    surface = DeepNavy,
    onSurface = CloudWhite,
    surfaceVariant = DeepNavy.copy(alpha = 0.92f),
    onSurfaceVariant = Mist,
)

private val LuxLightColors = lightColorScheme(
    primary = Ink,
    onPrimary = CloudWhite,
    secondary = ArcticBlue,
    onSecondary = MidnightBlue,
    tertiary = SunsetOrange,
    background = SoftSand,
    onBackground = Ink,
    surface = CloudWhite,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Ink.copy(alpha = 0.75f),
)

@Composable
fun LuxMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) LuxDarkColors else LuxLightColors,
        typography = LuxTypography,
        content = content,
    )
}
