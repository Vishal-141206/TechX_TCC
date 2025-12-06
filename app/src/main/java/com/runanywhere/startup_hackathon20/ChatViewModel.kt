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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private var downloadJob: kotlinx.coroutines.Job? = null

    private val _downloadedModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModels: StateFlow<Set<String>> = _downloadedModels

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

    // Live transcript for voice mode (like Google Assistant)
    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript

    // SharedPreferences keys
    private val PREFS_NAME = "finance_ai_prefs"
    private val KEY_LAST_MODEL_ID = "last_model_id"
    private val KEY_PARSED_SMS = "parsed_sms_json"
    private val KEY_SCAM_RESULTS = "scam_results_json"

    init {
        loadAvailableModels()
        loadDownloadedModelsStatus()
    }

    private fun loadDownloadedModelsStatus() {
        viewModelScope.launch {
            try {
                val models = withContext(Dispatchers.IO) { listAvailableModels() }
                val downloaded = models.filter { it.isDownloaded }.map { it.id }.toSet()
                _downloadedModels.value = downloaded
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading downloaded models: ${e.message}", e)
            }
        }
    }

    /**
     * Load persisted data (model ID, parsed SMS, scam results)
     * This is called from MainActivity on app start to restore the user's session
     */
    fun loadPersistedData(context: Context) {
        // Store app context for saving data later AND for voice/TTS
        appContext = context.applicationContext

        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                // Load last model ID and auto-load it if exists
                val lastModelId = prefs.getString(KEY_LAST_MODEL_ID, null)
                if (!lastModelId.isNullOrBlank()) {
                    Log.d("ChatViewModel", "=== AUTO-LOAD START ===")
                    Log.d("ChatViewModel", "Found saved model ID: $lastModelId")

                    // Wait for downloaded models list to load first (avoid race condition)
                    delay(1000) // Increased from 500ms to 1000ms for stability

                    // Verify model is still downloaded before auto-loading
                    val models = withContext(Dispatchers.IO) {
                        try {
                            listAvailableModels()
                        } catch (e: Exception) {
                            Log.e(
                                "ChatViewModel",
                                "Error checking model availability: ${e.message}"
                            )
                            emptyList()
                        }
                    }

                    Log.d("ChatViewModel", "Available models: ${models.map { it.id }}")
                    val modelInfo = models.find { it.id == lastModelId }
                    val isDownloaded = modelInfo?.isDownloaded == true

                    if (isDownloaded && _currentModelId.value == null) {
                        Log.d("ChatViewModel", "Auto-loading previously used model: $lastModelId")
                        _modelStatus.value = "Auto-loading model..."

                        // Actually load the model (not just set the ID)
                        loadModel(lastModelId)

                        // Wait for load to complete and verify
                        delay(2000) // Give model time to load

                        if (_currentModelId.value == lastModelId) {
                            Log.d(
                                "ChatViewModel",
                                "=== AUTO-LOAD SUCCESS: Model verified loaded ==="
                            )
                        } else {
                            Log.e(
                                "ChatViewModel",
                                "=== AUTO-LOAD FAILED: Model ID not set after load ==="
                            )
                            _modelStatus.value = "Auto-load failed. Please reload model manually."
                        }
                    } else if (!isDownloaded) {
                        Log.d(
                            "ChatViewModel",
                            "Model $lastModelId not available, skipping auto-load"
                        )
                        _modelStatus.value = "Please download a model to get started."
                        // Clear the saved model ID since it's no longer available
                        prefs.edit().remove(KEY_LAST_MODEL_ID).apply()
                    } else if (_currentModelId.value != null) {
                        Log.d("ChatViewModel", "Model already loaded: ${_currentModelId.value}")
                        _modelStatus.value = "✓ Model ready"
                    }
                } else {
                    Log.d("ChatViewModel", "No previously used model found")
                    _modelStatus.value = "Please download and load a model."
                }

                // Load parsed SMS data
                val parsedJson = prefs.getString(KEY_PARSED_SMS, null)
                if (parsedJson != null) {
                    try {
                        val parsedMap = JSONObject(parsedJson).let { json ->
                            val map = mutableMapOf<String, String>()
                            json.keys().forEach { key ->
                                map[key] = json.getString(key)
                            }
                            map
                        }
                        _parsedJsonBySms.value = parsedMap
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error loading parsed SMS: ${e.message}", e)
                    }
                }

                // Load scam results
                val scamJson = prefs.getString(KEY_SCAM_RESULTS, null)
                if (scamJson != null) {
                    try {
                        val scamMap = JSONObject(scamJson).let { json ->
                            val map = mutableMapOf<String, String>()
                            json.keys().forEach { key ->
                                map[key] = json.getString(key)
                            }
                            map
                        }
                        _scamResultBySms.value = scamMap
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error loading scam results: ${e.message}", e)
                    }
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading persisted data: ${e.message}", e)
            }
        }
    }

    /**
     * Save data to SharedPreferences
     */
    private fun savePersistedData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()

                // Save current model ID
                _currentModelId.value?.let { editor.putString(KEY_LAST_MODEL_ID, it) }

                // Save parsed SMS
                val parsedJson = JSONObject().apply {
                    _parsedJsonBySms.value.forEach { (key, value) ->
                        put(key, value)
                    }
                }.toString()
                editor.putString(KEY_PARSED_SMS, parsedJson)

                // Save scam results
                val scamJson = JSONObject().apply {
                    _scamResultBySms.value.forEach { (key, value) ->
                        put(key, value)
                    }
                }.toString()
                editor.putString(KEY_SCAM_RESULTS, scamJson)

                editor.apply()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error saving persisted data: ${e.message}", e)
            }
        }
    }

    fun initializeVoice(context: Context) {
        // Store application context for later restart of voice coach after TTS
        appContext = context.applicationContext
        if (voiceManager == null) {
            appContext?.let { ctx ->
                voiceManager = VoiceManager(ctx)
            }
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

                // Update downloaded models set
                val downloaded = models.filter { it.isDownloaded }.map { it.id }.toSet()
                _downloadedModels.value = downloaded

                _modelStatus.value = when {
                    models.isEmpty() -> "No models found. Check connection."
                    downloaded.isNotEmpty() -> "Models ready. Select a model to load."
                    else -> "Please download a model to get started."
                }
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
        downloadJob = viewModelScope.launch {
            try {
                _downloadingModelId.value = modelId
                _downloadProgress.value = 0f
                _modelStatus.value = "Downloading model..."

                // Use cancellable flow collection
                RunAnywhere.downloadModel(modelId).collect { progress ->
                    // Check if job is still active
                    if (!kotlinx.coroutines.coroutineScope { isActive }) {
                        throw kotlinx.coroutines.CancellationException("Download cancelled by user")
                    }
                    _downloadProgress.value = progress
                    _modelStatus.value = "Downloading: ${(progress * 100).toInt()}%"
                }

                _modelStatus.value = "Download complete! You can now load the model."

                // Add to downloaded models set
                _downloadedModels.update { it + modelId }

                refreshModels()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _modelStatus.value = "Download cancelled."
                Log.d("ChatViewModel", "Download cancelled for model: $modelId")
            } catch (e: Exception) {
                _modelStatus.value = "Download failed: ${e.message}"
                Log.e("ChatViewModel", "Download failed for model: $modelId", e)
            } finally {
                _downloadProgress.value = null
                _downloadingModelId.value = null
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        val currentDownload = _downloadingModelId.value
        downloadJob?.cancel(kotlinx.coroutines.CancellationException("User cancelled download"))

        // Force cleanup immediately
        _downloadProgress.value = null
        _downloadingModelId.value = null
        _modelStatus.value = "Download cancelled by user."
        downloadJob = null

        Log.d("ChatViewModel", "Download cancelled: $currentDownload")
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _modelStatus.value = "Loading model..."
            try {
                Log.d("ChatViewModel", "=== MODEL LOAD START ===")
                Log.d("ChatViewModel", "Attempting to load model: $modelId")

                // First verify the model is downloaded
                val models = withContext(Dispatchers.IO) {
                    try {
                        listAvailableModels()
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error listing models", e)
                        emptyList()
                    }
                }

                Log.d("ChatViewModel", "Available models count: ${models.size}")
                models.forEach { model ->
                    Log.d("ChatViewModel", "  - ${model.id}: downloaded=${model.isDownloaded}")
                }

                val modelInfo = models.find { it.id == modelId }

                if (modelInfo == null) {
                    _currentModelId.value = null
                    _modelStatus.value = "Model not found. Please download it first."
                    Log.e("ChatViewModel", "Model not found in available models list: $modelId")
                    return@launch
                }

                if (!modelInfo.isDownloaded) {
                    _currentModelId.value = null
                    _modelStatus.value = "Model not downloaded. Please download it first."
                    Log.e("ChatViewModel", "Model not downloaded: $modelId")
                    return@launch
                }

                Log.d("ChatViewModel", "Model verified, calling RunAnywhere.loadModel...")
                val success = withContext(Dispatchers.IO) {
                    try {
                        val result = RunAnywhere.loadModel(modelId)
                        Log.d("ChatViewModel", "RunAnywhere.loadModel returned: $result")
                        result
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Exception in RunAnywhere.loadModel", e)
                        false
                    }
                }

                if (success) {
                    Log.d("ChatViewModel", "=== MODEL LOAD SDK SUCCESS: $modelId ===")

                    // Test the model immediately with a simple prompt to verify it actually works
                    Log.d("ChatViewModel", "Testing model with simple prompt...")
                    var testPassed = false
                    try {
                        val testResponse = withContext(Dispatchers.IO) {
                            var response = ""
                            val testTimeout = withTimeoutOrNull(5000L) {
                                RunAnywhere.generateStream("Say hi").collect { token ->
                                    response += token
                                    Log.d("ChatViewModel", "Test token: '$token'")
                                }
                                true
                            }

                            if (testTimeout == null) {
                                Log.e("ChatViewModel", "Test timeout - model not responding")
                            }

                            Log.d(
                                "ChatViewModel",
                                "Test response: '$response' (length: ${response.length})"
                            )
                            response
                        }

                        if (testResponse.isNotBlank()) {
                            testPassed = true
                            Log.d("ChatViewModel", "✓ Model test PASSED - model is working!")
                        } else {
                            Log.e("ChatViewModel", "✗ Model test FAILED - empty response!")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "✗ Model test FAILED with exception", e)
                    }

                    if (testPassed) {
                        // Only set as loaded if test passed
                        _currentModelId.value = modelId
                        _modelStatus.value = "✓ Model ready & tested"
                        Log.d("ChatViewModel", "=== MODEL FULLY LOADED & VERIFIED: $modelId ===")

                        // Save model ID for next launch
                        appContext?.let { ctx ->
                            savePersistedData(ctx)
                            Log.d("ChatViewModel", "Model ID '$modelId' saved for auto-load")
                        }
                    } else {
                        // Model loaded in SDK but not working
                        _currentModelId.value = null
                        _modelStatus.value = "Model loaded but not responding. Try reload."
                        Log.e("ChatViewModel", "=== MODEL LOADED BUT NOT WORKING ===")
                    }
                } else {
                    _currentModelId.value = null
                    _modelStatus.value = "Failed to load model. Check logs."
                    Log.e(
                        "ChatViewModel",
                        "=== MODEL LOAD FAILED: RunAnywhere.loadModel returned false ==="
                    )
                }
            } catch (e: Exception) {
                _currentModelId.value = null
                _modelStatus.value = "Error: ${e.message?.take(50)}"
                Log.e("ChatViewModel", "=== MODEL LOAD ERROR ===", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshModels() = loadAvailableModels()

    fun clearChatHistory() {
        _messages.value = emptyList()
    }

    // ============================================================================================
    // SECTION 2: CHAT
    // ============================================================================================

    /**
     * Send a text prompt to the model and stream back the assistant reply.
     * If speakResponse==true, the final assistant reply is spoken by VoiceManager.
     */
    fun sendMessage(text: String, speakResponse: Boolean = false) {
        // Verify model is actually loaded (not just UI state)
        if (_currentModelId.value == null) {
            Log.e("ChatViewModel", "sendMessage called but _currentModelId is null")
            _messages.update {
                it + ChatMessage(
                    "⚠️ No AI model loaded. Please go to the Models tab to download and load a model first.",
                    false,
                    getCurrentTimestamp()
                )
            }
            _modelStatus.value = "Please load a model first"
            return
        }

        Log.d("ChatViewModel", "sendMessage called with model: ${_currentModelId.value}")

        // add user message to UI
        _messages.update { it + ChatMessage(text, true, getCurrentTimestamp()) }

        viewModelScope.launch {
            _isLoading.value = true
            _modelStatus.value = "Generating response..."

            try {
                var fullResponse = ""
                var tokenCount = 0

                // Use simple direct prompt - complex prompts may cause empty responses
                val simplePrompt = buildString {
                    appendLine("You are a helpful financial advisor. Keep answers brief and practical.")
                    appendLine()
                    appendLine("Question: $text")
                    appendLine()
                    appendLine("Answer:")
                }

                Log.d("ChatViewModel", "Current model: ${_currentModelId.value}")
                Log.d(
                    "ChatViewModel",
                    "Sending prompt (${simplePrompt.length} chars): ${simplePrompt.take(150)}..."
                )

                // stream tokens (60s timeout for voice - allows complete response)
                val result = withTimeoutOrNull(60_000L) {
                    try {
                        var hasReceivedTokens = false
                        RunAnywhere.generateStream(simplePrompt).collect { token ->
                            hasReceivedTokens = true
                            tokenCount++
                            fullResponse += token

                            // Log ALL tokens for debugging if less than 20
                            if (tokenCount <= 20) {
                                Log.d(
                                    "ChatViewModel",
                                    "Token #$tokenCount: '$token' (length: ${token.length})"
                                )
                            }

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

                        if (!hasReceivedTokens) {
                            Log.e("ChatViewModel", "No tokens received from model!")
                        }

                        hasReceivedTokens // Return true only if tokens were received
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error in generateStream: ${e.message}", e)
                        e.printStackTrace()
                        false
                    }
                }

                if (result == null) {
                    Log.e("ChatViewModel", "Timeout: No response from model after 60s")
                    _messages.update { current ->
                        val m = current.toMutableList()
                        if (m.lastOrNull()?.isUser == false && m.last().text.isBlank()) {
                            m.removeAt(m.lastIndex)
                        }
                        m.add(
                            ChatMessage(
                                "⚠️ Model timeout. Try:\n1. Reload the model from Models tab\n2. Use a simpler question\n3. Restart the app",
                                false,
                                getCurrentTimestamp()
                            )
                        )
                        m.toList()
                    }
                    _modelStatus.value = "Timeout - reload model"
                    return@launch
                }

                if (result == false) {
                    Log.e("ChatViewModel", "Model returned false - no tokens received")
                    _messages.update { current ->
                        val m = current.toMutableList()
                        if (m.lastOrNull()?.isUser == false && m.last().text.isBlank()) {
                            m.removeAt(m.lastIndex)
                        }
                        m.add(
                            ChatMessage(
                                "⚠️ Model not responding. Please:\n1. Go to Models tab\n2. Reload the model (tap Load button)\n3. Try again",
                                false,
                                getCurrentTimestamp()
                            )
                        )
                        m.toList()
                    }
                    _modelStatus.value = "Model not responding - reload"
                    return@launch
                }

                Log.d(
                    "ChatViewModel",
                    "Generation complete. Total tokens: $tokenCount, Response length: ${fullResponse.length}"
                )

                // If no response was generated
                if (fullResponse.isBlank()) {
                    Log.w(
                        "ChatViewModel",
                        "Empty response from model after receiving $tokenCount tokens"
                    )
                    _messages.update { current ->
                        val m = current.toMutableList()
                        if (m.lastOrNull()?.isUser == false) {
                            m[m.lastIndex] = m.last()
                                .copy(text = "⚠️ Empty response. The model is loaded but not generating text.\n\nTry:\n• Reload the model\n• Ask a simpler question\n• Restart the app")
                        } else {
                            m.add(
                                ChatMessage(
                                    "⚠️ Empty response. The model is loaded but not generating text.\n\nTry:\n• Reload the model\n• Ask a simpler question\n• Restart the app",
                                    false,
                                    getCurrentTimestamp()
                                )
                            )
                        }
                        m.toList()
                    }
                    _modelStatus.value = "Empty response - reload model"
                    return@launch
                }

                _modelStatus.value = "✓ Response complete"

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
                            // Wait longer (1.5s) to ensure TTS fully completes and user can process the answer
                            viewModelScope.launch {
                                delay(1500L)  // Increased from 300ms to 1500ms
                                // If voice coach mode is still desired and permission ok, restart.
                                if (_isVoiceListening.value && appContext != null && !_isLoading.value) {
                                    appContext?.let { ctx ->
                                        Log.d(
                                            "ChatViewModel",
                                            "Restarting voice listening after TTS completion"
                                        )
                                        // startVoiceCoach will check permission and set states; it will attempt to start ASR again
                                        startVoiceCoach(ctx)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "sendMessage error: ${e.message}", e)
                _messages.update {
                    it + ChatMessage(
                        "⚠️ Error: ${e.message?.take(100) ?: "Unknown error occurred"}",
                        false,
                        getCurrentTimestamp()) }
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

                // Save processed data to persistent storage
                appContext?.let { savePersistedData(it) }
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
        // Save to persistent storage
        appContext?.let { savePersistedData(it) }
    }

    // ============================================================================================
    // SECTION 4: AI PARSING & SCAM (INTERNAL HELPERS)
    // ============================================================================================

    private suspend fun internalParseSms(smsId: String, smsBody: String) {
        if (smsBody.isBlank()) return

        // SPEED OPTIMIZATION: Use heuristic parsing first (instant), AI as fallback
        try {
            val heuristicResult = quickHeuristicJson(smsBody)

            // If heuristic found amount, use it immediately (fast path)
            if (heuristicResult.contains("\"amount\"") && !heuristicResult.contains("\"amount\":null")) {
                _parsedJsonBySms.update { current ->
                    val m = current.toMutableMap()
                    m[smsId] = heuristicResult
                    m.toMap()
                }
                return
            }

            // Fallback to AI only if heuristic failed (slow path)
            val prompt = buildString {
                appendLine("Extract transaction data. Output ONLY JSON:")
                appendLine("{\"amount\":<number>,\"merchant\":\"<name>\",\"type\":\"debit|credit\",\"date\":\"YYYY-MM-DD\"}")
                appendLine()
                appendLine("SMS: \"$smsBody\"")
                appendLine()
                appendLine("JSON:")
            }

            val result = withTimeoutOrNull(15_000L) { // Reduced from 30s to 15s
                var acc = ""
                RunAnywhere.generateStream(prompt).collect { token -> acc += token }
                acc
            } ?: ""

            val parsedJson = extractFirstJsonObject(result) ?: heuristicResult

            _parsedJsonBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = parsedJson
                m.toMap()
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "internalParseSms error: ${e.message}", e)
            // Use heuristic as final fallback
            val fallback = quickHeuristicJson(smsBody)
            _parsedJsonBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = fallback
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

        // SPEED OPTIMIZATION: Use rule-based detection first (instant)
        val quickCheck = quickScamCheck(smsBody)

        // If it's clearly safe or scam, skip AI (fast path)
        if (quickCheck != "uncertain") {
            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = quickCheck
                m.toMap()
            }
            return
        }

        // Use AI only for uncertain cases (slow path)
        if (_currentModelId.value == null) {
            _scamResultBySms.update { current ->
                val m = current.toMutableMap()
                m[smsId] = "uncertain"
                m.toMap()
            }
            return
        }

        try {
            val prompt = buildString {
                appendLine("Is this SMS a scam? Answer: safe, likely_scam, or uncertain")
                appendLine()
                appendLine("SMS: \"$smsBody\"")
                appendLine()
                appendLine("Answer (one word):")
            }

            val result = withTimeoutOrNull(8_000L) { // Reduced from 10s to 8s
                var acc = ""
                RunAnywhere.generateStream(prompt).collect { acc += it }
                acc
            } ?: ""

            val cleaned = result.trim().lowercase(Locale.getDefault())

            val finalLabel = when {
                cleaned.contains("likely_scam") || cleaned.contains("scam") -> "likely_scam"
                cleaned.contains("safe") -> "safe"
                else -> "uncertain"
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
                m[smsId] = quickCheck // Use rule-based as fallback
                m.toMap()
            }
        }
    }

    /**
     * Quick rule-based scam detection (instant, no AI needed)
     */
    private fun quickScamCheck(smsBody: String): String {
        val lower = smsBody.lowercase()

        // High-confidence scam indicators
        val scamKeywords = listOf(
            "share otp", "enter otp", "provide otp", "give otp",
            "cvv", "pin", "password", "click here", "verify now",
            "account blocked", "kyc pending", "update kyc",
            "congratulations won", "lottery", "prize", "reward",
            "bit.ly", "tinyurl", "suspicious activity", "urgent action"
        )

        if (scamKeywords.any { lower.contains(it) }) {
            return "likely_scam"
        }

        // High-confidence safe indicators
        val safeKeywords = listOf(
            "debited", "credited", "transaction successful",
            "available balance", "avl bal", "a/c", "upi", "imps", "neft"
        )

        val hasSafeKeywords = safeKeywords.any { lower.contains(it) }
        val hasAmountPattern = Regex("(?i)(?:INR|Rs\\.?|₹)\\s*[0-9,]+").find(lower) != null

        if (hasSafeKeywords && hasAmountPattern) {
            return "safe"
        }

        return "uncertain"
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

    /**
     * Builds a context-aware prompt with user's financial data for personalized advice
     * Optimized for voice responses - concise, conversational, actionable
     */
    private fun buildContextAwarePrompt(userQuestion: String): String {
        // Generate user context from actual data
        val userContext = buildUserFinancialContext()

        return buildString {
            appendLine("You are a friendly AI financial advisor. Answer in a CONVERSATIONAL, natural way as if speaking to a friend.")
            appendLine()

            // Add user context if available
            if (userContext.isNotBlank()) {
                appendLine("USER'S FINANCIAL SITUATION:")
                appendLine(userContext)
                appendLine()
            }

            appendLine("RESPONSE RULES:")
            appendLine("- Answer in 2-3 SHORT sentences (40-60 words total)")
            appendLine("- Use a warm, encouraging, conversational tone")
            appendLine("- If user has financial data, reference their ACTUAL numbers")
            appendLine("- Give ONE specific, actionable tip they can do TODAY")
            appendLine("- Use simple language, avoid jargon")
            appendLine("- Be encouraging and positive")
            appendLine("- Format: Acknowledge → Key insight → Action step")
            appendLine()
            appendLine("Example good response:")
            appendLine("\"Great question! Based on your ₹38,000 monthly expenses, I see food is your biggest cost at ₹12,500. Try meal prepping on Sundays - you could save ₹3,000 a month!\"")
            appendLine()
            appendLine("User's question: $userQuestion")
            appendLine()
            appendLine("Your response (conversational, 2-3 sentences):")
        }
    }

    /**
     * Builds user financial context from actual transaction and cash flow data
     */
    private fun buildUserFinancialContext(): String {
        val context = StringBuilder()

        try {
            // Add cash flow summary if available
            val cashFlow = _cashFlowPrediction.value
            if (cashFlow != null) {
                context.appendLine("Monthly Summary:")
                context.appendLine(
                    "- Total Income: ₹${
                        String.format(
                            "%.0f",
                            cashFlow.totalIncome
                        )
                    }"
                )
                context.appendLine(
                    "- Total Expenses: ₹${
                        String.format(
                            "%.0f",
                            cashFlow.totalExpenses
                        )
                    }"
                )
                context.appendLine(
                    "- Net Cash Flow: ₹${
                        String.format(
                            "%.0f",
                            cashFlow.netCashFlow
                        )
                    }"
                )

                if (cashFlow.totalIncome > 0 && cashFlow.totalExpenses > 0) {
                    val savingsRate = ((cashFlow.netCashFlow / cashFlow.totalIncome) * 100).toInt()
                    context.appendLine("- Savings Rate: $savingsRate%")
                }

                // Top spending categories
                if (cashFlow.topCategories.isNotEmpty()) {
                    val topCat =
                        cashFlow.topCategories.entries.sortedByDescending { it.value }.take(3)
                    context.appendLine("Top Spending:")
                    topCat.forEach { (cat, amt) ->
                        context.appendLine("  • $cat: ₹${String.format("%.0f", amt)}")
                    }
                }
                context.appendLine()
            }

            // Add transaction summary if no cash flow but have parsed data
            if (cashFlow == null && _parsedJsonBySms.value.isNotEmpty()) {
                var totalDebit = 0.0
                var totalCredit = 0.0
                var txnCount = 0

                _parsedJsonBySms.value.values.forEach { jsonStr ->
                    try {
                        val json = JSONObject(jsonStr)
                        val amount = json.optDouble("amount", 0.0)
                        val type = json.optString("type", "")

                        if (amount > 0) {
                            txnCount++
                            when (type) {
                                "debit" -> totalDebit += amount
                                "credit" -> totalCredit += amount
                            }
                        }
                    } catch (e: Exception) {
                        // Skip invalid JSON
                    }
                }

                if (txnCount > 0) {
                    context.appendLine("Last 30 Days:")
                    context.appendLine("- Transactions: $txnCount")
                    if (totalCredit > 0) context.appendLine(
                        "- Income: ₹${
                            String.format(
                                "%.0f",
                                totalCredit
                            )
                        }"
                    )
                    if (totalDebit > 0) context.appendLine(
                        "- Expenses: ₹${
                            String.format(
                                "%.0f",
                                totalDebit
                            )
                        }"
                    )

                    if (totalCredit > 0 && totalDebit > 0) {
                        val netFlow = totalCredit - totalDebit
                        context.appendLine(
                            "- Net: ${if (netFlow >= 0) "+" else ""}₹${
                                String.format(
                                    "%.0f",
                                    netFlow
                                )
                            }"
                        )
                    }
                    context.appendLine()
                }
            }

        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error building context: ${e.message}", e)
        }

        return context.toString().trim()
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
                val vm = voiceManager
                if (vm == null) {
                    _modelStatus.value = "Voice manager not initialized"
                    _isVoiceListening.value = false
                    return@launch
                }

                try {
                    vm.startListening(
                        onPartial = { interim ->
                            _liveTranscript.value = interim  // Update live transcript
                            _modelStatus.value = "Listening…"
                        },

                        onFinal = { finalText ->
                            val trimmed = finalText.trim()
                            _liveTranscript.value = trimmed  // Show final text briefly

                            if (trimmed.isNotBlank()) {
                                // Update status to show processing
                                _modelStatus.value = "Processing your question..."

                                // send and request speaking of assistant's reply
                                viewModelScope.launch {
                                    // ensure _isVoiceListening remains true so sendMessage can restart listening after TTS
                                    sendMessage(trimmed, speakResponse = true)

                                    // Clear transcript after a delay
                                    delay(2000)
                                    _liveTranscript.value = ""
                                }
                            } else {
                                // Clear empty transcript
                                _liveTranscript.value = ""
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
            _liveTranscript.value = ""  // Clear transcript
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
