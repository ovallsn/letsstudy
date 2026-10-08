package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriol.letsstudy.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LetsStudyApp(viewModel: LetsStudyViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeSession = state.activeSession
    val selectedQuestion = state.selectedQuestion
    val questionRoute = state.practiceQuestionIds.ifEmpty { state.questions.map { it.id } }
    val selectedQuestionIndex = questionRoute.indexOf(selectedQuestion?.id).coerceAtLeast(0)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingForGeneratedSession by rememberSaveable { mutableStateOf(false) }
    var sessionIdBeforeGeneration by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = state.sessions.firstOrNull { it.id == pendingDeleteId }

    LaunchedEffect(state.activeSession?.id, state.isAnalyzing, state.errorMessage, waitingForGeneratedSession) {
        if (!waitingForGeneratedSession) return@LaunchedEffect
        val generatedSessionId = state.activeSession?.id
        when {
            generatedSessionId != null && generatedSessionId != sessionIdBeforeGeneration -> {
                destination = AppDestination.SESSION
                waitingForGeneratedSession = false
            }
            !state.isAnalyzing && state.errorMessage != null -> {
                destination = AppDestination.HOME
                waitingForGeneratedSession = false
            }
        }
    }

    val openSession: (String) -> Unit = { id ->
        destination = AppDestination.SESSION
        viewModel.openSession(id)
        scope.launch { drawerState.close() }
    }
    val startNewStudy: () -> Unit = {
        destination = AppDestination.HOME
        viewModel.closeSession()
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = LetsStudyColors.Canvas) {
                Surface(
                    color = LetsStudyColors.DeepPrimary,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = LetsStudyColors.Sun, shape = CircleShape, modifier = Modifier.size(54.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(53.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("let’sstudy", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("YOUR STUDY SPACE", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Sun)
                            }
                        }
                        Text("Every session is a step closer.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), modifier = Modifier.padding(top = 13.dp))
                    }
                }
                Text("YOUR LIBRARY", modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = startNewStudy,
                    shape = RoundedCornerShape(17.dp),
                    color = LetsStudyColors.Mint,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = LetsStudyColors.Primary, shape = CircleShape, modifier = Modifier.size(38.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = Color.White) }
                        }
                        Spacer(Modifier.width(11.dp))
                        Text("Prepare for a job", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LetsStudyColors.Primary)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 17.dp), color = LetsStudyColors.Border)
                Text("SAVED STUDIES · ${state.sessions.size}", modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (state.sessions.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = LetsStudyColors.Card,
                                border = BorderStroke(1.dp, LetsStudyColors.Border),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Column(Modifier.padding(17.dp)) {
                                    Surface(color = LetsStudyColors.Mint, shape = CircleShape, modifier = Modifier.size(42.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(21.dp))
                                        }
                                    }
                                    Text("Your saved sets will appear here", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 11.dp))
                                    Text("Start with a job link or a pasted description.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    items(state.sessions, key = { it.id }) { session ->
                        Row(modifier = Modifier.padding(start = 14.dp, end = 7.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { openSession(session.id) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (activeSession?.id == session.id) LetsStudyColors.Mint else LetsStudyColors.Card,
                                border = BorderStroke(1.dp, if (activeSession?.id == session.id) LetsStudyColors.Primary.copy(alpha = 0.35f) else LetsStudyColors.Border),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(19.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${session.practiceLanguage} · ${SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(session.createdAt))}", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 3.dp))
                                    }
                                }
                            }
                            IconButton(onClick = { pendingDeleteId = session.id }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${session.title}", tint = LetsStudyColors.Muted)
                            }
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = LetsStudyColors.Canvas,
            bottomBar = {
                AppBottomNavigation(destination = destination) { next ->
                    if (selectedQuestion != null) viewModel.closeQuestion()
                    if (next == AppDestination.NEW_STUDY) {
                        destination = AppDestination.HOME
                        viewModel.closeSession()
                    } else {
                        destination = next
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when {
                    selectedQuestion != null && activeSession != null -> key(selectedQuestion.id) {
                        PracticeScreen(
                            session = activeSession,
                            question = selectedQuestion,
                            isSubmitting = state.isSubmitting,
                            onBack = viewModel::closeQuestion,
                            onSubmit = viewModel::submitAnswer,
                            onSelectChoice = viewModel::selectChoice,
                            onResetChoice = viewModel::resetChoice,
                            questionNumber = selectedQuestionIndex + 1,
                            questionCount = questionRoute.size,
                            onPreviousQuestion = { viewModel.moveQuestion(-1) },
                            onNextQuestion = { viewModel.moveQuestion(1) },
                            onToggleReview = { viewModel.toggleReview(selectedQuestion) },
                            onDismissError = viewModel::clearError,
                            errorMessage = state.errorMessage,
                            isConceptLessonLoading = selectedQuestion.id in state.conceptLessonLoadingQuestionIds,
                            conceptLessonErrorCode = state.conceptLessonErrorCodes[selectedQuestion.id],
                            onRequestConceptLesson = viewModel::requestConceptLesson,
                        )
                    }

                    destination == AppDestination.LIBRARY -> StudyLibraryScreen(
                        sessions = state.sessions,
                        activeSessionId = activeSession?.id,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onOpenSession = openSession,
                        onDeleteSession = { pendingDeleteId = it },
                    )

                    destination == AppDestination.PROGRESS -> StudyProgressScreen(
                        sessions = state.sessions,
                        questions = state.allQuestions,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onOpenSession = openSession,
                    )

                    destination == AppDestination.SESSION && activeSession != null -> StudyScreen(
                        session = activeSession,
                        questions = state.questions,
                        isGenerating = state.isGenerating,
                        offlineAvailable = state.modelState is com.oriol.letsstudy.ai.ModelState.Available || state.modelState is com.oriol.letsstudy.ai.ModelState.Ready,
                        generationProgress = state.generationProgress,
                        errorMessage = state.errorMessage,
                        onOpenQuestion = viewModel::selectQuestion,
                        onContinue = viewModel::generateMore,
                        onBack = { viewModel.closeSession(); destination = AppDestination.LIBRARY },
                        onDismissError = viewModel::clearError,
                        onToggleReview = viewModel::toggleReview,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                    )

                    else -> HomeScreen(
                        isAnalyzing = state.isAnalyzing,
                        modelState = state.modelState,
                        downloadState = state.modelDownloadState,
                        generationProgress = state.generationProgress,
                        errorMessage = state.errorMessage,
                        latestSession = state.sessions.maxByOrNull { it.createdAt },
                        onAnalyze = { input, language, mode ->
                            sessionIdBeforeGeneration = activeSession?.id
                            waitingForGeneratedSession = true
                            viewModel.closeSession()
                            viewModel.analyze(input, language, mode)
                        },
                        onOpenSession = openSession,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onDismissError = viewModel::clearError,
                        onDownloadOnWifi = viewModel::downloadModel,
                        onDownloadOnMobileData = viewModel::downloadModelOnMobileData,
                        onCancelModelDownload = viewModel::cancelModelDownload,
                    )
                }
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = LetsStudyColors.Card,
            icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = LetsStudyColors.Clay) },
            title = { Text("Delete study session?") },
            text = { Text("“${pendingDelete.title}” and all its questions will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = pendingDelete.id
                        pendingDeleteId = null
                        if (activeSession?.id == id) destination = AppDestination.LIBRARY
                        viewModel.deleteSession(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } },
        )
    }
}
