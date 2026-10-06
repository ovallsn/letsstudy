package com.oriol.letsstudy.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyOutputParserTest {
    @Test
    fun parsesInitialMetadataAndExactlyFiveQuestions() {
        val output = """
            {
              "sourceReadable":true,
              "title":"Cloud Support Engineer",
              "summary":"Supports customers with cloud incidents.",
              "requirements":["Linux","Networking"],
              "sourceContext":"The role needs Linux and customer incident skills.",
              "coveredTopicsSummary":"Linux, customer support, networking",
              "questions":[${questions(5)}]
            }
        """.trimIndent()

        val result = StudyOutputParser.parseInitialBatch(output)

        assertEquals("Cloud Support Engineer", result.title)
        assertEquals(2, result.requirements.size)
        assertEquals(5, result.questions.size)
        assertTrue(result.sourceContext.contains("Linux"))
    }

    @Test
    fun acceptsFencedJsonButRejectsMalformedOutputAndMissingRequiredFields() {
        val fenced = "```json\n{\"coveredTopicsSummary\":\"Linux, networking\",\"questions\":[${questions(5)}]}\n```"
        assertEquals(5, StudyOutputParser.parseQuestionBatch(fenced).questions.size)

        assertInvalid("not json")
        assertInvalid("{\"coveredTopicsSummary\":\"Linux\",\"questions\":[{\"prompt\":\"Missing fields\"}]}")
    }

    @Test
    fun rejectsWrongQuestionCountAndRepeatedQuestions() {
        assertInvalid("{\"coveredTopicsSummary\":\"Linux\",\"questions\":[${questions(4)}]}")

        val repeated = questions(5, repeated = true)
        assertInvalid("{\"coveredTopicsSummary\":\"Linux\",\"questions\":[$repeated]}")
    }

    @Test
    fun rejectsInvalidMultipleChoiceOptionsAndCorrectIndex() {
        val valid = """{"coveredTopicsSummary":"Linux","questions":[${questions(5)}]}"""
        assertInvalid(valid.replace("\"correctOptionIndex\":0", "\"correctOptionIndex\":4"))
        assertInvalid(valid.replace("\"Delete data\"", "\"Check logs\""))
    }

    @Test
    fun parsesCompleteAnswerFeedbackAndRejectsIncompleteFeedback() {
        val result = StudyOutputParser.parseFeedback(
            """{"strengths":["Clear sequence"],"missingPoints":["Check counters"],"reasoningFeedback":"Good start, add evidence.","referenceAnswer":"Compare counters and traces.","reviewSuggested":true}""",
        )

        assertEquals(listOf("Clear sequence"), result.strengths)
        assertEquals("Compare counters and traces.", result.referenceAnswer)
        assertTrue(result.reviewSuggested)
        assertInvalid("{\"strengths\":[],\"missingPoints\":[],\"reviewSuggested\":false}")
    }

    @Test
    fun identifiesNearExactDuplicatesAgainstPreviouslyCoveredQuestions() {
        assertTrue(StudyOutputParser.hasNearDuplicate("Fix Linux issue", listOf("Fix Linux issue")))
        assertTrue(
            StudyOutputParser.hasNearDuplicate(
                "How do you diagnose a failing Linux service?",
                listOf("How do you diagnose a failing Linux service"),
            ),
        )
        assertFalse(
            StudyOutputParser.hasNearDuplicate(
                "How would you explain a network route change to a customer?",
                listOf("How do you diagnose a failing Linux service?"),
            ),
        )
    }

    private fun questions(count: Int, repeated: Boolean = false): String = (0 until count).joinToString(",") { index ->
        val prompt = if (repeated) {
            "How would you diagnose a Linux service issue during an outage?"
        } else {
            listOf(
                "How would you diagnose a Linux service issue during an outage?",
                "How would you explain a complex fix to a frustrated customer?",
                "Which network checks would you run after a connectivity complaint?",
                "How do you prioritize incidents when several customers report failures?",
                "What evidence would you collect before escalating a cloud problem?",
            )[index]
        }
        """{"category":"Technical","prompt":"$prompt","topic":"Support scenario ${index + 1}","options":["Check logs","Restart everything","Ignore alerts","Delete data"],"correctOptionIndex":0,"explanation":"Checking logs reveals the failure before changing anything.","sourceBasis":"The listing requires Linux support."}"""
    }

    private fun assertInvalid(output: String) {
        try {
            StudyOutputParser.parseQuestionBatch(output)
            throw AssertionError("Expected invalid model output to be rejected")
        } catch (_: InvalidStudyOutputException) {
            // Expected.
        }
    }
}
