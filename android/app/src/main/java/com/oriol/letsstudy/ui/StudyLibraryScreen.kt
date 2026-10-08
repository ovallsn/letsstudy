package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.oriol.letsstudy.data.StudySessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudyLibraryScreen(
    sessions: List<StudySessionEntity>,
    activeSessionId: String?,
    onOpenMenu: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenMenu) { Icon(Icons.Outlined.Menu, contentDescription = "Open study library menu", tint = LetsStudyColors.Ink) }
                Text("Studies", style = MaterialTheme.typography.titleMedium, color = LetsStudyColors.Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Surface(color = LetsStudyColors.Mint, shape = CircleShape) {
                    Text(sessions.size.toString(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                }
            }
            Text("Your library", style = MaterialTheme.typography.headlineSmall, color = LetsStudyColors.Ink, modifier = Modifier.padding(top = 13.dp))
            Text("Saved interview-prep sessions, ready when you are.", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        }

        if (sessions.isEmpty()) {
            item {
                EmptyLibraryCard()
            }
        } else {
            items(sessions, key = StudySessionEntity::id) { session ->
                SessionLibraryCard(
                    session = session,
                    selected = activeSessionId == session.id,
                    onClick = { onOpenSession(session.id) },
                    onDelete = { onDeleteSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = LetsStudyColors.Card),
        border = BorderStroke(1.dp, LetsStudyColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Surface(color = LetsStudyColors.Mint, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(46.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = LetsStudyColors.Primary, modifier = Modifier.size(23.dp))
                }
            }
            Text("Your study shelf is ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp))
            Text("Create a practice set from a job listing and it will be saved here on this phone.", style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun SessionLibraryCard(
    session: StudySessionEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = if (selected) LetsStudyColors.Mint else LetsStudyColors.Card,
            border = BorderStroke(1.dp, if (selected) LetsStudyColors.Primary.copy(alpha = 0.4f) else LetsStudyColors.Border),
            modifier = Modifier.weight(1f),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = LetsStudyColors.SoftBlue, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(43.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = LetsStudyColors.Primary, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.titleSmall, color = LetsStudyColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${session.practiceLanguage} · ${SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date(session.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LetsStudyColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open ${session.title}", tint = LetsStudyColors.Primary, modifier = Modifier.size(19.dp))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${session.title}", tint = LetsStudyColors.Muted, modifier = Modifier.size(21.dp))
        }
    }
}
