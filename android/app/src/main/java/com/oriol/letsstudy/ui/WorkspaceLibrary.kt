package com.oriol.letsstudy.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oriol.letsstudy.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WorkspaceLibrary(state: LetsStudyUiState, kind: String, onMenu: () -> Unit, onOpen: (String) -> Unit, onDelete: (String) -> Unit) {
    var search by rememberSaveable(kind) { mutableStateOf("") }
    var filter by rememberSaveable(kind) { mutableStateOf("All studies") }
    val title = when (kind) { "JOB" -> "Job preparation"; "HISTORY" -> "Study history"; else -> "My studies" }
    val sessions = state.sessions.filter { session ->
        val questions = state.allQuestions.filter { it.sessionId == session.id }
        val complete = questions.isNotEmpty() && questions.all { it.isAnswered() }
        (kind != "JOB" || session.studyKind == "JOB") &&
            (kind != "HISTORY" || session.lastOpenedAt > 0 || questions.any { it.isAnswered() }) &&
            (search.isBlank() || listOf(session.title, session.summary, session.sourceLabel).any { it.contains(search, true) }) &&
            when (filter) { "Active" -> !complete; "Completed" -> complete; "Job preparation" -> session.studyKind == "JOB"; else -> true }
    }.let { values -> if (kind == "HISTORY") values.sortedByDescending { maxOf(it.lastOpenedAt, state.allQuestions.filter { q -> q.sessionId == it.id }.maxOfOrNull { q -> q.answeredAt } ?: 0) } else values }
    WorkspacePage(title, onMenu) {
        item { WorkspaceTitle(if (kind == "JOB") "Walk in prepared." else if (kind == "HISTORY") "Look how far\nyou've come." else "Your curiosity,\nall in one place.", "Continue, revisit or make room for your next goal.") }
        item { WorkspaceField(search, { search = it }, "Search your studies", maxLength = 250) }
        if (kind == "ALL") item { WorkspaceSelect("Show", filter, listOf("All studies", "Active", "Completed", "Job preparation")) { filter = it } }
        if (sessions.isEmpty()) item { WorkspaceEmpty("A little space for something new", "Your saved studies will appear here. Use New to start a topic, import material or prepare for a job.") }
        items(sessions, key = { it.id }) { session ->
            val questions = state.allQuestions.filter { it.sessionId == session.id }
            val answered = questions.count { it.isAnswered() }
            WorkspaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.studyKind.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary)
                    IconButton({ onDelete(session.id) }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${session.title}", tint = LetsStudyColors.Muted) }
                }
                Text(session.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(session.summary, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                LinearProgressIndicator(progress = { if (questions.isEmpty()) 0f else answered.toFloat() / questions.size }, Modifier.fillMaxWidth(), trackColor = LetsStudyColors.Mint)
                Text("$answered / ${questions.size} questions answered · ${session.practiceLanguage}", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                if (kind == "HISTORY") Text("Last studied ${epochDay(maxOf(session.lastOpenedAt, questions.maxOfOrNull { it.answeredAt } ?: 0, session.createdAt)).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}", style = MaterialTheme.typography.labelSmall)
                TextButton({ onOpen(session.id) }) { Text(if (answered == 0) "Open study path" else "Continue studying"); Icon(Icons.Outlined.PlayArrow, null) }
            }
        }
    }
}

@Composable
fun WorkspaceSaved(state: LetsStudyUiState, onMenu: () -> Unit, onOpen: (StudyQuestionEntity) -> Unit, onToggle: (StudyQuestionEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val saved = state.allQuestions.filter { (it.markedForReview || it.reviewSuggested) && (query.isBlank() || it.prompt.contains(query, true) || it.topic.contains(query, true)) }
    WorkspacePage("Saved questions", onMenu) {
        item { WorkspaceTitle("Keep the ideas\nyou want to revisit.", "A personal collection of questions and explanations.") }
        item { WorkspaceField(query, { query = it }, "Search saved questions", maxLength = 250) }
        if (saved.isEmpty()) item { WorkspaceEmpty("Your collection starts here", "Save a question during practice to find it here again.") }
        items(saved, key = { it.id }) { question ->
            WorkspaceCard {
                Text(question.topic, style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary)
                Text(question.prompt, style = MaterialTheme.typography.titleMedium)
                Text(state.sessions.firstOrNull { it.id == question.sessionId }?.title.orEmpty(), style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                Row {
                    TextButton({ onOpen(question) }) { Text("Study again") }
                    Spacer(Modifier.weight(1f))
                    TextButton({ onToggle(question) }) { Text("Remove saved mark") }
                }
            }
        }
    }
}

@Composable
fun WorkspaceReviewDue(state: LetsStudyUiState, onMenu: () -> Unit, onOpen: (StudyQuestionEntity) -> Unit) {
    val due = state.allQuestions.filter { it.isDueForReview() }.sortedBy { it.nextReviewAt }
    WorkspacePage("Review due", onMenu) {
        item { WorkspaceTitle("Bring it back to mind.", "Questions return after increasing intervals so the ideas stay easier to recall.") }
        if (due.isEmpty()) item { WorkspaceEmpty("You're all caught up", "Answered questions will appear here when their next review date arrives.") }
        items(due, key = { it.id }) { question ->
            WorkspaceCard {
                Text(question.topic, style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary)
                Text(question.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.sessions.firstOrNull { it.id == question.sessionId }?.title.orEmpty(), style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                val interval = question.reviewIntervalDays.coerceAtLeast(1)
                Text("Last interval · $interval ${if (interval == 1) "day" else "days"}", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted)
                TextButton({ onOpen(question) }) { Text("Review this question"); Icon(Icons.Outlined.PlayArrow, null) }
            }
        }
    }
}

@Composable
fun WorkspaceProgress(
    state: LetsStudyUiState,
    onMenu: () -> Unit,
    onOpenQuestion: (StudyQuestionEntity) -> Unit,
    onOpenPlacement: (String) -> Unit,
    onReviewDue: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    val answered = state.allQuestions.filter { it.isAnswered() }
    val scored = answered.filter { it.score >= 0 || it.selectedOptionIndex >= 0 && it.correctOptionIndex >= 0 }
    val correct = scored.count { if (it.score >= 0) it.score >= 80 else it.selectedOptionIndex == it.correctOptionIndex }
    val days = (6L downTo 0L).map { LocalDate.now().minusDays(it) }
    val counts = days.map { day -> answered.count { it.answeredAt > 0 && epochDay(it.answeredAt) == day } }
    val maximum = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val activePaths = state.sessions.count { session -> state.allQuestions.none { it.sessionId == session.id } || state.allQuestions.any { it.sessionId == session.id && !it.isAnswered() } }
    WorkspacePage("Your progress", onMenu) {
        item { WorkspaceTitle("Little steps.\nReal progress.", "Every bit of practice counts. Here's yours.") }
        state.placementAttempts.firstOrNull()?.let { latest ->
            item {
                WorkspaceCard(color = LetsStudyColors.Mint) {
                    Text("LATEST LANGUAGE CHECK", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                    Text(latest.estimatedRange, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${latest.correct} of ${latest.total} correct in ${latest.language}", style = MaterialTheme.typography.bodyLarge)
                    Text("${latest.startingLevel} starting point · ${epochDay(latest.completedAt).format(DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH))}", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                    OutlinedButton(onClick = { onOpenPlacement(latest.id) }, modifier = Modifier.fillMaxWidth()) { Text("View result and answers") }
                }
            }
            if (state.placementAttempts.size > 1) {
                item { Text("Previous language checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(state.placementAttempts.drop(1), key = { it.id }) { attempt ->
                    WorkspaceAction(
                        "${attempt.language} · ${attempt.estimatedRange}",
                        "${attempt.correct}/${attempt.total} correct · ${epochDay(attempt.completedAt).format(DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH))}",
                        Icons.Outlined.Translate,
                    ) { onOpenPlacement(attempt.id) }
                }
            }
        }
        item {
            WorkspaceCard(color = LetsStudyColors.Mint) {
                val streak = studyStreak(state.allQuestions, state.activity)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = LetsStudyColors.Clay)
                    Text(if (streak == 0) "Start a study streak" else "$streak-day study streak", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text("${state.activity.sumOf { it.durationSeconds } / 60} minutes of focused study", color = LetsStudyColors.Muted)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProgressMetricCard("Answered", "${answered.size}", Modifier.weight(1f))
                ProgressMetricCard(
                    "Mastered answers",
                    if (scored.isEmpty()) "—" else "${100 * correct / scored.size}%",
                    Modifier.weight(1f),
                )
            }
            Text("Mastered means a correct objective answer, a ‘Know it’ rating or AI feedback of 80+.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            WorkspaceCard {
                Text("$activePaths active study paths", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Paths with practice still to complete", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
        val dueCount = state.allQuestions.count { it.isDueForReview() }
        if (dueCount > 0) item { WorkspaceAction("$dueCount questions ready to review", "Open your spaced-repetition queue", Icons.Outlined.Replay, onReviewDue) }
        item { WorkspaceAction("Community leaderboard", "Join with a nickname and track this week's study points", Icons.Outlined.EmojiEvents, onLeaderboard) }
        if (scored.isNotEmpty()) item {
            WorkspaceCard {
                Text("Topic strengths", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                scored.groupBy { it.topic }.entries.sortedBy { it.key }.forEach { (topic, questions) ->
                    val mastered = questions.count { if (it.score >= 0) it.score >= 80 else it.selectedOptionIndex == it.correctOptionIndex }
                    Text(topic, style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { mastered.toFloat() / questions.size }, modifier = Modifier.fillMaxWidth())
                    Text("$mastered/${questions.size} mastered", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                }
            }
        }
        item {
            WorkspaceCard {
                Text("Your last seven days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Questions last answered each day", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                Row(Modifier.fillMaxWidth().height(145.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    days.forEachIndexed { index, day ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${counts[index]}", style = MaterialTheme.typography.labelSmall)
                            Box(Modifier.fillMaxWidth().height((8 + 85 * counts[index] / maximum).dp).background(if (counts[index] == 0) LetsStudyColors.Border else LetsStudyColors.Primary, RoundedCornerShape(6.dp)))
                            Text(day.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted)
                        }
                    }
                }
            }
        }
        val weak = scored.filter { if (it.score >= 0) it.score < 60 else it.selectedOptionIndex != it.correctOptionIndex }
        if (weak.isNotEmpty()) {
            item { Text("Worth another look", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(weak.take(8), key = { it.id }) { q -> WorkspaceAction(q.topic, q.prompt.take(140), Icons.Outlined.Replay) { onOpenQuestion(q) } }
        } else item { WorkspaceEmpty(if (answered.isEmpty()) "Your progress is waiting" else "Keep exploring", if (answered.isEmpty()) "Answer a question to start seeing your learning here." else "Try a fresh round or revisit a saved question.") }
    }
}

@Composable
private fun ProgressMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    WorkspaceCard(modifier.fillMaxHeight()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.TopStart) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = LetsStudyColors.Muted)
        }
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun WorkspaceNotifications(state: LetsStudyUiState, onMenu: () -> Unit, onReviewDue: () -> Unit, onManageReminders: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var notificationsEnabled by remember(context) {
        mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val dueCount = state.allQuestions.count { it.isDueForReview() }
    val hasReminders = state.settings.dailyReminder || state.settings.weeklySummary

    WorkspacePage("Notifications", onMenu) {
        item { WorkspaceTitle("A little nudge,\nwhen it helps.", "Choose reminders for your study routine and pick up due reviews here.") }
        item {
            WorkspaceCard(color = if (notificationsEnabled) LetsStudyColors.Mint else LetsStudyColors.ClayWash) {
                Text("STUDY REMINDERS", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !notificationsEnabled -> "Notifications are turned off on this phone."
                        hasReminders -> "Your study reminders are ready."
                        else -> "No reminders are scheduled yet."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.settings.dailyReminder) Text("Daily practice · %02d:%02d".format(state.settings.reminderHour, state.settings.reminderMinute), color = LetsStudyColors.Muted)
                if (state.settings.weeklySummary) Text("Weekly learning summary", color = LetsStudyColors.Muted)
                OutlinedButton(onClick = onManageReminders, modifier = Modifier.fillMaxWidth()) { Text("Manage reminder schedule") }
                if (!notificationsEnabled) {
                    Button(
                        onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open Android notification settings") }
                }
            }
        }
        if (dueCount > 0) {
            item { Text("Ready when you are", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { WorkspaceAction("$dueCount questions ready to review", "Revisit them while they're fresh", Icons.Outlined.Replay, onReviewDue) }
        } else {
            item { WorkspaceEmpty("Nothing is waiting", "When a saved question is due for review, you'll find it here.") }
        }
    }
}

@Composable
fun WorkspaceSettings(state: LetsStudyUiState, viewModel: LetsStudyViewModel, onMenu: () -> Unit) {
    val context = LocalContext.current
    val settings = state.settings
    var clearConfirm by remember { mutableStateOf(false) }
    var showNotices by remember { mutableStateOf(false) }
    val notices = remember(context) { context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { name ->
        "$name\n\n" + context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
    } }
    var notificationGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    var pendingReminder by remember { mutableStateOf<Int?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
        val latest = viewModel.uiState.value.settings
        if (granted) pendingReminder?.let { index -> viewModel.saveSettings(if (index == 0) latest.copy(dailyReminder = true) else latest.copy(weeklySummary = true)) }
        pendingReminder = null
    }
    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::saveProfilePhoto)
    }
    WorkspacePage("Settings", onMenu) {
        item { WorkspaceTitle("Your space.\nYour pace.", "A few preferences to make studying feel like you.") }
        item { AccountSyncPanel(state.account, settings.name, viewModel) }
        item {
            WorkspaceCard {
                Text("About you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (state.account.email == null) {
                    WorkspaceField(settings.name, { viewModel.saveSettings(settings.copy(name = it)) }, "Display name on this device", maxLength = 80)
                } else {
                    Text("Your account display name and username are managed in Account & sync above.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                }
                if (settings.photoPath.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WorkspaceAvatar(settings.avatarId, 52.dp, photoPath = settings.photoPath)
                        Column(Modifier.weight(1f)) {
                            Text("Your photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Stored privately on this phone", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                        }
                        TextButton({ viewModel.saveSettings(settings.copy(photoPath = "")) }) { Text("Remove") }
                    }
                }
                OutlinedButton(
                    onClick = { profilePhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (settings.photoPath.isBlank()) "Choose a profile photo" else "Change profile photo") }
                if (state.errorMessage != null) Text(state.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                StudyAvatarPicker(
                    selectedAvatarId = if (state.account.email != null && state.account.avatarLoaded) state.account.avatarId else settings.avatarId,
                    onSelected = { avatarId ->
                        viewModel.saveSettings(settings.copy(avatarId = avatarId, photoPath = ""))
                        if (state.account.email != null) viewModel.updateAccountAvatar(avatarId)
                    },
                )
                Text(
                    "Photos stay private on this phone. Illustrated avatars sync with your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LetsStudyColors.Muted,
                )
                WorkspaceField(settings.language, { viewModel.saveSettings(settings.copy(language = it)) }, "Default study language", maxLength = 80)
                WorkspaceSelect("Preferred study mode", if (settings.mode == "ONLINE") "Fast online" else "On-device", listOf("Fast online", "On-device")) { viewModel.saveSettings(settings.copy(mode = if (it == "Fast online") "ONLINE" else "OFFLINE")) }
                Text("Fast online is quicker. On-device study may take longer and requires a one-time model download.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
        item {
            WorkspaceCard {
                Text("A gentle nudge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOf("Daily study reminder" to settings.dailyReminder, "Weekly summary" to settings.weeklySummary).forEachIndexed { index, (label, enabled) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        Switch(enabled, { next ->
                            if (next && !notificationGranted && Build.VERSION.SDK_INT >= 33) {
                                pendingReminder = index
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else viewModel.saveSettings(if (index == 0) settings.copy(dailyReminder = next) else settings.copy(weeklySummary = next))
                        })
                    }
                }
                OutlinedButton({ TimePickerDialog(context, { _, hour, minute -> viewModel.saveSettings(settings.copy(reminderHour = hour, reminderMinute = minute)) }, settings.reminderHour, settings.reminderMinute, true).show() }, Modifier.fillMaxWidth()) { Text("Reminder time · %02d:%02d".format(settings.reminderHour, settings.reminderMinute)) }
                Text(if (notificationGranted) "Android may adjust delivery to conserve battery." else "Notifications are disabled. Enable them in Android settings to receive reminders.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
        item { ModelSetupCard(state.modelState, state.modelDownloadState, viewModel::downloadModel, viewModel::downloadModelOnMobileData, viewModel::cancelModelDownload) }
        item {
            WorkspaceCard {
                Text("Privacy & your data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Your studies are saved on this phone. If you enable account sync, your profile and study library are stored in Firebase. Fast Online sends relevant study material and submitted answers or tutor messages to Google Gemini. On-device study generation uses the model installed on your phone; tutor messages and deeper lessons still use Google Gemini. Scanned PDFs are read on your phone, though Google ML Kit may send service metrics. The optional community board shows your chosen name and weekly points after you join; your email and study content stay private.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                TextButton({ clearConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete all study data") }
                TextButton({ showNotices = true }) { Text("Third-party licenses") }
            }
        }
    }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("Delete all studies?") }, text = { Text(if (state.account.email == null) "This removes every study, answer, tutor conversation and progress record from this phone. It can't be undone." else "This removes every study, answer, tutor conversation and progress record from this phone and your synced devices. An internet connection is needed to confirm the latest cloud copy first.") },
        confirmButton = { TextButton({ viewModel.clearAllStudies(); clearConfirm = false }) { Text("Delete all") } }, dismissButton = { TextButton({ clearConfirm = false }) { Text("Keep my studies") } })
    if (showNotices) AlertDialog(onDismissRequest = { showNotices = false }, title = { Text("Third-party licenses") }, text = { Text(notices, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton({ showNotices = false }) { Text("Close") } })
}

@Composable
private fun AccountSyncPanel(account: StudyAccountState, localDisplayName: String, viewModel: LetsStudyViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var deletePassword by remember { mutableStateOf("") }
    var createMode by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var profileEditing by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    LaunchedEffect(account.email, account.displayName, account.username, localDisplayName) {
        if (account.email == null) {
            profileEditing = false
            if (!createMode) {
                displayName = localDisplayName.trim()
                username = ""
            }
        } else {
            createMode = false
            if (!profileEditing && account.displayName != null) displayName = account.displayName
            if (!profileEditing && account.username != null) username = account.username
            if (displayName.isBlank()) displayName = localDisplayName.trim()
            password = ""
        }
    }
    LaunchedEffect(account.message) {
        if (account.message == "Your learner profile is updated.") profileEditing = false
    }
    WorkspaceCard {
        Text("Account & sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (account.email != null) {
            Text(account.email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (account.isProfileLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading your learner profile…", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            } else if (account.profileComplete && !profileEditing) {
                Text(account.displayName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("@${account.username.orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted)
                Text("Your profile is not listed publicly. When you save a username, Let’sStudy checks whether it is taken without revealing who owns it.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                OutlinedButton(onClick = { profileEditing = true }, enabled = !account.isBusy, modifier = Modifier.fillMaxWidth()) { Text("Edit learner profile") }
            } else {
                Text(if (account.profileComplete) "Update your learner profile" else "Complete your learner profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (account.profileComplete) "Your display name and username are private. Choose a name that feels like you." else "Choose the name and username you want to use in Let’sStudy. You can finish this later; your saved studies remain available.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                LearnerIdentityFields(displayName, { displayName = it }, username, { username = it }, enabled = !account.isBusy)
                if (account.profileComplete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            displayName = account.displayName.orEmpty()
                            username = account.username.orEmpty()
                            profileEditing = false
                        }, enabled = !account.isBusy, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = { viewModel.updateAccountProfile(displayName, username) }, enabled = profileIsValid(displayName, username) && !account.isBusy, modifier = Modifier.weight(1f)) { Text("Save profile") }
                    }
                } else {
                    Button(onClick = { viewModel.completeAccountProfile(displayName, username) }, enabled = profileIsValid(displayName, username) && !account.isBusy && !account.isProfileLoading, modifier = Modifier.fillMaxWidth()) {
                        if (account.isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save learner profile")
                    }
                }
            }
            val statusText = when (account.status) {
                CloudSyncStatus.SIGNED_OUT -> "Not signed in"
                CloudSyncStatus.CONNECTING -> "Connecting to your private study library…"
                CloudSyncStatus.SYNCING -> "Syncing your studies…"
                CloudSyncStatus.SYNCED -> "Your studies are up to date"
                CloudSyncStatus.OFFLINE -> "No connection · saved studies will sync when your connection returns"
                CloudSyncStatus.ERROR -> "Cloud sync needs attention"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Sync, contentDescription = null, tint = if (account.status == CloudSyncStatus.SYNCED) LetsStudyColors.Primary else LetsStudyColors.Muted)
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
            account.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            account.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Primary) }
            Button(onClick = viewModel::synchronizeAccount, enabled = !account.isBusy, modifier = Modifier.fillMaxWidth()) {
                if (account.isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sync now")
            }
            OutlinedButton(onClick = viewModel::signOut, enabled = !account.isBusy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            TextButton(onClick = { showDeleteConfirmation = true }, enabled = !account.isBusy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete account and cloud data") }
        } else {
            Text("You can study as a guest. Create an account only if you want your private study library to sync across devices.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            if (createMode) {
                LearnerIdentityFields(displayName, { displayName = it }, username, { username = it }, enabled = !account.isBusy)
                Text("Your profile is not listed publicly. A future leaderboard would require separate consent.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
            WorkspaceField(email, { email = it }, "Email address", maxLength = 254)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(1024) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Text(if (createMode) "Use at least 8 characters. Firebase manages your password securely." else "Your password is handled by Firebase Authentication and is not saved in Let’sStudy.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            if (createMode) {
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !account.isBusy)
                    Text("I agree to store my profile and sync study material, questions, answers, saved lessons, tutor conversations and progress to my private Firebase account. I can delete this data from Settings.", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = { if (createMode) viewModel.createAccount(displayName, username, email, password) else viewModel.signIn(email, password) },
                enabled = (!createMode || consent) && (!createMode || profileIsValid(displayName, username)) && !account.isBusy && email.contains('@') && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (account.isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (createMode) "Create account" else "Sign in and sync")
            }
            if (!createMode) TextButton(onClick = { viewModel.sendPasswordReset(email) }, enabled = !account.isBusy && email.contains('@')) { Text("Forgot password?") }
            TextButton(onClick = {
                val enteringCreateMode = !createMode
                createMode = enteringCreateMode
                consent = false
                password = ""
                if (enteringCreateMode && displayName.isBlank()) {
                    displayName = localDisplayName.trim()
                }
            }, enabled = !account.isBusy) {
                Text(if (createMode) "Already have an account? Sign in" else "New to Let’sStudy? Create an account")
            }
            account.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            account.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Primary) }
        }
    }
    if (showDeleteConfirmation) AlertDialog(
        onDismissRequest = { showDeleteConfirmation = false; deletePassword = "" },
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This permanently deletes your Firebase account and all synced studies, answers, conversations and progress. This can't be undone.")
                OutlinedTextField(value = deletePassword, onValueChange = { deletePassword = it.take(1024) }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm with your password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                account.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.deleteAccount(deletePassword); deletePassword = ""; showDeleteConfirmation = false }, enabled = deletePassword.isNotBlank() && !account.isBusy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete permanently") }
        },
        dismissButton = { TextButton(onClick = { showDeleteConfirmation = false; deletePassword = "" }, enabled = !account.isBusy) { Text("Cancel") } },
    )
}

@Composable
private fun LearnerIdentityFields(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    enabled: Boolean,
) {
    WorkspaceField(displayName, onDisplayNameChange, "Display name", maxLength = 80, enabled = enabled)
    WorkspaceField(username, onUsernameChange, "Username", maxLength = 20, enabled = enabled)
    val usernameError = if (username.isBlank()) null else runCatching { UsernamePolicy.normalize(username) }.exceptionOrNull()?.message
    if (usernameError != null) {
        Text(usernameError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    } else {
        Text("Use 3–20 letters, numbers or single underscores between words. Offensive terms and reserved names are blocked. Usernames stay private unless you join the community board.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
    }
}

private fun profileIsValid(displayName: String, username: String) =
    displayName.trim().isNotEmpty() && displayName.trim().length <= 80 && UsernamePolicy.isAllowed(username)
