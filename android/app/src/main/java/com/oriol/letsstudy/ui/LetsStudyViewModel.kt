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
import com.oriol.letsstudy.ai.AndroidChatGptCredentialStore
import com.oriol.letsstudy.ai.AndroidLoopbackOAuthCallbackServer
import com.oriol.letsstudy.ai.AndroidOAuthBrowserLauncher
import com.oriol.letsstudy.ai.ChatGptOAuthException
import com.oriol.letsstudy.ai.ChatGptOAuthFailure
import com.oriol.letsstudy.ai.ChatGptOAuthStage
import com.oriol.letsstudy.ai.ChatGptStudyAiProvider
import com.oriol.letsstudy.ai.DefaultOpenAiResponsesClient
import com.oriol.letsstudy.ai.OpenAiChatGptOAuthClient
import com.oriol.letsstudy.ai.StudyAiErrorKind
import com.oriol.letsstudy.ai.StudyAiException
import com.oriol.letsstudy.ai.StudyAiProviderId
import com.oriol.letsstudy.ai.StudyAiRouter
import com.oriol.letsstudy.ai.FirebaseStudyAiProvider
import com.oriol.letsstudy.ai.OnDeviceStudyAiProvider
import com.oriol.letsstudy.data.StudyDatabase
import com.oriol.letsstudy.data.JobOfferReader
import com.oriol.letsstudy.data.StudyQuestionEntity
import com.oriol.letsstudy.data.StudyRepository
import com.oriol.letsstudy.data.StudySessionEntity
import com.oriol.letsstudy.data.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.File

data class LetsStudyUiState(
    val sessions: List<StudySessionEntity> = emptyList(),
    val allQuestions: List<StudyQuestionEntity> = emptyList(),
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
    val messages: List<TutorMessageEntity> = emptyList(),
    val activity: List<StudyActivityEntity> = emptyList(),
    val placementAttempts: List<StudyPlacementAttemptEntity> = emptyList(),
    val settings: LearnerSettings = LearnerSettings(),
    val account: StudyAccountState = StudyAccountState(),
    val leaderboard: StudyLeaderboardState = StudyLeaderboardState(),
    val chatGptConnected: Boolean = false,
    val chatGptConnectionBusy: Boolean = false,
    val chatGptConnectionMessage: String? = null,
    val pendingLocalFallback: Boolean = false,
    val chatGptUsageLimitReached: Boolean = false,
    val studyAiNotice: String? = null,
    val workspaceBusy: Boolean = false,
    val workspaceStatus: String = "",
    val importedMaterial: ImportedMaterial? = null,
)

class LetsStudyViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = LocalModelManager(
        modelFile = ModelCatalog.modelFile(application),
        engineFactory = LiteRtLocalStudyEngineFactory(File(application.cacheDir, "litertlm-cache")),
    )
    private val firebaseAi = FirebaseFastQuestionGenerator()
    private val chatGptCredentials = AndroidChatGptCredentialStore(application)
    private val chatGptOAuth = OpenAiChatGptOAuthClient(
        callbackServerFactory = { AndroidLoopbackOAuthCallbackServer() },
        browserLauncher = AndroidOAuthBrowserLauncher(application),
        hostIdProvider = chatGptCredentials::getOrCreateHostId,
    )
    private val chatGptProvider = ChatGptStudyAiProvider(
        credentials = chatGptCredentials,
        oauthClient = chatGptOAuth,
        client = DefaultOpenAiResponsesClient(),
    )
    private val aiRouter = StudyAiRouter(
        mapOf(
            StudyAiProviderId.GEMINI to FirebaseStudyAiProvider(firebaseAi),
            StudyAiProviderId.CHATGPT to chatGptProvider,
            StudyAiProviderId.ON_DEVICE to OnDeviceStudyAiProvider(modelManager),
        ),
        onLocalFallback = { task ->
            _uiState.update { it.copy(studyAiNotice = "ChatGPT reported a usage limit · this ${task.displayName()} used the on-device model.") }
        },
    )
    private val repository = StudyRepository(
        StudyDatabase.get(application).studyDao(),
        modelManager,
        JobOfferReader(),
        firebaseAi,
        aiRouter,
        { _uiState.value.settings.localFallbackOnChatGptLimit },
    )
    private val modelDownloadRepository = ModelDownloadRepository(application)
    private val _uiState = MutableStateFlow(LetsStudyUiState())
    val uiState = _uiState.asStateFlow()
    private var questionsJob: Job? = null
    private var generationJob: Job? = null
    private val choiceMutex = Mutex()
    private val dao = StudyDatabase.get(application).studyDao()
    private val accountSync = StudyAccountSync(dao)
    private val leaderboardRepository = StudyLeaderboardRepository()
    private val preferences = StudyPreferences(application)
    private val profilePhotoStore = ProfilePhotoStore(application)
    private var pendingGeneration: PendingStudyGeneration? = null
    private val workspace = StudyWorkspace(
        dao = dao,
        router = aiRouter,
        localFallbackOnChatGptLimit = { _uiState.value.settings.localFallbackOnChatGptLimit },
        generate = { prompt, mode ->
            if (mode == "ONLINE") firebaseAi.generateWorkspace(prompt) else modelManager.generate(prompt, 4500)
        },
    )
    private var workspaceJob: Job? = null

    init {
        _uiState.update { it.copy(settings = preferences.read(), chatGptConnected = chatGptCredentials.read() != null) }
        viewModelScope.launch { accountSync.state.collectLatest { account ->
            _uiState.update { state -> state.copy(account = account, leaderboard = if (account.email == null) StudyLeaderboardState() else state.leaderboard) }
        } }
        viewModelScope.launch { dao.observeMessages().collectLatest { values -> _uiState.update { it.copy(messages = values) } } }
        viewModelScope.launch { dao.observeActivity().collectLatest { values -> _uiState.update { it.copy(activity = values) } } }
        viewModelScope.launch { dao.observePlacementAttempts().collectLatest { values -> _uiState.update { it.copy(placementAttempts = values) } } }
        viewModelScope.launch {
            repository.sessions.collectLatest { sessions -> _uiState.update { it.copy(sessions = sessions) } }
        }
        viewModelScope.launch {
            repository.allQuestions.collectLatest { questions -> _uiState.update { it.copy(allQuestions = questions) } }
        }
        viewModelScope.launch {
            modelManager.state.collectLatest { modelState -> _uiState.update { it.copy(modelState = modelState) } }
        }
        viewModelScope.launch {
            modelDownloadRepository.observeState().collectLatest { downloadState ->
                _uiState.update { it.copy(modelDownloadState = downloadState) }
                if (downloadState is ModelDownloadState.Ready) {
                    modelManager.refreshAvailability()
                    if (_uiState.value.pendingLocalFallback) resumePendingGeneration()
                }
            }
        }
    }

    fun analyze(input: StudyInput, language: String, mode: String) {
        if (_uiState.value.isAnalyzing || _uiState.value.isGenerating || _uiState.value.workspaceBusy) return
        val pending = PendingStudyGeneration.AnalyzeJob(input, language, mode)
        setPendingGeneration(pending)
        _uiState.update { it.copy(isAnalyzing = true, errorMessage = null, generationProgress = null) }
        generationJob = viewModelScope.launch {
            try {
                val result = repository.analyze(input, language, mode) { progress ->
                    _uiState.update { it.copy(generationProgress = progress) }
                }
                if (!result.sourceReadable || result.questions.size != 15) {
                    throw StudyGenerationException("SOURCE_UNAVAILABLE", "I couldn't read a job listing from that source. Paste the full job description and try again.")
                }
                val session = repository.createSession(result, input, language, mode)
                observeSession(session)
                clearPendingGeneration(pending)
            } catch (error: CancellationException) {
                clearPendingGeneration(pending)
                throw error
            } catch (error: Exception) {
                keepPendingOnlyForLocalFallback(pending, error)
                _uiState.update { it.copy(errorMessage = friendlyMessage(error)) }
            } finally {
                _uiState.update { it.copy(isAnalyzing = false, generationProgress = null) }
            }
        }
    }

    fun openSession(id: String) {
        viewModelScope.launch {
            val session = repository.getSession(id)
            if (session != null) {
                val openedAt = System.currentTimeMillis()
                dao.touchSession(id, openedAt)
                observeSession(session.copy(lastOpenedAt = openedAt))
            }
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
                generationJob?.cancelAndJoin()
                workspaceJob?.cancelAndJoin()
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
        if (_uiState.value.isGenerating || _uiState.value.isAnalyzing || _uiState.value.workspaceBusy) return
        val pending = PendingStudyGeneration.GenerateMoreJobQuestions(session.id, mode)
        setPendingGeneration(pending)
        _uiState.update { it.copy(isGenerating = true, errorMessage = null, generationProgress = null) }
        generationJob = viewModelScope.launch {
            try {
                repository.addMoreQuestions(session, _uiState.value.questions, mode) { progress ->
                    _uiState.update { it.copy(generationProgress = progress) }
                }
                val updatedSession = repository.getSession(session.id)
                if (updatedSession != null) _uiState.update { it.copy(activeSession = updatedSession) }
                clearPendingGeneration(pending)
            } catch (error: CancellationException) {
                clearPendingGeneration(pending)
                throw error
            } catch (error: Exception) {
                keepPendingOnlyForLocalFallback(pending, error)
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
        val pending = PendingStudyGeneration.EvaluateAnswer(question.id, answer.trim(), null, workspaceAnswer = false)
        setPendingGeneration(pending)
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, generationProgress = null) }
            try {
                repository.submitAnswer(session, question, answer.trim()) { progress ->
                    _uiState.update { it.copy(generationProgress = progress) }
                }
                clearPendingGeneration(pending)
            } catch (error: CancellationException) {
                clearPendingGeneration(pending)
                throw error
            } catch (error: Exception) {
                keepPendingOnlyForLocalFallback(pending, error)
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
        val pending = PendingStudyGeneration.GenerateLesson(question.id)
        setPendingGeneration(pending)

        _uiState.update {
            it.copy(
                conceptLessonLoadingQuestionIds = it.conceptLessonLoadingQuestionIds + question.id,
                conceptLessonErrorCodes = it.conceptLessonErrorCodes - question.id,
            )
        }
        viewModelScope.launch {
            try {
                repository.getOrGenerateConceptLesson(session, question)
                clearPendingGeneration(pending)
            } catch (error: CancellationException) {
                clearPendingGeneration(pending)
                throw error
            } catch (error: Exception) {
                keepPendingOnlyForLocalFallback(pending, error)
                _uiState.update { current ->
                    current.copy(
                        conceptLessonErrorCodes = current.conceptLessonErrorCodes + (question.id to conceptLessonErrorCode(error)),
                        errorMessage = if (hasChatGptUsageLimit(error) || requiresLocalModel(error)) friendlyMessage(error) else current.errorMessage,
                    )
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

    fun createStudy(setup: StudySetup) = runWorkspace("Creating your learning path…", PendingStudyGeneration.CreatePath(setup)) {
        val session = workspace.createPath(setup)
        observeSession(session)
    }

    fun addPractice(format: PracticeFormat, module: LearningModule? = null, interview: Boolean = false, mode: String? = null) {
        val original = _uiState.value.activeSession ?: return
        val session = original.copy(generationMode = mode ?: original.generationMode)
        val selectedFormat = if (interview) PracticeFormat.INTERVIEW else format
        val count = if (interview) 5 else 15
        val pending = PendingStudyGeneration.AddRound(session.id, selectedFormat, module?.id, count, session.generationMode)
        runWorkspace(if (interview) "Preparing your interview…" else "Building 15 ${format.label.lowercase()} questions…", pending) {
            val countBefore = dao.getQuestions(session.id).size
            val selectedModule = if (interview) LearningModule("interview-${java.util.UUID.randomUUID()}", "Mock interview", "Practice explaining your experience") else module
            workspace.addRound(session, selectedFormat, selectedModule, count) { complete, total ->
                _uiState.update { it.copy(workspaceStatus = "Building questions · $complete/$total batches") }
            }
            val added = dao.getQuestions(session.id).drop(countBefore)
            if (_uiState.value.activeSession?.id == session.id && added.isNotEmpty()) {
                val refreshedQuestions = dao.getQuestions(session.id)
                val refreshedSession = dao.getSession(session.id) ?: session
                _uiState.update { it.copy(activeSession = refreshedSession, questions = refreshedQuestions) }
                selectQuestion(added.first(), added)
            }
        }
    }

    fun answerPractice(answer: String, selfRating: Int? = null) {
        val question = _uiState.value.selectedQuestion ?: return
        val needsAi = selfRating == null && question.format != PracticeFormat.FILL_BLANK.name
        val pending = if (needsAi) PendingStudyGeneration.EvaluateAnswer(question.id, answer, selfRating) else null
        runWorkspace(if (needsAi) "Reviewing your answer…" else "Saving your answer…", pending) {
            workspace.answer(question, answer, selfRating)
        }
    }

    fun askTutor(message: String, questionContext: Boolean) {
        val current = _uiState.value
        val session = current.activeSession ?: return
        val question = if (questionContext) current.selectedQuestion else null
        val history = current.messages.filter { it.sessionId == session.id && it.questionId == question?.id.orEmpty() }
        val mode = current.settings.mode
        val pending = PendingStudyGeneration.TutorMessage(session.id, question?.id, message.trim())
        runWorkspace("Your tutor is thinking…", pending) {
            workspace.tutor(session.copy(generationMode = mode), question, message.trim(), history)
        }
    }

    fun connectChatGpt() {
        if (_uiState.value.chatGptConnectionBusy) return
        _uiState.update { it.copy(chatGptConnectionBusy = true, chatGptConnectionMessage = null) }
        viewModelScope.launch {
            try {
                val connected = chatGptOAuth.connect(chatGptCredentials.read())
                chatGptCredentials.write(connected)
                chatGptProvider.invalidateModelCatalog()
                _uiState.update { it.copy(chatGptConnected = true, chatGptConnectionMessage = "ChatGPT is connected on this device.") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ChatGptOAuthException) {
                val host = when (error.stage) {
                    ChatGptOAuthStage.DISCOVERY,
                    ChatGptOAuthStage.TOKEN_EXCHANGE,
                    ChatGptOAuthStage.IDENTITY_VERIFICATION,
                    -> "auth.openai.com"
                    else -> "none"
                }
                Log.w(
                    "LetsStudyChatGPT",
                    "Sign-in failed (stage=${error.stage?.name ?: "UNKNOWN"}, failure=${error.failure.name}, host=$host, transport=${error.cause?.javaClass?.simpleName ?: "none"}).",
                )
                val message = when (error.failure) {
                    ChatGptOAuthFailure.CONSENT_CANCELLED -> "ChatGPT sign-in was cancelled. You can connect whenever you are ready."
                    ChatGptOAuthFailure.NOT_ELIGIBLE -> "This ChatGPT account or plan cannot connect to Let’sStudy yet."
                    ChatGptOAuthFailure.NETWORK -> when (error.stage) {
                        ChatGptOAuthStage.TOKEN_EXCHANGE,
                        ChatGptOAuthStage.IDENTITY_VERIFICATION,
                        -> "The browser returned to Let’sStudy, but secure sign-in could not finish. Check your connection and try again."
                        else -> "Couldn’t reach ChatGPT. Check your connection and try again."
                    }
                    ChatGptOAuthFailure.AUTH_REQUIRED -> "The ChatGPT authorization expired. Please connect again."
                    ChatGptOAuthFailure.TEMPORARY_SERVICE -> "ChatGPT is temporarily unavailable. Try again shortly."
                    ChatGptOAuthFailure.REGISTRATION_FAILED,
                    ChatGptOAuthFailure.INVALID_CALLBACK,
                    ChatGptOAuthFailure.INVALID_RESPONSE,
                    -> "ChatGPT couldn’t complete the secure connection. Please try again."
                }
                _uiState.update { it.copy(chatGptConnectionMessage = message) }
            } catch (_: Exception) {
                _uiState.update { it.copy(chatGptConnectionMessage = "Couldn’t connect to ChatGPT. Please try again.") }
            } finally {
                _uiState.update { it.copy(chatGptConnectionBusy = false, chatGptConnected = chatGptCredentials.read() != null) }
            }
        }
    }

    fun disconnectChatGpt() {
        if (_uiState.value.chatGptConnectionBusy) return
        _uiState.update { it.copy(chatGptConnectionBusy = true, chatGptConnectionMessage = null) }
        viewModelScope.launch {
            try {
                chatGptCredentials.read()?.let { runCatching { chatGptOAuth.revoke(it) } }
                chatGptCredentials.clearCredentials()
                chatGptProvider.invalidateModelCatalog()
                val current = _uiState.value.settings
                if (current.mode == "CHATGPT") saveSettings(current.copy(mode = "ONLINE"))
                _uiState.update { it.copy(chatGptConnected = false, chatGptConnectionMessage = "ChatGPT was disconnected from this device.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(chatGptConnectionMessage = "Couldn’t remove the ChatGPT connection. Please try again.") }
            } finally {
                _uiState.update { it.copy(chatGptConnectionBusy = false, chatGptConnected = chatGptCredentials.read() != null) }
            }
        }
    }

    fun clearChatGptConnectionMessage() = _uiState.update { it.copy(chatGptConnectionMessage = null) }

    fun clearStudyAiNotice() = _uiState.update { it.copy(studyAiNotice = null) }

    fun showError(message: String) = _uiState.update { it.copy(errorMessage = message) }

    fun importMaterial(uri: android.net.Uri) = runWorkspace("Reading your document…") {
        val imported = StudyMaterialReader(getApplication()).read(uri)
        _uiState.update { it.copy(importedMaterial = imported) }
    }

    fun clearImportedMaterial() = _uiState.update { it.copy(importedMaterial = null) }

    fun openSavedQuestion(question: StudyQuestionEntity) {
        viewModelScope.launch {
            val session = dao.getSession(question.sessionId) ?: return@launch
            val questions = dao.getQuestions(session.id)
            val openedAt = System.currentTimeMillis()
            dao.touchSession(session.id, openedAt)
            observeSession(session.copy(lastOpenedAt = openedAt))
            _uiState.update { it.copy(questions = questions) }
            questions.firstOrNull { it.id == question.id }?.let { selectQuestion(it, questions) }
        }
    }

    fun openDueQuestion(question: StudyQuestionEntity) {
        viewModelScope.launch {
            val session = dao.getSession(question.sessionId) ?: return@launch
            val questions = dao.getQuestions(session.id)
            val due = questions.filter { it.isDueForReview() }.sortedBy { it.nextReviewAt }
            if (due.isEmpty()) return@launch
            val openedAt = System.currentTimeMillis()
            dao.touchSession(session.id, openedAt)
            observeSession(session.copy(lastOpenedAt = openedAt))
            _uiState.update { it.copy(questions = questions, practiceQuestionIds = due.map { item -> item.id }, selectedQuestion = due.firstOrNull { it.id == question.id } ?: due.first(), errorMessage = null) }
        }
    }

    fun removeSavedMark(question: StudyQuestionEntity) {
        viewModelScope.launch {
            dao.removeSavedMark(question.id)
        }
    }

    fun saveSettings(settings: LearnerSettings) {
        val safe = settings.copy(name = settings.name.take(80), avatarId = LearnerAvatarIds.safe(settings.avatarId), reminderHour = settings.reminderHour.coerceIn(0, 23), reminderMinute = settings.reminderMinute.coerceIn(0, 59))
        if (_uiState.value.settings.photoPath.isNotBlank() && safe.photoPath.isBlank()) profilePhotoStore.delete()
        preferences.save(safe)
        val before = _uiState.value.settings
        if (before.dailyReminder != safe.dailyReminder || before.weeklySummary != safe.weeklySummary || before.reminderHour != safe.reminderHour || before.reminderMinute != safe.reminderMinute) StudyReminders.schedule(getApplication(), safe)
        _uiState.update { it.copy(settings = safe) }
        if (before.photoPath != safe.photoPath && _uiState.value.leaderboard.enabled && _uiState.value.leaderboard.photoShared) {
            syncLeaderboardPhoto(safe.photoPath)
        }
    }

    private fun syncLeaderboardPhoto(path: String) {
        _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            try {
                val thumbnail = path.takeIf(String::isNotBlank)?.let { photoPath ->
                    withContext(Dispatchers.IO) { profilePhotoStore.createLeaderboardThumbnail(photoPath) }
                }
                leaderboardRepository.setPhotoSharing(thumbnail != null, thumbnail, currentWeeklyPoints(), currentStudyStreak())
                _uiState.update { it.copy(leaderboard = leaderboardRepository.load()) }
            } catch (error: Exception) {
                _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = false, error = leaderboardMessage(error))) }
            }
        }
    }

    fun saveProfilePhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, workspaceBusy = true, workspaceStatus = "Preparing your profile photo…") }
            try {
                val path = withContext(Dispatchers.IO) { profilePhotoStore.save(uri) }
                saveSettings(_uiState.value.settings.copy(photoPath = path))
            } catch (error: Exception) {
                Log.w("LetsStudyProfile", "Photo import failed (${error.javaClass.simpleName}).")
                val message = when (error) {
                    is SecurityException -> "LetsStudy couldn’t access that image. Select it again and try once more."
                    is IOException -> "LetsStudy couldn’t read that image. Choose a PNG or JPEG and try again."
                    else -> "LetsStudy couldn’t prepare that image. Choose a PNG or JPEG and try again."
                }
                _uiState.update { it.copy(errorMessage = message) }
            } finally {
                _uiState.update { it.copy(workspaceBusy = false, workspaceStatus = "") }
            }
        }
    }

    fun savePlacementAttempt(result: LanguagePlacementResult, answers: List<Int>, bankVersion: Int, attemptType: String, activePathLevel: String = "") {
        if (answers.size != result.total || answers.any { it < 0 }) return
        viewModelScope.launch {
            val previous = dao.getAllPlacementAttempts()
                .filter { it.deletedAt == 0L && it.language == result.language }
                .maxByOrNull { it.completedAt }
            val previousLevel = LanguageProgression.baselineLevel(activePathLevel, previous?.progressLevel?.ifBlank { previous.startingLevel }).orEmpty()
            val progression = LanguageProgression.advance(previousLevel.takeIf(String::isNotBlank), result.startingLevel)
            dao.insertPlacementAttempt(
                StudyPlacementAttemptEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    language = result.language,
                    level = result.level,
                    estimatedRange = result.estimatedRange,
                    startingLevel = result.startingLevel,
                    correct = result.correct,
                    total = result.total,
                    nextFocus = result.nextFocus,
                    answersCsv = answers.joinToString(","),
                    completedAt = System.currentTimeMillis(),
                    attemptType = attemptType,
                    bankVersion = bankVersion,
                    previousLevel = previousLevel,
                    progressLevel = progression.level,
                ),
            )
        }
    }

    fun createAccount(displayName: String, username: String, email: String, password: String) =
        accountSync.createAccount(displayName, username, email, password)

    fun completeAccountProfile(displayName: String, username: String) = accountSync.completeProfile(displayName, username)

    fun updateAccountProfile(displayName: String, username: String) = accountSync.updateProfile(displayName, username)

    fun updateAccountAvatar(avatarId: String) = accountSync.updateAvatar(avatarId)

    fun signIn(email: String, password: String) = accountSync.signIn(email, password)

    fun sendPasswordReset(email: String) = accountSync.sendPasswordReset(email)

    fun synchronizeAccount() = accountSync.syncNow()

    fun signOut() = accountSync.signOut()

    fun deleteAccount(password: String) = accountSync.deleteAccount(password)

    fun refreshLeaderboard() {
        val account = _uiState.value.account
        if (account.email == null || !account.profileComplete) {
            _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = false, error = null)) }
            return
        }
        _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            try {
                leaderboardRepository.publishCurrentScore(currentWeeklyPoints(), currentStudyStreak())
                _uiState.update { it.copy(leaderboard = leaderboardRepository.load()) }
            } catch (error: Exception) {
                _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = false, error = leaderboardMessage(error))) }
            }
        }
    }

    fun enableLeaderboard(nickname: String, sharePhoto: Boolean) {
        if (_uiState.value.account.email == null || !_uiState.value.account.profileComplete) {
            _uiState.update { it.copy(leaderboard = it.leaderboard.copy(error = "Sign in and complete your learner profile before joining.")) }
            return
        }
        val photoPath = _uiState.value.settings.photoPath
        if (sharePhoto && photoPath.isBlank()) {
            _uiState.update { it.copy(leaderboard = it.leaderboard.copy(error = "Choose a profile photo in Settings before sharing it.")) }
            return
        }
        _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            try {
                val thumbnail = if (sharePhoto) withContext(Dispatchers.IO) {
                    profilePhotoStore.createLeaderboardThumbnail(photoPath)
                } else null
                leaderboardRepository.optIn(nickname, currentWeeklyPoints(), thumbnail, currentStudyStreak())
                _uiState.update { it.copy(leaderboard = leaderboardRepository.load()) }
            } catch (error: Exception) {
                _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = false, error = leaderboardMessage(error))) }
            }
        }
    }

    fun setLeaderboardPhotoSharing(enabled: Boolean) {
        val photoPath = _uiState.value.settings.photoPath
        if (enabled && photoPath.isBlank()) {
            _uiState.update { it.copy(leaderboard = it.leaderboard.copy(error = "Choose a profile photo in Settings before sharing it.")) }
            return
        }
        _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            try {
                val thumbnail = if (enabled) withContext(Dispatchers.IO) {
                    profilePhotoStore.createLeaderboardThumbnail(photoPath)
                } else null
                leaderboardRepository.setPhotoSharing(enabled, thumbnail, currentWeeklyPoints(), currentStudyStreak())
                _uiState.update { it.copy(leaderboard = leaderboardRepository.load()) }
            } catch (error: Exception) {
                _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = false, error = leaderboardMessage(error))) }
            }
        }
    }

    fun disableLeaderboard() {
        _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            try {
                leaderboardRepository.optOut()
                _uiState.update { it.copy(leaderboard = leaderboardRepository.load()) }
            } catch (error: Exception) {
                _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = false, error = leaderboardMessage(error))) }
            }
        }
    }

    private suspend fun currentWeeklyPoints(): Int {
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val answered = dao.getAllQuestions().filter { it.answeredAt >= weekStart }.map { it.id }.toSet().size
        val minutes = (dao.getAllActivity().filter { it.startedAt >= weekStart }.sumOf { it.durationSeconds }.coerceAtMost(31_500L) / 60).toInt()
        return (answered * 10 + (minutes / 5)).coerceIn(0, StudyLeaderboardRepository.MAX_WEEKLY_POINTS)
    }

    private suspend fun currentStudyStreak(): Int = studyStreak(dao.getAllQuestions(), dao.getAllActivity())

    private fun leaderboardMessage(error: Exception): String = when (error) {
        is com.google.firebase.firestore.FirebaseFirestoreException -> when {
            error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "We couldn't open the community board. Your private studies are safe."
            error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION && error.message.orEmpty().contains("index", ignoreCase = true) ->
                "The community board isn't ready yet. Your private studies are safe."
            else -> "The community board couldn't load. Your private studies are safe. Try again when you're online."
        }
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "Check your account and try again."
        else -> "The community board couldn't load. Your private studies are safe. Try again when you're online."
    }

    fun clearAllStudies() {
        viewModelScope.launch {
            generationJob?.cancelAndJoin()
            workspaceJob?.cancelAndJoin()
            if (_uiState.value.account.email != null) {
                try {
                    accountSync.synchronizeNow()
                } catch (error: Exception) {
                    _uiState.update { it.copy(errorMessage = "Connect to the internet and wait for sync to finish before deleting cloud study data.") }
                    return@launch
                }
            }
            dao.clearStudyData()
            closeSession()
        }
    }

    override fun onCleared() {
        accountSync.close()
        super.onCleared()
    }

    fun recordStudy(sessionId: String, startedAt: Long, durationSeconds: Long) {
        if (durationSeconds !in 1..86400) return
        viewModelScope.launch {
            if (dao.getSession(sessionId) != null) dao.insertActivity(StudyActivityEntity(java.util.UUID.randomUUID().toString(), sessionId, startedAt, durationSeconds))
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        workspaceJob?.cancel()
        pendingGeneration = null
        _uiState.update { it.copy(pendingLocalFallback = false) }
    }

    fun resumePendingGeneration() {
        val pending = pendingGeneration ?: return
        if (_uiState.value.workspaceBusy || _uiState.value.isAnalyzing || _uiState.value.isGenerating) return
        _uiState.update { it.copy(pendingLocalFallback = false, chatGptUsageLimitReached = false, errorMessage = null) }
        when (pending) {
            is PendingStudyGeneration.AnalyzeJob -> analyze(pending.input, pending.language, pending.mode)
            is PendingStudyGeneration.CreatePath -> createStudy(pending.setup)
            is PendingStudyGeneration.GenerateMoreJobQuestions -> {
                if (_uiState.value.activeSession?.id == pending.sessionId) generateMore(pending.mode)
                else viewModelScope.launch {
                    dao.getSession(pending.sessionId)?.let { saved ->
                        observeSession(saved)
                        generateMore(pending.mode)
                    } ?: clearPendingGeneration(pending)
                }
            }
            is PendingStudyGeneration.AddRound -> viewModelScope.launch {
                val saved = dao.getSession(pending.sessionId)
                if (saved == null) {
                    clearPendingGeneration(pending)
                    return@launch
                }
                observeSession(saved)
                val module = pending.moduleId?.let { id -> saved.modules().firstOrNull { it.id == id } }
                addPractice(pending.format, module, pending.format == PracticeFormat.INTERVIEW, pending.mode)
            }
            is PendingStudyGeneration.EvaluateAnswer -> viewModelScope.launch {
                val question = dao.getQuestion(pending.questionId)
                val session = question?.let { dao.getSession(it.sessionId) }
                if (question == null || session == null) {
                    clearPendingGeneration(pending)
                    return@launch
                }
                observeSession(session)
                _uiState.update { it.copy(selectedQuestion = question, activeSession = session) }
                if (pending.workspaceAnswer) answerPractice(pending.answer, pending.selfRating)
                else submitAnswer(pending.answer)
            }
            is PendingStudyGeneration.GenerateLesson -> viewModelScope.launch {
                val question = dao.getQuestion(pending.questionId)
                val session = question?.let { dao.getSession(it.sessionId) }
                if (question == null || session == null) {
                    clearPendingGeneration(pending)
                    return@launch
                }
                observeSession(session)
                _uiState.update { it.copy(selectedQuestion = question, activeSession = session) }
                requestConceptLesson()
            }
            is PendingStudyGeneration.TutorMessage -> viewModelScope.launch {
                val session = dao.getSession(pending.sessionId)
                val question = pending.questionId?.let { dao.getQuestion(it) }
                if (session == null) {
                    clearPendingGeneration(pending)
                    return@launch
                }
                observeSession(session)
                _uiState.update { it.copy(selectedQuestion = question, activeSession = session) }
                askTutor(pending.text, question != null)
            }
        }
    }

    private fun runWorkspace(status: String, pending: PendingStudyGeneration? = null, action: suspend () -> Unit) {
        if (_uiState.value.workspaceBusy || _uiState.value.isAnalyzing || _uiState.value.isGenerating) return
        if (_uiState.value.pendingLocalFallback && pending != pendingGeneration) return
        if (pending != null) setPendingGeneration(pending)
        _uiState.update { it.copy(workspaceBusy = true, workspaceStatus = status, errorMessage = null) }
        workspaceJob = viewModelScope.launch {
            try {
                action()
                pending?.let(::clearPendingGeneration)
            }
            catch (error: CancellationException) {
                pending?.let(::clearPendingGeneration)
                throw error
            }
            catch (error: Exception) {
                val message = if (error is IllegalArgumentException || error is IllegalStateException) error.message ?: friendlyMessage(error) else friendlyMessage(error)
                pending?.let { keepPendingOnlyForLocalFallback(it, error) }
                _uiState.update { it.copy(errorMessage = message) }
            } finally { _uiState.update { it.copy(workspaceBusy = false, workspaceStatus = "") } }
        }
    }

    fun downloadModel() {
        _uiState.update { it.copy(errorMessage = null) }
        modelDownloadRepository.enqueueDownload(allowMetered = false)
    }

    fun downloadModelOnMobileData() {
        _uiState.update { it.copy(errorMessage = null) }
        modelDownloadRepository.enqueueDownload(allowMetered = true)
    }

    fun cancelModelDownload() = modelDownloadRepository.cancelDownload()

    private fun setPendingGeneration(pending: PendingStudyGeneration) {
        pendingGeneration = pending
        _uiState.update { it.copy(pendingLocalFallback = false, chatGptUsageLimitReached = false) }
    }

    private fun clearPendingGeneration(pending: PendingStudyGeneration) {
        if (pendingGeneration == pending) {
            pendingGeneration = null
            _uiState.update { it.copy(pendingLocalFallback = false, chatGptUsageLimitReached = false) }
        }
    }

    private fun keepPendingOnlyForLocalFallback(pending: PendingStudyGeneration, error: Throwable) {
        val localModelRequired = requiresLocalModel(error)
        val usageLimitReached = hasChatGptUsageLimit(error)
        if (localModelRequired || usageLimitReached) {
            pendingGeneration = pending
            _uiState.update { it.copy(pendingLocalFallback = localModelRequired, chatGptUsageLimitReached = usageLimitReached) }
        } else {
            clearPendingGeneration(pending)
        }
    }

    private fun hasChatGptUsageLimit(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is StudyAiException && (current.kind == StudyAiErrorKind.USAGE_LIMIT ||
                current.kind == StudyAiErrorKind.MODEL_NOT_AVAILABLE && current.fallbackReason == StudyAiErrorKind.USAGE_LIMIT)
            ) return true
            current = current.cause
        }
        return false
    }

    private fun requiresLocalModel(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is ModelUnavailableException ||
                current is StudyAiException && current.kind == StudyAiErrorKind.MODEL_NOT_AVAILABLE
            ) return true
            current = current.cause
        }
        return false
    }

    private fun friendlyMessage(error: Exception): String = when (error) {
        is StudyAiException -> when (error.kind) {
            StudyAiErrorKind.AUTH_REQUIRED -> "Connect ChatGPT in Settings before choosing it for study generation."
            StudyAiErrorKind.NOT_ELIGIBLE -> "This ChatGPT account or plan cannot be used by Let’sStudy. Choose Gemini or On-device in Settings."
            StudyAiErrorKind.USAGE_LIMIT -> "You’ve reached the current ChatGPT plan usage limit. Manage it in ChatGPT Settings → Usage, or turn on On-device fallback in Let’sStudy Settings."
            StudyAiErrorKind.USAGE_UNAVAILABLE -> "ChatGPT could not check plan usage right now. Try again later; this does not mean your allowance is exhausted."
            StudyAiErrorKind.MODEL_NOT_AVAILABLE -> "The On-device model is needed to continue this study. Download it from Settings, then resume the same action."
            StudyAiErrorKind.UNSUPPORTED_CAPABILITY -> "ChatGPT can’t complete this type of study request. Choose Gemini or On-device in Settings."
            StudyAiErrorKind.NETWORK -> "Couldn’t reach the selected study service. Check your connection and try again."
            StudyAiErrorKind.TEMPORARY_SERVICE -> "The selected study service is temporarily unavailable. Try again shortly."
            StudyAiErrorKind.INVALID_RESPONSE -> "The study service returned an incomplete response. Your saved studies are safe; try again."
        }
        is StudyGenerationException -> {
            error.cause?.message?.let { Log.w("LetsStudyAI", it) }
            StudyGenerationCopy.forGenerationError(error.code, error.message)
        }
        is ModelUnavailableException -> "This study option needs a one-time download. Open Settings to get it ready."
        is InvalidStudyOutputException -> "We couldn't finish this study. Try again in a moment. Your saved studies are safe."
        else -> StudyGenerationCopy.onlineFailure(error)
    }

    private fun com.oriol.letsstudy.ai.StudyAiTask.displayName(): String = when (this) {
        com.oriol.letsstudy.ai.StudyAiTask.JOB_QUESTIONS -> "job questions"
        com.oriol.letsstudy.ai.StudyAiTask.STUDY_PATH -> "study path"
        com.oriol.letsstudy.ai.StudyAiTask.PRACTICE_ROUND -> "practice round"
        com.oriol.letsstudy.ai.StudyAiTask.ANSWER_FEEDBACK -> "answer feedback"
        com.oriol.letsstudy.ai.StudyAiTask.CONCEPT_LESSON -> "lesson"
        com.oriol.letsstudy.ai.StudyAiTask.TUTOR -> "tutor reply"
    }

    class Factory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LetsStudyViewModel::class.java)) return LetsStudyViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
