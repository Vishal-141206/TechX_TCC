package com.runanywhere.startup_hackathon20

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
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
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel(), startInVoiceMode: Boolean = false) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val isVoiceListening by viewModel.isVoiceListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    val context = LocalContext.current

    // local UI state
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // If navigation requested voice mode, initialize voice and optionally auto-start.
    LaunchedEffect(startInVoiceMode) {
        if (startInVoiceMode) {
            viewModel.initializeVoice(context)
            // Small delay to let UI render before audio starts (optional)
            delay(200)
            viewModel.startVoiceCoach(context)
        }
    }

    // Auto-scroll when messages update
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (startInVoiceMode) "Voice Finance Coach" else "AI Chat Assistant"
                        )
                        Text(
                            text = modelStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // When in voice mode, show voice-first UI above messages
            if (startInVoiceMode) {
                VoiceHeader(
                    isListening = isVoiceListening,
                    isSpeaking = isSpeaking,
                    modelStatus = modelStatus,
                    onMicToggle = {
                        if (isVoiceListening) viewModel.stopVoiceCoach() else viewModel.startVoiceCoach(context)
                    },
                    onStop = { viewModel.stopVoiceCoach() }
                )
            }

            // Messages list (same in both modes)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (startInVoiceMode) "Say something to get finance insights" else "💬 Financial AI Assistant",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (startInVoiceMode)
                                        "Speak to the assistant and it will reply here.\nTap mic to start/stop."
                                    else
                                        "Ask me about:\n• Financial advice\n• Transaction analysis\n• Budget planning\n• Investment tips",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(messages) { message ->
                    MessageBubble(message)
                }

                if (isLoading) {
                    item {
                        // small "Thinking..." indicator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Thinking...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Bottom area - if voice mode, show only small controls; otherwise show input box
            if (startInVoiceMode) {
                VoiceControlsRow(
                    isListening = isVoiceListening,
                    onMicToggle = {
                        if (isVoiceListening) viewModel.stopVoiceCoach() else viewModel.startVoiceCoach(context)
                    },
                    onClose = {
                        // stop voice and navigate back behavior should be done by caller; here we ensure we stop listening.
                        viewModel.stopVoiceCoach()
                    }
                )
            } else {
                // Normal chat input area
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask about your finances...") },
                            maxLines = 3,
                            enabled = !isLoading,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            enabled = !isLoading && inputText.isNotBlank(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceHeader(
    isListening: Boolean,
    isSpeaking: Boolean,
    modelStatus: String,
    onMicToggle: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isListening) "Listening…" else if (isSpeaking) "Speaking…" else "Voice coach ready",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = modelStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mic toggle
            IconButton(onClick = onMicToggle) {
                if (isListening) {
                    Icon(Icons.Default.Mic, contentDescription = "Stop listening", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.MicOff, contentDescription = "Start listening", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Optional stop/close
            TextButton(onClick = onStop) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun VoiceControlsRow(
    isListening: Boolean,
    onMicToggle: () -> Unit,
    onClose: () -> Unit
) {
    // simple persistent control bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMicToggle) {
                if (isListening) {
                    Icon(Icons.Default.Mic, contentDescription = "Stop Listening", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.MicOff, contentDescription = "Start Listening", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (isListening) "Listening..." else "Tap mic to start", style = MaterialTheme.typography.bodyMedium)
        }

        TextButton(onClick = onClose) {
            Text("Stop")
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = bubbleColor,
                contentColor = textColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isUser) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "Assistant",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isUser) "You" else "Finance Assistant",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)

                message.timestamp?.let { timestamp ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
