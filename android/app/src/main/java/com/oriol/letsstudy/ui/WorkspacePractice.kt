package com.oriol.letsstudy.ui

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oriol.letsstudy.ai.StudyOutputParser
import com.oriol.letsstudy.data.*

@Composable
fun WorkspaceStudyPath(state: LetsStudyUiState, viewModel: LetsStudyViewModel, onMenu: () -> Unit, onTutor: () -> Unit, onProgressCheck: (String, String) -> Unit, onGenerate: (PracticeFormat, LearningModule?, String, Boolean) -> Unit) {
    val session = state.activeSession ?: return
    val questions = state.questions
    var selectedFormat by rememberSaveable(session.id) { mutableStateOf(PracticeFormat.MULTIPLE_CHOICE.name) }
    var mode by rememberSaveable(session.id) { mutableStateOf(session.generationMode) }
    var selectedModuleId by rememberSaveable(session.id) { mutableStateOf("") }
    var filter by rememberSaveable(session.id) { mutableStateOf("All questions") }
    val modules = session.modules().ifEmpty {
        questions.groupBy { it.topic }.entries.mapIndexed { index, entry -> LearningModule("topic-$index", entry.key, "Practice ${entry.value.size} questions about ${entry.key}") }
    }
    val selectedModule = modules.firstOrNull { it.id == selectedModuleId }
    val visible = questions.filter { q ->
        (selectedModule == null || q.moduleId == selectedModule.id || q.moduleId.isEmpty() && q.topic == selectedModule.title) &&
            when (filter) { "To practise" -> !q.isAnswered(); "Answered" -> q.isAnswered(); "Saved" -> q.markedForReview || q.reviewSuggested; else -> true }
    }
    val answered = questions.count { it.isAnswered() }
    WorkspacePage("Study path", onMenu) {
        item {
            Text(
                when (session.studyKind) {
                    "JOB" -> "INTERVIEW PREPARATION"
                    "MATERIAL" -> "MATERIAL STUDY"
                    "LANGUAGE" -> "LANGUAGE LEARNING"
                    else -> "TOPIC STUDY"
                },
                style = MaterialTheme.typography.labelSmall,
                color = LetsStudyColors.Primary,
                fontWeight = FontWeight.Bold,
            )
            WorkspaceTitle(session.title, session.summary)
            Spacer(Modifier.height(12.dp))
            Text("${session.practiceLanguage} · ${session.level}", color = LetsStudyColors.Primary, style = MaterialTheme.typography.labelMedium)
        }
        item {
            WorkspaceCard(color = LetsStudyColors.Mint) {
                Text("$answered of ${questions.size} questions explored", fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { if (questions.isEmpty()) 0f else answered.toFloat() / questions.size }, Modifier.fillMaxWidth())
                if (session.goal.isNotBlank()) Text("Your goal: ${session.goal}")
                if (session.target.isNotBlank()) Text("Target: ${session.target}", style = MaterialTheme.typography.bodySmall)
                if (session.studyKind == "LANGUAGE") {
                    OutlinedButton({ onProgressCheck(session.practiceLanguage, session.level) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Translate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Check my ${session.practiceLanguage} level")
                    }
                }
                if (questions.isNotEmpty()) Button({
                    val q = questions.firstOrNull { !it.isAnswered() } ?: questions.first()
                    viewModel.selectQuestion(q, questions)
                }, Modifier.fillMaxWidth()) { Text(if (answered == questions.size) "Study answers again" else "Continue practice"); Icon(Icons.Outlined.PlayArrow, null) }
            }
        }
        if (modules.isNotEmpty()) item {
            Text("Your learning route", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(modules, key = { it.id }) { module ->
            val moduleQuestions = questions.filter { it.moduleId == module.id || it.moduleId.isEmpty() && it.topic == module.title }
            var showLesson by rememberSaveable(session.id, module.id) { mutableStateOf(false) }
            WorkspaceCard(color = if (selectedModuleId == module.id) LetsStudyColors.Mint else LetsStudyColors.Card) {
                Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(module.outcome, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                Text("${moduleQuestions.count { it.isAnswered() }} / ${moduleQuestions.size} answered", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary)
                if (module.theory.isNotBlank() || module.example.isNotBlank() || module.commonMistake.isNotBlank()) {
                    TextButton({ showLesson = !showLesson }) { Text(if (showLesson) "Hide lesson" else "Read lesson") }
                    if (showLesson) {
                        if (module.theory.isNotBlank()) {
                            Text("The idea", style = MaterialTheme.typography.labelLarge, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                            Text(module.theory, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (module.example.isNotBlank()) {
                            Text("Example", style = MaterialTheme.typography.labelLarge, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                            Text(module.example, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (module.commonMistake.isNotBlank()) {
                            Text("Watch for", style = MaterialTheme.typography.labelLarge, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                            Text(module.commonMistake, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text("Open the study tutor for a lesson on this topic.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                }
                Row {
                    TextButton({ selectedModuleId = if (selectedModuleId == module.id) "" else module.id }) { Text(if (selectedModuleId == module.id) "Show all modules" else "Choose module") }
                    if (moduleQuestions.isNotEmpty()) TextButton({ viewModel.selectQuestion(moduleQuestions.firstOrNull { !it.isAnswered() } ?: moduleQuestions.first(), moduleQuestions) }) { Text("Practise") }
                }
            }
        }
        item {
            WorkspaceCard {
                Text(if (selectedModule == null) "Keep your curiosity moving" else "Practise ${selectedModule.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                WorkspaceSelect("Practice format", PracticeFormat.valueOf(selectedFormat).label, PracticeFormat.entries.map { it.label }) { label -> selectedFormat = PracticeFormat.entries.first { it.label == label }.name }
                WorkspaceSelect("Study mode", if (mode == "ONLINE") "Fast online" else "On-device", listOf("Fast online", "On-device")) { mode = if (it == "Fast online") "ONLINE" else "OFFLINE" }
                Button({ onGenerate(PracticeFormat.valueOf(selectedFormat), selectedModule, mode, false) }, Modifier.fillMaxWidth(), enabled = !state.workspaceBusy && !state.isGenerating) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add 15 new questions") }
            }
        }
        item { WorkspaceAction("Ask your study tutor", "Definitions, examples and a little more clarity", Icons.Outlined.ChatBubbleOutline, onTutor) }
        if (session.studyKind == "JOB") item { WorkspaceAction("Mock interview", "Five questions · answer in writing or dictate", Icons.Outlined.Mic) { onGenerate(PracticeFormat.INTERVIEW, null, mode, true) } }
        if (questions.isNotEmpty()) item { WorkspaceSelect("Review questions", filter, listOf("All questions", "To practise", "Answered", "Saved")) { filter = it } }
        items(visible, key = { it.id }) { question ->
            WorkspaceAction("${question.position + 1}. ${question.prompt}", "${question.category} · ${if (question.isAnswered()) "Answered" else "Ready to practise"}", if (question.isAnswered()) Icons.Outlined.CheckCircle else Icons.Outlined.PlayCircleOutline) { viewModel.selectQuestion(question, visible) }
        }
    }
}

@Composable
fun WorkspacePractice(state: LetsStudyUiState, viewModel: LetsStudyViewModel, onTutor: () -> Unit, onLesson: () -> Unit, onAnswer: (String) -> Unit) {
    val question = state.selectedQuestion ?: return
    val session = state.activeSession ?: return
    val queue = state.practiceQuestionIds.ifEmpty { state.questions.map { it.id } }
    val index = queue.indexOf(question.id).coerceAtLeast(0)
    val options = question.options()
    val objective = options.isNotEmpty() && question.correctOptionIndex in options.indices
    val recall = question.format in listOf(PracticeFormat.FLASHCARD.name, PracticeFormat.VOCABULARY.name)
    var answer by rememberSaveable(question.id) { mutableStateOf(question.learnerAnswer) }
    var revealed by rememberSaveable(question.id) { mutableStateOf(question.answeredAt > 0) }
    var selected by remember(question.id, question.selectedOptionIndex) { mutableStateOf(question.selectedOptionIndex) }
    var showExplanation by rememberSaveable(question.id) { mutableStateOf(false) }
    var showTheory by rememberSaveable(question.id) { mutableStateOf(false) }
    var speechError by rememberSaveable(question.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val speechIntent = remember(session.practiceLanguage) { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Your interview answer")
        val language = when (session.practiceLanguage.lowercase()) { "english" -> "en"; "spanish", "español" -> "es"; "thai", "ไทย" -> "th"; else -> java.util.Locale.getDefault().toLanguageTag() }
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
    } }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text -> answer = (answer + " " + text).trim().take(8000) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { speech.launch(speechIntent) }.onFailure { speechError = "Speech recognition isn't available. You can type your answer." }
        else speechError = "Microphone permission was not granted. You can type your answer."
    }
    Scaffold(containerColor = LetsStudyColors.Canvas, bottomBar = {
        Surface(color = LetsStudyColors.Card, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ viewModel.moveQuestion(-1) }, enabled = index > 0 && !state.workspaceBusy, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp)); Text("Previous") }
                    Button({ if (index < queue.lastIndex) viewModel.moveQuestion(1) else viewModel.closeQuestion() }, enabled = !state.workspaceBusy, modifier = Modifier.weight(1f)) { Text(if (index < queue.lastIndex) "Next question" else "Finish round"); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(viewModel::closeQuestion) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to study") }
                    Text(if (question.format == PracticeFormat.INTERVIEW.name && question.moduleId.startsWith("interview-")) "Mock interview" else "Focused practice", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconButton({ viewModel.toggleReview(question) }) { Icon(if (question.markedForReview) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (question.markedForReview) "Remove bookmark" else "Save question", tint = LetsStudyColors.Primary) }
                }
                LinearProgressIndicator(progress = { (index + 1f) / queue.size.coerceAtLeast(1) }, Modifier.fillMaxWidth(), trackColor = LetsStudyColors.Mint)
                Text("QUESTION ${index + 1} OF ${queue.size} · ${question.category.uppercase()}", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary, modifier = Modifier.padding(top = 10.dp))
            }
            item {
                Text(question.topic, style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted)
                Text(question.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, fontFamily = if (question.format == PracticeFormat.CODE.name) FontFamily.Monospace else MaterialTheme.typography.headlineSmall.fontFamily, modifier = Modifier.padding(top = 8.dp))
            }
            if (objective) {
                items(options.indices.toList()) { choice ->
                    Surface(onClick = { if (selected < 0) { selected = choice; viewModel.selectChoice(choice); showExplanation = true } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        color = if (selected == choice) LetsStudyColors.Mint else LetsStudyColors.Card,
                        border = BorderStroke(1.dp, if (selected == choice) LetsStudyColors.Primary else LetsStudyColors.Border)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${'A' + choice}", color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                            Text(options[choice], Modifier.weight(1f))
                            if (selected == choice) Icon(if (choice == question.correctOptionIndex) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel, null, tint = if (choice == question.correctOptionIndex) androidx.compose.ui.graphics.Color(0xFF20876C) else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else if (recall) item {
                WorkspaceCard {
                    Text("Take a moment to recall what you know.", color = LetsStudyColors.Muted)
                    if (!revealed) Button({ revealed = true }, Modifier.fillMaxWidth()) { Text("Reveal explanation") }
                    else {
                        Text(question.referenceAnswer, style = MaterialTheme.typography.titleMedium)
                        Text(question.explanation)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton({ viewModel.answerPractice(question.referenceAnswer, 0) }, enabled = !state.workspaceBusy, modifier = Modifier.weight(1f)) { Text("Again") }
                            Button({ viewModel.answerPractice(question.referenceAnswer, 100) }, enabled = !state.workspaceBusy, modifier = Modifier.weight(1f)) { Text("Know it") }
                        }
                    }
                }
            } else item {
                WorkspaceCard {
                    WorkspaceField(answer, { answer = it }, if (question.format == PracticeFormat.FILL_BLANK.name) "Missing term" else "Your answer", if (question.format == PracticeFormat.FILL_BLANK.name) 1 else 5)
                    if (question.format == PracticeFormat.INTERVIEW.name) {
                        OutlinedButton({ permission.launch(Manifest.permission.RECORD_AUDIO) }, enabled = !state.workspaceBusy) { Icon(Icons.Outlined.Mic, null); Text("Dictate answer") }
                        Text("Uses your phone's speech service; audio may be processed online. Let’sStudy saves only the transcript.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                        speechError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    Button({ onAnswer(answer.trim()); revealed = true }, enabled = answer.isNotBlank() && !state.workspaceBusy, modifier = Modifier.fillMaxWidth()) { Text(if (question.format == PracticeFormat.FILL_BLANK.name) "Check answer" else if (question.answeredAt > 0) "Review my answer again" else "Get feedback") }
                }
            }
            if (selected >= 0 || question.answeredAt > 0) item {
                WorkspaceCard(color = LetsStudyColors.Mint) {
                    Text(if (objective) if (selected == question.correctOptionIndex) "You got it." else "A useful one to revisit." else if (question.format == PracticeFormat.FILL_BLANK.name) if (question.score == 100) "Correct term." else "Here's the term to remember." else "Your answer review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!objective && question.score >= 0) Text("Study feedback · ${question.score}/100", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary)
                    Text(question.reasoningFeedback.ifBlank { question.explanation })
                    if (question.strengths.isNotBlank()) { Text("What worked", fontWeight = FontWeight.Bold); Text(question.strengths) }
                    if (question.missingPoints.isNotBlank()) { Text("What to add", fontWeight = FontWeight.Bold); Text(question.missingPoints) }
                    Text("Answer to study", fontWeight = FontWeight.Bold)
                    Text(if (objective) options.getOrNull(question.correctOptionIndex).orEmpty() else question.improvedReferenceAnswer.ifBlank { question.referenceAnswer })
                    if (objective) TextButton({ viewModel.resetChoice(); selected = -1 }) { Text("Try again") }
                }
            }
            item { WorkspaceAction("Understand the whole idea", "Definitions, reasoning and a practical example", Icons.AutoMirrored.Outlined.MenuBook) { showTheory = true } }
            item { WorkspaceAction("Ask your tutor", "Ask a follow-up about this question", Icons.Outlined.ChatBubbleOutline, onTutor) }
            if (question.sourceBasis.isNotBlank()) item { Text("Source basis: ${question.sourceBasis}", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted) }
        }
    }
    if (showExplanation) AlertDialog(onDismissRequest = { showExplanation = false }, title = { Text(if (selected == question.correctOptionIndex) "That's right!" else "Let's understand why") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(options.getOrNull(question.correctOptionIndex).orEmpty(), fontWeight = FontWeight.Bold); Text(question.explanation)
        } }, confirmButton = { TextButton({ showExplanation = false }) { Text("Got it") } }, dismissButton = { TextButton({ showExplanation = false; showTheory = true }) { Text("More theory") } })
    if (showTheory) WorkspaceTheory(state, question, { showTheory = false }, onLesson)
}

@Composable
private fun WorkspaceTheory(state: LetsStudyUiState, question: StudyQuestionEntity, onDismiss: () -> Unit, onRequest: () -> Unit) {
    val lesson = remember(question.conceptLessonJson) { if (question.conceptLessonJson.isBlank()) null else runCatching { StudyOutputParser.parseConceptLesson(question.conceptLessonJson) }.getOrNull() }
    val loading = question.id in state.conceptLessonLoadingQuestionIds
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Understand ${question.topic}") },
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(question.explanation.ifBlank { question.reasoningFeedback })
            if (lesson != null) {
                lesson.sections.forEach { section -> Text(section.title, fontWeight = FontWeight.Bold); Text(section.content) }
                if (lesson.keyTerms.isNotEmpty()) Text("Terms to know", fontWeight = FontWeight.Bold)
                lesson.keyTerms.forEach { term -> Text(term.term, fontWeight = FontWeight.SemiBold); Text(term.definition) }
                Text("Remember this", fontWeight = FontWeight.Bold); Text(lesson.rememberThis)
            } else {
                Text("Go deeper with a definition, how it works, an example and key terms. Your lesson is saved with this question.", style = MaterialTheme.typography.bodySmall)
                if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                else Button(onRequest) { Text("Learn this topic") }
                state.conceptLessonErrorCodes[question.id]?.let { code -> Text(when (code) { "QUOTA" -> "The study service is temporarily unavailable. Try again later."; "NETWORK" -> "Check your internet connection and try again."; else -> "The lesson couldn't be created. Try again." }, color = MaterialTheme.colorScheme.error) }
            }
        } }, confirmButton = { TextButton(onDismiss) { Text("Back to practice") } })
}

@Composable
fun WorkspaceTutor(state: LetsStudyUiState, questionContext: Boolean, onBack: () -> Unit, onSend: (String) -> Unit) {
    val session = state.activeSession ?: return
    val question = if (questionContext) state.selectedQuestion else null
    val messages = state.messages.filter { it.sessionId == session.id && it.questionId == question?.id.orEmpty() }
    var draft by rememberSaveable(session.id, question?.id) { mutableStateOf("") }
    val scroll = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.lastOrNull()?.role == "assistant" && messages.dropLast(1).lastOrNull()?.text == draft.trim()) draft = ""
        if (messages.lastOrNull()?.role == "user" && draft.isBlank()) draft = messages.last().text
        if (messages.isNotEmpty()) scroll.animateScrollToItem(messages.size + 1)
    }
    Scaffold(containerColor = LetsStudyColors.Canvas, bottomBar = {
        Surface(color = LetsStudyColors.Card) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkspaceField(draft, { draft = it }, "What would you like to understand?", maxLength = 6000)
                Button({ onSend(draft.trim()) }, Modifier.fillMaxWidth(), enabled = draft.isNotBlank() && !state.workspaceBusy) { Text("Ask tutor"); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to study") }; Text("Your study tutor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                WorkspaceTitle("Make it click.", question?.topic ?: session.title)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Explain this with a simple example", "What are the key terms I should know?", "Give me a practical scenario to work through").forEach { prompt ->
                        OutlinedButton({ draft = prompt }, enabled = !state.workspaceBusy, modifier = Modifier.fillMaxWidth()) { Text(prompt) }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                WorkspaceCard(color = if (message.role == "user") LetsStudyColors.Mint else LetsStudyColors.Card) {
                    Text(if (message.role == "user") "You" else "Study tutor", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.workspaceBusy) item { Text("Thinking through your question…", color = LetsStudyColors.Muted) }
        }
    }
}
