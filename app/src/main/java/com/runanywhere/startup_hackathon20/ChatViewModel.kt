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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.regex.Pattern
import java.text.SimpleDateFormat
import java.util.*

// Simple Message Data Class for the Chat Interface
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: String? = null
)


// Cash Flow Prediction data class
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

    // Per-screen status flows (avoid a single global status string)
    private val _modelStatus = MutableStateFlow("Initializing models...")
    val modelStatus: StateFlow<String> = _modelStatus

    // Expose a generic statusMessage for existing UI that expects it.
    // This is an alias to modelStatus so old screens continue to work.
    val statusMessage: StateFlow<String> = _modelStatus

    private val _smsImportStatus = MutableStateFlow("Idle")
    val smsImportStatus: StateFlow<String> = _smsImportStatus

    private val _batchProcessingStatus = MutableStateFlow("Idle")
    val batchProcessingStatus: StateFlow<String> = _batchProcessingStatus

    private val _predictionStatus = MutableStateFlow("Idle")
    val predictionStatus: StateFlow<String> = _predictionStatus

    // --- SMS Data State ---
    private val _smsList = MutableStateFlow<List<RawSms>>(emptyList())
    val smsList: StateFlow<List<RawSms>> = _smsList

    private val _isImportingSms = MutableStateFlow(false)
    val isImportingSms: StateFlow<Boolean> = _isImportingSms

    // Stores the RAW JSON string extracted from the SMS
    private val _parsedJsonBySms = MutableStateFlow<Map<String, String>>(emptyMap())
    val parsedJsonBySms: StateFlow<Map<String, String>> = _parsedJsonBySms

    // Stores Scam Status: "safe", "likely_scam", "uncertain"
    private val _scamResultBySms = MutableStateFlow<Map<String, String>>(emptyMap())
    val scamResultBySms: StateFlow<Map<String, String>> = _scamResultBySms

    // Progress for Batch Processing (0 to 100)
    private val _processingProgress = MutableStateFlow(0)
    val processingProgress: StateFlow<Int> = _processingProgress

    // --- Cash Flow Prediction State ---
    private val _cashFlowPrediction = MutableStateFlow<CashFlowPrediction?>(null)
    val cashFlowPrediction: StateFlow<CashFlowPrediction?> = _cashFlowPrediction

    private val _isPredicting = MutableStateFlow(false)
    val isPredicting: StateFlow<Boolean> = _isPredicting

    private val cashFlowPredictor = CashFlowPredictor()

    // --- Voice Manager State ---
    private var voiceManager: VoiceManager? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    init {
        loadAvailableModels()
    }

    /**
     * Initialize voice manager (call from UI with context)
     */
    fun initializeVoice(context: Context) {
        if (voiceManager == null) {
            // FIX: Use Application Context to prevent Memory Leaks
            voiceManager = VoiceManager(context.applicationContext)
            // Also try to load persisted data when context is available
            loadParsedDataFromDisk(context.applicationContext)
        }
    }

    // ============================================================================================
    // SECTION 1: MODEL MANAGEMENT (RunAnywhere SDK)
    // ============================================================================================

    private fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                val models = withContext(Dispatchers.IO) {
                    listAvailableModels()
                }
                _availableModels.value = models
                _modelStatus.value = "Ready - Please download and load a model"
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading models", e)
                _modelStatus.value = "Error loading models: ${e.message}"
            }
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                _modelStatus.value = "Downloading model..."
                RunAnywhere.downloadModel(modelId).collect { progress ->
                    _downloadProgress.value = progress
                    _modelStatus.value = "Downloading: ${(progress * 100).toInt()}%"
                }
                _downloadProgress.value = null
                _modelStatus.value = "Download complete! Please load the model."
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error downloading model", e)
                _modelStatus.value = "Download failed: ${e.message}"
                _downloadProgress.value = null
            }
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            try {
                _modelStatus.value = "Loading model..."
                val startTime = System.currentTimeMillis()
                val success = withContext(Dispatchers.IO) {
                    RunAnywhere.loadModel(modelId)
                }
                val duration = System.currentTimeMillis() - startTime

                if (success) {
                    _currentModelId.value = modelId
                    _modelStatus.value = "Model loaded in ${duration / 1000.0}s! Ready."
                } else {
                    _modelStatus.value = "Failed to load model"
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading model", e)
                _modelStatus.value = "Error loading model: ${e.message}"
            }
        }
    }

    fun refreshModels() {
        loadAvailableModels()
    }

    // ============================================================================================
    // SECTION 2: CHAT INTERFACE
    // ============================================================================================

    fun sendMessage(text: String) {
        // guard: nothing to send
        if (text.isBlank()) return

        // 1) require model loaded
        val modelId = _currentModelId.value
        if (modelId == null) {
            _modelStatus.value = "Please load a model first"
            _messages.update { current ->
                current + ChatMessage(
                    text = "Error: no model loaded. Open Model Management to load one.",
                    isUser = false,
                    timestamp = getCurrentTimestamp()
                )
            }
            return
        }

        // Append user message immediately with timestamp
        _messages.update { current ->
            current + ChatMessage(
                text = text,
                isUser = true,
                timestamp = getCurrentTimestamp()
            )
        }

        viewModelScope.launch {
            _isLoading.value = true
            var assistantResponse = ""

            try {
                _modelStatus.value = "Generating..."

                // Try streaming tokens with timeout (30s)
                val streamed = withTimeoutOrNull(30_000L) {
                    var acc = ""
                    RunAnywhere.generateStream(text).collect { token ->
                        acc += token
                        // update UI incrementally
                        _messages.update { current ->
                            val newList = current.toMutableList()
                            if (newList.lastOrNull()?.isUser == false) {
                                // Update last assistant message
                                newList[newList.lastIndex] = newList.last().copy(text = acc)
                            } else {
                                // Add new assistant message
                                newList.add(ChatMessage(
                                    text = acc,
                                    isUser = false,
                                    timestamp = getCurrentTimestamp()
                                ))
                            }
                            newList
                        }
                    }
                    acc
                }

                if (!streamed.isNullOrBlank()) {
                    assistantResponse = streamed.trim()
                    _modelStatus.value = "Generation complete"
                } else {
                    // fallback: try non-streaming generate call or final attempt
                    _modelStatus.value = "No streamed tokens received — trying fallback..."
                    try {
                        var fallback = ""
                        withTimeoutOrNull(20_000L) {
                            RunAnywhere.generateStream(text).collect { t -> fallback += t }
                        }
                        if (!fallback.isNullOrBlank()) {
                            assistantResponse = fallback.trim()
                            _modelStatus.value = "Generation (fallback) complete"
                        } else {
                            assistantResponse = "Error: model produced no output."
                            _modelStatus.value = "Model produced no output"
                        }
                    } catch (e: Exception) {
                        assistantResponse = "Error: generation fallback failed: ${e.message}"
                        _modelStatus.value = "Generation failed"
                        Log.e("ChatViewModel", "Fallback generation error", e)
                    }
                }

                // Ensure UI contains the final assistant response
                if (assistantResponse.isNotEmpty()) {
                    _messages.update { current ->
                        val newList = current.toMutableList()
                        if (newList.lastOrNull()?.isUser == false) {
                            newList[newList.lastIndex] = newList.last().copy(text = assistantResponse)
                        } else {
                            newList.add(ChatMessage(
                                text = assistantResponse,
                                isUser = false,
                                timestamp = getCurrentTimestamp()
                            ))
                        }
                        newList
                    }
                }

            } catch (e: Throwable) {
                // Log and surface safe error
                Log.e("ChatViewModel", "sendMessage error: ${e.message}", e)
                _messages.update { current ->
                    current + ChatMessage(
                        text = "Error: ${e.message ?: "Unknown error during generation"}",
                        isUser = false,
                        timestamp = getCurrentTimestamp()
                    )
                }
                _modelStatus.value = "Generation failed: ${e.message ?: "unknown"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    // ============================================================================================
    // SECTION 3: SMS IMPORT & BATCH PROCESSING
    // ============================================================================================

    fun importSms(context: Context) {
        viewModelScope.launch {
            _isImportingSms.value = true
            _smsImportStatus.value = "Reading inbox..."

            val list = withContext(Dispatchers.IO) {
                try {
                    // Look back 365 days for better prediction data
                    readSmsInbox(context, limit = 3000, daysLookBack = 365)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error reading SMS", e)
                    emptyList()
                }
            }

            _smsList.value = list
            _smsImportStatus.value = "Imported ${list.size} financial messages."
            _isImportingSms.value = false
        }
    }

    fun processAllMessages() {
        val allMessages = _smsList.value
        if (allMessages.isEmpty()) {
            _batchProcessingStatus.value = "No messages to process."
            return
        }

        viewModelScope.launch {
            _batchProcessingStatus.value = "Batch processing ${allMessages.size} messages..."
            val total = allMessages.size
            _processingProgress.value = 0
            var processedCount = 0

            // Run batch processing on IO with parallel chunks
            withContext(Dispatchers.IO) {
                // Chunk size 20 to balance speed and resource usage
                allMessages.chunked(20).forEach { chunk ->
                    chunk.map { sms ->
                        async {
                            // 1. Parse if not present
                            if (!_parsedJsonBySms.value.containsKey(sms.id)) {
                                internalParseSms(sms.id, sms.body ?: "")
                            }
                            // 2. Check Scam (Heuristic first)
                            if (shouldCheckForScam(sms.body)) {
                                internalDetectScam(sms.id, sms.body ?: "")
                            }
                        }
                    }.forEach { it.await() } // Wait for chunk

                    processedCount += chunk.size
                    _processingProgress.value = (processedCount * 100) / total
                    _batchProcessingStatus.value = "Processed $processedCount / $total messages..."
                }
            }

            // Save progress to disk implicitly via predict call or manual
            predictCashFlow()
            _batchProcessingStatus.value = "Batch Processing Complete."
            _processingProgress.value = 0
        }
    }

    private fun shouldCheckForScam(body: String?): Boolean {
        if (body == null) return false
        val lower = body.lowercase()
        return lower.contains("otp") || lower.contains("http") || lower.contains("click") ||
                lower.contains("call") || lower.contains("kyc") || lower.contains("urgent") ||
                lower.contains("verify") || lower.contains("password") || lower.contains("login")
    }

    // ============================================================================================
    // SECTION 4: AI PARSING & STRUCTURED OUTPUT
    // ============================================================================================

    private val extractionPromptTemplate = """
        You are a strict JSON extractor. Input: a single bank/payment SMS in English. Output: ONLY a single JSON object between BEGIN_JSON and END_JSON tags. The JSON must have keys:
        amount (number or null), currency ("INR"), merchant (string or null), type ("debit"|"credit"|"info"), date (YYYY-MM-DD or null), account_tail (string or null), balance (number or null), raw_text (original message).

        Return valid JSON ONLY. NOTHING else. Examples follow.
        Example 1:
        SMS: "HDFC Bank: Debited INR 1,250.00 at AMAZON PAY on 2025-11-26. Avl Bal: INR 5,000."
        JSON:
        BEGIN_JSON
        {"amount":1250,"currency":"INR","merchant":"AMAZON PAY","type":"debit","date":"2025-11-26","account_tail":null,"balance":5000,"raw_text":"HDFC Bank: Debited INR 1,250.00 at AMAZON PAY on 2025-11-26. Avl Bal: INR 5,000."}
        END_JSON

        Example 2:
        SMS: "SBI: Credited Rs. 10,000.00 via NEFT. Ref 12345."
        JSON:
        BEGIN_JSON
        {"amount":10000,"currency":"INR","merchant":null,"type":"credit","date":null,"account_tail":null,"balance":null,"raw_text":"SBI: Credited Rs. 10,000.00 via NEFT. Ref 12345."}
        END_JSON

        Now parse this SMS (return ONLY one JSON between BEGIN_JSON and END_JSON):
    """.trimIndent()

    fun parseSms(smsId: String, smsBody: String) {
        viewModelScope.launch {
            internalParseSms(smsId, smsBody)
        }
    }

    private suspend fun internalParseSms(smsId: String, smsBody: String) {
        // 1. Try AI Model if loaded
        if (_currentModelId.value != null) {
            try {
                val prompt = buildString {
                    append(extractionPromptTemplate)
                    append("\n\nSMS: \"${smsBody.replace("\"", "\\\"")}\"\n")
                    append("JSON:\n")
                    append("BEGIN_JSON\n")
                }

                var streamed = ""
                // 30s timeout is safer for batch processing
                val result = withTimeoutOrNull(30000L) {
                    RunAnywhere.generateStream(prompt).collect { token ->
                        streamed += token
                    }
                    streamed
                } ?: ""

                val finalText = prompt + streamed
                val beginIdx = finalText.indexOf("BEGIN_JSON")
                val endIdx = finalText.indexOf("END_JSON", beginIdx)

                val parsedResult = if (beginIdx >= 0 && endIdx > beginIdx) {
                    finalText.substring(beginIdx + "BEGIN_JSON".length, endIdx).trim()
                } else {
                    extractFirstJsonObject(streamed) ?: quickHeuristicJson(smsBody)
                }

                // Update parsed map safely (produce new map snapshot)
                _parsedJsonBySms.update { current ->
                    current + (smsId to parsedResult)
                }
                return

            } catch (e: Exception) {
                Log.e("ChatViewModel", "AI Parse failed, using heuristic: ${e.message}")
            }
        }

        // 2. Fallback to Heuristic (Regex)
        val heuristic = quickHeuristicJson(smsBody)
        _parsedJsonBySms.update { current ->
            current + (smsId to heuristic)
        }
    }

    private val scamPromptTemplate = """
        You are a scam detector. Input: a financial SMS text. Output: return exactly one word: safe, likely_scam, or uncertain. 
        Use "likely_scam" if the message requests OTP, links, asks to call a number for payments, or has suspicious phrasing.
        Examples:
        "Your OTP is 1234" -> likely_scam
        "HDFC: Debited Rs 1000 at Amazon" -> safe
        "URGENT: Your KYC is expired. Click here" -> likely_scam
        Now classify:
    """.trimIndent()

    fun detectScam(smsId: String, smsBody: String) {
        viewModelScope.launch { internalDetectScam(smsId, smsBody) }
    }

    private suspend fun internalDetectScam(smsId: String, smsBody: String) {
        if (_currentModelId.value == null) {
            // Simple keyword fallback
            val lower = smsBody.lowercase()
            val fallback = when {
                lower.contains("otp") || lower.contains("link") || lower.contains("kyc") ||
                        lower.contains("urgent") || lower.contains("verify now") -> "likely_scam"
                lower.contains("debited") || lower.contains("credited") ||
                        lower.contains("balance") || lower.contains("transaction") -> "safe"
                else -> "uncertain"
            }
            _scamResultBySms.update { current ->
                current + (smsId to fallback)
            }
            return
        }

        // Mark as checking so UI can show progress per-sms if desired
        _scamResultBySms.update { current ->
            current + (smsId to "Checking...")
        }

        try {
            val prompt = "$scamPromptTemplate\n\nSMS: \"${smsBody.replace("\"", "\\\"")}\"\nAnswer:"
            var label = ""
            withTimeoutOrNull(10000L) {
                RunAnywhere.generateStream(prompt).collect { token ->
                    label += token
                }
            }
            val cleanLabel = label.trim().lowercase().replace(".", "")
            val finalLabel = when {
                cleanLabel.contains("likely_scam") -> "likely_scam"
                cleanLabel.contains("safe") -> "safe"
                else -> "uncertain"
            }
            _scamResultBySms.update { current ->
                current + (smsId to finalLabel)
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error detecting scam", e)
            _scamResultBySms.update { current ->
                current + (smsId to "error")
            }
        }
    }

    fun forceSaveParsedJson(smsId: String, json: String) {
        _parsedJsonBySms.update { current ->
            current + (smsId to json)
        }
    }

    // ============================================================================================
    // SECTION 5: CASH FLOW
    // ============================================================================================

    fun predictCashFlow() {
        if (_isPredicting.value) return

        viewModelScope.launch {
            _isPredicting.value = true
            _predictionStatus.value = "Predicting cash flow..."
            try {
                val smsMap = _smsList.value.associateBy { it.id }
                val prediction = cashFlowPredictor.predictCashFlow(_parsedJsonBySms.value, smsMap)
                _cashFlowPrediction.value = prediction
                _predictionStatus.value = "Prediction complete!"

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error predicting cash flow", e)
                _predictionStatus.value = "Prediction failed: ${e.message}"
            }
            _isPredicting.value = false
        }
    }

    // ============================================================================================
    // SECTION 6: VOICE
    // ============================================================================================

    fun speakCashFlowSummary() {
        val prediction = _cashFlowPrediction.value ?: return
        voiceManager?.let { vm ->
            _isSpeaking.value = true
            // Use the generator to create the string, then speak it
            val text = vm.generateVoiceSummary(prediction)
            vm.speak(text) {
                _isSpeaking.value = false
            }
        } ?: run {
            _modelStatus.value = "Voice manager not initialized"
        }
    }

    fun speakTransactionStats() {
        val totalDebits = _smsList.value.sumOf { sms ->
            _parsedJsonBySms.value[sms.id]?.let { json ->
                try {
                    val obj = JSONObject(json)
                    val type = obj.optString("type")
                    if (type == "debit") obj.optDouble("amount", 0.0) else 0.0
                } catch (_: Exception) { 0.0 }
            } ?: 0.0
        }
        val totalCredits = _smsList.value.sumOf { sms ->
            _parsedJsonBySms.value[sms.id]?.let { json ->
                try {
                    val obj = JSONObject(json)
                    val type = obj.optString("type")
                    if (type == "credit") obj.optDouble("amount", 0.0) else 0.0
                } catch (_: Exception) { 0.0 }
            } ?: 0.0
        }

        voiceManager?.let { vm ->
            _isSpeaking.value = true
            val summary = vm.generateTransactionSummary(_smsList.value.size, totalDebits, totalCredits)
            vm.speak(summary) {
                _isSpeaking.value = false
            }
        } ?: run {
            _modelStatus.value = "Voice manager not initialized"
        }
    }

    fun speakScamResults() {
        val scamResults = _scamResultBySms.value
        val scamCount = scamResults.values.count { it.contains("likely_scam", ignoreCase = true) }

        voiceManager?.let { vm ->
            _isSpeaking.value = true
            val alert = vm.generateScamAlert(scamCount)
            vm.speak(alert) {
                _isSpeaking.value = false
            }
        } ?: run {
            _modelStatus.value = "Voice manager not initialized"
        }
    }

    fun stopSpeaking() {
        voiceManager?.stop()
        _isSpeaking.value = false
    }

    // ============================================================================================
    // SECTION 7: PERSISTENCE & UTILITIES
    // ============================================================================================

    // Load data from internal storage to persist analysis across app restarts
    private fun loadParsedDataFromDisk(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = context.getFileStreamPath("parsed_sms_cache.json")
                if (file.exists()) {
                    val jsonString = context.openFileInput("parsed_sms_cache.json").bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(jsonString)
                    val map = mutableMapOf<String, String>()
                    val keys = jsonObject.keys()
                    while(keys.hasNext()) {
                        val key = keys.next()
                        map[key] = jsonObject.getString(key)
                    }
                    _parsedJsonBySms.value = map
                    Log.d("ChatViewModel", "Loaded ${map.size} parsed items from disk.")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to load cache: ${e.message}")
            }
        }
    }

    // Helper to find JSON block in AI text
    private fun extractFirstJsonObject(text: String): String? {
        var depth = 0
        var start = -1
        for (i in text.indices) {
            when (text[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start != -1) {
                        return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    // Fast Regex fallback for when AI is loading or fails
    private suspend fun quickHeuristicJson(sms: String): String = withContext(Dispatchers.Default) {
        val amountPattern = Pattern.compile("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
        val balPattern = Pattern.compile("(?i)(?:Avl Bal|Available balance|Bal(?:ance)?:)\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
        val datePattern = Pattern.compile("\\b(\\d{2}[/-]\\d{2}[/-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2})\\b")
        val acctPattern = Pattern.compile("(?i)(?:a/c|acct|account|ending|xx)\\s*[:#]?\\s*([0-9A-Za-z]+)")

        val amount = amountPattern.matcher(sms).let { if (it.find()) it.group(1) else null }
        val balance = balPattern.matcher(sms).let { if (it.find()) it.group(1) else null }
        val date = datePattern.matcher(sms).let { if (it.find()) it.group(1) else null }
        val acct = acctPattern.matcher(sms).let { if (it.find()) it.group(1) else null }

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
        val type = if (sms.contains("credit", true) || sms.contains("credited", true)) "credit"
        else if (sms.contains("debit", true) || sms.contains("debited", true)) "debit"
        else "info"

        // Attempt simple merchant extraction
        var merchant: String? = null
        if (type == "debit" && sms.contains(" at ", true)) {
            merchant = sms.substringAfterLast(" at ").substringBefore(".").take(25).trim()
        } else if (type == "debit" && sms.contains(" to ", true)) {
            merchant = sms.substringAfterLast(" to ").substringBefore(".").take(25).trim()
        }

        return@withContext buildString {
            append("{")
            append("\"amount\":${amtNum ?: "null"},")
            append("\"currency\":\"INR\",")
            append("\"merchant\":${if (merchant != null) "\"${merchant.replace("\"", "\\\"")}\"" else "null"},")
            append("\"type\":\"$type\",")
            append("\"date\":${if (date != null) "\"${normalizeDate(date)}\"" else "null"},")
            append("\"account_tail\":${if (acct != null) "\"$acct\"" else "null"},")
            append("\"balance\":${balNum ?: "null"},")
            append("\"raw_text\":\"${sms.replace("\"", "\\\"").replace("\n", " ")}\"")
            append("}")
        }
    }

    private fun normalizeDate(s: String): String {
        if (s.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return s
        val parts = s.split('/', '-')
        return try {
            if (parts.size == 3) {
                var d = parts[0]
                val m = parts[1]
                var y = parts[2]
                if (y.length == 2) y = "20$y"
                // Swap if likely MM/DD/YYYY based on day > 12? Assuming DD/MM for India
                String.format("%04d-%02d-%02d", y.toInt(), m.toInt(), d.toInt())
            } else s
        } catch (_: Exception) { s }
    }

    // Cleanup resources
    override fun onCleared() {
        super.onCleared()
        voiceManager?.shutdown()
    }
}