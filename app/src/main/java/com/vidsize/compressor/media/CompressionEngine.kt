package com.vidsize.compressor.media

import android.content.ContentValues
import android.content.Context
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
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.CompressionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
object CompressionEngine {

    private const val PROGRESS_POLL_MS = 300L
    private const val PENDING_EXPIRY_MILLIS = 24L * 60L * 60L * 1000L
    private const val COPY_BUFFER_BYTES = 256 * 1024

    /**
     * Share of the reported 0..1 progress that belongs to the encode. The
     * remainder belongs to the MediaStore copy, which on a 400 MB output takes
     * long enough that leaving the ring pinned at 100% reads as a hang.
     */
    private const val ENCODE_PROGRESS_SHARE = 0.90f

    suspend fun compress(
        context: Context,
        input: Uri,
        preset: CompressionPreset,
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
        val temp = File(context.cacheDir, "vidsize_${System.nanoTime()}.mp4")

        try {
            val export = runExport(
                context = context,
                input = input,
                output = temp,
                videoBitrate = plan.videoBitrate,
                audioBitrate = plan.audioBitrate,
                targetHeight = plan.targetHeight,
                hasAudio = info.hasAudio,
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

            val published = publish(context, temp) { fraction ->
                onProgress?.invoke(
                    ENCODE_PROGRESS_SHARE + fraction * (1f - ENCODE_PROGRESS_SHARE),
                )
            }
            return CompressionResult(
                outputUri = published,
                sourceBytes = info.sourceBytes,
                outputBytes = actual,
                elapsedMs = System.currentTimeMillis() - started,
                preset = preset,
            )
        } finally {
            temp.delete()
        }
    }

    private suspend fun runExport(
        context: Context,
        input: Uri,
        output: File,
        videoBitrate: Int,
        audioBitrate: Int,
        targetHeight: Int,
        hasAudio: Boolean,
        onProgress: ((Float) -> Unit)?,
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val videoSettings = VideoEncoderSettings.Builder()
            .setBitrate(videoBitrate)
            .setiFrameIntervalSeconds(2f)
            .build()
        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(videoSettings)

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

        val effects = Effects(
            emptyList(),
            listOf<Effect>(Presentation.createForHeight(targetHeight)),
        )
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
            put(MediaStore.Video.Media.DISPLAY_NAME, "Vidsize_${System.currentTimeMillis()}.mp4")
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
