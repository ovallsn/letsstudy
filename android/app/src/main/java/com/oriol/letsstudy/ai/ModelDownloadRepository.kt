package com.oriol.letsstudy.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.lifecycle.Observer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState
    data object Queued : ModelDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState
    data class Verifying(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Failed(val message: String) : ModelDownloadState
    data object Cancelled : ModelDownloadState

    enum class Stage { DOWNLOADING, VERIFYING }
}

class ModelDownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun observeState(): Flow<ModelDownloadState> = callbackFlow {
        val workInfos = workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
        val observer = Observer<List<WorkInfo>> { values -> trySend(readState(values)) }
        workInfos.observeForever(observer)
        awaitClose { workInfos.removeObserver(observer) }
    }

    fun enqueueDownload(allowMetered: Boolean = false) {
        if (ModelCatalog.modelFile(appContext).isFile) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_ALLOW_METERED to allowMetered))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun readState(workInfos: List<WorkInfo>): ModelDownloadState {
        if (ModelCatalog.modelFile(appContext).isFile) return ModelDownloadState.Ready
        val info = workInfos.firstOrNull() ?: return ModelDownloadState.NotDownloaded
        val progress = info.progress
        val downloaded = progress.getLong(KEY_DOWNLOADED_BYTES, 0L)
        val total = progress.getLong(KEY_TOTAL_BYTES, ModelCatalog.EXPECTED_SIZE_BYTES)
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelDownloadState.Queued
            WorkInfo.State.RUNNING -> {
                if (progress.getString(KEY_STAGE) == ModelDownloadState.Stage.VERIFYING.name) {
                    ModelDownloadState.Verifying(downloaded, total)
                } else {
                    ModelDownloadState.Downloading(downloaded, total)
                }
            }
            WorkInfo.State.SUCCEEDED -> ModelDownloadState.Ready
            WorkInfo.State.FAILED -> ModelDownloadState.Failed(
                info.outputData.getString(KEY_ERROR) ?: "The model download failed. Try again.",
            )
            WorkInfo.State.CANCELLED -> ModelDownloadState.Cancelled
        }
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "letsstudy-on-device-model"
        internal const val KEY_ALLOW_METERED = "allow_metered"
        internal const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        internal const val KEY_TOTAL_BYTES = "total_bytes"
        internal const val KEY_STAGE = "stage"
        internal const val KEY_ERROR = "error"
    }
}
