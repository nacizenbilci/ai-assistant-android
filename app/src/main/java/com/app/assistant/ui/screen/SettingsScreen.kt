package com.app.assistant.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.assistant.R
import com.app.assistant.llm.LlmProvider
import com.app.assistant.viewmodel.SettingsViewModel
import com.app.assistant.viewmodel.VerificationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
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

    var hasChanged by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val verificationState by settingsViewModel.verificationState.collectAsState()
    val fetchedModelsMap by settingsViewModel.fetchedModels.collectAsState()
    val isFetchingModels by settingsViewModel.isFetchingModels.collectAsState()
    val modelFetchError by settingsViewModel.modelFetchError.collectAsState()
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.resetVerificationState()
        val currentKey = initialChatKey
        if (currentKey.isNotBlank() || initialProvider == LlmProvider.OLLAMA || initialProvider == LlmProvider.OPEN_ROUTER) {
            settingsViewModel.fetchModelsForProvider(
                provider = initialProvider,
                apiKey = currentKey,
                customUrl = initialCustomUrl,
                customHeaders = initialCustomHeaders
            )
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
                            enabled = !hasChanged || verificationState is VerificationState.Success,
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
                                        if (provider != p) {
                                            provider = p
                                            hasChanged = true
                                            settingsViewModel.resetVerificationState()
                                            if (p != LlmProvider.CUSTOM) {
                                                model = p.defaultModel
                                                val currentKey = apiKey2
                                                if (currentKey.isNotBlank() || p == LlmProvider.OLLAMA || p == LlmProvider.OPEN_ROUTER) {
                                                    settingsViewModel.fetchModelsForProvider(p, currentKey)
                                                }
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
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val modelsList = fetchedModelsMap[provider] ?: provider.suggestedModels

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { 
                                model = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
                            label = { Text(stringResource(id = R.string.llm_model)) },
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = if (!modelFetchError.isNullOrBlank()) {
                                { Text(modelFetchError ?: "", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            trailingIcon = {
                                IconButton(onClick = { modelDropdownExpanded = !modelDropdownExpanded }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_arrow_drop_down),
                                        contentDescription = "Show suggested models"
                                    )
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isFetchingModels) {
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Fetching models...")
                                        }
                                    },
                                    onClick = {}
                                )
                            } else {
                                if (provider != LlmProvider.CUSTOM) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_restart),
                                                    contentDescription = "Fetch latest",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text("Fetch latest models from API")
                                            }
                                        },
                                        onClick = {
                                            settingsViewModel.fetchModelsForProvider(
                                                provider = provider,
                                                apiKey = apiKey2,
                                                customUrl = customUrl,
                                                customHeaders = customHeaders
                                            )
                                        }
                                    )
                                    if (modelsList.isNotEmpty()) {
                                        androidx.compose.material3.HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }

                                if (modelsList.isNotEmpty()) {
                                    modelsList.forEach { suggested ->
                                        DropdownMenuItem(
                                            text = { Text(suggested) },
                                            onClick = {
                                                model = suggested
                                                hasChanged = true
                                                settingsViewModel.resetVerificationState()
                                                modelDropdownExpanded = false
                                            }
                                        )
                                    }
                                } else if (provider == LlmProvider.CUSTOM) {
                                    DropdownMenuItem(
                                        text = { Text("No suggested models. Please enter your model name.") },
                                        onClick = { modelDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey2,
                        onValueChange = { 
                            apiKey2 = it
                            hasChanged = true
                            settingsViewModel.resetVerificationState()
                        },
                        label = { Text(stringResource(id = R.string.chat_api_key)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Verification Section
                    VerifySection(
                        verificationState = verificationState,
                        onVerify = {
                            settingsViewModel.verifyModelAndSaveCapabilities(
                                provider = provider,
                                model = model,
                                apiKey = apiKey2,
                                customUrl = customUrl,
                                customHeaders = customHeaders,
                                customResponsePath = customResponsePath,
                                customRequestTemplate = customRequestTemplate,
                                customMessageFormat = customMessageFormat,
                                customSystemRole = customSystemRole,
                                customUserRole = customUserRole,
                                customAssistantRole = customAssistantRole
                            )
                            hasChanged = false
                        }
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
                            onValueChange = { 
                                customUrl = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
                            label = { Text("API Endpoint URL") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customHeaders,
                            onValueChange = { 
                                customHeaders = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
                            label = { Text("Custom Headers (JSON)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customResponsePath,
                            onValueChange = { 
                                customResponsePath = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
                            label = { Text("Response Extraction JSON Path") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customRequestTemplate,
                            onValueChange = { 
                                customRequestTemplate = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
                            label = { Text("Request Body JSON Template") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 10
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customMessageFormat,
                            onValueChange = { 
                                customMessageFormat = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
                            label = { Text("Single Message Format (JSON)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customSystemRole,
                            onValueChange = { 
                                customSystemRole = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
                            label = { Text("System Role Mapping") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customUserRole,
                            onValueChange = { 
                                customUserRole = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
                            label = { Text("User Role Mapping") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customAssistantRole,
                            onValueChange = { 
                                customAssistantRole = it
                                hasChanged = true
                                settingsViewModel.resetVerificationState()
                            },
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
                        onValueChange = { 
                            apiKey1 = it
                            hasChanged = true
                        },
                        label = { Text(stringResource(id = R.string.youtube_api_key)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun VerifySection(
    verificationState: VerificationState,
    onVerify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        when (verificationState) {
            is VerificationState.Idle -> {
                Button(
                    onClick = onVerify,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verify & Detect Capabilities")
                }
            }
            is VerificationState.Verifying -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Verifying connection and capabilities...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            is VerificationState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verification Successful!",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Detected Model Capabilities:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        CapabilityRow("Text Completion Support", true)
                        CapabilityRow("Vision / Image Support", verificationState.capabilities.hasImageInput)
                        CapabilityRow("Audio Support", verificationState.capabilities.hasAudioInput)
                        CapabilityRow("Video Support", verificationState.capabilities.hasVideoInput)
                        CapabilityRow("Document Support (PDF/TXT)", verificationState.capabilities.hasDocumentInput)

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onVerify,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Re-Verify Settings")
                        }
                    }
                }
            }
            is VerificationState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verification Failed",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = verificationState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onVerify,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retry Verification")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityRow(label: String, supported: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (supported) Icons.Default.Check else Icons.Default.Close,
            contentDescription = if (supported) "Supported" else "Unsupported",
            tint = if (supported) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
