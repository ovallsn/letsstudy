package com.oriol.letsstudy.domain

/** A short, structured lesson that expands on one study question. */
data class ConceptLesson(
    val sections: List<ConceptLessonSection>,
    val keyTerms: List<ConceptLessonTerm>,
    val rememberThis: String,
)

data class ConceptLessonSection(
    val id: String,
    val title: String,
    val content: String,
)

data class ConceptLessonTerm(
    val term: String,
    val definition: String,
)

data class ConceptLessonPromptInput(
    val language: String,
    val sourceContext: String,
    val question: String,
    val topic: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val sourceBasis: String,
)
