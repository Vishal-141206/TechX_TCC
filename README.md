# 💰 Privacy-First Personal Finance Manager

A production-ready Android app that analyzes financial SMS messages using **100% on-device AI** - no
cloud, no tracking, complete privacy.

[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Local-green)](.)
[![AI](https://img.shields.io/badge/AI-On--Device-blue)](.)
[![Voice](https://img.shields.io/badge/Voice-TTS%20Enabled-orange)](.)
[![Status](https://img.shields.io/badge/Status-Production%20Ready-success)](.)

> **Hackathon Project:** Privacy-First Personal Finance Manager with On-Device AI

## 🎯 What This App Does

A complete personal finance manager that:

1. **Reads** your bank SMS messages (last 30 days)
2. **Extracts** transaction details using on-device AI
3. **Detects** scam/phishing messages automatically
4. **Predicts** your next month's cash flow
5. **Finds** recurring subscriptions and bills
6. **Analyzes** spending by category with trends
7. **Speaks** financial summaries out loud (TTS)
8. **Works** 100% offline - no data leaves your device

**Privacy Guarantee:** All processing happens on your phone. Works in Airplane Mode.

## Features

### Core Features

- **Model Management**: Download and load AI models directly in the app
- **Real-time Streaming**: See AI responses generate word-by-word
- **Simple UI**: Clean Jetpack Compose interface
- **On-Device AI**: All inference runs locally on your Android device

### NEW: Financial SMS Analysis

- **SMS Import**: Read and filter financial SMS messages
- **AI Parsing**: Extract transaction details (amount, merchant, category)
- **Scam Detection**: Identify suspicious/phishing messages
- **💰 Cash Flow Prediction**: Smart financial forecasting with:
    - Next month income/expense predictions
    - Recurring transaction detection (subscriptions, bills)
    - Category-wise spending analysis with trends
    - Personalized financial insights
    - Confidence scoring based on data quality

## Quick Start

### 1. Build and Run

```bash
./gradlew assembleDebug
# Or open in Android Studio and click Run
```

### 2. Download a Model

1. Launch the app
2. Tap "Models" in the top bar
3. Choose a model (we recommend starting with "SmolLM2 360M Q8_0" - only 119 MB)
4. Tap "Download" and wait for it to complete

### 3. Load the Model

1. Once downloaded, tap "Load" on the model
2. Wait for "Model loaded! Ready to chat." message

### 4. Start Chatting!

1. Type a message in the text field
2. Tap "Send"
3. Watch the AI response generate in real-time

### 5. Try Cash Flow Prediction! 💰 (New Feature)

1. Grant SMS permissions (tap "Grant Permissions")
2. Import SMS messages (tap "Import SMS")
3. Parse some transaction messages (tap "Parse" on each SMS)
4. Click "💰 Predict Cash Flow" to see your financial forecast
5. Review predictions, insights, and spending trends

**See:** [HOW_TO_USE_CASH_FLOW.md](HOW_TO_USE_CASH_FLOW.md) for detailed instructions

## Available Models

The app comes pre-configured with two models:

| Model | Size | Quality | Best For |
|-------|------|---------|----------|
| SmolLM2 360M Q8_0 | 119 MB | Basic | Testing, quick responses |
| Qwen 2.5 0.5B Instruct Q6_K | 374 MB | Better | General conversations |

## Technical Details

### SDK Components Used

- **RunAnywhere Core SDK**: Component architecture and model management
- **LlamaCpp Module**: Optimized llama.cpp inference engine with 7 ARM64 variants
- **Kotlin Coroutines**: For async operations and streaming

### Architecture

```
MyApplication (initialization)
    ↓
ChatViewModel (state management)
    ↓
ChatScreen (UI layer)
```

### Key Files

- `MyApplication.kt` - SDK initialization and model registration
- `ChatViewModel.kt` - Business logic and state management
- `MainActivity.kt` - UI components and composables
- `CashFlowPredictor.kt` - **NEW:** Financial prediction algorithm
- `SMSreader.kt` - SMS import utilities
- `TransactionRepo.kt` - Transaction processing logic

## Requirements

- Android 7.0 (API 24) or higher
- ~200 MB free storage (for smallest model)
- Internet connection (for downloading models)

## Troubleshooting

### Models not showing up

- Wait a few seconds for SDK initialization
- Tap "Refresh" in the Models section
- Check logcat for initialization errors

### Download fails

- Check internet connection
- Ensure sufficient storage space
- Verify INTERNET permission in AndroidManifest.xml

### App crashes during generation

- Try the smaller model (SmolLM2 360M)
- Close other apps to free memory
- Check that `largeHeap="true"` is set in AndroidManifest.xml

### Generation is slow

- This is normal for on-device inference
- Smaller models run faster
- Performance depends on device CPU

## Next Steps

Want to customize this app? Try:

1. **Add more models** - Edit `MyApplication.kt` → `registerModels()`
2. **Customize UI** - Edit `MainActivity.kt` compose functions
3. **Add system prompts** - Modify message format in `ChatViewModel.kt`
4. **Persist chat history** - Add Room database or DataStore
5. **Add model parameters** - Explore temperature, top-k, top-p settings

## Resources

### Getting Started

- [Full Quick Start Guide](app/src/main/java/com/runanywhere/startup_hackathon20/QUICK_START_ANDROID.md)
- [RunAnywhere SDK Repository](https://github.com/RunanywhereAI/runanywhere-sdks)
- [SDK Documentation](https://github.com/RunanywhereAI/runanywhere-sdks/blob/main/CLAUDE.md)

### Cash Flow Prediction Documentation

- 📑 **[Documentation Index](CASHFLOW_INDEX.md)** - Navigate all cash flow docs ⭐ START HERE
- 📘 [User Guide: How to Use Cash Flow](HOW_TO_USE_CASH_FLOW.md) - Step-by-step instructions
- 🔧 [Developer Guide](DEVELOPER_GUIDE_CASHFLOW.md) - Technical implementation details
- 📋 [Feature Overview](CASH_FLOW_PREDICTION_FEATURE.md) - Complete feature description
- ⚡ [Quick Reference](CASHFLOW_QUICK_REFERENCE.md) - Quick lookup guide
- 🧪 [Testing Checklist](TESTING_CHECKLIST.md) - 140 test cases
- 🎨 [Visual Guide](VISUAL_GUIDE_CASHFLOW.md) - UI flow diagrams
- 📊 [Implementation Summary](IMPLEMENTATION_SUMMARY.md) - What was built

## License

This example app follows the license of the RunAnywhere SDK.
