package com.oriol.letsstudy.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.oriol.letsstudy.network.AnalyzeOfferResponseDto
import com.oriol.letsstudy.network.FeedbackResponseDto
import com.oriol.letsstudy.network.MoreQuestionsResponseDto
import com.oriol.letsstudy.network.QuestionDto
import com.oriol.letsstudy.domain.ConceptLesson
import com.oriol.letsstudy.domain.ConceptLessonSection
import com.oriol.letsstudy.domain.ConceptLessonTerm
import java.util.UUID

object StudyOutputParser {
    fun parseConceptLesson(text: String): ConceptLesson {
        if (text.length > MAX_CONCEPT_LESSON_CHARS) throw InvalidStudyOutputException("The topic lesson was too long. Please try again.")
        val json = parseObject(text)
        json.requireKeysExactly(setOf("sections", "keyTerms", "rememberThis"))

        val sectionValues = json.get("sections")
        if (sectionValues == null || !sectionValues.isJsonArray || sectionValues.asJsonArray.size() != REQUIRED_LESSON_SECTION_IDS.size) {
            throw InvalidStudyOutputException("The topic lesson is missing a required section.")
        }
        val sections = sectionValues.asJsonArray.map { value ->
            if (!value.isJsonObject) throw InvalidStudyOutputException()
            val section = value.asJsonObject
            section.requireKeysExactly(setOf("id", "title", "content"))
            ConceptLessonSection(
                id = section.requiredString("id", MAX_SECTION_ID_CHARS),
                title = section.requiredString("title", MAX_SECTION_TITLE_CHARS),
                content = section.requiredString("content", MAX_SECTION_CONTENT_CHARS),
            )
        }
        if (sections.map { it.id } != REQUIRED_LESSON_SECTION_IDS) {
            throw InvalidStudyOutputException("The topic lesson has an invalid section order.")
        }

        val termValues = json.get("keyTerms")
        if (termValues == null || !termValues.isJsonArray || termValues.asJsonArray.size() !in MIN_LESSON_TERMS..MAX_LESSON_TERMS) {
            throw InvalidStudyOutputException("The topic lesson must include 2 to 5 key terms.")
        }
        val terms = termValues.asJsonArray.map { value ->
            if (!value.isJsonObject) throw InvalidStudyOutputException()
            val term = value.asJsonObject
            term.requireKeysExactly(setOf("term", "definition"))
            ConceptLessonTerm(
                term = term.requiredString("term", MAX_TERM_CHARS),
                definition = term.requiredString("definition", MAX_TERM_DEFINITION_CHARS),
            )
        }
        if (terms.map { it.term.lowercase() }.toSet().size != terms.size) {
            throw InvalidStudyOutputException("The topic lesson repeated a key term.")
        }
        return ConceptLesson(
            sections = sections,
            keyTerms = terms,
            rememberThis = json.requiredString("rememberThis", MAX_REMEMBER_THIS_CHARS),
        )
    }

    fun parseInitialBatch(text: String): AnalyzeOfferResponseDto {
        val json = parseObject(text)
        val sourceReadable = json.requiredBoolean("sourceReadable")
        if (!sourceReadable) throw InvalidStudyOutputException("The model could not create a study from this listing.")
        val response = AnalyzeOfferResponseDto(
            sourceReadable = true,
            title = json.requiredString("title", MAX_TITLE_CHARS),
            summary = json.requiredString("summary", MAX_SUMMARY_CHARS),
            requirements = json.requiredStringList("requirements", min = 1, max = MAX_REQUIREMENTS, itemMax = MAX_REQUIREMENT_CHARS),
            sourceContext = json.requiredString("sourceContext", MAX_CONTEXT_CHARS),
            coveredTopicsSummary = json.requiredString("coveredTopicsSummary", MAX_COVERED_CHARS),
            questions = json.requiredQuestions(expectedCount = 5),
        )
        return response
    }

    fun parseQuestionBatch(text: String, expectedCount: Int = 5): MoreQuestionsResponseDto {
        require(expectedCount in 1..MAX_BATCH_SIZE) { "expectedCount must be between 1 and 15." }
        val json = parseObject(text)
        return MoreQuestionsResponseDto(
            coveredTopicsSummary = json.requiredString("coveredTopicsSummary", MAX_COVERED_CHARS),
            questions = json.requiredQuestions(expectedCount),
        )
    }

    fun parseFeedback(text: String): FeedbackResponseDto {
        val json = parseObject(text)
        return FeedbackResponseDto(
            strengths = json.requiredStringList("strengths", min = 0, max = MAX_FEEDBACK_ITEMS, itemMax = MAX_FEEDBACK_CHARS),
            missingPoints = json.requiredStringList("missingPoints", min = 0, max = MAX_FEEDBACK_ITEMS, itemMax = MAX_FEEDBACK_CHARS),
            reasoningFeedback = json.requiredString("reasoningFeedback", MAX_FEEDBACK_CHARS),
            referenceAnswer = json.requiredString("referenceAnswer", MAX_ANSWER_CHARS),
            reviewSuggested = json.requiredBoolean("reviewSuggested"),
        )
    }

    fun hasNearDuplicate(candidate: String, previous: List<String>): Boolean {
        val candidateWords = normalizedWords(candidate)
        if (candidateWords.isEmpty()) return false
        return previous.any { prior ->
            val priorWords = normalizedWords(prior)
            if (priorWords.isEmpty()) return@any false
            if (candidateWords == priorWords) return@any true
            if (candidateWords.size < MIN_WORDS_FOR_SIMILARITY || priorWords.size < MIN_WORDS_FOR_SIMILARITY) {
                return@any false
            }
            val intersection = candidateWords.intersect(priorWords).size
            val union = candidateWords.union(priorWords).size
            union > 0 && intersection.toDouble() / union >= NEAR_DUPLICATE_THRESHOLD
        }
    }

    private fun parseObject(text: String): JsonObject {
        if (text.length > MAX_MODEL_OUTPUT_CHARS) throw InvalidStudyOutputException("The model response was too large.")
        var jsonText = text.trim()
        if (jsonText.startsWith("```")) {
            jsonText = jsonText.removePrefix("```").removePrefix("json").removePrefix("JSON")
                .removeSuffix("```").trim()
        }
        val parsed = try {
            JsonParser.parseString(jsonText)
        } catch (_: RuntimeException) {
            throw InvalidStudyOutputException()
        }
        if (!parsed.isJsonObject) throw InvalidStudyOutputException()
        return parsed.asJsonObject
    }

    private fun JsonObject.requiredString(key: String, maxChars: Int): String {
        val value = get(key)
        if (value == null || value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            throw InvalidStudyOutputException("The model response is missing '$key'.")
        }
        return value.asString.trim().takeIf(String::isNotBlank)
            ?.takeIf { it.length <= maxChars }
            ?: throw InvalidStudyOutputException("The model response has an invalid '$key' value.")
    }

    private fun JsonObject.requireKeysExactly(expected: Set<String>) {
        if (keySet() != expected) throw InvalidStudyOutputException("The topic lesson has an invalid structure.")
    }

    private fun JsonObject.requiredBoolean(key: String): Boolean {
        val value = get(key)
        if (value == null || value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) {
            throw InvalidStudyOutputException("The model response is missing '$key'.")
        }
        return value.asBoolean
    }

    private fun JsonObject.requiredStringList(key: String, min: Int, max: Int, itemMax: Int): List<String> {
        val value = get(key)
        if (value == null || value.isJsonNull || !value.isJsonArray) {
            throw InvalidStudyOutputException("The model response is missing '$key'.")
        }
        val items = value.asJsonArray.map { item ->
            if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString) throw InvalidStudyOutputException()
            item.asString.trim().takeIf(String::isNotBlank)
                ?.takeIf { it.length <= itemMax }
                ?: throw InvalidStudyOutputException("The model response has an invalid '$key' item.")
        }
        if (items.size !in min..max) throw InvalidStudyOutputException("The model response has an invalid number of '$key' items.")
        return items
    }

    private fun JsonObject.requiredQuestions(expectedCount: Int): List<QuestionDto> {
        val value = get("questions")
        if (value == null || value.isJsonNull || !value.isJsonArray || value.asJsonArray.size() != expectedCount) {
            throw InvalidStudyOutputException("The model must return exactly $expectedCount questions.")
        }
        val questions = value.asJsonArray.map { questionElement -> parseQuestion(questionElement) }
        if (questions.any { hasNearDuplicate(it.prompt, questions.filterNot { other -> other.id == it.id }.map(QuestionDto::prompt)) }) {
            throw InvalidStudyOutputException("The model returned repeated or near-repeated questions.")
        }
        return questions
    }

    private fun parseQuestion(element: JsonElement): QuestionDto {
        if (!element.isJsonObject) throw InvalidStudyOutputException()
        val json = element.asJsonObject
        return QuestionDto(
            id = UUID.randomUUID().toString(),
            category = json.requiredString("category", MAX_CATEGORY_CHARS),
            prompt = json.requiredString("prompt", MAX_PROMPT_CHARS),
            topic = json.requiredString("topic", MAX_TOPIC_CHARS),
            evaluationCriteria = if (json.has("evaluationCriteria")) json.requiredStringList("evaluationCriteria", min = 2, max = MAX_CRITERIA, itemMax = MAX_CRITERION_CHARS) else emptyList(),
            referenceAnswer = if (json.has("referenceAnswer")) json.requiredString("referenceAnswer", MAX_ANSWER_CHARS) else "",
            sourceBasis = json.requiredString("sourceBasis", MAX_SOURCE_BASIS_CHARS),
            options = json.requiredStringList("options", min = 4, max = 4, itemMax = MAX_OPTION_CHARS).also { options ->
                if (options.map { it.lowercase() }.toSet().size != 4) throw InvalidStudyOutputException("Answer options must be distinct.")
            },
            correctOptionIndex = json.requiredIndex("correctOptionIndex"),
            explanation = json.requiredString("explanation", MAX_EXPLANATION_CHARS),
        )
    }

    private fun JsonObject.requiredIndex(key: String): Int {
        val value = get(key)
        val index = if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            value.asString.toIntOrNull()
        } else null
        return index?.takeIf { it in 0..3 }
            ?: throw InvalidStudyOutputException("The model response has an invalid '$key' value.")
    }

    private fun normalizedWords(value: String): Set<String> = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.length > 1 }
        .toSet()

    private const val MAX_MODEL_OUTPUT_CHARS = 100_000
    private const val MAX_BATCH_SIZE = 15
    private const val MAX_OPTION_CHARS = 300
    private const val MAX_EXPLANATION_CHARS = 1_200
    private const val MAX_TITLE_CHARS = 180
    private const val MAX_SUMMARY_CHARS = 1_500
    private const val MAX_REQUIREMENTS = 20
    private const val MAX_REQUIREMENT_CHARS = 300
    private const val MAX_CONTEXT_CHARS = 8_000
    private const val MAX_COVERED_CHARS = 1_200
    private const val MAX_CATEGORY_CHARS = 80
    private const val MAX_PROMPT_CHARS = 700
    private const val MAX_TOPIC_CHARS = 140
    private const val MAX_CRITERIA = 5
    private const val MAX_CRITERION_CHARS = 350
    private const val MAX_ANSWER_CHARS = 1_200
    private const val MAX_SOURCE_BASIS_CHARS = 500
    private const val MAX_FEEDBACK_ITEMS = 6
    private const val MAX_FEEDBACK_CHARS = 1_200
    private const val MIN_WORDS_FOR_SIMILARITY = 4
    private const val NEAR_DUPLICATE_THRESHOLD = 0.9
    private val REQUIRED_LESSON_SECTION_IDS = listOf("what_it_is", "how_it_works", "example", "why_it_matters")
    private const val MAX_CONCEPT_LESSON_CHARS = 15_000
    private const val MAX_SECTION_ID_CHARS = 32
    private const val MAX_SECTION_TITLE_CHARS = 100
    private const val MAX_SECTION_CONTENT_CHARS = 2_500
    private const val MIN_LESSON_TERMS = 2
    private const val MAX_LESSON_TERMS = 5
    private const val MAX_TERM_CHARS = 90
    private const val MAX_TERM_DEFINITION_CHARS = 450
    private const val MAX_REMEMBER_THIS_CHARS = 500
}

class InvalidStudyOutputException(
    override val message: String = "The model response was incomplete. Please try again.",
) : Exception(message)
