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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Info
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
import com.oriol.letsstudy.data.StudyProgressStage
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
    } && (generationMode == "ONLINE" || modelReady)

    Scaffold(
        containerColor = LetsStudyColors.Canvas,
        bottomBar = {
            HomeBuildBar(
                enabled = canAnalyze && chosenLanguage.isNotBlank() && !isAnalyzing,
                isAnalyzing = isAnalyzing,
                onClick = { onAnalyze(StudyInput(kind, source.trim()), chosenLanguage, generationMode) },
            )
        },
    ) { screenPadding ->
    Column(
        Modifier.fillMaxSize().padding(screenPadding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        BrandHeader(onOpenMenu)
        Spacer(Modifier.height(20.dp))
        HeroCard()
        if (latestSession != null) {
            Spacer(Modifier.height(18.dp))
            Surface(
                onClick = { onOpenSession(latestSession.id) },
                shape = RoundedCornerShape(22.dp),
                color = LetsStudyColors.Mint,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PlayArrow, null, tint = LetsStudyColors.Primary) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PICK UP WHERE YOU LEFT OFF", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text(latestSession.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LetsStudyColors.Primary)
                }
            }
        }
        Spacer(Modifier.height(27.dp))
        Text("START SOMETHING NEW", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LetsStudyColors.Clay, letterSpacing = 1.sp)
        Text("Make a study set", style = MaterialTheme.typography.headlineSmall, color = LetsStudyColors.Ink, modifier = Modifier.padding(top = 4.dp))
        Text("A job post becomes a focused practice session.", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))

            Column(Modifier.fillMaxWidth()) {
                Text("01  THE ROLE", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SourceTab("Job link", kind == SourceKind.URL, Icons.Outlined.Link, Modifier.weight(1f)) { kind = SourceKind.URL; source = "" }
                    SourceTab("Paste text", kind == SourceKind.TEXT, Icons.Outlined.Description, Modifier.weight(1f)) { kind = SourceKind.TEXT; source = "" }
                }
                Spacer(Modifier.height(13.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    label = { Text(if (isText) "Job description" else "Job listing URL") },
                    placeholder = { Text(if (isText) "Paste the role description…" else "https://company.com/jobs/…") },
                    minLines = if (isText) 5 else 1,
                    maxLines = if (isText) 8 else 3,
                    supportingText = { Text(if (isText) "Include at least 100 characters." else "Can't read the page? Paste its text instead.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = LetsStudyColors.Card,
                        unfocusedContainerColor = LetsStudyColors.Card,
                    ),
                )

                Spacer(Modifier.height(12.dp))
                Text("02  MAKE IT YOURS", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = { languageMenu = true },
                    shape = RoundedCornerShape(15.dp),
                    color = LetsStudyColors.Canvas,
                    border = BorderStroke(1.dp, LetsStudyColors.Border),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Language, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(language, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(18.dp))
                        Text("▾", color = LetsStudyColors.Muted)
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
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                        singleLine = true,
                        label = { Text("Question language") },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ModeTile("Fast online", "Quick questions", generationMode == "ONLINE", Icons.Outlined.Bolt, Modifier.weight(1f)) { generationMode = "ONLINE" }
                    ModeTile("On-device", "Runs on your phone", generationMode == "OFFLINE", Icons.Outlined.Memory, Modifier.weight(1f)) { generationMode = "OFFLINE" }
                }
                Text(
                    if (generationMode == "ONLINE") "Uses the project's free Gemini quota. Job text is sent to Google."
                    else "No Gemini quota. Download the model once; generation takes longer.",
                    style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (generationMode == "OFFLINE") {
                    Spacer(Modifier.height(12.dp))
                    ModelSetupCard(modelState, downloadState, onDownloadOnWifi, onDownloadOnMobileData, onCancelModelDownload)
                }
                if (isAnalyzing) {
                    Spacer(Modifier.height(16.dp))
                    GenerationJourneyCard(generationProgress, generationMode == "ONLINE")
                }
                Text("No account needed · Your answers stay on this phone", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp))
            }
        if (errorMessage != null) {
            Spacer(Modifier.height(13.dp))
            ErrorBanner(errorMessage, onDismissError)
        }
        Spacer(Modifier.height(30.dp))
    }
    }
}

@Composable
private fun HomeBuildBar(enabled: Boolean, isAnalyzing: Boolean, onClick: () -> Unit) {
    Surface(color = LetsStudyColors.Canvas, shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.Primary),
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
                Text("Making your study set…")
            } else {
                Text("Create 15 questions", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(9.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BrandHeader(onOpenMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenMenu) { Icon(Icons.Outlined.Menu, contentDescription = "Open study sessions", tint = LetsStudyColors.Ink) }
        Spacer(Modifier.width(4.dp))
        Text("let'sstudy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LetsStudyColors.Ink)
        Spacer(Modifier.weight(1f))
        Surface(color = LetsStudyColors.Mint, shape = CircleShape) {
            Text("YOUR STUDY SPACE", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeroCard() {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = LetsStudyColors.DeepPrimary), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR NEXT CHAPTER", style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Sun, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Walk in\nprepared.", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Surface(color = LetsStudyColors.Sun, shape = CircleShape, modifier = Modifier.size(66.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(65.dp))
                    }
                }
            }
            Text("Practice the role. Learn every answer.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 7.dp))
        }
    }
}

@Composable
private fun SourceTab(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = if (selected) LetsStudyColors.ClayWash else LetsStudyColors.Canvas, border = BorderStroke(1.dp, if (selected) LetsStudyColors.Clay else LetsStudyColors.Border)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (selected) LetsStudyColors.Clay else LetsStudyColors.Muted)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) LetsStudyColors.Ink else LetsStudyColors.Muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ModeTile(title: String, detail: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(17.dp), color = if (selected) LetsStudyColors.Mint else LetsStudyColors.Canvas, border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) LetsStudyColors.Primary else LetsStudyColors.Border)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = LetsStudyColors.Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted, maxLines = 2)
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LetsStudyColors.ClayWash), border = BorderStroke(1.dp, LetsStudyColors.Clay.copy(alpha = 0.35f))) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = LetsStudyColors.Clay, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Let's try that again", style = MaterialTheme.typography.titleSmall, color = LetsStudyColors.Ink, fontWeight = FontWeight.Bold)
                Text(message, color = LetsStudyColors.Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Got it", color = LetsStudyColors.Clay) }
            }
        }
    }
}
