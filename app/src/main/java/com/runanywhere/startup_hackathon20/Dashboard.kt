package com.runanywhere.startup_hackathon20

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VoiceChat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(navController: NavController, viewModel: ChatViewModel) {

    // Collect state from ViewModel
    val smsCount by viewModel.smsList.collectAsState()
    val parsedCount by viewModel.parsedJsonBySms.collectAsState()
    val scamCount by viewModel.scamResultBySms.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()

    // Calculate statistics
    val totalSms = smsCount.size
    val totalParsed = parsedCount.size
    val potentialScams = scamCount.values.count { it.contains("likely_scam", ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Finance AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "100% On-Device • Privacy First",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Stats Overview
            item {
                StatsOverview(
                    totalSms = totalSms,
                    totalParsed = totalParsed,
                    potentialScams = potentialScams,
                    modelStatus = modelStatus
                )
            }

            // AI Chat (pure chatbot)
            item {
                DashboardCard(
                    title = "AI Chat Assistant",
                    description = "Chat with your financial assistant (no SMS required).",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = { navController.navigate("chat") }
                )
            }

            // SMS Analysis & Scam Detection (Combined)
            item {
                DashboardCard(
                    title = "SMS Analysis & Scam Check",
                    description = "Import SMS to analyze transactions and detect potential scams/fraud.",
                    icon = Icons.Default.Analytics,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { navController.navigate("analysis") }
                )
            }

            // Cash Flow Predictions
            item {
                DashboardCard(
                    title = "Cash Flow Prediction",
                    description = "Forecast income, expenses and projected balance.",
                    icon = Icons.Default.MonetizationOn,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = { navController.navigate("cash_flow") }
                )
            }

            // Voice Finance Coach
            item {
                DashboardCard(
                    title = "Voice Finance Coach",
                    description = "Get voice summaries of your finances and insights.",
                    icon = Icons.Default.VoiceChat,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    onClick = {
                        // Initialize voice if needed and navigate
                        navController.navigate("chat")
                    }
                )
            }

            // Model Management Screen
            item {
                DashboardCard(
                    title = "AI Model Management",
                    description = "Download and load on-device AI models.",
                    icon = Icons.Default.Settings,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { navController.navigate("models") }
                )
            }
        }
    }
}

@Composable
fun StatsOverview(
    totalSms: Int,
    totalParsed: Int,
    potentialScams: Int,
    modelStatus: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "SMS",
                    value = totalSms.toString(),
                    color = MaterialTheme.colorScheme.primary
                )

                StatItem(
                    label = "Parsed",
                    value = totalParsed.toString(),
                    color = MaterialTheme.colorScheme.secondary
                )

                StatItem(
                    label = "Scams",
                    value = potentialScams.toString(),
                    color = if (potentialScams > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Model Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Model Status",
                    modifier = Modifier.size(16.dp),
                    tint = if (modelStatus.contains("Ready", ignoreCase = true))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = modelStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DashboardCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 2
                )
            }

            // Navigation arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat, // Using chat icon as arrow
                contentDescription = "Navigate",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}