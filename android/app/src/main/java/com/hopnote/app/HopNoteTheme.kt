package com.hopnote.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HopNoteNight = darkColorScheme(
    primary = Color(0xFF8CFFB4),
    onPrimary = Color(0xFF062010),
    secondary = Color(0xFF2BE4FF),
    onSecondary = Color(0xFF001E24),
    tertiary = Color(0xFFFF68DC),
    background = Color(0xFF07110E),
    onBackground = Color(0xFFE7FFF0),
    surface = Color(0xFF101C18),
    onSurface = Color(0xFFE7FFF0),
    surfaceVariant = Color(0xFF1A2A24),
    onSurfaceVariant = Color(0xFFB6CDC0),
    outline = Color(0xFF4A7561),
    error = Color(0xFFFF8B8B)
)

@Composable
fun HopNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HopNoteNight, content = content)
}
