package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oriol.letsstudy.ai.ModelState
import com.oriol.letsstudy.ai.ModelCatalog
import com.oriol.letsstudy.ai.ModelDownloadState

@Composable
fun ModelSetupCard(
    modelState: ModelState,
    downloadState: ModelDownloadState,
    onDownloadOnWifi: () -> Unit,
    onDownloadOnMobileData: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    var confirmMobileData by remember { mutableStateOf(false) }
    var showModelLicense by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val presentation = modelSetupPresentation(modelState, downloadState)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Mint),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LetsStudyColors.Primary.copy(alpha = 0.20f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = LetsStudyColors.DeepPrimary,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.size(42.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (presentation == ModelSetupPresentation.Ready) Icons.Outlined.Verified else Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = LetsStudyColors.Sun,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        titleFor(presentation),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LetsStudyColors.Ink,
                    )
                    Text(
                        detailFor(presentation),
                        style = MaterialTheme.typography.bodySmall,
                        color = LetsStudyColors.Muted,
                    )
                }
            }

            when (presentation) {
                ModelSetupPresentation.NeedsDownload -> {
                    Spacer(Modifier.height(11.dp))
                    Text(
                        "One-time download · ${ModelCatalog.DISPLAY_SIZE} · Keep about 2.73 GB free. Downloads start on Wi‑Fi by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LetsStudyColors.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onDownloadOnWifi,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LetsStudyColors.Primary),
                    ) {
                        Icon(Icons.Outlined.Wifi, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download on Wi‑Fi · ${ModelCatalog.DISPLAY_SIZE}")
                    }
                    TextButton(onClick = { confirmMobileData = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Use mobile data instead", color = LetsStudyColors.Muted)
                    }
                }
                ModelSetupPresentation.Queued -> {
                    Spacer(Modifier.height(12.dp))
                    Text("Connect to Wi‑Fi to begin the download.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                    TextButton(onClick = onCancelDownload, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Outlined.Cancel, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Cancel download")
                    }
                }
                is ModelSetupPresentation.Downloading -> {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { presentation.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = LetsStudyColors.Primary,
                        trackColor = LetsStudyColors.Mint,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${presentation.percent}% · ${ModelCatalog.DISPLAY_SIZE}", style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCancelDownload) {
                            Icon(Icons.Outlined.Cancel, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }
                ModelSetupPresentation.Verifying, ModelSetupPresentation.Loading -> {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LetsStudyColors.Primary)
                }
                ModelSetupPresentation.Ready -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ready to create another study round.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LetsStudyColors.Muted,
                    )
                }
                is ModelSetupPresentation.Failed -> {
                    Spacer(Modifier.height(8.dp))
                    Text(presentation.message, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Clay)
                    if (modelState !is ModelState.Failed) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onDownloadOnWifi, shape = RoundedCornerShape(12.dp)) { Text("Retry on Wi‑Fi") }
                            TextButton(onClick = { confirmMobileData = true }) { Text("Use mobile data") }
                        }
                    }
                }
            }

            TextButton(
                onClick = { showModelLicense = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("About On-device study", color = LetsStudyColors.Primary)
            }
        }
    }

    if (confirmMobileData) {
        AlertDialog(
            onDismissRequest = { confirmMobileData = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = LetsStudyColors.Card,
            icon = { Icon(Icons.Outlined.CloudDownload, null, tint = LetsStudyColors.Primary) },
            title = { Text("Download using mobile data?") },
            text = {
                Text("This downloads ${ModelCatalog.DISPLAY_SIZE} once. Your mobile provider may charge for the data or count it toward your plan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmMobileData = false
                        onDownloadOnMobileData()
                    },
                ) { Text("Download ${ModelCatalog.DISPLAY_SIZE}") }
            },
            dismissButton = { TextButton(onClick = { confirmMobileData = false }) { Text("Cancel") } },
        )
    }

    if (showModelLicense) {
        val licenseText = remember {
            context.assets.open("licenses/Apache-2.0.txt").bufferedReader().use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { showModelLicense = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = LetsStudyColors.Card,
            title = { Text("About On-device study") },
            text = {
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Let'sStudy downloads Gemma 4 E2B instruction-tuned, converted to LiteRT-LM format by litert-community. Model author: Google DeepMind.")
                    Text("The model is licensed under Apache License 2.0. The full license text is included below and bundled with the app.")
                    Text(licenseText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelLicense = false }) { Text("Close") }
            },
        )
    }
}

private fun titleFor(state: ModelSetupPresentation): String = when (state) {
    ModelSetupPresentation.NeedsDownload -> "Set up On-device study"
    ModelSetupPresentation.Queued -> "Waiting for Wi‑Fi"
    is ModelSetupPresentation.Downloading -> "Downloading study model"
    ModelSetupPresentation.Verifying -> "Checking the model file"
    ModelSetupPresentation.Ready -> "On-device study is ready"
    ModelSetupPresentation.Loading -> "Opening the study model"
    is ModelSetupPresentation.Failed -> "Couldn’t finish model setup"
}

private fun detailFor(state: ModelSetupPresentation): String = when (state) {
    ModelSetupPresentation.NeedsDownload -> "Download the model once to use this study option"
    ModelSetupPresentation.Queued -> "The download will resume when Wi‑Fi is available"
    is ModelSetupPresentation.Downloading -> "This can take a while. You can pause and resume later."
    ModelSetupPresentation.Verifying -> "Making sure every model file is complete"
    ModelSetupPresentation.Ready -> "Ready for your next study round"
    ModelSetupPresentation.Loading -> "Starting the model for your first study"
    is ModelSetupPresentation.Failed -> "Your saved sessions are still here"
}
