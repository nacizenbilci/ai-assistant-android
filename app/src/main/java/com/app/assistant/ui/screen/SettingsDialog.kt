package com.app.assistant.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
@Suppress("ktlint:standard:function-naming")
fun SettingsDialog(
    initialYoutubeKey: String,
    initialChatKey: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var apiKey1 by rememberSaveable { mutableStateOf(initialYoutubeKey) }
    var apiKey2 by rememberSaveable { mutableStateOf(initialChatKey) }

    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                TextField(
                    value = apiKey1,
                    onValueChange = { apiKey1 = it },
                    label = { Text("YouTube API Key") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = apiKey2,
                    onValueChange = { apiKey2 = it },
                    label = { Text("Chat API Key") },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("App Version: $versionName")
            }
        },
        confirmButton = {
            Button(onClick = { onSave(apiKey1, apiKey2) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
