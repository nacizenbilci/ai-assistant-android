package com.app.assistant.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.assistant.R
import com.app.assistant.model.Attachment
import com.app.assistant.ui.theme.AssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

@Composable
@Suppress("ktlint:standard:function-naming")
fun UserInputField(
    focusManager: androidx.compose.ui.focus.FocusManager,
    keyboardController: SoftwareKeyboardController,
    isSpeaking: Boolean,
    isListening: Boolean,
    question: String,
    onQuestionChange: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartListening: () -> Unit,
    onProcessQuestion: (androidx.compose.ui.focus.FocusManager, SoftwareKeyboardController, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    selectedAttachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (Attachment) -> Unit = {},
    onAttachClick: (String) -> Unit = {},
    isImageSupported: Boolean = false,
    isAudioSupported: Boolean = false,
    isVideoSupported: Boolean = false,
    isDocumentSupported: Boolean = false,
) {
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(8.dp)
    ) {
        if (selectedAttachments.isNotEmpty()) {
            AttachmentPreviewList(
                attachments = selectedAttachments,
                onRemove = onRemoveAttachment,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20)) // clip ripple inside
                .onGloballyPositioned { coords -> textFieldSize = coords.size },
        ) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20),
                value = question,
                placeholder = { Text(stringResource(id = R.string.type_here_placeholder)) },
                maxLines = 4,
                onValueChange = onQuestionChange,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                leadingIcon = {
                    val showAttachmentButton = isImageSupported || isAudioSupported || isVideoSupported || isDocumentSupported
                    if (showAttachmentButton && !isSpeaking) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_attachment),
                                    contentDescription = "Attach file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                if (isImageSupported) {
                                    DropdownMenuItem(
                                        text = { Text("Image") },
                                        onClick = {
                                            menuExpanded = false
                                            onAttachClick("image/*")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Take Photo (Camera)") },
                                        onClick = {
                                            menuExpanded = false
                                            onAttachClick("camera")
                                        }
                                    )
                                }
                                if (isAudioSupported) {
                                    DropdownMenuItem(
                                        text = { Text("Audio") },
                                        onClick = {
                                            menuExpanded = false
                                            onAttachClick("audio/*")
                                        }
                                    )
                                }
                                if (isVideoSupported) {
                                    DropdownMenuItem(
                                        text = { Text("Video") },
                                        onClick = {
                                            menuExpanded = false
                                            onAttachClick("video/*")
                                        }
                                    )
                                }
                                if (isDocumentSupported) {
                                    DropdownMenuItem(
                                        text = { Text("Document (PDF)") },
                                        onClick = {
                                            menuExpanded = false
                                            onAttachClick("application/pdf")
                                        }
                                    )
                                }
                                // Always support text files as they are decrypted and appended inline as text
                                DropdownMenuItem(
                                    text = { Text("Text Document (.txt/.csv/.json)") },
                                    onClick = {
                                        menuExpanded = false
                                        onAttachClick("text/*")
                                    }
                                )
                            }
                        }
                    }
                },
                trailingIcon = {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSpeaking) {
                            IconButton(onClick = {
                                if (isSpeaking) {
                                    onStopSpeaking()
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_stop),
                                    contentDescription = stringResource(id = R.string.stop_desc),
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                onStartListening()
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic),
                                    contentDescription = stringResource(id = R.string.mic_desc),
                                )
                            }
                        }
                        IconButton(onClick = {
                            if (question.isNotEmpty() || selectedAttachments.isNotEmpty()) {
                                onProcessQuestion(focusManager, keyboardController, false)
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
                modifier = Modifier
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
}

@Composable
fun AttachmentPreviewList(
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments) { attachment ->
            AttachmentPreviewCard(attachment = attachment, onRemove = { onRemove(attachment) })
        }
    }
}

@Composable
fun AttachmentPreviewCard(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (attachment.mimeType.startsWith("image/")) {
            var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(attachment) {
                withContext(Dispatchers.IO) {
                    try {
                        val fileBytes = java.io.File(attachment.filePath).readBytes()
                        val decrypted = com.app.assistant.db.EncryptionUtil.decryptFile(fileBytes, attachment.iv)
                        bitmap = android.graphics.BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val iconRes = when {
                    attachment.mimeType.startsWith("audio/") -> R.drawable.ic_mic
                    attachment.mimeType.startsWith("video/") -> R.drawable.ic_stop
                    else -> R.drawable.ic_translate
                }
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = attachment.fileName,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                .padding(2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

fun adjustContrast(
    color: Color,
    factor: Float = 0.15f,
): Color {
    val isDark = color.luminance() < 0.5
    return if (isDark) {
        Color(
            red = (color.red + factor).coerceAtMost(1f),
            green = (color.green + factor).coerceAtMost(1f),
            blue = (color.blue + factor).coerceAtMost(1f),
            alpha = color.alpha,
        )
    } else {
        Color(
            red = (color.red - factor).coerceAtLeast(0f),
            green = (color.green - factor).coerceAtLeast(0f),
            blue = (color.blue - factor).coerceAtLeast(0f),
            alpha = color.alpha,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UserInputFieldIdlePreview() {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    AssistantTheme {
        if (keyboardController != null) {
            UserInputField(
                focusManager = focusManager,
                keyboardController = keyboardController,
                isSpeaking = false,
                isListening = false,
                question = "",
                onQuestionChange = {},
                onStopSpeaking = {},
                onStartListening = {},
                onProcessQuestion = { _, _, _ -> },
                isImageSupported = true
            )
        }
    }
}
