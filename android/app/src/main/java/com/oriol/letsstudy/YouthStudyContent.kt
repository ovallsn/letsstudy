package com.oriol.letsstudy

enum class LearnerAgeGroup(val canUseAdultWorkspace: Boolean) {
    AGE_9_12(false),
    AGE_13_17(false),
    ADULT(true);

    companion object {
        fun fromStoredValue(value: String?): LearnerAgeGroup? = entries.firstOrNull { it.name == value }
    }
}

data class YouthQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
)

data class YouthCourse(
    val id: String,
    val title: String,
    val description: String,
    val questions: List<YouthQuestion>,
)

data class YouthProgress(val answers: Map<String, Boolean>) {
    val answeredCount: Int get() = answers.size
    val correctCount: Int get() = answers.values.count { it }

    fun answer(questionId: String, correct: Boolean): YouthProgress = copy(answers = answers + (questionId to correct))

    fun storedValues(): Set<String> = answers.mapTo(mutableSetOf()) { (id, correct) -> "$id:$correct" }

    companion object {
        val EMPTY = YouthProgress(emptyMap())

        fun fromStoredValues(values: Set<String>, knownIds: Set<String>): YouthProgress = YouthProgress(
            values.mapNotNull { value ->
                val id = value.substringBefore(':')
                val result = value.substringAfter(':', missingDelimiterValue = "")
                if (id !in knownIds || result !in setOf("true", "false")) null else id to (result == "true")
            }.toMap(),
        )
    }
}

object YouthCourseCatalog {
    fun forGroup(group: LearnerAgeGroup): List<YouthCourse> = when (group) {
        LearnerAgeGroup.AGE_9_12 -> junior
        LearnerAgeGroup.AGE_13_17 -> teen
        LearnerAgeGroup.ADULT -> emptyList()
    }

    private fun q(id: String, prompt: String, answer: Int, explanation: String, vararg options: String) =
        YouthQuestion(id, prompt, options.toList(), answer, explanation)

    private val junior = listOf(
        YouthCourse("junior-maths", "Everyday maths", "Fractions, patterns and useful numbers.", listOf(
            q("jm-1", "What is half of 18?", 1, "Half means divide by 2. 18 ÷ 2 = 9.", "6", "9", "12", "16"),
            q("jm-2", "Which fraction is the same as one half?", 2, "Two of four equal parts make one half.", "1/4", "3/4", "2/4", "4/4"),
            q("jm-3", "A book costs 12 coins. You have 20. How many coins remain?", 0, "Subtract the cost: 20 − 12 = 8.", "8", "10", "12", "32"),
            q("jm-4", "What comes next: 3, 6, 9, 12, ...?", 3, "The pattern adds 3 each time, so 12 + 3 = 15.", "13", "14", "16", "15"),
            q("jm-5", "A rectangle has sides of 4 cm and 3 cm. What is its perimeter?", 1, "Add all four sides: 4 + 3 + 4 + 3 = 14 cm.", "7 cm", "14 cm", "12 cm", "24 cm"),
            q("jm-6", "Which number is a multiple of 5?", 2, "Multiples of 5 end in 0 or 5.", "23", "42", "35", "47"),
        )),
        YouthCourse("junior-science", "Science around us", "Observe, reason and learn how the world works.", listOf(
            q("js-1", "What do plants absorb from the air to make food?", 0, "Plants use carbon dioxide, water and light for photosynthesis.", "Carbon dioxide", "Oxygen", "Sand", "Salt"),
            q("js-2", "Which change turns liquid water into water vapour?", 1, "Evaporation changes liquid water into a gas.", "Freezing", "Evaporation", "Melting", "Condensation"),
            q("js-3", "Why does a shadow change length during the day?", 3, "The Sun's position in the sky changes as Earth rotates.", "The object shrinks", "The ground moves", "Clouds always block it", "The Sun's position changes"),
            q("js-4", "Which material is most likely to be attracted by a magnet?", 2, "Iron is a magnetic material.", "Paper", "Glass", "Iron", "Wood"),
            q("js-5", "What is a fair test of how light affects plant growth?", 0, "Change only the light and keep water, soil and plant type the same.", "Keep everything except light the same", "Use different plants and soil", "Change water and light", "Measure only one plant once"),
            q("js-6", "Which organ pumps blood around your body?", 1, "The heart pumps blood through blood vessels.", "Lungs", "Heart", "Stomach", "Brain"),
        )),
    )

    private val teen = listOf(
        YouthCourse("teen-maths", "Maths in practice", "Percentages, ratios and evidence.", listOf(
            q("tm-1", "A price falls from 80 to 60. What is the percentage decrease?", 2, "The decrease is 20 out of the original 80: 20 ÷ 80 = 25%.", "20%", "30%", "25%", "75%"),
            q("tm-2", "A map scale is 1:1000. What does 2 cm on the map represent?", 1, "2 × 1000 cm = 2000 cm, or 20 metres.", "2 metres", "20 metres", "200 metres", "2 kilometres"),
            q("tm-3", "What is the mean of 4, 6, 8 and 10?", 0, "Add the values to get 28, then divide by 4: 7.", "7", "8", "6", "28"),
            q("tm-4", "Solve 3x + 5 = 20. What is x?", 3, "Subtract 5, then divide 15 by 3: x = 5.", "3", "4", "15", "5"),
            q("tm-5", "A bag has 3 blue and 2 red counters. What is P(red)?", 1, "Two of the five equally likely counters are red.", "3/5", "2/5", "1/2", "2/3"),
            q("tm-6", "Which graph best shows change over time?", 2, "A line graph makes trends over time easy to see.", "Pie chart", "Venn diagram", "Line graph", "Map"),
        )),
        YouthCourse("teen-digital", "Digital judgement", "Check sources and protect your information.", listOf(
            q("td-1", "A website makes a surprising claim. What should you do first?", 1, "Check the source and compare with reliable independent evidence.", "Share it immediately", "Check its source and evidence", "Trust the headline", "Count the emojis"),
            q("td-2", "Which password is strongest?", 3, "A long, unique passphrase is harder to guess than a short common word.", "password123", "Your first name", "12345678", "A long unique passphrase"),
            q("td-3", "What should you avoid sharing in a public study group?", 0, "Your home address is private personal information.", "Your home address", "A maths method", "A book title", "A question about a lesson"),
            q("td-4", "An AI answer sounds confident. What is a good next step?", 2, "AI can be wrong. Check important claims against trusted sources.", "Assume it is always right", "Copy it without reading", "Verify important claims", "Ignore every source"),
            q("td-5", "What does a graph need so you can interpret it fairly?", 1, "Labels and a clear scale help show what is being measured.", "Bright colours only", "Labels and a clear scale", "A 3D effect", "No source"),
            q("td-6", "A message asks you to enter your password on a link. What should you do?", 3, "Open the service yourself using its known app or address; do not trust the message link.", "Reply with the password", "Forward it to friends", "Use the link quickly", "Open the service directly"),
        )),
    )
}
