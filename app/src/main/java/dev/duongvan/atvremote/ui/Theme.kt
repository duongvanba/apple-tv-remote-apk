package dev.duongvan.atvremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF1B3E6F),
    onPrimaryContainer = Color(0xFFD7E3FF),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E6EB),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFE3E6EB),
    surfaceVariant = Color(0xFF232935),
    onSurfaceVariant = Color(0xFFB9C0CC)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C5FA8),
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E8F2)
)

@Composable
fun AtvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
