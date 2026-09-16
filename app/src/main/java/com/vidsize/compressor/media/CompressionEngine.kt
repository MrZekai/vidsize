package com.vidsize.compressor.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecInfo
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
    /**
     * How many times a size-target job may encode before it settles.
     *
     * ## Why a cap at all, and why three
     *
     * Hardware VBR does not land on a requested bitrate exactly, so the only way
     * to hit a byte ceiling is to encode, measure, and correct. Left uncapped
     * that is an unbounded loop on the user's battery, driven by an encoder that
     * may simply be unable to go lower.
     *
     * Three is where the arithmetic stops paying. Pass one lands within roughly
     * 20% - that is what [CompressionPlanner]'s variance constant is calibrated
     * for - and a proportional correction closes most of that gap in pass two.
     * Pass three exists for the stubborn cases. A fourth would double the wait
     * again to chase a few percent, and the honest answer at that point is to
     * hand back the smallest file produced and say what it is.
     */
    private const val MAX_TARGET_PASSES = 3

    /**
     * How many times a PRESET job may encode.
     *
     * Two, not three. A preset's promise is the estimate on the screen, and with
     * CBR the first pass normally lands on it - this is the net under the
     * tightrope, not the tightrope. One correction catches a device whose
     * encoder ignores the bitrate mode; a second would double the wait again for
     * a case that should not exist.
     */
    private const val MAX_PRESET_PASSES = 2

    /**
     * How far a preset's output may exceed its estimate before a correction pass
     * is worth the user's time.
     *
     * A 15% miss is a rounding difference nobody notices next to a multi-minute
     * encode. Beyond it the number on the screen was a lie, and re-encoding is
     * cheaper than shipping a lie. The field misses this exists for were +50%
     * and +65%.
     */
    private const val PRESET_OVERSHOOT_TOLERANCE = 1.15

    /**
     * @param targetBytes when non-null, the job aims at this output size instead
     *        of at [preset]'s quality level, and may encode up to
     *        [MAX_TARGET_PASSES] times to reach it.
     * @param onPass called before each encode with (pass number, pass ceiling),
     *        so the UI can explain why a job is on its second lap rather than
     *        appearing to restart.
     */
    suspend fun compress(
        context: Context,
        input: Uri,
        preset: CompressionPreset,
        watermark: Boolean,
        targetBytes: Long? = null,
        onProgress: ((Float) -> Unit)? = null,
        onPass: ((Int, Int) -> Unit)? = null,
    ): CompressionResult {
        val info = withContext(Dispatchers.IO) { VideoProbe.probe(context, input) }
        var plan = if (targetBytes != null) {
            CompressionPlanner.planForTarget(info, targetBytes)
        } else {
            CompressionPlanner.plan(info, preset)
        }

        // Fail before the encode, not four minutes into it. The UI already
        // blocks this, but a share-sheet entry or a stale pre-flight check can
        // still reach here.
        if (!plan.viable) throw NoCompressionSavingsException()

        // The same principle, for the failure the 4K field report exposed. When
        // the probe already established that no decoder on this device will
        // open the source, there is nothing to attempt: the ladder varies the
        // output frame, and the decoder's problem is the input frame. Refusing
        // here costs the user a dialog; not refusing cost them several minutes
        // and three identical failures.
        if (!info.deviceCanDecode) {
            throw SourceUndecodableException(width = info.width, height = info.height)
        }

        val storage = StorageGuard.check(context, plan.estimatedOutputBytes, info.sourceBytes)
        if (!storage.hasRoom) throw OutOfSpaceException()

        val started = System.currentTimeMillis()
        val passCeiling = if (targetBytes != null) MAX_TARGET_PASSES else MAX_PRESET_PASSES

        // What this job is correcting towards. A size target is the user's own
        // number; a preset is the estimate the screen showed them - which is a
        // promise the app made and should therefore have to keep.
        val goalBytes = targetBytes ?: plan.estimatedOutputBytes
        val acceptableBytes = if (targetBytes != null) {
            goalBytes
        } else {
            (goalBytes * PRESET_OVERSHOOT_TOLERANCE).toLong()
        }

        // The best file produced so far, and its size. Each pass writes its own
        // temp file rather than overwriting: a correction that overshoots
        // downward is still a worse file than the one before it, and
        // overwriting would have thrown the better one away.
        var best: File? = null
        var bestBytes = Long.MAX_VALUE

        try {
            for (pass in 1..passCeiling) {
                onPass?.invoke(pass, passCeiling)
                val candidate = File(context.cacheDir, "$TEMP_PREFIX${System.nanoTime()}.mp4")
                val export = runExportWithFallbacks(
                    context = context,
                    input = input,
                    output = candidate,
                    plan = plan,
                    hasAudio = info.hasAudio,
                    watermark = watermark,
                    // Display dimensions: VideoProbe has already applied any
                    // rotation metadata, and the shape check is only meaningful
                    // against what the viewer sees.
                    sourceWidth = info.width,
                    sourceHeight = info.height,
                    onProgress = onProgress?.let { report ->
                        { fraction -> report(fraction * ENCODE_PROGRESS_SHARE) }
                    },
                )
                val actual = candidate.length().takeIf { it > 0 } ?: export.fileSizeBytes
                require(actual > 0) { "Compression finished without a readable output file." }

                if (actual < bestBytes) {
                    best?.delete()
                    best = candidate
                    bestBytes = actual
                } else {
                    candidate.delete()
                }

                // Close enough: nothing left to correct.
                if (bestBytes <= acceptableBytes) break

                // Every pass re-encodes from the ORIGINAL. Chaining passes would
                // compound generation loss, so the second attempt is a different
                // encode of the same source, never a re-encode of the first.
                //
                // The correction that feeds the LAST pass may also drop the
                // frame size. Bitrate alone bottoms out at what the hardware
                // will accept, and a job that stops there reports the target as
                // missed - the one outcome this mode exists to avoid. Given a
                // last chance, it spends it on a smaller, clean frame instead of
                // giving up.
                //
                // A preset corrects towards its own estimate, so the same
                // machinery serves both modes. Its frame size is never dropped:
                // the user chose a quality level, and silently handing them
                // fewer pixels would be answering a question they did not ask.
                plan = CompressionPlanner.correctedForTarget(
                    info = info,
                    plan = if (targetBytes != null) plan else plan.copy(targetBytes = goalBytes),
                    actualBytes = actual,
                    allowResolutionDrop = targetBytes != null && pass == passCeiling - 1,
                )?.copy(targetBytes = targetBytes) ?: break
            }

            // Not `require(output != null && ...)`. That leans on the compiler
            // smart-casting through a contract on a compound condition to turn
            // File? into File for the publish() call below - which it may well
            // do, but "may well" is not a thing to ship in the one place that
            // decides whether the user's file exists. An explicit elvis makes
            // the type non-null by construction.
            val output = best ?: throw IllegalStateException(
                "Compression produced no output file.",
            )
            require(bestBytes > 0L) { "Compression produced an empty output file." }

            // Do not publish a "successful" file that consumes the same or more
            // storage than the original. The user keeps the better original.
            if (info.sourceBytes > 0L && bestBytes >= info.sourceBytes) {
                throw NoCompressionSavingsException()
            }

            val published = publish(context, output) { fraction ->
                onProgress?.invoke(
                    ENCODE_PROGRESS_SHARE + fraction * (1f - ENCODE_PROGRESS_SHARE),
                )
            }
            return CompressionResult(
                outputUri = published,
                sourceUri = input,
                sourceBytes = info.sourceBytes,
                outputBytes = bestBytes,
                elapsedMs = System.currentTimeMillis() - started,
                preset = plan.preset,
                watermarked = watermark,
                targetBytes = targetBytes,
                targetMet = targetBytes == null || bestBytes <= targetBytes,
            )
        } finally {
            best?.delete()
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
        sourceWidth: Int,
        sourceHeight: Int,
        onProgress: ((Float) -> Unit)?,
    ): ExportResult {
        val attempts = buildAttempts(plan)
        var lastFailure: Throwable? = null

        attempts.forEachIndexed { index, attempt ->
            try {
                val export = runExport(
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

                // QA NEW-01. The export "succeeded" and the file was wrong.
                //
                // Media3 reshapes a request the encoder will not take, under its
                // own fallback, and reports success for whatever came out. On
                // the QA device a 1080x1920 portrait became a 1080x1088 squash
                // and the app put it in the user's gallery behind a green tick.
                //
                // So the result is measured rather than assumed, and a wrong
                // shape is treated as this rung failing - which is exactly what
                // it is. The catch below deletes the file and moves to the next
                // configuration.
                verifyGeometry(output, sourceWidth, sourceHeight)

                return export
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (throwable.looksLikeOutOfSpace()) throw throwable

                // A partial file from the failed attempt must not be handed to
                // the next one or, worse, published.
                runCatching { output.delete() }

                // Stop the ladder dead when the DECODER is what failed.
                //
                // Every rung below this one differs only in the output frame,
                // and the decoder's problem is the input frame - it reads the
                // source at 3840x2160 whether this app is writing 1920x1088 or
                // 1280x720. The field report is exactly this: three rungs, three
                // identical `type=VideoDecoder` failures, several minutes gone,
                // and then a dialog telling the user their ENCODER could not
                // manage it "even at a lower resolution".
                //
                // The probe normally catches this before any encode starts.
                // This is the second line of defence, for the device that
                // advertises a capability it does not have.
                if (DecoderFailure.isDecoderSide(throwable)) {
                    throw SourceUndecodableException(
                        width = sourceWidth,
                        height = sourceHeight,
                        cause = throwable,
                    )
                }

                lastFailure = throwable
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

    /**
     * Throws unless the file at [output] has the same shape as the source.
     *
     * Separated from the ladder so the decision is one testable predicate
     * ([OutputVerification.aspectMatches]) and the I/O is one call. The
     * exceptions it throws are ordinary attempt failures: the caller deletes the
     * file and tries the next encoder configuration.
     */
    private fun verifyGeometry(output: File, sourceWidth: Int, sourceHeight: Int) {
        // Nothing to compare against. Probing the source is what normally
        // supplies these, and a source with no readable size never reaches here,
        // but a caller is not made to prove that: an unknown source shape means
        // the check abstains rather than rejecting a file it cannot judge.
        if (sourceWidth <= 0 || sourceHeight <= 0) return

        val geometry = VideoProbe.probeGeometry(output.absolutePath)
            ?: throw OutputUnreadableException()

        if (!OutputVerification.aspectMatches(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                outputWidth = geometry.width,
                outputHeight = geometry.height,
            )
        ) {
            throw OutputGeometryException(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                outputWidth = geometry.width,
                outputHeight = geometry.height,
            )
        }
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
                // CONSTANT bitrate, not the VBR default.
                //
                // This is the single line behind every size number the app has
                // ever got wrong. Media3's default is BITRATE_MODE_VBR, and a
                // hardware VBR encoder treats a requested bitrate as a
                // suggestion: on the field device it returned 2.24 Mbps when
                // asked for 1.04, and 2.71 Mbps when asked for 1.45 - 1.9x to
                // 2.2x over, every time.
                //
                // The planner could not model that. It carried a 1.22 overshoot
                // constant, which was both far too small to cover VBR and large
                // enough, applied to every estimate, to make Balanced look like
                // it saved nothing - so the app disabled its own default preset
                // on ordinary phone video while the jobs it did run missed their
                // predicted size by half.
                //
                // CBR makes the encoder spend the bitrate it was given. The
                // estimate becomes a number the file actually lands on, which is
                // what lets ENCODER_VARIANCE drop to 1.03 and what puts Balanced
                // back on the screen. The cost is real and accepted: CBR spends
                // bits on easy scenes that VBR would have saved. A predictable
                // file is worth more here than a slightly smaller one.
                .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
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
    internal fun outputDisplayName(nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat(NAME_TIMESTAMP_PATTERN, Locale.US)
            .format(Date(nowMillis))
        return "Vidsize_$stamp.mp4"
    }

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
        source: File,
        onProgress: (Float) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outputDisplayName())
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
