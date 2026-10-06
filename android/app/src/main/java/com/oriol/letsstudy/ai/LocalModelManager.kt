package com.oriol.letsstudy.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data object Available : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

interface StudyTextGenerator {
    val state: StateFlow<ModelState>
    suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String? = null): String
}

open class LocalModelException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ModelUnavailableException : LocalModelException("Download the study model before creating a study.")

class LocalModelManager(
    private val modelFile: File,
    private val engineFactory: LocalStudyEngineFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StudyTextGenerator {
    private val mutex = Mutex()
    private var engine: LocalStudyEngine? = null
    private val mutableState = MutableStateFlow<ModelState>(
        if (modelFile.isFile) ModelState.Available else ModelState.NotDownloaded,
    )
    override val state: StateFlow<ModelState> = mutableState.asStateFlow()

    suspend fun ensureLoaded() = withContext(ioDispatcher) {
        mutex.withLock { ensureLoadedLocked() }
    }

    suspend fun refreshAvailability() = withContext(ioDispatcher) {
        mutex.withLock {
            if (engine == null) {
                mutableState.value = if (modelFile.isFile) ModelState.Available else ModelState.NotDownloaded
            }
        }
    }

    override suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String?): String = withContext(ioDispatcher) {
        mutex.withLock {
            val readyEngine = ensureLoadedLocked()
            try {
                readyEngine.generate(prompt, maxOutputTokens, responseFormatSchema)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw LocalModelException("The on-device model couldn't complete this study step.", error)
            }
        }
    }

    suspend fun close() = withContext(ioDispatcher) {
        mutex.withLock {
            engine?.close()
            engine = null
            mutableState.value = if (modelFile.isFile) ModelState.Available else ModelState.NotDownloaded
        }
    }

    private suspend fun ensureLoadedLocked(): LocalStudyEngine {
        engine?.let { return it }
        if (!modelFile.isFile) {
            mutableState.value = ModelState.NotDownloaded
            throw ModelUnavailableException()
        }
        mutableState.value = ModelState.Loading
        try {
            engine = engineFactory.create(modelFile)
            mutableState.value = ModelState.Ready
            return checkNotNull(engine)
        } catch (cancelled: CancellationException) {
            mutableState.value = ModelState.Available
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = ModelState.Failed("This phone couldn't load the on-device study model.")
            throw LocalModelException("This phone couldn't load the on-device study model.", error)
        }
    }
}
