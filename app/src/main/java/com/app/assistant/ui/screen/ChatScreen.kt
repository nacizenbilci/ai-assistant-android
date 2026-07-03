package com.app.assistant.ui.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.app.assistant.R
import com.app.assistant.llm.LlmProvider
import com.app.assistant.ui.theme.AssistantTheme
import com.app.assistant.viewmodel.MainViewModel
import com.app.assistant.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.collectLatest
import com.app.assistant.model.Group
import com.app.assistant.model.Conversation
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupUI(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onToggleVisionMode: () -> Unit,
    onToggleScreenMode: () -> Unit
) {
    AssistantTheme {
        val isCustomUI by viewModel.isCustomUI.collectAsState()
        val isCustomUIHalfPage by viewModel.isCustomUIHalfPage.collectAsState()
        val isVisionModeActive by viewModel.isVisionModeActive.collectAsState()
        val isScreenModeActive by viewModel.isScreenModeActive.collectAsState()
        val groupList by viewModel.groupList.collectAsState(initial = emptyList())
        val chatList = viewModel.chatList
        val isSpeaking by viewModel.isSpeaking.collectAsState()
        val isListening by viewModel.isListening.collectAsState()
        val isVoiceProcessing by viewModel.isVoiceProcessing.collectAsState()
        val isHandsFree by viewModel.isHandsFreeModeActive.collectAsState()
        val isMicMuted by viewModel.isMicMuted.collectAsState()
        val question by viewModel.question.collectAsState()
        val isTranslationEnabled by settingsViewModel.isTranslationEnabled.collectAsState()
        val isDeletingChat by viewModel.isDeletingChat.collectAsState()
        val audioAmplitude by viewModel.audioAmplitude.collectAsState()


        var currentScreen by remember { mutableStateOf("chat") }

        val selectedAttachments by viewModel.selectedAttachments.collectAsState()
        val isImageSupported = remember(currentScreen) { viewModel.settingsRepository.getIsImageSupported() }
        val isAudioSupported = remember(currentScreen) { viewModel.settingsRepository.getIsAudioSupported() }
        val isVideoSupported = remember(currentScreen) { viewModel.settingsRepository.getIsVideoSupported() }
        val isDocumentSupported = remember(currentScreen) { viewModel.settingsRepository.getIsDocumentSupported() }

        val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.addSelectedAttachment(it) }
        }

        val context = LocalContext.current

        var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
        val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                tempCameraUri?.let { uri ->
                    viewModel.addSelectedAttachment(uri)
                }
            }
        }

        fun launchCamera() {
            try {
                val file = java.io.File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                val authority = "${context.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                android.util.Log.e("ChatScreen", "Failed to launch camera", e)
                Toast.makeText(context, "Failed to launch camera", Toast.LENGTH_SHORT).show()
            }
        }

        val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(Unit) {
            viewModel.showToastEvent.collectLatest { message: String ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        val clipboardManager = LocalClipboardManager.current

        if (currentScreen == "settings") {
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onBack = { currentScreen = "chat" },
                onTranslationEnabledChange = { enabled ->
                    settingsViewModel.updateTranslationEnabled(enabled)
                },
                onLanguageSelected = { langCode ->
                    settingsViewModel.updateActiveLanguageCode(langCode)
                }
            )
        } else {
            ChatScreenContent(
                isCustomUI = isCustomUI,
                isCustomUIHalfPage = isCustomUIHalfPage,
                groupList = groupList,
                chatList = chatList,
                isSpeaking = isSpeaking,
                isListening = isListening,
                audioAmplitude = audioAmplitude,
                isHandsFree = isHandsFree,
                isVoiceProcessing = isVoiceProcessing,
                question = question,
                isTranslationEnabled = isTranslationEnabled,
                isDeletingChat = isDeletingChat,
                onDeleteGroupClick = { viewModel.deleteGroup(it) },
                onGroupClick = { viewModel.loadMessagesFromGroup(it) },
                onSettingsClick = { currentScreen = "settings" },
                onNewChatClick = { viewModel.newChat() },
                onCopyClick = { index ->
                    val conversation = viewModel.chatList.getOrNull(index)
                    val textToCopy = conversation?.text ?: ""
                    clipboardManager.setText(AnnotatedString(textToCopy))
                },
                onDeleteClick = { viewModel.deleteMessage(it) },
                onClearChatClick = { viewModel.clearBoxes() },
                onMenuClick = { /* Handled inside drawer state toggling */ },
                onDragEnd = { viewModel.expandToFullScreen() },
                onQuestionChange = { viewModel.setQuestion(it) },
                onStopSpeaking = { viewModel.stopTextToSpeech() },
                onStartListening = {
                    viewModel.startSpeechRecognition()
                },
                onProcessQuestion = { fm, kc, isVoice ->
                    viewModel.processQuestion(fm, kc, isVoice)
                },
                onBackPressed = { /* Handled in parent context back handler */ },
                selectedAttachments = selectedAttachments,
                onRemoveAttachment = { viewModel.removeSelectedAttachment(it) },
                onAttachClick = { type ->
                    if (type == "camera") {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    } else {
                        pickerLauncher.launch(type)
                    }
                },
                isImageSupported = isImageSupported,
                isAudioSupported = isAudioSupported,
                isVideoSupported = isVideoSupported,
                isDocumentSupported = isDocumentSupported,
                onToggleHandsFree = { viewModel.toggleHandsFreeMode() },
                onExitHandsFree = { viewModel.setHandsFreeModeActive(false) },
                isMicMuted = isMicMuted,
                onToggleMicMute = { viewModel.toggleMicMute() },
                isVisionModeActive = isVisionModeActive,
                onToggleVisionMode = onToggleVisionMode,
                isScreenModeActive = isScreenModeActive,
                onToggleScreenMode = onToggleScreenMode
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    isCustomUI: Boolean,
    isCustomUIHalfPage: Boolean,
    groupList: List<Group>,
    chatList: List<Conversation>,
    isSpeaking: Boolean,
    isListening: Boolean,
    audioAmplitude: Float = 0f,
    question: String,
    isTranslationEnabled: Boolean,
    isDeletingChat: Boolean,
    onGroupClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onCopyClick: (Int) -> Unit,
    onDeleteClick: (Int) -> Unit,
    onClearChatClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDragEnd: () -> Unit,
    onQuestionChange: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartListening: () -> Unit,
    onProcessQuestion: (FocusManager, SoftwareKeyboardController, Boolean) -> Unit,
    onBackPressed: () -> Unit,
    onDeleteGroupClick: (Long) -> Unit,
    selectedAttachments: List<com.app.assistant.model.Attachment> = emptyList(),
    onRemoveAttachment: (com.app.assistant.model.Attachment) -> Unit = {},
    onAttachClick: (String) -> Unit = {},
    isImageSupported: Boolean = false,
    isAudioSupported: Boolean = false,
    isVideoSupported: Boolean = false,
    isDocumentSupported: Boolean = false,
    isHandsFree: Boolean = false,
    onToggleHandsFree: () -> Unit = {},
    onExitHandsFree: () -> Unit = {},
    isVoiceProcessing: Boolean = false,
    isMicMuted: Boolean = false,
    onToggleMicMute: () -> Unit = {},
    isVisionModeActive: Boolean = false,
    onToggleVisionMode: () -> Unit = {},
    isScreenModeActive: Boolean = false,
    onToggleScreenMode: () -> Unit = {},
) {
    var showCopyIcon by remember { mutableStateOf(false) }
    var selectedItemIndex by remember { mutableStateOf<Int?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var clearShowDialog by remember { mutableStateOf(false) }
    var deleteShowDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val drawerState = remember(isCustomUI) {
        DrawerState(DrawerValue.Closed)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            if (!isCustomUI) {
                ChatDrawerContent(
                    groupList = groupList,
                    isDeletingChat = isDeletingChat,
                    onGroupClick = { groupId ->
                        onGroupClick(groupId)
                        scope.launch { drawerState.close() }
                    },
                    onSettingsClick = {
                        onSettingsClick()
                        scope.launch { drawerState.close() }
                    },
                    onNewChatClick = {
                        onNewChatClick()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteGroupClick = onDeleteGroupClick
                )
            }
        },
    ) {
        val scaffoldContent = @Composable {
            Scaffold(
                containerColor = if (isCustomUIHalfPage) Color.Transparent else MaterialTheme.colorScheme.background,
                modifier =
                    if (isCustomUI) {
                        Modifier
                            .height(screenHeight / 2)
                            .padding(16.dp, 16.dp, 16.dp, 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Transparent, shape = RoundedCornerShape(16.dp))
                            .clickable {
                                if (!isCustomUIHalfPage) {
                                    Modifier.clickable(onClick = {})
                                } else {
                                    backPressedDispatcher?.onBackPressed()
                                }
                            }
                    } else {
                        Modifier
                    },
                topBar = {
                    if (!isCustomUI) {
                        ChatTopAppBar(
                            showCopyIcon = showCopyIcon,
                            onCopyClick = {
                                selectedItemIndex?.let { index ->
                                    onCopyClick(index)
                                    selectedItemIndex = null
                                }
                            },
                            onDeleteClick = { deleteShowDialog = true },
                            chatListNotEmpty = chatList.isNotEmpty(),
                            onClearChatClick = { clearShowDialog = true },
                            onMenuClick = {
                                scope.launch {
                                    drawerState.apply {
                                        if (isClosed) open() else close()
                                    }
                                }
                            }
                        )
                    }
                    if (!isCustomUIHalfPage && isCustomUI) {
                        CustomUiDragHandle(
                            onDragEnd = onDragEnd
                        )
                    }
                },
            ) { innerPadding ->
                ChatLayout(
                    modifier = Modifier.padding(innerPadding),
                    onShowCopyIconChange = { newValue ->
                        showCopyIcon = newValue
                    },
                    selectedItemIndex = selectedItemIndex,
                    onSelectedItemChange = { newIndex -> selectedItemIndex = newIndex },
                    isCustomUI = isCustomUI,
                    chatList = chatList,
                    isSpeaking = isSpeaking,
                    isListening = isListening,
                    audioAmplitude = audioAmplitude,
                    question = question,
                    onQuestionChange = onQuestionChange,
                    onStopSpeaking = onStopSpeaking,
                    onStartListening = onStartListening,
                    onProcessQuestion = onProcessQuestion,
                    isTranslateEnabled = isTranslationEnabled,
                    selectedAttachments = selectedAttachments,
                    onRemoveAttachment = onRemoveAttachment,
                    onAttachClick = onAttachClick,
                    isImageSupported = isImageSupported,
                    isAudioSupported = isAudioSupported,
                    isVideoSupported = isVideoSupported,
                    isDocumentSupported = isDocumentSupported,
                    isHandsFree = isHandsFree,
                    onToggleHandsFree = onToggleHandsFree,
                    onExitHandsFree = onExitHandsFree,
                    isVoiceProcessing = isVoiceProcessing,
                    isMicMuted = isMicMuted,
                    onToggleMicMute = onToggleMicMute,
                    isVisionModeActive = isVisionModeActive,
                    onToggleVisionMode = onToggleVisionMode,
                    isScreenModeActive = isScreenModeActive,
                    onToggleScreenMode = onToggleScreenMode,
                )
            }
        }

        if (isCustomUI) {
            var tapped by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                tapped = false
            }

            val animatedBoxAlpha by animateFloatAsState(
                targetValue = if (tapped) 0.0f else 0.3f,
                animationSpec = tween(durationMillis = 100), // Quick animation
                label = "boxBackgroundAlpha",
                finishedListener = {
                    if (tapped) {
                        backPressedDispatcher?.onBackPressed()
                    }
                },
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = animatedBoxAlpha))
                        .imePadding()
                        .animateContentSize()
                        .clickable(
                            enabled = !tapped,
                            onClick = {
                                tapped = true
                            },
                        ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                scaffoldContent()
            }
        } else {
            scaffoldContent()
        }
    }

    // Translation Bottom Sheet removed (moved to Settings)

    if (clearShowDialog) {
        ClearChatDialog(
            onConfirm = {
                onClearChatClick()
                clearShowDialog = false
            },
            onDismiss = { clearShowDialog = false }
        )
    }

    if (deleteShowDialog) {
        DeleteMessageDialog(
            onConfirm = {
                selectedItemIndex?.let { index ->
                    onDeleteClick(index)
                }
                deleteShowDialog = false
                selectedItemIndex = null
            },
            onDismiss = {
                deleteShowDialog = false
                selectedItemIndex = null
            }
        )
    }

    // SettingsDialog removed

    BackHandler(enabled = selectedItemIndex != null) {
        selectedItemIndex = null
    }


}



@Preview(showBackground = true)
@Composable
fun ChatScreenContentPreview() {
    AssistantTheme {
        ChatScreenContent(
            isCustomUI = false,
            isCustomUIHalfPage = false,
            groupList = listOf(
                Group(1L, "Recent Conversation 1"),
                Group(2L, "Recent Conversation 2")
            ),
            chatList = listOf(
                Conversation(
                    text = "Hi! How can I configure my API keys?",
                    isMe = true
                ),
                Conversation(
                    text = "You can configure them by opening settings in the side drawer menu.",
                    isMe = false
                )
            ),
            isSpeaking = false,
            isListening = false,
            isHandsFree = true,
            question = "",
            isTranslationEnabled = false,
            isDeletingChat = false,
            onGroupClick = {},
            onSettingsClick = {},
            onNewChatClick = {},
            onCopyClick = {},
            onDeleteClick = {},
            onClearChatClick = {},
            onMenuClick = {},
            onDragEnd = {},
            onQuestionChange = {},
            onStopSpeaking = {},
            onStartListening = {},
            onProcessQuestion = { _, _, _ -> },
            onBackPressed = {},
            onDeleteGroupClick = {}
        )
    }
}

