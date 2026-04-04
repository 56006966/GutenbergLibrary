package com.kdhuf.projectgutenberglibrary.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

data class ReaderTtsEngineState(
    val isReady: Boolean = false,
    val isUnavailable: Boolean = false,
    val availableVoices: List<Voice> = emptyList()
)

interface ReaderTtsControllerListener {
    fun onEngineStateChanged(state: ReaderTtsEngineState)
    fun onRangeStart(start: Int, end: Int)
    fun onUtteranceDone()
    fun onUtteranceError()
}

class ReaderTtsController(
    context: Context,
    private val preferredVoiceNameProvider: () -> String?,
    private val onPreferredVoiceSelected: (String) -> Unit,
    private val listener: ReaderTtsControllerListener
) : TextToSpeech.OnInitListener {
    private val logTag = "ReaderTtsController"

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var textToSpeech: TextToSpeech? = null
    private var pendingInitStatus: Int? = null

    var state: ReaderTtsEngineState = ReaderTtsEngineState()
        private set

    override fun onInit(status: Int) {
        val tts = textToSpeech
        if (tts == null) {
            pendingInitStatus = status
            debugLog(logTag) { "Received TTS init callback before engine reference was stored; deferring setup" }
            return
        }
        pendingInitStatus = null
        debugLog(logTag) { "onInit status=$status" }
        completeInitialization(tts, status)
    }

    fun ensureInitialized() {
        if (textToSpeech != null) return
        debugLog(logTag) { "Creating TextToSpeech instance" }
        updateState(state.copy(isReady = false, isUnavailable = false, availableVoices = emptyList()))
        val createdTts = TextToSpeech(appContext, this)
        textToSpeech = createdTts
        pendingInitStatus?.let { deferredStatus ->
            pendingInitStatus = null
            completeInitialization(createdTts, deferredStatus)
        }
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun speak(text: String, utteranceId: String): Boolean {
        val tts = textToSpeech ?: return false
        tts.stop()
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        debugLog(logTag) { "speak utteranceId=$utteranceId result=$result textLength=${text.length}" }
        if (result == TextToSpeech.ERROR) {
            markUnavailableAndRelease("speak returned ERROR")
            return false
        }
        return true
    }

    fun selectVoice(voice: Voice) {
        val tts = textToSpeech ?: return
        tts.voice = voice
        tts.language = voice.locale
        onPreferredVoiceSelected(voice.name)
    }

    fun release() {
        debugLog(logTag) { "Releasing TextToSpeech instance" }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        pendingInitStatus = null
        state = ReaderTtsEngineState()
    }

    private fun completeInitialization(tts: TextToSpeech, status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            markUnavailableAndRelease("init failed with status=$status")
            return
        }

        val defaultLanguageResult = tts.setLanguage(Locale.getDefault())
        val defaultVoice = tts.defaultVoice
        val allVoices = tts.voices
            ?.sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { it.locale.displayName }, { it.name }))
            .orEmpty()
        val localVoices = allVoices.filter { voice -> !voice.isNetworkConnectionRequired }

        debugLog(logTag) {
            "Init languageResult=$defaultLanguageResult defaultLocale=${Locale.getDefault()} " +
                "defaultVoice=${defaultVoice?.name} localVoiceCount=${localVoices.size} totalVoiceCount=${allVoices.size}"
        }

        val selectableVoices = if (localVoices.isNotEmpty()) localVoices else allVoices

        val preferredVoiceName = preferredVoiceNameProvider()
        val preferredVoice = selectableVoices.firstOrNull { it.name == preferredVoiceName }
        val selectedVoice = preferredVoice
            ?: selectableVoices.firstOrNull { voice ->
                voice.locale.language == Locale.getDefault().language
            }
            ?: selectableVoices.firstOrNull()
            ?: defaultVoice

        selectedVoice?.let { voice ->
            try {
                tts.voice = voice
                tts.language = voice.locale
                if (voice.name != preferredVoiceName) {
                    onPreferredVoiceSelected(voice.name)
                }
                debugLog(logTag) {
                    "Selected voice=${voice.name} locale=${voice.locale} networkRequired=${voice.isNetworkConnectionRequired}"
                }
            } catch (exception: Exception) {
                Log.w(logTag, "Failed to apply selected voice ${voice.name}", exception)
            }
        }

        val canUseDefaultLanguage = defaultLanguageResult != TextToSpeech.LANG_MISSING_DATA &&
            defaultLanguageResult != TextToSpeech.LANG_NOT_SUPPORTED
        val isReady = selectedVoice != null || canUseDefaultLanguage
        if (!isReady) {
            markUnavailableAndRelease(
                "no usable voice and default language unsupported result=$defaultLanguageResult"
            )
            return
        }

        tts.setSpeechRate(1.0f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                mainHandler.post {
                    listener.onRangeStart(start, end)
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    listener.onUtteranceDone()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.w(logTag, "Utterance error for utteranceId=$utteranceId")
                markUnavailableAndRelease("utterance progress listener reported error")
                mainHandler.post {
                    listener.onUtteranceError()
                }
            }
        })

        updateState(
            ReaderTtsEngineState(
                isReady = isReady,
                isUnavailable = false,
                availableVoices = selectableVoices
            )
        )
    }

    private fun markUnavailableAndRelease(reason: String) {
        Log.w(logTag, "Marking TTS unavailable: $reason")
        try {
            textToSpeech?.shutdown()
        } catch (exception: Exception) {
            Log.w(logTag, "shutdown failed while marking TTS unavailable", exception)
        }
        textToSpeech = null
        pendingInitStatus = null
        updateState(ReaderTtsEngineState(isReady = false, isUnavailable = true, availableVoices = emptyList()))
    }

    private fun updateState(newState: ReaderTtsEngineState) {
        state = newState
        mainHandler.post {
            listener.onEngineStateChanged(newState)
        }
    }
}
