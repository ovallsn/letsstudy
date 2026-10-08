package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriol.letsstudy.R
import com.oriol.letsstudy.ai.ModelDownloadState
import com.oriol.letsstudy.ai.ModelState
import com.oriol.letsstudy.data.StudyGenerationProgress
import com.oriol.letsstudy.data.StudySessionEntity
import com.oriol.letsstudy.domain.SourceKind
import com.oriol.letsstudy.domain.StudyInput

@Composable
fun HomeScreen(
    isAnalyzing: Boolean,
    modelState: ModelState,
    downloadState: ModelDownloadState,
    generationProgress: StudyGenerationProgress?,
    errorMessage: String?,
    latestSession: StudySessionEntity?,
    onAnalyze: (StudyInput, String, String) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onDismissError: () -> Unit,
    onDownloadOnWifi: () -> Unit,
    onDownloadOnMobileData: () -> Unit,
    onCancelModelDownload: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(SourceKind.URL) }
    var source by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("English") }
    var languageMenu by remember { mutableStateOf(false) }
    var customLanguage by rememberSaveable { mutableStateOf("") }
    var generationMode by rememberSaveable { mutableStateOf("ONLINE") }
    val isText = kind == SourceKind.TEXT
    val chosenLanguage = if (language == "Other language") customLanguage.trim() else language
    val modelReady = modelSetupPresentation(modelState, downloadState) == ModelSetupPresentation.Ready
    val canAnalyze = source.trim().let {
        it.startsWith("http://") || it.startsWith("https://") || (isText && it.length >= 100)
    } && (generationMode == "ONLINE" || modelReady) && chosenLanguage.isNotBlank()

    Scaffold(containerColor = LetsStudyColors.Canvas) { screenPadding ->
        Column(
            Modifier.fillMaxSize().padding(screenPadding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open study library", tint = LetsStudyColors.Ink)
                }
                Text("Home", style = MaterialTheme.typography.titleMedium, color = LetsStudyColors.Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Surface(color = LetsStudyColors.Mint, shape = CircleShape, modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Let’s Study", modifier = Modifier.size(36.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("A FOCUSED WAY TO PREPARE", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text(
                "Walk into your\nnext interview ready.",
                style = MaterialTheme.typography.headlineMedium,
                color = LetsStudyColors.Ink,
                lineHeight = 31.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
            Text(
                "Turn a job listing into practical questions, clear explanations, and a study path you can revisit.",
                style = MaterialTheme.typography.bodyMedium,
                color = LetsStudyColors.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (latestSession != null) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    onClick = { onOpenSession(latestSession.id) },
                    shape = RoundedCornerShape(20.dp),
                    color = LetsStudyColors.Mint,
                    border = BorderStroke(1.dp, LetsStudyColors.Primary.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(45.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = LetsStudyColors.Primary)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("CONTINUE STUDYING", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                            Text(latestSession.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open latest study", tint = LetsStudyColors.Primary)
                    }
                }
            }

            Spacer(Modifier.height(21.dp))
            Card(
                shape = RoundedCornerShape(25.dp),
                colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Card),
                border = BorderStroke(1.dp, LetsStudyColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = LetsStudyColors.Mint, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Bolt, contentDescription = null, tint = LetsStudyColors.Primary, modifier = Modifier.size(19.dp))
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text("AI INTERVIEW PRACTICE", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold, letterSpacing = 0.55.sp)
                    }
                    Text("What role are you preparing for?", style = MaterialTheme.typography.titleLarge, color = LetsStudyColors.Ink, modifier = Modifier.padding(top = 14.dp))
                    Text("Start with a public job listing or paste its description.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 15.dp))

                    Text("THE JOB LISTING", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted, fontWeight = FontWeight.Bold, letterSpacing = 0.65.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceTab("Job link", kind == SourceKind.URL, Icons.Outlined.Link, Modifier.weight(1f)) { kind = SourceKind.URL; source = "" }
                        SourceTab("Paste text", kind == SourceKind.TEXT, Icons.Outlined.Description, Modifier.weight(1f)) { kind = SourceKind.TEXT; source = "" }
                    }
                    Spacer(Modifier.height(11.dp))
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        label = { Text(if (isText) "Job description" else "Job listing URL") },
                        placeholder = { Text(if (isText) "Paste the role description…" else "https://company.com/jobs/…") },
                        minLines = if (isText) 4 else 1,
                        maxLines = if (isText) 8 else 3,
                        supportingText = { Text(if (isText) "Include at least 100 characters." else "If the page can’t be read, paste its text instead.") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = LetsStudyColors.Card,
                            unfocusedContainerColor = LetsStudyColors.Card,
                        ),
                    )

                    Text("QUESTION LANGUAGE", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted, fontWeight = FontWeight.Bold, letterSpacing = 0.65.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    Box {
                        Surface(
                            onClick = { languageMenu = true },
                            shape = RoundedCornerShape(14.dp),
                            color = LetsStudyColors.Canvas,
                            border = BorderStroke(1.dp, LetsStudyColors.Border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Language, contentDescription = null, tint = LetsStudyColors.Primary, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(9.dp))
                                Text(language, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text("⌄", color = LetsStudyColors.Muted)
                            }
                        }
                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                            listOf("English", "Spanish", "Thai", "Other language").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { language = option; languageMenu = false })
                            }
                        }
                    }
                    if (language == "Other language") {
                        OutlinedTextField(
                            value = customLanguage,
                            onValueChange = { customLanguage = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            label = { Text("Question language") },
                        )
                    }

                    Text("GENERATION MODE", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Muted, fontWeight = FontWeight.Bold, letterSpacing = 0.65.sp, modifier = Modifier.padding(top = 15.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ModeTile("Fast online", "Quick generation", generationMode == "ONLINE", Icons.Outlined.Bolt, Modifier.weight(1f)) { generationMode = "ONLINE" }
                        ModeTile("On-device", "Runs on your phone", generationMode == "OFFLINE", Icons.Outlined.Memory, Modifier.weight(1f)) { generationMode = "OFFLINE" }
                    }
                    Text(
                        if (generationMode == "ONLINE") "Uses the project’s Gemini service. Job text is sent to Google."
                        else "Download the model once. Generation stays on your phone and takes longer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LetsStudyColors.Muted,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                    if (generationMode == "OFFLINE") {
                        Spacer(Modifier.height(11.dp))
                        ModelSetupCard(modelState, downloadState, onDownloadOnWifi, onDownloadOnMobileData, onCancelModelDownload)
                    }
                    if (isAnalyzing) {
                        Spacer(Modifier.height(12.dp))
                        GenerationJourneyCard(generationProgress, generationMode == "ONLINE")
                    }
                    Button(
                        onClick = { onAnalyze(StudyInput(kind, source.trim()), chosenLanguage, generationMode) },
                        enabled = canAnalyze && !isAnalyzing,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.Primary),
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(9.dp))
                            Text("Preparing your questions…")
                        } else {
                            Text("Create 15 questions", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(9.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text("No account needed · Your answers stay on this phone", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 11.dp))
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(14.dp))
                ErrorBanner(errorMessage, onDismissError)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SourceTab(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = if (selected) LetsStudyColors.Mint else LetsStudyColors.Canvas,
        border = BorderStroke(1.dp, if (selected) LetsStudyColors.Primary.copy(alpha = 0.45f) else LetsStudyColors.Border),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(17.dp), tint = if (selected) LetsStudyColors.Primary else LetsStudyColors.Muted)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) LetsStudyColors.Ink else LetsStudyColors.Muted, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun ModeTile(title: String, detail: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) LetsStudyColors.Mint else LetsStudyColors.Canvas,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) LetsStudyColors.Primary.copy(alpha = 0.55f) else LetsStudyColors.Border),
    ) {
        Column(Modifier.padding(11.dp)) {
            Icon(icon, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(7.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = LetsStudyColors.Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, maxLines = 2)
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LetsStudyColors.ClayWash), border = BorderStroke(1.dp, LetsStudyColors.Clay.copy(alpha = 0.35f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = LetsStudyColors.Clay, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Let’s try that again", style = MaterialTheme.typography.titleSmall, color = LetsStudyColors.Ink, fontWeight = FontWeight.Bold)
                Text(message, color = LetsStudyColors.Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Got it", color = LetsStudyColors.Primary) }
            }
        }
    }
}
