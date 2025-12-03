package com.runanywhere.startup_hackathon20

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.models.ModelInfo
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.listAvailableModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// Simple Message Data Class for the Chat Interface
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: String? = null
)

// Data class for Cash Flow Prediction results
data class CashFlowPrediction(
    val totalIncome: Double,
    val totalExpenses: Double,
    val netCashFlow: Double,
    val predictedBalance: Double,
    val topCategories: Map<String, Double>,
    val riskyDays: List<String>,
    val recommendation: String
)

// ViewModel
class ChatViewModel : ViewModel() {

    // --- Chat State ---
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // --- Model Management State ---
    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _currentModelId = MutableStateFlow<String?>(null)
    val currentModelId: StateFlow<String?> = _currentModelId

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    val downloadingModelId: StateFlow<String?> = _downloadingModelId

    // --- Status Flows ---
    private val _modelStatus = MutableStateFlow("Initializing...")
    val modelStatus: StateFlow<String> = _modelStatus

    private val _smsImportStatus = MutableStateFlow("Idle")
    val smsImportStatus: StateFlow<String> = _smsImportStatus

    private val _batchProcessingStatus = MutableStateFlow("Idle")
    val batchProcessingStatus: StateFlow<String> = _batchProcessingStatus

    private val _predictionStatus = MutableStateFlow("Idle")
    val predictionStatus: StateFlow<String> = _predictionStatus

    // alias kept for backward compatibility
    val statusMessage: StateFlow<String> = _modelStatus

    // --- SMS Data State ---
    private val _smsList = MutableStateFlow<List<RawSms>>(emptyList())
    val smsList: StateFlow<List<RawSms>> = _smsList

    private val _isImportingSms = MutableStateFlow(false)
    val isImportingSms: StateFlow<Boolean> = _isImportingSms

    private val _parsedJsonBySms = MutableStateFlow<Map<String, String>>(emptyMap())
    val parsedJsonBySms: StateFlow<Map<String, String>> = _parsedJsonBySms

    private val _scamResultBySms = MutableStateFlow<Map<String, String>>(emptyMap())
    val scamResultBySms: StateFlow<Map<String, String>> = _scamResultBySms

    private val _processingProgress = MutableStateFlow(0)
    val processingProgress: StateFlow<Int> = _processingProgress

    // --- Cash Flow Prediction State ---
    private val _cashFlowPrediction = MutableStateFlow<CashFlowPrediction?>(null)
    val cashFlowPrediction: StateFlow<CashFlowPrediction?> = _cashFlowPrediction

    private val _isPredicting = MutableStateFlow(false)
    val isPredicting: StateFlow<Boolean> = _isPredicting

    private val _predictionProgress = MutableStateFlow(0f)
    val predictionProgress: StateFlow<Float> = _predictionProgress

    private val cashFlowPredictor = CashFlowPredictor()

    // --- Voice Manager State ---
    private var voiceManager: VoiceManager? = null
    private var appContext: Context? = null
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    init {
        loadAvailableModels()
    }

    fun initializeVoice(context: Context) {
        // Store application context for later restart of voice coach after TTS
        appContext = context.applicationContext
        if (voiceManager == null) {
            voiceManager = VoiceManager(appContext!!)
        }
    }

    // ============================================================================================
    // SECTION 1: MODEL MANAGEMENT
    // ============================================================================================

    private fun loadAvailableModels() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val models = withContext(Dispatchers.IO) { listAvailableModels() }
                _availableModels.value = models
                _modelStatus.value =
                    if (models.isEmpty()) "No models found. Check connection." else "Ready - Please download and load a model"
            } catch (e: Exception) {
                _modelStatus.value = "Error loading models: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun downloadModel(modelId: String) {
        if (_downloadingModelId.value != null) {
            _modelStatus.value = "Another download is already in progress."
            return
        }
        viewModelScope.launch {
            try {
                _downloadingModelId.value = modelId
                _downloadProgress.value = 0f
                _modelStatus.value = "Downloading model..."
                RunAnywhere.downloadModel(modelId).collect { progress ->
                    _downloadProgress.value = progress
                    _modelStatus.value = "Downloading: ${(progress * 100).toInt()}%"
                }
                _modelStatus.value = "Download complete!"
                refreshModels()
            } catch (e: Exception) {
                _modelStatus.value = "Download failed: ${e.message}"
            } finally {
                _downloadProgress.value = null
                _downloadingModelId.value = null
            }
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _modelStatus.value = "Loading model..."
            try {
                val success = withContext(Dispatchers.IO) { RunAnywhere.loadModel(modelId) }
                if (success) {
                    _currentModelId.value = modelId
                    _modelStatus.value = "Model loaded successfully!"
                } else {
                    _currentModelId.value = null
                    _modelStatus.value = "Failed to load model."
                }
            } catch (e: Exception) {
                _modelStatus.value = "Error loading model: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshModels() = loadAvailableModels()

    // ============================================================================================
    // SECTION 2: CHAT
    // ============================================================================================

    /**
     * Send a user message to the model.
     * If speakResponse==true, the assistant's final reply will be spoken via VoiceManager.
     */
    /**
     * Send a text prompt to the model and stream back the assistant reply.
     * If speakResponse==true, the final assistant reply is spoken by VoiceManager.
     */
    fun sendMessage(text: String, speakResponse: Boolean = false) {
        if (_currentModelId.value == null) {
            _messages.update { it + ChatMessage("Error: No AI model is loaded.", false) }
            return
        }
        // add user message to UI
        _messages.update { it + ChatMessage(text, true, getCurrentTimestamp()) }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                var fullResponse = ""
                // stream tokens (30s timeout)
                withTimeoutOrNull(30_000L) {
                    RunAnywhere.generateStream(text).collect { token ->
                        fullResponse += token
                        // update partial assistant response in-place
                        _messages.update { current ->
                            val m = current.toMutableList()
                            // if last message is assistant, replace it; else append new assistant message
                            if (m.lastOrNull()?.isUser == false) {
                                m[m.lastIndex] = m.last().copy(text = fullResponse)
                            } else {
                                m.add(ChatMessage(fullResponse, false, getCurrentTimestamp()))
                            }
                            m.toList()
                        }
                    }
                }

                // At this point fullResponse contains the final assistant text (or empty if timed out).
                // If user requested TTS for the response (voice coach), speak it.
                if (speakResponse) {
                    val responseTrim = fullResponse.trim()
                    if (responseTrim.isNotEmpty()) {
                        // ensure voice manager exists
                        initializeVoice(appContext ?: return@launch)

                        // Stop model ASR to avoid feedback
                        voiceManager?.stopListening()
                        _isSpeaking.value = true

                        // Speak and after completion, restart listening if the voice coach is still expected.
                        voiceManager?.speak(responseTrim) {
                            // called on main thread by VoiceManager
                            _isSpeaking.value = false

                            // Restart voice listening only if voice coach was active before and appContext is present
                            // We avoid infinite loop by small delay and re-checking _isVoiceListening flag.
                            viewModelScope.launch {
                                delay(300L)
                                // If voice coach mode is still desired and permission ok, restart.
                                if (_isVoiceListening.value) {
                                    appContext?.let { ctx ->
                                        // startVoiceCoach will check permission and set states; it will attempt to start ASR again
                                        startVoiceCoach(ctx)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _messages.update { it + ChatMessage("Error: ${e.message}", false) }
            } finally {
                _isLoading.value = false
            }
        }
    }


    // ============================================================================================
    // SECTION 3: SMS PROCESSING (ROBUST & PARALLEL)
    // ============================================================================================

    fun importSms(context: Context) {
        viewModelScope.launch {
            _isImportingSms.value = true
            _smsImportStatus.value = "Reading inbox..."
            try {
                val sms = withContext(Dispatchers.IO) { readSmsInbox(context, daysLookBack = 30) }
                _smsList.value = sms
                _smsImportStatus.value = "Imported ${sms.size} financial messages."
            } catch (e: Exception) {
                _smsImportStatus.value = "Error importing SMS: ${e.message}"
            } finally {
                _isImportingSms.value = false
            }
        }
    }

    // --- Robust batch processor ---
    fun processAllMessages() {
        val messagesToProcess = _smsList.value
        if (messagesToProcess.isEmpty()) {
            _batchProcessingStatus.value = "No messages to process."
            return
        }

        viewModelScope.launch {
            _batchProcessingStatus.value = "Starting batch processing..."
            _processingProgress.value = 0
            val total = messagesToProcess.size
            var processedCount = 0

            // Pre-mark as checking
            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                messagesToProcess.forEach { sms ->
                    val cur = m[sms.id]
                    if (cur == null || cur.equals("Not checked", ignoreCase = true)) {
                        m[sms.id] = "Checking..."
                    }
                }
                m.toMap()
            }

            try {
                val jobs = messagesToProcess.map { sms ->
                    async(Dispatchers.IO) {
                        try {
                            val parsedExists = _parsedJsonBySms.value.containsKey(sms.id)
                            if (!parsedExists || _parsedJsonBySms.value[sms.id]?.contains("error") == true) {
                                internalParseSms(sms.id, sms.body ?: "")
                            }

                            if (shouldCheckForScam(sms.body) || _scamResultBySms.value[sms.id].isNullOrBlank() ||
                                _scamResultBySms.value[sms.id] == "Checking...") {
                                internalDetectScam(sms.id, sms.body ?: "")
                            }

                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "processAllMessages item error for ${sms.id}: ${e.message}", e)
                            _scamResultBySms.update { current ->
                                val m = current.toMutableMap()
                                m[sms.id] = "error"
                                m.toMap()
                            }
                        } finally {
                            synchronized(this) {
                                processedCount++
                                _processingProgress.value = (processedCount * 100) / total
                                _batchProcessingStatus.value = "Processed $processedCount / $total messages..."
                            }
                        }
                    }
                }

                jobs.awaitAll()

                ensureAllScamStatusesResolved()

                _batchProcessingStatus.value = "Batch processing complete!"
            } catch (e: Exception) {
                Log.e("ChatViewModel", "processAllMessages failed: ${e.message}", e)
                _batchProcessingStatus.value = "Error during batch processing: ${e.message}"
            } finally {
                _processingProgress.value = 0
            }
        }
    }

    private fun ensureAllScamStatusesResolved() {
        _scamResultBySms.update { current ->
            val m = current.toMutableMap()
            var changed = false
            for (key in m.keys.toList()) {
                val v = m[key]
                if (v == null || v.equals("Not checked", ignoreCase = true) || v.equals("Checking...", ignoreCase = true)) {
                    m[key] = "uncertain"
                    changed = true
                }
            }
            _smsList.value.forEach { sms ->
                if (!m.containsKey(sms.id)) {
                    m[sms.id] = "uncertain"
                    changed = true
                }
            }
            if (changed) m.toMap() else current
        }
    }

    // ---------------------------------------------------------
    // Individual Message Actions (Called from UI)
    // ---------------------------------------------------------

    fun parseSms(smsId: String, smsBody: String) {
        viewModelScope.launch {
            _parsedJsonBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = "Parsing..."
                m.toMap()
            }
            internalParseSms(smsId, smsBody)
        }
    }

    fun detectScam(smsId: String, smsBody: String) {
        viewModelScope.launch {
            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = "Checking..."
                m.toMap()
            }
            internalDetectScam(smsId, smsBody)
        }
    }

    fun forceSaveParsedJson(smsId: String, newJson: String) {
        _parsedJsonBySms.update { current ->
            val m = current.toMutableMap()
            m[smsId] = newJson
            m.toMap()
        }
    }

    // ============================================================================================
    // SECTION 4: AI PARSING & SCAM (INTERNAL HELPERS)
    // ============================================================================================

    private suspend fun internalParseSms(smsId: String, smsBody: String) {
        if (smsBody.isBlank()) return

        val prompt = buildString {
            append(
                "You are a strict JSON extractor. Input: a single bank/payment SMS in English. " +
                        "Output: ONLY a single JSON object with keys: amount (number or null), currency ('INR'), merchant (string or null), type ('debit'|'credit'|'info'), date (YYYY-MM-DD or null), account_tail (string or null), balance (number or null). Use null for missing values.\n\n"
            )
            append("SMS: \"$smsBody\"\nJSON:")
        }

        try {
            val result = withTimeoutOrNull(30_000L) {
                var acc = ""
                RunAnywhere.generateStream(prompt).collect { token -> acc += token }
                acc
            } ?: ""

            val parsedJson = extractFirstJsonObject(result) ?: quickHeuristicJson(smsBody)

            _parsedJsonBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = parsedJson
                m.toMap()
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "internalParseSms error: ${e.message}", e)
            _parsedJsonBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = "{\"error\": \"${e.message ?: "parse failed"}\"}"
                m.toMap()
            }
        }
    }

    private suspend fun internalDetectScam(smsId: String, smsBody: String) {
        if (smsBody.isBlank()) {
            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = "uncertain"
                m.toMap()
            }
            return
        }

        if (_currentModelId.value == null) {
            val fallback = when {
                smsBody.contains("otp", true) || smsBody.contains("click", true) || smsBody.contains("http", true) -> "likely_scam"
                smsBody.contains("debited", true) || smsBody.contains("credited", true) -> "safe"
                else -> "uncertain"
            }
            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = fallback
                m.toMap()
            }
            return
        }

        try {
            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = "Checking..."
                m.toMap()
            }

            val prompt = """
            You are a financial scam detector. Output EXACTLY one word: safe, likely_scam, or uncertain.
            SMS: "$smsBody"
        """.trimIndent()

            val result = withTimeoutOrNull(10_000L) {
                var acc = ""
                RunAnywhere.generateStream(prompt).collect { acc += it }
                acc
            } ?: ""

            val cleaned = result.trim().lowercase(Locale.getDefault())

            val finalLabel = when {
                cleaned.contains("likely_scam") -> "likely_scam"
                cleaned.contains("safe") -> "safe"
                cleaned.contains("uncertain") -> "uncertain"
                else -> {
                    when {
                        smsBody.contains("otp", true) || smsBody.contains("click", true) || smsBody.contains("http", true) -> "likely_scam"
                        smsBody.contains("debited", true) || smsBody.contains("credited", true) -> "safe"
                        else -> "uncertain"
                    }
                }
            }

            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = finalLabel
                m.toMap()
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "internalDetectScam error for $smsId: ${e.message}", e)
            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = "error"
                m.toMap()
            }
        }
    }

    // Fast Regex fallback for when AI is unavailable / fails
    private suspend fun quickHeuristicJson(sms: String): String = withContext(Dispatchers.Default) {
        // Basic heuristics (similar to earlier implementation)
        val amountPattern = Regex("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
        val balPattern = Regex("(?i)(?:Avl Bal|Available balance|Bal(?:ance)?:)\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
        val datePattern = Regex("\\b(\\d{2}[/-]\\d{2}[/-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2})\\b")
        val acctPattern = Regex("(?i)(?:a/c|acct|account|ending|xx)\\s*[:#]?\\s*([0-9A-Za-z]+)")

        val amount = amountPattern.find(sms)?.groups?.get(1)?.value
        val balance = balPattern.find(sms)?.groups?.get(1)?.value
        val date = datePattern.find(sms)?.groups?.get(1)?.value
        val acct = acctPattern.find(sms)?.groups?.get(1)?.value

        fun cleanNum(s: String?): Number? {
            if (s == null) return null
            val cleaned = s.replace(",", "").replace("₹", "").replace("Rs", "", ignoreCase = true).trim()
            return try {
                if (cleaned.contains(".")) cleaned.toDouble() else cleaned.toInt()
            } catch (_: Exception) {
                null
            }
        }

        val amtNum = cleanNum(amount)
        val balNum = cleanNum(balance)

        val type = when {
            sms.contains("credit", true) || sms.contains("credited", true) -> "credit"
            sms.contains("debit", true) || sms.contains("debited", true) -> "debit"
            else -> "info"
        }

        var merchant: String? = null
        if (type == "debit" && sms.contains(" at ", true)) {
            merchant = sms.substringAfterLast(" at ").substringBefore(".").take(30).trim()
        } else if (type == "debit" && sms.contains(" to ", true)) {
            merchant = sms.substringAfterLast(" to ").substringBefore(".").take(30).trim()
        }

        val dateIso = date?.let { normalizeDateToIso(it) }

        return@withContext buildString {
            append("{")
            append("\"amount\":${amtNum ?: "null"},")
            append("\"currency\":\"INR\",")
            append("\"merchant\":${if (merchant != null) "\"${merchant.replace("\"", "\\\"")}\"" else "null"},")
            append("\"type\":\"$type\",")
            append("\"date\":${if (dateIso != null) "\"${dateIso}\"" else "null"},")
            append("\"account_tail\":${if (acct != null) "\"$acct\"" else "null"},")
            append("\"balance\":${balNum ?: "null"},")
            append("\"raw_text\":\"${sms.replace("\"", "\\\"").replace("\n", " ")}\"")
            append("}")
        }
    }

    private fun normalizeDateToIso(s: String): String? {
        try {
            val trimmed = s.trim()
            if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return trimmed
            val parts = trimmed.split('/', '-')
            if (parts.size == 3) {
                var d = parts[0]
                var m = parts[1]
                var y = parts[2]
                if (y.length == 2) y = "20$y"
                val dd = d.padStart(2, '0').toIntOrNull() ?: return null
                val mm = m.padStart(2, '0').toIntOrNull() ?: return null
                val yy = y.padStart(4, '0').toIntOrNull() ?: return null
                return String.format(Locale.getDefault(), "%04d-%02d-%02d", yy, mm, dd)
            }
        } catch (_: Exception) { }
        return null
    }

    // Helper to extract JSON object from text using brace matching
    private fun extractFirstJsonObject(text: String): String? {
        val startIndex = text.indexOf('{')
        if (startIndex == -1) return null
        var braceCount = 1
        for (i in (startIndex + 1) until text.length) {
            when (text[i]) {
                '{' -> braceCount++
                '}' -> braceCount--
            }
            if (braceCount == 0) {
                return text.substring(startIndex, i + 1)
            }
        }
        return null
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    private fun shouldCheckForScam(body: String?): Boolean {
        if (body == null) return false
        val lower = body.lowercase()
        return lower.contains("otp") || lower.contains("http") || lower.contains("click") || lower.contains("call") || lower.contains("urgent")
    }

    // Put these inside ChatViewModel

    // Helper wrapper: runs the model stream, collects tokens, and retries once with a stricter prompt if result is empty.
// Returns final string (may be empty).
    private suspend fun callModelWithRetries(userPrompt: String, maxWaitMs: Long = 45000L): String {
        // Primary attempt: normal prompt
        suspend fun runStream(prompt: String, timeoutMs: Long): String {
            var acc = ""
            try {
                withTimeoutOrNull(timeoutMs) {
                    RunAnywhere.generateStream(prompt).collect { token ->
                        acc += token
                    }
                }
            } catch (e: Exception) {
                // keep acc and return (outer code will handle empty)
            }
            return acc.trim()
        }

        // 1) Normal attempt
        val primary = runStream(userPrompt, maxWaitMs)

        if (primary.isNotBlank()) {
            // quick sanity: return as-is
            android.util.Log.d("ChatViewModel", "Model primary response length=${primary.length}")
            return primary
        }

        // 2) Retry with stricter prompt (deterministic hint + ask for short answer only)
        val strictPrompt = buildString {
            appendLine("STRICT OUTPUT: Answer concisely. Do NOT add any commentary or explanation.")
            appendLine("If you are giving a multi-sentence answer, keep it to the essential facts.")
            appendLine()
            appendLine("User prompt:")
            appendLine(userPrompt)
            appendLine()
            appendLine("REPLY:")
        }

        android.util.Log.w("ChatViewModel", "Primary model response empty — retrying with strict prompt")
        val retry = runStream(strictPrompt, maxWaitMs / 2) // shorter retry timeout
        if (retry.isNotBlank()) {
            android.util.Log.d("ChatViewModel", "Model retry response length=${retry.length}")
            return retry
        }

        // 3) final fallback - empty result but return empty string so callers can handle fallback heuristics
        android.util.Log.e("ChatViewModel", "Model returned empty on both attempts")
        return ""
    }

    // Robust sendMessage which streams partial tokens and retries/fallbacks when empty
    fun sendMessage(text: String) {
        if (_currentModelId.value == null) {
            _messages.update { it + ChatMessage("Error: No AI model is loaded.", false) }
            return
        }
        // push user message
        _messages.update { it + ChatMessage(text, true, getCurrentTimestamp()) }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Build your full prompt (system/role + user) if you use one. Small example:
                val prompt = buildString {
                    appendLine("You are a helpful, precise financial assistant. Be concise and factual.")
                    appendLine()
                    appendLine("User: $text")
                }

                // call wrapper that handles retries
                val finalResponse = callModelWithRetries(prompt, maxWaitMs = 45000L)

                if (finalResponse.isBlank()) {
                    // fallback behavior: show polite failure + suggest action
                    val fallback = "Sorry — I couldn't get a response from the model. Try again or load a different model."
                    _messages.update { it + ChatMessage(fallback, false, getCurrentTimestamp()) }
                } else {
                    // If the model returned content, update messages.
                    _messages.update { current ->
                        val m = current.toMutableList()
                        // If streaming partial assistant already exists, replace it; otherwise append
                        if (m.lastOrNull()?.isUser == false) {
                            m[m.lastIndex] = m.last().copy(text = finalResponse, timestamp = getCurrentTimestamp())
                        } else {
                            m.add(ChatMessage(finalResponse, false, getCurrentTimestamp()))
                        }
                        m.toList()
                    }
                }
            } catch (e: Exception) {
                _messages.update { it + ChatMessage("Error: ${e.message}", false, getCurrentTimestamp()) }
                android.util.Log.e("ChatViewModel", "sendMessage exception: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }




    // ============================================================================================
    // SECTION 5: CASH FLOW PREDICTION (ROBUST)
    // ============================================================================================

    fun predictCashFlow() {
        if (_isPredicting.value) return

        viewModelScope.launch {
            _isPredicting.value = true
            _cashFlowPrediction.value = null // Clear old prediction
            _predictionProgress.value = 0f
            _predictionStatus.value = "Starting prediction..."

            try {
                val validTransactions = _parsedJsonBySms.value.values.count { it.contains("amount") }
                if (validTransactions < 5) {
                    _predictionStatus.value = "Error: Not enough transaction data. Please parse more SMS."
                    delay(3000)
                    _predictionStatus.value = "Idle"
                    return@launch
                }

                val parsedSnapshot = _parsedJsonBySms.value
                val smsMap = _smsList.value.associateBy { it.id }

                val prediction = withContext(Dispatchers.Default) {
                    cashFlowPredictor.predictCashFlow(parsedSnapshot, smsMap) { status, prog ->
                        _predictionStatus.value = status
                        _predictionProgress.value = prog.coerceIn(0f, 1f)
                    }
                }

                _cashFlowPrediction.value = prediction
                _predictionStatus.value = "Prediction complete!"
                _predictionProgress.value = 1f
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Cash flow prediction failed", e)
                _predictionStatus.value = "Error: Prediction failed. ${e.message}"
                _predictionProgress.value = 0f
            } finally {
                delay(300)
                _isPredicting.value = false
            }
        }
    }

    // ============================================================================================
    // SECTION 6: VOICE & CLEANUP
    // ============================================================================================

    fun speakCashFlowSummary() {
        val prediction = _cashFlowPrediction.value ?: return
        voiceManager?.let { vm ->
            _isSpeaking.value = true
            val text = vm.generateVoiceSummary(prediction)
            vm.speak(text) { _isSpeaking.value = false }
        } ?: run {
            _modelStatus.value = "Voice manager not initialized"
        }
    }

    fun stopSpeaking() {
        try {
            voiceManager?.stop()
        } catch (e: Exception) {
            Log.w("ChatViewModel", "stopSpeaking error: ${e.message}")
        } finally {
            _isSpeaking.value = false
        }
    }

    // --- Voice Coach / ASR state ---
    private val _isVoiceListening = MutableStateFlow(false)
    val isVoiceListening: StateFlow<Boolean> = _isVoiceListening

    /**
     * Start voice coach (ASR) -> will call sendMessage(...) on final transcripts.
     * Caller must ensure RECORD_AUDIO permission is granted (we check it here).
     */
    fun startVoiceCoach(context: Context) {
        viewModelScope.launch {
            try {
                initializeVoice(context)

                // check permission (activity/compose layer should have requested it already)
                val perm = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                )
                if (perm != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    _modelStatus.value = "Voice permission required. Please grant RECORD_AUDIO."
                    return@launch
                }

                if (voiceManager == null) {
                    _modelStatus.value = "Voice manager not initialized"
                    return@launch
                }

                _modelStatus.value = "Starting voice coach..."
                _isVoiceListening.value = true

                // Call startListening — VoiceManager provides callbacks
                try {
                    voiceManager!!.startListening(
                        onPartial = { interim ->
                            _modelStatus.value = "Listening… $interim"
                        },

                        onFinal = { finalText ->
                            val trimmed = finalText.trim()
                            if (trimmed.isNotBlank()) {
                                // send and request speaking of assistant's reply
                                viewModelScope.launch {
                                    // ensure _isVoiceListening remains true so sendMessage can restart listening after TTS
                                    sendMessage(trimmed, speakResponse = true)
                                }
                            }
                        },


                        onError = { err ->
                            _modelStatus.value = "Voice error: $err"
                            // stop listening when error occurs
                            stopVoiceCoach()
                        },
                        onStopped = {
                            // ensure we update state on main thread
                            viewModelScope.launch {
                                _isVoiceListening.value = false
                                _modelStatus.value = "Voice stopped"
                            }
                        }
                    )
                } catch (e: SecurityException) {
                    _modelStatus.value = "Permission denied for audio: ${e.message}"
                    _isVoiceListening.value = false
                } catch (e: Exception) {
                    _modelStatus.value = "Failed to start voice coach: ${e.message}"
                    _isVoiceListening.value = false
                }

            } catch (se: SecurityException) {
                _modelStatus.value = "Permission denied for audio: ${se.message}"
                _isVoiceListening.value = false
            } catch (e: Exception) {
                _modelStatus.value = "Failed to start voice coach: ${e.message}"
                _isVoiceListening.value = false
            }
        }
    }

    /**
     * Stop the voice listening session cleanly.
     */
    fun stopVoiceCoach() {
        try {
            voiceManager?.stopListening()
        } catch (e: Exception) {
            Log.w("ChatViewModel", "stopVoiceCoach error: ${e.message}")
        } finally {
            _isVoiceListening.value = false
            _modelStatus.value = "Voice stopped"
        }
    }

    fun onClearedCleanup() {
        try {
            voiceManager?.shutdown()
        } catch (e: Exception) { /* ignore */ }
    }

    override fun onCleared() {
        super.onCleared()
        onClearedCleanup()
    }
}
