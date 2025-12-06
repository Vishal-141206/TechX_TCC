package com.runanywhere.startup_hackathon20

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel(), startInVoiceMode: Boolean = false) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val isVoiceListening by viewModel.isVoiceListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val currentModelId by viewModel.currentModelId.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()  // Live transcript

    val context = LocalContext.current

    // local UI state
    var inputText by remember { mutableStateOf("") }
    var showSuggestedQuestions by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // Suggested questions for financial assistant (finance coach style)
    val suggestedQuestions = listOf(
        "How to save ₹50,000 in 6 months?",
        "What's the 50-30-20 budget rule?",
        "Best way to invest ₹10,000/month?",
        "How to build emergency fund?",
        "Tax-saving investments under 80C?"
    )

    // Quick action chips
    val quickActions = listOf(
        "💰 Savings Plan" to "Analyze my spending and suggest where I can save money",
        "📊 Budget Plan" to "Create a personalized budget based on 50-30-20 rule",
        "🎯 Investment Tips" to "What are the best investment options for beginners?",
        "💳 Debt Strategy" to "How to pay off credit card debt faster?"
    )

    // If navigation requested voice mode, initialize voice and optionally auto-start.
    LaunchedEffect(startInVoiceMode) {
        if (startInVoiceMode) {
            viewModel.initializeVoice(context)
            delay(500)  // Increased delay to ensure initialization
            // Check permission before starting
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.startVoiceCoach(context)
            }
        }
    }

    // Auto-scroll when messages update
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            showSuggestedQuestions = false
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // AI Avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF667EEA),
                                            Color(0xFF764BA2)
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (startInVoiceMode) "Voice Coach" else "AI Assistant",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when {
                                    isVoiceListening || isSpeaking -> {
                                        Text(
                                            text = if (isVoiceListening) "🎤 Listening..." else "🔊 Speaking...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            fontSize = 12.sp
                                        )
                                    }

                                    currentModelId != null -> {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Secure",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4CAF50),
                                            maxLines = 1,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    else -> {
                                        Text(
                                            text = "Offline",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Clear chat button (only show if messages exist)
                    if (messages.isNotEmpty()) {
                        var showClearDialog by remember { mutableStateOf(false) }

                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showClearDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearDialog = false },
                                icon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                title = { Text("Clear Chat?") },
                                text = { Text("This will delete all messages. This cannot be undone.") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.clearChatHistory()
                                            showClearDialog = false
                                            showSuggestedQuestions = true
                                        }
                                    ) {
                                        Text("Clear", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // When in voice mode, show voice-first UI above messages
            if (startInVoiceMode) {
                // Get last user message and AI response for display
                val lastUserMsg = messages.lastOrNull { it.isUser }?.text ?: ""
                val lastAiMsg = messages.lastOrNull { !it.isUser }?.text ?: ""

                VoiceHeroCard(
                    isListening = isVoiceListening,
                    isSpeaking = isSpeaking,
                    isLoading = isLoading,
                    modelStatus = modelStatus,
                    liveTranscript = liveTranscript,
                    lastUserMessage = lastUserMsg,
                    lastAiResponse = lastAiMsg,
                    onMicToggle = {
                        if (isVoiceListening) viewModel.stopVoiceCoach() else viewModel.startVoiceCoach(
                            context
                        )
                    }
                )
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyStateView(
                            startInVoiceMode = startInVoiceMode,
                            currentModelId = currentModelId,
                            showSuggestedQuestions = showSuggestedQuestions,
                            suggestedQuestions = suggestedQuestions,
                            onQuestionClick = { question ->
                                inputText = question
                                viewModel.sendMessage(question)
                                showSuggestedQuestions = false
                            }
                        )
                    }
                }

                items(messages) { message ->
                    MessageBubble(message, context)
                }

                if (isLoading) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            // Quick Actions (show when messages exist and not in voice mode)
            if (!startInVoiceMode && messages.isNotEmpty() && !isLoading && currentModelId != null) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickActions) { (label, prompt) ->
                        QuickActionChip(label) {
                            viewModel.sendMessage(prompt)
                        }
                    }
                }
            }

            // Bottom area - if voice mode, show only small controls; otherwise show input box
            if (startInVoiceMode) {
                VoiceControlsBar(
                    isListening = isVoiceListening,
                    isSpeaking = isSpeaking,
                    hasMessages = messages.isNotEmpty(),
                    onMicToggle = {
                        if (isVoiceListening) viewModel.stopVoiceCoach() else viewModel.startVoiceCoach(
                            context
                        )
                    },
                    onStopSpeaking = {
                        viewModel.stopSpeaking()
                    }
                )
            } else {
                // Premium Input Area - Fixed keyboard gap issue
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),  // imePadding on Surface itself for proper keyboard handling
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Input field with modern styling
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 120.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = if (currentModelId == null) "Load a model first..."
                                        else "Type a message...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                maxLines = 4,
                                enabled = !isLoading && currentModelId != null,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge
                            )
                        }

                        // Send button
                        FilledIconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            enabled = !isLoading && inputText.isNotBlank() && currentModelId != null,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (!isLoading && inputText.isNotBlank() && currentModelId != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(20.dp),
                                tint = if (!isLoading && inputText.isNotBlank() && currentModelId != null)
                                    Color.White
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceHeroCard(
    isListening: Boolean,
    isSpeaking: Boolean,
    isLoading: Boolean,
    modelStatus: String,
    liveTranscript: String,
    lastUserMessage: String,
    lastAiResponse: String,
    onMicToggle: () -> Unit
) {
    // Google Assistant style animations
    val infiniteTransition = rememberInfiniteTransition(label = "voice")

    // Pulsing animation for mic
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Color animation for listening state
    val colorAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Main Voice Card - Google Assistant Style
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = when {
                                isListening -> listOf(
                                    Color(0xFF4285F4).copy(alpha = 0.9f + colorAnimation * 0.1f),
                                    Color(0xFF34A853),
                                    Color(0xFFFBBC05),
                                    Color(0xFFEA4335)
                                )

                                isSpeaking -> listOf(Color(0xFF00C853), Color(0xFF00E676))
                                isLoading -> listOf(Color(0xFF448AFF), Color(0xFF2979FF))
                                else -> listOf(Color(0xFF424242), Color(0xFF616161))
                            }
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large Mic Button - Google Style
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Animated rings when listening
                        if (isListening) {
                            repeat(3) { index ->
                                val ringDelay = index * 200
                                val ringAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.6f,
                                    targetValue = 0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, delayMillis = ringDelay),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "ring_alpha_$index"
                                )
                                val ringScale by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, delayMillis = ringDelay),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "ring_scale_$index"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .scale(ringScale)
                                        .background(
                                            Color.White.copy(alpha = ringAlpha * 0.3f),
                                            CircleShape
                                        )
                                )
                            }
                        }

                        // Main mic button
                        Surface(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(pulseScale)
                                .clickable(onClick = onMicToggle),
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        isListening -> Icons.Default.Mic
                                        isSpeaking -> Icons.Default.VolumeUp
                                        isLoading -> Icons.Default.Psychology
                                        else -> Icons.Default.Mic
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isListening -> Color(0xFF4285F4)
                                        isSpeaking -> Color(0xFF00C853)
                                        isLoading -> Color(0xFF448AFF)
                                        else -> Color(0xFF757575)
                                    },
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status text
                    Text(
                        text = when {
                            isListening -> "Listening..."
                            isSpeaking -> "Speaking..."
                            isLoading -> "Processing..."
                            else -> "Tap to speak"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when {
                            isListening -> "Ask about savings, budgeting, or investing"
                            isSpeaking -> "AI is answering your question"
                            isLoading -> "Generating response..."
                            else -> "Voice-powered financial assistant"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Transcript Card - Clean Google Style
        if (liveTranscript.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated listening indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(pulseScale)
                            .background(Color(0xFF4285F4), CircleShape)
                    )

                    Text(
                        text = liveTranscript,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // AI Response Card - Clean design
        if ((isSpeaking || (!isListening && !isLoading)) && lastAiResponse.isNotBlank() && liveTranscript.isBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // AI avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF4285F4), Color(0xFF34A853))
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Finance AI",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isSpeaking) {
                                    // Animated speaking indicator
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        repeat(3) { index ->
                                            val barHeight by infiniteTransition.animateFloat(
                                                initialValue = 4f,
                                                targetValue = 12f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(
                                                        300,
                                                        delayMillis = index * 100
                                                    ),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "bar_$index"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(barHeight.dp)
                                                    .background(
                                                        Color(0xFF00C853),
                                                        RoundedCornerShape(2.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = lastAiResponse,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Loading Card
        if (isLoading && !isSpeaking) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = Color(0xFF4285F4)
                    )
                    Text(
                        text = "Thinking...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Suggestions - Only when idle
        if (!isListening && !isSpeaking && !isLoading && liveTranscript.isBlank() && lastAiResponse.isBlank()) {
            Text(
                text = "Try saying:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val suggestions = listOf(
                "\"How can I save money?\"",
                "\"Create a budget for me\"",
                "\"Best investment tips\""
            )

            suggestions.forEach { suggestion ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceControlsBar(
    isListening: Boolean,
    isSpeaking: Boolean,
    hasMessages: Boolean,
    onMicToggle: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop speaking button (only when AI is speaking)
                if (isSpeaking) {
                    FilledTonalIconButton(
                        onClick = onStopSpeaking,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Speaking",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Main mic button
                FilledIconButton(
                    onClick = onMicToggle,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = when {
                            isListening -> MaterialTheme.colorScheme.primary
                            isSpeaking -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Icon(
                        imageVector = when {
                            isListening -> Icons.Default.Mic
                            isSpeaking -> Icons.Default.VolumeUp
                            else -> Icons.Default.MicOff
                        },
                        contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                        modifier = Modifier.size(36.dp),
                        tint = if (isListening || isSpeaking) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Placeholder for symmetry when speaking
                if (isSpeaking) {
                    Spacer(modifier = Modifier.size(56.dp))
                }
            }

            // Hint text below buttons
            if (!isListening && !isSpeaking) {
                Text(
                    text = "Tap mic to ask a question",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    startInVoiceMode: Boolean,
    currentModelId: String?,
    showSuggestedQuestions: Boolean,
    suggestedQuestions: List<String>,
    onQuestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // AI Avatar with gradient
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF667EEA),
                            Color(0xFF764BA2)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (startInVoiceMode) "Voice Finance Coach" else "Your AI Financial Assistant",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (startInVoiceMode)
                "Speak naturally and get instant financial advice"
            else
                "Ask me anything about budgeting, saving, or investing",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Security Badge
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "100% Private & Secure",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        maxLines = 1
                    )
                    Text(
                        text = "Conversations stay on your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        // Show model status if no model is loaded
        if (currentModelId == null) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "AI Model Not Loaded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Go to Models tab to download and load an AI model",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Show suggested questions when chat is empty (not in voice mode)
        if (showSuggestedQuestions && !startInVoiceMode && currentModelId != null) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Try asking:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            suggestedQuestions.forEach { question ->
                SuggestedQuestionCard(question, onQuestionClick)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SuggestedQuestionCard(question: String, onClick: (String) -> Unit) {
    Card(
        onClick = { onClick(question) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QuickActionChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(20.dp),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            enabled = true,
            selected = false
        )
    )
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, context: android.content.Context) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                modifier = Modifier.widthIn(max = 320.dp),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 20.dp else 4.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = if (isUser) 4.dp else 20.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Message text
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )

                    // Timestamp
                    message.timestamp?.let { timestamp ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Copy button for assistant messages
            if (!isUser && message.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip =
                            android.content.ClipData.newPlainText("AI Response", message.text)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(
                            context,
                            "Copied to clipboard",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
