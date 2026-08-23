package com.fitsize.compressor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF6750F5)
private val AccentBlue = Color(0xFF387CF4)
private val Teal = Color(0xFF10BFA3)

private val Light = lightColorScheme(
    primary = Accent,
    secondary = AccentBlue,
    tertiary = Teal,
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F3F8),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    outlineVariant = Color(0xFFE5E7EF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9D8BFF),
    secondary = Color(0xFF75A7FF),
    tertiary = Color(0xFF55D7C1),
    background = Color(0xFF111417),
    surface = Color(0xFF181C21),
    surfaceVariant = Color(0xFF20252B),
)

@Composable
fun FitsizeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = MaterialTheme.typography,
        content = content,
    )
}
