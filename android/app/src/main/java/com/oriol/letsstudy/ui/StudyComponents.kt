package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oriol.letsstudy.data.StudyGenerationProgress
import com.oriol.letsstudy.data.StudyProgressStage

@Composable
fun GenerationJourneyCard(progress: StudyGenerationProgress?, online: Boolean, moreQuestions: Boolean = false) {
    val reading = progress?.stage == StudyProgressStage.READING_SOURCE
    val title = when {
        reading -> "Reading the source"
        moreQuestions -> "Making your next round"
        else -> "Making your study set"
    }
    val detail = when {
        reading -> "Finding the role, skills and topics worth practising."
        online -> "Writing 15 multiple-choice questions and explanations."
        else -> "The model is creating questions on your phone. This can take a while."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Warm),
        border = BorderStroke(1.dp, LetsStudyColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = LetsStudyColors.DeepPrimary, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(43.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = LetsStudyColors.Sun, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = LetsStudyColors.Primary,
                trackColor = LetsStudyColors.Mint,
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                JourneyStep("Read", done = !reading, active = reading)
                JourneyStep("Create", done = false, active = !reading)
                JourneyStep("Study", done = false, active = false)
            }
        }
    }
}

@Composable
private fun JourneyStep(label: String, done: Boolean, active: Boolean) {
    Icon(
        if (done) Icons.Outlined.CheckCircle else Icons.Outlined.HourglassEmpty,
        null,
        modifier = Modifier.size(15.dp),
        tint = if (done || active) LetsStudyColors.Primary else LetsStudyColors.Muted,
    )
    Text(label, style = MaterialTheme.typography.labelSmall, color = if (done || active) LetsStudyColors.Primary else LetsStudyColors.Muted, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
}
