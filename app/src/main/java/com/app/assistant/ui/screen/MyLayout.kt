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
    onStartListening: (onResult: (String) -> Unit, onPartialResult: (String) -> Unit) -> Unit,
    onProcessQuestion: (FocusManager, SoftwareKeyboardController, Boolean) -> Unit,
    isTranslateEnabled: Boolean,
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
            )
        }
    }

    LaunchedEffect(chatList.size) {
        if (chatList.isNotEmpty()) {
            scrollState.animateScrollToItem(chatList.size - 1)
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
                    englishText = "What is the weather today?",
                    translatedText = "Quelle est la météo aujourd'hui?",
                    isMe = true
                ),
                Conversation(
                    englishText = "The weather is sunny at 22°C.",
                    translatedText = "Il fait beau à 22°C.",
                    isMe = false
                )
            ),
            isSpeaking = false,
            isListening = false,
            question = "",
            onQuestionChange = {},
            onStopSpeaking = {},
            onStartListening = { _, _ -> },
            onProcessQuestion = { _, _, _ -> },
            isTranslateEnabled = false
        )
    }
}
