package com.oriol.letsstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import android.content.Intent
import androidx.core.net.toUri
import com.oriol.letsstudy.LegalLinks
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oriol.letsstudy.LearnerAgeGroup
import com.oriol.letsstudy.YouthCourseCatalog
import com.oriol.letsstudy.YouthProgressStore

@Composable
fun AgeSelectionScreen(onSelect: (LearnerAgeGroup) -> Unit) {
    val context = LocalContext.current
    var tooYoung by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = LetsStudyColors.Canvas) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Spacer(Modifier.height(24.dp)) }
            item { Text("Welcome to LetsStudy", style = MaterialTheme.typography.headlineMedium, color = LetsStudyColors.Ink) }
            item { Text("Choose your age range so we can show the right learning experience. We do not ask for your birthday.", style = MaterialTheme.typography.bodyLarge) }
            item {
                WorkspaceCard {
                    listOf(
                        "9–12" to LearnerAgeGroup.AGE_9_12,
                        "13–17" to LearnerAgeGroup.AGE_13_17,
                        "18 or older" to LearnerAgeGroup.ADULT,
                    ).forEach { (label, group) ->
                        OutlinedButton(onClick = { onSelect(group) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(label) }
                    }
                    TextButton(onClick = { tooYoung = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Under 9") }
                }
            }
            if (tooYoung) item { Text("LetsStudy currently starts at age 9. Ask an adult to help you find an app made for younger learners.", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted) }
            item { TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, LegalLinks.PRIVACY.toUri())) }) { Text("Privacy policy") } }
        }
    }
}

@Composable
fun YouthStudyApp(group: LearnerAgeGroup) {
    val context = LocalContext.current
    val store = remember(group) { YouthProgressStore(context, group) }
    val courses = remember(group) { YouthCourseCatalog.forGroup(group) }
    var progress by remember(group) { mutableStateOf(store.read()) }
    var courseId by rememberSaveable(group) { mutableStateOf<String?>(null) }
    var questionIndex by rememberSaveable(courseId) { mutableIntStateOf(0) }
    var chosenIndex by rememberSaveable(courseId, questionIndex) { mutableIntStateOf(-1) }
    var clearConfirm by remember { mutableStateOf(false) }
    val course = courses.firstOrNull { it.id == courseId }

    Scaffold(containerColor = LetsStudyColors.Canvas) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(if (course == null) "Learn a little every day" else course.title, style = MaterialTheme.typography.headlineSmall)
                Text("Your lessons and progress stay on this phone. There is no account, chat or public sharing in this experience.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
            if (course == null) {
                courses.forEach { entry ->
                    item(key = entry.id) {
                        WorkspaceCard {
                            Text(entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(entry.description)
                            val answered = entry.questions.count { it.id in progress.answers }
                            Text("$answered of ${entry.questions.size} questions explored", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                            Button(onClick = { courseId = entry.id; questionIndex = entry.questions.indexOfFirst { it.id !in progress.answers }.coerceAtLeast(0) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (answered == 0) "Start lesson" else "Continue lesson")
                            }
                        }
                    }
                }
                item { TextButton(onClick = { clearConfirm = true }) { Text("Erase my progress") } }
                item { TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, LegalLinks.PRIVACY.toUri())) }) { Text("Privacy policy") } }
            } else {
                val question = course.questions[questionIndex.coerceIn(0, course.questions.lastIndex)]
                item {
                    TextButton(onClick = { courseId = null }, contentPadding = PaddingValues(0.dp)) { Text("Back to lessons") }
                    Text("Question ${questionIndex + 1} of ${course.questions.size}", style = MaterialTheme.typography.labelLarge, color = LetsStudyColors.Primary)
                }
                item {
                    WorkspaceCard {
                        Text(question.prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        question.options.forEachIndexed { index, option ->
                            OutlinedButton(
                                onClick = {
                                    chosenIndex = index
                                    progress = progress.answer(question.id, index == question.answerIndex)
                                    store.save(progress)
                                },
                                enabled = chosenIndex < 0,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) { Text(option, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
                        }
                        if (chosenIndex >= 0) {
                            HorizontalDivider()
                            Text(if (chosenIndex == question.answerIndex) "That's right." else "Good try. ${question.options[question.answerIndex]} is the answer.", fontWeight = FontWeight.Bold)
                            Text(question.explanation)
                            Button(onClick = {
                                if (questionIndex == course.questions.lastIndex) courseId = null
                                else questionIndex++
                                chosenIndex = -1
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (questionIndex == course.questions.lastIndex) "Back to lessons" else "Next question") }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { questionIndex--; chosenIndex = -1 }, enabled = questionIndex > 0) { Text("Previous") }
                        TextButton(onClick = { questionIndex++; chosenIndex = -1 }, enabled = questionIndex < course.questions.lastIndex) { Text("Skip") }
                    }
                }
            }
        }
    }
    if (clearConfirm) AlertDialog(
        onDismissRequest = { clearConfirm = false },
        title = { Text("Erase progress?") },
        text = { Text("This removes your saved answers from this phone. You can start the lessons again.") },
        confirmButton = { TextButton(onClick = { store.clear(); progress = store.read(); clearConfirm = false }) { Text("Erase") } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Keep progress") } },
    )
}
