package com.runanywhere.startup_hackathon20

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.models.ModelInfo
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.listAvailableModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.regex.Pattern

// Simple Message Data Class for the Chat Interface
data class ChatMessage(
    val text: String,
    val isUser: Boolean
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

    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage

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
    @Suppress("unused")
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
            voiceManager = VoiceManager(context)
        }
    }

    // ============================================================================================
    // SECTION 1: MODEL MANAGEMENT (RunAnywhere SDK)
    // ============================================================================================

    private fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                val models = listAvailableModels()
                _availableModels.value = models
                _statusMessage.value = "Ready - Please download and load a model"
            } catch (e: Exception) {
                _statusMessage.value = "Error loading models: ${e.message}"
            }
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Downloading model..."
                RunAnywhere.downloadModel(modelId).collect { progress ->
                    _downloadProgress.value = progress
                    _statusMessage.value = "Downloading: ${(progress * 100).toInt()}%"
                }
                _downloadProgress.value = null
                _statusMessage.value = "Download complete! Please load the model."
            } catch (e: Exception) {
                _statusMessage.value = "Download failed: ${e.message}"
                _downloadProgress.value = null
            }
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Loading model..."
                val startTime = System.currentTimeMillis()
                val success = RunAnywhere.loadModel(modelId)
                val duration = System.currentTimeMillis() - startTime

                if (success) {
                    _currentModelId.value = modelId
                    _statusMessage.value = "Model loaded in ${duration / 1000.0}s! Ready."
                } else {
                    _statusMessage.value = "Failed to load model"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error loading model: ${e.message}"
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
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            return
        }

        _messages.value += ChatMessage(text, isUser = true)

        viewModelScope.launch {
            _isLoading.value = true
            try {
                var assistantResponse = ""
                RunAnywhere.generateStream(text).collect { token ->
                    assistantResponse += token
                    val currentMessages = _messages.value.toMutableList()
                    if (currentMessages.lastOrNull()?.isUser == false) {
                        currentMessages[currentMessages.lastIndex] =
                            ChatMessage(assistantResponse, isUser = false)
                    } else {
                        currentMessages.add(ChatMessage(assistantResponse, isUser = false))
                    }
                    _messages.value = currentMessages
                }
            } catch (e: Exception) {
                _messages.value += ChatMessage("Error: ${e.message}", isUser = false)
            }
            _isLoading.value = false
        }
    }

    // ============================================================================================
    // SECTION 3: SMS IMPORT & BATCH PROCESSING
    // ============================================================================================

    fun importSms(context: Context) {
        viewModelScope.launch {
            _isImportingSms.value = true
            _statusMessage.value = "Reading inbox..."

            val list = withContext(Dispatchers.IO) {
                try {
                    // Uses the filtered reader we created (Business/Banks only)
                    readSmsInbox(context, daysLookBack = 30)
                } catch (_: Exception) {
                    emptyList()
                }
            }

            _smsList.value = list
            _statusMessage.value = "Imported ${list.size} financial messages."
            _isImportingSms.value = false
        }
    }

    /**
     * FEATURE: One-Click Processing
     * Iterates through all loaded SMS messages and parses them automatically.
     */
    @Suppress("unused")
    fun processAllMessages() {
        val allMessages = _smsList.value
        if (allMessages.isEmpty()) {
            _statusMessage.value = "No messages to process."
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Batch processing ${allMessages.size} messages..."
            var doneCount = 0
            _processingProgress.value = 0

            // Process strictly sequentially to prevent OOM on mobile devices
            for (sms in allMessages) {
                // 1. Check if already parsed to save time
                if (!_parsedJsonBySms.value.containsKey(sms.id)) {
                    // This calls the AI or Heuristic
                    internalParseSms(sms.id, sms.body ?: "")
                }

                // 2. (Optional) Simple triggers for Scam Check to save battery
                // If it mentions OTP, Login, or Link, check it for scams
                if (shouldCheckForScam(sms.body)) {
                    internalDetectScam(sms.id, sms.body ?: "")
                }

                doneCount++
                _processingProgress.value = (doneCount * 100) / allMessages.size
            }

            _statusMessage.value = "Batch Processing Complete."
            _processingProgress.value = 0 // Reset
        }
    }

    private fun shouldCheckForScam(body: String?): Boolean {
        if (body == null) return false
        val lower = body.lowercase()
        return lower.contains("otp") || lower.contains("http") || lower.contains("click") || lower.contains("call")
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

    // Public wrapper for UI calls
    fun parseSms(smsId: String, smsBody: String) {
        viewModelScope.launch {
            internalParseSms(smsId, smsBody)
        }
    }

    // Internal suspend function for batching
    private suspend fun internalParseSms(smsId: String, smsBody: String) {
        if (_currentModelId.value == null) {
            // Fallback immediately to heuristic if no model loaded
            val heuristic = quickHeuristicJson(smsBody)
            _parsedJsonBySms.value += (smsId to heuristic)
            return
        }

        // Show parsing state
        _parsedJsonBySms.value += (smsId to "Parsing...")

        try {
            val prompt = buildString {
                append(extractionPromptTemplate)
                append("\n\nSMS: \"${smsBody.replace("\"", "\\\"")}\"\n")
                append("BEGIN_JSON\n")
                append("\n")
                append("END_JSON\n")
            }

            var streamed = ""
            // Timeout to prevent hanging on one message
            val result = withTimeoutOrNull(45000L) {
                RunAnywhere.generateStream(prompt).collect { token ->
                    streamed += token
                    // Optional: Update UI with streaming text?
                    // No, too fast for batching. Just collect.
                }
                streamed
            } ?: ""

            var finalText = result.trim()

            // Logic: Extract JSON between markers
            val beginIdx = finalText.indexOf("BEGIN_JSON")
            val endIdx = finalText.indexOf("END_JSON")

            finalText = if (beginIdx in 0 until endIdx) {
                finalText.substring(beginIdx + "BEGIN_JSON".length, endIdx).trim()
            } else {
                extractFirstJsonObject(finalText) ?: quickHeuristicJson(smsBody)
            }

            _parsedJsonBySms.value += (smsId to finalText)

        } catch (_: Exception) {
            // Fallback to Heuristic on error
            val heuristic = quickHeuristicJson(smsBody)
            _parsedJsonBySms.value += (smsId to heuristic)
        }
    }

    // ============================================================================================
    // SECTION 5: SCAM DETECTION
    // ============================================================================================

    private val scamPromptTemplate = """
You are a scam detector. Input: a financial SMS text. Output: return exactly one word: safe, likely_scam, or uncertain. 
Use "likely_scam" if the message requests OTP, links, asks to call a number for payments, or has suspicious phrasing.
Examples:
"Your OTP is 1234" -> likely_scam
"HDFC: Debited Rs 1000 at Amazon" -> safe
"URGENT: Your KYC is expired. Click here" -> likely_scam
Now classify:
""" .trimIndent()

    fun detectScam(smsId: String, smsBody: String) {
        viewModelScope.launch { internalDetectScam(smsId, smsBody) }
    }

    private suspend fun internalDetectScam(smsId: String, smsBody: String) {
        if (_currentModelId.value == null) return

        _scamResultBySms.value += (smsId to "Checking...")

        try {
            val prompt = "$scamPromptTemplate\n\nSMS: \"${smsBody.replace("\"", "\\\"")}\"\nAnswer:"
            var label = ""
            withTimeoutOrNull(10000L) {
                RunAnywhere.generateStream(prompt).collect { token ->
                    label += token
                }
            }
            // Clean up response (remove punctuation, newlines)
            val cleanLabel = label.trim().lowercase().replace(".", "")
            _scamResultBySms.value += (smsId to cleanLabel)
        } catch (_: Exception) {
            _scamResultBySms.value += (smsId to "error")
        }
    }

    // ============================================================================================
    // SECTION 6: HELPER UTILITIES
    // ============================================================================================

    private fun extractFirstJsonObject(text: String): String? {
        var depth = 0
        var start = -1
        for (i in text.indices) {
            val c = text[i]
            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start >= 0) {
                    return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    // Runs on Background Thread to prevent UI freeze
    private suspend fun quickHeuristicJson(sms: String): String = withContext(Dispatchers.Default) {
        // Regex Patterns
        val amountPattern = Pattern.compile("""(?i)(?:INR|Rs\.?|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        val balPattern = Pattern.compile("""(?i)(?:Avl Bal|Available balance|Bal(?:ance)?:)\s*(?:INR|Rs\.?|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        val datePattern = Pattern.compile("""\b(\d{2}[/-]\d{2}[/-]\d{2,4}|\d{4}-\d{2}-\d{2})\b""")
        val acctPattern = Pattern.compile("""(?i)(?:a/c|acct|account|ending|xx)\s*[:#]?\s*([0-9A-Za-z]+)""")

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

        return@withContext buildString {
            append("{")
            append("\"amount\":${amtNum ?: "null"},")
            append("\"currency\":\"INR\",")
            append("\"merchant\":null,")
            append("\"type\":\"$type\",")
            append("\"date\":${if (date != null) "\"${normalizeDate(date)}\"" else "null"},")
            append("\"account_tail\":${if (acct != null) "\"$acct\"" else "null"},")
            append("\"balance\":${balNum ?: "null"},")
            append("\"raw_text\":\"${sms.replace("\"", "\\\"")}\"")
            append("}")
        }
    }

    private fun normalizeDate(s: String): String {
        if (s.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return s
        val parts = s.split('/', '-')
        return try {
            if (parts.size == 3) {
                val d = parts[0]
                val m = parts[1]
                var y = parts[2]
                if (y.length == 2) y = "20$y"
                String.format("%04d-%02d-%02d", y.toInt(), m.toInt(), d.toInt())
            } else s
        } catch (_: Exception) { s }
    }

    // Manually save edited JSON from UI back into the parsed map
    fun forceSaveParsedJson(smsId: String, json: String) {
        _parsedJsonBySms.value += (smsId to json)
    }

    // ============================================================================================
    // SECTION 7: CASH FLOW PREDICTION
    // ============================================================================================

    /**
     * Analyzes all parsed transactions and generates cash flow predictions
     */
    fun predictCashFlow() {
        viewModelScope.launch {
            _isPredicting.value = true
            _statusMessage.value = "Analyzing cash flow patterns..."

            try {
                // Create a map of SMS ID to RawSms for easy lookup
                val smsMap = _smsList.value.associateBy { it.id }

                // Get prediction
                val prediction = cashFlowPredictor.predictCashFlow(
                    parsedJsonMap = _parsedJsonBySms.value,
                    smsListMap = smsMap
                )

                _cashFlowPrediction.value = prediction
                _statusMessage.value = "Cash flow prediction complete!"
            } catch (e: Exception) {
                _statusMessage.value = "Prediction failed: ${e.message}"
                _cashFlowPrediction.value = null
            } finally {
                _isPredicting.value = false
            }
        }
    }

    /**
     * Clear cash flow prediction
     */
    fun clearCashFlowPrediction() {
        _cashFlowPrediction.value = null
    }

    // ============================================================================================
    // SECTION 8: VOICE FEATURES
    // ============================================================================================

    /**
     * Speak cash flow prediction summary
     */
    fun speakCashFlowSummary() {
        val prediction = _cashFlowPrediction.value ?: return
        voiceManager?.let { vm ->
            _isSpeaking.value = true
            vm.speakCashFlowSummary(prediction) {
                _isSpeaking.value = false
            }
        }
    }

    /**
     * Speak quick transaction stats
     */
    fun speakTransactionStats() {
        val transactions = _parsedJsonBySms.value
        if (transactions.isEmpty()) return

        var totalDebits = 0.0
        var totalCredits = 0.0

        transactions.values.forEach { jsonStr ->
            try {
                val json = org.json.JSONObject(jsonStr)
                val amount = json.optDouble("amount", 0.0)
                val type = json.optString("type", "info")

                when (type) {
                    "debit" -> totalDebits += amount
                    "credit" -> totalCredits += amount
                }
            } catch (_: Exception) {
            }
        }

        voiceManager?.let { vm ->
            _isSpeaking.value = true
            val summary =
                vm.generateTransactionSummary(transactions.size, totalDebits, totalCredits)
            vm.speak(summary) {
                _isSpeaking.value = false
            }
        }
    }

    /**
     * Speak scam detection results
     */
    fun speakScamResults() {
        val scamResults = _scamResultBySms.value
        val scamCount = scamResults.values.count { it.contains("likely_scam", ignoreCase = true) }

        voiceManager?.let { vm ->
            _isSpeaking.value = true
            val alert = vm.generateScamAlert(scamCount)
            vm.speak(alert) {
                _isSpeaking.value = false
            }
        }
    }

    /**
     * Stop speaking immediately
     */
    fun stopSpeaking() {
        voiceManager?.stop()
        _isSpeaking.value = false
    }

    /**
     * Cleanup voice manager
     */
    override fun onCleared() {
        super.onCleared()
        voiceManager?.shutdown()
    }

}