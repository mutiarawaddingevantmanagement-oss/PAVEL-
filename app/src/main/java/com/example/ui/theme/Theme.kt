package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = TextWhite,
    secondary = AccentCyan,
    onSecondary = DeepBlueBg,
    tertiary = AccentCyanDim,
    background = DeepBlueBg,
    onBackground = TextWhite,
    surface = DeepBlueSurface,
    onSurface = TextWhite,
    error = ErrorRed,
    onError = TextWhite,
    primaryContainer = PrimaryBlue,
    onPrimaryContainer = TextWhite,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = TextGray
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Force dark colors for IPTV dark immersion mode
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
