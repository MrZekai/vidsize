package com.vidsize.compressor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VidsizeColorScheme = lightColorScheme(
    primary = VidsizeColor.Indigo,
    onPrimary = Color.White,
    primaryContainer = VidsizeColor.IndigoSoft,
    onPrimaryContainer = VidsizeColor.Ink,

    secondary = VidsizeColor.Cyan,
    onSecondary = Color.White,
    secondaryContainer = VidsizeColor.CyanSoft,
    onSecondaryContainer = VidsizeColor.Ink,

    tertiary = VidsizeColor.Mint,
    onTertiary = Color.White,
    tertiaryContainer = VidsizeColor.MintSoft,
    onTertiaryContainer = VidsizeColor.Ink,

    background = VidsizeColor.Background,
    onBackground = VidsizeColor.Ink,

    surface = VidsizeColor.Surface,
    onSurface = VidsizeColor.Ink,
    surfaceVariant = VidsizeColor.SurfaceMuted,
    onSurfaceVariant = VidsizeColor.Muted,

    outline = VidsizeColor.BorderStrong,
    outlineVariant = VidsizeColor.Border,

    error = VidsizeColor.Danger,
    onError = Color.White,
    errorContainer = VidsizeColor.DangerSoft,
    onErrorContainer = VidsizeColor.Danger,

    scrim = VidsizeColor.Scrim,
)

private val VidsizeShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/**
 * Vidsize theme.
 *
 * V1 is deliberately **light only**. The product identity was approved as Light
 * Minimal, and a half-finished dark palette would ship a second, unreviewed
 * visual language. The device's dark-mode setting is therefore ignored here on
 * purpose — see [com.vidsize.compressor.MainActivity], which pins the system
 * bars to dark icons so the status bar stays legible over the light canvas even
 * when the phone itself is in dark mode.
 *
 * A dark theme is a V2 item: add a `darkColorScheme` alongside this one and
 * switch on `isSystemInDarkTheme()`. Nothing else in the UI needs to change,
 * because every screen reads its colours from [VidsizeColor] rather than
 * hard-coding hex values.
 */
@Composable
fun VidsizeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VidsizeColorScheme,
        typography = VidsizeTypography,
        shapes = VidsizeShapes,
        content = content,
    )
}
