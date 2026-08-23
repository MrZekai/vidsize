package com.fitsize.compressor.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.fitsize.compressor.model.CompressionPreset
import com.fitsize.compressor.model.CompressionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
object CompressionEngine {

    suspend fun compress(
        context: Context,
        input: Uri,
        preset: CompressionPreset,
    ): CompressionResult {
        val info = withContext(Dispatchers.IO) { VideoProbe.probe(context, input) }
        val plan = CompressionPlanner.plan(info, preset)
        val started = System.currentTimeMillis()
        val temp = File(context.cacheDir, "fitsize_${System.nanoTime()}.mp4")

        try {
            val export = runExport(
                context = context,
                input = input,
                output = temp,
                videoBitrate = plan.videoBitrate,
                audioBitrate = plan.audioBitrate,
                targetHeight = plan.targetHeight,
            )
            val actual = temp.length().takeIf { it > 0 } ?: export.fileSizeBytes
            require(actual > 0) { "Compression finished without a readable output file." }

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

        continuation.invokeOnCancellation { transformer.cancel() }
        val effects = Effects(
            emptyList(),
            listOf<Effect>(Presentation.createForHeight(targetHeight)),
        )
        val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(effects)
            .build()
        transformer.start(item, output.absolutePath)
    }

    private fun publish(context: Context, source: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Fitsize_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Fitsize")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = requireNotNull(resolver.insert(collection, values))
        try {
            resolver.openOutputStream(uri, "w")!!.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}
