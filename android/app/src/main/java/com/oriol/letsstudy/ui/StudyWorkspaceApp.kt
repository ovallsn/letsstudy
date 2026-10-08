package com.oriol.letsstudy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriol.letsstudy.R
import kotlinx.coroutines.launch

@Composable
fun StudyWorkspaceApp(viewModel: LetsStudyViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var initialTopic by rememberSaveable { mutableStateOf("") }
    var initialLevel by rememberSaveable { mutableStateOf("") }
    var initialLanguage by rememberSaveable { mutableStateOf("") }
    var initialGoal by rememberSaveable { mutableStateOf("") }
    var initialKind by rememberSaveable { mutableStateOf("TOPIC") }
    var checkLanguage by rememberSaveable { mutableStateOf("") }
    var checkCurrentLevel by rememberSaveable { mutableStateOf("") }
    var checkRunId by rememberSaveable { mutableStateOf(0) }
    var showTutor by rememberSaveable { mutableStateOf(false) }
    var tutorQuestion by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var placementAttemptId by rememberSaveable { mutableStateOf<String?>(null) }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var awaitingNew by rememberSaveable { mutableStateOf(false) }
    var sessionBefore by rememberSaveable { mutableStateOf<String?>(null) }
    var onlineAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val moreRoutes = setOf(AppDestination.HISTORY, AppDestination.SAVED, AppDestination.REVIEW_DUE, AppDestination.LANGUAGE_CHECK, AppDestination.NOTIFICATIONS, AppDestination.LEADERBOARD, AppDestination.SETTINGS)
    LaunchedEffect(destination) { moreExpanded = destination in moreRoutes }
    val busy = state.isAnalyzing || state.isGenerating || state.workspaceBusy
    val menu: () -> Unit = { scope.launch { drawer.open() } }
    val online: (String, () -> Unit) -> Unit = { mode, action ->
        if (mode == "ONLINE" && !state.settings.onlineDisclosureAccepted) onlineAction = action else action()
    }
    val navigate: (AppDestination) -> Unit = { next ->
        if (next == AppDestination.LANGUAGE_CHECK) { checkLanguage = ""; checkCurrentLevel = ""; checkRunId++ }
        showTutor = false
        viewModel.closeQuestion()
        destination = next
        scope.launch { drawer.close() }
    }
    val open: (String) -> Unit = { id -> destination = AppDestination.SESSION; viewModel.openSession(id); scope.launch { drawer.close() } }
    val topic: (String) -> Unit = { value -> initialTopic = value; initialLevel = ""; initialLanguage = ""; initialGoal = ""; initialKind = "TOPIC"; destination = AppDestination.TOPIC; viewModel.clearImportedMaterial() }
    val buildLanguagePath: (com.oriol.letsstudy.data.LanguagePlacementResult) -> Unit = { result ->
        initialTopic = "${result.language} language practice"
        initialLevel = result.startingLevel
        initialLanguage = result.language
        initialKind = "LANGUAGE"
        initialGoal = "Use my approximate written-language placement range (${result.correct}/${result.total}; ${result.estimatedRange}) and start around ${result.startingLevel}. Focus on ${result.nextFocus}. Build practical vocabulary, grammar and reading lessons at this level."
        destination = AppDestination.TOPIC
    }
    val openLanguageCheck: (String, String) -> Unit = { language, currentLevel ->
        checkLanguage = language
        checkCurrentLevel = currentLevel
        checkRunId++
        showTutor = false
        viewModel.closeQuestion()
        destination = AppDestination.LANGUAGE_CHECK
        scope.launch { drawer.close() }
    }
    val openPlacementAttempt: (String) -> Unit = { id -> placementAttemptId = id; navigate(AppDestination.PLACEMENT_RESULT) }
    val material: () -> Unit = { destination = AppDestination.MATERIAL; initialTopic = ""; initialLevel = ""; initialLanguage = ""; initialGoal = ""; viewModel.clearImportedMaterial() }
    val beginNew: () -> Unit = { sessionBefore = state.activeSession?.id; awaitingNew = true }
    LaunchedEffect(state.activeSession?.id, state.errorMessage, awaitingNew) {
        if (awaitingNew && state.activeSession != null && state.activeSession?.id != sessionBefore) { destination = AppDestination.SESSION; awaitingNew = false }
        else if (awaitingNew && state.errorMessage != null) awaitingNew = false
    }
    StudyFocusTracker(viewModel, state.activeSession?.id, !busy && (destination == AppDestination.SESSION || showTutor))
    BackHandler(showTutor || state.selectedQuestion != null || destination != AppDestination.HOME) {
        when { showTutor -> showTutor = false; state.selectedQuestion != null -> viewModel.closeQuestion(); destination == AppDestination.PLACEMENT_RESULT -> destination = AppDestination.PROGRESS; destination == AppDestination.SESSION -> { viewModel.closeSession(); destination = AppDestination.LIBRARY }; else -> destination = AppDestination.HOME }
    }
    val headerActions = WorkspaceHeaderActions(
        onSearch = { navigate(AppDestination.LIBRARY) },
        onNotifications = { navigate(AppDestination.NOTIFICATIONS) },
        onProfile = { navigate(AppDestination.SETTINGS) },
        learnerName = if (state.account.email != null) state.account.displayName.orEmpty() else state.settings.name,
        avatarId = if (state.account.email != null && state.account.avatarLoaded) state.account.avatarId else state.settings.avatarId,
        photoPath = state.settings.photoPath,
        hasReminder = state.settings.dailyReminder || state.settings.weeklySummary,
    )
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(modifier = Modifier.widthIn(max = 248.dp), drawerContainerColor = LetsStudyColors.Canvas) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp))
                        Column { Text("let’sstudy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("A little wiser, every day.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted) }
                    }
                }
                val mainEntries = listOf(
                    Triple(AppDestination.HOME, "Home", Icons.Outlined.Home),
                    Triple(AppDestination.LIBRARY, "My studies", Icons.AutoMirrored.Outlined.MenuBook),
                    Triple(AppDestination.NEW_STUDY, "New study", Icons.Outlined.Add),
                    Triple(AppDestination.JOBS, "Job preparation", Icons.Outlined.WorkOutline),
                    Triple(AppDestination.PROGRESS, "Progress", Icons.AutoMirrored.Outlined.ShowChart),
                )
                mainEntries.forEach { (route, title, icon) -> item {
                    NavigationDrawerItem(label = { Text(title) }, selected = destination == route, onClick = { navigate(route) }, icon = { Icon(icon, null) })
                } }
                item {
                    NavigationDrawerItem(
                        label = {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("More", Modifier.weight(1f))
                                Icon(if (moreExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                            }
                        },
                        selected = destination in moreRoutes,
                        onClick = { moreExpanded = !moreExpanded },
                        icon = { Icon(Icons.Outlined.MoreHoriz, null) },
                    )
                }
                if (moreExpanded) {
                    val moreEntries = listOf(
                        Triple(AppDestination.HISTORY, "History", Icons.Outlined.History),
                        Triple(AppDestination.SAVED, "Saved questions", Icons.Outlined.BookmarkBorder),
                        Triple(AppDestination.REVIEW_DUE, "Review due", Icons.Outlined.Replay),
                        Triple(AppDestination.LANGUAGE_CHECK, "Language check", Icons.Outlined.Translate),
                        Triple(AppDestination.NOTIFICATIONS, "Notifications", Icons.Outlined.NotificationsNone),
                        Triple(AppDestination.LEADERBOARD, "Leaderboard", Icons.Outlined.EmojiEvents),
                        Triple(AppDestination.SETTINGS, "Settings", Icons.Outlined.Settings),
                    )
                    moreEntries.forEach { (route, title, icon) -> item {
                        NavigationDrawerItem(label = { Text(title) }, selected = destination == route, onClick = { navigate(route) }, icon = { Icon(icon, null) })
                    } }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text("Your studies stay on this phone, with optional account sync.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            }
        }
    }) {
        CompositionLocalProvider(LocalWorkspaceHeaderActions provides headerActions) {
            Scaffold(containerColor = LetsStudyColors.Canvas, contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                bottomBar = { if (state.selectedQuestion == null && !showTutor) AppBottomNavigation(destination, navigate) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    when {
                        showTutor && state.activeSession != null -> WorkspaceTutor(state, tutorQuestion, { showTutor = false }) { message -> online("ONLINE") { viewModel.askTutor(message, tutorQuestion) } }
                        state.selectedQuestion != null && state.activeSession != null -> WorkspacePractice(state, viewModel,
                            onTutor = { tutorQuestion = true; showTutor = true }, onLesson = { online("ONLINE", viewModel::requestConceptLesson) },
                            onAnswer = { answer ->
                                if (state.selectedQuestion?.format == com.oriol.letsstudy.data.PracticeFormat.FILL_BLANK.name) viewModel.answerPractice(answer)
                                else online(state.activeSession!!.generationMode) { viewModel.answerPractice(answer) }
                            })
                        destination == AppDestination.SESSION && state.activeSession != null -> WorkspaceStudyPath(state, viewModel, menu,
                            onTutor = { tutorQuestion = false; showTutor = true },
                            onProgressCheck = openLanguageCheck,
                            onGenerate = { format, module, mode, interview -> online(mode) { viewModel.addPractice(format, module, interview, mode) } })
                        destination in listOf(AppDestination.LIBRARY, AppDestination.JOBS, AppDestination.HISTORY) -> WorkspaceLibrary(state,
                            when (destination) { AppDestination.JOBS -> "JOB"; AppDestination.HISTORY -> "HISTORY"; else -> "ALL" }, menu, open) { pendingDeleteId = it }
                        destination == AppDestination.SAVED -> WorkspaceSaved(state, menu, { question -> destination = AppDestination.SESSION; viewModel.openSavedQuestion(question) }, viewModel::removeSavedMark)
                        destination == AppDestination.PROGRESS -> WorkspaceProgress(state, menu, { question -> destination = AppDestination.SESSION; viewModel.openSavedQuestion(question) }, openPlacementAttempt, { destination = AppDestination.REVIEW_DUE }) { destination = AppDestination.LEADERBOARD }
                        destination == AppDestination.REVIEW_DUE -> WorkspaceReviewDue(state, menu) { question -> destination = AppDestination.SESSION; viewModel.openDueQuestion(question) }
                        destination == AppDestination.LANGUAGE_CHECK -> WorkspaceLevelCheck(
                            onMenu = menu,
                            placementAttempts = state.placementAttempts,
                            initialLanguage = checkLanguage,
                            runId = checkRunId,
                            onComplete = { result, answers, version, type, currentLevel -> viewModel.savePlacementAttempt(result, answers, version, type, currentLevel) },
                            onBuildPath = buildLanguagePath,
                            initialCurrentLevel = checkCurrentLevel,
                        )
                        destination == AppDestination.NOTIFICATIONS -> WorkspaceNotifications(state, menu, { destination = AppDestination.REVIEW_DUE }) { destination = AppDestination.SETTINGS }
                        destination == AppDestination.PLACEMENT_RESULT -> state.placementAttempts.firstOrNull { it.id == placementAttemptId }?.let { attempt ->
                            WorkspacePlacementResultScreen(attempt, menu, buildLanguagePath)
                        } ?: WorkspaceHome(state, menu, topic, { destination = AppDestination.JOB_PREP }, open, { destination = AppDestination.PROGRESS }, { destination = AppDestination.LANGUAGE_CHECK }) { destination = AppDestination.REVIEW_DUE }
                        destination == AppDestination.LEADERBOARD -> WorkspaceLeaderboard(state, viewModel, menu)
                        destination == AppDestination.SETTINGS -> WorkspaceSettings(state, viewModel, menu)
                        destination == AppDestination.NEW_STUDY -> WorkspaceNewStudy(menu, { topic("") }, { destination = AppDestination.JOB_PREP }, material)
                        destination == AppDestination.JOB_PREP -> WorkspaceJobPreparation(state, menu) { input, language, mode -> online(mode) { beginNew(); viewModel.analyze(input, language, mode) } }
                        destination == AppDestination.TOPIC || destination == AppDestination.MATERIAL -> WorkspaceTopicSetup(state, initialTopic,
                            destination == AppDestination.MATERIAL, menu, viewModel::importMaterial, { setup -> online(setup.mode) { beginNew(); viewModel.createStudy(setup) } }, initialLevel, initialLanguage, initialGoal, initialKind)
                        else -> WorkspaceHome(state, menu, topic, { destination = AppDestination.JOB_PREP }, open, { destination = AppDestination.PROGRESS }, { destination = AppDestination.LANGUAGE_CHECK }) { destination = AppDestination.REVIEW_DUE }
                    }
                }
            }
        }
    }
    val pendingDelete = state.sessions.firstOrNull { it.id == pendingDeleteId }
    if (pendingDelete != null) AlertDialog(onDismissRequest = { pendingDeleteId = null }, title = { Text("Delete this study?") }, text = { Text(if (state.account.email == null) "“${pendingDelete.title}”, its questions, conversations and progress will be removed from this phone." else "“${pendingDelete.title}”, its questions, conversations and progress will be removed from this phone and your synced devices.") },
        confirmButton = { TextButton({ viewModel.deleteSession(pendingDelete.id); pendingDeleteId = null }) { Text("Delete") } }, dismissButton = { TextButton({ pendingDeleteId = null }) { Text("Keep study") } })
    if (onlineAction != null) AlertDialog(onDismissRequest = { onlineAction = null }, title = { Text("Use Fast Online") }, text = { Text("To prepare study content, relevant material and any answers or messages you submit are sent to Google Gemini. This free service has a shared usage limit and may be temporarily unavailable.") },
        confirmButton = { TextButton({ viewModel.saveSettings(state.settings.copy(onlineDisclosureAccepted = true)); val action = onlineAction; onlineAction = null; action?.invoke() }) { Text("Continue") } }, dismissButton = { TextButton({ onlineAction = null }) { Text("Cancel") } })
    if (busy) Dialog(onDismissRequest = {}) {
        WorkspaceCard {
            CircularProgressIndicator(Modifier.size(34.dp))
            Text(if (state.workspaceBusy) state.workspaceStatus else if (state.generationProgress?.stage == com.oriol.letsstudy.data.StudyProgressStage.READING_SOURCE) "Reading your source…" else "Building your questions…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.generationProgress?.let { Text("${it.completedBatches} / ${it.totalBatches} batches complete", color = LetsStudyColors.Muted) }
            Text("You can cancel safely. Your saved studies will stay available.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            TextButton({ awaitingNew = false; viewModel.cancelGeneration() }) { Text("Cancel") }
        }
    }
    if (!busy && state.errorMessage != null) AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("Let's try that again") }, text = { Text(state.errorMessage!!) }, confirmButton = { TextButton(viewModel::clearError) { Text("Got it") } })
}

@Composable
private fun StudyFocusTracker(viewModel: LetsStudyViewModel, sessionId: String?, active: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, sessionId, active) {
        var start = if (active && sessionId != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) System.currentTimeMillis() else 0L
        fun flush() {
            if (start > 0 && sessionId != null) viewModel.recordStudy(sessionId, start, (System.currentTimeMillis() - start) / 1000)
            start = 0
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && active && sessionId != null && start == 0L) start = System.currentTimeMillis()
            if (event == Lifecycle.Event.ON_PAUSE) flush()
        }
        lifecycle.addObserver(observer)
        onDispose { flush(); lifecycle.removeObserver(observer) }
    }
}
