package com.hopnote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HopNoteNight = darkColorScheme(
    primary = Color(0xFFB026FF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF19E6FF),
    onSecondary = Color(0xFF001F25),
    tertiary = Color(0xFFFF4FB8),
    background = Color(0xFF090A12),
    onBackground = Color(0xFFF6F1FF),
    surface = Color(0xFF121421),
    onSurface = Color(0xFFF6F1FF),
    surfaceVariant = Color(0xFF1D2030),
    onSurfaceVariant = Color(0xFFC8C5D6),
    outline = Color(0xFF62637D),
    error = Color(0xFFFF8B8B)
)

@Composable
fun HopNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HopNoteNight, content = content)
}
