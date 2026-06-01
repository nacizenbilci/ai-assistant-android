package com.app.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.app.assistant.model.Conversation
import com.app.assistant.util.Category
import com.app.assistant.viewmodel.MainViewModel

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
                    .align(alignment = Alignment.CenterVertically),
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
                    .align(alignment = Alignment.CenterVertically),
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
