package com.fitsize.compressor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FitsizeColorScheme = lightColorScheme(
    primary = FitsizeColor.Indigo,
    onPrimary = Color.White,
    primaryContainer = FitsizeColor.IndigoSoft,
    onPrimaryContainer = FitsizeColor.Ink,

    secondary = FitsizeColor.Cyan,
    onSecondary = Color.White,
    secondaryContainer = FitsizeColor.CyanSoft,
    onSecondaryContainer = FitsizeColor.Ink,

    tertiary = FitsizeColor.Mint,
    onTertiary = Color.White,
    tertiaryContainer = FitsizeColor.MintSoft,
    onTertiaryContainer = FitsizeColor.Ink,

    background = FitsizeColor.Background,
    onBackground = FitsizeColor.Ink,

    surface = FitsizeColor.Surface,
    onSurface = FitsizeColor.Ink,
    surfaceVariant = FitsizeColor.SurfaceMuted,
    onSurfaceVariant = FitsizeColor.Muted,

    outline = FitsizeColor.BorderStrong,
    outlineVariant = FitsizeColor.Border,

    error = FitsizeColor.Danger,
    onError = Color.White,
    errorContainer = FitsizeColor.DangerSoft,
    onErrorContainer = FitsizeColor.Danger,

    scrim = FitsizeColor.Scrim,
)

private val FitsizeShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/**
 * Fitsize theme.
 *
 * V1 is deliberately **light only**. The product identity was approved as Light
 * Minimal, and a half-finished dark palette would ship a second, unreviewed
 * visual language. The device's dark-mode setting is therefore ignored here on
 * purpose — see [com.fitsize.compressor.MainActivity], which pins the system
 * bars to dark icons so the status bar stays legible over the light canvas even
 * when the phone itself is in dark mode.
 *
 * A dark theme is a V2 item: add a `darkColorScheme` alongside this one and
 * switch on `isSystemInDarkTheme()`. Nothing else in the UI needs to change,
 * because every screen reads its colours from [FitsizeColor] rather than
 * hard-coding hex values.
 */
@Composable
fun FitsizeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FitsizeColorScheme,
        typography = FitsizeTypography,
        shapes = FitsizeShapes,
        content = content,
    )
}
