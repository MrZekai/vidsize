package com.fitsize.compressor.ui.components

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
import com.fitsize.compressor.ui.theme.FitsizeColor
import com.fitsize.compressor.ui.theme.FitsizeType

/**
 * Determinate progress ring.
 *
 * Hand-drawn rather than using [androidx.compose.material3.CircularProgressIndicator]
 * for two reasons: the Material indicator cannot take a gradient stroke, and its
 * progress parameter has changed signature across Material 3 releases. A Canvas
 * has neither problem and gives us the exact stroke width and cap we want.
 *
 * @param progress 0f..1f. Values outside that range are clamped.
 * @param indeterminateSweep when the encoder has not reported a figure yet, the
 *        ring shows a fixed hint arc instead of pretending to know.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    strokeWidth: Dp = 10.dp,
    indeterminateSweep: Boolean = false,
    centreLabel: String? = null,
) {
    val safe = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = safe,
        animationSpec = tween(durationMillis = 320),
        label = "fitsize-progress",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = FitsizeColor.IndigoSoft,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            val sweep = if (indeterminateSweep) 72f else 360f * animated
            if (sweep > 0f) {
                drawArc(
                    brush = FitsizeColor.PrimaryGradient,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        if (centreLabel != null) {
            Text(
                text = centreLabel,
                style = FitsizeType.figure,
                color = FitsizeColor.Ink,
            )
        }
    }
}
