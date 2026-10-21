package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State representing current status of the voice-to-text recognition process.
 */
sealed class VoiceInputState {
    data object Idle : VoiceInputState()
    data class Listening(val rmsDb: Float = 0f, val partialText: String = "") : VoiceInputState()
    data class Processing(val text: String = "") : VoiceInputState()
    data class Success(val query: String) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}

/**
 * Manages device microphone voice-to-text recognition using Android's native SpeechRecognizer.
 * Translates spoken queries into text to be transmitted directly to Xiaomi Mi TV as HID keyboard keystrokes.
 */
class VoiceInputHelper(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputHelper"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val voiceState: StateFlow<VoiceInputState> = _voiceState.asStateFlow()

    private var currentOnSuccess: ((String) -> Unit)? = null
    private var currentOnError: ((String) -> Unit)? = null

    /**
     * Checks if speech recognition service is available on this device.
     */
    fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            Log.w(TAG, "SpeechRecognizer check failed", e)
            false
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            _voiceState.value = VoiceInputState.Listening(rmsDb = 0f, partialText = "")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
            val currentPartial = (_voiceState.value as? VoiceInputState.Listening)?.partialText.orEmpty()
            _voiceState.value = VoiceInputState.Listening(rmsDb = 2f, partialText = currentPartial)
        }

        override fun onRmsChanged(rmsdB: Float) {
            val currentState = _voiceState.value
            if (currentState is VoiceInputState.Listening) {
                // Normalize rmsdB (typically -2 to 10 dB) to positive float for visualizer animation
                val normalized = rmsdB.coerceIn(0f, 10f)
                _voiceState.value = currentState.copy(rmsDb = normalized)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            val currentPartial = (_voiceState.value as? VoiceInputState.Listening)?.partialText.orEmpty()
            _voiceState.value = VoiceInputState.Processing(text = currentPartial)
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted."
                SpeechRecognizer.ERROR_NETWORK -> "Network required for speech transcription."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out during speech."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Retrying…"
                SpeechRecognizer.ERROR_SERVER -> "Speech server error. Please try again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout."
                else -> "Recognition error (code $error). Try speaking again."
            }
            Log.w(TAG, "SpeechRecognizer onError: $errorMessage (code $error)")
            _voiceState.value = VoiceInputState.Error(errorMessage)
            currentOnError?.invoke(errorMessage)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val bestMatch = matches?.firstOrNull()?.trim().orEmpty()
            Log.d(TAG, "onResults: $bestMatch")

            if (bestMatch.isNotEmpty()) {
                _voiceState.value = VoiceInputState.Success(bestMatch)
                currentOnSuccess?.invoke(bestMatch)
            } else {
                val errorMsg = "No words recognized. Please try again."
                _voiceState.value = VoiceInputState.Error(errorMsg)
                currentOnError?.invoke(errorMsg)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim().orEmpty()
            if (partial.isNotEmpty()) {
                val currentRms = (_voiceState.value as? VoiceInputState.Listening)?.rmsDb ?: 0f
                _voiceState.value = VoiceInputState.Listening(rmsDb = currentRms, partialText = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Starts listening for voice speech from the device microphone.
     */
    fun startListening(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        currentOnSuccess = onSuccess
        currentOnError = onError

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(recognitionListener)
                    }
                }

                _voiceState.value = VoiceInputState.Listening(rmsDb = 0f, partialText = "")

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognition", e)
                val msg = "Speech recognizer unavailable on this device."
                _voiceState.value = VoiceInputState.Error(msg)
                onError(msg)
            }
        }
    }

    /**
     * Stops listening and requests processing of speech received so far.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e)
            }
        }
    }

    /**
     * Cancels any active speech recognition session.
     */
    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error canceling SpeechRecognizer", e)
            }
            _voiceState.value = VoiceInputState.Idle
        }
    }

    /**
     * Resets the state back to Idle without releasing the engine.
     */
    fun resetState() {
        _voiceState.value = VoiceInputState.Idle
    }

    /**
     * For testing: simulate a voice recognition result.
     */
    fun simulateResultForTesting(query: String) {
        _voiceState.value = VoiceInputState.Success(query)
        currentOnSuccess?.invoke(query)
    }

    /**
     * Releases speech recognizer resources on cleanup.
     */
    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying SpeechRecognizer", e)
            }
            speechRecognizer = null
            _voiceState.value = VoiceInputState.Idle
        }
    }
}
