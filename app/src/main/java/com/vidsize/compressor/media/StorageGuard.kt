package com.vidsize.compressor.media

import android.content.Context
import android.os.StatFs
import com.vidsize.compressor.model.VideoInfo

/**
 * Pre-flight checks that run *before* an export starts.
 *
 * The most damaging failure in this category is not slow compression - it is a
 * job that runs for four minutes, fills the disk, and dies. That failure is
 * entirely predictable from numbers we already have before the first frame is
 * encoded.
 *
 * **Vidsize advertises no maximum file size.** The real ceiling is the device's
 * free space, its encoder, and the user's patience, and all three vary per
 * phone. The app checks quietly, refuses early with a specific reason, and warns
 * when a job will simply take a while.
 */
object StorageGuard {

    /** Muxer overhead plus breathing room for the MediaStore copy. */
    private const val OVERHEAD_BYTES = 64L * 1024L * 1024L

    /** Never start a job on a device that is essentially full. */
    private const val FLOOR_BYTES = 150L * 1024L * 1024L

    /**
     * The estimate can under-predict when a hardware encoder overshoots its VBR
     * target, so the check is also floored against a share of the source. Sizing
     * purely against the estimate is what let a job start with 130 MB free and
     * then need 244 MB.
     */
    private const val SOURCE_SAFETY_SHARE = 0.60

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
     * temp file is deleted.
     */
    fun requiredBytes(estimatedOutputBytes: Long, sourceBytes: Long = 0L): Long {
        val safetyFloor = if (sourceBytes > 0L) {
            (sourceBytes * SOURCE_SAFETY_SHARE).toLong()
        } else {
            0L
        }
        val worstCaseOutput = maxOf(estimatedOutputBytes, safetyFloor)
        return maxOf(worstCaseOutput * 2L + OVERHEAD_BYTES, FLOOR_BYTES)
    }

    fun availableBytes(context: Context): Long = runCatching {
        val stat = StatFs(context.cacheDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(Long.MAX_VALUE) // A failed probe must never block a valid job.

    fun check(
        context: Context,
        estimatedOutputBytes: Long,
        sourceBytes: Long = 0L,
    ): Verdict {
        val required = requiredBytes(estimatedOutputBytes, sourceBytes)
        val available = availableBytes(context)
        return Verdict(
            hasRoom = available >= required,
            requiredBytes = required,
            availableBytes = available,
        )
    }

    /**
     * True for jobs worth warning about. Not a refusal - plenty of users
     * genuinely want to compress a 40-minute 4K clip, and they should be
     * allowed to. They should simply not be surprised by how long it takes.
     */
    fun isLongJob(info: VideoInfo): Boolean =
        info.sourceBytes > LARGE_SOURCE_BYTES || info.durationMs > LONG_DURATION_MS
}
