package com.remotwrt.bot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = RemotTeal,
    onPrimary = Color(0xFF00201C),
    secondary = RemotTealBright,
    background = RemotBackground,
    surface = RemotSurface,
    surfaceVariant = RemotSurfaceElevated,
    error = RemotRed
)

private val LightColors = lightColorScheme(
    primary = RemotTeal,
    error = RemotRed
)

@Composable
fun RemotWRTBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
