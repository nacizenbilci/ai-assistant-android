package com.app.assistant.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.sin

@Suppress("UNUSED_PARAMETER")
@Composable
fun FullScreenHandsFreeDialog(
    isSpeaking: Boolean,
    isListening: Boolean,
    isThinking: Boolean,
    isMicReady: Boolean,
    onExitHandsFree: () -> Unit,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    isVisionModeActive: Boolean = false,
    onToggleVisionMode: () -> Unit = {},
    isScreenModeActive: Boolean = false,
    onToggleScreenMode: () -> Unit = {},
    audioAmplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    val currentState = remember(
        isSpeaking,
        isListening,
        isThinking,
        isMicReady
    ) {
        when {
            isThinking -> HandsFreeState.THINKING
            isSpeaking -> HandsFreeState.SPEAKING
            isListening -> HandsFreeState.LISTENING
            isMicReady -> HandsFreeState.LISTENING
            else -> HandsFreeState.STARTING
        }
    }

    val statusText = when (currentState) {
        HandsFreeState.STARTING -> "Hazırlanıyor..."
        HandsFreeState.LISTENING -> "Dinliyorum..."
        HandsFreeState.THINKING -> "Düşünüyorum..."
        HandsFreeState.SPEAKING -> "Konuşuyorum..."
    }

    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true

        onDispose {
            view.keepScreenOn = false
        }
    }

    val infiniteTransition =
        rememberInfiniteTransition(label = "sabanVoiceAnimation")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1400,
                easing = LinearEasing
            )
        ),
        label = "wavePhase"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 900,
                easing = FastOutSlowInEasing
            )
        ),
        label = "orbPulse"
    )

    val targetAmplitude = when (currentState) {
        HandsFreeState.LISTENING ->
            (0.15f + audioAmplitude.coerceAtLeast(0f)).coerceAtMost(1f)

        HandsFreeState.SPEAKING -> 0.7f
        HandsFreeState.THINKING -> 0.35f
        HandsFreeState.STARTING -> 0.15f
    }

    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(120),
        label = "voiceAmplitude"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Canvas(
                        modifier = Modifier.size(310.dp)
                    ) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f

                        val baseRadius =
                            size.minDimension * 0.22f * pulse

                        drawCircle(
                            color = primaryColor.copy(alpha = 0.08f),
                            radius = baseRadius * 1.8f,
                            center = Offset(centerX, centerY)
                        )

                        drawCircle(
                            color = primaryColor.copy(alpha = 0.16f),
                            radius = baseRadius * 1.45f,
                            center = Offset(centerX, centerY)
                        )

                        drawCircle(
                            color = primaryColor.copy(alpha = 0.22f),
                            radius = baseRadius,
                            center = Offset(centerX, centerY)
                        )

                        val barCount = 25
                        val usableWidth = size.width * 0.72f
                        val startX =
                            centerX - usableWidth / 2f
                        val spacing =
                            usableWidth / (barCount - 1)

                        for (i in 0 until barCount) {

                            val wave =
                                (sin(
                                    phase +
                                        i.toFloat() * 0.52f
                                ) + 1f) / 2f

                            val distanceFromCenter =
                                kotlin.math.abs(
                                    i - (barCount - 1) / 2f
                                ) / (barCount / 2f)

                            val centerWeight =
                                1f - distanceFromCenter * 0.55f

                            val halfHeight =
                                size.height *
                                    (0.025f +
                                        wave * 0.07f +
                                        animatedAmplitude * 0.12f) *
                                    centerWeight

                            val x =
                                startX + spacing * i

                            drawLine(
                                color = primaryColor,
                                start = Offset(
                                    x,
                                    centerY - halfHeight
                                ),
                                end = Offset(
                                    x,
                                    centerY + halfHeight
                                ),
                                strokeWidth = 7f
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(18.dp)
                    )

                    Text(
                        text = "ŞABAN",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Text(
                        text = statusText,
                        fontSize = 20.sp,
                        color = primaryColor
                    )
                }

                Button(
                    onClick = onExitHandsFree,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "DİNLEMEYİ KAPAT",
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 7.dp
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
