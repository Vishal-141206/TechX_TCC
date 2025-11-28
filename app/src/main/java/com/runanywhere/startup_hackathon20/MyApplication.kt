package com.runanywhere.startup_hackathon20


import android.app.Application
import android.util.Log
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.data.models.SDKEnvironment
import com.runanywhere.sdk.public.extensions.addModelFromURL
import com.runanywhere.sdk.llm.llamacpp.LlamaCppServiceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
class MyApplication : Application() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        // Initialize SDK on Main Thread to ensure native libraries load correctly
        // Using Dispatchers.Main because initializeSDK is a suspend function and we need to handle the coroutine context
        GlobalScope.launch(Dispatchers.Main) {
            initializeSDK()
        }
    }

    private suspend fun initializeSDK() {
        try {
            // Step 1: Initialize SDK
            // Running this on the main dispatcher ensures context is valid for any immediate native init,
            // while the suspend function allows the SDK to offload heavy work internally.
            RunAnywhere.initialize(
                context = this@MyApplication,
                apiKey = "dev",  // Any string works in dev mode
                environment = SDKEnvironment.DEVELOPMENT
            )

            // Step 2: Register LLM Service Provider
            // This triggers native library loading, which is safest on the main thread
            LlamaCppServiceProvider.register()

            // Step 3: Register Models
            // Offload network/disk operations to IO
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                registerModels()
                // Step 4: Scan for previously downloaded models
                RunAnywhere.scanForDownloadedModels()
            }

            Log.i("MyApp", "SDK initialized successfully")

        } catch (e: Exception) {
            Log.e("MyApp", "SDK initialization failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun registerModels() {
        // Medium-sized model - better quality (374 MB)
        addModelFromURL(
            url = "https://huggingface.co/Triangle104/Qwen2.5-0.5B-Instruct-Q6_K-GGUF/resolve/main/qwen2.5-0.5b-instruct-q6_k.gguf",
            name = "Qwen 2.5 0.5B Instruct Q6_K",
            type = "LLM"
        )
    }
}
