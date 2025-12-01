package com.runanywhere.startup_hackathon20

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.startup_hackathon20.ui.theme.Startup_hackathon20Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Startup_hackathon20Theme {
                ChatScreen()
            }
        }
    }
}

@Composable
fun RequestSmsAndAudioPermissionButton(
    onPermissionGranted: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            onPermissionGranted()
        }
    }

    Button(onClick = {
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }) {
        Text("Grant Permissions")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val currentModelId by viewModel.currentModelId.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }
    
    // Initialize voice manager once
    LaunchedEffect(Unit) {
        viewModel.initializeVoice(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Chat") },
                actions = {
                    TextButton(onClick = { showModelSelector = !showModelSelector }) {
                        Text("Models")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    downloadProgress?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }

            // Model selector (collapsible)
            if (showModelSelector) {
                ModelSelector(
                    models = availableModels,
                    currentModelId = currentModelId,
                    onDownload = { modelId -> viewModel.downloadModel(modelId) },
                    onLoad = { modelId -> viewModel.loadModel(modelId) },
                    onRefresh = { viewModel.refreshModels() }
                )
            }

            val listState = rememberLazyListState()

            // SMS Import Section
            val context = LocalContext.current
            val smsList by viewModel.smsList.collectAsState()
            val isImportingSms by viewModel.isImportingSms.collectAsState()

            // New: parsed/scam maps collected once
            val parsedMap by viewModel.parsedJsonBySms.collectAsState()
            val scamMap by viewModel.scamResultBySms.collectAsState()

            // Cash Flow Prediction State
            val cashFlowPrediction by viewModel.cashFlowPrediction.collectAsState()
            val isPredicting by viewModel.isPredicting.collectAsState()
            var showPredictionDialog by remember { mutableStateOf(false) }

            // Permission + Import row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RequestSmsAndAudioPermissionButton {
                    viewModel.importSms(context)
                }

                Button(onClick = { viewModel.importSms(context) }, enabled = !isImportingSms) {
                    Text(if (isImportingSms) "Importing..." else "Import SMS")
                }
            }

            // Cash Flow Prediction Button
            if (smsList.isNotEmpty() && parsedMap.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            viewModel.predictCashFlow()
                            showPredictionDialog = true
                        },
                        enabled = !isPredicting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text(if (isPredicting) "Analyzing..." else "💰 Predict Cash Flow")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Show Cash Flow Summary Card if prediction exists
            if (cashFlowPrediction != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { showPredictionDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📊 Cash Flow Summary",
                                style = MaterialTheme.typography.titleSmall
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Voice button
                                IconButton(onClick = {
                                    if (isSpeaking) {
                                        viewModel.stopSpeaking()
                                    } else {
                                        viewModel.speakCashFlowSummary()
                                    }
                                }) {
                                    Text(if (isSpeaking) "🔇" else "🔊", fontSize = 18.sp)
                                }

                                Text(
                                    "Tap for details",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Next Month", fontSize = 11.sp)
                                Text(
                                    "₹${
                                        String.format(
                                            "%.0f",
                                            cashFlowPrediction!!.predictedBalance
                                        )
                                    }",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (cashFlowPrediction!!.predictedBalance >= 0)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Confidence", fontSize = 11.sp)
                                Text(
                                    cashFlowPrediction!!.confidence,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }

            // Show Cash Flow Prediction Dialog
            if (showPredictionDialog && cashFlowPrediction != null) {
                CashFlowPredictionDialog(
                    prediction = cashFlowPrediction!!,
                    onDismiss = { showPredictionDialog = false }
                )
            }

            // Show a small preview list of imported SMS (max 6)
            if (smsList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Imported SMS (preview):",
                        style = MaterialTheme.typography.labelLarge
                    )

                    // Voice summary button
                    if (parsedMap.isNotEmpty()) {
                        IconButton(onClick = {
                            if (isSpeaking) {
                                viewModel.stopSpeaking()
                            } else {
                                viewModel.speakTransactionStats()
                            }
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isSpeaking) "🔇" else "🔊", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stats", fontSize = 12.sp)
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(smsList.take(6)) { sms ->
                        var showEditDialog by remember { mutableStateOf(false) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // optional: show details
                                },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = sms.address ?: "Unknown", fontSize = 12.sp)
                                Text(
                                    text = formatDate(sms.date),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if ((sms.body?.length ?: 0) > 120) sms.body?.take(120) + "..." else sms.body
                                        ?: "",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                // Action row: Parse + Scam
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        viewModel.parseSms(sms.id, sms.body ?: "")
                                    }) {
                                        Text("Parse")
                                    }

                                    TextButton(onClick = {
                                        viewModel.detectScam(sms.id, sms.body ?: "")
                                    }) {
                                        Text("Scam")
                                    }

                                    TextButton(onClick = { showEditDialog = true }) {
                                        Text("Edit")
                                    }
                                }

                                // Show parsed JSON (if present)
                                val parsed = parsedMap[sms.id]
                                if (!parsed.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Parsed JSON:", style = MaterialTheme.typography.labelSmall)

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Text(
                                            text = parsed,
                                            modifier = Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                // Show Scam status if present
                                val scamStatus = scamMap[sms.id]
                                if (!scamStatus.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))

                                    val color = when {
                                        scamStatus.contains("likely_scam", ignoreCase = true) -> MaterialTheme.colorScheme.error
                                        scamStatus.contains("safe", ignoreCase = true) -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Text(
                                        text = "Scam: $scamStatus",
                                        color = color,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        // Edit dialog when Edit button clicked
                        if (showEditDialog) {
                            val existing = parsedMap[sms.id] ?: ""
                            EditParsedDialog(
                                initialText = existing,
                                onDismiss = { showEditDialog = false },
                                onSave = { newText ->
                                    // save edited JSON back into ViewModel parsed map
                                    viewModel.forceSaveParsedJson(sms.id, newText)
                                    showEditDialog = false
                                    // optional toast
                                    Toast.makeText(context, "Parsed JSON updated", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
            }

            // Auto-scroll to bottom when new messages arrive
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            // Input Field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    enabled = !isLoading && currentModelId != null
                )

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !isLoading && inputText.isNotBlank() && currentModelId != null
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun EditParsedDialog(initialText: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initialText)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Edit parsed JSON", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onSave(text.text) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (message.isUser) "You" else "AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ModelSelector(
    models: List<com.runanywhere.sdk.models.ModelInfo>,
    currentModelId: String?,
    onDownload: (String) -> Unit,
    onLoad: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Available Models",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (models.isEmpty()) {
                Text(
                    text = "No models available. Initializing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(models) { model ->
                        ModelItem(
                            model = model,
                            isLoaded = model.id == currentModelId,
                            onDownload = { onDownload(model.id) },
                            onLoad = { onLoad(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelItem(
    model: com.runanywhere.sdk.models.ModelInfo,
    isLoaded: Boolean,
    onDownload: () -> Unit,
    onLoad: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleSmall
            )

            if (isLoaded) {
                Text(
                    text = "✓ Currently Loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        enabled = !model.isDownloaded
                    ) {
                        Text(if (model.isDownloaded) "Downloaded" else "Download")
                    }

                    Button(
                        onClick = onLoad,
                        modifier = Modifier.weight(1f),
                        enabled = model.isDownloaded
                    ) {
                        Text("Load")
                    }
                }
            }
        }
    }
}

@Composable
fun CashFlowPredictionDialog(prediction: CashFlowPrediction, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            LazyColumn(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💰 Cash Flow Prediction",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                    Text(
                        text = "Confidence: ${prediction.confidence}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (prediction.confidence) {
                            "High" -> MaterialTheme.colorScheme.primary
                            "Medium" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }

                // Summary Cards
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Next Month Summary", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Expected Income", fontSize = 12.sp)
                                    Text(
                                        "₹${String.format("%.2f", prediction.nextMonthIncome)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Expected Expenses", fontSize = 12.sp)
                                    Text(
                                        "₹${String.format("%.2f", prediction.nextMonthExpenses)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Net Cash Flow", fontSize = 12.sp)
                            Text(
                                "₹${String.format("%.2f", prediction.predictedBalance)}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (prediction.predictedBalance >= 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Insights
                if (prediction.insights.isNotEmpty()) {
                    item {
                        Text("Key Insights", style = MaterialTheme.typography.titleMedium)
                    }
                    items(prediction.insights) { insight ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = insight,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Category Breakdown
                if (prediction.categoryBreakdown.isNotEmpty()) {
                    item {
                        Text("Spending by Category", style = MaterialTheme.typography.titleMedium)
                    }
                    items(prediction.categoryBreakdown.entries.sortedByDescending { it.value.totalSpent }) { (_, categorySpend) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = categorySpend.category,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${
                                            String.format(
                                                "%.1f",
                                                categorySpend.percentageOfTotal
                                            )
                                        }%",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₹${
                                        String.format(
                                            "%.2f",
                                            categorySpend.totalSpent
                                        )
                                    } (${categorySpend.transactionCount} txns)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Trend: ${categorySpend.trend}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (categorySpend.trend) {
                                        "Increasing" -> MaterialTheme.colorScheme.error
                                        "Decreasing" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                // Recurring Transactions
                if (prediction.recurringTransactions.isNotEmpty()) {
                    item {
                        Text("Recurring Transactions", style = MaterialTheme.typography.titleMedium)
                    }
                    items(prediction.recurringTransactions.take(10)) { recurring ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = recurring.merchant,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${recurring.confidence}% confident",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (recurring.confidence >= 75)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₹${
                                        String.format(
                                            "%.2f",
                                            recurring.averageAmount
                                        )
                                    } • ${recurring.frequency}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (recurring.nextExpectedDate != null) {
                                    Text(
                                        text = "Next: ${recurring.nextExpectedDate}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Startup_hackathon20Theme {
        ChatScreen()
    }
}
