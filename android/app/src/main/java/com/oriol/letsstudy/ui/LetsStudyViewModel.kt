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
    val settings: LearnerSettings = LearnerSettings(),
    val account: StudyAccountState = StudyAccountState(),
    val leaderboard: StudyLeaderboardState = StudyLeaderboardState(),
    val workspaceBusy: Boolean = false,
    val workspaceStatus: String = "",
    val importedMaterial: ImportedMaterial? = null,
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
    private val dao = StudyDatabase.get(application).studyDao()
    private val accountSync = StudyAccountSync(dao)
    private val leaderboardRepository = StudyLeaderboardRepository()
    private val preferences = StudyPreferences(application)
    private val workspaceAi = FirebaseFastQuestionGenerator()
    private val workspace = StudyWorkspace(dao) { prompt, mode ->
        if (mode == "ONLINE") workspaceAi.generateWorkspace(prompt)
        else modelManager.generate(prompt, 4500)
    }
    private var workspaceJob: Job? = null

    init {
        _uiState.update { it.copy(settings = preferences.read()) }
        viewModelScope.launch { accountSync.state.collectLatest { account ->
            _uiState.update { state -> state.copy(account = account, leaderboard = if (account.email == null) StudyLeaderboardState() else state.leaderboard) }
        } }
        viewModelScope.launch { dao.observeMessages().collectLatest { values -> _uiState.update { it.copy(messages = values) } } }
        viewModelScope.launch { dao.observeActivity().collectLatest { values -> _uiState.update { it.copy(activity = values) } } }
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
                if (downloadState is ModelDownloadState.Ready) modelManager.refreshAvailability()
            }
        }
    }

    fun analyze(input: StudyInput, language: String, mode: String) {
        if (_uiState.value.isAnalyzing || _uiState.value.isGenerating || _uiState.value.workspaceBusy) return
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
        _uiState.update { it.copy(isGenerating = true, errorMessage = null, generationProgress = null) }
        generationJob = viewModelScope.launch {
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

    fun createStudy(setup: StudySetup) = runWorkspace("Creating your learning path…") {
        val session = workspace.createPath(setup)
        observeSession(session)
    }

    fun addPractice(format: PracticeFormat, module: LearningModule? = null, interview: Boolean = false, mode: String? = null) {
        val original = _uiState.value.activeSession ?: return
        val session = original.copy(generationMode = mode ?: original.generationMode)
        runWorkspace(if (interview) "Preparing your interview…" else "Building 15 ${format.label.lowercase()} questions…") {
            val countBefore = dao.getQuestions(session.id).size
            val selectedModule = if (interview) LearningModule("interview-${java.util.UUID.randomUUID()}", "Mock interview", "Practice explaining your experience") else module
            workspace.addRound(session, if (interview) PracticeFormat.INTERVIEW else format, selectedModule, if (interview) 5 else 15) { complete, total ->
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
        runWorkspace(if (selfRating != null || question.format == PracticeFormat.FILL_BLANK.name) "Saving your answer…" else "Reviewing your answer…") {
            workspace.answer(question, answer, selfRating)
        }
    }

    fun askTutor(message: String, questionContext: Boolean) {
        val current = _uiState.value
        val session = current.activeSession ?: return
        val question = if (questionContext) current.selectedQuestion else null
        val history = current.messages.filter { it.sessionId == session.id && it.questionId == question?.id.orEmpty() }
        runWorkspace("Your tutor is thinking…") { workspace.tutor(session.copy(generationMode = "ONLINE"), question, message.trim(), history) }
    }

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
        val safe = settings.copy(name = settings.name.take(80), reminderHour = settings.reminderHour.coerceIn(0, 23), reminderMinute = settings.reminderMinute.coerceIn(0, 59))
        preferences.save(safe)
        val before = _uiState.value.settings
        if (before.dailyReminder != safe.dailyReminder || before.weeklySummary != safe.weeklySummary || before.reminderHour != safe.reminderHour || before.reminderMinute != safe.reminderMinute) StudyReminders.schedule(getApplication(), safe)
        _uiState.update { it.copy(settings = safe) }
    }

    fun createAccount(displayName: String, username: String, email: String, password: String) =
        accountSync.createAccount(displayName, username, email, password)

    fun completeAccountProfile(displayName: String, username: String) = accountSync.completeProfile(displayName, username)

    fun updateAccountProfile(displayName: String, username: String) = accountSync.updateProfile(displayName, username)

    fun signIn(email: String, password: String) = accountSync.signIn(email, password)

    fun sendPasswordReset(email: String) = accountSync.sendPasswordReset(email)

    fun synchronizeAccount() = accountSync.syncNow()

    fun signOut() = accountSync.signOut()

    fun deleteAccount(password: String) = accountSync.deleteAccount(password)

    fun refreshLeaderboard() {
        val account = _uiState.value.account
        if (account.email == null || !account.profileComplete) {
            _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = false, error = "Sign in and complete your learner profile to view the community board.")) }
            return
        }
        _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            try {
                leaderboardRepository.publishCurrentScore(currentWeeklyPoints())
                _uiState.update { it.copy(leaderboard = leaderboardRepository.load()) }
            } catch (error: Exception) {
                _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = false, error = leaderboardMessage(error))) }
            }
        }
    }

    fun enableLeaderboard(nickname: String) {
        if (_uiState.value.account.email == null || !_uiState.value.account.profileComplete) {
            _uiState.update { it.copy(leaderboard = it.leaderboard.copy(error = "Sign in and complete your learner profile before joining.")) }
            return
        }
        _uiState.update { it.copy(leaderboard = it.leaderboard.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            try {
                leaderboardRepository.optIn(nickname, currentWeeklyPoints())
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

    private fun leaderboardMessage(error: Exception): String = when (error) {
        is com.google.firebase.firestore.FirebaseFirestoreException -> if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "Firebase denied the leaderboard request. Publish the updated Firestore rules from the project README."
        } else "The community board couldn't load. Your private studies are safe. Try again when you're online."
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

    fun cancelGeneration() { generationJob?.cancel(); workspaceJob?.cancel() }

    private fun runWorkspace(status: String, action: suspend () -> Unit) {
        if (_uiState.value.workspaceBusy || _uiState.value.isAnalyzing || _uiState.value.isGenerating) return
        _uiState.update { it.copy(workspaceBusy = true, workspaceStatus = status, errorMessage = null) }
        workspaceJob = viewModelScope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = if (error is IllegalArgumentException || error is IllegalStateException) error.message ?: friendlyMessage(error) else friendlyMessage(error)
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
