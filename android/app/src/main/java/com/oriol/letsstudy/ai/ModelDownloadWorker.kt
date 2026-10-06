package com.oriol.letsstudy.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import java.io.IOException

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val modelFile = ModelCatalog.modelFile(applicationContext)
        val partialFile = ModelCatalog.partialFile(applicationContext)
        val downloader = ModelFileDownloader()
        var lastProgressAt = 0L
        var lastPublishedBytes = -PROGRESS_UPDATE_BYTES

        return try {
            setDownloadForeground(0, "Preparing the on-device model…")
            setProgress(workDataOf(
                ModelDownloadRepository.KEY_STAGE to ModelDownloadState.Stage.DOWNLOADING.name,
                ModelDownloadRepository.KEY_DOWNLOADED_BYTES to partialFile.takeIf { it.isFile }?.length().orZero(),
                ModelDownloadRepository.KEY_TOTAL_BYTES to ModelCatalog.EXPECTED_SIZE_BYTES,
            ))

            downloader.download(
                url = ModelCatalog.downloadUrl,
                partialFile = partialFile,
                targetFile = modelFile,
                expectedSize = ModelCatalog.EXPECTED_SIZE_BYTES,
                expectedSha256 = ModelCatalog.SHA256,
                availableBytes = { ModelCatalog.modelDirectory(applicationContext).usableSpace },
                isCancelled = { isStopped },
                onProgress = { downloaded, total ->
                    if (isStopped) throw CancellationException("Model download cancelled.")
                    val now = System.currentTimeMillis()
                    if (downloaded - lastPublishedBytes >= PROGRESS_UPDATE_BYTES || now - lastProgressAt >= PROGRESS_UPDATE_MILLIS) {
                        lastProgressAt = now
                        lastPublishedBytes = downloaded
                        val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        setProgress(workDataOf(
                            ModelDownloadRepository.KEY_STAGE to ModelDownloadState.Stage.DOWNLOADING.name,
                            ModelDownloadRepository.KEY_DOWNLOADED_BYTES to downloaded,
                            ModelDownloadRepository.KEY_TOTAL_BYTES to total,
                        ))
                        setDownloadForeground(percent, "Downloading study model · $percent%")
                    }
                },
            )

            setProgress(workDataOf(
                ModelDownloadRepository.KEY_STAGE to ModelDownloadState.Stage.VERIFYING.name,
                ModelDownloadRepository.KEY_DOWNLOADED_BYTES to ModelCatalog.EXPECTED_SIZE_BYTES,
                ModelDownloadRepository.KEY_TOTAL_BYTES to ModelCatalog.EXPECTED_SIZE_BYTES,
            ))
            setDownloadForeground(100, "Verifying the downloaded model…")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ModelInsufficientSpaceException) {
            val shortage = (error.requiredBytes - error.availableBytes).coerceAtLeast(1L)
            Result.failure(workDataOf(ModelDownloadRepository.KEY_ERROR to "Free up about ${formatBytes(shortage)} of storage and try again."))
        } catch (error: ModelFileIntegrityException) {
            Result.failure(workDataOf(ModelDownloadRepository.KEY_ERROR to "The downloaded model failed its integrity check. Try downloading again."))
        } catch (error: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(ModelDownloadRepository.KEY_ERROR to "Download interrupted. Check your connection and try again."))
        } catch (error: Exception) {
            Result.failure(workDataOf(ModelDownloadRepository.KEY_ERROR to "The model could not be downloaded on this device."))
        }
    }

    private suspend fun setDownloadForeground(progress: Int, text: String) {
        ensureNotificationChannel()
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Let’sStudy model")
            .setContentText(text)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
        setForeground(foregroundInfo)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress while downloading Let’sStudy's on-device model"
                },
            )
        }
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private fun formatBytes(bytes: Long): String = "%.2f GB".format(bytes / 1_000_000_000.0)

    companion object {
        private const val CHANNEL_ID = "letsstudy_model_downloads"
        private const val NOTIFICATION_ID = 4271
        private const val MAX_RETRIES = 5
        private const val PROGRESS_UPDATE_MILLIS = 1_000L
        private const val PROGRESS_UPDATE_BYTES = 10L * 1024L * 1024L
    }
}
