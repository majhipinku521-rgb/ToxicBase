package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ToxicBaseDarkColorScheme = darkColorScheme(
    primary = ToxicGreen,
    onPrimary = OnToxicGreen,
    primaryContainer = ToxicGreenContainer,
    onPrimaryContainer = ToxicGreen,
    secondary = CyberCyan,
    onSecondary = Color(0xFF001F24),
    secondaryContainer = CyberCyanContainer,
    onSecondaryContainer = CyberCyan,
    tertiary = AmberWarn,
    onTertiary = Color(0xFF261900),
    tertiaryContainer = AmberWarnContainer,
    onTertiaryContainer = AmberWarn,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = CrimsonError,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ToxicBaseDarkColorScheme,
        typography = Typography,
        content = content
    )
}
