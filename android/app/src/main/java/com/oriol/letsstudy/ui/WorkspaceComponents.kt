package com.oriol.letsstudy.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriol.letsstudy.data.*
import java.time.LocalDate
import java.time.ZoneId

data class WorkspaceHeaderActions(
    val onLeaderboard: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val learnerName: String = "",
    val avatarId: String = LearnerAvatarIds.DEFAULT,
    val photoPath: String = "",
)

val LocalWorkspaceHeaderActions = staticCompositionLocalOf { WorkspaceHeaderActions() }

@Composable
fun StudyStreakChip(days: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = when (days) {
        0 -> "Start today"
        1 -> "1 day streak"
        else -> "$days day streak"
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = LetsStudyColors.ClayWash,
        border = BorderStroke(1.dp, LetsStudyColors.Border),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.LocalFireDepartment, null, tint = LetsStudyColors.Clay, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = LetsStudyColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

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
            HeaderActionButton(actions.onLeaderboard, "Community leaderboard") {
                Icon(Icons.Outlined.EmojiEvents, null, tint = LetsStudyColors.Muted, modifier = Modifier.size(19.dp))
            }
            HeaderActionButton(actions.onNotifications, "Study reminders") {
                Icon(Icons.Outlined.NotificationsNone, null, tint = LetsStudyColors.Muted, modifier = Modifier.size(19.dp))
            }
            HeaderActionButton(actions.onProfile, actions.learnerName.takeIf(String::isNotBlank)?.let { "Profile for $it" } ?: "Profile and settings") {
                WorkspaceAvatar(actions.avatarId, 32.dp, photoPath = actions.photoPath, displayName = actions.learnerName)
            }
        }
    }
}

private data class StudyAvatarOption(
    val id: String,
    val label: String,
    val icon: ImageVector?,
    val foreground: Color,
    val background: Color,
)

private val studyAvatarOptions = listOf(
    StudyAvatarOption(LearnerAvatarIds.DEFAULT, "Initials", null, Color.White, LetsStudyColors.Ink),
    StudyAvatarOption("book", "Book", Icons.AutoMirrored.Outlined.MenuBook, LetsStudyColors.Clay, LetsStudyColors.Warm),
    StudyAvatarOption("spark", "Spark", Icons.Outlined.Lightbulb, LetsStudyColors.DeepPrimary, LetsStudyColors.SoftBlue),
    StudyAvatarOption("paw", "Paw print", Icons.Outlined.Pets, LetsStudyColors.DeepPrimary, LetsStudyColors.Mint),
    StudyAvatarOption("globe", "Globe", Icons.Outlined.Public, LetsStudyColors.Clay, LetsStudyColors.ClayWash),
    StudyAvatarOption("star", "Star", Icons.Outlined.StarOutline, LetsStudyColors.DeepPrimary, LetsStudyColors.Warm),
)

@Composable
fun WorkspaceAvatar(avatarId: String, size: Dp, modifier: Modifier = Modifier, photoPath: String = "", displayName: String = "") {
    val option = studyAvatarOptions.firstOrNull { it.id == avatarId } ?: studyAvatarOptions.first()
    val bitmap = remember(photoPath) {
        photoPath.takeIf(String::isNotBlank)?.let { path -> runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = option.background,
        border = BorderStroke(1.dp, LetsStudyColors.Border),
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = "Profile photo", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else if (option.id == LearnerAvatarIds.DEFAULT) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    LearnerAvatarIds.initials(displayName),
                    color = option.foreground,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        } else {
            option.icon?.let { icon ->
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = option.foreground, modifier = Modifier.size(size * 0.54f))
                }
            }
        }
    }
}

@Composable
fun StudyAvatarPicker(selectedAvatarId: String, onSelected: (String) -> Unit, displayName: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Profile picture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        studyAvatarOptions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val selected = selectedAvatarId == option.id
                    Surface(
                        onClick = { onSelected(option.id) },
                        modifier = Modifier.weight(1f).heightIn(min = 82.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) LetsStudyColors.Mint else LetsStudyColors.Card,
                        border = BorderStroke(1.dp, if (selected) LetsStudyColors.Primary else LetsStudyColors.Border),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            WorkspaceAvatar(option.id, 40.dp, displayName = displayName)
                            Text(option.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
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

fun generationModeLabel(mode: String): String = if (mode == "ONLINE") "Quick online" else "On this phone"

fun generationModeValue(label: String): String = if (label == "Quick online") "ONLINE" else "OFFLINE"

@Composable
fun WorkspaceGenerationModeSelector(mode: String, onModeChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WorkspaceSelect(
            "Question generation",
            generationModeLabel(mode),
            listOf("Quick online", "On this phone"),
        ) { onModeChange(generationModeValue(it)) }
        if (mode != "ONLINE") {
            Text("Needs a one-time download. Creating a set may take longer.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
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
