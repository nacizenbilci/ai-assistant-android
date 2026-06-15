package com.app.assistant.ui.screen

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.app.assistant.model.Conversation
import androidx.compose.ui.tooling.preview.Preview
import com.app.assistant.ui.theme.AssistantTheme
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween

@Composable
@Suppress("ktlint:standard:function-naming")
fun MyLayout(
    modifier: Modifier = Modifier,
    onShowCopyIconChange: (Boolean) -> Unit,
    selectedItemIndex: Int?,
    onSelectedItemChange: (Int) -> Unit,
    isCustomUI: Boolean,
    chatList: List<Conversation>,
    isSpeaking: Boolean,
    isListening: Boolean,
    question: String,
    onQuestionChange: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartListening: () -> Unit,
    onProcessQuestion: (FocusManager, SoftwareKeyboardController, Boolean) -> Unit,
    isTranslateEnabled: Boolean,
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
    isMicReady: Boolean = false,
) {
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
            itemsIndexed(chatList) { index, conversation ->
                ConversationItem(
                    conversation = conversation,
                    index = index,
                    isSelected = index == selectedItemIndex,
                    onLongClick = { newIndex -> onSelectedItemChange(newIndex) },
                    isTranslateEnabled = isTranslateEnabled,
                )
            }
        }

        var isDismissed by remember(chatList.size, isImageSupported, isAudioSupported, isVideoSupported, isDocumentSupported) {
            mutableStateOf(false)
        }

        val showWarning = remember(chatList.size, isImageSupported, isAudioSupported, isVideoSupported, isDocumentSupported) {
            chatList.any { conversation ->
                conversation.attachments.any { att ->
                    val isTextDoc = att.mimeType.startsWith("text/") || att.mimeType == "application/json"
                    if (isTextDoc) {
                        false
                    } else {
                        when {
                            att.mimeType.startsWith("image/") -> !isImageSupported
                            att.mimeType.startsWith("audio/") -> !isAudioSupported
                            att.mimeType.startsWith("video/") -> !isVideoSupported
                            att.mimeType.startsWith("application/pdf") -> !isDocumentSupported
                            else -> true
                        }
                    }
                }
            }
        }

        if (showWarning && !isDismissed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Current model does not support some media in this chat. Incompatible past attachments will be ignored by the LLM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { isDismissed = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss warning",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        AnimatedContent(
            targetState = isHandsFree,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(300)) { height -> height } + fadeIn(animationSpec = tween(300)))
                    .togetherWith(
                        slideOutVertically(animationSpec = tween(300)) { height -> height } + fadeOut(animationSpec = tween(300))
                    )
            },
            label = "InputFieldTransition",
            modifier = Modifier.fillMaxWidth()
        ) { handsFreeActive ->
            if (handsFreeActive) {
                HandsFreeBar(
                    isSpeaking = isSpeaking,
                    isListening = isListening,
                    isThinking = isVoiceProcessing || chatList.any { it.isLoading || it.isStreaming },
                    isMicReady = isMicReady,
                    onExitHandsFree = onExitHandsFree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            } else {
                if (keyboardController != null) {
                    UserInputField(
                        focusManager = focusManager,
                        keyboardController = keyboardController,
                        isSpeaking = isSpeaking,
                        isListening = isListening,
                        question = question,
                        onQuestionChange = onQuestionChange,
                        onStopSpeaking = onStopSpeaking,
                        onStartListening = onStartListening,
                        onProcessQuestion = onProcessQuestion,
                        selectedAttachments = selectedAttachments,
                        onRemoveAttachment = onRemoveAttachment,
                        onAttachClick = onAttachClick,
                        isImageSupported = isImageSupported,
                        isAudioSupported = isAudioSupported,
                        isVideoSupported = isVideoSupported,
                        isDocumentSupported = isDocumentSupported,
                        isHandsFree = isHandsFree,
                        onToggleHandsFree = onToggleHandsFree,
                    )
                }
            }
        }
    }

    val lastItem = chatList.lastOrNull()
    LaunchedEffect(chatList.size, lastItem?.text, lastItem?.isStreaming, lastItem?.isLoading) {
        if (chatList.isNotEmpty()) {
            val lastIndex = chatList.size - 1
            
            var lastVisibleItem = scrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }
            if (lastVisibleItem == null) {
                if (lastItem?.isStreaming == true) {
                    scrollState.scrollToItem(lastIndex, 0)
                } else {
                    scrollState.animateScrollToItem(lastIndex, 0)
                }
                delay(50)
                lastVisibleItem = scrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }
            }
            
            val itemHeight = lastVisibleItem?.size ?: 0
            val viewportHeight = scrollState.layoutInfo.viewportEndOffset - scrollState.layoutInfo.viewportStartOffset
            if (viewportHeight > 0 && itemHeight > 0) {
                val scrollOffset = viewportHeight - itemHeight
                if (lastItem?.isStreaming == true) {
                    scrollState.scrollToItem(lastIndex, scrollOffset)
                } else {
                    scrollState.animateScrollToItem(lastIndex, scrollOffset)
                }
            } else {
                if (lastItem?.isStreaming == true) {
                    scrollState.scrollToItem(lastIndex, 0)
                } else {
                    scrollState.animateScrollToItem(lastIndex, 0)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MyLayoutPreview() {
    AssistantTheme {
        MyLayout(
            onShowCopyIconChange = {},
            selectedItemIndex = null,
            onSelectedItemChange = {},
            isCustomUI = false,
            chatList = listOf(
                Conversation(
                    text = "What is the weather today?",
                    isMe = true
                ),
                Conversation(
                    text = "The weather is sunny at 22°C.",
                    isMe = false
                )
            ),
            isSpeaking = false,
            isListening = false,
            question = "",
            onQuestionChange = {},
            onStopSpeaking = {},
            onStartListening = {},
            onProcessQuestion = { _, _, _ -> },
            isTranslateEnabled = false
        )
    }
}
