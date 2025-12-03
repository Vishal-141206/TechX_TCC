package com.runanywhere.startup_hackathon20

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsAnalysisScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val smsList by viewModel.smsList.collectAsState()
    val isImportingSms by viewModel.isImportingSms.collectAsState()
    val parsedMap by viewModel.parsedJsonBySms.collectAsState()
    val scamMap by viewModel.scamResultBySms.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val batchStatus by viewModel.batchProcessingStatus.collectAsState()
    val smsImportStatus by viewModel.smsImportStatus.collectAsState()

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SMS Analysis") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Import Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Permission Request button
                RequestSmsAndAudioPermissionButton {
                    viewModel.importSms(context)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Direct import (if permissions already granted)
                Button(
                    onClick = { viewModel.importSms(context) },
                    enabled = !isImportingSms
                ) {
                    Text(if (isImportingSms) "Importing..." else "Refresh SMS")
                }
            }

            Divider()

            // Top Status area: import status + batch status + progress bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = smsImportStatus, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = batchStatus, style = MaterialTheme.typography.bodySmall)
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

            // Processing Controls (centered) — single button for process+scam
            if (smsList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { viewModel.processAllMessages() },
                        enabled = processingProgress == 0
                    ) {
                        Text("Process All & Check Scams")
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No SMS imported yet.\nTap 'Grant Permissions' to start.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Divider()

            // SMS list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(smsList) { sms ->
                    SmsCardWithLiveStatus(
                        sms = sms,
                        parsedJson = parsedMap[sms.id],
                        scamStatus = scamMap[sms.id],
                        onParse = { viewModel.parseSms(sms.id, sms.body ?: "") },
                        onCheckScam = { viewModel.detectScam(sms.id, sms.body ?: "") },
                        onSaveEdited = { newText -> viewModel.forceSaveParsedJson(sms.id, newText) }
                    )
                }
            }
        }
    }
}

@Composable
fun SmsCardWithLiveStatus(
    sms: RawSms,
    parsedJson: String?,
    scamStatus: String?,
    onParse: () -> Unit,
    onCheckScam: () -> Unit,
    onSaveEdited: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ---------- Header ----------
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

            // ---------- SMS Body ----------
            Text(
                text = sms.body ?: "",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ---------- Parsed JSON ----------
            if (!parsedJson.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = parsedJson,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ---------- Scam Status (Own Row) ----------
            when {
                scamStatus == null -> {
                    Text("Scam Check: Not checked", style = MaterialTheme.typography.labelMedium)
                }
                scamStatus.equals("Checking...", ignoreCase = true) -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scam Check: Checking...", style = MaterialTheme.typography.labelMedium)
                    }
                }
                scamStatus.equals("likely_scam", ignoreCase = true) -> {
                    Text(
                        "Scam Check: Likely Scam",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                scamStatus.equals("safe", ignoreCase = true) -> {
                    Text(
                        "Scam Check: Safe",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                scamStatus.equals("uncertain", ignoreCase = true) -> {
                    Text("Scam Check: Uncertain", style = MaterialTheme.typography.labelMedium)
                }
                scamStatus.equals("error", ignoreCase = true) -> {
                    Text(
                        "Scam Check: Error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                else -> {
                    Text("Scam Check: $scamStatus", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---------- Actions (ALWAYS RIGHT-ALIGNED) ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onParse) { Text("Parse") }
                TextButton(onClick = onCheckScam) { Text("Check Scam") }
                TextButton(onClick = { showEditDialog = true }) { Text("Edit") }
            }
        }
    }

    // ---------- Edit Dialog ----------
    if (showEditDialog) {
        EditParsedDialog(
            initialText = parsedJson ?: "",
            onDismiss = { showEditDialog = false },
            onSave = { newText ->
                onSaveEdited(newText)
                showEditDialog = false
                android.widget.Toast.makeText(context, "Saved", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }
}


/** Permission request composable (keeps your original semantics) */
@Composable
fun RequestSmsAndAudioPermissionButton(onPermissionGranted: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.READ_SMS] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        if (smsGranted) {
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

/** Edit dialog reused from your original file */
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
