package com.oriol.letsstudy.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyAiProviderTest {
    @Test
    fun firebaseProviderPreservesSpecializedGenerationMethods() = runBlocking {
        val generator = RecordingFastQuestionGenerator()
        val provider = FirebaseStudyAiProvider(generator)

        StudyAiTask.entries.forEach { task ->
            provider.generate(StudyAiRequest(task.name, task, 900, "schema"))
        }

        assertEquals(listOf("question", "workspace", "workspace", "workspace", "lesson", "workspace"), generator.calls)
        assertEquals(listOf("JOB_QUESTIONS", "STUDY_PATH", "PRACTICE_ROUND", "ANSWER_FEEDBACK", "CONCEPT_LESSON", "TUTOR"), generator.prompts)
    }

    @Test
    fun onDeviceProviderPassesOutputLimitAndSchema() = runBlocking {
        var received: StudyAiRequest? = null
        val generator = object : StudyTextGenerator {
            override val state = MutableStateFlow<ModelState>(ModelState.Ready)
            override suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String?): String {
                received = StudyAiRequest(prompt, StudyAiTask.PRACTICE_ROUND, maxOutputTokens, responseFormatSchema)
                return "Local response"
            }
        }
        val request = StudyAiRequest("Make a round", StudyAiTask.PRACTICE_ROUND, 1200, "{\"type\":\"object\"}")

        val result = OnDeviceStudyAiProvider(generator).generate(request)

        assertEquals("Local response", result)
        assertEquals(request, received)
    }

    private class RecordingFastQuestionGenerator : FastQuestionGenerator {
        val calls = mutableListOf<String>()
        val prompts = mutableListOf<String>()

        override suspend fun generate(prompt: String): String = record("question", prompt)
        override suspend fun generateWorkspace(prompt: String): String = record("workspace", prompt)
        override suspend fun generateLesson(prompt: String): String = record("lesson", prompt)

        private fun record(kind: String, prompt: String): String {
            calls += kind
            prompts += prompt
            return "response"
        }
    }
}
