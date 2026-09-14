package com.vidsize.compressor.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.vidsize.compressor.model.CompressionPlan
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.CompressionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
object CompressionEngine {

    /** Prefix every scratch file shares, so a sweep can recognise them. */
    private const val TEMP_PREFIX = "vidsize_"

    /**
     * Deletes scratch files a previous process left in the cache.
     *
     * QA finding: the temp file is removed in a `finally`, which covers success,
     * failure and cancellation but not the one case that matters most for a job
     * running several minutes in the background - the process being killed. Each
     * orphan is the size of a video, and StorageGuard refuses to start a new job
     * when free space is low, so a few killed jobs could leave the app
     * permanently reporting "not enough space" on a device with plenty of it.
     *
     * Called once at startup, when by definition no job of ours is running, so
     * any `vidsize_*` file present is from a process that no longer exists.
     */
    fun sweepOrphanedTempFiles(context: Context) {
        runCatching {
            context.cacheDir.listFiles { file ->
                file.isFile && file.name.startsWith(TEMP_PREFIX)
            }?.forEach { runCatching { it.delete() } }
        }
    }

    /**
     * `Vidsize_2026-09-14_06-50-32.mp4`. Sorts chronologically as text, is
     * legal on every filesystem Android exposes, and reads as a date at a
     * glance.
     */
    private const val NAME_TIMESTAMP_PATTERN = "yyyy-MM-dd_HH-mm-ss"

    private const val PROGRESS_POLL_MS = 300L
    private const val PENDING_EXPIRY_MILLIS = 24L * 60L * 60L * 1000L
    private const val COPY_BUFFER_BYTES = 256 * 1024

    /**
     * Share of the reported 0..1 progress that belongs to the encode. The
     * remainder belongs to the MediaStore copy, which on a 400 MB output takes
     * long enough that leaving the ring pinned at 100% reads as a hang.
     */
    private const val ENCODE_PROGRESS_SHARE = 0.90f

    /**
     * @param watermark whether the output carries the Vidsize mark. Decided by
     *        the caller, never here: a free export is marked, and an export the
     *        user has paid for with a rewarded ad is not. See [Watermark].
     */
    suspend fun compress(
        context: Context,
        input: Uri,
        preset: CompressionPreset,
        watermark: Boolean,
        onProgress: ((Float) -> Unit)? = null,
    ): CompressionResult {
        val info = withContext(Dispatchers.IO) { VideoProbe.probe(context, input) }
        val plan = CompressionPlanner.plan(info, preset)

        // Fail before the encode, not four minutes into it. The UI already
        // blocks this, but a share-sheet entry or a stale pre-flight check can
        // still reach here.
        if (!plan.viable) throw NoCompressionSavingsException()

        val storage = StorageGuard.check(context, plan.estimatedOutputBytes, info.sourceBytes)
        if (!storage.hasRoom) throw OutOfSpaceException()

        val started = System.currentTimeMillis()
        val temp = File(context.cacheDir, "$TEMP_PREFIX${System.nanoTime()}.mp4")

        try {
            val export = runExportWithFallbacks(
                context = context,
                input = input,
                output = temp,
                plan = plan,
                hasAudio = info.hasAudio,
                watermark = watermark,
                onProgress = onProgress?.let { report ->
                    { fraction -> report(fraction * ENCODE_PROGRESS_SHARE) }
                },
            )
            val actual = temp.length().takeIf { it > 0 } ?: export.fileSizeBytes
            require(actual > 0) { "Compression finished without a readable output file." }

            // Do not publish a "successful" file that consumes the same or more
            // storage than the original. The user keeps the better original.
            if (info.sourceBytes > 0L && actual >= info.sourceBytes) {
                throw NoCompressionSavingsException()
            }

            val published = publish(context, input, temp) { fraction ->
                onProgress?.invoke(
                    ENCODE_PROGRESS_SHARE + fraction * (1f - ENCODE_PROGRESS_SHARE),
                )
            }
            return CompressionResult(
                outputUri = published,
                sourceUri = input,
                sourceBytes = info.sourceBytes,
                outputBytes = actual,
                elapsedMs = System.currentTimeMillis() - started,
                preset = preset,
                watermarked = watermark,
            )
        } finally {
            temp.delete()
        }
    }

    /**
     * One configuration the encoder will be asked for.
     *
     * @param useRequestedSettings false on the last-resort attempt, where
     *        Media3 is allowed to pick the video encoder settings itself. The
     *        `Presentation` effect still forces a transcode, so the output is
     *        still re-encoded; only the bitrate choice moves to Media3.
     */
    private data class Attempt(
        val label: String,
        val width: Int,
        val height: Int,
        val videoBitrate: Int,
        val useRequestedSettings: Boolean,
    )

    /**
     * Runs the export, retrying with progressively safer encoder configurations
     * before giving up.
     *
     * ## Why a ladder - QA v0.8.7 BUG-05 (Critical)
     *
     * v0.8.7 made exactly one attempt with whatever geometry the aspect
     * arithmetic produced. On the test device that attempt failed outright for
     * 4K and for a band of common SD/qHD sources, and because there was no
     * second attempt and no error surfaced, tapping COMPRESS VIDEO did nothing
     * observable at all.
     *
     * The rungs, in order:
     *
     *  1. **Faithful.** The planner's exact target geometry, snapped only to the
     *     alignment the device itself reports, with the bitrate clamped into the
     *     encoder's advertised range. This is the rung that should always run,
     *     and it preserves the source resolution and aspect ratio exactly
     *     (BUG-03).
     *  2. **Macroblock-aligned.** The same frame snapped to 16, for an encoder
     *     that rejects geometry it advertised as supported. Costs at most 8px on
     *     an edge.
     *  3. **1080p-capped, macroblock-aligned.** For a device whose encoder
     *     cannot do 4K even though its capability query says otherwise - the
     *     single most likely cause of the 3840x2160 failure on a budget chipset.
     *  4. **720p-capped, Media3's own encoder settings.** Nothing of Vidsize's
     *     own configuration is left to be wrong.
     *
     * A cancellation propagates immediately and is never retried. Out-of-space
     * is not retried either: another attempt would fail the same way, slower.
     */
    private suspend fun runExportWithFallbacks(
        context: Context,
        input: Uri,
        output: File,
        plan: CompressionPlan,
        hasAudio: Boolean,
        watermark: Boolean,
        onProgress: ((Float) -> Unit)?,
    ): ExportResult {
        val attempts = buildAttempts(plan)
        var lastFailure: Throwable? = null

        attempts.forEachIndexed { index, attempt ->
            try {
                return runExport(
                    context = context,
                    input = input,
                    output = output,
                    videoBitrate = attempt.videoBitrate,
                    audioBitrate = plan.audioBitrate,
                    targetWidth = attempt.width,
                    targetHeight = attempt.height,
                    useRequestedSettings = attempt.useRequestedSettings,
                    hasAudio = hasAudio,
                    watermark = watermark,
                    onProgress = onProgress,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (throwable.looksLikeOutOfSpace()) throw throwable
                lastFailure = throwable
                // A partial file from the failed attempt must not be handed to
                // the next one or, worse, published.
                runCatching { output.delete() }
                if (index < attempts.lastIndex) {
                    onProgress?.invoke(0f)
                }
            }
        }

        throw EncoderUnsupportedException(
            width = plan.targetWidth,
            height = plan.targetHeight,
            attempted = attempts.joinToString(", ") { "${it.label} ${it.width}x${it.height}" },
            cause = lastFailure,
        )
    }

    private fun buildAttempts(plan: CompressionPlan): List<Attempt> {
        val faithful = EncoderSupport.fitToEncoder(plan.targetWidth, plan.targetHeight)
        val aligned = EncoderSupport.fitToEncoder(
            plan.targetWidth,
            plan.targetHeight,
            minAlignment = EncoderSupport.SAFE_ALIGNMENT,
        )
        val capped1080 = EncoderSupport.fitToEncoder(
            plan.targetWidth,
            plan.targetHeight,
            minAlignment = EncoderSupport.SAFE_ALIGNMENT,
            maxShortEdge = 1080,
        )
        val capped720 = EncoderSupport.fitToEncoder(
            plan.targetWidth,
            plan.targetHeight,
            minAlignment = EncoderSupport.SAFE_ALIGNMENT,
            maxShortEdge = 720,
        )

        val bitrate = EncoderSupport.clampBitrate(plan.videoBitrate)

        val candidates = listOf(
            Attempt("faithful", faithful.width, faithful.height, bitrate, true),
            Attempt("aligned", aligned.width, aligned.height, bitrate, true),
            Attempt("1080p", capped1080.width, capped1080.height, bitrate, true),
            Attempt("720p-default", capped720.width, capped720.height, bitrate, false),
        )

        // Drop rungs that duplicate an earlier one, except the final rung, whose
        // value is the encoder settings rather than the geometry.
        val seen = LinkedHashSet<String>()
        return candidates.filterIndexed { index, attempt ->
            val key = "${attempt.width}x${attempt.height}:${attempt.useRequestedSettings}"
            index == candidates.lastIndex || seen.add(key)
        }
    }

    private suspend fun runExport(
        context: Context,
        input: Uri,
        output: File,
        videoBitrate: Int,
        audioBitrate: Int,
        targetWidth: Int,
        targetHeight: Int,
        useRequestedSettings: Boolean,
        hasAudio: Boolean,
        watermark: Boolean,
        onProgress: ((Float) -> Unit)?,
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)

        if (useRequestedSettings) {
            val videoSettings = VideoEncoderSettings.Builder()
                .setBitrate(videoBitrate)
                .setiFrameIntervalSeconds(2f)
                .build()
            encoderFactoryBuilder.setRequestedVideoEncoderSettings(videoSettings)
        }

        // A zero-bitrate AudioEncoderSettings is meaningless and would be passed
        // straight to MediaFormat if the source turns out to have an audio track
        // that MediaMetadataRetriever failed to report.
        if (audioBitrate > 0) {
            encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder().setBitrate(audioBitrate).build(),
            )
        }
        val encoderFactory = encoderFactoryBuilder.build()

        lateinit var transformer: Transformer
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                if (continuation.isActive) continuation.resume(result)
            }

            override fun onError(
                composition: Composition,
                result: ExportResult,
                exception: ExportException,
            ) {
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        }

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(listener)
            .build()

        val handler = Handler(Looper.getMainLooper())
        var poller: Runnable? = null

        continuation.invokeOnCancellation {
            handler.post {
                poller?.let { handler.removeCallbacks(it) }
                transformer.cancel()
            }
        }

        // Both edges, not just the height.
        //
        // `Presentation.createForHeight` derived the other edge itself by
        // floating-point arithmetic, which is how a 854x480 source ended up at
        // 850x478 - a width that is not even a multiple of 4. Passing a frame
        // the device has already confirmed it supports removes a whole class of
        // encoder-configuration failure (QA v0.8.7 BUG-05).
        //
        // STRETCH rather than SCALE_TO_FIT: the requested frame differs from the
        // source ratio by at most half the encoder's alignment - well under 1% -
        // and a sub-1% stretch is invisible, whereas SCALE_TO_FIT would bake a
        // thin black bar into every output.
        // Presentation first, Watermark second. Order is the pipeline order, so
        // the mark is applied to the frame Vidsize is actually writing - sized
        // from targetHeight, which is why it is the same relative size on a 4K
        // source and a 480p one.
        val videoEffects = buildList<Effect> {
            add(
                Presentation.createForWidthAndHeight(
                    targetWidth,
                    targetHeight,
                    Presentation.LAYOUT_STRETCH_TO_FIT,
                ),
            )
            if (watermark) add(Watermark.effect(targetHeight))
        }
        val effects = Effects(emptyList(), videoEffects)
        val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(effects)
            .build()

        val sequence = if (hasAudio) {
            EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item))
        } else {
            EditedMediaItemSequence.withVideoFrom(listOf(item))
        }
        val composition = Composition.Builder(listOf(sequence))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        transformer.start(composition, output.absolutePath)

        if (onProgress != null) {
            val holder = ProgressHolder()
            val runnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress.coerceIn(0, 100) / 100f)
                    }
                    handler.postDelayed(this, PROGRESS_POLL_MS)
                }
            }
            poller = runnable
            handler.postDelayed(runnable, PROGRESS_POLL_MS)
        }
    }

    /**
     * The name the user actually sees.
     *
     * QA finding: the output was named `Vidsize_<epoch millis>.mp4`. That string
     * carries the same information as a date and communicates none of it - in
     * the recent list it reads as a serial number, and in a share sheet or a
     * chat it tells the recipient nothing about what they were sent.
     *
     * Local time, not UTC: this name exists to be recognised by the person who
     * made the file, and they think in their own clock. Colons are illegal in a
     * file name and dots would fight the extension, so the time is separated
     * with hyphens.
     *
     * Collisions are MediaStore's problem, not this function's: two files
     * created in the same second get `(1)` appended by the provider. A counter
     * here would only duplicate that, and less reliably.
     */
    internal fun outputDisplayName(
        nowMillis: Long = System.currentTimeMillis(),
        sourceDisplayName: String? = null,
    ): String {
        val base = sourceDisplayName
            ?.substringBeforeLast('.')
            ?.replace(Regex("""[\/:*?"<>|]"""), "_")
            ?.trim(' ', '.', '_')
            ?.take(80)
            ?.takeIf { it.isNotBlank() }
        if (base != null) return "${base}_VidSize.mp4"

        val stamp = SimpleDateFormat(NAME_TIMESTAMP_PATTERN, Locale.US)
            .format(Date(nowMillis))
        return "Vidsize_$stamp.mp4"
    }

    private fun sourceDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    /**
     * Copies the encoded temp file into MediaStore.
     *
     * Written as a cancellable manual copy rather than `copyTo`: `copyTo` has no
     * suspension point, so a Cancel pressed during this phase used to run the
     * copy to completion, flip IS_PENDING to 0, and leave an orphan file in the
     * gallery that the app had no history row for.
     */
    private suspend fun publish(
        context: Context,
        input: Uri,
        source: File,
        onProgress: (Float) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val originalDisplayName = sourceDisplayName(context, input)
        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                outputDisplayName(sourceDisplayName = originalDisplayName),
            )
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Vidsize")
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(
                MediaStore.MediaColumns.DATE_EXPIRES,
                (System.currentTimeMillis() + PENDING_EXPIRY_MILLIS) / 1000L,
            )
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = requireNotNull(resolver.insert(collection, values))
        try {
            val total = source.length().coerceAtLeast(1L)
            resolver.openOutputStream(uri, "w")!!.use { out ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var copied = 0L
                    var lastReported = -1
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        copied += read
                        val percent = ((copied * 100L) / total).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent / 100f)
                        }
                    }
                    out.flush()
                }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            values.putNull(MediaStore.MediaColumns.DATE_EXPIRES)
            resolver.update(uri, values, null, null)
            uri
        } catch (cancellation: CancellationException) {
            // Cancelled mid-copy: remove the half-written row so the user never
            // finds a file in the gallery from a job they cancelled.
            runCatching { resolver.delete(uri, null, null) }
            throw cancellation
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }
}
