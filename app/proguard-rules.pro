# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ===============================================================================
# Production-Ready ProGuard Rules for Finance AI App
# ===============================================================================

# Keep line numbers for crash reports
-keepattributes LineNumberTable
-keepattributes SourceFile

# Keep all data classes used in the app
-keep class com.runanywhere.startup_hackathon20.ChatMessage { *; }
-keep class com.runanywhere.startup_hackathon20.CashFlowPrediction { *; }
-keep class com.runanywhere.startup_hackathon20.RawSms { *; }
-keep class com.runanywhere.startup_hackathon20.CashFlowPredictor$Transaction { *; }
-keep class com.runanywhere.startup_hackathon20.NavigationItem { *; }

# Keep ViewModel classes
-keep class com.runanywhere.startup_hackathon20.ChatViewModel { *; }

# Keep RunAnywhere SDK classes (used via reflection)
-keep class com.runanywhere.sdk.** { *; }
-keepclassmembers class com.runanywhere.sdk.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep JSON parsing classes
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class org.json.** { *; }

# Keep Jetpack Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep Android TTS and Speech Recognition
-keep class android.speech.** { *; }
-keep class android.speech.tts.** { *; }

# Keep WorkManager classes
-keep class androidx.work.** { *; }
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep custom Application class
-keep class com.runanywhere.startup_hackathon20.MyApplication { *; }

# Keep service classes
-keep class com.runanywhere.startup_hackathon20.TransactionProcessingService { *; }
-keep class com.runanywhere.startup_hackathon20.SmsReceiver { *; }
-keep class com.runanywhere.startup_hackathon20.SmsProcessingWorker { *; }

# Keep VoiceManager (uses reflection for ASR)
-keep class com.runanywhere.startup_hackathon20.VoiceManager { *; }
-keepclassmembers class com.runanywhere.startup_hackathon20.VoiceManager {
    private ** callModelAsr(...);
}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep serialization annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep ViewModels for Compose
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Optimization settings for production
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Allow obfuscation but keep important classes
-dontwarn kotlinx.coroutines.flow.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
