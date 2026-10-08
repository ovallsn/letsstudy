package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WorkspaceLeaderboard(state: LetsStudyUiState, viewModel: LetsStudyViewModel, onMenu: () -> Unit) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.account.email, state.account.profileComplete) { viewModel.refreshLeaderboard() }
    LaunchedEffect(state.leaderboard.nickname) { if (nickname.isBlank()) nickname = state.leaderboard.nickname }
    val leaderboard = state.leaderboard

    WorkspacePage("Community board", onMenu) {
        item { WorkspaceTitle("A little friendly momentum.", "See how many study points people have earned this week.") }
        item {
            WorkspaceCard(color = LetsStudyColors.Mint) {
                Text("Optional and separate from your private profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Joining publishes the nickname you choose and your points for the current week. You earn 10 points per answered question and 1 point per five tracked study minutes, up to 500 points each week. Your email, username, study topics, questions and answers stay private. Points are self-reported and meant for encouragement, not a verified competition.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
            }
        }
        if (state.account.email == null || !state.account.profileComplete) item {
            WorkspaceEmpty("Sign in to join", "Create or complete your learner profile in Settings to use the optional community board.")
        } else if (!leaderboard.enabled) item {
            WorkspaceCard {
                Text("Choose a board nickname", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                WorkspaceField(nickname, { nickname = it }, "Public nickname", maxLength = 24)
                Text("Use a nickname rather than your email or full name. You can turn this off at any time.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                Button(onClick = { viewModel.enableLeaderboard(nickname) }, enabled = nickname.trim().length in 2..24 && !leaderboard.isLoading, modifier = Modifier.fillMaxWidth()) {
                    Text("Join the community board")
                }
            }
        } else {
            item {
                WorkspaceCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("This week's board", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = viewModel::refreshLeaderboard, enabled = !leaderboard.isLoading) {
                            if (leaderboard.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else androidx.compose.material3.Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Text("Refresh")
                        }
                    }
                    if (leaderboard.entries.isEmpty()) WorkspaceEmpty("A fresh week", "Your points will appear here after someone joins and studies.")
                    leaderboard.entries.forEachIndexed { index, entry ->
                        val mine = entry.id == leaderboard.myEntryId
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (mine) LetsStudyColors.Mint else LetsStudyColors.Card,
                            border = BorderStroke(1.dp, if (mine) LetsStudyColors.Primary else LetsStudyColors.Border),
                        ) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                androidx.compose.material3.Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = LetsStudyColors.Primary)
                                Text("${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LetsStudyColors.Muted)
                                Text(entry.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium)
                                Text("${entry.weeklyPoints} pts", style = MaterialTheme.typography.labelLarge, color = LetsStudyColors.Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (leaderboard.myEntryId != null && leaderboard.entries.none { it.id == leaderboard.myEntryId }) {
                        Text("${leaderboard.nickname} · your score is outside the top 25 displayed here.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
                    }
                    HorizontalDivider(color = LetsStudyColors.Border)
                    TextButton(onClick = { confirmLeave = true }, modifier = Modifier.align(Alignment.End)) { Text("Leave the board") }
                }
            }
        }
        if (leaderboard.error != null) item {
            WorkspaceCard {
                Text(leaderboard.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = viewModel::refreshLeaderboard, enabled = !leaderboard.isLoading) { Text("Try again") }
            }
        }
        if (leaderboard.enabled && !leaderboard.isLoading) item {
            Text("Points are based on practice saved on this phone. The board refreshes only when you open it or tap Refresh.", style = MaterialTheme.typography.bodySmall, color = LetsStudyColors.Muted)
        }
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Leave the community board?") },
        text = { Text("Your nickname and score will be removed from the public board. Your private studies and account will stay as they are.") },
        confirmButton = { TextButton(onClick = { viewModel.disableLeaderboard(); confirmLeave = false }) { Text("Leave board") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
    )
}
