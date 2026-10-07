package com.oriol.letsstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriol.letsstudy.ai.StudyOutputParser
import com.oriol.letsstudy.data.StudyQuestionEntity
import com.oriol.letsstudy.data.StudySessionEntity
import com.oriol.letsstudy.domain.ConceptLesson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Composable
fun PracticeScreen(
    session: StudySessionEntity,
    question: StudyQuestionEntity,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onSelectChoice: (Int) -> Unit,
    onResetChoice: () -> Unit,
    questionNumber: Int,
    questionCount: Int,
    onPreviousQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onToggleReview: () -> Unit,
    onDismissError: () -> Unit,
    errorMessage: String?,
    isConceptLessonLoading: Boolean = false,
    conceptLessonErrorCode: String? = null,
    onRequestConceptLesson: () -> Unit = {},
) {
    val lessonCopy = remember(session.practiceLanguage) { ConceptLessonUiCopy.forLanguage(session.practiceLanguage) }
    val conceptLesson = remember(question.conceptLessonJson) {
        question.conceptLessonJson.takeIf(String::isNotBlank)?.let { raw ->
            runCatching { StudyOutputParser.parseConceptLesson(raw) }.getOrNull()
        }
    }
    val options = remember(question.optionsJson) {
        runCatching { Gson().fromJson<List<String>>(question.optionsJson, object : TypeToken<List<String>>() {}.type) }.getOrDefault(emptyList())
    }
    if (options.size == 4 && question.correctOptionIndex in 0..3) {
        MultipleChoiceScreen(
            session, question, options, onBack, onSelectChoice, onResetChoice, onToggleReview,
            questionNumber, questionCount, onPreviousQuestion, onNextQuestion,
            conceptLesson, lessonCopy, isConceptLessonLoading, conceptLessonErrorCode, onRequestConceptLesson,
        )
        return
    }
    var answer by rememberSaveable(question.id) { mutableStateOf(question.learnerAnswer) }
    var showConceptLesson by rememberSaveable(question.id) { mutableStateOf(false) }
    val strengths = question.strengths.lines().filter(String::isNotBlank)
    val missing = question.missingPoints.lines().filter(String::isNotBlank)

    Scaffold(
        containerColor = LetsStudyColors.Canvas,
        bottomBar = { PracticeFooter(questionNumber, questionCount, onPreviousQuestion, onNextQuestion, onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Back to questions")
                }
                Spacer(Modifier.weight(1f))
                Surface(color = LetsStudyColors.Mint, shape = CircleShape) {
                    Text(session.practiceLanguage, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = LetsStudyColors.Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            PracticeProgressHeader(questionNumber, questionCount)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LetsStudyColors.Border),
            ) {
                Column(Modifier.fillMaxWidth().padding(19.dp)) {
                    Surface(color = LetsStudyColors.ClayWash, shape = CircleShape) {
                        Text(question.category, Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = LetsStudyColors.Clay, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(question.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp))
                    Text("Topic: ${question.topic}", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }

            LessonActionCard(
                hasLesson = conceptLesson != null,
                isLoading = isConceptLessonLoading,
                errorMessage = lessonCopy.errorFor(conceptLessonErrorCode),
                copy = lessonCopy,
                onClick = {
                    showConceptLesson = true
                    if (conceptLesson == null) onRequestConceptLesson()
                },
                onRetry = onRequestConceptLesson,
            )

            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(top = 18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What a strong answer should show", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    question.evaluationCriteria.split("\n").filter(String::isNotBlank).forEach { criterion ->
                        Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.Top) {
                            Text("•", color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                            Text(criterion, style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted)
                        }
                    }
                }
            }

            Text("YOUR ANSWER", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 7.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 12,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Explain your answer in your own words…") },
            )
            Button(
                onClick = { onSubmit(answer) },
                enabled = answer.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.Primary),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("Reviewing your answer…")
                } else {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (question.reasoningFeedback.isBlank()) "Check my answer" else "Review my answer again")
                }
            }
            Text("Get constructive pointers on accuracy, coverage and reasoning. Valid answers count even when you use different wording.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 9.dp))

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(errorMessage, onDismissError)
            }

            if (question.reasoningFeedback.isNotBlank()) {
                Spacer(Modifier.height(22.dp))
                FeedbackPanel(question, strengths, missing, onToggleReview)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showConceptLesson) {
        ConceptLessonSheet(
            lesson = conceptLesson,
            isLoading = isConceptLessonLoading,
            errorMessage = lessonCopy.errorFor(conceptLessonErrorCode),
            copy = lessonCopy,
            questionNumber = questionNumber,
            questionCount = questionCount,
            onDismiss = { showConceptLesson = false },
            onRetry = onRequestConceptLesson,
            onNextQuestion = { showConceptLesson = false; if (questionNumber < questionCount) onNextQuestion() else onBack() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultipleChoiceScreen(
    session: StudySessionEntity,
    question: StudyQuestionEntity,
    options: List<String>,
    onBack: () -> Unit,
    onSelectChoice: (Int) -> Unit,
    onResetChoice: () -> Unit,
    onToggleReview: () -> Unit,
    questionNumber: Int,
    questionCount: Int,
    onPreviousQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    conceptLesson: ConceptLesson?,
    lessonCopy: ConceptLessonUiCopy,
    isConceptLessonLoading: Boolean,
    conceptLessonErrorCode: String?,
    onRequestConceptLesson: () -> Unit,
) {
    var selected by remember(question.id) { mutableStateOf(question.selectedOptionIndex) }
    var showExplanation by remember(question.id) { mutableStateOf(false) }
    var showConceptLesson by rememberSaveable(question.id) { mutableStateOf(false) }
    Scaffold(
        containerColor = LetsStudyColors.Canvas,
        bottomBar = { PracticeFooter(questionNumber, questionCount, onPreviousQuestion, onNextQuestion, onBack, answered = selected >= 0) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Back to questions")
                }
                Spacer(Modifier.weight(1f))
                Text(session.practiceLanguage, color = LetsStudyColors.Muted, style = MaterialTheme.typography.labelMedium)
            }
            PracticeProgressHeader(questionNumber, questionCount)
            Surface(color = LetsStudyColors.ClayWash, shape = CircleShape, modifier = Modifier.padding(top = 20.dp)) {
                Text(question.category.uppercase(), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = LetsStudyColors.Clay, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(question.prompt, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp), modifier = Modifier.padding(top = 14.dp))
            if (question.topic.isNotBlank() && question.topic != "Interview practice") Text(question.topic, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 8.dp))
            Text("CHOOSE ONE ANSWER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
            options.forEachIndexed { index, option ->
                val isCorrectOption = selected >= 0 && index == question.correctOptionIndex
                val isWrongChoice = selected == index && index != question.correctOptionIndex
                val optionColor = when {
                    isCorrectOption -> LetsStudyColors.Mint
                    isWrongChoice -> LetsStudyColors.ClayWash
                    else -> MaterialTheme.colorScheme.surface
                }
                val optionBorder = when {
                    isCorrectOption -> LetsStudyColors.Primary
                    isWrongChoice -> LetsStudyColors.Clay
                    else -> LetsStudyColors.Border
                }
                Card(
                    onClick = { selected = index; onSelectChoice(index); showExplanation = true },
                    enabled = selected < 0,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = optionColor, disabledContainerColor = optionColor, disabledContentColor = LetsStudyColors.Ink),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(if (isCorrectOption || isWrongChoice) 2.dp else 1.dp, optionBorder),
                ) {
                    Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (isCorrectOption) LetsStudyColors.Primary else if (isWrongChoice) LetsStudyColors.Clay else LetsStudyColors.Warm, shape = CircleShape) {
                            Text(('A' + index).toString(), Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = if (isCorrectOption || isWrongChoice) androidx.compose.ui.graphics.Color.White else LetsStudyColors.Ink, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(13.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge, color = LetsStudyColors.Ink, modifier = Modifier.weight(1f))
                        if (isCorrectOption || isWrongChoice) {
                            Spacer(Modifier.width(6.dp))
                            Icon(if (isCorrectOption) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel, null, tint = optionBorder, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            LessonActionCard(
                hasLesson = conceptLesson != null,
                isLoading = isConceptLessonLoading,
                errorMessage = lessonCopy.errorFor(conceptLessonErrorCode),
                copy = lessonCopy,
                onClick = {
                    showConceptLesson = true
                    if (conceptLesson == null) onRequestConceptLesson()
                },
                onRetry = onRequestConceptLesson,
            )
            if (selected >= 0) {
                val isCorrect = selected == question.correctOptionIndex
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isCorrect) LetsStudyColors.Mint else LetsStudyColors.ClayWash),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(if (isCorrect) "Well done" else "Let's learn from this", style = MaterialTheme.typography.titleMedium, color = if (isCorrect) LetsStudyColors.Primary else LetsStudyColors.Clay, fontWeight = FontWeight.Bold)
                        Text("THE IDEA TO REMEMBER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 12.dp))
                        Text(question.explanation.ifBlank { "Review the role context and check the correct answer before relying on it." }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { showExplanation = true }) { Text("See explanation") }
                            TextButton(onClick = {
                                selected = -1
                                showExplanation = false
                                onResetChoice()
                            }) { Text("Try again") }
                        }
                    }
                }
            }
            TextButton(onClick = onToggleReview, modifier = Modifier.fillMaxWidth()) {
                Icon(if (question.markedForReview) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null)
                Spacer(Modifier.width(7.dp))
                Text(if (question.markedForReview) "Remove from review" else "Save for review")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showExplanation && selected >= 0) {
        val isCorrect = selected == question.correctOptionIndex
        val explanationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showExplanation = false },
            sheetState = explanationSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = LetsStudyColors.Card,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.60f).dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = if (isCorrect) LetsStudyColors.Mint else LetsStudyColors.ClayWash, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(if (isCorrect) Icons.Outlined.CheckCircle else Icons.Outlined.Lightbulb, null, tint = if (isCorrect) LetsStudyColors.Primary else LetsStudyColors.Clay, modifier = Modifier.size(25.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (isCorrect) "You got it" else "Let's break it down", style = MaterialTheme.typography.titleLarge, color = LetsStudyColors.Ink)
                        Text("QUESTION ${questionNumber.toString().padStart(2, '0')} · ${question.category.uppercase()}", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted)
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (!isCorrect) Text("YOUR CHOICE  ${'A' + selected}. ${options[selected]}", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Clay)
                Surface(color = LetsStudyColors.Mint, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = if (isCorrect) 0.dp else 10.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("CORRECT ANSWER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LetsStudyColors.Primary)
                            Text("${'A' + question.correctOptionIndex}. ${options[question.correctOptionIndex]}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text("Why it works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
                Text(question.explanation.ifBlank { "Review the role context and check the correct answer before relying on it." }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 7.dp))
                if (question.sourceBasis.isNotBlank()) Text("IN THIS ROLE  ${question.sourceBasis}", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 13.dp))
                TextButton(
                    onClick = {
                        showExplanation = false
                        showConceptLesson = true
                        if (conceptLesson == null) onRequestConceptLesson()
                    },
                    modifier = Modifier.padding(top = 5.dp),
                ) {
                    Icon(Icons.Outlined.Lightbulb, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (conceptLesson == null) lessonCopy.learnAction else lessonCopy.openAction)
                }
                Spacer(Modifier.height(14.dp))
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Button(onClick = {
                        showExplanation = false
                        if (questionNumber < questionCount) onNextQuestion() else onBack()
                    }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.Primary)) {
                        Text(if (questionNumber < questionCount) "Next question" else "Back to set")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                    TextButton(onClick = { showExplanation = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Stay on this question") }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
    if (showConceptLesson) {
        ConceptLessonSheet(
            lesson = conceptLesson,
            isLoading = isConceptLessonLoading,
            errorMessage = lessonCopy.errorFor(conceptLessonErrorCode),
            copy = lessonCopy,
            questionNumber = questionNumber,
            questionCount = questionCount,
            onDismiss = { showConceptLesson = false },
            onRetry = onRequestConceptLesson,
            onNextQuestion = { showConceptLesson = false; if (questionNumber < questionCount) onNextQuestion() else onBack() },
        )
    }
}

@Composable
private fun LessonActionCard(
    hasLesson: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    copy: ConceptLessonUiCopy,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Warm),
        border = androidx.compose.foundation.BorderStroke(1.dp, LetsStudyColors.Border),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, null, tint = LetsStudyColors.Clay, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(copy.lessonIntro, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(copy.quotaNote, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 3.dp))
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
                TextButton(onClick = onRetry, enabled = !isLoading, modifier = Modifier.align(Alignment.End)) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    else Text(copy.retryAction)
                }
            } else {
                Button(
                    onClick = onClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.Primary),
                    modifier = Modifier.fillMaxWidth().padding(top = 11.dp).height(45.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(17.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(copy.loading)
                    } else {
                        Icon(Icons.Outlined.Lightbulb, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (hasLesson) copy.openAction else copy.learnAction)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConceptLessonSheet(
    lesson: ConceptLesson?,
    isLoading: Boolean,
    errorMessage: String?,
    copy: ConceptLessonUiCopy,
    questionNumber: Int,
    questionCount: Int,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onNextQuestion: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = LetsStudyColors.Card,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.78f)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 23.dp),
            ) {
                Surface(color = LetsStudyColors.Mint, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lightbulb, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(25.dp))
                    }
                }
                Text(copy.lessonTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp))
                Text(copy.lessonIntro, style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 5.dp))
                when {
                    lesson != null -> {
                        lesson.sections.forEach { section ->
                            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 21.dp))
                            Text(section.content, style = MaterialTheme.typography.bodyMedium, lineHeight = 24.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        Text(copy.keyTermsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 21.dp))
                        lesson.keyTerms.forEach { term ->
                            Column(Modifier.fillMaxWidth().padding(top = 9.dp)) {
                                Text(term.term, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = LetsStudyColors.Primary)
                                Text(term.definition, style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Mint),
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        ) {
                            Column(Modifier.padding(15.dp)) {
                                Text(copy.rememberTitle, style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                                Text(lesson.rememberThis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                    isLoading -> Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LetsStudyColors.Primary)
                        Text(copy.loading, style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    }
                    errorMessage != null -> Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Button(onClick = onRetry, shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(top = 12.dp)) { Text(copy.retryAction) }
                    }
                    else -> Text(copy.loading, style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(vertical = 30.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(copy.closeAction) }
                Button(
                    onClick = onNextQuestion,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.Primary),
                    modifier = Modifier.weight(1f).height(49.dp),
                ) {
                    Text(if (questionNumber < questionCount) "Next question" else "Back to set")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun PracticeProgressHeader(questionNumber: Int, questionCount: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 17.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("QUESTION ${questionNumber.coerceAtLeast(1).toString().padStart(2, '0')}", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("${questionNumber.coerceAtLeast(1)} of $questionCount", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted)
    }
    LinearProgressIndicator(
        progress = { if (questionCount > 0) questionNumber.toFloat() / questionCount else 0f },
        modifier = Modifier.fillMaxWidth().height(5.dp),
        color = LetsStudyColors.Primary,
        trackColor = LetsStudyColors.Mint,
    )
}

@Composable
private fun PracticeFooter(
    questionNumber: Int,
    questionCount: Int,
    onPreviousQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onBack: () -> Unit,
    answered: Boolean = true,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPreviousQuestion, enabled = questionNumber > 1) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Previous")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = if (questionNumber < questionCount) onNextQuestion else onBack,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.Primary),
                modifier = Modifier.height(50.dp),
            ) {
                Text(if (questionNumber < questionCount) { if (answered) "Next question" else "Skip for now" } else "Back to set")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FeedbackPanel(question: StudyQuestionEntity, strengths: List<String>, missing: List<String>, onToggleReview: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = LetsStudyColors.Mint, shape = RoundedCornerShape(11.dp), modifier = Modifier.size(37.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Your answer review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Use these ideas to strengthen your next answer.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
        if (strengths.isNotEmpty()) {
            FeedbackListCard("What you did well", strengths, icon = Icons.Outlined.CheckCircle, accent = LetsStudyColors.Primary)
        }
        if (missing.isNotEmpty()) {
            FeedbackListCard("Points to strengthen", missing, icon = Icons.Outlined.Lightbulb, accent = LetsStudyColors.Clay)
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(top = 10.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Why this feedback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(question.reasoningFeedback, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Mint), modifier = Modifier.padding(top = 10.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Example answer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LetsStudyColors.Primary)
                Text(question.improvedReferenceAnswer.ifBlank { question.referenceAnswer }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(top = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (question.markedForReview) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null, tint = LetsStudyColors.Primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (question.markedForReview) "Saved for review" else "Come back to this one?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Saved questions appear in your review list.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                }
                TextButton(onClick = onToggleReview) { Text(if (question.markedForReview) "Remove" else "Save", color = LetsStudyColors.Primary) }
            }
        }
    }
}

@Composable
private fun FeedbackListCard(
    title: String,
    items: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(top = 10.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items.forEach { item ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Text("•", color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
