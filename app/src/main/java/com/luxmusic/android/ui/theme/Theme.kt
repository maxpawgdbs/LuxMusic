package com.luxmusic.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LuxDarkColors = darkColorScheme(
    primary = Honey,
    onPrimary = Ink,
    secondary = Emerald,
    onSecondary = Ink,
    tertiary = Coral,
    onTertiary = WarmPaper,
    background = Night,
    onBackground = WarmPaper,
    surface = NightSurface,
    onSurface = WarmPaper,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Mist,
    outline = Slate,
)

private val LuxLightColors = lightColorScheme(
    primary = HoneyDark,
    onPrimary = WarmPaper,
    secondary = Emerald,
    onSecondary = WarmPaper,
    tertiary = Coral,
    onTertiary = WarmPaper,
    background = WarmPaper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = WarmSurface,
    onSurfaceVariant = Slate,
    outline = Mist,
)

@Composable
fun LuxMusicTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        LuxDarkColors
    } else {
        LuxLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LuxTypography,
        content = content,
    )
}
