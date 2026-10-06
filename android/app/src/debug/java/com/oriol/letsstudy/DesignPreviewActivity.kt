package com.oriol.letsstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.oriol.letsstudy.data.StudyQuestionEntity
import com.oriol.letsstudy.data.StudySessionEntity
import com.oriol.letsstudy.ui.LetsStudyTheme
import com.oriol.letsstudy.ui.PracticeScreen
import com.oriol.letsstudy.ui.StudyScreen

/** Debug-only visual canvas for inspecting the session and question flow without making an AI request. */
class DesignPreviewActivity : ComponentActivity() {
    private val session = StudySessionEntity(
        id = "preview", title = "Technical Support · Bitcoin Mining", sourceLabel = "https://example.com/jobs/support",
        summary = "Practice the infrastructure and troubleshooting skills this role needs.",
        sourceContext = "", practiceLanguage = "English", coveredTopicsSummary = "Networking, Linux, mining devices",
        createdAt = System.currentTimeMillis(), generationMode = "ONLINE",
    )
    private var questions by mutableStateOf(
        listOf(
            sampleQuestion(0, "A mining device is unreachable over the network. Which command would you use first to check basic connectivity?", "Networking", listOf("ping 192.168.1.20", "ssh 192.168.1.20", "chmod 777 /dev/eth0", "systemctl reboot"), 0),
            sampleQuestion(1, "Which protocol provides encrypted remote administrative access to a Linux mining host?", "Linux", listOf("FTP", "Telnet", "SSH", "HTTP"), 2),
            sampleQuestion(2, "What should you check when a mining rig reports a high temperature and a falling hash rate?", "Hardware", listOf("Cooling and airflow", "The keyboard layout", "Screen brightness", "DNS cache only"), 0),
        ),
    )
    private var selectedIndex by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LetsStudyTheme {
                val index = selectedIndex
                if (index == null) {
                    StudyScreen(
                        session = session,
                        questions = questions,
                        isGenerating = false,
                        offlineAvailable = true,
                        generationProgress = null,
                        errorMessage = null,
                        onOpenQuestion = { question, _ -> selectedIndex = question.position },
                        onContinue = {},
                        onBack = {},
                        onDismissError = {},
                        onToggleReview = { question -> update(question.position) { it.copy(markedForReview = !it.markedForReview) } },
                        onOpenMenu = {},
                    )
                } else {
                    val question = questions[index]
                    key(question.id) { PracticeScreen(
                        session = session,
                        question = question,
                        isSubmitting = false,
                        onBack = { selectedIndex = null },
                        onSubmit = {},
                        onSelectChoice = { choice -> update(index) { it.copy(selectedOptionIndex = choice) } },
                        onResetChoice = { update(index) { it.copy(selectedOptionIndex = -1) } },
                        questionNumber = index + 1,
                        questionCount = questions.size,
                        onPreviousQuestion = { selectedIndex = (index - 1).coerceAtLeast(0) },
                        onNextQuestion = { selectedIndex = (index + 1).coerceAtMost(questions.lastIndex) },
                        onToggleReview = { update(index) { it.copy(markedForReview = !it.markedForReview) } },
                        onDismissError = {},
                        errorMessage = null,
                    ) }
                }
            }
        }
    }

    private fun update(index: Int, change: (StudyQuestionEntity) -> StudyQuestionEntity) {
        questions = questions.toMutableList().also { it[index] = change(it[index]) }
    }

    private fun sampleQuestion(position: Int, prompt: String, category: String, options: List<String>, correct: Int) = StudyQuestionEntity(
        id = "preview-$position", sessionId = "preview", position = position, category = category,
        prompt = prompt, topic = "Interview practice", evaluationCriteria = "", referenceAnswer = "",
        sourceBasis = "Relevant to support of mining infrastructure", optionsJson = Gson().toJson(options),
        correctOptionIndex = correct,
        explanation = when (category) {
            "Networking" -> "Ping tests whether the device responds at its network address. If it fails, check power, cabling, the local subnet, and routing before trying remote administration."
            "Linux" -> "SSH encrypts the remote session and is the normal way to administer Linux systems securely. Telnet and FTP do not provide the same protection."
            else -> "High temperature can cause a device to reduce performance to protect itself. Check fans, airflow, dust, and ambient temperature before changing software settings."
        },
    )
}
