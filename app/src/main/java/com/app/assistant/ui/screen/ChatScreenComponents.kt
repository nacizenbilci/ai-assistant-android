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
import androidx.compose.ui.tooling.preview.Preview
import com.app.assistant.ui.theme.AssistantTheme

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

// LanguageSelectionBottomSheet removed (moved to Settings)

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

@Preview(showBackground = true)
@Composable
fun ChatDrawerContentPreview() {
    AssistantTheme {
        ChatDrawerContent(
            groupList = listOf(
                Group(1L, "Recent Conversation 1"),
                Group(2L, "Recent Conversation 2")
            ),
            onGroupClick = {},
            onSettingsClick = {},
            onNewChatClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatTopAppBarPreview() {
    AssistantTheme {
        ChatTopAppBar(
            showCopyIcon = true,
            onCopyClick = {},
            onDeleteClick = {},
            chatListNotEmpty = true,
            onClearChatClick = {},
            onMenuClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CustomUiDragHandlePreview() {
    AssistantTheme {
        CustomUiDragHandle(onDragEnd = {})
    }
}

@Preview(showBackground = true)
@Composable
fun ClearChatDialogPreview() {
    AssistantTheme {
        ClearChatDialog(onConfirm = {}, onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
fun DeleteMessageDialogPreview() {
    AssistantTheme {
        DeleteMessageDialog(onConfirm = {}, onDismiss = {})
    }
}
