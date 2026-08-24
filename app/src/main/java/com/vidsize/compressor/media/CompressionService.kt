package com.vidsize.compressor.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vidsize.compressor.MainActivity
import com.vidsize.compressor.R
import com.vidsize.compressor.data.history.CompressionHistoryEntry
import com.vidsize.compressor.data.history.PrefsHistoryRepository
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.CompressionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Runs one compression, in the background, with a progress notification.
 *
 * This is the fix for the complaint that sits under every competitor in this
 * category: *"it stops when I switch apps."* Because the work now lives in a
 * started foreground service, the user can leave Vidsize, reply to a message,
 * and come back to a finished file.
 *
 * Design constraints this class respects:
 *
 *  - **The engine is untouched.** [CompressionEngine.compress] is called exactly
 *    as the screen used to call it. The service adds lifecycle, not logic.
 *  - **Media3's Transformer needs a Looper thread.** A `Service` runs on the main
 *    thread, so the job is launched on [Dispatchers.Main]; the engine still moves
 *    its own I/O to a background dispatcher internally.
 *  - **Android 15's six-hour cap.** `mediaProcessing` foreground services are
 *    time-limited and the system calls `onTimeout`; failing to stop promptly
 *    throws a fatal `RemoteServiceException`. The API 35 timeout callback is handled below.
 *  - **One job at a time.** A second start request while a job is running is
 *    ignored rather than queued — batch is a deliberate V1.1 feature, not an
 *    accident of concurrency.
 */
class CompressionService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelJob()
                return START_NOT_STICKY
            }
        }

        val uri = intent?.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        val preset = intent?.getStringExtra(EXTRA_PRESET)
            ?.let { name -> runCatching { CompressionPreset.valueOf(name) }.getOrNull() }

        if (uri == null || preset == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Already busy: ignore rather than trample the running export.
        if (job?.isActive == true) return START_NOT_STICKY

        startForegroundSafely(buildNotification(progressPercent = null))
        CompressionJobState.markRunning()

        job = scope.launch {
            runCatching {
                CompressionEngine.compress(
                    context = applicationContext,
                    input = uri,
                    preset = preset,
                    onProgress = { value ->
                        CompressionJobState.markProgress(value)
                        updateNotification((value.coerceIn(0f, 1f) * 100f).roundToInt())
                    },
                )
            }.onSuccess { result ->
                recordHistory(result)
                CompressionJobState.markDone(result)
                showCompletionNotification()
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    CompressionJobState.reset()
                } else {
                    CompressionJobState.markFailed(
                        message = throwable.message,
                        outOfSpace = throwable.isOutOfSpace(),
                    )
                }
            }
            stopSelfSafely()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** Android 15+ callback for the six-hour mediaProcessing time limit. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    private fun handleTimeout() {
        // The system gives us seconds, not minutes. Stop immediately.
        cancelJob()
    }

    private fun cancelJob() {
        job?.cancel()
        CompressionJobState.reset()
        stopSelfSafely()
    }

    // -- notification ---------------------------------------------------------

    private fun startForegroundSafely(notification: Notification) {
        // FOREGROUND_SERVICE_TYPE_MANIFEST is available from API 29. The
        // manifest declares only mediaProcessing, which Android documents as
        // valid for this transcoding use case and usable on earlier releases.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
        )
    }

    private fun updateNotification(progressPercent: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching {
            manager?.notify(NOTIFICATION_ID, buildNotification(progressPercent))
        }
    }

    private fun buildNotification(progressPercent: Int?): Notification {
        ensureChannel()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, CompressionService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, getString(R.string.cancel), cancel)

        if (progressPercent == null) {
            builder.setContentText(getString(R.string.processing_preparing))
            builder.setProgress(0, 0, true)
        } else {
            builder.setContentText(
                String.format(Locale.getDefault(), "%d%%", progressPercent),
            )
            builder.setProgress(100, progressPercent, false)
        }

        return builder.build()
    }

    private fun showCompletionNotification() {
        ensureChannel()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val openApp = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_done_title))
            .setContentText(getString(R.string.notification_done_body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { manager.notify(COMPLETION_NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun stopSelfSafely() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun recordHistory(result: CompressionResult) {
        runCatching {
            PrefsHistoryRepository(applicationContext).add(
                CompressionHistoryEntry(
                    id = System.currentTimeMillis(),
                    outputUri = result.outputUri.toString(),
                    displayName = queryDisplayName(result.outputUri),
                    sourceBytes = result.sourceBytes,
                    outputBytes = result.outputBytes,
                    presetTitle = result.preset.title,
                    completedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: getString(R.string.result_default_name)

    companion object {
        private const val CHANNEL_ID = "vidsize_compression"
        private const val NOTIFICATION_ID = 1001
        private const val COMPLETION_NOTIFICATION_ID = 1002
        private const val ACTION_CANCEL = "com.vidsize.compressor.CANCEL"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_PRESET = "preset"

        /** Starts a compression. Safe to call from the UI thread. */
        fun start(context: Context, uri: Uri, preset: CompressionPreset) {
            val intent = Intent(context, CompressionService::class.java)
                .putExtra(EXTRA_URI, uri.toString())
                .putExtra(EXTRA_PRESET, preset.name)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Cancels the running compression, if any. */
        fun cancel(context: Context) {
            val intent = Intent(context, CompressionService::class.java)
                .setAction(ACTION_CANCEL)
            runCatching { context.startService(intent) }
        }
    }
}

/**
 * Disk-full detection is message-based on purpose: the underlying cause surfaces
 * from several layers (muxer, file system, MediaStore) with different types, and
 * the user only needs to know which of two actions to take.
 */
private fun Throwable.isOutOfSpace(): Boolean {
    val text = (message ?: "").lowercase(Locale.US)
    return text.contains("enospc") ||
        text.contains("no space") ||
        text.contains("not enough space") ||
        text.contains("disk full")
}
