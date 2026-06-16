package com.app.assistant.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.app.assistant.R
import androidx.compose.ui.tooling.preview.Preview
import com.app.assistant.ui.theme.AssistantTheme

enum class HandsFreeState {
    STARTING,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun HandsFreeBar(
    isSpeaking: Boolean,
    isListening: Boolean,
    isThinking: Boolean,
    isMicReady: Boolean,
    onExitHandsFree: () -> Unit,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentState = remember(isSpeaking, isListening, isThinking, isMicReady) {
        when {
            isThinking -> HandsFreeState.THINKING
            isSpeaking -> HandsFreeState.SPEAKING
            isListening -> HandsFreeState.LISTENING
            isMicReady -> HandsFreeState.LISTENING
            else -> HandsFreeState.STARTING
        }
    }

    val orbBackgroundColor by animateColorAsState(
        targetValue = when (currentState) {
            HandsFreeState.STARTING -> MaterialTheme.colorScheme.surfaceVariant
            HandsFreeState.LISTENING -> MaterialTheme.colorScheme.primaryContainer
            HandsFreeState.THINKING -> MaterialTheme.colorScheme.tertiaryContainer
            HandsFreeState.SPEAKING -> MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = tween(durationMillis = 350),
        label = "orbBackground"
    )

    val orbContentColor by animateColorAsState(
        targetValue = when (currentState) {
            HandsFreeState.STARTING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            HandsFreeState.LISTENING -> MaterialTheme.colorScheme.primary
            HandsFreeState.THINKING -> MaterialTheme.colorScheme.tertiary
            HandsFreeState.SPEAKING -> MaterialTheme.colorScheme.secondary
        },
        animationSpec = tween(durationMillis = 350),
        label = "orbContent"
    )

    val orbWidth by animateDpAsState(
        targetValue = when (currentState) {
            HandsFreeState.STARTING -> 48.dp
            HandsFreeState.LISTENING -> 72.dp
            HandsFreeState.THINKING -> 72.dp
            HandsFreeState.SPEAKING -> 88.dp
        },
        animationSpec = tween(durationMillis = 350),
        label = "orbWidth"
    )

    val orbHeight = 48.dp

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    
    // Pulse animation for thinking outer glow ring
    val thinkingPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingPulseScale"
    )
    val thinkingPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingPulseAlpha"
    )

    // Wave phase animation for Listening state sine wave
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left actions: Camera and Share Screen
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { /* Add camera feature later */ }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = "Camera (Hands-free)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { /* Add screen share feature later */ }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_screen_share),
                    contentDescription = "Share Screen (Hands-free)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Center State Orb
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (currentState == HandsFreeState.THINKING) {
                // Outer pulsing ring (drawn behind using graphicsLayer scale so it does not affect parent height)
                Box(
                    modifier = Modifier
                        .size(orbWidth, orbHeight)
                        .graphicsLayer {
                            scaleX = thinkingPulseScale
                            scaleY = thinkingPulseScale
                            alpha = thinkingPulseAlpha
                        }
                        .background(
                            color = orbBackgroundColor,
                            shape = RoundedCornerShape(24.dp)
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(orbWidth, orbHeight)
                    .background(color = orbBackgroundColor, shape = RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Creative animations depending on state
                when (currentState) {
                    HandsFreeState.STARTING -> {
                        // Circular rotating arc segment with a pulsing inner core dot
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "startingRotation"
                        )
                        
                        val coreScale by infiniteTransition.animateFloat(
                            initialValue = 0.7f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "startingCore"
                        )

                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outer spinning arc segment
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = rotation }
                            ) {
                                drawArc(
                                    color = orbContentColor,
                                    startAngle = 0f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            // Inner breathing core
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .graphicsLayer {
                                        scaleX = coreScale
                                        scaleY = coreScale
                                    }
                                    .background(orbContentColor, CircleShape)
                            )
                        }
                    }
                    HandsFreeState.LISTENING -> {
                        // Out of the box: Flowing double sine wave
                        Canvas(modifier = Modifier.size(width = 52.dp, height = 24.dp)) {
                            val width = size.width
                            val height = size.height
                            val centerY = height / 2
                            val amplitude = height / 2 - 2.dp.toPx()

                            // Draw primary wave
                            val wavePath = Path()
                            wavePath.moveTo(0f, centerY)
                            for (x in 0..width.toInt()) {
                                val frequency = (2 * Math.PI.toFloat() / width) * 1.5f
                                val y = kotlin.math.sin(x * frequency + wavePhase) * amplitude + centerY
                                wavePath.lineTo(x.toFloat(), y)
                            }
                            drawPath(
                                path = wavePath,
                                color = orbContentColor,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Draw secondary wave
                            val wavePath2 = Path()
                            wavePath2.moveTo(0f, centerY)
                            for (x in 0..width.toInt()) {
                                val frequency = (2 * Math.PI.toFloat() / width) * 1.2f
                                val y = kotlin.math.sin(x * frequency - wavePhase + 1.2f) * (amplitude * 0.6f) + centerY
                                wavePath2.lineTo(x.toFloat(), y)
                            }
                            drawPath(
                                path = wavePath2,
                                color = orbContentColor.copy(alpha = 0.5f),
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                    HandsFreeState.THINKING -> {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            val centerX = width / 2
                            val centerY = height / 2

                            val baseRadius = 2.5.dp.toPx()
                            val spacing = 7.5.dp.toPx()
                            val horizontalAmplitude = 5.dp.toPx()
                            val verticalAmplitude = 1.5.dp.toPx()

                            for (i in 0 until 5) {
                                // Staggered phase for each dot
                                val dotPhase = wavePhase - i * 0.6f

                                // 3D-like orbital offsets
                                val dx = horizontalAmplitude * kotlin.math.cos(dotPhase)
                                val dy = verticalAmplitude * kotlin.math.sin(dotPhase)

                                val homeX = centerX + (i - 2) * spacing
                                val x = homeX + dx
                                val y = centerY + dy

                                // Scale and alpha oscillate to create depth/3D effect
                                val scale = 0.8f + 0.4f * ((kotlin.math.sin(dotPhase) + 1f) / 2f)
                                val alpha = 0.4f + 0.6f * ((kotlin.math.sin(dotPhase) + 1f) / 2f)

                                drawCircle(
                                    color = orbContentColor,
                                    radius = baseRadius * scale,
                                    center = Offset(x, y),
                                    alpha = alpha
                                )
                            }
                        }
                    }
                    HandsFreeState.SPEAKING -> {
                        // Out of the box: Active voice equalizer with varied baseline heights
                        val speakTransition = rememberInfiniteTransition(label = "speakingEqualizer")
                        val s1 by speakTransition.animateFloat(
                            initialValue = 0.15f,
                            targetValue = 0.9f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(350, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "speakBar1"
                        )
                        val s2 by speakTransition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 0.75f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(280, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "speakBar2"
                        )
                        val s3 by speakTransition.animateFloat(
                            initialValue = 0.1f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(420, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "speakBar3"
                        )
                        val s4 by speakTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 0.8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(310, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "speakBar4"
                        )
                        val s5 by speakTransition.animateFloat(
                            initialValue = 0.15f,
                            targetValue = 0.6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(460, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "speakBar5"
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Box(Modifier.width(3.dp).height(8.dp + 16.dp * s1).background(orbContentColor, CircleShape))
                            Box(Modifier.width(3.dp).height(12.dp + 14.dp * s2).background(orbContentColor, CircleShape))
                            Box(Modifier.width(3.dp).height(6.dp + 22.dp * s3).background(orbContentColor, CircleShape))
                            Box(Modifier.width(3.dp).height(10.dp + 18.dp * s4).background(orbContentColor, CircleShape))
                            Box(Modifier.width(3.dp).height(8.dp + 12.dp * s5).background(orbContentColor, CircleShape))
                        }
                    }
                }
            }
        }

        // Right actions: Mic and Close
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onToggleMute
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic
                    ),
                    contentDescription = if (isMuted) "Unmute Mic" else "Mute Mic",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onExitHandsFree
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit Hands-free",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HandsFreeBarStartingPreview() {
    AssistantTheme {
        HandsFreeBar(
            isSpeaking = false,
            isListening = false,
            isThinking = false,
            isMicReady = false,
            onExitHandsFree = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HandsFreeBarListeningPreview() {
    AssistantTheme {
        HandsFreeBar(
            isSpeaking = false,
            isListening = true,
            isMicReady = true,
            isThinking = false,
            onExitHandsFree = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HandsFreeBarThinkingPreview() {
    AssistantTheme {
        HandsFreeBar(
            isSpeaking = false,
            isListening = false,
            isMicReady = true,
            isThinking = true,
            onExitHandsFree = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HandsFreeBarSpeakingPreview() {
    AssistantTheme {
        HandsFreeBar(
            isSpeaking = true,
            isListening = false,
            isMicReady = true,
            isThinking = false,
            onExitHandsFree = {}
        )
    }
}
