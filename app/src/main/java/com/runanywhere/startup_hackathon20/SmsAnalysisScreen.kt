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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsAnalysisScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val smsList by viewModel.smsList.collectAsState()
    val isImportingSms by viewModel.isImportingSms.collectAsState()
    val parsedMap by viewModel.parsedJsonBySms.collectAsState()
    val scamMap by viewModel.scamResultBySms.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Analysis") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Import Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Permission Request Button
                RequestSmsAndAudioPermissionButton {
                    viewModel.importSms(context)
                }

                // Direct Import Button (if permissions already granted)
                Button(
                    onClick = { viewModel.importSms(context) },
                    enabled = !isImportingSms
                ) {
                    Text(if (isImportingSms) "Importing..." else "Refresh SMS")
                }
            }

            HorizontalDivider()

            // Processing Controls
            if (smsList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { viewModel.processAllMessages() },
                        enabled = true
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

            // SMS List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(smsList) { sms ->
                    SmsCard(sms, viewModel, parsedMap[sms.id], scamMap[sms.id])
                }
            }
        }
    }
}

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
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (!scamStatus.isNullOrBlank()) {
                val isScam = scamStatus.contains("likely", true)
                Text(
                    text = "Scam Check: $scamStatus",
                    color = if (isScam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
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
