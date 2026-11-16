package com.oriol.letsstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun ContentReportDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val reasons = listOf(
        "unsafe" to "Unsafe or harmful",
        "inappropriate" to "Inappropriate or abusive",
        "incorrect" to "Incorrect or misleading",
        "other" to "Other concern",
    )
    var selected by rememberSaveable(title) { mutableStateOf("unsafe") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose why you are reporting this. A short excerpt is sent to Valls Solutions for review.", style = MaterialTheme.typography.bodyMedium)
                reasons.forEach { (value, label) ->
                    TextButton(onClick = { selected = value }) {
                        RadioButton(selected = selected == value, onClick = null)
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(selected) }) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
