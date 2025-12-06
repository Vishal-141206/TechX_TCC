package com.runanywhere.startup_hackathon20

import android.app.Application
import android.util.Log
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.data.models.SDKEnvironment
import com.runanywhere.sdk.public.extensions.addModelFromURL
import com.runanywhere.sdk.llm.llamacpp.LlamaCppServiceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application() {

    companion object {
        lateinit var instance: MyApplication
            private set
    }

    // Application-scoped coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize any app-wide components here
        Log.d("MyApplication", "Application started")

        // Initialize RunAnywhere SDK asynchronously
        initializeSDK()

        // You can initialize your ML models, databases, etc. here
        initializeAppComponents()
    }

    private fun initializeSDK() {
        applicationScope.launch {
            try {
                Log.d("MyApplication", "Initializing RunAnywhere SDK...")

                // Step 1: Initialize SDK with DEVELOPMENT mode
                // Use "dev" as API key for development mode
                RunAnywhere.initialize(
                    context = this@MyApplication,
                    apiKey = "dev",
                    environment = SDKEnvironment.DEVELOPMENT
                )

                // Step 2: Register LlamaCpp Service Provider (for LLM functionality)
                LlamaCppServiceProvider.register()
                Log.d("MyApplication", "LlamaCpp service provider registered")

                // Step 3: Register available models
                registerModels()

                // Step 4: Scan for previously downloaded models
                RunAnywhere.scanForDownloadedModels()

                Log.i("MyApplication", "RunAnywhere SDK initialized successfully")

            } catch (e: Exception) {
                Log.e("MyApplication", "SDK initialization failed: ${e.message}", e)
            }
        }
    }

    private suspend fun registerModels() {
        try {
            // Register a small model for testing (SmolLM2 360M - 119MB)
            addModelFromURL(
                url = "https://huggingface.co/prithivMLmods/SmolLM2-360M-GGUF/resolve/main/SmolLM2-360M.Q8_0.gguf",
                name = "SmolLM2 360M Q8_0",
                type = "LLM"
            )
            Log.d("MyApplication", "Registered SmolLM2 360M model")

            // Register another small model (Qwen 2.5 0.5B - 374MB)
            addModelFromURL(
                url = "https://huggingface.co/Triangle104/Qwen2.5-0.5B-Instruct-Q6_K-GGUF/resolve/main/qwen2.5-0.5b-instruct-q6_k.gguf",
                name = "Qwen 2.5 0.5B Instruct Q6_K",
                type = "LLM"
            )
            Log.d("MyApplication", "Registered Qwen 2.5 0.5B model")

            // Register Llama 3.2 1B model (815MB)
            addModelFromURL(
                url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q6_K_L.gguf",
                name = "Llama 3.2 1B Instruct Q6_K",
                type = "LLM"
            )
            Log.d("MyApplication", "Registered Llama 3.2 1B model")

            Log.d("MyApplication", "All models registered successfully")

        } catch (e: Exception) {
            Log.e("MyApplication", "Failed to register models: ${e.message}", e)
        }
    }

    private fun initializeAppComponents() {
        // Initialize database, ML models, etc.
        // This runs before any activity is created
    }

    // Helper function to get application context anywhere
    fun getAppContext() = applicationContext
}