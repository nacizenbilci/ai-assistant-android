package com.app.assistant.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.assistant.R
import com.app.assistant.llm.LlmProvider
import com.app.assistant.ui.theme.AssistantTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialYoutubeKey: String,
    initialChatKey: String,
    initialProvider: LlmProvider,
    initialModel: String,
    initialCustomUrl: String,
    initialCustomHeaders: String,
    initialCustomResponsePath: String,
    initialCustomRequestTemplate: String,
    initialCustomMessageFormat: String,
    initialCustomSystemRole: String,
    initialCustomUserRole: String,
    initialCustomAssistantRole: String,
    onBack: () -> Unit,
    onSave: (String, String, LlmProvider, String, String, String, String, String, String, String, String, String) -> Unit,
) {
    var apiKey1 by rememberSaveable { mutableStateOf(initialYoutubeKey) }
    var apiKey2 by rememberSaveable { mutableStateOf(initialChatKey) }
    var provider by rememberSaveable { mutableStateOf(initialProvider) }
    var model by rememberSaveable { mutableStateOf(initialModel) }
    
    var customUrl by rememberSaveable { mutableStateOf(initialCustomUrl) }
    var customHeaders by rememberSaveable { mutableStateOf(initialCustomHeaders) }
    var customResponsePath by rememberSaveable { mutableStateOf(initialCustomResponsePath) }
    var customRequestTemplate by rememberSaveable { mutableStateOf(initialCustomRequestTemplate) }
    var customMessageFormat by rememberSaveable { mutableStateOf(initialCustomMessageFormat) }
    var customSystemRole by rememberSaveable { mutableStateOf(initialCustomSystemRole) }
    var customUserRole by rememberSaveable { mutableStateOf(initialCustomUserRole) }
    var customAssistantRole by rememberSaveable { mutableStateOf(initialCustomAssistantRole) }

    var expanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                onSave(
                                    apiKey1,
                                    apiKey2,
                                    provider,
                                    model,
                                    customUrl,
                                    customHeaders,
                                    customResponsePath,
                                    customRequestTemplate,
                                    customMessageFormat,
                                    customSystemRole,
                                    customUserRole,
                                    customAssistantRole
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.save))
                        }
                    }
                    Text(
                        text = stringResource(id = R.string.app_version, versionName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // CARD 1: AI MODEL SETTINGS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI Model Provider",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = provider.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(id = R.string.llm_provider)) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_drop_down),
                                    contentDescription = null
                                )
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expanded = true }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LlmProvider.entries.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.displayName) },
                                    onClick = {
                                        provider = p
                                        if (p != LlmProvider.CUSTOM) {
                                            model = p.defaultModel
                                        } else {
                                            model = p.defaultModel
                                            if (customUrl.isBlank()) customUrl = p.config.url
                                            if (customHeaders.isBlank()) customHeaders = "{\"Authorization\": \"Bearer {{API_KEY}}\", \"Content-Type\": \"application/json\"}"
                                            if (customResponsePath.isBlank()) customResponsePath = p.config.responsePath
                                            if (customRequestTemplate.isBlank()) customRequestTemplate = p.config.requestTemplate
                                            if (customMessageFormat.isBlank()) customMessageFormat = p.config.messageFormat
                                            if (customSystemRole.isBlank()) customSystemRole = p.config.systemRole ?: "system"
                                            if (customUserRole.isBlank()) customUserRole = p.config.userRole
                                            if (customAssistantRole.isBlank()) customAssistantRole = p.config.assistantRole
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(id = R.string.llm_model)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey2,
                        onValueChange = { apiKey2 = it },
                        label = { Text(stringResource(id = R.string.chat_api_key)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // CARD 2: CUSTOM PROVIDER DETAILS (ONLY RENDERED IF CUSTOM SELECTED)
            if (provider == LlmProvider.CUSTOM) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Custom Endpoint Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { customUrl = it },
                            label = { Text("API Endpoint URL") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customHeaders,
                            onValueChange = { customHeaders = it },
                            label = { Text("Custom Headers (JSON)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customResponsePath,
                            onValueChange = { customResponsePath = it },
                            label = { Text("Response Extraction JSON Path") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customRequestTemplate,
                            onValueChange = { customRequestTemplate = it },
                            label = { Text("Request Body JSON Template") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 10
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customMessageFormat,
                            onValueChange = { customMessageFormat = it },
                            label = { Text("Single Message Format (JSON)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customSystemRole,
                            onValueChange = { customSystemRole = it },
                            label = { Text("System Role Mapping") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customUserRole,
                            onValueChange = { customUserRole = it },
                            label = { Text("User Role Mapping") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customAssistantRole,
                            onValueChange = { customAssistantRole = it },
                            label = { Text("Assistant Role Mapping") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // CARD 3: INTEGRATIONS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Integrations",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey1,
                        onValueChange = { apiKey1 = it },
                        label = { Text(stringResource(id = R.string.youtube_api_key)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    AssistantTheme {
        SettingsScreen(
            initialYoutubeKey = "AIzaSyFakeKey123",
            initialChatKey = "gsk_FakeKey456",
            initialProvider = LlmProvider.GROQ,
            initialModel = "llama-3.3-70b-versatile",
            initialCustomUrl = "",
            initialCustomHeaders = "",
            initialCustomResponsePath = "",
            initialCustomRequestTemplate = "",
            initialCustomMessageFormat = "",
            initialCustomSystemRole = "",
            initialCustomUserRole = "",
            initialCustomAssistantRole = "",
            onBack = {},
            onSave = { _, _, _, _, _, _, _, _, _, _, _, _ -> }
        )
    }
}
