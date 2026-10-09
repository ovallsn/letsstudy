package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriol.letsstudy.data.LanguagePlacementResult
import com.oriol.letsstudy.data.LanguagePlacementTest
import com.oriol.letsstudy.data.LanguageProgression
import com.oriol.letsstudy.data.StudyPlacementAttemptEntity
import com.oriol.letsstudy.data.answers
import com.oriol.letsstudy.data.toPlacementResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkspaceLevelCheck(
    onMenu: () -> Unit,
    placementAttempts: List<StudyPlacementAttemptEntity>,
    initialLanguage: String = "",
    runId: Int = 0,
    onComplete: (LanguagePlacementResult, List<Int>, Int, String, String) -> Unit,
    onBuildPath: (LanguagePlacementResult) -> Unit,
    initialCurrentLevel: String = "",
) {
    var language by rememberSaveable(runId, initialLanguage) { mutableStateOf(initialLanguage.ifBlank { "English" }) }
    val latestAttempt = placementAttempts.filter { it.deletedAt == 0L && it.language == language }.maxByOrNull { it.completedAt }
    var bankVersion by rememberSaveable(runId, language) { mutableStateOf((latestAttempt?.bankVersion ?: -1) + 1) }
    var previousLevel by rememberSaveable(runId, language, initialLanguage, initialCurrentLevel) {
        mutableStateOf(LanguageProgression.baselineLevel(initialCurrentLevel, latestAttempt?.progressLevel?.ifBlank { latestAttempt.startingLevel }, initialLanguage, language).orEmpty())
    }
    var isProgressCheck by rememberSaveable(runId, language, initialLanguage, initialCurrentLevel) { mutableStateOf(previousLevel.isNotBlank()) }
    var questionIndex by rememberSaveable(runId, language) { mutableStateOf(0) }
    var encodedAnswers by rememberSaveable(runId, language) { mutableStateOf(List(20) { -1 }.joinToString(",")) }
    var showReview by rememberSaveable(runId, language) { mutableStateOf(false) }
    val questions = LanguagePlacementTest.questions(language, bankVersion)
    val answers = encodedAnswers.split(",").map { it.toIntOrNull() ?: -1 }
    val placement = LanguagePlacementTest.result(language, answers, bankVersion)
    val progress = LanguageProgression.advance(previousLevel.takeIf(String::isNotBlank), placement.startingLevel)
    val effectivePlacement = placement.copy(startingLevel = progress.level)

    WorkspacePage("Language check", onMenu) {
        item {
            WorkspaceTitle(
                if (isProgressCheck) "See how far you’ve come." else "Find your starting point.",
                if (isProgressCheck) "A fresh check helps shape your next step in ${language}." else "A short, original check to guide your language study—not a certificate or a self-rating.",
            )
        }
        item {
            WorkspaceCard {
                WorkspaceSelect("Language to assess", language, LanguagePlacementTest.languages) {
                    language = it
                }
            Text("20 questions · reading, grammar and vocabulary · a guide, not a pass/fail exam", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
        if (questionIndex < questions.size) {
            val question = questions[questionIndex]
            item {
                WorkspaceCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("QUESTION ${questionIndex + 1} OF ${questions.size}", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                        Text(question.band, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted)
                    }
                    LinearProgressIndicator({ (questionIndex + 1f) / questions.size }, Modifier.fillMaxWidth(), trackColor = LetsStudyColors.Mint)
                    Text(question.prompt, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                    question.options.forEachIndexed { optionIndex, option ->
                        val selected = answers.getOrElse(questionIndex) { -1 } == optionIndex
                        Surface(
                            onClick = {
                                val next = answers.toMutableList().apply { this[questionIndex] = optionIndex }
                                encodedAnswers = next.joinToString(",")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) LetsStudyColors.Mint else LetsStudyColors.Card,
                            border = BorderStroke(1.dp, if (selected) LetsStudyColors.Primary else LetsStudyColors.Border),
                        ) {
                            Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (selected) LetsStudyColors.Primary else LetsStudyColors.Muted)
                                Text(option, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { questionIndex-- }, enabled = questionIndex > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
                        Button(onClick = {
                            if (questionIndex == questions.lastIndex) onComplete(placement, answers, bankVersion, if (isProgressCheck) "PROGRESS_CHECK" else "PLACEMENT", previousLevel)
                            questionIndex++
                        }, enabled = answers.getOrElse(questionIndex) { -1 } >= 0, modifier = Modifier.weight(1f)) {
                            Text(if (questionIndex == questions.lastIndex) "See my estimate" else "Next")
                        }
                    }
                }
            }
        } else {
            item {
                WorkspaceCard(color = LetsStudyColors.Mint) {
                    Text(if (isProgressCheck) "Your progress check" else "A good place to start", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                    Text(if (isProgressCheck) placement.estimatedRange else placement.startingLevel, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${placement.correct} of ${placement.total} correct in ${placement.language}.", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                    if (!isProgressCheck && placement.estimatedRange != placement.startingLevel) {
                        Text("Your answers also show some skills around ${placement.estimatedRange.substringAfter('–')}.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                    Text("We consider your whole answer pattern, so one difficult band won’t cancel stronger answers elsewhere.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    if (isProgressCheck) {
                        Text("Your current study level: $previousLevel", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (progress.advanced) "This check supports moving your study path from $previousLevel to ${progress.level}. The next lessons will focus there."
                            else "Your study level remains $previousLevel. A single check will not lower it; you can check again after more practice.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                    } else Text(placementGuidance(placement.level, placement.startingLevel), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Text("This short check samples written vocabulary, grammar and reading. It is not a grade or an official CEFR or EF SET result, and it cannot measure speaking, listening or writing. You can change your study level at any time.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                }
            }
            item {
                WorkspaceCard {
                    Text("Your next focus", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(placement.nextFocus, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                    placement.bandScores.forEach { score ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(score.band, fontWeight = FontWeight.SemiBold)
                                Text("${score.correct}/${score.total}", color = LetsStudyColors.Muted)
                            }
                            LinearProgressIndicator({ score.correct / score.total.toFloat() }, Modifier.fillMaxWidth(), trackColor = LetsStudyColors.Mint)
                        }
                    }
                    Button(onClick = { onBuildPath(effectivePlacement) }, modifier = Modifier.fillMaxWidth()) { Text(if (isProgressCheck) "Create my ${effectivePlacement.startingLevel} study path" else "Build my ${placement.language} study path") }
                    Text("Lessons will teach the language at this level, with clear explanations and examples.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                    OutlinedButton(onClick = {
                        val latest = placementAttempts.filter { it.deletedAt == 0L && it.language == language }.maxByOrNull { it.completedAt }
                        bankVersion = (latest?.bankVersion ?: bankVersion) + 1
                        previousLevel = latest?.progressLevel?.ifBlank { latest.startingLevel }.orEmpty()
                        isProgressCheck = latest != null
                        questionIndex = 0
                        encodedAnswers = List(questions.size) { -1 }.joinToString(",")
                        showReview = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (isProgressCheck) "Take another progress check" else "Take the check again") }
                    TextButton(onClick = { showReview = !showReview }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (showReview) "Hide answer review" else "Review my answers") }
                }
            }
            if (showReview) {
                itemsForPlacement(questions.size) { index ->
                    val question = questions[index]
                    val selected = answers.getOrElse(index) { -1 }
                    WorkspaceCard {
                        Text("${index + 1}. ${question.band}", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                        Text(question.prompt, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Your answer: ${question.options.getOrElse(selected) { "Not answered" }}")
                        Text("Best answer: ${question.options[question.answerIndex]}", fontWeight = FontWeight.SemiBold)
                        Text(question.explanation, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspacePlacementResultScreen(
    attempt: StudyPlacementAttemptEntity,
    onMenu: () -> Unit,
    onBuildPath: (LanguagePlacementResult) -> Unit,
) {
    val placement = attempt.toPlacementResult()
    val answers = attempt.answers()
    var showReview by rememberSaveable(attempt.id) { mutableStateOf(false) }
    val completedDate = remember(attempt.completedAt) {
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(attempt.completedAt))
    }
    val questions = LanguagePlacementTest.questions(attempt.language, attempt.bankVersion)
    val currentLevel = attempt.progressLevel.ifBlank { attempt.startingLevel }

    WorkspacePage("Language check", onMenu) {
        item { WorkspaceTitle(if (attempt.attemptType == "PROGRESS_CHECK") "Your learning progress." else "Your starting point.", "$completedDate · ${attempt.language}") }
        item {
            WorkspaceCard(color = LetsStudyColors.Mint) {
                Text("YOUR STARTING POINT", style = MaterialTheme.typography.labelLarge, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                Text(attempt.startingLevel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${attempt.correct} of ${attempt.total} correct · estimated range ${attempt.estimatedRange}.", style = MaterialTheme.typography.bodyMedium)
                Text("This is a study recommendation based on the whole answer pattern, not a pass/fail score.", style = MaterialTheme.typography.bodyMedium)
                Text(placementGuidance(attempt.level, attempt.startingLevel), style = MaterialTheme.typography.bodyMedium)
                if (attempt.attemptType == "PROGRESS_CHECK") {
                    Text("Study level: $currentLevel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (currentLevel != attempt.previousLevel) "Your study level moved from ${attempt.previousLevel} to $currentLevel."
                        else "Your study level remains $currentLevel. Keep practising and check again when you’re ready.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("This short check samples written vocabulary, grammar and reading. It is not a grade or an official CEFR or EF SET result, and it cannot measure speaking, listening or writing. You can change your study level at any time.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
        item {
            WorkspaceCard {
                Text("Your next focus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(attempt.nextFocus, style = MaterialTheme.typography.bodyLarge)
                attempt.toPlacementResult().bandScores.forEach { score ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(score.band, fontWeight = FontWeight.SemiBold)
                            Text("${score.correct}/${score.total}", color = LetsStudyColors.Muted)
                        }
                        LinearProgressIndicator({ score.correct / score.total.toFloat() }, Modifier.fillMaxWidth(), trackColor = LetsStudyColors.Mint)
                    }
                }
                Button(onClick = { onBuildPath(placement.copy(startingLevel = currentLevel)) }, modifier = Modifier.fillMaxWidth()) { Text(if (attempt.attemptType == "PROGRESS_CHECK") "Create my $currentLevel study path" else "Build my ${attempt.language} study path") }
                TextButton(onClick = { showReview = !showReview }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (showReview) "Hide answer review" else "Review my answers")
                }
            }
        }
        if (showReview) {
            itemsForPlacement(questions.size) { index ->
                val question = questions[index]
                val selected = answers.getOrElse(index) { -1 }
                WorkspaceCard {
                    Text("${index + 1}. ${question.band}", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                    Text(question.prompt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Your answer: ${question.options.getOrElse(selected) { "Not answered" }}")
                    Text("Best answer: ${question.options[question.answerIndex]}", fontWeight = FontWeight.SemiBold)
                    Text(question.explanation, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                }
            }
        }
    }
}

private fun placementGuidance(level: String, startingLevel: String): String =
    if (startingLevel != level) {
        "You have a steady foundation through $level, with some answers at $startingLevel. Start there if you want a challenge, or choose the level that feels comfortable."
    } else {
        "Based on this short sample, $level is a useful place to begin. Move up or down whenever the material feels too easy or too difficult."
    }

private fun androidx.compose.foundation.lazy.LazyListScope.itemsForPlacement(count: Int, item: @Composable (Int) -> Unit) {
    items(count, key = { "placement-review-$it" }) { item(it) }
}
