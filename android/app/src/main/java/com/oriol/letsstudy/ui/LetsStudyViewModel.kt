package com.oriol.letsstudy.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oriol.letsstudy.ai.LiteRtLocalStudyEngineFactory
import com.oriol.letsstudy.ai.FirebaseFastQuestionGenerator
import com.oriol.letsstudy.ai.LocalModelManager
import com.oriol.letsstudy.ai.ModelCatalog
import com.oriol.letsstudy.ai.ModelDownloadRepository
import com.oriol.letsstudy.ai.ModelDownloadState
import com.oriol.letsstudy.ai.ModelState
import com.oriol.letsstudy.ai.ModelUnavailableException
import com.oriol.letsstudy.ai.InvalidStudyOutputException
import com.oriol.letsstudy.data.StudyDatabase
import com.oriol.letsstudy.data.JobOfferReader
import com.oriol.letsstudy.data.StudyQuestionEntity
import com.oriol.letsstudy.data.StudyRepository
import com.oriol.letsstudy.data.StudySessionEntity
import com.oriol.letsstudy.domain.ConceptLesson
import com.oriol.letsstudy.data.StudyGenerationException
import com.oriol.letsstudy.data.StudyGenerationProgress
import com.oriol.letsstudy.domain.StudyInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.File

data class LetsStudyUiState(
    val sessions: List<StudySessionEntity> = emptyList(),
    val activeSession: StudySessionEntity? = null,
    val questions: List<StudyQuestionEntity> = emptyList(),
    val selectedQuestion: StudyQuestionEntity? = null,
    val practiceQuestionIds: List<String> = emptyList(),
    val isAnalyzing: Boolean = false,
    val isGenerating: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val modelState: ModelState = ModelState.NotDownloaded,
    val modelDownloadState: ModelDownloadState = ModelDownloadState.NotDownloaded,
    val generationProgress: StudyGenerationProgress? = null,
    val conceptLessonLoadingQuestionIds: Set<String> = emptySet(),
    val conceptLessonErrorCodes: Map<String, String> = emptyMap(),
)

class LetsStudyViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = LocalModelManager(
        modelFile = ModelCatalog.modelFile(application),
        engineFactory = LiteRtLocalStudyEngineFactory(File(application.cacheDir, "litertlm-cache")),
    )
    private val repository = StudyRepository(
        StudyDatabase.get(application).studyDao(),
        modelManager,
        JobOfferReader(),
        FirebaseFastQuestionGenerator(),
    )
    private val modelDownloadRepository = ModelDownloadRepository(application)
    private val _uiState = MutableStateFlow(LetsStudyUiState())
    val uiState = _uiState.asStateFlow()
    private var questionsJob: Job? = null
    private var generationJob: Job? = null
    private val choiceMutex = Mutex()

    init {
        viewModelScope.launch {
            repository.sessions.collectLatest { sessions -> _uiState.update { it.copy(sessions = sessions) } }
        }
        viewModelScope.launch {
            modelManager.state.collectLatest { modelState -> _uiState.update { it.copy(modelState = modelState) } }
        }
        viewModelScope.launch {
            modelDownloadRepository.observeState().collectLatest { downloadState ->
                _uiState.update { it.copy(modelDownloadState = downloadState) }
                if (downloadState is ModelDownloadState.Ready) modelManager.refreshAvailability()
            }
        }
    }

    fun analyze(input: StudyInput, language: String, mode: String) {
        if (_uiState.value.isAnalyzing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, errorMessage = null, generationProgress = null) }
            try {
                val result = repository.analyze(input, language, mode) { progress ->
                    _uiState.update { it.copy(generationProgress = progress) }
                }
                if (!result.sourceReadable || result.questions.size != 15) {
                    throw StudyGenerationException("SOURCE_UNAVAILABLE", "I couldn't read a job listing from that source. Paste the full job description and try again.")
                }
                val session = repository.createSession(result, input, language, mode)
                observeSession(session)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = friendlyMessage(error)) }
            } finally {
                _uiState.update { it.copy(isAnalyzing = false, generationProgress = null) }
            }
        }
    }

    fun openSession(id: String) {
        viewModelScope.launch {
            val session = repository.getSession(id)
            if (session != null) observeSession(session)
            else _uiState.update { it.copy(errorMessage = "I couldn't find that saved study session.") }
        }
    }

    private fun observeSession(session: StudySessionEntity) {
        questionsJob?.cancel()
        _uiState.update { it.copy(activeSession = session, selectedQuestion = null, practiceQuestionIds = emptyList(), errorMessage = null) }
        questionsJob = viewModelScope.launch {
                repository.questions(session.id).collectLatest { questions ->
                    _uiState.update { state ->
                    state.copy(
                        activeSession = state.activeSession ?: session,
                        questions = questions,
                        selectedQuestion = state.selectedQuestion?.let { selected -> questions.firstOrNull { it.id == selected.id } },
                    )
                }
            }
        }
    }

    fun selectQuestion(question: StudyQuestionEntity, visibleQuestions: List<StudyQuestionEntity>) = _uiState.update {
        it.copy(selectedQuestion = question, practiceQuestionIds = visibleQuestions.map(StudyQuestionEntity::id), errorMessage = null)
    }

    fun moveQuestion(offset: Int) {
        _uiState.update { state ->
            val current = state.selectedQuestion ?: return@update state
            val queue = state.practiceQuestionIds.ifEmpty { state.questions.map(StudyQuestionEntity::id) }
            val index = queue.indexOf(current.id)
            val destination = index + offset
            if (index < 0 || destination !in queue.indices) state
            else state.questions.firstOrNull { it.id == queue[destination] }?.let { next ->
                state.copy(selectedQuestion = next, errorMessage = null)
            } ?: state
        }
    }

    fun closeQuestion() = _uiState.update { it.copy(selectedQuestion = null, practiceQuestionIds = emptyList(), errorMessage = null) }

    fun closeSession() {
        questionsJob?.cancel()
        _uiState.update { it.copy(activeSession = null, selectedQuestion = null, practiceQuestionIds = emptyList(), questions = emptyList(), errorMessage = null) }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            try {
                if (_uiState.value.activeSession?.id == id) generationJob?.cancelAndJoin()
                repository.deleteSession(id)
                if (_uiState.value.activeSession?.id == id) closeSession()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Couldn't delete this study session. Try again.") }
            }
        }
    }

    fun generateMore(mode: String) {
        val session = _uiState.value.activeSession ?: return
        if (_uiState.value.isGenerating || _uiState.value.isAnalyzing) return
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null, generationProgress = null) }
            try {
                repository.addMoreQuestions(session, _uiState.value.questions, mode) { progress ->
                    _uiState.update { it.copy(generationProgress = progress) }
                }
                val updatedSession = repository.getSession(session.id)
                if (updatedSession != null) _uiState.update { it.copy(activeSession = updatedSession) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = friendlyMessage(error)) }
            } finally {
                _uiState.update { it.copy(isGenerating = false, generationProgress = null) }
            }
        }
    }

    fun submitAnswer(answer: String) {
        val session = _uiState.value.activeSession ?: return
        val question = _uiState.value.selectedQuestion ?: return
        if (answer.isBlank() || _uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, generationProgress = null) }
            try {
                repository.submitAnswer(session, question, answer.trim()) { progress ->
                    _uiState.update { it.copy(generationProgress = progress) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = friendlyMessage(error)) }
            } finally {
                _uiState.update { it.copy(isSubmitting = false, generationProgress = null) }
            }
        }
    }

    fun selectChoice(choice: Int) {
        val question = _uiState.value.selectedQuestion ?: return
        viewModelScope.launch {
            try {
                choiceMutex.withLock { repository.selectChoice(question, choice) }
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = friendlyMessage(error)) }
            }
        }
    }

    fun resetChoice() {
        val question = _uiState.value.selectedQuestion ?: return
        viewModelScope.launch {
            try {
                choiceMutex.withLock { repository.resetChoice(question) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Couldn't reset this question. Try again.") }
            }
        }
    }

    fun requestConceptLesson() {
        val state = _uiState.value
        val session = state.activeSession ?: return
        val question = state.selectedQuestion ?: return
        if (question.conceptLessonJson.isNotBlank() || question.id in state.conceptLessonLoadingQuestionIds) return

        _uiState.update {
            it.copy(
                conceptLessonLoadingQuestionIds = it.conceptLessonLoadingQuestionIds + question.id,
                conceptLessonErrorCodes = it.conceptLessonErrorCodes - question.id,
            )
        }
        viewModelScope.launch {
            try {
                repository.getOrGenerateConceptLesson(session, question)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { current ->
                    current.copy(conceptLessonErrorCodes = current.conceptLessonErrorCodes + (question.id to conceptLessonErrorCode(error)))
                }
            } finally {
                _uiState.update { current ->
                    current.copy(conceptLessonLoadingQuestionIds = current.conceptLessonLoadingQuestionIds - question.id)
                }
            }
        }
    }

    private fun conceptLessonErrorCode(error: Exception): String {
        val text = error.message.orEmpty()
        val className = error.javaClass.simpleName
        return when {
            error is IOException || text.contains("network", ignoreCase = true) || text.contains("internet", ignoreCase = true) ||
                text.contains("unable to resolve host", ignoreCase = true) -> "NETWORK"
            className.contains("Quota", ignoreCase = true) || text.contains("quota", ignoreCase = true) || text.contains("rate limit", ignoreCase = true) -> "QUOTA"
            text.contains("App Check", ignoreCase = true) || text.contains("AppCheck", ignoreCase = true) -> "APP_CHECK"
            text.contains("too long", ignoreCase = true) || className.contains("Timeout", ignoreCase = true) -> "TIMEOUT"
            error is InvalidStudyOutputException -> "INVALID_RESPONSE"
            else -> "SERVICE"
        }
    }

    fun toggleReview(question: StudyQuestionEntity) {
        viewModelScope.launch {
            try {
                repository.toggleReview(question)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = friendlyMessage(error)) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun downloadModel() {
        _uiState.update { it.copy(errorMessage = null) }
        modelDownloadRepository.enqueueDownload(allowMetered = false)
    }

    fun downloadModelOnMobileData() {
        _uiState.update { it.copy(errorMessage = null) }
        modelDownloadRepository.enqueueDownload(allowMetered = true)
    }

    fun cancelModelDownload() = modelDownloadRepository.cancelDownload()

    private fun friendlyMessage(error: Exception): String = when (error) {
        is StudyGenerationException -> {
            error.cause?.message?.let { Log.w("LetsStudyAI", it) }
            error.message
        }
        is ModelUnavailableException -> "Download the on-device model before starting a study."
        is InvalidStudyOutputException -> error.message
        is IOException -> "There's no internet connection. Check your connection and try again."
        else -> when {
            error.javaClass.simpleName.contains("Quota", ignoreCase = true) || error.message.orEmpty().contains("quota", ignoreCase = true) ->
                "The free online study limit has been reached. Choose On-device mode for another round, or try online later."
            error.message.orEmpty().contains("App Check", ignoreCase = true) ->
                "Firebase App Check could not verify this build. Check the app registration and try again."
            else -> "The online study service could not finish this round. Try again or choose On-device mode. Your saved progress is safe."
        }
    }

    class Factory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LetsStudyViewModel::class.java)) return LetsStudyViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
