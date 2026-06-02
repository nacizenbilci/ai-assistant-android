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
import com.app.assistant.ui.theme.AssistantTheme
import com.app.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.launch

import android.widget.Toast
import kotlinx.coroutines.flow.collectLatest
import com.app.assistant.model.Group
import com.app.assistant.model.Conversation
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupUI(viewModel: MainViewModel) {
    AssistantTheme {
        val showBottomSheet by viewModel.showBottomSheet.collectAsState()
        val isCustomUI by viewModel.isCustomUI.collectAsState()
        val isCustomUIHalfPage by viewModel.isCustomUIHalfPage.collectAsState()
        val groupList by viewModel.groupList.collectAsState(initial = emptyList())
        val chatList = viewModel.chatList
        val isSpeaking by viewModel.isSpeaking.collectAsState()
        val isListening by viewModel.isListening.collectAsState()
        val question by viewModel.question.collectAsState()
        val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsState()
        val isLanguageLoading by viewModel.isLanguageLoading.collectAsState()
        val languages = viewModel.languages
        val youtubeKey = remember(viewModel) { viewModel.loadYoutubeKey() ?: "" }
        val chatKey = remember(viewModel) { viewModel.loadChatKey() ?: "" }

        val context = LocalContext.current
        LaunchedEffect(Unit) {
            viewModel.showToastEvent.collectLatest { message: String ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        val clipboardManager = LocalClipboardManager.current

        ChatScreenContent(
            isCustomUI = isCustomUI,
            isCustomUIHalfPage = isCustomUIHalfPage,
            groupList = groupList,
            chatList = chatList,
            isSpeaking = isSpeaking,
            isListening = isListening,
            question = question,
            showBottomSheet = showBottomSheet,
            isTranslationEnabled = isTranslationEnabled,
            isLanguageLoading = isLanguageLoading,
            languages = languages,
            youtubeKey = youtubeKey,
            chatKey = chatKey,
            onGroupClick = { viewModel.loadMessagesFromGroup(it) },
            onSettingsClick = { /* Handled in child SettingsDialog */ },
            onNewChatClick = { viewModel.newChat() },
            onCopyClick = { index ->
                val textToCopy = when {
                    viewModel.getIsTranslationEnabled() -> {
                        viewModel.chatList.getOrNull(index)?.translatedText
                    }
                    else -> {
                        viewModel.chatList.getOrNull(index)?.englishText
                    }
                } ?: ""
                clipboardManager.setText(AnnotatedString(textToCopy))
            },
            onDeleteClick = { viewModel.deleteMessage(it) },
            onClearChatClick = { viewModel.clearBoxes() },
            onTranslateClick = { viewModel.setShowBottomSheet(true) },
            onMenuClick = { /* Handled inside drawer state toggling */ },
            onDragEnd = { viewModel.expandToFullScreen() },
            onQuestionChange = { viewModel.setQuestion(it) },
            onStopSpeaking = { viewModel.stopTextToSpeech() },
            onStartListening = { onResult, onPartialResult ->
                viewModel.startSpeechToText(onResult, onPartialResult)
            },
            onProcessQuestion = { fm, kc, isVoice ->
                viewModel.processQuestion(fm, kc, isVoice)
            },
            onTranslationEnabledChange = { viewModel.updateTranslationEnabled(it) },
            onLanguageSelected = { viewModel.onItemSelected(it) },
            onDismissBottomSheet = { viewModel.setShowBottomSheet(false) },
            onSaveSettings = { ytKey, chKey -> viewModel.saveKeys(ytKey, chKey) },
            onBackPressed = { /* Handled in parent context back handler */ }
        )
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
    question: String,
    showBottomSheet: Boolean,
    isTranslationEnabled: Boolean,
    isLanguageLoading: Boolean,
    languages: List<Pair<String, String>>,
    youtubeKey: String,
    chatKey: String,
    onGroupClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onCopyClick: (Int) -> Unit,
    onDeleteClick: (Int) -> Unit,
    onClearChatClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDragEnd: () -> Unit,
    onQuestionChange: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartListening: (onResult: (String) -> Unit, onPartialResult: (String) -> Unit) -> Unit,
    onProcessQuestion: (FocusManager, SoftwareKeyboardController, Boolean) -> Unit,
    onTranslationEnabledChange: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onDismissBottomSheet: () -> Unit,
    onSaveSettings: (String, String) -> Unit,
    onBackPressed: () -> Unit,
) {
    var showCopyIcon by remember { mutableStateOf(false) }
    var selectedItemIndex by remember { mutableStateOf<Int?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var clearShowDialog by remember { mutableStateOf(false) }
    var deleteShowDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()
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
                    onGroupClick = { groupId ->
                        onGroupClick(groupId)
                        scope.launch { drawerState.close() }
                    },
                    onSettingsClick = {
                        showSettingsDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onNewChatClick = {
                        onNewChatClick()
                        scope.launch { drawerState.close() }
                    }
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
                            onTranslateClick = onTranslateClick,
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
                MyLayout(
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
                    question = question,
                    onQuestionChange = onQuestionChange,
                    onStopSpeaking = onStopSpeaking,
                    onStartListening = onStartListening,
                    onProcessQuestion = onProcessQuestion,
                    isTranslateEnabled = isTranslationEnabled,
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

    if (showBottomSheet) {
        LanguageSelectionBottomSheet(
            sheetState = sheetState,
            languages = languages,
            isTranslationEnabled = isTranslationEnabled,
            isLanguageLoading = isLanguageLoading,
            onTranslationEnabledChange = onTranslationEnabledChange,
            onLanguageSelected = onLanguageSelected,
            onDismissRequest = onDismissBottomSheet
        )
    }

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

    if (showSettingsDialog) {
        SettingsDialog(
            initialYoutubeKey = youtubeKey,
            initialChatKey = chatKey,
            onDismiss = { showSettingsDialog = false },
            onSave = { ytKey, chKey ->
                onSaveSettings(ytKey, chKey)
                showSettingsDialog = false
            },
        )
    }

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
                    englishText = "Hi! How can I configure my API keys?",
                    translatedText = "Salut! Comment puis-je configurer mes clés API?",
                    isMe = true
                ),
                Conversation(
                    englishText = "You can configure them by opening settings in the side drawer menu.",
                    translatedText = "Vous pouvez les configurer en ouvrant les paramètres.",
                    isMe = false
                )
            ),
            isSpeaking = false,
            isListening = false,
            question = "",
            showBottomSheet = false,
            isTranslationEnabled = false,
            isLanguageLoading = false,
            languages = listOf("English" to "en", "French" to "fr"),
            youtubeKey = "",
            chatKey = "",
            onGroupClick = {},
            onSettingsClick = {},
            onNewChatClick = {},
            onCopyClick = {},
            onDeleteClick = {},
            onClearChatClick = {},
            onTranslateClick = {},
            onMenuClick = {},
            onDragEnd = {},
            onQuestionChange = {},
            onStopSpeaking = {},
            onStartListening = { _, _ -> },
            onProcessQuestion = { _, _, _ -> },
            onTranslationEnabledChange = {},
            onLanguageSelected = {},
            onDismissBottomSheet = {},
            onSaveSettings = { _, _ -> },
            onBackPressed = {}
        )
    }
}

