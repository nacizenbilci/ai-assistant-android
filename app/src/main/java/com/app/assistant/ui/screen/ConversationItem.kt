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
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.app.assistant.R
import com.app.assistant.model.Conversation
import com.app.assistant.util.Category
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.app.assistant.ui.theme.AssistantTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight

@Composable
@Suppress("ktlint:standard:function-naming")
fun ConversationItem(
    conversation: Conversation,
    index: Int,
    isSelected: Boolean,
    onLongClick: (Int) -> Unit,
    isTranslateEnabled: Boolean,
    backgroundColor: Color =
        if (conversation.isMe) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    cornerRadius: Dp = 20.dp,
) {
    val uriHandler = LocalUriHandler.current
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
                        OtherCard(conversation, onLongClick, index)
                    } else {
                        when (conversation.category) {
                            Category.CALL.name -> {
                                MakeCall(uriHandler, conversation)
                            }

                            Category.OTHER.name -> {
                                OtherCard(conversation, onLongClick, index)
                            }

                            Category.SETTINGS.name -> {
                                // TODO
                                OtherCard(conversation, onLongClick, index)
                            }

                            Category.SONGS.name -> {
                                PlaySong(uriHandler, conversation)
                            }

                            Category.NAVIGATION.name -> {
                                StartNavigation(uriHandler, conversation)
                            }

                            Category.WEATHER.name -> {
                                ShowWeather(uriHandler, conversation)
                            }

                            Category.REMINDER.name -> {
                                OtherCard(conversation, onLongClick, index)
                            }

                            Category.ALARM.name -> {
                                OtherCard(conversation, onLongClick, index)
                            }

                            else -> {
                                OtherCard(conversation, onLongClick, index)
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
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StartNavigation(
    uriHandler: UriHandler,
    conversation: Conversation,
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
                contentDescription = stringResource(id = R.string.navigation_desc),
            )
        }

        MarkdownText(
            modifier =
                Modifier
                    .align(alignment = Alignment.CenterVertically),
            markdown = conversation.text,
        )
    }
}

@Composable
private fun PlaySong(
    uriHandler: UriHandler,
    conversation: Conversation,
) {
    AsyncImage(
        modifier =
            Modifier
                .clickable {
                    uriHandler.openUri(conversation.navigationURI.toString())
                },
        model = conversation.contentURL,
        contentDescription = conversation.text,
    )
}

@Composable
private fun MakeCall(
    uriHandler: UriHandler,
    conversation: Conversation,
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
                contentDescription = stringResource(id = R.string.call_desc),
            )
        }

        MarkdownText(
            modifier =
                Modifier
                    .align(alignment = Alignment.CenterVertically),
            markdown = conversation.text,
        )
    }
}

@Composable
private fun ShowWeather(
    uriHandler: UriHandler,
    conversation: Conversation,
) {
    MarkdownText(
        modifier =
            Modifier
                .padding(16.dp)
                .clickable { uriHandler.openUri(conversation.navigationURI.toString()) },
        markdown = conversation.text,
    )
}

@Composable
private fun OtherCard(
    conversation: Conversation,
    onLongClick: (Int) -> Unit,
    index: Int,
) {
    val thinkingText = conversation.getThinkingProcess()
    val answerText = conversation.getActualAnswer()

    Column(
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        onLongClick(index)
                    },
                )
            }
    ) {
        if (thinkingText != null) {
            var isExpanded by remember { mutableStateOf(true) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧠",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Thinking Process",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = thinkingText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        if (answerText.isNotEmpty() || conversation.isStreaming) {
            val displayAnswer = if (conversation.isStreaming) {
                "$answerText ▮"
            } else {
                answerText
            }

            MarkdownText(
                markdown = displayAnswer,
                modifier = Modifier
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UserConversationItemPreview() {
    AssistantTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ConversationItem(
                conversation = Conversation(
                    text = "Hello! Can you help me?",
                    isMe = true,
                    category = Category.OTHER.name
                ),
                index = 0,
                isSelected = false,
                onLongClick = {},
                isTranslateEnabled = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AiConversationItemPreview() {
    AssistantTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ConversationItem(
                conversation = Conversation(
                    text = "Sure! I can help you set an alarm or make a call.",
                    isMe = false,
                    category = Category.OTHER.name
                ),
                index = 1,
                isSelected = false,
                onLongClick = {},
                isTranslateEnabled = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingConversationItemPreview() {
    AssistantTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ConversationItem(
                conversation = Conversation(
                    text = "",
                    isMe = false,
                    isLoading = true
                ),
                index = 2,
                isSelected = false,
                onLongClick = {},
                isTranslateEnabled = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherConversationItemPreview() {
    AssistantTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ConversationItem(
                conversation = Conversation(
                    text = "Today's weather is sunny, 25°C.",
                    isMe = false,
                    category = Category.WEATHER.name
                ),
                index = 3,
                isSelected = false,
                onLongClick = {},
                isTranslateEnabled = false
            )
        }
    }
}
