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
        var showBottomSheet by viewModel.showBottomSheet

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
                if (isCustomUI) {
                    null
                } else {
                    ModalDrawerSheet {
                        Text("Chats", modifier = Modifier.padding(16.dp))
                        Column(
                            modifier =
                                Modifier
                                    .padding(horizontal = 8.dp)
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            groupList.forEach { group ->
                                NavigationDrawerItem(
                                    label = { Text(text = group.title) },
                                    selected = false,
                                    onClick = {
                                        viewModel.loadMessagesFromGroup(group.groupId)
                                        scope.launch { drawerState.close() }
                                    },
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(
                            label = { Text(text = "Settings") },
                            icon = {
                                Icon(
                                    painter = (painterResource(id = R.drawable.ic_settings)),
                                    contentDescription = "Settings",
                                )
                            },
                            selected = false,
                            onClick = {
                                showSettingsDialog = true
                                scope.launch { drawerState.close() }
                            },
                        )
                        NavigationDrawerItem(
                            label = { Text(text = "Start new chat") },
                            icon = {
                                Icon(
                                    painter = (painterResource(id = R.drawable.ic_add_new_chat)),
                                    contentDescription = "Start new chat",
                                )
                            },
                            selected = false,
                            onClick = {
                                viewModel.newChat()
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
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
                            TopAppBar(
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            drawerState.apply {
                                                if (isClosed) open() else close()
                                            }
                                        }
                                    }) {
                                        Icon(
                                            painter = (painterResource(id = R.drawable.ic_menu)),
                                            contentDescription = "Menu",
                                        )
                                    }
                                },
                                title = { Text("") },
                                actions = {
                                    if (showCopyIcon) {
                                        IconButton(onClick = {
                                            selectedItemIndex?.let { index ->
                                                val textToCopy =
                                                    when {
                                                        viewModel.getIsTranslationEnabled() -> {
                                                            viewModel.chatList
                                                                .getOrNull(
                                                                    index,
                                                                )?.translatedText
                                                        }

                                                        else -> {
                                                            viewModel.chatList.getOrNull(index)?.englishText
                                                        }
                                                    } ?: ""
                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                                selectedItemIndex = null
                                            }
                                        }) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_content_copy),
                                                contentDescription = "Copy",
                                            )
                                        }
                                        IconButton(onClick = {
                                            deleteShowDialog = true
                                        }) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_delete),
                                                contentDescription = "Delete",
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        showBottomSheet = true
                                    }) {
                                        Icon(
                                            painter = (painterResource(id = R.drawable.ic_translate)),
                                            contentDescription = "Translate",
                                        )
                                    }
                                    if (viewModel.chatList.isNotEmpty()) {
                                        IconButton(onClick = { clearShowDialog = true }) {
                                            Icon(
                                                painter = (painterResource(id = R.drawable.ic_restart)),
                                                contentDescription = "Restart Chat",
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        if (!isCustomUIHalfPage && isCustomUI) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                        .then(
                                            Modifier
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDragStart = { /* Optional: trigger on drag start */ },
                                                        onDragEnd = {
                                                            viewModel.expandToFullScreen()
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            // track dragAmount if needed
                                                        },
                                                    )
                                                },
                                        ).clickable { viewModel.expandToFullScreen() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(36.dp)
                                            .height(4.dp)
                                            .background(
                                                color = Color.Gray,
                                                shape = RoundedCornerShape(2.dp),
                                            ),
                                )
                            }
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
                val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

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
            var searchQuery by remember { mutableStateOf("") }
            val filteredItems = viewModel.languages.filter { it.first.contains(searchQuery, true) }.map { it }
            val isLanguageLoading by viewModel.isLanguageLoading.collectAsState()
            val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsState()

            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                },
                sheetState = sheetState,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 0.dp, 16.dp, 16.dp),
                    ) {
                        Text(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .align(Alignment.CenterVertically),
                            text = "Chat in different languages",
                        )
                        Switch(
                            checked = isTranslationEnabled,
                            onCheckedChange = { viewModel.updateTranslationEnabled(it) },
                            modifier =
                                Modifier.align(Alignment.CenterVertically),
                        )
                    }

                    TextField(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(0.dp)
                                .imePadding(),
                        value = searchQuery,
                        placeholder = { Text("Search...") },
                        singleLine = true,
                        onValueChange = { searchQuery = it },
                        colors =
                            TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        enabled = isTranslationEnabled,
                    )

                    if (isLanguageLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(0.dp, 16.dp, 0.dp, 0.dp),
                    ) {
                        items(items = filteredItems) { lang ->
                            Text(
                                text = lang.first,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = isTranslationEnabled) {
                                            if (isTranslationEnabled) {
                                                viewModel.onItemSelected(lang.second)
                                            }
                                        }.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }

        if (clearShowDialog) {
            AlertDialog(
                onDismissRequest = { clearShowDialog = false },
                text = { Text("Are you sure you want to clear this chat from history?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearBoxes()
                        clearShowDialog = false
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clearShowDialog = false }) {
                        Text("No")
                    }
                },
            )
        }

        if (deleteShowDialog) {
            AlertDialog(
                onDismissRequest = {
                    deleteShowDialog = false
                    selectedItemIndex = null
                },
                text = { Text("Delete this message?") },
                confirmButton = {
                    TextButton(onClick = {
                        selectedItemIndex?.let { index ->
                            viewModel.deleteMessage(index)
                        }
                        deleteShowDialog = false
                        selectedItemIndex = null
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        deleteShowDialog = false
                        selectedItemIndex = null
                    }) {
                        Text("No")
                    }
                },
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
