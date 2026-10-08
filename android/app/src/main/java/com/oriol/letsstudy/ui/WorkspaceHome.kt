package com.oriol.letsstudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriol.letsstudy.data.*
import com.oriol.letsstudy.domain.SourceKind
import com.oriol.letsstudy.domain.StudyInput
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkspaceHome(state: LetsStudyUiState, onMenu: () -> Unit, onTopic: (String) -> Unit, onJob: () -> Unit, onOpen: (String) -> Unit, onProgress: () -> Unit, onLevelCheck: () -> Unit, onReviewDue: () -> Unit) {
    var topic by rememberSaveable { mutableStateOf("") }
    val hour = java.time.LocalTime.now().hour
    val greeting = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
    val name = state.settings.name.trim()
    val greetingLine = if (name.isBlank()) greeting else "$greeting,\n$name"
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
    WorkspacePage("Home", onMenu) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(date, style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted, letterSpacing = 0.7.sp)
                        Text(greetingLine, style = MaterialTheme.typography.titleLarge, color = LetsStudyColors.Ink,
                            fontWeight = FontWeight.Bold, lineHeight = 27.sp)
                    }
                    Surface(onClick = onProgress, shape = RoundedCornerShape(12.dp), color = LetsStudyColors.ClayWash) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(17.dp), tint = LetsStudyColors.Clay)
                            Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
                                Text("${studyStreak(state.allQuestions, state.activity)} day", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Ink, fontWeight = FontWeight.SemiBold)
                                Text("streak", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted)
                            }
                        }
                    }
                }
                Text("Prepare for an interview or learn something new.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
        item {
            WorkspaceCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(color = LetsStudyColors.Mint, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.School, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(17.dp)) }
                    }
                    Text("STUDY, YOUR WAY", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                }
                Text("What do you want to study?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(Modifier.fillMaxWidth().border(BorderStroke(1.dp, LetsStudyColors.Border), RoundedCornerShape(12.dp))
                    .background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    BasicTextField(
                        value = topic,
                        onValueChange = { topic = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LetsStudyColors.Ink),
                        minLines = 3,
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth().heightIn(min = 68.dp), contentAlignment = Alignment.TopStart) {
                                if (topic.isBlank()) Text("An interview, EU law, or English conversation…", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted)
                                innerTextField()
                            }
                        },
                    )
                    HorizontalDivider(color = LetsStudyColors.Border)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = LetsStudyColors.Sage, modifier = Modifier.size(15.dp))
                        Text("A learning plan made for you", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted)
                        Button(onClick = { onTopic(topic.trim()) }, modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Create study plan", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeQuickAction(Modifier.weight(1f), "Paste a job offer", Icons.Outlined.WorkOutline, onJob)
                HomeQuickAction(Modifier.weight(1f), "Study a topic", Icons.Outlined.AutoAwesome, { onTopic(topic.trim()) })
            }
        }
        item { WorkspaceAction("Find my language level", "A free 20-question estimate before you choose a study path", Icons.Outlined.Translate, onLevelCheck) }
        val dueCount = state.allQuestions.count { it.isDueForReview() }
        if (dueCount > 0) item { WorkspaceAction("Review due · $dueCount", "Bring key ideas back at the right time", Icons.Outlined.Replay, onReviewDue) }
        val latest = state.sessions.maxByOrNull { maxOf(it.lastOpenedAt, it.createdAt) }
        if (latest != null) item {
            Text("Pick up where you left off", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            WorkspaceAction(latest.title, "Continue your study path", Icons.Outlined.PlayArrow) { onOpen(latest.id) }
        }
    }
}

@Composable
private fun HomeQuickAction(modifier: Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
        color = LetsStudyColors.Card, border = BorderStroke(1.dp, LetsStudyColors.Border)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(14.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), maxLines = 1, color = LetsStudyColors.Ink)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LetsStudyColors.Muted, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun WorkspaceNewStudy(onMenu: () -> Unit, onTopic: () -> Unit, onJob: () -> Unit, onImport: () -> Unit) {
    WorkspacePage("New study", onMenu) {
        item { WorkspaceTitle("Make room for\nsomething new.", "Choose a starting point. We'll help you build the habit.") }
        item { WorkspaceAction("Study a topic", "A clear path from curious to confident", Icons.Outlined.School, onTopic) }
        item { WorkspaceAction("Prepare for a job", "Practical questions for your next interview", Icons.Outlined.WorkOutline, onJob) }
        item { WorkspaceAction("Import study material", "Practice from your PDF, PPTX or text", Icons.Outlined.Description, onImport) }
    }
}

@Composable
fun WorkspaceTopicSetup(state: LetsStudyUiState, initialTopic: String, materialMode: Boolean, onMenu: () -> Unit, onImport: (android.net.Uri) -> Unit, onCreate: (StudySetup) -> Unit, initialLevel: String = "", initialLanguage: String = "", initialGoal: String = "") {
    var topic by rememberSaveable(initialTopic, materialMode) { mutableStateOf(initialTopic) }
    var goal by rememberSaveable(initialTopic, initialGoal, materialMode) { mutableStateOf(initialGoal) }
    var level by rememberSaveable(initialTopic, initialLevel, materialMode) { mutableStateOf(initialLevel.ifBlank { "Beginner" }) }
    var intensity by rememberSaveable { mutableStateOf("Balanced") }
    var target by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable(initialTopic, initialLanguage, materialMode) { mutableStateOf(initialLanguage.ifBlank { state.settings.language }) }
    var mode by rememberSaveable { mutableStateOf(state.settings.mode) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }
    LaunchedEffect(state.importedMaterial?.name) {
        if (materialMode && topic.isBlank()) topic = state.importedMaterial?.name?.substringBeforeLast('.')?.take(500).orEmpty()
    }
    WorkspacePage(if (materialMode) "Your material" else "Study a topic", onMenu) {
        item {
            WorkspaceTitle(
                "A path made\nfor you.",
                if (materialMode) "Turn your notes or presentation into focused practice."
                else "Explore a subject, build your English, or work towards a clear goal."
            )
        }
        if (materialMode) item {
            WorkspaceCard {
                OutlinedButton({ picker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/plain")) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Choose a document") }
                Text("PDF, PPTX or TXT · up to 10 MB and 100 pages/slides. Scanned PDF OCR runs on your phone for up to 30 Latin-script pages; Thai-script scans are not supported yet.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                state.importedMaterial?.let { imported ->
                    Text(imported.name, fontWeight = FontWeight.Bold)
                    Text("${imported.text.length} characters ready · preview", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary)
                    Text(imported.text.take(900), style = MaterialTheme.typography.bodySmall)
                    Text("Extracted text is limited to the first 18,000 characters. Online mode sends this text to Google; the original file stays on your phone.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                }
            }
        }
        item {
            WorkspaceCard {
                WorkspaceField(topic, { topic = it }, if (materialMode) "What is this material about?" else "What do you want to learn?", maxLength = 500)
                WorkspaceField(goal, { goal = it }, "Your goal (optional)", maxLength = 1000)
                WorkspaceSelect("Your starting point", level, listOf("Pre-A1", "A1", "A2", "B1", "B2", "C1", "Beginner", "Some experience", "Advanced", "Assess me")) { level = it }
                if (level in listOf("Pre-A1", "A1", "A2", "B1", "B2", "C1")) Text("This is an approximate written-language estimate, not a certified level.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                WorkspaceSelect("Study pace", intensity, listOf("Quick · 10 min", "Balanced", "Intensive · 40 min")) { intensity = it }
                WorkspaceField(target, { target = it }, "Target date or milestone (optional)", maxLength = 200)
                WorkspaceField(language, { language = it }, "Study language", maxLength = 80)
                WorkspaceSelect("Generation", if (mode == "ONLINE") "Fast online" else "On-device", listOf("Fast online", "On-device")) { mode = if (it == "Fast online") "ONLINE" else "OFFLINE" }
                Button({ onCreate(StudySetup(topic.trim(), goal.trim(), level, intensity, target.trim(), language.trim(), mode,
                    if (materialMode) "MATERIAL" else "TOPIC", if (materialMode) state.importedMaterial?.text.orEmpty() else "", if (materialMode) state.importedMaterial?.name.orEmpty() else "")) },
                    Modifier.fillMaxWidth(), enabled = topic.trim().length >= 3 && language.isNotBlank() && (!materialMode || state.importedMaterial != null) && !state.workspaceBusy) { Text("Build my learning path") }
            }
        }
    }
}

@Composable
fun WorkspaceJobPreparation(state: LetsStudyUiState, onMenu: () -> Unit, onAnalyze: (StudyInput, String, String) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(SourceKind.URL) }
    var source by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf(state.settings.language) }
    var mode by rememberSaveable { mutableStateOf(state.settings.mode) }
    WorkspacePage("Job preparation", onMenu) {
        item { WorkspaceTitle("Your next interview.\nYour best prepared self.", "Practice what the role asks of you, with clear explanations along the way.") }
        item {
            WorkspaceCard {
                WorkspaceSelect("Start with", if (kind == SourceKind.URL) "Job link" else "Paste description", listOf("Job link", "Paste description")) { kind = if (it == "Job link") SourceKind.URL else SourceKind.TEXT }
                WorkspaceField(source, { source = it }, if (kind == SourceKind.URL) "Public HTTPS job URL" else "Full job description", if (kind == SourceKind.TEXT) 5 else 1, 18000)
                Text("Some listings, including LinkedIn, require sign-in or block reading. Paste the description if that happens.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                WorkspaceField(language, { language = it }, "Question language", maxLength = 80)
                WorkspaceSelect("Generation", if (mode == "ONLINE") "Fast online" else "On-device", listOf("Fast online", "On-device")) { mode = if (it == "Fast online") "ONLINE" else "OFFLINE" }
                Button({ onAnalyze(StudyInput(kind, source.trim()), language.trim(), mode) }, Modifier.fillMaxWidth(), enabled = !state.isAnalyzing && language.isNotBlank() && if (kind == SourceKind.URL) source.startsWith("https://") else source.trim().length >= 100) { Text("Build my first 15 questions") }
                Text("15 practical multiple-choice questions · four options · explanations", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
    }
}
