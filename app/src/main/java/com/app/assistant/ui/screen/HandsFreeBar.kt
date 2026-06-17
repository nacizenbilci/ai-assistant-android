package com.app.assistant.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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

    // Dynamic state transition parameters
    val startingAlpha by animateFloatAsState(
        targetValue = if (currentState == HandsFreeState.STARTING) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "startingAlpha"
    )

    val listeningProgress by animateFloatAsState(
        targetValue = if (currentState == HandsFreeState.LISTENING) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "listeningProgress"
    )

    val thinkingProgress by animateFloatAsState(
        targetValue = if (currentState == HandsFreeState.THINKING) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "thinkingProgress"
    )

    val speakingProgress by animateFloatAsState(
        targetValue = if (currentState == HandsFreeState.SPEAKING) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "speakingProgress"
    )

    val dotsSpreadProgress by animateFloatAsState(
        targetValue = if (currentState == HandsFreeState.THINKING || currentState == HandsFreeState.SPEAKING) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "dotsSpreadProgress"
    )

    // Keep the orb background color and content color consistent across all states
    val orbBackgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    val orbContentColor = MaterialTheme.colorScheme.primary

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
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Starting state animations
    val startingRotation by infiniteTransition.animateFloat(
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

    // Speaking state voice equalizer animations
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
            // Outer pulsing ring (drawn behind using graphicsLayer scale so it does not affect parent height)
            if (thinkingProgress > 0f) {
                Box(
                    modifier = Modifier
                        .size(orbWidth, orbHeight)
                        .graphicsLayer {
                            scaleX = thinkingPulseScale
                            scaleY = thinkingPulseScale
                            alpha = thinkingPulseAlpha * thinkingProgress
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
                val colorPrimary = MaterialTheme.colorScheme.primary
                val colorSecondary = MaterialTheme.colorScheme.secondary
                val colorTertiary = MaterialTheme.colorScheme.tertiary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerX = width / 2
                    val centerY = height / 2

                    // Helper to lerp floats
                    fun lerp(start: Float, stop: Float, fraction: Float): Float {
                        return start + fraction * (stop - start)
                    }

                    // A. STARTING outer ring rotating arc segment
                    if (startingAlpha > 0f) {
                        val arcSize = 32.dp.toPx()
                        rotate(degrees = startingRotation, pivot = Offset(centerX, centerY)) {
                            drawArc(
                                color = orbContentColor,
                                startAngle = 0f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = Offset(centerX - arcSize / 2, centerY - arcSize / 2),
                                size = Size(arcSize, arcSize),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                alpha = startingAlpha
                            )
                        }
                    }

                    // B. STARTING core dot (solid color)
                    if (startingAlpha > 0f) {
                        val startDotRadius = 6.dp.toPx() * coreScale
                        drawCircle(
                            color = orbContentColor,
                            radius = startDotRadius,
                            center = Offset(centerX, centerY),
                            alpha = startingAlpha
                        )
                    }

                    // C. LISTENING state morphing blob (gradient with white outline)
                    if (listeningProgress > 0f) {
                        // 1. Soft glowing background aura inside the capsule
                        val auraRadius = (height / 2) * 0.85f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colorTertiary.copy(alpha = 0.3f * listeningProgress),
                                    colorPrimary.copy(alpha = 0.1f * listeningProgress),
                                    Color.Transparent
                                ),
                                center = Offset(centerX, centerY),
                                radius = auraRadius
                            ),
                            radius = auraRadius,
                            center = Offset(centerX, centerY)
                        )

                        // 2. Morphing Blob Path calculation
                        val baseRadius = lerp(6.dp.toPx(), height * 0.32f, listeningProgress)
                        val amp1 = lerp(0f, 2.dp.toPx(), listeningProgress)
                        val amp2 = lerp(0f, 1.5.dp.toPx(), listeningProgress)

                        val blobPath = Path()
                        val steps = 72
                        for (i in 0..steps) {
                            val angle = (i * 2 * Math.PI / steps).toFloat()
                            val r = baseRadius +
                                    amp1 * kotlin.math.sin(2 * angle + wavePhase) +
                                    amp2 * kotlin.math.cos(3 * angle - wavePhase)
                            val x = centerX + r * kotlin.math.cos(angle)
                            val y = centerY + r * kotlin.math.sin(angle)
                            if (i == 0) {
                                blobPath.moveTo(x, y)
                            } else {
                                blobPath.lineTo(x, y)
                            }
                        }
                        blobPath.close()

                        val blobGradient = Brush.linearGradient(
                            colors = listOf(colorPrimary, colorTertiary, colorSecondary),
                            start = Offset(centerX - baseRadius, centerY - baseRadius),
                            end = Offset(centerX + baseRadius, centerY + baseRadius)
                        )

                        // Fill the blob
                        drawPath(path = blobPath, brush = blobGradient, alpha = listeningProgress)

                        // Thin white outline
                        drawPath(
                            path = blobPath,
                            color = Color.White.copy(alpha = 0.4f * listeningProgress),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // D. THINKING and SPEAKING states: 5 orbiting dots / equalizer bars
                    if (dotsSpreadProgress > 0f) {
                        val spacing = 8.dp.toPx()
                        val dotWidth = 5.dp.toPx()
                        val dotHeight = 5.dp.toPx()

                        val horizontalAmplitude = 5.dp.toPx()
                        val verticalAmplitude = 1.5.dp.toPx()

                        for (i in 0 until 5) {
                            // Equalizer height for this bar
                            val eqHeight = when (i) {
                                0 -> 8.dp.toPx() + 16.dp.toPx() * s1
                                1 -> 12.dp.toPx() + 14.dp.toPx() * s2
                                2 -> 6.dp.toPx() + 22.dp.toPx() * s3
                                3 -> 10.dp.toPx() + 18.dp.toPx() * s4
                                else -> 8.dp.toPx() + 12.dp.toPx() * s5
                            }

                            // Interpolate height between circle (dotHeight) and equalizer bar height
                            val currentHeight = lerp(dotHeight, eqHeight, speakingProgress)

                            // 3D-like orbital offsets (only active in THINKING)
                            val dotPhase = wavePhase - i * 0.6f
                            val dx = horizontalAmplitude * kotlin.math.cos(dotPhase.toDouble()).toFloat() * thinkingProgress
                            val dy = verticalAmplitude * kotlin.math.sin(dotPhase.toDouble()).toFloat() * thinkingProgress

                            // Interpolate home horizontal position based on spread progress
                            val homeX = centerX + (i - 2) * spacing * dotsSpreadProgress
                            val x = homeX + dx
                            val y = centerY + dy

                            val topLeftX = x - dotWidth / 2
                            val topLeftY = y - currentHeight / 2

                            drawRoundRect(
                                color = orbContentColor,
                                topLeft = Offset(topLeftX, topLeftY),
                                size = Size(dotWidth, currentHeight),
                                cornerRadius = CornerRadius(dotWidth / 2, dotWidth / 2),
                                alpha = dotsSpreadProgress
                            )
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
