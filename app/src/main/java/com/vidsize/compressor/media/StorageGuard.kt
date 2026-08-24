package com.vidsize.compressor.media

import android.content.Context
import android.os.StatFs
import com.vidsize.compressor.model.VideoInfo

/**
 * Pre-flight checks that run *before* an export starts.
 *
 * This exists because of what the competitor reviews in this category actually
 * say. The most damaging failure is not slow compression — it is a job that runs
 * for four minutes, fills the disk, and dies, leaving the user with a wasted
 * wait and sometimes an unplayable file. That failure is entirely predictable
 * from numbers we already have before the first frame is encoded.
 *
 * The product decision that goes with it: **Vidsize advertises no maximum file
 * size.** There is no fixed limit to advertise — the real ceiling is the
 * device's free space, its encoder, and the user's patience, and all three vary
 * per phone. Publishing "supports up to 4 GB" would be a promise some devices
 * cannot keep, and every phone that fails below the advertised number produces a
 * one-star review. Instead the app checks quietly, refuses early with a specific
 * reason, and warns when a job will simply take a while.
 */
object StorageGuard {

    /** Muxer overhead plus breathing room for the MediaStore copy. */
    private const val OVERHEAD_BYTES = 64L * 1024L * 1024L

    /** Never start a job on a device that is essentially full. */
    private const val FLOOR_BYTES = 150L * 1024L * 1024L

    /** Above this, warn the user that the job is long. */
    private const val LARGE_SOURCE_BYTES = 1_500L * 1024L * 1024L

    /** Above this duration, warn as well. */
    private const val LONG_DURATION_MS = 15L * 60L * 1000L

    data class Verdict(
        val hasRoom: Boolean,
        val requiredBytes: Long,
        val availableBytes: Long,
    )

    /**
     * Peak disk demand is roughly two copies of the *output*: the temporary file
     * the encoder writes, plus the copy published into MediaStore before the
     * temp file is deleted. Sizing against the estimated output rather than the
     * source keeps the check honest — refusing a 4 GB source that compresses to
     * 300 MB on a phone with 1 GB free would be wrong.
     */
    fun requiredBytes(estimatedOutputBytes: Long): Long =
        maxOf(estimatedOutputBytes * 2L + OVERHEAD_BYTES, FLOOR_BYTES)

    fun availableBytes(context: Context): Long = runCatching {
        val stat = StatFs(context.cacheDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(Long.MAX_VALUE) // A failed probe must never block a valid job.

    fun check(context: Context, estimatedOutputBytes: Long): Verdict {
        val required = requiredBytes(estimatedOutputBytes)
        val available = availableBytes(context)
        return Verdict(
            hasRoom = available >= required,
            requiredBytes = required,
            availableBytes = available,
        )
    }

    /**
     * True for jobs worth warning about. Not a refusal — plenty of users
     * genuinely want to compress a 40-minute 4K clip, and they should be allowed
     * to. They should simply not be surprised by how long it takes.
     */
    fun isLongJob(info: VideoInfo): Boolean =
        info.sourceBytes > LARGE_SOURCE_BYTES || info.durationMs > LONG_DURATION_MS
}
