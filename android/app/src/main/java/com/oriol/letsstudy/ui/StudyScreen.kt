package com.oriol.letsstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriol.letsstudy.data.StudyQuestionEntity
import com.oriol.letsstudy.data.StudySessionEntity
import com.oriol.letsstudy.data.StudyGenerationProgress
import com.oriol.letsstudy.data.StudyProgressStage
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    session: StudySessionEntity,
    questions: List<StudyQuestionEntity>,
    isGenerating: Boolean,
    offlineAvailable: Boolean,
    generationProgress: StudyGenerationProgress?,
    errorMessage: String?,
    onOpenQuestion: (StudyQuestionEntity, List<StudyQuestionEntity>) -> Unit,
    onContinue: (String) -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
    onToggleReview: (StudyQuestionEntity) -> Unit,
    onOpenMenu: () -> Unit,
) {
    var questionFilter by rememberSaveable(session.id) { mutableStateOf("ALL") }
    var nextRoundMode by rememberSaveable(session.id) { mutableStateOf("ONLINE") }
    var modeMenuOpen by remember { mutableStateOf(false) }
    val visibleQuestions = when (questionFilter) {
        "ANSWERED" -> questions.filter { it.selectedOptionIndex >= 0 || it.learnerAnswer.isNotBlank() }
        "SAVED" -> questions.filter { it.markedForReview || it.reviewSuggested }
        else -> questions
    }
    val answered = questions.count { it.selectedOptionIndex >= 0 || it.learnerAnswer.isNotBlank() }
    val correct = questions.count { it.correctOptionIndex >= 0 && it.selectedOptionIndex == it.correctOptionIndex }
    val reviewCount = questions.count { it.markedForReview || it.reviewSuggested }
    val nextQuestion = questions.firstOrNull { it.selectedOptionIndex < 0 && it.learnerAnswer.isBlank() }
        ?: questions.firstOrNull { it.markedForReview || it.reviewSuggested }
        ?: questions.firstOrNull()
    val sourceDisplay = remember(session.sourceLabel) {
        runCatching {
            URI(session.sourceLabel).host?.removePrefix("www.")?.let { "From $it" } ?: session.sourceLabel
        }.getOrDefault(session.sourceLabel)
    }

    Scaffold(
        containerColor = LetsStudyColors.Canvas,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenMenu) { Icon(Icons.Outlined.Menu, contentDescription = "Open study sessions") }
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Studies")
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(color = LetsStudyColors.Mint, shape = CircleShape) {
                        Text("${session.practiceLanguage}", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = LetsStudyColors.Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Text("JOB PREPARATION", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                Text(session.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 5.dp))
                Text(sourceDisplay, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                SessionOverviewCard(questions.size, answered, correct, reviewCount)
                if (nextQuestion != null) {
                    Spacer(Modifier.height(13.dp))
                    ContinueStudyCard(nextQuestion, answered, questions.size) { onOpenQuestion(nextQuestion, questions) }
                }
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Surface(
                            onClick = { modeMenuOpen = true },
                            shape = CircleShape,
                            color = LetsStudyColors.Mint,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (nextRoundMode == "ONLINE") "Fast online" else "On-device", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.ExpandMore, contentDescription = "Choose generation mode", tint = LetsStudyColors.Primary, modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("Fast online") }, onClick = { nextRoundMode = "ONLINE"; modeMenuOpen = false })
                            DropdownMenuItem(text = { Text(if (offlineAvailable) "On-device" else "On-device · set up from Home") }, enabled = offlineAvailable, onClick = { nextRoundMode = "OFFLINE"; modeMenuOpen = false })
                        }
                    }
                    Button(
                        onClick = { onContinue(nextRoundMode) }, enabled = !isGenerating,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.DeepPrimary),
                        modifier = Modifier.height(40.dp),
                    ) {
                        if (isGenerating) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (isGenerating) "Making 15 more…" else "Add 15 questions", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!offlineAvailable) Text("Offline mode can be set up from Home.", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 5.dp))
            }
            if (isGenerating) item { GenerationJourneyCard(generationProgress, nextRoundMode == "ONLINE", moreQuestions = true) }
            if (errorMessage != null) item { ErrorBanner(errorMessage, onDismissError) }
            item {
                Column(modifier = Modifier.padding(top = 7.dp)) {
                    Text("Practice route", style = MaterialTheme.typography.headlineSmall)
                    Text("${questions.size} questions · Tap any question to begin", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = questionFilter == "ALL", onClick = { questionFilter = "ALL" }, label = { Text("All") })
                        FilterChip(selected = questionFilter == "ANSWERED", onClick = { questionFilter = "ANSWERED" }, label = { Text("Study answers · $answered") })
                        FilterChip(selected = questionFilter == "SAVED", onClick = { questionFilter = "SAVED" }, label = { Text("Saved · $reviewCount") }, leadingIcon = { Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp)) })
                    }
                }
            }
            if (visibleQuestions.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                        Text(
                            if (questionFilter == "ANSWERED") "Answer a question first, then return here to study the explanation."
                            else "No questions are saved for review yet. Save a question to practice it again.",
                            Modifier.padding(18.dp), color = LetsStudyColors.Muted,
                        )
                    }
                }
            } else {
                itemsIndexed(visibleQuestions, key = { _, item -> item.id }) { _, question ->
                    QuestionListCard(
                        number = question.position + 1,
                        question = question,
                        onClick = { onOpenQuestion(question, visibleQuestions) },
                        onToggleReview = { onToggleReview(question) },
                    )
                }
            }
            item { Text("End of this round · Add another 15 whenever you're ready.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(vertical = 18.dp)) }
        }
    }
}

@Composable
private fun ContinueStudyCard(question: StudyQuestionEntity, answered: Int, total: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = LetsStudyColors.Warm,
        border = androidx.compose.foundation.BorderStroke(1.dp, LetsStudyColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = LetsStudyColors.Sun, shape = CircleShape, modifier = Modifier.size(49.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("${question.position + 1}", color = LetsStudyColors.DeepPrimary, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (answered < total) "CONTINUE YOUR PRACTICE" else "REVISIT A QUESTION", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Clay, fontWeight = FontWeight.Bold)
                Text(question.prompt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SessionOverviewCard(total: Int, answered: Int, correct: Int, review: Int) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Card),
        border = androidx.compose.foundation.BorderStroke(1.dp, LetsStudyColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WorkOutline, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(9.dp))
                Text("YOUR PROGRESS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LetsStudyColors.Primary)
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text("$answered / $total", style = MaterialTheme.typography.headlineSmall, color = LetsStudyColors.Ink)
                Spacer(Modifier.width(9.dp))
                Text("questions answered", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else answered.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = LetsStudyColors.Primary,
                trackColor = LetsStudyColors.Mint,
            )
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$correct correct", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary)
                Spacer(Modifier.width(16.dp))
                Text("$review saved for review", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted)
            }
        }
    }
}

@Composable
private fun QuestionListCard(
    number: Int,
    question: StudyQuestionEntity,
    onClick: () -> Unit,
    onToggleReview: () -> Unit,
) {
    val answered = question.selectedOptionIndex >= 0 || question.learnerAnswer.isNotBlank()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = if (answered) LetsStudyColors.Primary else LetsStudyColors.Warm, shape = CircleShape, modifier = Modifier.size(39.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (answered) Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(21.dp))
                    else Text(number.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.padding(top = 6.dp).width(2.dp).height(62.dp).background(LetsStudyColors.Border))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(question.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Clay, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(question.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Text(
                if (!answered) "Ready to practise" else if (question.correctOptionIndex >= 0 && question.selectedOptionIndex == question.correctOptionIndex) "Learned · Review answer" else "Study the answer",
                style = MaterialTheme.typography.bodySmall,
                color = if (answered) LetsStudyColors.Primary else LetsStudyColors.Muted,
                modifier = Modifier.padding(top = 5.dp, bottom = 14.dp),
            )
            HorizontalDivider(color = LetsStudyColors.Border)
        }
        IconButton(onClick = onToggleReview, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Outlined.Flag, contentDescription = if (question.markedForReview) "Remove saved question" else "Save question for review", modifier = Modifier.size(19.dp), tint = if (question.markedForReview) LetsStudyColors.Clay else LetsStudyColors.Muted)
        }
    }
}
