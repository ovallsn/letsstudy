package com.oriol.letsstudy.data

import com.google.gson.Gson
import com.oriol.letsstudy.ai.InvalidStudyOutputException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StudyWorkspaceTest {
    private val prompts = listOf("Inspect disk capacity on a Linux host.", "Explain why DNS caches expire.", "Diagnose a failed TLS handshake.",
        "Compare proof of work with proof of stake.", "Plan an escalation for a customer outage.", "Choose a safe rollback strategy.",
        "Describe how ASIC chips differ from CPUs.", "Prioritise a security alert during maintenance.", "Measure packet loss between regions.",
        "Identify risks of an overloaded power supply.", "Interpret an unexpected HTTP response code.", "Improve a database index for a slow query.",
        "Protect backups from ransomware.", "Document the impact of a firmware update.", "Resolve a DHCP address conflict.")
    private fun session() = StudySessionEntity("study", "Systems", "Topic", "Learn systems", "Technical foundations", "English", "", 1, "ONLINE", studyKind = "TOPIC")
    private fun output(format: PracticeFormat, invalidAt: Int = -1): String = Gson().toJson(mapOf("questions" to prompts.mapIndexed { index, prompt ->
        val options = when (format) { PracticeFormat.MULTIPLE_CHOICE -> listOf("Inspect evidence", "Delete files", "Ignore warning", "Restart blindly"); PracticeFormat.TRUE_FALSE -> listOf("True", "False"); else -> emptyList() }
        mutableMapOf<String, Any>("prompt" to prompt, "topic" to "Systems", "options" to options, "correctOptionIndex" to if (options.isEmpty()) -1 else 0,
            "expectedAnswer" to "Inspect evidence", "alternatives" to listOf("Review evidence"), "criteria" to listOf("Explain reasoning"),
            "explanation" to "Evidence gives a useful and safe basis for the next step.", "sourceBasis" to "General domain knowledge").apply { if (index == invalidAt) remove("explanation") }
    }))

    @Test fun everyFormatSavesACompleteValidatedRoundWithOneOnlineCall() = runBlocking {
        PracticeFormat.entries.forEach { format ->
            val dao = StudyRepositoryTest.FakeStudyDao()
            dao.saveSession(session())
            var calls = 0
            StudyWorkspace(dao) { _, _ -> calls++; output(format) }.addRound(session(), format, null)
            assertEquals(1, calls)
            assertEquals(15, dao.savedQuestions.size)
            assertTrue(dao.savedQuestions.all { it.format == format.name && it.answeredAt == 0L })
        }
    }

    @Test fun malformedRoundDoesNotSaveAnyQuestionsOrRetry() = runBlocking {
        val dao = StudyRepositoryTest.FakeStudyDao(); dao.saveSession(session())
        var calls = 0
        try { StudyWorkspace(dao) { _, _ -> calls++; output(PracticeFormat.MULTIPLE_CHOICE, 9) }.addRound(session(), PracticeFormat.MULTIPLE_CHOICE, null); fail("Expected validation failure") }
        catch (_: InvalidStudyOutputException) { }
        assertEquals(1, calls); assertTrue(dao.savedQuestions.isEmpty()); assertEquals(1, dao.savedSessions.size)
    }

    @Test fun addingRoundPreservesLatestHistoryTimestamp() = runBlocking {
        val dao = StudyRepositoryTest.FakeStudyDao()
        dao.saveSession(session().copy(lastOpenedAt = 12345))
        StudyWorkspace(dao) { _, _ -> output(PracticeFormat.MULTIPLE_CHOICE) }.addRound(session(), PracticeFormat.MULTIPLE_CHOICE, null)
        assertEquals(12345L, dao.getSession("study")?.lastOpenedAt)
    }

    @Test fun topicPlanIsValidatedAndSavedWithModules() = runBlocking {
        val dao = StudyRepositoryTest.FakeStudyDao()
        val plan = """{"title":"Systems foundations","summary":"Learn practical diagnostics","modules":[{"title":"DNS","outcome":"Resolve names","theory":"DNS maps readable hostnames to network addresses so clients can find services.","example":"A lookup for example.org returns its address records.","commonMistake":"Changing a DNS record does not instantly update every cache."},{"title":"TLS","outcome":"Diagnose certificates","theory":"TLS protects data in transit and verifies a server with a certificate.","example":"A browser checks that the certificate matches the requested domain."},{"title":"Linux","outcome":"Inspect evidence","theory":"Linux exposes processes and network state through commands and virtual filesystems.","example":"Use ss -lntp to inspect listening TCP ports.","commonMistake":"A listening port does not prove that a remote firewall allows access."}]}"""
        var calls = 0
        val path = StudyWorkspace(dao) { prompt, _ ->
            calls++
            assertTrue(prompt.substringBefore("DATA:").contains("Thai"))
            assertTrue(prompt.contains("For changing subjects such as law, policy, health, or regulations"))
            assertTrue(prompt.contains("without a supplied source, label it as general background and advise checking a current official source"))
            assertTrue(prompt.contains("If material is supplied, ground lessons in it and distinguish facts from assumptions"))
            plan
        }.createPath(StudySetup("Systems", "Interview", "Beginner", "Balanced", "Next month", "Thai", "ONLINE"))
        assertEquals(1, calls)
        assertEquals(3, path.modules().size)
        assertTrue(path.modules().all { it.theory.isNotBlank() && it.example.isNotBlank() })
        assertEquals(path, dao.getSession(path.id))
        assertEquals("TOPIC", path.studyKind)
    }

    @Test fun failedLastOfflineBatchDoesNotLeakPartialQuestions() = runBlocking {
        val dao = StudyRepositoryTest.FakeStudyDao()
        val local = session().copy(generationMode = "OFFLINE")
        dao.saveSession(local)
        var calls = 0
        val all = com.google.gson.JsonParser.parseString(output(PracticeFormat.MULTIPLE_CHOICE)).asJsonObject.getAsJsonArray("questions")
        val workspace = StudyWorkspace(dao) { _, mode ->
            assertEquals("OFFLINE", mode)
            val batch = com.google.gson.JsonArray()
            for (i in calls*5 until calls*5+5) batch.add(all[i])
            calls++
            if (calls == 3) "{}" else Gson().toJson(mapOf("questions" to batch))
        }
        try { workspace.addRound(local, PracticeFormat.MULTIPLE_CHOICE, null); fail("Expected invalid last batch") }
        catch (_: InvalidStudyOutputException) { }
        assertEquals(3, calls)
        assertTrue(dao.savedQuestions.isEmpty())
    }

    @Test fun fillBlankAcceptsGeneratedAlternativesAndScoresLocally() = runBlocking {
        val dao = StudyRepositoryTest.FakeStudyDao(); dao.saveSession(session())
        val workspace = StudyWorkspace(dao) { _, _ -> output(PracticeFormat.FILL_BLANK) }
        workspace.addRound(session(), PracticeFormat.FILL_BLANK, null)
        val question = dao.savedQuestions.first()
        workspace.answer(question, "  REVIEW   EVIDENCE  ")
        assertEquals(100, dao.getQuestion(question.id)?.score)
        assertTrue(dao.getQuestion(question.id)!!.isAnswered())
    }

    @Test fun pendingFeedbackIsNotCountedAsCompleted() {
        val question = StudyQuestionEntity("q", "study", 0, "Open answer", "Why?", "DNS", "Reason", "Because", "General", learnerAnswer = "My response", format = PracticeFormat.OPEN_ANSWER.name)
        assertFalse(question.isAnswered())
        assertTrue(question.copy(answeredAt = 123, score = 50).isAnswered())
    }

    @Test fun failedTutorMessageSurvivesAndCanBeRetriedWithoutDuplicatingIt() = runBlocking {
        val dao = StudyRepositoryTest.FakeStudyDao(); dao.saveSession(session())
        try { StudyWorkspace(dao) { _, _ -> throw java.io.IOException("offline") }.tutor(session(), null, "Explain DNS", emptyList()); fail("Expected failure") }
        catch (_: java.io.IOException) { }
        assertEquals(1, dao.savedMessages.size)
        StudyWorkspace(dao) { _, _ -> "{\"reply\":\"DNS maps names to addresses.\"}" }.tutor(session(), null, "Explain DNS", dao.savedMessages.toList())
        assertEquals(listOf("user", "assistant"), dao.savedMessages.map { it.role })
    }

    @Test fun deletionRemovesQuestionsConversationsAndActivity() = runBlocking {
        val dao = StudyRepositoryTest.FakeStudyDao(); dao.saveSession(session())
        StudyWorkspace(dao) { _, _ -> output(PracticeFormat.VOCABULARY) }.addRound(session(), PracticeFormat.VOCABULARY, null)
        dao.insertMessage(TutorMessageEntity("m", "study", "", "user", "Hello", 1))
        dao.insertActivity(StudyActivityEntity("a", "study", 1, 60))
        dao.deleteSessionWithQuestions("study")
        assertTrue(dao.savedSessions.isEmpty()); assertTrue(dao.savedQuestions.isEmpty()); assertTrue(dao.savedMessages.isEmpty()); assertTrue(dao.savedActivity.isEmpty())
    }

    @Test fun slidesAreReadInNumericOrderAndExternalEntitiesAreRejected() {
        val archive = zip(mapOf("ppt/slides/slide10.xml" to slide("Tenth"), "ppt/slides/slide2.xml" to slide("Second"), "ppt/slides/slide1.xml" to slide("First")))
        val text = PresentationTextReader.read(archive)
        assertTrue(text.indexOf("First") < text.indexOf("Second")); assertTrue(text.indexOf("Second") < text.indexOf("Tenth"))
        try { PresentationTextReader.read(zip(mapOf("ppt/slides/slide1.xml" to "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///private'>]><foo>&xxe;</foo>"))); fail("Expected unsafe XML rejection") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun oversizedSlidesAndDuplicateSlidesAreRejected() {
        try { PresentationTextReader.read(zip(mapOf("ppt/slides/slide1.xml" to "x".repeat(1024 * 1024 + 1)))); fail("Expected size rejection") }
        catch (_: IllegalArgumentException) { }
    }

    private fun slide(text: String) = "<p:sld xmlns:p='urn:slide' xmlns:a='urn:drawing'><a:t>$text</a:t></p:sld>"
    private fun zip(files: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { output -> files.forEach { (name, text) -> output.putNextEntry(ZipEntry(name)); output.write(text.toByteArray()); output.closeEntry() } }
        return bytes.toByteArray()
    }
}
