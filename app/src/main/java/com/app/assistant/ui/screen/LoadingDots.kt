package com.app.assistant.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.tooling.preview.Preview
import com.app.assistant.ui.theme.AssistantTheme

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

@Preview(showBackground = true)
@Composable
fun LoadingDotsPreview() {
    AssistantTheme {
        LoadingDots()
    }
}
