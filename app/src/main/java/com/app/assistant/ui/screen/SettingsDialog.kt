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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.assistant.R

import androidx.compose.ui.tooling.preview.Preview
import com.app.assistant.ui.theme.AssistantTheme
import androidx.compose.runtime.remember

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
    val versionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.settings_title)) },
        text = {
            Column {
                TextField(
                    value = apiKey1,
                    onValueChange = { apiKey1 = it },
                    label = { Text(stringResource(id = R.string.youtube_api_key)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = apiKey2,
                    onValueChange = { apiKey2 = it },
                    label = { Text(stringResource(id = R.string.chat_api_key)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(id = R.string.app_version, versionName))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(apiKey1, apiKey2) }) {
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsDialogPreview() {
    AssistantTheme {
        SettingsDialog(
            initialYoutubeKey = "AIzaSyFakeKey123",
            initialChatKey = "gsk_FakeKey456",
            onDismiss = {},
            onSave = { _, _ -> }
        )
    }
}
