package com.fitsize.compressor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FitsizeInk = Color(0xFF101828)
val FitsizeMuted = Color(0xFF667085)
val FitsizeSoft = Color(0xFFF7F8FC)
val FitsizeCard = Color(0xFFFFFFFF)
val FitsizeBorder = Color(0xFFE7EAF0)
val FitsizeAccent = Color(0xFF6657E8)
val FitsizeAccentStrong = Color(0xFF5546D8)
val FitsizeAccentSoft = Color(0xFFF0EEFF)
val FitsizeBlueSoft = Color(0xFFEEF6FF)
val FitsizeSuccess = Color(0xFF16A36A)
val FitsizeSuccessSoft = Color(0xFFEAF8F1)

private val Light = lightColorScheme(
    primary = FitsizeAccent,
    onPrimary = Color.White,
    primaryContainer = FitsizeAccentSoft,
    onPrimaryContainer = FitsizeInk,
    secondary = Color(0xFF2E77D0),
    tertiary = FitsizeSuccess,
    background = FitsizeSoft,
    onBackground = FitsizeInk,
    surface = FitsizeCard,
    onSurface = FitsizeInk,
    surfaceVariant = Color(0xFFF3F5F9),
    onSurfaceVariant = FitsizeMuted,
    outline = Color(0xFFD6DAE3),
    outlineVariant = FitsizeBorder,
    error = Color(0xFFB42318),
)

@Composable
fun FitsizeTheme(content: @Composable () -> Unit) {
    // V1 visual direction is intentionally Light Minimal.
    // Device dark mode must not silently replace the approved product identity.
    MaterialTheme(
        colorScheme = Light,
        typography = MaterialTheme.typography,
        content = content,
    )
}
