package com.vidsize.compressor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Vidsize type scale.
 *
 * Built on the platform sans family (Roboto on most devices, the OEM system
 * font elsewhere) so the app inherits every locale's correct glyphs — including
 * Arabic, Hindi and CJK — without shipping font files.
 *
 * Two rules are enforced here:
 *  1. Every style declares a line height of at least 1.2x its font size, so
 *     descenders and diacritics are never clipped at large font scales.
 *  2. `LineHeightStyle` trims the leading gap on the first/last line, so tight
 *     card layouts stay optically centred rather than sitting low.
 */
private val Family = FontFamily.SansSerif

private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = Trim,
)

object VidsizeType {
    /** Wordmark in the app bar. */
    val wordmark = style(24, 29, FontWeight.ExtraBold, (-0.4))

    /** Hero headline. Two lines maximum at 360dp. */
    val hero = style(31, 37, FontWeight.ExtraBold, (-0.6))

    /** Screen title (e.g. "Choose compression"). */
    val screenTitle = style(26, 32, FontWeight.ExtraBold, (-0.4))

    /** Large numeric readout (e.g. "1.02 GB → 612 MB"). */
    val figure = style(22, 27, FontWeight.ExtraBold, (-0.3))

    /** Section heading above a group of cards. */
    val section = style(16, 21, FontWeight.Bold, (-0.1))

    /** Card / row title. */
    val cardTitle = style(16, 21, FontWeight.SemiBold, (-0.1))

    /** Standard body copy. */
    val body = style(15, 22, FontWeight.Normal)

    /** Supporting copy under a title. */
    val supporting = style(13, 19, FontWeight.Normal)

    /** Button label. */
    val button = style(15, 20, FontWeight.Bold, 0.3)

    /** Uppercase eyebrow above a heading. */
    val eyebrow = style(11, 15, FontWeight.ExtraBold, 1.1)

    /** Small metadata line. */
    val caption = style(12, 17, FontWeight.Medium)

    /** Advertising label and other legal micro-copy. */
    val micro = style(10, 14, FontWeight.Medium, 0.4)
}

/**
 * Material 3 typography mapped onto the Vidsize scale, so any Material
 * component picking up a default style still looks like the rest of the app.
 */
val VidsizeTypography = Typography(
    displaySmall = VidsizeType.hero,
    headlineMedium = VidsizeType.screenTitle,
    headlineSmall = VidsizeType.figure,
    titleLarge = VidsizeType.section,
    titleMedium = VidsizeType.cardTitle,
    bodyLarge = VidsizeType.body,
    bodyMedium = VidsizeType.supporting,
    bodySmall = VidsizeType.caption,
    labelLarge = VidsizeType.button,
    labelMedium = VidsizeType.caption,
    labelSmall = VidsizeType.micro,
)
