package com.vidsize.compressor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay

/**
 * The mark burned into a free export, and the thing a rewarded ad removes.
 *
 * ## Why this costs nothing structurally
 *
 * Vidsize already runs every frame through the GL pipeline: [CompressionEngine]
 * always applies a [androidx.media3.effect.Presentation] effect to snap the
 * frame to the encoder's alignment, so there has never been a stream-copy fast
 * path to give up. Adding an overlay adds one shader pass to a pipeline that was
 * already running - not a new stage, and not a second encode.
 *
 * ## Why it is small, cornered and half-transparent
 *
 * The product's promise is "your video, smaller". A mark that fights the picture
 * breaks that promise and earns one-star reviews faster than it earns rewarded
 * impressions. A mark the user can see but does not resent is the one that gets
 * an ad watched to remove it, so the defaults here are deliberately modest and
 * all three live in one place, tunable without touching the drawing code.
 */
@OptIn(UnstableApi::class)
object Watermark {

    /** The wordmark. Not localised: it is a brand, not copy. */
    const val TEXT: String = "Vidsize"

    /** Cap height as a share of the output frame height. */
    private const val TEXT_HEIGHT_FRACTION = 0.045f

    /** Never smaller than this, or a 240p export gets an illegible smudge. */
    private const val MIN_TEXT_PX = 16f

    /** 0 is invisible, 1 is opaque. */
    private const val ALPHA_SCALE = 0.5f

    /**
     * Transparent margin baked into the bitmap, as a share of the text height.
     *
     * The margin lives in the bitmap rather than in the anchor because anchor
     * coordinates are normalised to the frame: the same offset would be a
     * different number of pixels on a 16:9 and a 9:16 video, so a margin tuned
     * on one would sit wrong on the other. Bitmap padding is in pixels and is
     * therefore identical on both.
     */
    private const val PADDING_FRACTION = 0.6f

    /** Drop shadow, so the mark stays readable on a white frame. */
    private const val SHADOW_ALPHA = 110

    /**
     * The overlay for a frame of this size.
     *
     * @param frameHeight the OUTPUT frame height in pixels. The overlay bitmap
     *        is drawn 1:1 into the frame, so the mark has to be sized from the
     *        frame Vidsize is actually producing, not from the source.
     */
    fun effect(frameHeight: Int): Effect {
        val bitmap = render(frameHeight)
        val settings = StaticOverlaySettings.Builder()
            .setAlphaScale(ALPHA_SCALE)
            // (1, -1) is the bottom-right corner in both coordinate spaces, so
            // the overlay's own bottom-right corner lands on the frame's. The
            // visible gap is the transparent padding inside the bitmap.
            .setBackgroundFrameAnchor(1f, -1f)
            .setOverlayFrameAnchor(1f, -1f)
            .build()
        return OverlayEffect(
            listOf<TextureOverlay>(
                BitmapOverlay.createStaticBitmapOverlay(bitmap, settings),
            ),
        )
    }

    /**
     * Draws the wordmark onto a transparent bitmap sized to its own text.
     *
     * ARGB_8888 is required: the overlay is uploaded straight to a GL texture,
     * and any other config produces either a crash or a black box.
     */
    private fun render(frameHeight: Int): Bitmap {
        val textSize = (frameHeight * TEXT_HEIGHT_FRACTION).coerceAtLeast(MIN_TEXT_PX)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(textSize / 8f, 0f, textSize / 16f, Color.argb(SHADOW_ALPHA, 0, 0, 0))
        }

        val padding = textSize * PADDING_FRACTION
        val metrics = paint.fontMetrics
        val textWidth = paint.measureText(TEXT)
        val textHeight = metrics.descent - metrics.ascent

        // Ceil, then force even: an odd texture dimension is legal but has bitten
        // enough drivers that it is not worth finding out on a user's phone.
        val width = evenAtLeastTwo((textWidth + padding * 2f).toInt() + 1)
        val height = evenAtLeastTwo((textHeight + padding * 2f).toInt() + 1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(TEXT, padding, padding - metrics.ascent, paint)
        return bitmap
    }

    private fun evenAtLeastTwo(value: Int): Int {
        val floored = if (value < 2) 2 else value
        return if (floored % 2 == 0) floored else floored + 1
    }
}
