package com.runanywhere.startup_hackathon20

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.Brush
import java.util.Locale
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(navController: NavController, viewModel: ChatViewModel) {

    // Collect state from ViewModel
    val smsList by viewModel.smsList.collectAsState()
    val parsedMap by viewModel.parsedJsonBySms.collectAsState()
    val scamMap by viewModel.scamResultBySms.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val cashFlowPrediction by viewModel.cashFlowPrediction.collectAsState()

    val context = LocalContext.current

    // Derived stats (remembered)
    val totalSms = remember(smsList) { smsList.size }
    val totalParsed = remember(parsedMap) { parsedMap.size }
    val potentialScams =
        remember(scamMap) { scamMap.values.count { it.contains("likely_scam", ignoreCase = true) } }

    // Generate smart insights
    val insights = remember(parsedMap, cashFlowPrediction) {
        FinancialInsights.generateInsights(parsedMap, cashFlowPrediction)
    }

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

            // Smart Insights Section
            if (insights.isNotEmpty()) {
                item {
                    Text(
                        text = "💡 Smart Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(insights.take(3)) { insight ->
                    InsightCard(
                        insight = insight,
                        onClick = {
                            when (insight.actionLabel) {
                                "Import SMS" -> navController.navigate("analysis")
                                "View Details", "Create Budget", "Plan Budget" -> navController.navigate(
                                    "cash_flow"
                                )

                                else -> {}
                            }
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Cash Flow Quick Summary (if available)
            cashFlowPrediction?.let { prediction ->
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "📊 Quick Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                item {
                    CashFlowQuickSummary(
                        prediction = prediction,
                        onClick = { navController.navigate("cash_flow") }
                    )
                }
            }

            // Features Section Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🚀 Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
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
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    onClick = {
                        // If RECORD_AUDIO is already granted -> navigate to chat in voice mode.
                        // Otherwise ask MainActivity to request it (the Activity will show the system dialog).
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            navController.navigate("chat?start_voice=true")
                        } else {
                            // Ask the Activity to request permission via the registered launcher.
                            (context as? MainActivity)?.requestAudioPermissionIfNeeded()
                        }
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
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // Navigation arrow (keeps consistent look)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Navigate",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun CashFlowQuickSummary(
    prediction: CashFlowPrediction,
    onClick: () -> Unit
) {
    val isPositive = prediction.netCashFlow >= 0
    val locale = Locale.getDefault()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isPositive) Color(0xFF4CAF50).copy(alpha = 0.15f)
            else Color(0xFFEF5350).copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isPositive) Color(0xFF4CAF50).copy(alpha = 0.2f)
                                else Color(0xFFEF5350).copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.TrendingUp
                            else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isPositive) Color(0xFF4CAF50) else Color(0xFFEF5350),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Cash Flow Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "30-day forecast",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "View Details",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                QuickMetric(
                    label = "Income",
                    value = "₹${String.format(locale, "%,.0f", prediction.totalIncome)}",
                    color = Color(0xFF4CAF50)
                )
                QuickMetric(
                    label = "Expenses",
                    value = "₹${String.format(locale, "%,.0f", prediction.totalExpenses)}",
                    color = Color(0xFFEF5350)
                )
                QuickMetric(
                    label = "Net Flow",
                    value = "${if (isPositive) "+" else ""}₹${
                        String.format(
                            locale,
                            "%,.0f",
                            kotlin.math.abs(prediction.netCashFlow)
                        )
                    }",
                    color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFEF5350)
                )
            }
        }
    }
}

@Composable
private fun QuickMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
