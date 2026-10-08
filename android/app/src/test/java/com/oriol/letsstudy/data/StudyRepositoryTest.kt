package com.oriol.letsstudy.data

import com.oriol.letsstudy.ai.LocalModelManager
import com.oriol.letsstudy.ai.FastQuestionGenerator
import com.oriol.letsstudy.ai.LocalStudyEngine
import com.oriol.letsstudy.ai.LocalStudyEngineFactory
import com.oriol.letsstudy.ai.ModelState
import com.oriol.letsstudy.ai.ModelUnavailableException
import com.oriol.letsstudy.ai.StudyTextGenerator
import com.google.gson.JsonParser
import com.oriol.letsstudy.domain.SourceKind
import com.oriol.letsstudy.domain.StudyInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StudyRepositoryTest {
    @Test
    fun createsOneFifteenQuestionSessionAcrossThreeLocalBatches() = runBlocking {
        val dao = FakeStudyDao()
        val generator = QueueStudyGenerator(
            initialOutput(0),
            questionOutput(5),
            questionOutput(10),
        )
        val repository = StudyRepository(dao, generator, FakeJobOfferReader())

        val result = repository.analyze(textInput(), "English")
        val session = repository.createSession(result, textInput(), "English")

        assertEquals(15, result.questions.size)
        assertEquals(3, generator.prompts.size)
        assertEquals(1, dao.savedSessions.size)
        assertEquals(15, dao.savedQuestions.size)
        assertEquals(session.id, dao.savedQuestions.first().sessionId)
    }

    @Test
    fun continuationAddsFifteenDistinctQuestionsWithoutRepeatingEarlierOnes() = runBlocking {
        val dao = FakeStudyDao()
        val generator = QueueStudyGenerator(
            initialOutput(0), questionOutput(5), questionOutput(10),
            questionOutput(15), questionOutput(20), questionOutput(25),
        )
        val repository = StudyRepository(dao, generator, FakeJobOfferReader())
        val input = textInput()
        val initial = repository.analyze(input, "English")
        val session = repository.createSession(initial, input, "English")
        val firstFifteen = dao.savedQuestions.toList()

        repository.addMoreQuestions(session, firstFifteen)

        assertEquals(30, dao.savedQuestions.size)
        assertEquals(30, dao.savedQuestions.map { it.prompt }.distinct().size)
        assertEquals(6, generator.prompts.size)
        assertTrue(generator.prompts.drop(3).all { it.contains("QUESTIONS ALREADY ASKED") })
    }

    @Test
    fun answerFeedbackIsStoredOnTheQuestion() = runBlocking {
        val dao = FakeStudyDao()
        val session = session()
        val question = entityQuestion(0, session.id)
        dao.saveSession(session)
        dao.saveQuestions(listOf(question))
        val generator = QueueStudyGenerator(feedbackOutput())
        val repository = StudyRepository(dao, generator, FakeJobOfferReader())

        val feedback = repository.submitAnswer(session, question, "I would inspect service logs and counters.")

        assertEquals(listOf("Clear troubleshooting order"), feedback.strengths)
        val saved = dao.savedQuestions.single()
        assertEquals("I would inspect service logs and counters.", saved.learnerAnswer)
        assertEquals("Clear troubleshooting order", saved.strengths)
        assertTrue(saved.reviewSuggested)
    }

    @Test
    fun onlineModeBuildsFifteenInOneRequestAndScoresChoiceWithoutAnotherRequest() = runBlocking {
        val dao = FakeStudyDao()
        var cloudCalls = 0
        val cloud = object : FastQuestionGenerator {
            override suspend fun generate(prompt: String): String {
                cloudCalls++
                assertTrue(prompt.contains("exactly 15 new distinct questions"))
                return """{"coveredTopicsSummary":"Role scenarios","questions":[${questionObjects(0, 15)}]}"""
            }
        }
        val repository = StudyRepository(dao, QueueStudyGenerator(), FakeJobOfferReader(), cloud)
        val result = repository.analyze(textInput(), "English", "ONLINE")
        val session = repository.createSession(result, textInput(), "English", "ONLINE")
        repository.selectChoice(dao.savedQuestions.first(), 0)

        assertEquals(1, cloudCalls)
        assertEquals(15, dao.savedQuestions.size)
        assertEquals(0, dao.savedQuestions.first().selectedOptionIndex)
        assertEquals("ONLINE", session.generationMode)
    }

    @Test
    fun onlineContinuationAddsFifteenWithOneMoreRequestAndPriorContext() = runBlocking {
        val dao = FakeStudyDao()
        val prompts = mutableListOf<String>()
        val cloud = object : FastQuestionGenerator {
            override suspend fun generate(prompt: String): String {
                prompts += prompt
                val start = if (prompts.size == 1) 0 else 15
                return """{"coveredTopicsSummary":"Role scenarios","questions":[${questionObjects(start, 15)}]}"""
            }
        }
        val repository = StudyRepository(dao, QueueStudyGenerator(), FakeJobOfferReader(), cloud)
        val result = repository.analyze(textInput(), "English", "ONLINE")
        val session = repository.createSession(result, textInput(), "English", "ONLINE")

        repository.addMoreQuestions(session, dao.savedQuestions, "ONLINE")

        assertEquals(2, prompts.size)
        assertTrue(prompts[1].contains(distinctPrompts.first()))
        assertEquals(30, dao.savedQuestions.size)
    }

    @Test
    fun invalidLaterBatchDoesNotSaveAnIncompleteStudy() = runBlocking {
        val dao = FakeStudyDao()
        val incompleteOutput = questionOutput(5).replace(
            ",\"sourceBasis\":\"The job listing describes support responsibilities.\"",
            "",
        )
        val generator = QueueStudyGenerator(initialOutput(0), incompleteOutput, incompleteOutput)
        val repository = StudyRepository(dao, generator, FakeJobOfferReader())

        try {
            repository.analyze(textInput(), "English")
            throw AssertionError("Expected invalid output to stop study creation")
        } catch (error: StudyGenerationException) {
            assertTrue(error.message.contains("questions 6–10"))
            val diagnostic = error.cause?.message.orEmpty()
            assertTrue(diagnostic.contains("batch=2"))
            assertTrue(diagnostic.contains("attempt=2"))
            assertTrue(diagnostic.contains("output_limit_tokens=2000"))
            assertTrue(diagnostic.matches(Regex(".*response_chars=\\d+.*")))
            assertTrue(diagnostic.contains("reason=missing_field_sourceBasis"))
            assertTrue(!diagnostic.contains("The job listing describes support responsibilities"))
            assertTrue(dao.savedSessions.isEmpty())
            assertTrue(dao.savedQuestions.isEmpty())
        }
    }

    @Test
    fun retriesAnInvalidQuestionBatchWithShorterJsonInstructions() = runBlocking {
        val generator = QueueStudyGenerator(
            initialOutput(0), "not-json", questionOutput(5), questionOutput(10),
        )
        val repository = StudyRepository(FakeStudyDao(), generator, FakeJobOfferReader())

        val result = repository.analyze(textInput(), "English")

        assertEquals(15, result.questions.size)
        assertTrue(generator.prompts[2].contains("previous response was incomplete or invalid JSON"))
        assertTrue(generator.prompts[2].contains("every option under 14 words"))
    }

    @Test
    fun retryForMissingQuestionFieldsRestatesTheCompleteQuestionSchema() = runBlocking {
        val incompleteOutput = questionOutput(5).replace(
            ",\"sourceBasis\":\"The job listing describes support responsibilities.\"",
            "",
        )
        val generator = QueueStudyGenerator(
            initialOutput(0), incompleteOutput, questionOutput(5), questionOutput(10),
        )
        val repository = StudyRepository(FakeStudyDao(), generator, FakeJobOfferReader())

        val result = repository.analyze(textInput(), "English")

        assertEquals(15, result.questions.size)
        assertTrue(generator.prompts[2].contains("Every question must include"))
        assertTrue(generator.prompts[2].contains("correctOptionIndex"))
    }

    @Test
    fun laterQuestionBatchesUseConstrainedJsonResponseFormat() = runBlocking {
        val generator = QueueStudyGenerator(initialOutput(0), questionOutput(5), questionOutput(10))
        val repository = StudyRepository(FakeStudyDao(), generator, FakeJobOfferReader())

        repository.analyze(textInput(), "English")

        assertEquals(3, generator.responseSchemas.size)
        assertTrue(generator.responseSchemas.drop(1).all { !it.isNullOrBlank() })
        val schema = JsonParser.parseString(checkNotNull(generator.responseSchemas[1])).asJsonObject
        val questionArray = schema.getAsJsonObject("properties").getAsJsonObject("questions")
        assertEquals(5, questionArray.get("minItems").asInt)
        assertEquals(5, questionArray.get("maxItems").asInt)
        val questionSchema = questionArray.getAsJsonObject("items")
        val requiredFields = questionSchema.getAsJsonArray("required").map { it.asString }.toSet()
        assertEquals(
            setOf("category", "prompt", "topic", "options", "correctOptionIndex", "explanation", "sourceBasis"),
            requiredFields,
        )
    }

    @Test
    fun modelUnavailableStopsBeforeCreatingAnInferenceEngine() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("study-model-missing").toFile()
        var factoryCalls = 0
        val modelFile = File(directory, "missing.litertlm")
        val manager = LocalModelManager(
            modelFile = modelFile,
            engineFactory = LocalStudyEngineFactory {
                factoryCalls++
                FakeEngine()
            },
        )
        val repository = StudyRepository(FakeStudyDao(), manager, FakeJobOfferReader())

        try {
            repository.analyze(textInput(), "English")
            throw AssertionError("Expected the missing model to stop analysis")
        } catch (_: ModelUnavailableException) {
            assertEquals(0, factoryCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeJobOfferReader : JobOfferSourceReader {
        override suspend fun read(url: String) = JobOfferSource(
            canonicalUrl = url,
            title = "Cloud Support Engineer",
            extractedText = "A public job listing describing Linux support responsibilities and network requirements.",
            readable = true,
        )
    }

    private class QueueStudyGenerator(vararg outputs: String) : StudyTextGenerator {
        override val state = MutableStateFlow<ModelState>(ModelState.Ready)
        private val queued = outputs.toMutableList()
        val prompts = mutableListOf<String>()
        val responseSchemas = mutableListOf<String?>()

        override suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String?): String {
            prompts += prompt
            responseSchemas += responseFormatSchema
            if (queued.isEmpty()) throw AssertionError("Unexpected extra local generation call")
            return queued.removeAt(0)
        }
    }

    private class FakeEngine : LocalStudyEngine {
        override suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String?) = "{}"
        override fun close() = Unit
    }

    internal class FakeStudyDao : StudyDao {
        private val sessions = linkedMapOf<String, StudySessionEntity>()
        private val questions = linkedMapOf<String, StudyQuestionEntity>()
        val savedSessions get() = sessions.values.toList()
        val savedQuestions get() = questions.values.sortedBy(StudyQuestionEntity::position)
        val savedMessages = mutableListOf<TutorMessageEntity>()
        val savedActivity = mutableListOf<StudyActivityEntity>()
        override fun observeAllQuestions(): Flow<List<StudyQuestionEntity>> = flow { emit(savedQuestions) }
        override fun observeMessages(): Flow<List<TutorMessageEntity>> = flow { emit(savedMessages.toList()) }
        override fun observeActivity(): Flow<List<StudyActivityEntity>> = flow { emit(savedActivity.toList()) }
        override suspend fun touchSession(id: String, time: Long) { sessions[id]?.let { sessions[id] = it.copy(lastOpenedAt = time) } }
        override suspend fun insertMessage(message: TutorMessageEntity) { savedMessages += message }
        override suspend fun insertActivity(activity: StudyActivityEntity) { savedActivity += activity }
        override suspend fun deleteMessages(sessionId: String) { savedMessages.removeAll { it.sessionId == sessionId } }
        override suspend fun deleteActivity(sessionId: String) { savedActivity.removeAll { it.sessionId == sessionId } }
        override suspend fun clearMessages() { savedMessages.clear() }
        override suspend fun clearActivity() { savedActivity.clear() }
        override suspend fun clearQuestions() { questions.clear() }
        override suspend fun clearSessions() { sessions.clear() }
        override suspend fun toggleSavedMark(id: String) { questions[id]?.let { questions[id] = it.copy(markedForReview = !it.markedForReview) } }
        override suspend fun removeSavedMark(id: String) { questions[id]?.let { questions[id] = it.copy(markedForReview = false, reviewSuggested = false) } }

        override fun observeSessions(): Flow<List<StudySessionEntity>> = flow { emit(savedSessions) }
        override suspend fun getSession(id: String) = sessions[id]
        override suspend fun saveSession(session: StudySessionEntity) { sessions[session.id] = session }
        override suspend fun saveQuestions(questions: List<StudyQuestionEntity>) { questions.forEach { this.questions[it.id] = it } }
        override fun observeQuestions(sessionId: String): Flow<List<StudyQuestionEntity>> = flow {
            emit(savedQuestions.filter { it.sessionId == sessionId })
        }
        override suspend fun getQuestions(sessionId: String) = savedQuestions.filter { it.sessionId == sessionId }
        override suspend fun getQuestion(id: String) = questions[id]
        override suspend fun updateChoice(id: String, choice: Int, answeredAt: Long) {
            questions[id]?.let { questions[id] = it.copy(selectedOptionIndex = choice, answeredAt = answeredAt, score = if (choice < 0) -1 else if (choice == it.correctOptionIndex) 100 else 0) }
        }
        override suspend fun deleteQuestionsForSession(sessionId: String) {
            questions.values.filter { it.sessionId == sessionId }.map { it.id }.forEach { questions.remove(it) }
        }
        override suspend fun deleteSessionById(sessionId: String) { sessions.remove(sessionId) }
        override suspend fun updateQuestion(question: StudyQuestionEntity) { questions[question.id] = question }
    }

    private fun textInput() = StudyInput(
        SourceKind.TEXT,
        "Cloud Support Engineer\nResponsibilities include supporting Linux users and investigating network incidents. Requirements include customer communication and incident documentation.",
    )

    private fun session() = StudySessionEntity(
        id = "session-1", title = "Cloud Support", sourceLabel = "Pasted job description", summary = "Support role",
        sourceContext = "The role supports Linux customers.", practiceLanguage = "English",
        coveredTopicsSummary = "Linux support", createdAt = 1L,
    )

    private fun entityQuestion(index: Int, sessionId: String) = StudyQuestionEntity(
        id = "question-$index", sessionId = sessionId, position = index, category = "Technical",
        prompt = distinctPrompts[index], topic = "Support scenario $index", evaluationCriteria = "Check evidence\nExplain impact",
        referenceAnswer = "Gather evidence and explain findings.", sourceBasis = "The listing requires support experience.",
    )

    private fun initialOutput(firstIndex: Int): String =
        """{"sourceReadable":true,"title":"Cloud Support Engineer","summary":"A role supporting cloud customers.","requirements":["Linux support","Customer communication"],"sourceContext":"Supports Linux users and network incidents.","coveredTopicsSummary":"Support triage, Linux, networking","questions":[${questionObjects(firstIndex)}]}"""

    private fun questionOutput(firstIndex: Int): String =
        """{"coveredTopicsSummary":"Support scenarios ${firstIndex + 1} through ${firstIndex + 5}","questions":[${questionObjects(firstIndex)}]}"""

    private fun questionObjects(firstIndex: Int, count: Int = 5): String = (firstIndex until firstIndex + count).joinToString(",") { index ->
        """{"category":"Technical","prompt":"${distinctPrompts[index]}","topic":"Support topic ${index + 1}","options":["Check logs and counters","Restart every host","Ignore the incident","Delete all records"],"correctOptionIndex":0,"explanation":"Evidence should guide safe troubleshooting.","sourceBasis":"The job listing describes support responsibilities."}"""
    }

    private fun feedbackOutput() =
        """{"strengths":["Clear troubleshooting order"],"missingPoints":["Compare timing across affected hosts"],"reasoningFeedback":"Good start; support the conclusion with comparable evidence.","referenceAnswer":"Compare service state, logs, counters, and timing across affected hosts.","reviewSuggested":true}"""

    companion object {
        private val distinctPrompts = listOf(
            "Trace DNS resolution when a customer's laptop cannot open the portal.",
            "Explain a complex cloud fix to a frustrated customer without hiding risk.",
            "Rank simultaneous incidents affecting payments, email, and test systems.",
            "Compare service logs, health checks, and host metrics during a startup failure.",
            "Design an escalation plan for repeated authentication errors across regions.",
            "Choose evidence to collect before escalating a cloud storage outage.",
            "Restore access when one office loses connectivity after a switch replacement.",
            "Communicate a service impact timeline to customers during a partial outage.",
            "Separate a slow database symptom from a network congestion cause.",
            "Prioritize a security alert while several routine support requests are open.",
            "Investigate a Linux process that exits immediately after deployment.",
            "Plan a safe rollback when a configuration change affects multiple tenants.",
            "Explain when a support issue needs an engineering escalation and why.",
            "Compare packet captures from working and failing customer connections.",
            "Document a recurring incident so another shift can continue the investigation.",
            "Assess a payment delay after a new certificate is installed on a gateway.",
            "Build a diagnostic checklist for users who cannot sign in from mobile devices.",
            "Explain the difference between DNS failure and an application timeout.",
            "Coordinate updates when a regional service degradation has no confirmed cause.",
            "Identify whether disk saturation or memory pressure is causing a host slowdown.",
            "Prepare a customer handover when a critical issue spans two time zones.",
            "Evaluate a proposed workaround that reduces availability for one customer group.",
            "Verify that a service recovery is stable before closing its incident ticket.",
            "Investigate intermittent packet loss between a cloud region and an office.",
            "Summarize a root-cause finding without overstating what the logs prove.",
            "Choose monitoring signals for detecting a repeat outage before customers report it.",
            "Handle conflicting evidence from application traces and infrastructure metrics.",
            "Plan access restoration after a customer account policy is changed incorrectly.",
            "Explain a delayed support response and set a clear next update time.",
            "Compare recent deployments to isolate a regression in file uploads.",
        )
    }
}
