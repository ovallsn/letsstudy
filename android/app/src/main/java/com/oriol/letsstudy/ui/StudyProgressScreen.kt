package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriol.letsstudy.data.StudyQuestionEntity
import com.oriol.letsstudy.data.StudySessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudyProgressScreen(
    sessions: List<StudySessionEntity>,
    questions: List<StudyQuestionEntity>,
    onOpenMenu: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val answered = questions.count { it.selectedOptionIndex >= 0 || it.learnerAnswer.isNotBlank() }
    val answeredChoices = questions.filter { it.selectedOptionIndex >= 0 && it.correctOptionIndex >= 0 }
    val correct = answeredChoices.count { it.selectedOptionIndex == it.correctOptionIndex }
    val accuracy = if (answeredChoices.isEmpty()) null else correct.toFloat() / answeredChoices.size
    val savedForReview = questions.count { it.markedForReview || it.reviewSuggested }
    val lessons = questions.count { it.conceptLessonJson.isNotBlank() }
    val answerProgress = if (questions.isEmpty()) 0f else answered.toFloat() / questions.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenMenu) { Icon(Icons.Outlined.Menu, contentDescription = "Open study library menu", tint = LetsStudyColors.Ink) }
                Text("Progress", style = MaterialTheme.typography.titleMedium, color = LetsStudyColors.Ink, fontWeight = FontWeight.SemiBold)
            }
            Text("Your learning, at a glance", style = MaterialTheme.typography.headlineSmall, color = LetsStudyColors.Ink, modifier = Modifier.padding(top = 13.dp))
            Text("A summary of answers and lessons saved on this phone.", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 4.dp))
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Card),
                border = BorderStroke(1.dp, LetsStudyColors.Border),
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = LetsStudyColors.Mint, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Outlined.ShowChart, contentDescription = null, tint = LetsStudyColors.Primary)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("QUESTIONS ANSWERED", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                            Text("$answered", style = MaterialTheme.typography.headlineMedium, color = LetsStudyColors.Ink, modifier = Modifier.padding(top = 2.dp))
                        }
                        Surface(color = LetsStudyColors.Mint, shape = CircleShape) {
                            Text("${sessions.size} ${if (sessions.size == 1) "study set" else "study sets"}", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { answerProgress },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = LetsStudyColors.Primary,
                        trackColor = LetsStudyColors.Mint,
                    )
                    Text("${questions.size - answered} of ${questions.size} questions still to try", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressStatCard(
                    title = "Multiple-choice accuracy",
                    value = accuracy?.let { "${(it * 100).toInt()}%" } ?: "—",
                    supporting = if (answeredChoices.isEmpty()) "No answers yet" else "$correct of ${answeredChoices.size} correct",
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.weight(1f),
                )
                ProgressStatCard(
                    title = "Saved to revisit",
                    value = savedForReview.toString(),
                    supporting = "Questions for review",
                    icon = Icons.Outlined.WorkOutline,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            ProgressStatCard(
                title = "Concept lessons",
                value = lessons.toString(),
                supporting = "Saved in your library",
                icon = Icons.Outlined.School,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (sessions.isNotEmpty()) {
            item {
                Text("Recent study sets", style = MaterialTheme.typography.titleLarge, color = LetsStudyColors.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            items(sessions.take(3), key = StudySessionEntity::id) { session ->
                Surface(
                    onClick = { onOpenSession(session.id) },
                    shape = RoundedCornerShape(17.dp),
                    color = LetsStudyColors.Card,
                    border = BorderStroke(1.dp, LetsStudyColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = LetsStudyColors.SoftBlue, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.WorkOutline, contentDescription = null, tint = LetsStudyColors.Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${session.practiceLanguage} · ${SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date(session.createdAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = LetsStudyColors.Muted,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open ${session.title}", tint = LetsStudyColors.Primary)
                    }
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Card), border = BorderStroke(1.dp, LetsStudyColors.Border)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Your progress starts with one study set", style = MaterialTheme.typography.titleMedium, color = LetsStudyColors.Ink, fontWeight = FontWeight.Bold)
                        Text("Create interview practice from a job listing to see answers and saved lessons here.", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressStatCard(
    title: String,
    value: String,
    supporting: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Card),
        border = BorderStroke(1.dp, LetsStudyColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = LetsStudyColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted, fontWeight = FontWeight.SemiBold)
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, color = LetsStudyColors.Ink, modifier = Modifier.padding(top = 11.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
