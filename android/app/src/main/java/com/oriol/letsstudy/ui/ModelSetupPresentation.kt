package com.oriol.letsstudy.ui

import com.oriol.letsstudy.ai.ModelState
import com.oriol.letsstudy.ai.ModelDownloadState

sealed interface ModelSetupPresentation {
    data object NeedsDownload : ModelSetupPresentation
    data object Queued : ModelSetupPresentation
    data class Downloading(val percent: Int) : ModelSetupPresentation
    data object Verifying : ModelSetupPresentation
    data object Ready : ModelSetupPresentation
    data class Failed(val message: String) : ModelSetupPresentation
    data object Loading : ModelSetupPresentation
}

fun modelSetupPresentation(modelState: ModelState, downloadState: ModelDownloadState): ModelSetupPresentation {
    if (modelState is ModelState.Failed) return ModelSetupPresentation.Failed(modelState.message)
    if (modelState is ModelState.Loading) return ModelSetupPresentation.Loading
    if (downloadState is ModelDownloadState.Ready || modelState is ModelState.Available || modelState is ModelState.Ready) {
        return ModelSetupPresentation.Ready
    }
    return when (downloadState) {
        ModelDownloadState.NotDownloaded, ModelDownloadState.Cancelled -> ModelSetupPresentation.NeedsDownload
        ModelDownloadState.Queued -> ModelSetupPresentation.Queued
        is ModelDownloadState.Downloading -> ModelSetupPresentation.Downloading(downloadState.percent())
        is ModelDownloadState.Verifying -> ModelSetupPresentation.Verifying
        ModelDownloadState.Ready -> ModelSetupPresentation.Ready
        is ModelDownloadState.Failed -> ModelSetupPresentation.Failed(downloadState.message)
    }
}

private fun ModelDownloadState.Downloading.percent(): Int {
    if (totalBytes <= 0L) return 0
    return ((downloadedBytes.coerceIn(0L, totalBytes) * 100) / totalBytes).toInt().coerceIn(0, 100)
}
