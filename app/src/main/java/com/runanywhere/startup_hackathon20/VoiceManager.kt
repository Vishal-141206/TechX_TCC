package com.runanywhere.startup_hackathon20

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Voice Manager for text-to-speech functionality
 * Provides voice summaries for finance insights
 */
class VoiceManager(context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeechCompleted: (() -> Unit)? = null

    init {
        initializeTTS(context)
    }

    private fun initializeTTS(context: Context) {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("VoiceManager", "Language not supported")
                } else {
                    isInitialized = true
                    setupUtteranceListener()
                    Log.d("VoiceManager", "TTS Initialized successfully")
                }
            } else {
                Log.e("VoiceManager", "TTS Initialization failed")
            }
        }
    }

    private fun setupUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("VoiceManager", "Speech started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d("VoiceManager", "Speech completed: $utteranceId")
                onSpeechCompleted?.invoke()
                onSpeechCompleted = null
            }

            override fun onError(utteranceId: String?) {
                Log.e("VoiceManager", "Speech error: $utteranceId")
                onSpeechCompleted?.invoke()
                onSpeechCompleted = null
            }
        })
    }

    fun speak(text: String, onCompleted: (() -> Unit)? = null) {
        if (!isInitialized || textToSpeech == null) {
            Log.e("VoiceManager", "TTS not initialized")
            onCompleted?.invoke()
            return
        }

        onSpeechCompleted = onCompleted

        // Clear any pending speech
        stop()

        // Speak the text
        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "voice_utterance"
        )
    }

    fun generateVoiceSummary(prediction: CashFlowPrediction): String {
        return """
            Cash Flow Summary:
            Total Income: ₹${String.format("%.2f", prediction.totalIncome)}
            Total Expenses: ₹${String.format("%.2f", prediction.totalExpenses)}
            Net Cash Flow: ₹${String.format("%.2f", prediction.netCashFlow)}
            ${prediction.recommendation}
        """.trimIndent()
    }

    fun generateTransactionSummary(
        totalMessages: Int,
        totalDebits: Double,
        totalCredits: Double
    ): String {
        return """
            Transaction Summary:
            Analyzed $totalMessages SMS messages.
            Total Money Spent: ₹${String.format("%.2f", totalDebits)}
            Total Money Received: ₹${String.format("%.2f", totalCredits)}
            Net Balance Change: ₹${String.format("%.2f", totalCredits - totalDebits)}
        """.trimIndent()
    }

    fun generateScamAlert(scamCount: Int): String {
        return when {
            scamCount == 0 -> "Great news! No scam messages detected in your SMS."
            scamCount == 1 -> "Caution! Found 1 potential scam message. Please review your SMS carefully."
            else -> "Alert! Found $scamCount potential scam messages. Please review your SMS immediately."
        }
    }

    fun stop() {
        textToSpeech?.stop()
        onSpeechCompleted?.invoke()
        onSpeechCompleted = null
    }

    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }
}