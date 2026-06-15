package com.app.assistant.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {

    private class MockStateCallback : VoiceStateMachine.StateCallback {
        var transitionToListeningCalled = 0
        var transitionToProcessingCalled = 0
        var transitionToBotSpeakingCalled = 0
        var transitionToIdleCalled = 0
        var stopTtsPlaybackCalled = 0
        var onMicReadyValues = mutableListOf<Boolean>()
        var lastSamples: FloatArray? = null

        override fun onTransitionToListening() {
            transitionToListeningCalled++
        }

        override fun onTransitionToProcessing(speechSamples: FloatArray) {
            transitionToProcessingCalled++
            lastSamples = speechSamples
        }

        override fun onTransitionToBotSpeaking() {
            transitionToBotSpeakingCalled++
        }

        override fun onTransitionToIdle() {
            transitionToIdleCalled++
        }

        override fun stopTtsPlayback() {
            stopTtsPlaybackCalled++
        }

        override fun onMicReady(ready: Boolean) {
            onMicReadyValues.add(ready)
        }
    }

    @Test
    fun testBasicFlowManualMode() {
        val callback = MockStateCallback()
        val stateMachine = VoiceStateMachine(isHandsFree = false, callback = callback)

        assertEquals(VoiceState.IDLE, stateMachine.getCurrentState())

        // Start listening
        stateMachine.onUserStartedListening()
        assertEquals(VoiceState.LISTENING, stateMachine.getCurrentState())
        assertEquals(1, callback.transitionToListeningCalled)
        assertEquals(listOf(true), callback.onMicReadyValues)

        // Speech start (no state change, just listening)
        stateMachine.onSpeechStartDetected()
        assertEquals(VoiceState.LISTENING, stateMachine.getCurrentState())
        assertEquals(2, callback.transitionToListeningCalled)

        // Speech end (transitions to processing)
        val dummySamples = floatArrayOf(0.1f, 0.2f, 0.3f)
        stateMachine.onSpeechEndDetected(dummySamples)
        assertEquals(VoiceState.PROCESSING, stateMachine.getCurrentState())
        assertEquals(1, callback.transitionToProcessingCalled)
        assertEquals(dummySamples, callback.lastSamples)
        assertEquals(listOf(true, false), callback.onMicReadyValues)

        // TTS starts playing
        stateMachine.onTtsStateChanged(isSpeaking = true)
        assertEquals(VoiceState.BOT_SPEAKING, stateMachine.getCurrentState())
        assertEquals(1, callback.transitionToBotSpeakingCalled)

        // TTS finishes (transitions to IDLE in manual mode)
        stateMachine.onTtsStateChanged(isSpeaking = false)
        assertEquals(VoiceState.IDLE, stateMachine.getCurrentState())
        assertEquals(1, callback.transitionToIdleCalled)
        assertEquals(listOf(true, false, false), callback.onMicReadyValues)
    }

    @Test
    fun testHandsFreeLoop() {
        val callback = MockStateCallback()
        val stateMachine = VoiceStateMachine(isHandsFree = true, callback = callback)

        // Start listening
        stateMachine.onUserStartedListening()
        assertEquals(VoiceState.LISTENING, stateMachine.getCurrentState())

        // Speech end
        stateMachine.onSpeechEndDetected(floatArrayOf(0.0f))
        assertEquals(VoiceState.PROCESSING, stateMachine.getCurrentState())

        // TTS starts
        stateMachine.onTtsStateChanged(isSpeaking = true)
        assertEquals(VoiceState.BOT_SPEAKING, stateMachine.getCurrentState())

        // TTS finishes (transitions back to LISTENING in hands-free mode)
        stateMachine.onTtsStateChanged(isSpeaking = false)
        assertEquals(VoiceState.LISTENING, stateMachine.getCurrentState())
        assertEquals(2, callback.transitionToListeningCalled)
        assertEquals(listOf(true, false, true), callback.onMicReadyValues)
    }

    @Test
    fun testInterruptionFlow() {
        val callback = MockStateCallback()
        val stateMachine = VoiceStateMachine(isHandsFree = true, callback = callback)

        // Listen -> Speech End -> TTS Starts
        stateMachine.onUserStartedListening()
        stateMachine.onSpeechEndDetected(floatArrayOf(0.0f))
        stateMachine.onTtsStateChanged(isSpeaking = true)
        assertEquals(VoiceState.BOT_SPEAKING, stateMachine.getCurrentState())

        // User interrupts during TTS!
        stateMachine.onSpeechStartDetected()
        assertEquals(VoiceState.LISTENING, stateMachine.getCurrentState())
        assertEquals(1, callback.stopTtsPlaybackCalled)
        assertEquals(2, callback.transitionToListeningCalled)
        assertEquals(listOf(true, false, true), callback.onMicReadyValues)

        // User finishes speaking new question
        val newSamples = floatArrayOf(0.9f, 0.8f)
        stateMachine.onSpeechEndDetected(newSamples)
        assertEquals(VoiceState.PROCESSING, stateMachine.getCurrentState())
        assertEquals(dummySamplesMatch(newSamples, callback.lastSamples), true)
    }

    private fun dummySamplesMatch(expected: FloatArray, actual: FloatArray?): Boolean {
        if (actual == null || expected.size != actual.size) return false
        for (i in expected.indices) {
            if (expected[i] != actual[i]) return false
        }
        return true
    }
}
