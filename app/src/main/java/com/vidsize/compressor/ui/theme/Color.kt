package com.vidsize.compressor.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Vidsize colour tokens.
 *
 * V1 visual direction is "Light Minimal": a near-white canvas, one controlled
 * indigo/violet accent, and very small amounts of mint/cyan for supporting
 * signals. Every colour used anywhere in the UI is declared here so the palette
 * stays auditable and consistent.
 */
object VidsizeColor {

    // -- Canvas ---------------------------------------------------------------
    /** App background. Slightly cool off-white so white cards still read as cards. */
    val Background = Color(0xFFF6F6FB)

    /** Primary card / sheet surface. */
    val Surface = Color(0xFFFFFFFF)

    /** Secondary surface for inset rows and quiet blocks. */
    val SurfaceMuted = Color(0xFFF9F9FD)

    /** Hero panel wash. Barely tinted — this is not a gradient showpiece. */
    val SurfaceTint = Color(0xFFF2F1FE)

    // -- Text -----------------------------------------------------------------
    /** Headlines and primary values. */
    val Ink = Color(0xFF0F1222)

    /** Body copy on light surfaces. */
    val InkSoft = Color(0xFF474C61)

    /** Supporting / secondary copy. */
    val Muted = Color(0xFF6E7385)

    /** Eyebrows, captions, disabled copy. */
    val Faint = Color(0xFF9AA0B0)

    // -- Lines ----------------------------------------------------------------
    /** Hairline card border. */
    val Border = Color(0xFFEBECF3)

    /** Slightly stronger divider for structural separation (e.g. above the ad). */
    val BorderStrong = Color(0xFFE1E3EE)

    // -- Accent ---------------------------------------------------------------
    /** Primary accent. Used for the main action, selection and key values. */
    val Indigo = Color(0xFF5559EE)

    /** Pressed / deep variant of the primary accent. */
    val IndigoDeep = Color(0xFF4340CE)

    /** Gradient partner for the primary action. */
    val Violet = Color(0xFF8250F5)

    /** Tinted accent container (selected states, value chips). */
    val IndigoSoft = Color(0xFFEEEDFF)

    /** Border for tinted accent containers. */
    val IndigoBorder = Color(0xFFD9D7FB)

    // -- Supporting signals ---------------------------------------------------
    val Mint = Color(0xFF0EA97A)
    val MintSoft = Color(0xFFE6F7F0)
    val MintBorder = Color(0xFFC9EEE0)

    val Cyan = Color(0xFF0C93B4)
    val CyanSoft = Color(0xFFE5F5F9)
    val CyanBorder = Color(0xFFC6E8F1)

    // -- Status ---------------------------------------------------------------
    val Danger = Color(0xFFB42318)
    val DangerSoft = Color(0xFFFFF3F1)
    val DangerBorder = Color(0xFFFCD9D3)

    // -- Effects --------------------------------------------------------------
    /** Very soft, colour-matched shadow. Never a hard grey drop shadow. */
    val Shadow = Color(0x140F1222)

    /** Scrim behind the processing overlay and the result sheet. */
    val Scrim = Color(0x660F1222)

    /**
     * The single gradient in the product, reserved for the primary action.
     * Two stops only, low contrast between them — controlled, not decorative.
     */
    val PrimaryGradient = Brush.horizontalGradient(listOf(Indigo, Violet))

    /** Muted version of the primary gradient for disabled primary buttons. */
    val PrimaryGradientDisabled = Brush.horizontalGradient(
        listOf(Color(0xFFC8CAF4), Color(0xFFD5C7F7)),
    )
}
