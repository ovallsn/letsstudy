package com.oriol.letsstudy.ui

import com.oriol.letsstudy.ai.ModelState
import com.oriol.letsstudy.ai.ModelDownloadState
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSetupStateTest {
    @Test
    fun mapsMissingQueuedDownloadingVerifyingAndReadyStates() {
        assertEquals(
            ModelSetupPresentation.NeedsDownload,
            modelSetupPresentation(ModelState.NotDownloaded, ModelDownloadState.NotDownloaded),
        )
        assertEquals(
            ModelSetupPresentation.Queued,
            modelSetupPresentation(ModelState.NotDownloaded, ModelDownloadState.Queued),
        )
        assertEquals(
            ModelSetupPresentation.Downloading(42),
            modelSetupPresentation(ModelState.NotDownloaded, ModelDownloadState.Downloading(42, 100)),
        )
        assertEquals(
            ModelSetupPresentation.Verifying,
            modelSetupPresentation(ModelState.NotDownloaded, ModelDownloadState.Verifying(100, 100)),
        )
        assertEquals(
            ModelSetupPresentation.Ready,
            modelSetupPresentation(ModelState.Available, ModelDownloadState.Ready),
        )
    }

    @Test
    fun mapsDownloadErrorsAndClampsInvalidProgress() {
        assertEquals(
            ModelSetupPresentation.Failed("Free up storage"),
            modelSetupPresentation(ModelState.NotDownloaded, ModelDownloadState.Failed("Free up storage")),
        )
        assertEquals(
            ModelSetupPresentation.Downloading(100),
            modelSetupPresentation(ModelState.NotDownloaded, ModelDownloadState.Downloading(120, 100)),
        )
        assertEquals(
            ModelSetupPresentation.Downloading(0),
            modelSetupPresentation(ModelState.NotDownloaded, ModelDownloadState.Downloading(0, 0)),
        )
    }

    @Test
    fun loadingAndModelCompatibilityFailuresTakePrecedence() {
        assertEquals(
            ModelSetupPresentation.Loading,
            modelSetupPresentation(ModelState.Loading, ModelDownloadState.NotDownloaded),
        )
        assertEquals(
            ModelSetupPresentation.Failed("Unsupported phone"),
            modelSetupPresentation(ModelState.Failed("Unsupported phone"), ModelDownloadState.Ready),
        )
    }
}
