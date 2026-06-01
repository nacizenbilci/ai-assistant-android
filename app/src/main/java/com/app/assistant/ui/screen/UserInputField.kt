package com.app.assistant.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.app.assistant.R
import com.app.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

@Composable
@Suppress("ktlint:standard:function-naming")
fun UserInputField(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController,
    viewModel: MainViewModel,
) {
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val question by viewModel.question.collectAsState()

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
            value = question,
            placeholder = { Text(stringResource(id = R.string.type_here_placeholder)) },
            maxLines = 2,
            onValueChange = { viewModel.setQuestion(it) },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            trailingIcon = {
                Row(horizontalArrangement = Arrangement.End) {
                    if (isSpeaking) {
                        IconButton(onClick = {
                            if (isSpeaking) {
                                viewModel.stopTextToSpeech()
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stop),
                                contentDescription = stringResource(id = R.string.stop_desc),
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            viewModel.startSpeechToText(
                                onResult = { recognizedText ->
                                    viewModel.setQuestion(recognizedText)
                                    if (question.isNotEmpty()) {
                                        viewModel.processQuestion(
                                            focusManager,
                                            keyboardController,
                                            true,
                                        )
                                    }
                                },
                                onPartialResult = { recognizedText ->
                                    viewModel.setQuestion(recognizedText)
                                },
                            )
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_mic),
                                contentDescription = stringResource(id = R.string.mic_desc),
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (question.isNotEmpty()) {
                            viewModel.processQuestion(
                                focusManager,
                                keyboardController,
                                false,
                            )
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(id = R.string.send_desc),
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
