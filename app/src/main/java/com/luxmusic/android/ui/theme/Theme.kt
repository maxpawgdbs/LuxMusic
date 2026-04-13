package com.luxmusic.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LuxDarkColors = darkColorScheme(
    primary = Color(0xFF3A7AFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF17398A),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF18A57B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0A4D39),
    onSecondaryContainer = Color(0xFFC8F9E8),
    tertiary = Color(0xFFF39A3D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF6B3D00),
    onTertiaryContainer = Color(0xFFFFDEBC),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF4F7FB),
    surface = Color(0xFF161B24),
    onSurface = Color(0xFFF4F7FB),
    surfaceVariant = Color(0xFF212836),
    onSurfaceVariant = Color(0xFFC1CAD9),
    outline = Color(0xFF8791A5),
)

private val LuxLightColors = lightColorScheme(
    primary = Color(0xFF215EEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF001A53),
    secondary = Color(0xFF0D8C67),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBDF3E1),
    onSecondaryContainer = Color(0xFF002117),
    tertiary = Color(0xFFA85B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEBC),
    onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF141922),
    surface = Color.White,
    onSurface = Color(0xFF141922),
    surfaceVariant = Color(0xFFE7ECF4),
    onSurfaceVariant = Color(0xFF465468),
    outline = Color(0xFF758198),
)

@Composable
fun LuxMusicTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) LuxDarkColors else LuxLightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LuxTypography,
        content = content,
    )
}
