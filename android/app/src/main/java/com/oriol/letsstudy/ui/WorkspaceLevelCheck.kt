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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriol.letsstudy.data.LanguagePlacementResult
import com.oriol.letsstudy.data.LanguagePlacementTest

@Composable
fun WorkspaceLevelCheck(onMenu: () -> Unit, onBuildPath: (LanguagePlacementResult) -> Unit) {
    var language by rememberSaveable { mutableStateOf("English") }
    var questionIndex by rememberSaveable(language) { mutableStateOf(0) }
    var encodedAnswers by rememberSaveable(language) { mutableStateOf(List(20) { -1 }.joinToString(",")) }
    var showReview by rememberSaveable(language) { mutableStateOf(false) }
    val questions = LanguagePlacementTest.questions(language)
    val answers = encodedAnswers.split(",").map { it.toIntOrNull() ?: -1 }
    val placement = LanguagePlacementTest.result(language, answers)

    WorkspacePage("Language check", onMenu) {
        item {
            WorkspaceTitle("Find your starting point.", "A short, original check to guide your language study—not a certificate or a self-rating.")
        }
        item {
            WorkspaceCard {
                WorkspaceSelect("Language to assess", language, LanguagePlacementTest.languages) {
                    language = it
                }
                Text("20 questions · reading, grammar and vocabulary", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
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
                        Button(onClick = { questionIndex++ }, enabled = answers.getOrElse(questionIndex) { -1 } >= 0, modifier = Modifier.weight(1f)) {
                            Text(if (questionIndex == questions.lastIndex) "See my estimate" else "Next")
                        }
                    }
                }
            }
        } else {
            item {
                WorkspaceCard(color = LetsStudyColors.Mint) {
                    Text("Your estimated range", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                    Text(placement.estimatedRange, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${placement.correct} of ${placement.total} correct in ${placement.language}.", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                    Text(
                        if (placement.startingLevel != placement.level) "You showed a solid foundation through ${placement.level} and some ${placement.startingLevel} skills. Start around ${placement.startingLevel} and adjust as you study."
                        else "Use this as a starting point, not a judgment. Start around ${placement.startingLevel} and adjust as you study.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    Text("This short check samples written vocabulary, grammar and reading. It does not measure speaking, listening or writing, and is not an official CEFR or EF SET result.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
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
                    Button(onClick = { onBuildPath(placement) }, modifier = Modifier.fillMaxWidth()) { Text("Build my ${placement.language} study path") }
                    Text("The level check is free and local. Creating a learning path uses the generation mode you choose on the next screen.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                    OutlinedButton(onClick = { questionIndex = 0; encodedAnswers = List(questions.size) { -1 }.joinToString(","); showReview = false }, modifier = Modifier.fillMaxWidth()) { Text("Take the check again") }
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

private fun androidx.compose.foundation.lazy.LazyListScope.itemsForPlacement(count: Int, item: @Composable (Int) -> Unit) {
    items(count, key = { "placement-review-$it" }) { item(it) }
}
