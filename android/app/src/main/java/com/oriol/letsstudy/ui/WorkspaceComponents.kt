package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriol.letsstudy.data.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class WorkspaceHeaderActions(
    val onSearch: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val learnerName: String = "",
    val hasReminder: Boolean = false,
)

val LocalWorkspaceHeaderActions = staticCompositionLocalOf { WorkspaceHeaderActions() }

@Composable
fun WorkspacePage(title: String, onMenu: () -> Unit, content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { WorkspaceTopBar(title, onMenu) }
        content()
    }
}

@Composable
fun WorkspaceTopBar(title: String, onMenu: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val actions = LocalWorkspaceHeaderActions.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderActionButton(onMenu, "Open navigation", Modifier.width(44.dp), Alignment.CenterStart) {
            Icon(Icons.Outlined.Menu, null, tint = LetsStudyColors.Ink, modifier = Modifier.offset(x = (-2).dp).size(19.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 1, modifier = Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else {
            HeaderActionButton(actions.onSearch, "Search studies") {
                Icon(Icons.Outlined.Search, null, tint = LetsStudyColors.Muted, modifier = Modifier.size(19.dp))
            }
            HeaderActionButton(actions.onNotifications, "Study reminders") {
                Box {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = LetsStudyColors.Muted, modifier = Modifier.size(19.dp))
                    if (actions.hasReminder) {
                        Box(Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = 1.dp).size(6.dp)
                            .background(LetsStudyColors.Clay, CircleShape))
                    }
                }
            }
            val initials = learnerInitials(actions.learnerName)
            HeaderActionButton(actions.onProfile, "Profile and settings") {
                Surface(shape = CircleShape, color = LetsStudyColors.Card,
                    border = BorderStroke(1.dp, LetsStudyColors.Border), modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (initials == null) Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = LetsStudyColors.Muted, modifier = Modifier.size(18.dp))
                        else Text(initials, style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun learnerInitials(name: String): String? {
    val initials = name.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(2).mapNotNull { word ->
        val codePoint = word.codePointAt(0)
        if (Character.isLetterOrDigit(codePoint)) String(Character.toChars(codePoint)).uppercase(Locale.ROOT) else null
    }.joinToString("")
    return initials.takeIf(String::isNotBlank)
}

@Composable
private fun HeaderActionButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = alignment,
        content = content,
    )
}

@Composable
fun WorkspaceCard(modifier: Modifier = Modifier, color: Color = LetsStudyColors.Card, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = color, border = BorderStroke(1.dp, LetsStudyColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable fun WorkspaceTitle(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LetsStudyColors.Muted)
    }
}

@Composable
fun WorkspaceAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = LetsStudyColors.Card, border = BorderStroke(1.dp, LetsStudyColors.Border)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = LetsStudyColors.Mint, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = LetsStudyColors.Primary) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LetsStudyColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun WorkspaceSelect(label: String, value: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LetsStudyColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(14.dp)) {
                Text(value, Modifier.weight(1f), color = LetsStudyColors.Ink)
                Text("⌄", color = LetsStudyColors.Muted)
            }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { option -> DropdownMenuItem({ Text(option) }, { onSelected(option); expanded = false }) }
            }
        }
    }
}

@Composable fun WorkspaceField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1, maxLength: Int = 8000, enabled: Boolean = true) {
    OutlinedTextField(value, { onValueChange(it.take(maxLength)) }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(label) },
        shape = RoundedCornerShape(14.dp), minLines = minLines, maxLines = if (minLines == 1) 3 else 8,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFF8A92A4)))
}

@Composable fun WorkspaceEmpty(title: String, text: String) {
    WorkspaceCard(color = LetsStudyColors.Mint) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(text, color = LetsStudyColors.Muted) }
}

fun epochDay(time: Long): LocalDate = java.time.Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
fun studyStreak(questions: List<StudyQuestionEntity>, activity: List<StudyActivityEntity>): Int {
    val days = questions.filter { it.answeredAt > 0 }.map { epochDay(it.answeredAt) }.toSet() + activity.map { epochDay(it.startedAt) }
    var day = LocalDate.now()
    if (day !in days) day = day.minusDays(1)
    var count = 0
    while (day in days) { count++; day = day.minusDays(1) }
    return count
}
