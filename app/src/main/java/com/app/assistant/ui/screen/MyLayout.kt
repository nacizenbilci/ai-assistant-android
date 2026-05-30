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
import com.app.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

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
