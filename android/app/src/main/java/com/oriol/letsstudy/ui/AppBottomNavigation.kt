package com.oriol.letsstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppDestination {
    HOME,
    LIBRARY,
    PROGRESS,
    NEW_STUDY,
    SESSION,
    JOBS,
    HISTORY,
    SAVED,
    SETTINGS,
    JOB_PREP,
    TOPIC,
    MATERIAL,
}

private data class NavigationItem(
    val label: String,
    val icon: ImageVector,
    val destination: AppDestination,
    val selected: (AppDestination) -> Boolean,
)

@Composable
fun AppBottomNavigation(destination: AppDestination, onNavigate: (AppDestination) -> Unit) {
    val items = listOf(
        NavigationItem("Home", Icons.Outlined.Home, AppDestination.HOME) { it == AppDestination.HOME },
        NavigationItem("Studies", Icons.AutoMirrored.Outlined.MenuBook, AppDestination.LIBRARY) { it == AppDestination.LIBRARY || it == AppDestination.SESSION },
        NavigationItem("New", Icons.Outlined.Add, AppDestination.NEW_STUDY) { it in setOf(AppDestination.NEW_STUDY, AppDestination.TOPIC, AppDestination.MATERIAL) },
        NavigationItem("Jobs", Icons.Outlined.WorkOutline, AppDestination.JOBS) { it == AppDestination.JOBS || it == AppDestination.JOB_PREP },
        NavigationItem("Progress", Icons.AutoMirrored.Outlined.ShowChart, AppDestination.PROGRESS) { it == AppDestination.PROGRESS },
    )
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = LetsStudyColors.Border, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = item.selected(destination)
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable(role = Role.Tab, onClickLabel = item.label, onClick = { onNavigate(item.destination) })
                            .semantics { this.selected = selected },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (selected) LetsStudyColors.Primary else LetsStudyColors.Muted,
                            modifier = Modifier.size(if (item.label == "New") 22.dp else 19.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label,
                            color = if (selected) LetsStudyColors.Primary else LetsStudyColors.Muted,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
