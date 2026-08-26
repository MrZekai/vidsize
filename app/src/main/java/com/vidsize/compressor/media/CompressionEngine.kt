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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
object CompressionEngine {

    private const val PROGRESS_POLL_MS = 300L
    private const val PENDING_EXPIRY_MILLIS = 24L * 60L * 60L * 1000L

    suspend fun compress(
        context: Context,
        input: Uri,
        preset: CompressionPreset,
        onProgress: ((Float) -> Unit)? = null,
    ): CompressionResult {
        val info = withContext(Dispatchers.IO) { VideoProbe.probe(context, input) }
        val plan = CompressionPlanner.plan(info, preset)
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
                onProgress = onProgress,
            )
            val actual = temp.length().takeIf { it > 0 } ?: export.fileSizeBytes
            require(actual > 0) { "Compression finished without a readable output file." }

            // Do not publish a "successful" file that consumes the same or more
            // storage than the original. The user keeps the better original.
            if (info.sourceBytes > 0L && actual >= info.sourceBytes) {
                throw NoCompressionSavingsException()
            }

            val published = withContext(Dispatchers.IO) { publish(context, temp) }
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
        val audioSettings = AudioEncoderSettings.Builder()
            .setBitrate(audioBitrate)
            .build()
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(videoSettings)
            .setRequestedAudioEncoderSettings(audioSettings)
            .build()

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

    private fun publish(context: Context, source: File): Uri {
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
            resolver.openOutputStream(uri, "w")!!.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            values.putNull(MediaStore.MediaColumns.DATE_EXPIRES)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}
