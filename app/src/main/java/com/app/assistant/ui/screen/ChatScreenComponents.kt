package com.app.assistant.ui.screen

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.assistant.R
import com.app.assistant.model.Group

@Composable
fun ChatDrawerContent(
    groupList: List<Group>,
    onGroupClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit,
) {
    ModalDrawerSheet {
        Text(stringResource(id = R.string.chats), modifier = Modifier.padding(16.dp))
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
                    onClick = { onGroupClick(group.groupId) },
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text(text = stringResource(id = R.string.settings_title)) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = stringResource(id = R.string.settings_title),
                )
            },
            selected = false,
            onClick = onSettingsClick,
        )
        NavigationDrawerItem(
            label = { Text(text = stringResource(id = R.string.start_new_chat)) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_new_chat),
                    contentDescription = stringResource(id = R.string.start_new_chat),
                )
            },
            selected = false,
            onClick = onNewChatClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    showCopyIcon: Boolean,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTranslateClick: () -> Unit,
    chatListNotEmpty: Boolean,
    onClearChatClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu),
                    contentDescription = stringResource(id = R.string.menu),
                )
            }
        },
        title = { Text("") },
        actions = {
            if (showCopyIcon) {
                IconButton(onClick = onCopyClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_content_copy),
                        contentDescription = stringResource(id = R.string.copy),
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = stringResource(id = R.string.delete),
                    )
                }
            }
            IconButton(onClick = onTranslateClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_translate),
                    contentDescription = stringResource(id = R.string.translate),
                )
            }
            if (chatListNotEmpty) {
                IconButton(onClick = onClearChatClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_restart),
                        contentDescription = stringResource(id = R.string.restart_chat),
                    )
                }
            }
        },
    )
}

@Composable
fun CustomUiDragHandle(
    onDragEnd: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .then(
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { /* Optional: trigger on drag start */ },
                            onDragEnd = onDragEnd,
                            onDrag = { _, _ ->
                                // track dragAmount if needed
                            },
                        )
                    },
                )
                .clickable { onDragEnd() },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionBottomSheet(
    sheetState: SheetState,
    languages: List<Pair<String, String>>,
    isTranslationEnabled: Boolean,
    isLanguageLoading: Boolean,
    onTranslationEnabledChange: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(searchQuery, languages) {
        languages.filter { it.first.contains(searchQuery, true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
                    text = stringResource(id = R.string.chat_different_languages),
                )
                Switch(
                    checked = isTranslationEnabled,
                    onCheckedChange = onTranslationEnabledChange,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            TextField(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(0.dp)
                        .imePadding(),
                value = searchQuery,
                placeholder = { Text(stringResource(id = R.string.search_placeholder)) },
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
                                    onLanguageSelected(lang.second)
                                }
                                .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ClearChatDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(id = R.string.clear_chat_history_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.no))
            }
        },
    )
}

@Composable
fun DeleteMessageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(id = R.string.delete_message_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.no))
            }
        },
    )
}
