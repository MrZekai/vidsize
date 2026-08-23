package com.fitsize.compressor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Spacing scale.
 *
 * Everything in the UI snaps to this scale. No ad-hoc 7.dp / 11.dp values —
 * inconsistent spacing is the single clearest signal of a developer-built
 * screen, and it is the first thing this redesign removes.
 */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp

    /** Horizontal page gutter. Holds at 360dp, 393dp and 412dp widths. */
    val gutter = 20.dp
}

/**
 * Corner radius scale — 14dp to 28dp, per the approved design direction.
 */
object Radius {
    val chip = 999.dp
    val sm = 14.dp
    val md = 18.dp
    val lg = 22.dp
    val xl = 26.dp
    val hero = 28.dp
}

object FitsizeShape {
    val chip = RoundedCornerShape(Radius.chip)
    val small = RoundedCornerShape(Radius.sm)
    val medium = RoundedCornerShape(Radius.md)
    val large = RoundedCornerShape(Radius.lg)
    val extraLarge = RoundedCornerShape(Radius.xl)
    val hero = RoundedCornerShape(Radius.hero)

    /** Bottom sheet: rounded top only, flush to the bottom edge. */
    val sheet = RoundedCornerShape(topStart = Radius.hero, topEnd = Radius.hero)
}

/**
 * Fixed component metrics.
 *
 * Heights are expressed as *minimums* everywhere in the UI so that a large
 * system font scale grows the control instead of clipping the label.
 */
object Sizes {
    val primaryButtonMinHeight = 56.dp
    val secondaryButtonMinHeight = 52.dp
    val iconButton = 44.dp
    val icon = 22.dp
    val iconSmall = 18.dp
    val mrecWidth = 300.dp
    val mrecHeight = 250.dp
}
