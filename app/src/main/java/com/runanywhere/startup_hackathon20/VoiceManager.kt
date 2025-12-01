package com.runanywhere.startup_hackathon20

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import kotlin.math.roundToInt

/**
 * Voice Manager for Text-to-Speech (TTS)
 * Provides voice output for financial summaries and insights
 *
 * Privacy: All TTS happens on-device, no data sent to cloud
 */
class VoiceManager(private val context: Context) {

    private var tts: TextToSpeech? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                _isInitialized.value = true
                Log.d("VoiceManager", "TTS initialized successfully")
            } else {
                Log.e("VoiceManager", "TTS initialization failed")
                _isInitialized.value = false
            }
        }
    }

    /**
     * Speak text out loud
     * @param text The text to speak
     * @param onComplete Callback when speaking is done
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!_isInitialized.value) {
            Log.w("VoiceManager", "TTS not initialized yet")
            return
        }

        _isSpeaking.value = true

        // Set callback for when speech finishes
        tts?.setOnUtteranceProgressListener(object :
            android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onComplete?.invoke()
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                Log.e("VoiceManager", "TTS error")
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VoiceSummary")
    }

    /**
     * Stop speaking immediately
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * Generate a voice summary from cash flow prediction
     */
    fun generateVoiceSummary(prediction: CashFlowPrediction): String {
        return buildString {
            // Introduction
            append("Here's your financial summary. ")

            // Balance statement
            val balance = prediction.predictedBalance
            if (balance >= 0) {
                append("Good news! You're expected to have a surplus of ${formatAmount(balance)} next month. ")
            } else {
                append("Warning! You may face a deficit of ${formatAmount(kotlin.math.abs(balance))} next month. ")
            }

            // Income and expenses
            append("Your predicted income is ${formatAmount(prediction.nextMonthIncome)}, ")
            append("and expenses are ${formatAmount(prediction.nextMonthExpenses)}. ")

            // Confidence
            append("This prediction has ${prediction.confidence.lowercase()} confidence. ")

            // Key insights (max 3 for voice)
            val topInsights = prediction.insights.take(3)
            if (topInsights.isNotEmpty()) {
                append("Key insights: ")
                topInsights.forEachIndexed { index, insight ->
                    // Remove emojis for voice
                    val cleanInsight = insight.replace(Regex("[^A-Za-z0-9 .,:%₹-]"), "")
                    append(cleanInsight)
                    if (index < topInsights.size - 1) {
                        append(". ")
                    }
                }
            }

            // Top spending category
            val topCategory = prediction.categoryBreakdown.maxByOrNull { it.value.totalSpent }
            if (topCategory != null) {
                append(" Your highest spending is in ${topCategory.key}, ")
                append("totaling ${formatAmount(topCategory.value.totalSpent)}. ")
            }

            // Recurring subscriptions
            val subscriptions = prediction.recurringTransactions.filter {
                it.frequency == "Monthly" && it.confidence > 70
            }
            if (subscriptions.isNotEmpty()) {
                append("You have ${subscriptions.size} recurring monthly subscription")
                if (subscriptions.size > 1) append("s")
                append(", costing ${formatAmount(subscriptions.sumOf { it.averageAmount })} per month. ")
            }

            // Closing
            append("That's your financial overview. Tap for more details.")
        }
    }

    /**
     * Generate quick transaction summary
     */
    fun generateTransactionSummary(count: Int, totalDebits: Double, totalCredits: Double): String {
        return buildString {
            append("You have $count transactions. ")
            append("Total spending: ${formatAmount(totalDebits)}. ")
            append("Total income: ${formatAmount(totalCredits)}. ")

            val balance = totalCredits - totalDebits
            if (balance >= 0) {
                append("Net balance: positive ${formatAmount(balance)}.")
            } else {
                append("Net balance: negative ${formatAmount(kotlin.math.abs(balance))}.")
            }
        }
    }

    /**
     * Generate scam alert message
     */
    fun generateScamAlert(scamCount: Int): String {
        return if (scamCount > 0) {
            "Alert! $scamCount potentially suspicious messages detected. Please review them carefully."
        } else {
            "Good news! No suspicious messages found. Your transactions appear safe."
        }
    }

    /**
     * Format rupee amount for voice
     * Converts ₹12450.50 to "12,450 rupees and 50 paise"
     */
    private fun formatAmount(amount: Double): String {
        val rupees = amount.toInt()
        val paise = ((amount - rupees) * 100).roundToInt()

        return buildString {
            append("${formatNumber(rupees)} rupees")
            if (paise > 0) {
                append(" and $paise paise")
            }
        }
    }

    /**
     * Format number with proper pronunciation
     * 1234 -> "1,234" -> "one thousand two hundred thirty four"
     */
    private fun formatNumber(number: Int): String {
        return when {
            number >= 10000000 -> "${number / 10000000} crore ${(number % 10000000) / 100000} lakh"
            number >= 100000 -> "${number / 100000} lakh ${(number % 100000) / 1000} thousand"
            number >= 1000 -> "${number / 1000} thousand ${number % 1000}"
            else -> number.toString()
        }
    }

    /**
     * Speak cash flow summary
     */
    fun speakCashFlowSummary(prediction: CashFlowPrediction, onComplete: (() -> Unit)? = null) {
        val summary = generateVoiceSummary(prediction)
        speak(summary, onComplete)
    }

    /**
     * Speak quick stats
     */
    fun speakQuickStats(stats: String, onComplete: (() -> Unit)? = null) {
        speak(stats, onComplete)
    }

    /**
     * Cleanup when done
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
        _isSpeaking.value = false
    }
}