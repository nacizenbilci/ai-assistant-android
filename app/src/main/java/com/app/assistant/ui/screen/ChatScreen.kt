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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.app.assistant.R
import com.app.assistant.ui.theme.AssistantTheme
import com.app.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupUI(viewModel: MainViewModel) {
    AssistantTheme {
        var showCopyIcon by remember { mutableStateOf(false) }
        var selectedItemIndex by remember { mutableStateOf<Int?>(null) }
        val clipboardManager = LocalClipboardManager.current
        var clearShowDialog by remember { mutableStateOf(false) }
        var deleteShowDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }

        val sheetState = rememberModalBottomSheetState()
        val showBottomSheet by viewModel.showBottomSheet.collectAsState()

        val isCustomUI by viewModel.isCustomUI.collectAsState()
        val isCustomUIHalfPage by viewModel.isCustomUIHalfPage.collectAsState()
        val drawerState =
            remember(isCustomUI) {
                DrawerState(DrawerValue.Closed)
            }
        val scope = rememberCoroutineScope()
        val groupList by viewModel.groupList.collectAsState(initial = emptyList())

        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                if (!isCustomUI) {
                    ChatDrawerContent(
                        groupList = groupList,
                        onGroupClick = { groupId ->
                            viewModel.loadMessagesFromGroup(groupId)
                            scope.launch { drawerState.close() }
                        },
                        onSettingsClick = {
                            showSettingsDialog = true
                            scope.launch { drawerState.close() }
                        },
                        onNewChatClick = {
                            viewModel.newChat()
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
                                        val textToCopy =
                                            when {
                                                viewModel.getIsTranslationEnabled() -> {
                                                    viewModel.chatList.getOrNull(index)?.translatedText
                                                }
                                                else -> {
                                                    viewModel.chatList.getOrNull(index)?.englishText
                                                }
                                            } ?: ""
                                        clipboardManager.setText(AnnotatedString(textToCopy))
                                        selectedItemIndex = null
                                    }
                                },
                                onDeleteClick = { deleteShowDialog = true },
                                onTranslateClick = { viewModel.setShowBottomSheet(true) },
                                chatListNotEmpty = viewModel.chatList.isNotEmpty(),
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
                                onDragEnd = { viewModel.expandToFullScreen() }
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
                        viewModel = viewModel,
                        isCustomUI = isCustomUI,
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
            val isLanguageLoading by viewModel.isLanguageLoading.collectAsState()
            val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsState()

            LanguageSelectionBottomSheet(
                sheetState = sheetState,
                languages = viewModel.languages,
                isTranslationEnabled = isTranslationEnabled,
                isLanguageLoading = isLanguageLoading,
                onTranslationEnabledChange = { viewModel.updateTranslationEnabled(it) },
                onLanguageSelected = { viewModel.onItemSelected(it) },
                onDismissRequest = { viewModel.setShowBottomSheet(false) }
            )
        }

        if (clearShowDialog) {
            ClearChatDialog(
                onConfirm = {
                    viewModel.clearBoxes()
                    clearShowDialog = false
                },
                onDismiss = { clearShowDialog = false }
            )
        }

        if (deleteShowDialog) {
            DeleteMessageDialog(
                onConfirm = {
                    selectedItemIndex?.let { index ->
                        viewModel.deleteMessage(index)
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
            val youtubeKey = viewModel.loadYoutubeKey() ?: ""
            val chatKey = viewModel.loadChatKey() ?: ""

            SettingsDialog(
                initialYoutubeKey = youtubeKey,
                initialChatKey = chatKey,
                onDismiss = { showSettingsDialog = false },
                onSave = { ytKey, chKey ->
                    viewModel.saveKeys(ytKey, chKey)
                    showSettingsDialog = false
                },
            )
        }

        BackHandler(enabled = selectedItemIndex != null) {
            selectedItemIndex = null // Clear selection instead of handling system back
        }
    }
}

