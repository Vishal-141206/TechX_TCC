package com.runanywhere.startup_hackathon20

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.runanywhere.sdk.public.RunAnywhere
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.max

/**
 * Robust VoiceManager: TTS + model-first ASR with platform fallback.
 *
 * Public API:
 *  - speak(text, onCompleted)
 *  - startListening(onPartial, onFinal, onError, onStopped, continuous = true)
 *  - stopListening()
 *  - shutdown()
 *
 * Notes:
 * - Caller (Activity/Compose) must request RECORD_AUDIO permission at runtime.
 * - This implementation attempts to call RunAnywhere.transcribeAudio(byte[], sampleRate) via reflection
 *   for model ASR. If not present it falls back to Android SpeechRecognizer.
 */
class VoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceManager"
        private const val SAMPLE_RATE = 16000                   // model-friendly sample rate
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_MS = 200                       // frequency to poll audio (ms)
        private const val SILENCE_THRESHOLD = 200               // RMS threshold for "speech"
        private const val SILENCE_TIMEOUT_MS = 1200L            // treat silence as end of utterance
        private const val MAX_RECORD_MS = 30_000L               // safety max recording length
        private const val TRY_MODEL_ASR = true                  // true = attempt SDK model ASR first
    }

    // ---- TTS ----
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var onSpeechCompleted: (() -> Unit)? = null

    // ---- ASR state ----
    private var speechRecognizer: SpeechRecognizer? = null
    private var isPlatformListening = false

    private var audioRecord: AudioRecord? = null
    private var isModelListening = false
    private var modelJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Continuous listening flags
    private var continuousListening: Boolean = true
    private var userRequestedStop: Boolean = false

    init {
        initializeTTS(context)
    }

    // ---------------- TTS ----------------
    private fun initializeTTS(context: Context) {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = textToSpeech?.setLanguage(Locale.getDefault())
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS language not supported ($res)")
                } else {
                    isTtsInitialized = true
                    setupUtteranceListener()
                    Log.d(TAG, "TTS initialized")
                }
            } else {
                Log.e(TAG, "TTS init failed (status=$status)")
            }
        }
    }

    private fun setupUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS onStart: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS onDone: $utteranceId")
                onSpeechCompleted?.invoke()
                onSpeechCompleted = null
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS onError: $utteranceId")
                onSpeechCompleted?.invoke()
                onSpeechCompleted = null
            }
        })
    }

    fun speak(text: String, onCompleted: (() -> Unit)? = null) {
        if (!isTtsInitialized || textToSpeech == null) {
            Log.e(TAG, "TTS not initialized")
            onCompleted?.invoke()
            return
        }
        onSpeechCompleted = onCompleted
        // flush any pending speech
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "TTS stop before speak failed: ${e.message}")
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_utterance")
    }

    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "TTS stop exception: ${e.message}", e)
        }
        onSpeechCompleted?.invoke()
        onSpeechCompleted = null
    }

    fun shutdown() {
        stopListening()
        try {
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "TTS shutdown exception: ${e.message}", e)
        }
        textToSpeech = null
        isTtsInitialized = false
        scope.cancel()
    }

    fun isSpeaking(): Boolean = textToSpeech?.isSpeaking ?: false

    // ---------------- Generators ----------------
    fun generateVoiceSummary(prediction: CashFlowPrediction): String {
        val income = prediction.totalIncome.toInt()
        val expenses = prediction.totalExpenses.toInt()
        val netFlow = prediction.netCashFlow.toInt()
        val predictedBalance = prediction.predictedBalance.toInt()

        val savingsRate = if (income > 0) {
            ((income - expenses).toDouble() / income * 100).toInt()
        } else 0

        val flowStatus = if (netFlow >= 0) "surplus" else "deficit"
        val healthStatus = when {
            savingsRate >= 30 -> "excellent"
            savingsRate >= 20 -> "good"
            savingsRate >= 10 -> "fair"
            savingsRate >= 0 -> "needs improvement"
            else -> "critical"
        }

        return buildString {
            appendLine("Hello! Here's your 30-day financial forecast.")
            appendLine()

            // Income and expenses
            append("Your total income is ${income} rupees, ")
            appendLine("and your total expenses are ${expenses} rupees.")
            appendLine()

            // Net cash flow
            if (netFlow >= 0) {
                append("That gives you a positive cash flow of ${kotlin.math.abs(netFlow)} rupees. ")
                appendLine("Great job managing your finances!")
            } else {
                append("This results in a deficit of ${kotlin.math.abs(netFlow)} rupees. ")
                appendLine("You're spending more than you earn.")
            }
            appendLine()

            // Savings rate
            append("Your savings rate is $savingsRate percent, ")
            appendLine("which is $healthStatus.")
            appendLine()

            // Predicted balance
            if (predictedBalance >= 0) {
                appendLine(
                    "Your predicted balance for next month is ${
                        kotlin.math.abs(
                            predictedBalance
                        )
                    } rupees."
                )
            } else {
                appendLine(
                    "Warning: Your predicted balance shows a negative ${
                        kotlin.math.abs(
                            predictedBalance
                        )
                    } rupees."
                )
            }
            appendLine()

            // Smart recommendation
            appendLine("My recommendation: ${prediction.recommendation}")
            appendLine()
            appendLine("Remember, small changes today lead to big results tomorrow. Keep it up!")
        }.trimIndent()
    }

    fun generateTransactionSummary(totalMessages: Int, totalDebits: Double, totalCredits: Double): String {
        val debits = totalDebits.toInt()
        val credits = totalCredits.toInt()
        val netChange = (totalCredits - totalDebits).toInt()

        return buildString {
            appendLine("I've analyzed $totalMessages financial messages for you.")
            appendLine()

            if (credits > 0) {
                appendLine("You received a total of ${credits} rupees.")
            }

            if (debits > 0) {
                appendLine("You spent ${debits} rupees during this period.")
            }
            appendLine()

            if (netChange > 0) {
                append("Your net balance increased by ${kotlin.math.abs(netChange)} rupees. ")
                appendLine("You're building wealth steadily!")
            } else if (netChange < 0) {
                append("Your net balance decreased by ${kotlin.math.abs(netChange)} rupees. ")
                appendLine("Consider reviewing your expenses.")
            } else {
                appendLine("Your income and expenses are perfectly balanced.")
            }
            appendLine()
            appendLine("Would you like me to analyze spending patterns or suggest budget improvements?")
        }.trimIndent()
    }

    fun generateScamAlert(scamCount: Int): String {
        return when {
            scamCount == 0 -> {
                "Excellent news! I've analyzed all your messages and found zero scam attempts. " +
                        "Your financial messages are secure. However, stay vigilant and never share OTPs or passwords."
            }

            scamCount == 1 -> {
                "Alert! I've detected 1 suspicious message that could be a scam. " +
                        "Please review it carefully. Remember, banks never ask for your PIN, OTP, or CVV via SMS. " +
                        "If in doubt, contact your bank directly using their official number."
            }

            scamCount <= 3 -> {
                "Warning! I found $scamCount potentially fraudulent messages. " +
                        "These could be phishing attempts. Do not click any links or share personal information. " +
                        "Delete these messages immediately and report them to your service provider."
            }

            else -> {
                "Critical Alert! $scamCount scam messages detected. " +
                        "This is a high number and suggests your number may be targeted. " +
                        "Review and delete all suspicious messages, enable spam filters, and consider changing your number if this continues. " +
                        "Report these to cyber crime authorities if needed."
            }
        }
    }

    // ---------------- Public ASR API ----------------
    /**
     * Start listening for speech:
     *  onPartial(interimText)
     *  onFinal(finalText)
     *  onError(message)
     *  onStopped()
     *
     * continuous: automatically keep listening across utterances (default = true).
     *
     * Caller should request RECORD_AUDIO permission; this function checks permission and fails fast if missing.
     */
    fun startListening(
        onPartial: (String) -> Unit = {},
        onFinal: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onStopped: () -> Unit = {},
        continuous: Boolean = true
    ) {
        continuousListening = continuous
        userRequestedStop = false

        // permission check
        val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (perm != PermissionChecker.PERMISSION_GRANTED) {
            onError("Missing RECORD_AUDIO permission")
            onStopped()
            return
        }

        // avoid duplicate starts
        if (isModelListening || isPlatformListening) {
            Log.w(TAG, "Already listening (model=$isModelListening, platform=$isPlatformListening)")
            return
        }

        try {
            if (TRY_MODEL_ASR) {
                tryStartModelAsr(onPartial, onFinal, { modelErr ->
                    Log.w(TAG, "Model ASR failed: $modelErr — falling back to platform ASR")
                    if (!userRequestedStop) startPlatformAsr(onPartial, onFinal, onError, onStopped)
                }, onStopped)
                return
            }
            startPlatformAsr(onPartial, onFinal, onError, onStopped)
        } catch (se: SecurityException) {
            Log.e(TAG, "Permission denied starting STT: ${se.message}", se)
            onError("SecurityException: ${se.message}")
            onStopped()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in startListening: ${e.message}", e)
            onError("Failed to start listening: ${e.message}")
            onStopped()
        }
    }

    fun stopListening() {
        try {
            userRequestedStop = true

            if (isModelListening) {
                isModelListening = false
                modelJob?.cancel()
                modelJob = null
                try { audioRecord?.stop() } catch (_: Exception) {}
                try { audioRecord?.release() } catch (_: Exception) {}
                audioRecord = null
            }

            if (isPlatformListening) {
                isPlatformListening = false
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopListening exception: ${e.message}", e)
        }
    }

    // ---------------- Platform ASR implementation ----------------
    private fun startPlatformAsr(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onStopped: () -> Unit
    ) {
        if (isPlatformListening) {
            Log.w(TAG, "Platform ASR already running")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            onStopped()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        val recognizer = speechRecognizer ?: run {
            onError("Failed to create speech recognizer")
            onStopped()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "Platform STT ready") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "Platform STT begin") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.d(TAG, "Platform STT end of speech") }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error ($error)"
                }
                Log.e(TAG, "Platform STT error: $msg ($error)")
                isPlatformListening = false
                onError(msg)

                // Only restart for non-fatal errors (not NO_MATCH or SPEECH_TIMEOUT)
                val shouldRestart = continuousListening && !userRequestedStop &&
                        error != SpeechRecognizer.ERROR_NO_MATCH &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                if (shouldRestart) {
                    // backoff then restart (longer delay to avoid rapid restarts)
                    scope.launch {
                        delay(1000L)  // Increased from 300ms to 1000ms
                        if (!userRequestedStop) {
                            Log.d(TAG, "Restarting Platform ASR after error backoff")
                            startPlatformAsr(onPartial, onFinal, onError, onStopped)
                        } else {
                            onStopped()
                        }
                    }
                } else {
                    Log.d(
                        TAG,
                        "Not restarting ASR: continuous=$continuousListening, userStop=$userRequestedStop, error=$error"
                    )
                    onStopped()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                Log.d(TAG, "Platform STT final: $text")
                try {
                    onFinal(text)
                } finally {
                    isPlatformListening = false
                    if (continuousListening && !userRequestedStop) {
                        // Wait longer before restart to avoid rapid cycling
                        scope.launch {
                            delay(500L)  // Increased from 100ms to 500ms
                            if (!userRequestedStop) {
                                Log.d(TAG, "Restarting Platform ASR after successful recognition")
                                startPlatformAsr(onPartial, onFinal, onError, onStopped)
                            } else {
                                onStopped()
                            }
                        }
                    } else {
                        onStopped()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotEmpty()) {
                    Log.d(TAG, "Platform STT partial: $partial")
                    onPartial(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            isPlatformListening = true
            recognizer.startListening(intent)
            Log.d(TAG, "Platform STT started")
        } catch (e: Exception) {
            Log.e(TAG, "Platform STT start error: ${e.message}", e)
            onError("Failed to start platform STT: ${e.message}")
            isPlatformListening = false
            onStopped()
        }
    }

    // ---------------- Model ASR implementation (AudioRecord -> RunAnywhere) ----------------
    private fun tryStartModelAsr(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onModelError: (String) -> Unit,
        onStopped: () -> Unit
    ) {
        // Launch long-running recorder/transcriber in background
        modelJob = scope.launch(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = max(minBuffer, (SAMPLE_RATE * BUFFER_MS / 1000) * 2) // bytes (16-bit)
            @Suppress("MissingPermission")
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    val err = "AudioRecord init failed"
                    Log.e(TAG, err)
                    withContext(Dispatchers.Main) { onModelError(err) }
                    return@launch
                }

                @Suppress("MissingPermission")
                try {
                    audioRecord?.startRecording()
                } catch (se: SecurityException) {
                    Log.e(TAG, "startRecording SecurityException: ${se.message}", se)
                    withContext(Dispatchers.Main) {
                        onModelError("Permission denied for audio recording")
                        onStopped()
                    }
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "startRecording failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        onModelError("Audio start failed: ${e.message}")
                        onStopped()
                    }
                    return@launch
                }

                isModelListening = true
                Log.d(TAG, "Model ASR: recording started (sr=$SAMPLE_RATE)")

                val byteBuf = ByteArray(bufferSize)
                val pcmAccumulator = ArrayList<Byte>()
                var lastSpeechTs = System.currentTimeMillis()
                var startTs = System.currentTimeMillis()
                var interimSent = false

                while (isActive && isModelListening) {
                    val read = try { audioRecord?.read(byteBuf, 0, byteBuf.size) ?: 0 } catch (e: Exception) { 0 }
                    if (read > 0) {
                        // compute RMS for silence detection
                        var sum = 0L
                        val bb = ByteBuffer.wrap(byteBuf, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                        while (bb.remaining() >= 2) {
                            val sample = bb.short.toInt()
                            sum += sample * sample
                        }
                        val rms = if (read > 0) Math.sqrt(sum.toDouble() / (read / 2)) else 0.0

                        // append chunk
                        for (i in 0 until read) pcmAccumulator.add(byteBuf[i])

                        if (rms > SILENCE_THRESHOLD) {
                            lastSpeechTs = System.currentTimeMillis()
                            if (!interimSent && pcmAccumulator.size > SAMPLE_RATE * 2 / 5) {
                                interimSent = true
                                val interimBytes = pcmAccumulator.toByteArray()
                                launchModelTranscribe(interimBytes) { interimText, _ ->
                                    if (interimText != null) {
                                        scope.launch(Dispatchers.Main) { onPartial(interimText) }
                                    }
                                }
                            }
                        }

                        val now = System.currentTimeMillis()
                        // finalize when silence or max length
                        if ((now - lastSpeechTs) > SILENCE_TIMEOUT_MS || (now - startTs) > MAX_RECORD_MS) {
                            val finalBytes = pcmAccumulator.toByteArray()
                            pcmAccumulator.clear()
                            interimSent = false

                            val (finalText, modelErr) = callModelAsr(finalBytes)
                            if (finalText != null) {
                                withContext(Dispatchers.Main) { onFinal(finalText) }
                                // Reset timers for next utterance (continue listening if continuous)
                                startTs = System.currentTimeMillis()
                                lastSpeechTs = startTs
                                if (userRequestedStop) {
                                    Log.d(TAG, "User requested stop; terminating model ASR loop.")
                                    break
                                }
                                // continue listening for next utterance
                            } else {
                                withContext(Dispatchers.Main) { onModelError(modelErr ?: "Model ASR failure") }
                                isModelListening = false
                                break
                            }
                        }
                    } else {
                        delay(50)
                    }
                }
            } catch (ce: CancellationException) {
                Log.d(TAG, "Model ASR job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Model ASR exception: ${e.message}", e)
                withContext(Dispatchers.Main) { onModelError(e.message ?: "unknown model ASR error") }
            } finally {
                try { audioRecord?.stop() } catch (_: Exception) {}
                try { audioRecord?.release() } catch (_: Exception) {}
                audioRecord = null
                isModelListening = false
                withContext(Dispatchers.Main) { onStopped() }
            }
        }
    }

    private fun launchModelTranscribe(audioPcmBytes: ByteArray, cb: (String?, String?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val (text, err) = callModelAsr(audioPcmBytes)
                cb(text, err)
            } catch (e: Exception) {
                cb(null, e.message)
            }
        }
    }

    private fun callModelAsr(pcm16leBytes: ByteArray): Pair<String?, String?> {
        return try {
            val kclass = RunAnywhere::class.java
            val method = try { kclass.getMethod("transcribeAudio", ByteArray::class.java, Int::class.javaPrimitiveType) } catch (nsme: NoSuchMethodException) { null }
            if (method != null) {
                val result = method.invoke(null, pcm16leBytes, SAMPLE_RATE)
                if (result is String) Pair(result, null) else Pair(null, "RunAnywhere.transcribeAudio returned unexpected type")
            } else {
                Log.w(TAG, "RunAnywhere.transcribeAudio not found via reflection")
                Pair(null, "Model ASR not available in SDK")
            }
        } catch (inv: Exception) {
            Log.e(TAG, "Error invoking model ASR via reflection: ${inv.message}", inv)
            Pair(null, "Model ASR invocation failed: ${inv.message}")
        }
    }

    // cleanup
    fun onDestroy() {
        shutdown()
    }
}
