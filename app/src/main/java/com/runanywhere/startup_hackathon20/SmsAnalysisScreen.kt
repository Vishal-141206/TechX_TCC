package com.runanywhere.startup_hackathon20

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsAnalysisScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current

    // Use the ViewModel's StateFlows (these exist in your ChatViewModel)
    val smsList by viewModel.smsList.collectAsState()
    val isImportingSms by viewModel.isImportingSms.collectAsState()
    val parsedMap by viewModel.parsedJsonBySms.collectAsState()
    val scamMap by viewModel.scamResultBySms.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    LaunchedEffect(Unit) {
        Log.d("UI_ROUTE", "SmsAnalysisScreen mounted")
    }

    Scaffold(topBar = { TopAppBar(title = { Text("SMS Analysis") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Import controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Request permission button
                Button(
                    onClick = {
                        // You might want to call a permission request function here
                        // For now, just trigger SMS import
                        viewModel.importSms(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text("Request Permission & Import SMS")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { viewModel.importSms(context) },
                    enabled = !isImportingSms
                ) {
                    Text(if (isImportingSms) "Importing..." else "Import SMS")
                }
            }

            Divider()

            // Status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = statusMessage, style = MaterialTheme.typography.bodySmall)
            }

            Divider()

            // Processing controls
            if (smsList.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { viewModel.processAllMessages() },
                            enabled = processingProgress == 0
                        ) {
                            if (processingProgress > 0) Text("Processing...") else Text("Process All")
                        }
                    }

                    if (processingProgress > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = (processingProgress.coerceIn(0, 100) / 100f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${processingProgress.coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                Divider()
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No SMS messages imported yet. Tap 'Import SMS' to load messages from your device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Divider()
            }

            // Messages list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(smsList) { sms ->
                    SmsCard(
                        sms = sms,
                        viewModel = viewModel,
                        parsedJson = parsedMap[sms.id],
                        scamStatus = scamMap[sms.id]
                    )
                }
            }
        }
    }
}

/** SmsCard matching your RawSms data model and ViewModel actions */
@Composable
fun SmsCard(
    sms: RawSms,
    viewModel: ChatViewModel,
    parsedJson: String?,
    scamStatus: String?
) {
    var showEditDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sms.address ?: "Unknown",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatDate(sms.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body
            Text(
                text = sms.body ?: "",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Results
            if (!parsedJson.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Parsed: $parsedJson",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (!scamStatus.isNullOrBlank()) {
                Text(
                    text = "Scam Check: $scamStatus",
                    color = if (scamStatus.contains("likely", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { viewModel.parseSms(sms.id, sms.body ?: "") }) {
                    Text("Parse")
                }
                TextButton(onClick = { viewModel.detectScam(sms.id, sms.body ?: "") }) {
                    Text("Check Scam")
                }
                TextButton(onClick = { showEditDialog = true }) {
                    Text("Edit")
                }
            }
        }
    }

    if (showEditDialog) {
        EditParsedDialog(
            initialText = parsedJson ?: "",
            onDismiss = { showEditDialog = false },
            onSave = { newText ->
                viewModel.forceSaveParsedJson(sms.id, newText)
                showEditDialog = false
                android.widget.Toast.makeText(context, "Saved", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun EditParsedDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Parsed JSON") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}