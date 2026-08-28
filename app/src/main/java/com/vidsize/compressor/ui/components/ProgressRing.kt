package com.vidsize.compressor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeType

/**
 * Determinate progress ring.
 *
 * Hand-drawn rather than using [androidx.compose.material3.CircularProgressIndicator]
 * for two reasons: the Material indicator cannot take a gradient stroke, and its
 * progress parameter has changed signature across Material 3 releases. A Canvas
 * has neither problem and gives us the exact stroke width and cap we want.
 *
 * ## v0.8.4: no pseudo-progress
 *
 * The old `indeterminateSweep` flag drew a fixed 72-degree accent arc while the
 * encoder was still starting up. On a real device that reads as "20% done", and
 * when the first real figure arrived the arc collapsed back towards zero and
 * grew again - which looked like the job had restarted. There is now no arc at
 * all until a real figure exists: [trackOnly] draws the neutral background ring
 * and nothing else, and the accent arc always represents reported progress.
 *
 * @param progress 0f..1f. Values outside that range are clamped.
 * @param trackOnly draw only the neutral track. Used before the first real
 *        encoder figure arrives. Never animates, never sweeps.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    strokeWidth: Dp = 10.dp,
    trackOnly: Boolean = false,
    centreLabel: String? = null,
) {
    val safe = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = safe,
        animationSpec = tween(durationMillis = 320),
        label = "vidsize-progress",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = VidsizeColor.IndigoSoft,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // No accent arc until real progress exists. The geometry of the
            // track is identical in both states, so the ring never jumps when
            // the first figure arrives.
            if (!trackOnly) {
                val sweep = 360f * animated
                if (sweep > 0f) {
                    drawArc(
                        brush = VidsizeColor.PrimaryGradient,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }

        if (centreLabel != null) {
            Text(
                text = centreLabel,
                style = VidsizeType.figure,
                color = VidsizeColor.Ink,
            )
        }
    }
}
