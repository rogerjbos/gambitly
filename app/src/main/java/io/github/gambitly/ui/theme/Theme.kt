package io.github.gambitly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF5B7F3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EBC6),
    onPrimaryContainer = Color(0xFF1D3708),
    secondary = Color(0xFF8A6D3B),
    secondaryContainer = Color(0xFFFFE6B0),
    onSecondaryContainer = Color(0xFF3A2A00),
    background = Color(0xFFF7F5EF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9E6DC),
    onSurfaceVariant = Color(0xFF47453D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9CF85),
    onPrimary = Color(0xFF1D3708),
    primaryContainer = Color(0xFF34501C),
    onPrimaryContainer = Color(0xFFD9EBC6),
    secondary = Color(0xFFDDC08A),
    secondaryContainer = Color(0xFF574313),
    onSecondaryContainer = Color(0xFFFFE6B0),
    background = Color(0xFF161512),
    surface = Color(0xFF262421),
    surfaceVariant = Color(0xFF3A3833),
    onSurfaceVariant = Color(0xFFCBC7BB),
)

@Composable
fun GambitlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
