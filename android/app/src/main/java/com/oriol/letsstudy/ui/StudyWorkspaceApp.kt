package com.oriol.letsstudy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    var showTutor by rememberSaveable { mutableStateOf(false) }
    var tutorQuestion by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingNew by rememberSaveable { mutableStateOf(false) }
    var sessionBefore by rememberSaveable { mutableStateOf<String?>(null) }
    var onlineAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val busy = state.isAnalyzing || state.isGenerating || state.workspaceBusy
    val menu: () -> Unit = { scope.launch { drawer.open() } }
    val online: (String, () -> Unit) -> Unit = { mode, action ->
        if (mode == "ONLINE" && !state.settings.onlineDisclosureAccepted) onlineAction = action else action()
    }
    val navigate: (AppDestination) -> Unit = { next ->
        showTutor = false
        viewModel.closeQuestion()
        destination = next
        scope.launch { drawer.close() }
    }
    val open: (String) -> Unit = { id -> destination = AppDestination.SESSION; viewModel.openSession(id); scope.launch { drawer.close() } }
    val topic: (String) -> Unit = { value -> initialTopic = value; destination = AppDestination.TOPIC; viewModel.clearImportedMaterial() }
    val material: () -> Unit = { destination = AppDestination.MATERIAL; initialTopic = ""; viewModel.clearImportedMaterial() }
    val beginNew: () -> Unit = { sessionBefore = state.activeSession?.id; awaitingNew = true }
    LaunchedEffect(state.activeSession?.id, state.errorMessage, awaitingNew) {
        if (awaitingNew && state.activeSession != null && state.activeSession?.id != sessionBefore) { destination = AppDestination.SESSION; awaitingNew = false }
        else if (awaitingNew && state.errorMessage != null) awaitingNew = false
    }
    StudyFocusTracker(viewModel, state.activeSession?.id, !busy && (destination == AppDestination.SESSION || showTutor))
    BackHandler(showTutor || state.selectedQuestion != null || destination != AppDestination.HOME) {
        when { showTutor -> showTutor = false; state.selectedQuestion != null -> viewModel.closeQuestion(); destination == AppDestination.SESSION -> { viewModel.closeSession(); destination = AppDestination.LIBRARY }; else -> destination = AppDestination.HOME }
    }
    val headerActions = WorkspaceHeaderActions(
        onSearch = { navigate(AppDestination.LIBRARY) },
        onNotifications = { navigate(AppDestination.SETTINGS) },
        onProfile = { navigate(AppDestination.SETTINGS) },
        learnerName = if (state.account.email != null) state.account.displayName.orEmpty() else state.settings.name,
        hasReminder = state.settings.dailyReminder || state.settings.weeklySummary,
    )
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = LetsStudyColors.Canvas) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Row(Modifier.padding(12.dp, 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(46.dp))
                        Column { Text("let’sstudy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("A little wiser, every day.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted) }
                    }
                }
                val entries = listOf(Triple(AppDestination.HOME, "Home", Icons.Outlined.Home), Triple(AppDestination.LIBRARY, "My studies", Icons.Outlined.MenuBook),
                    Triple(AppDestination.NEW_STUDY, "New study", Icons.Outlined.Add), Triple(AppDestination.JOBS, "Job preparation", Icons.Outlined.WorkOutline),
                    Triple(AppDestination.PROGRESS, "Progress", Icons.Outlined.ShowChart), Triple(AppDestination.HISTORY, "History", Icons.Outlined.History),
                    Triple(AppDestination.SAVED, "Saved questions", Icons.Outlined.BookmarkBorder), Triple(AppDestination.SETTINGS, "Settings", Icons.Outlined.Settings))
                entries.forEach { (route, title, icon) -> item {
                    NavigationDrawerItem(label = { Text(title) }, selected = destination == route, onClick = { navigate(route) }, icon = { Icon(icon, null) })
                } }
                item { HorizontalDivider(Modifier.padding(vertical = 15.dp)); Text("Your studies stay on this phone, with optional account sync.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(12.dp)) }
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
                            onGenerate = { format, module, mode, interview -> online(mode) { viewModel.addPractice(format, module, interview, mode) } })
                        destination in listOf(AppDestination.LIBRARY, AppDestination.JOBS, AppDestination.HISTORY) -> WorkspaceLibrary(state,
                            when (destination) { AppDestination.JOBS -> "JOB"; AppDestination.HISTORY -> "HISTORY"; else -> "ALL" }, menu, open) { pendingDeleteId = it }
                        destination == AppDestination.SAVED -> WorkspaceSaved(state, menu, { question -> destination = AppDestination.SESSION; viewModel.openSavedQuestion(question) }, viewModel::removeSavedMark)
                        destination == AppDestination.PROGRESS -> WorkspaceProgress(state, menu) { question -> destination = AppDestination.SESSION; viewModel.openSavedQuestion(question) }
                        destination == AppDestination.SETTINGS -> WorkspaceSettings(state, viewModel, menu)
                        destination == AppDestination.NEW_STUDY -> WorkspaceNewStudy(menu, { topic("") }, { destination = AppDestination.JOB_PREP }, material)
                        destination == AppDestination.JOB_PREP -> WorkspaceJobPreparation(state, menu) { input, language, mode -> online(mode) { beginNew(); viewModel.analyze(input, language, mode) } }
                        destination == AppDestination.TOPIC || destination == AppDestination.MATERIAL -> WorkspaceTopicSetup(state, initialTopic,
                            destination == AppDestination.MATERIAL, menu, viewModel::importMaterial) { setup -> online(setup.mode) { beginNew(); viewModel.createStudy(setup) } }
                        else -> WorkspaceHome(state, menu, topic, { destination = AppDestination.JOB_PREP }, open) { destination = AppDestination.PROGRESS }
                    }
                }
            }
        }
    }
    val pendingDelete = state.sessions.firstOrNull { it.id == pendingDeleteId }
    if (pendingDelete != null) AlertDialog(onDismissRequest = { pendingDeleteId = null }, title = { Text("Delete this study?") }, text = { Text(if (state.account.email == null) "“${pendingDelete.title}”, its questions, conversations and progress will be removed from this phone." else "“${pendingDelete.title}”, its questions, conversations and progress will be removed from this phone and your synced devices.") },
        confirmButton = { TextButton({ viewModel.deleteSession(pendingDelete.id); pendingDeleteId = null }) { Text("Delete") } }, dismissButton = { TextButton({ pendingDeleteId = null }) { Text("Keep study") } })
    if (onlineAction != null) AlertDialog(onDismissRequest = { onlineAction = null }, title = { Text("Study with Fast Online") }, text = { Text("Relevant study material and submitted messages or answers are sent to Google's Gemini service. The app's free quota is shared and limited. You don't need an AI account. Saved content can be read offline.") },
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
