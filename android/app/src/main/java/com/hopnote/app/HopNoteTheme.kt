package com.hopnote.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppTheme { ELECTRIC_BLUE, INDUSTRIAL_AMBER, LASER_RED }

private fun nightScheme(primary: Color, onPrimary: Color) = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    secondary = primary,
    onSecondary = onPrimary,
    tertiary = primary,
    background = Color(0xFF090A12),
    onBackground = Color(0xFFF6F1FF),
    surface = Color(0xFF121421),
    onSurface = Color(0xFFF6F1FF),
    surfaceVariant = Color(0xFF1D2030),
    onSurfaceVariant = Color(0xFFC8C5D6),
    outline = Color(0xFF62637D),
    error = Color(0xFFFF8B8B)
)

private val ElectricBlue = nightScheme(Color(0xFF3D7BFF), Color.White)
private val IndustrialAmber = nightScheme(Color(0xFFFFB800), Color(0xFF211600))
private val LaserRed = nightScheme(Color(0xFFFF4161), Color.White)

fun AppTheme.colorScheme(): ColorScheme = when (this) {
    AppTheme.ELECTRIC_BLUE -> ElectricBlue
    AppTheme.INDUSTRIAL_AMBER -> IndustrialAmber
    AppTheme.LASER_RED -> LaserRed
}

fun AppTheme.accentColor(): Color = when (this) {
    AppTheme.ELECTRIC_BLUE -> Color(0xFF3D7BFF)
    AppTheme.INDUSTRIAL_AMBER -> Color(0xFFFFB800)
    AppTheme.LASER_RED -> Color(0xFFFF4161)
}

@Composable
fun HopNoteTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = theme.colorScheme(), content = content)
}
