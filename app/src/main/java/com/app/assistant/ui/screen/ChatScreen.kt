package com.app.assistant.ui.screen

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.app.assistant.R
import com.app.assistant.model.Conversation
import com.app.assistant.ui.theme.AssistantTheme
import com.app.assistant.util.Category
import com.app.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import kotlin.math.hypot
import org.commonmark.node.Text as CText

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

@Composable
@Suppress("ktlint:standard:function-naming")
fun MyLayout(
    modifier: Modifier = Modifier,
    onShowCopyIconChange: (Boolean) -> Unit,
    selectedItemIndex: Int?,
    onSelectedItemChange: (Int) -> Unit,
    viewModel: MainViewModel,
    isCustomUI: Boolean,
) {
    val boxes = viewModel.chatList
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberLazyListState()

    val context = LocalContext.current
    val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    LaunchedEffect(Unit) {
        viewModel.showToastEvent.collectLatest { message: String ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(selectedItemIndex) {
        onShowCopyIconChange(selectedItemIndex != null)
        if (selectedItemIndex != null) {
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(0.dp, 0.dp, 0.dp, 0.dp),
            verticalArrangement =
                if (isCustomUI) {
                    Arrangement.spacedBy(8.dp, Alignment.Bottom)
                } else {
                    Arrangement.spacedBy(8.dp)
                },
            state = scrollState,
        ) {
            itemsIndexed(boxes) { index, conversation ->
                ConversationItem(
                    conversation = conversation,
                    index = index,
                    isSelected = index == selectedItemIndex,
                    onLongClick = { newIndex -> onSelectedItemChange(newIndex) },
                    viewModel = viewModel,
                )
            }
        }

        if (keyboardController != null) {
            UserInputField(
                focusManager = focusManager,
                keyboardController = keyboardController,
                viewModel = viewModel,
            )
        }
    }

    LaunchedEffect(boxes.size) {
        if (boxes.isNotEmpty()) {
            scrollState.animateScrollToItem(boxes.size - 1)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun UserInputField(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController,
    viewModel: MainViewModel,
) {
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isListening by viewModel.isListening.collectAsState()

    val containerColor = TextFieldDefaults.colors().unfocusedContainerColor
    val rippleColor = adjustContrast(containerColor)

    // State for current radius + alpha
    val rippleRadius = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }

    // Size of TextField (for max ripple radius)
    var textFieldSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isListening, textFieldSize) {
        if (isListening && textFieldSize != IntSize.Zero) {
            val maxRadius =
                hypot(
                    textFieldSize.width / 2f,
                    textFieldSize.height / 2f,
                )
            while (currentCoroutineContext().isActive) { // keep running until LaunchedEffect cancels
                rippleRadius.snapTo(0f)
                rippleAlpha.snapTo(0.5f)

                // animate simultaneously
                launch {
                    rippleRadius.animateTo(
                        targetValue = maxRadius,
                        animationSpec = tween(1000, easing = LinearEasing),
                    )
                }
                rippleAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(1000, easing = LinearEasing),
                )
            }
        } else {
            rippleRadius.snapTo(0f)
            rippleAlpha.snapTo(0f)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(8.dp)
                .clip(RoundedCornerShape(20)) // clip ripple inside
                .onGloballyPositioned { coords -> textFieldSize = coords.size },
    ) {
        TextField(
            modifier =
                Modifier
                    .fillMaxWidth(),
            shape = RoundedCornerShape(20),
            value = viewModel.question.value,
            placeholder = { Text("Type here...") },
            maxLines = 2,
            onValueChange = { viewModel.question.value = it },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            trailingIcon = {
                Row(horizontalArrangement = Arrangement.End) {
                    val context = LocalContext.current
                    if (isSpeaking) {
                        IconButton(onClick = {
                            if (isSpeaking) {
                                viewModel.stopTextToSpeech()
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stop),
                                contentDescription = "Stop",
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            viewModel.startSpeechToText(
                                context,
                                onResult = { recognizedText ->
                                    viewModel.question.value = recognizedText
                                    if (viewModel.question.value.isNotEmpty()) {
                                        viewModel.processQuestion(
                                            focusManager,
                                            keyboardController,
                                            context,
                                            true,
                                        )
                                    }
                                },
                                onPartialResult = { recognizedText ->
                                    viewModel.question.value = recognizedText
                                },
                            )
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_mic),
                                contentDescription = "Mic",
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (viewModel.question.value.isNotEmpty()) {
                            viewModel.processQuestion(
                                focusManager,
                                keyboardController,
                                context,
                                false,
                            )
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                        )
                    }
                }
            },
        )

        Canvas(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20)),
        ) {
            if (rippleAlpha.value > 0f) {
                drawCircle(
                    color = rippleColor.copy(alpha = rippleAlpha.value),
                    radius = rippleRadius.value,
                    center = center,
                )
            }
        }
    }
}

fun adjustContrast(
    color: Color,
    factor: Float = 0.15f,
): Color {
    val isDark = color.luminance() < 0.5
    return if (isDark) {
        // lighten in dark mode
        Color(
            red = (color.red + factor).coerceAtMost(1f),
            green = (color.green + factor).coerceAtMost(1f),
            blue = (color.blue + factor).coerceAtMost(1f),
            alpha = color.alpha,
        )
    } else {
        // darken in light mode
        Color(
            red = (color.red - factor).coerceAtLeast(0f),
            green = (color.green - factor).coerceAtLeast(0f),
            blue = (color.blue - factor).coerceAtLeast(0f),
            alpha = color.alpha,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun ConversationItem(
    conversation: Conversation,
    index: Int,
    isSelected: Boolean,
    onLongClick: (Int) -> Unit,
    viewModel: MainViewModel,
    backgroundColor: Color =
        if (conversation.isMe) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    cornerRadius: Dp = 20.dp,
) {
    val uriHandler = LocalUriHandler.current
    val isTranslateEnabled = viewModel.getIsTranslationEnabled()
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            horizontalArrangement = if (conversation.isMe) Arrangement.Start else Arrangement.End,
        ) {
            if (conversation.isMe) {
                Spacer(modifier = Modifier.weight(1f))
            }
            Card(
                Modifier
                    .wrapContentWidth()
                    .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.85f),
                shape = RoundedCornerShape(cornerRadius),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                backgroundColor
                            },
                    ),
            ) {
                if (conversation.isLoading) {
                    LoadingDots() // Show loading animation when isLoading is true
                } else {
                    if (conversation.isMe) {
                        OtherCard(conversation, onLongClick, index, isTranslateEnabled)
                    } else {
                        when (conversation.category) {
                            Category.CALL.name -> {
                                MakeCall(uriHandler, conversation, isTranslateEnabled)
                            }

                            Category.OTHER.name -> {
                                OtherCard(conversation, onLongClick, index, isTranslateEnabled)
                            }

                            Category.SETTINGS.name -> {
                                // TODO
                                OtherCard(conversation, onLongClick, index, isTranslateEnabled)
                            }

                            Category.SONGS.name -> {
                                PlaySong(uriHandler, conversation, isTranslateEnabled)
                            }

                            Category.NAVIGATION.name -> {
                                StartNavigation(uriHandler, conversation, isTranslateEnabled)
                            }

                            Category.WEATHER.name -> {
                                ShowWeather(uriHandler, conversation, isTranslateEnabled)
                            }

                            Category.REMINDER.name -> {
                                OtherCard(conversation, onLongClick, index, isTranslateEnabled)
                            }

                            Category.ALARM.name -> {
                                OtherCard(conversation, onLongClick, index, isTranslateEnabled)
                            }

                            else -> {
                                OtherCard(conversation, onLongClick, index, isTranslateEnabled)
                            }
                        }
                    }
                }
            }
            if (!conversation.isMe) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        if (conversation.isMe) {
            Text(
                text = conversation.category,
                modifier =
                    Modifier
                        .padding(0.dp, 0.dp, 20.dp, 0.dp)
                        .fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun StartNavigation(
    uriHandler: UriHandler,
    conversation: Conversation,
    isTranslateEnabled: Boolean,
) {
    Row(
        modifier =
            Modifier
                .padding(8.dp)
                .clickable {
                    uriHandler.openUri(conversation.navigationURI.toString())
                },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ),
        ) {
            Icon(
                modifier = Modifier.padding(8.dp),
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Navigation",
            )
        }

        MarkdownText(
            modifier =
                Modifier
                    .align(alignment = Alignment.CenterVertically)
                    .clickable {
                        uriHandler.openUri(conversation.navigationURI.toString())
                    },
            markdown = if (isTranslateEnabled) conversation.translatedText else conversation.englishText,
        )
    }
}

@Composable
private fun PlaySong(
    uriHandler: UriHandler,
    conversation: Conversation,
    isTranslateEnabled: Boolean,
) {
    AsyncImage(
        modifier =
            Modifier
                .clickable {
                    uriHandler.openUri(conversation.navigationURI.toString())
                },
        model = conversation.contentURL,
        contentDescription = if (isTranslateEnabled) conversation.translatedText else conversation.englishText,
    )
}

@Composable
private fun MakeCall(
    uriHandler: UriHandler,
    conversation: Conversation,
    isTranslateEnabled: Boolean,
) {
    Row(
        modifier =
            Modifier
                .padding(8.dp)
                .clickable {
                    uriHandler.openUri(conversation.navigationURI.toString())
                },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ),
        ) {
            Icon(
                modifier = Modifier.padding(8.dp),
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
            )
        }

        MarkdownText(
            modifier =
                Modifier
                    .align(alignment = Alignment.CenterVertically)
                    .clickable {
                        uriHandler.openUri(conversation.navigationURI.toString())
                    },
            markdown = if (isTranslateEnabled) conversation.translatedText else conversation.englishText,
        )
    }
}

@Composable
private fun ShowWeather(
    uriHandler: UriHandler,
    conversation: Conversation,
    isTranslateEnabled: Boolean,
) {
    MarkdownText(
        modifier =
            Modifier
                .padding(16.dp)
                .clickable { uriHandler.openUri(conversation.navigationURI.toString()) },
        markdown = if (isTranslateEnabled) conversation.translatedText else conversation.englishText,
    )
}

@Composable
private fun OtherCard(
    conversation: Conversation,
    onLongClick: (Int) -> Unit,
    index: Int,
    isTranslateEnabled: Boolean,
) {
    MarkdownText(
        markdown =
            if (isTranslateEnabled && conversation.translatedText.isNotBlank()) {
                conversation.translatedText
            } else {
                conversation.englishText
            },
        modifier =
            Modifier
                .padding(16.dp, 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            onLongClick(index)
                        },
                    )
                },
    )
}

@Composable
fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition()

    // Animating each dot separately
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(0), // First dot starts immediately
            ),
        label = "",
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(200), // Second dot starts with a delay
            ),
        label = "",
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(400), // Third dot starts with a larger delay
            ),
        label = "",
    )

    // Unicode character for big dot (⏺ or ● for larger circles)
    val dotChar = "\u25CF"

    Row(modifier = Modifier.padding(16.dp)) {
        // First dot
        Text(
            text = dotChar,
            modifier = Modifier.offset(y = dot1Offset.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.width(4.dp))

        // Second dot
        Text(
            text = dotChar,
            modifier = Modifier.offset(y = dot2Offset.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.width(4.dp))

        // Third dot
        Text(
            text = dotChar,
            modifier = Modifier.offset(y = dot3Offset.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun MarkdownText(
    modifier: Modifier = Modifier,
    markdown: String,
) {
    val annotatedText =
        remember(markdown) {
            parseMarkdownToAnnotatedString(markdown)
        }
    Text(
        text = annotatedText,
        modifier = modifier,
    )
}

fun parseMarkdownToAnnotatedString(markdown: String): AnnotatedString {
    val parser = Parser.builder().build()
    val document = parser.parse(markdown)
    val builder = AnnotatedString.Builder()
    processNodes(document, builder)
    return builder.toAnnotatedString()
}

/**
 * Recursively processes nodes. We only append a newline if there's a *next sibling*,
 * preventing extra blank lines at the very end.
 */
private fun processNodes(
    node: Node,
    builder: AnnotatedString.Builder,
) {
    var child = node.firstChild
    while (child != null) {
        val nextSibling = child.next
        when (child) {
            is CText -> {
                builder.append(child.literal)
            }

            is Emphasis -> {
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }

            is StrongEmphasis -> {
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }

            is Code -> {
                // Inline code (single backticks)
                val start = builder.length
                builder.append(child.literal)
                val end = builder.length
                // Monospace but keep background transparent for inline code
                builder.addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace),
                    start,
                    end,
                )
            }

            is Link -> {
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                // Annotate the text with the URL (useful if you want to make it clickable later)
                builder.addStringAnnotation(
                    tag = "URL",
                    annotation = child.destination,
                    start = start,
                    end = end,
                )
                // Style the link text
                builder.addStyle(
                    SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline),
                    start,
                    end,
                )
            }

            is Paragraph -> {
                // Process paragraph content
                processNodes(child, builder)
                // Only append a newline if there's another sibling after this paragraph
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is Heading -> {
                // Insert a newline before headings (for spacing), unless it's the first node
                if (builder.isNotEmpty() && !builder.toString().endsWith("\n")) {
                    builder.append("\n")
                }
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                // Map heading level to style
                val headingStyle =
                    when (child.level) {
                        1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        4 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        5 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        else -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                builder.addStyle(headingStyle, start, end)
                // Only append a newline if there's another sibling
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is BlockQuote -> {
                // Optional: style block quotes in italics or different color
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                builder.addStyle(
                    SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic),
                    start,
                    end,
                )
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is BulletList -> {
                var listItem = child.firstChild
                while (listItem != null) {
                    if (listItem is ListItem) {
                        // Prefix with a bullet
                        builder.append("• ")
                        processNodes(listItem, builder)
                        builder.append("\n")
                    }
                    listItem = listItem.next
                }
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is OrderedList -> {
                var index = child.startNumber
                var listItem = child.firstChild
                while (listItem != null) {
                    if (listItem is ListItem) {
                        builder.append("$index. ")
                        processNodes(listItem, builder)
                        builder.append("\n")
                        index++
                    }
                    listItem = listItem.next
                }
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is FencedCodeBlock -> {
                // Code block with triple backticks
                val start = builder.length
                // Insert the code exactly as-is
                builder.append(child.literal)
                val end = builder.length
                // Always dark background, white text
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                    ),
                    start,
                    end,
                )
                // Only append a newline if there's another sibling
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is IndentedCodeBlock -> {
                val start = builder.length
                builder.append(child.literal)
                val end = builder.length
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                    ),
                    start,
                    end,
                )
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is ThematicBreak -> {
                builder.append("\n----------------\n")
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is SoftLineBreak -> {
                // Usually rendered as a space in Markdown
                builder.append(" ")
            }

            is HardLineBreak -> {
                builder.append("\n")
            }

            else -> {
                processNodes(child, builder)
            }
        }
        child = nextSibling
    }
}

/** Quick helper to check if the builder already has text. */
private fun AnnotatedString.Builder.isNotEmpty(): Boolean = this.length > 0
