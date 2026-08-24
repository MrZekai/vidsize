package com.vidsize.compressor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidsize.compressor.ui.theme.VidsizeColor

/**
 * The hero illustration.
 *
 * Drawn with Canvas rather than shipped as a PNG so it is resolution
 * independent, weighs nothing in the APK, and can be recoloured from the
 * palette. It is deliberately quiet: a soft indigo halo, a media card, a play
 * mark, and a small "reduced" badge. No motion, no glassmorphism, no stock
 * gradient blob.
 */
@Composable
fun HeroArt(
    modifier: Modifier = Modifier,
    size: Dp = 116.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val centre = Offset(w * 0.46f, h * 0.44f)
        val radius = w * 0.42f

        // 1. Halo — three concentric washes, each fainter than the last.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VidsizeColor.Indigo.copy(alpha = 0.16f),
                    VidsizeColor.Indigo.copy(alpha = 0.02f),
                ),
                center = centre,
                radius = radius * 1.5f,
            ),
            radius = radius * 1.5f,
            center = centre,
        )
        drawCircle(
            color = VidsizeColor.Indigo.copy(alpha = 0.07f),
            radius = radius * 1.16f,
            center = centre,
        )
        drawCircle(
            color = VidsizeColor.Indigo.copy(alpha = 0.10f),
            radius = radius * 0.88f,
            center = centre,
        )

        // 2. Thin orbit ring for structure.
        drawCircle(
            color = VidsizeColor.Indigo.copy(alpha = 0.18f),
            radius = radius * 1.34f,
            center = centre,
            style = Stroke(width = 1.dp.toPx()),
        )

        // 3. The media card.
        val cardW = w * 0.38f
        val cardH = h * 0.40f
        val cardTopLeft = Offset(centre.x - cardW / 2f, centre.y - cardH / 2f)
        drawRoundRect(
            color = Color.White,
            topLeft = cardTopLeft,
            size = Size(cardW, cardH),
            cornerRadius = CornerRadius(w * 0.055f, w * 0.055f),
        )
        drawRoundRect(
            color = VidsizeColor.Indigo.copy(alpha = 0.16f),
            topLeft = cardTopLeft,
            size = Size(cardW, cardH),
            cornerRadius = CornerRadius(w * 0.055f, w * 0.055f),
            style = Stroke(width = 1.dp.toPx()),
        )

        // 4. Play mark inside the card.
        val play = Path().apply {
            val px = centre.x - cardW * 0.13f
            val py = centre.y
            val ph = cardH * 0.26f
            moveTo(px, py - ph)
            lineTo(px + cardW * 0.30f, py)
            lineTo(px, py + ph)
            close()
        }
        drawPath(path = play, brush = VidsizeColor.PrimaryGradient)

        // 5. "Reduced" badge — a mint chip with a downward arrow, no claim text.
        val badgeW = w * 0.30f
        val badgeH = h * 0.155f
        val badgeTopLeft = Offset(centre.x + cardW * 0.30f, centre.y + cardH * 0.42f)
        drawRoundRect(
            color = VidsizeColor.MintSoft,
            topLeft = badgeTopLeft,
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(badgeH / 2f, badgeH / 2f),
        )
        drawRoundRect(
            color = VidsizeColor.MintBorder,
            topLeft = badgeTopLeft,
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(badgeH / 2f, badgeH / 2f),
            style = Stroke(width = 1.dp.toPx()),
        )
        drawDownArrow(
            centre = Offset(badgeTopLeft.x + badgeW * 0.30f, badgeTopLeft.y + badgeH / 2f),
            height = badgeH * 0.46f,
            color = VidsizeColor.Mint,
        )
        // Two shrinking bars to the right of the arrow, reading as "smaller".
        val barX = badgeTopLeft.x + badgeW * 0.52f
        drawRoundRect(
            color = VidsizeColor.Mint.copy(alpha = 0.85f),
            topLeft = Offset(barX, badgeTopLeft.y + badgeH * 0.30f),
            size = Size(badgeW * 0.34f, badgeH * 0.14f),
            cornerRadius = CornerRadius(badgeH * 0.07f, badgeH * 0.07f),
        )
        drawRoundRect(
            color = VidsizeColor.Mint.copy(alpha = 0.45f),
            topLeft = Offset(barX, badgeTopLeft.y + badgeH * 0.56f),
            size = Size(badgeW * 0.20f, badgeH * 0.14f),
            cornerRadius = CornerRadius(badgeH * 0.07f, badgeH * 0.07f),
        )

        // 6. Two small sparkles for depth.
        drawSparkle(Offset(w * 0.90f, h * 0.16f), w * 0.035f, VidsizeColor.Violet.copy(alpha = 0.5f))
        drawSparkle(Offset(w * 0.10f, h * 0.72f), w * 0.026f, VidsizeColor.Indigo.copy(alpha = 0.35f))
    }
}

private fun DrawScope.drawDownArrow(centre: Offset, height: Float, color: Color) {
    val half = height / 2f
    val path = Path().apply {
        moveTo(centre.x - half * 0.85f, centre.y - half * 0.15f)
        lineTo(centre.x, centre.y + half * 0.8f)
        lineTo(centre.x + half * 0.85f, centre.y - half * 0.15f)
        close()
    }
    drawPath(path = path, color = color)
    drawRoundRect(
        color = color,
        topLeft = Offset(centre.x - half * 0.16f, centre.y - half * 0.95f),
        size = Size(half * 0.32f, half * 0.9f),
        cornerRadius = CornerRadius(half * 0.16f, half * 0.16f),
    )
}

private fun DrawScope.drawSparkle(centre: Offset, radius: Float, color: Color) {
    // Four-point diamond built from straight segments only — no bezier APIs,
    // so this stays source-compatible across Compose releases.
    val path = Path().apply {
        moveTo(centre.x, centre.y - radius)
        lineTo(centre.x + radius * 0.42f, centre.y)
        lineTo(centre.x, centre.y + radius)
        lineTo(centre.x - radius * 0.42f, centre.y)
        close()
    }
    drawPath(path = path, color = color)
}
