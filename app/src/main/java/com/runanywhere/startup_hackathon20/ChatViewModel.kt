package com.runanywhere.startup_hackathon20

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// From SmsReader.kt
import com.runanywhere.startup_hackathon20.RawSms
import com.runanywhere.startup_hackathon20.readSmsInbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.listAvailableModels
import com.runanywhere.sdk.models.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Simple Message Data Class
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

// ViewModel
class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _currentModelId = MutableStateFlow<String?>(null)
    val currentModelId: StateFlow<String?> = _currentModelId

    private val _statusMessage = MutableStateFlow<String>("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage

    init {
        loadAvailableModels()
    }

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
                    _statusMessage.value = "Model loaded in ${duration / 1000.0}s! Ready to chat."
                } else {
                    _statusMessage.value = "Failed to load model"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error loading model: ${e.message}"
            }
        }
    }

    fun sendMessage(text: String) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            return
        }

        // Add user message
        _messages.value += ChatMessage(text, isUser = true)

        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Generate response with streaming
                var assistantResponse = ""
                RunAnywhere.generateStream(text).collect { token ->
                    assistantResponse += token

                    // Update assistant message in real-time
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

    fun refreshModels() {
        loadAvailableModels()
    }

    // ---------------- SMS IMPORT STATE ----------------

    private val _smsList = MutableStateFlow<List<RawSms>>(emptyList())
    val smsList: StateFlow<List<RawSms>> = _smsList

    private val _isImportingSms = MutableStateFlow(false)
    val isImportingSms: StateFlow<Boolean> = _isImportingSms

    fun importSms(context: Context) {
        viewModelScope.launch {
            _isImportingSms.value = true

            val list = withContext(Dispatchers.IO) {
                try {
                    readSmsInbox(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<RawSms>()
                }
            }

            _smsList.value = list
            _isImportingSms.value = false
        }
    }

    // ------------ Parse & Scam state ------------
    private val _parsedJsonBySms = MutableStateFlow<Map<String, String>>(emptyMap())
    val parsedJsonBySms: StateFlow<Map<String, String>> = _parsedJsonBySms

    private val _scamResultBySms = MutableStateFlow<Map<String, String>>(emptyMap()) // values: "safe", "likely_scam", "uncertain", or error
    val scamResultBySms: StateFlow<Map<String, String>> = _scamResultBySms

    // --- Simple few-shot prompts (you can tweak these) ---
    private val extractionPromptTemplate = """
You are a JSON extractor. Input: a bank/SMS message. Output: ONLY a JSON object with keys:
amount (number, in rupees), currency (INR), merchant (string or null), type ("debit" or "credit" or "info"), date (YYYY-MM-DD or null), account_tail (string or null), balance (number or null), raw_text (original message).

Return valid JSON only. Examples:
SMS: "HDFC Bank: Debited INR 1,250.00 at AMAZON PAY on 2025-11-26. Avl Bal: INR 5,000."
JSON: {"amount":1250, "currency":"INR", "merchant":"AMAZON PAY", "type":"debit", "date":"2025-11-26", "account_tail":null, "balance":5000, "raw_text":"HDFC Bank: Debited INR 1,250.00 at AMAZON PAY on 2025-11-26. Avl Bal: INR 5,000."}

Now parse this SMS:
""" .trimIndent()

    private val scamPromptTemplate = """
You are a scam detector. Input: a financial SMS text. Output: return exactly one word: safe, likely_scam, or uncertain. Use "likely_scam" if the message requests OTP, links, asks to call a number for payments, or has suspicious phrasing. Examples:
"Your OTP is 1234" -> likely_scam
"HDFC: Debited Rs 1000 at Amazon" -> safe
Now classify:
""" .trimIndent()

    // ------------- parseSms(smsId, smsBody) -------------
    fun parseSms(smsId: String, smsBody: String) {
        // ensure model loaded
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model before parsing."
            return
        }

        // optimistic: set working placeholder
        _parsedJsonBySms.value = _parsedJsonBySms.value + (smsId to "Parsing...")

        viewModelScope.launch {
            try {
                val prompt = extractionPromptTemplate + "\n\nSMS: \"${smsBody.replace("\"", "\\\"")}\"\nJSON:"
                var jsonResult = ""
                RunAnywhere.generateStream(prompt).collect { token ->
                    jsonResult += token
                    // optional: partial updates (not necessary)
                    _parsedJsonBySms.value = _parsedJsonBySms.value + (smsId to jsonResult)
                }

                // clean up whitespace
                jsonResult = jsonResult.trim()
                _parsedJsonBySms.value = _parsedJsonBySms.value + (smsId to jsonResult)
            } catch (e: Exception) {
                e.printStackTrace()
                _parsedJsonBySms.value = _parsedJsonBySms.value + (smsId to "Error: ${e.message}")
            }
        }
    }

    // ------------- detectScam(smsId, smsBody) -------------
    fun detectScam(smsId: String, smsBody: String) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model before running scam detection."
            return
        }

        _scamResultBySms.value = _scamResultBySms.value + (smsId to "Checking...")

        viewModelScope.launch {
            try {
                val prompt = scamPromptTemplate + "\n\nSMS: \"${smsBody.replace("\"", "\\\"")}\"\nAnswer:"
                var label = ""
                RunAnywhere.generateStream(prompt).collect { token ->
                    label += token
                    // quick update
                    _scamResultBySms.value = _scamResultBySms.value + (smsId to label.trim())
                }
                _scamResultBySms.value = _scamResultBySms.value + (smsId to label.trim())
            } catch (e: Exception) {
                e.printStackTrace()
                _scamResultBySms.value = _scamResultBySms.value + (smsId to "error")
            }
        }
    }
}
