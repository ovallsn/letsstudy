package com.oriol.letsstudy.ai

import com.google.gson.Gson
import com.oriol.letsstudy.data.JobOfferSource
import com.oriol.letsstudy.data.JobOfferSourceReader
import com.oriol.letsstudy.data.LearningModule
import com.oriol.letsstudy.data.PracticeFormat
import com.oriol.letsstudy.data.StudyRepository
import com.oriol.letsstudy.data.StudyRepositoryTest
import com.oriol.letsstudy.data.StudySetup
import com.oriol.letsstudy.data.StudyWorkspace
import com.oriol.letsstudy.data.StudyQuestionEntity
import com.oriol.letsstudy.data.StudySessionEntity
import com.oriol.letsstudy.domain.SourceKind
import com.oriol.letsstudy.domain.StudyInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAiIntegrationTest {
    @Test
    fun jobQuestionsUseSelectedProvider() = runBlocking {
        val provider = RecordingProvider { request -> when (request.task) {
            StudyAiTask.JOB_QUESTIONS -> questionBatch(15)
            else -> error("Unexpected task ${request.task}")
        } }
        val dao = StudyRepositoryTest.FakeStudyDao()
        val repository = repository(dao, provider)
        val input = StudyInput(SourceKind.URL, "https://jobs.example/private?tracking=secret")

        val result = repository.analyze(input, "English", "CHATGPT")

        assertEquals(15, result.questions.size)
        assertEquals(listOf(StudyAiTask.JOB_QUESTIONS), provider.requests.map(StudyAiRequest::task))
    }

    @Test
    fun workspacePathAndPracticeUseSelectedProvider() = runBlocking {
        val provider = RecordingProvider { request -> when (request.task) {
            StudyAiTask.STUDY_PATH -> pathJson()
            StudyAiTask.PRACTICE_ROUND -> practiceQuestions(15)
            else -> error("Unexpected task ${request.task}")
        } }
        val dao = StudyRepositoryTest.FakeStudyDao()
        val workspace = workspace(dao, provider)

        val session = workspace.createPath(StudySetup("Network support", "Interview", "B1", "Balanced", "One week", "English", "CHATGPT"))
        workspace.addRound(session, PracticeFormat.MULTIPLE_CHOICE, LearningModule("module-0", "Networking", "Troubleshoot networks"))

        assertEquals(listOf(StudyAiTask.STUDY_PATH, StudyAiTask.PRACTICE_ROUND), provider.requests.map(StudyAiRequest::task))
        assertEquals(15, dao.savedQuestions.size)
    }

    @Test
    fun answerFeedbackLessonAndTutorUseSelectedProvider() = runBlocking {
        var feedbackCount = 0
        val provider = RecordingProvider { request -> when (request.task) {
            StudyAiTask.ANSWER_FEEDBACK -> if (feedbackCount++ == 0) feedbackJson() else workspaceFeedbackJson()
            StudyAiTask.CONCEPT_LESSON -> lessonJson()
            StudyAiTask.TUTOR -> "{\"reply\":\"A subnet divides an IP network into smaller ranges.\"}"
            else -> error("Unexpected task ${request.task}")
        } }
        val dao = StudyRepositoryTest.FakeStudyDao()
        val session = sampleSession()
        val question = sampleQuestion(session.id)
        dao.saveSession(session)
        dao.saveQuestions(listOf(question))

        repository(dao, provider).submitAnswer(session, question, "I would check the route table.")
        repository(dao, provider).getOrGenerateConceptLesson(session, question)
        workspace(dao, provider).answer(question.copy(format = PracticeFormat.OPEN_ANSWER.name), "I would inspect the route table.")
        workspace(dao, provider).tutor(session, question, "What is a subnet?", emptyList())

        assertEquals(
            listOf(StudyAiTask.ANSWER_FEEDBACK, StudyAiTask.CONCEPT_LESSON, StudyAiTask.ANSWER_FEEDBACK, StudyAiTask.TUTOR),
            provider.requests.map(StudyAiRequest::task),
        )
    }

    @Test
    fun existingOnlineAndOfflineValuesKeepTheirCurrentProvider() {
        assertEquals(StudyAiProviderId.GEMINI, studyAiProviderIdForGenerationMode("ONLINE"))
        assertEquals(StudyAiProviderId.ON_DEVICE, studyAiProviderIdForGenerationMode("OFFLINE"))
        assertEquals(StudyAiProviderId.CHATGPT, studyAiProviderIdForGenerationMode("CHATGPT"))
        assertEquals(StudyAiProviderId.GEMINI, studyAiProviderIdForGenerationMode("unknown"))
    }

    @Test
    fun chatGptWithGeminiDisabledNeverCallsFirebaseAi() = runBlocking {
        val chatGpt = RecordingProvider { questionBatch(15) }
        var firebaseCalls = 0
        val firebase = object : StudyAiProvider {
            override val id = StudyAiProviderId.GEMINI
            override suspend fun generate(request: StudyAiRequest): String { firebaseCalls++; return questionBatch(15) }
        }
        val dao = StudyRepositoryTest.FakeStudyDao()
        repository(dao, chatGpt, firebase).analyze(textInput(), "English", "CHATGPT")

        assertEquals(1, chatGpt.requests.size)
        assertEquals(0, firebaseCalls)
    }

    @Test
    fun chatGptRequestUsesExtractedSourceContextWithoutOriginalUrlOrFileBytes() = runBlocking {
        val provider = RecordingProvider { questionBatch(15) }
        val originalUrl = "https://jobs.example/private?candidate-email=hidden@example.com"
        val repository = StudyRepository(
            dao = StudyRepositoryTest.FakeStudyDao(),
            model = EmptyStudyGenerator(),
            jobOfferReader = object : JobOfferSourceReader {
                override suspend fun read(url: String) = JobOfferSource(
                    canonicalUrl = url,
                    title = "Infrastructure Engineer",
                    extractedText = "Maintains Linux and mining infrastructure. Safe extracted detail.",
                    readable = true,
                )
            },
            router = router(provider),
        )

        val result = repository.analyze(StudyInput(SourceKind.URL, originalUrl), "English", "CHATGPT")
        val sentPrompt = provider.requests.single().prompt

        assertFalse(sentPrompt.contains(originalUrl))
        assertFalse(sentPrompt.contains("candidate-email=hidden@example.com"))
        assertTrue(sentPrompt.contains("Safe extracted detail"))
        assertFalse(result.sourceContext.contains(originalUrl))
        assertEquals(StudyAiTask.JOB_QUESTIONS, provider.requests.single().task)
    }

    private fun repository(
        dao: StudyRepositoryTest.FakeStudyDao,
        chatGpt: RecordingProvider,
        firebase: StudyAiProvider? = null,
    ) = StudyRepository(
        dao = dao,
        model = EmptyStudyGenerator(),
        jobOfferReader = object : JobOfferSourceReader {
            override suspend fun read(url: String) = JobOfferSource(
                canonicalUrl = url,
                title = "Infrastructure Engineer",
                extractedText = "Maintains Linux and mining infrastructure.",
                readable = true,
            )
        },
        router = router(chatGpt, firebase),
    )

    private fun workspace(dao: StudyRepositoryTest.FakeStudyDao, provider: RecordingProvider) =
        StudyWorkspace(
            dao = dao,
            router = router(provider),
            generate = { _, _ -> error("Legacy generation must not run when a router is configured") },
        )

    private fun router(chatGpt: RecordingProvider, firebase: StudyAiProvider? = null) = StudyAiRouter(
        buildMap {
            put(StudyAiProviderId.CHATGPT, chatGpt)
            if (firebase != null) put(StudyAiProviderId.GEMINI, firebase)
        },
    )

    private fun textInput() = StudyInput(
        SourceKind.TEXT,
        "Infrastructure Engineer. Maintains Linux and mining infrastructure. Supports network and storage incidents.",
    )

    private fun questionBatch(count: Int): String = """{"coveredTopicsSummary":"Infrastructure","questions":[${questionObjects(count)}]}"""

    private fun questionObjects(count: Int): String = (0 until count).joinToString(",") { index ->
        val prompt = jobPrompts[index % jobPrompts.size]
        """{"category":"Technical","prompt":"$prompt","topic":"Infrastructure $index","options":["Inspect evidence","Restart blindly","Delete records","Ignore symptoms"],"correctOptionIndex":0,"explanation":"Evidence helps isolate the failure safely.","sourceBasis":"The role includes infrastructure support."}"""
    }

    private fun practiceQuestions(count: Int): String = Gson().toJson(mapOf(
        "questions" to (0 until count).map { index -> mapOf(
            "prompt" to practicePrompts[index % practicePrompts.size],
            "topic" to "Networking $index",
            "options" to listOf("Inspect routes", "Delete routes", "Ignore alerts", "Restart blindly"),
            "correctOptionIndex" to 0,
            "expectedAnswer" to "Inspect routes and verify connectivity.",
            "alternatives" to emptyList<String>(),
            "criteria" to listOf("Explain why the route matters."),
            "explanation" to "The route table shows where traffic is directed.",
            "sourceBasis" to "General networking knowledge.",
        ) },
    ))

    private fun pathJson() = """{"title":"Networking foundations","summary":"Build troubleshooting confidence.","modules":[{"title":"Routes","outcome":"Read route tables","theory":"A route table determines where packets go.","example":"Use ip route to inspect routes."},{"title":"DNS","outcome":"Resolve hostnames","theory":"DNS maps names to addresses.","example":"Use dig to query a record."},{"title":"Connectivity","outcome":"Test paths","theory":"Layered tests isolate network failures.","example":"Compare ping and traceroute."}]}"""

    private fun feedbackJson() = """{"strengths":["Clear first step"],"missingPoints":["Explain expected evidence"],"reasoningFeedback":"Check the route and compare results.","referenceAnswer":"Inspect the route table, then test the destination.","reviewSuggested":false}"""

    private fun workspaceFeedbackJson() = """{"score":84,"strengths":["Clear first step"],"missingPoints":["Explain expected evidence"],"explanation":"Check the route and compare results.","improvedAnswer":"Inspect the route table, then test the destination."}"""

    private fun lessonJson() = """{"sections":[{"id":"what_it_is","title":"What it is","content":"A route table maps destinations to next hops."},{"id":"how_it_works","title":"How it works","content":"The system selects a matching route."},{"id":"example","title":"Example","content":"ip route shows Linux routes."},{"id":"why_it_matters","title":"Why it matters","content":"A wrong route can interrupt connectivity."}],"keyTerms":[{"term":"Next hop","definition":"The next router for a packet."},{"term":"Prefix","definition":"A range of IP addresses."}],"rememberThis":"Verify routes before changing them."}"""

    private fun sampleSession() = StudySessionEntity("session", "Networking", "Topic", "Learn networking", "Network support source", "English", "", 1, "CHATGPT")

    private fun sampleQuestion(sessionId: String) = StudyQuestionEntity(
        id = "question", sessionId = sessionId, position = 0, category = "Technical", prompt = "How would you diagnose a route failure?", topic = "Routing",
        evaluationCriteria = "Check route table", referenceAnswer = "Inspect the route table.", sourceBasis = "Networking basics",
    )

    private val jobPrompts = listOf(
        "Compare rack temperatures against manufacturer thermal thresholds.",
        "Trace packet loss between regional gateways using interface counters.",
        "Inspect Linux process limits after repeated worker restarts.",
        "Prioritize customer incidents by outage scope and business impact.",
        "Validate DNS records before changing public service endpoints.",
        "Plan a reversible storage migration with verified backups.",
        "Interpret authentication failures across distributed identity providers.",
        "Measure database query latency before adjusting indexes.",
        "Escalate suspicious mining pool traffic using captured network evidence.",
        "Assess power supply capacity after adding accelerator hardware.",
        "Document rollback criteria for a high availability deployment.",
        "Separate firewall filtering from application timeout symptoms.",
        "Estimate memory pressure from container metrics and kernel logs.",
        "Explain incident timelines to nontechnical infrastructure customers.",
        "Secure recovery credentials against unauthorized administrative access.",
    )

    private val practicePrompts = listOf(
        "Which command shows Linux routing entries?",
        "How can DNS resolution be checked independently?",
        "Which metric helps locate packet loss?",
        "What should be verified before a rollback?",
        "How do you inspect a listening TCP port?",
        "Which evidence identifies exhausted disk capacity?",
        "What reveals repeated service process exits?",
        "How can an HTTPS certificate mismatch be diagnosed?",
        "Which test measures latency to a remote gateway?",
        "What confirms that a backup can be restored?",
        "How should a broad customer outage be prioritized?",
        "Which signal indicates high memory pressure?",
        "What should be checked after a firewall change?",
        "How do you compare regional service health?",
        "Which log can explain an authentication failure?",
    )

    private class RecordingProvider(private val response: (StudyAiRequest) -> String) : StudyAiProvider {
        override val id = StudyAiProviderId.CHATGPT
        val requests = mutableListOf<StudyAiRequest>()
        override suspend fun generate(request: StudyAiRequest): String { requests += request; return response(request) }
    }

    private class EmptyStudyGenerator : com.oriol.letsstudy.ai.StudyTextGenerator {
        override val state = kotlinx.coroutines.flow.MutableStateFlow(ModelState.Ready)
        override suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String?) =
            error("Local model must not run for ChatGPT generation")
    }
}
