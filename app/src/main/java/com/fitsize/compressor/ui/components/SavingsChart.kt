package com.fitsize.compressor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitsize.compressor.ui.theme.FitsizeColor

/**
 * Compact savings sparkline for the "Storage saved" card.
 *
 * Takes the most recent compressions (oldest → newest) as saved-byte values and
 * draws them as a rising bar set. With no history it draws a faint placeholder
 * so the card still has structure on first launch instead of a blank hole.
 *
 * Bars are proportional to the largest value in the window, which is the honest
 * reading for "recent activity" — it is a shape, not a measured axis, so no
 * numbers are implied.
 */
@Composable
fun SavingsChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    barCount: Int = 8,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val gap = w * 0.055f
        val barWidth = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
        val radius = CornerRadius(barWidth * 0.34f, barWidth * 0.34f)

        val window = values.takeLast(barCount)
        val maxValue = window.maxOrNull() ?: 0L

        for (index in 0 until barCount) {
            val x = index * (barWidth + gap)
            val filledIndex = index - (barCount - window.size)

            val fraction: Float
            val empty: Boolean
            if (filledIndex >= 0 && maxValue > 0L) {
                fraction = (window[filledIndex].toFloat() / maxValue.toFloat())
                    .coerceIn(0.18f, 1f)
                empty = false
            } else {
                // Placeholder rhythm: a gentle rise so the empty state still
                // reads as a chart rather than as a broken component.
                fraction = 0.16f + (index / barCount.toFloat()) * 0.22f
                empty = true
            }

            val barHeight = (h * fraction).coerceAtLeast(barWidth * 0.6f)
            val colour = when {
                empty -> FitsizeColor.Border
                index == barCount - 1 -> FitsizeColor.Indigo
                else -> FitsizeColor.Indigo.copy(alpha = 0.28f + 0.06f * index)
            }

            drawRoundRect(
                color = colour,
                topLeft = Offset(x, h - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}
