package com.runanywhere.startup_hackathon20

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.runanywhere.startup_hackathon20.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FintechDashboard(navController: NavController, viewModel: ChatViewModel) {
    val smsList by viewModel.smsList.collectAsState()
    val parsedMap by viewModel.parsedJsonBySms.collectAsState()
    val scamMap by viewModel.scamResultBySms.collectAsState()
    val cashFlowPrediction by viewModel.cashFlowPrediction.collectAsState()

    val totalSms = remember(smsList) { smsList.size }
    val totalParsed = remember(parsedMap) { parsedMap.size }
    val potentialScams = remember(scamMap) {
        scamMap.values.count { it.contains("likely_scam", ignoreCase = true) }
    }

    // Calculate financial summary
    val financialSummary = remember(parsedMap) {
        calculateFinancialSummary(parsedMap)
    }

    Scaffold(
        topBar = {
            PremiumTopBar()
        },
        containerColor = Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Premium Balance Card (Hero Section)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                PremiumBalanceCard(
                    balance = financialSummary.netBalance,
                    income = financialSummary.totalIncome,
                    expenses = financialSummary.totalExpenses,
                    onCardClick = { navController.navigate("cash_flow") }
                )
            }

            // Quick Actions Row
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = OnBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                QuickActionsRow(navController)
            }

            // Financial Insights
            if (parsedMap.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Financial Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = OnBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    InsightsSection(
                        parsedMap = parsedMap,
                        cashFlow = cashFlowPrediction,
                        navController = navController
                    )
                }
            }

            // Recent Activity
            if (totalSms > 0) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Activity Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = OnBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    ActivityStatsCard(
                        totalTransactions = totalSms,
                        parsedCount = totalParsed,
                        scamCount = potentialScams
                    )
                }
            }

            // Features Grid
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Explore Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = OnBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                FeaturesGrid(navController)
            }
        }
    }
}

@Composable
private fun PremiumTopBar() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lock icon for security
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "Finance AI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Private • On-Device",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Profile Icon with encrypted indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PrimaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumBalanceCard(
    balance: Double,
    income: Double,
    expenses: Double,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(200.dp)
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(CardGradientStart, CardGradientEnd)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Net Balance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Icon(
                        imageVector = if (balance >= 0) Icons.Default.TrendingUp
                        else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Balance Amount
                Text(
                    text = "₹${String.format(Locale.getDefault(), "%,.0f", balance)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Income & Expenses Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "₹${String.format(Locale.getDefault(), "%,.0f", income)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Expenses",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "₹${String.format(Locale.getDefault(), "%,.0f", expenses)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(navController: NavController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(getQuickActions()) { action ->
            QuickActionButton(
                icon = action.icon,
                label = action.label,
                color = action.color,
                onClick = {
                    when (action.route) {
                        "chat" -> navController.navigate("chat")
                        "analysis" -> navController.navigate("analysis")
                        "cash_flow" -> navController.navigate("cash_flow")
                        "voice" -> navController.navigate("chat?start_voice=true")
                    }
                }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InsightsSection(
    parsedMap: Map<String, String>,
    cashFlow: CashFlowPrediction?,
    navController: NavController
) {
    val insights = remember(parsedMap, cashFlow) {
        FinancialInsights.generateInsights(parsedMap, cashFlow)
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(insights.take(5)) { insight ->
            ModernInsightCard(
                insight = insight,
                onClick = {
                    when (insight.actionLabel) {
                        "Import SMS" -> navController.navigate("analysis")
                        "View Details", "Create Budget", "Plan Budget" ->
                            navController.navigate("cash_flow")

                        else -> {}
                    }
                }
            )
        }
    }
}

@Composable
private fun ModernInsightCard(
    insight: FinancialInsights.Insight,
    onClick: () -> Unit
) {
    val color = FinancialInsights.getColorForType(insight.type)

    Card(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = insight.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ActivityStatsCard(
    totalTransactions: Int,
    parsedCount: Int,
    scamCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn(
                    value = totalTransactions.toString(),
                    label = "Transactions",
                    color = Primary
                )
                StatColumn(
                    value = parsedCount.toString(),
                    label = "Analyzed",
                    color = Secondary
                )
                StatColumn(
                    value = scamCount.toString(),
                    label = "Alerts",
                    color = if (scamCount > 0) Error else Success
                )
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )
    }
}

@Composable
private fun FeaturesGrid(navController: NavController) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard(
                title = "AI Assistant",
                description = "Smart finance chat",
                icon = Icons.Default.Psychology,
                color = PrimaryContainer,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("chat") }
            )
            FeatureCard(
                title = "Voice Coach",
                description = "Speak to analyze",
                icon = Icons.Default.RecordVoiceOver,
                color = SecondaryContainer,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("chat?start_voice=true") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard(
                title = "Cash Flow",
                description = "30-day forecast",
                icon = Icons.Default.AccountBalance,
                color = TertiaryContainer,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("cash_flow") }
            )
            FeatureCard(
                title = "Scan SMS",
                description = "Auto-detect money",
                icon = Icons.Default.DocumentScanner,
                color = InfoContainer,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate("analysis") }
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class QuickAction(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val route: String
)

private fun getQuickActions() = listOf(
    QuickAction(Icons.Default.Psychology, "AI Chat", Color(0xFF667EEA), "chat"),
    QuickAction(Icons.Default.RecordVoiceOver, "Voice Coach", Color(0xFF9C27B0), "voice"),
    QuickAction(Icons.Default.Analytics, "Analytics", Color(0xFF00C853), "analysis"),
    QuickAction(Icons.Default.TrendingUp, "Forecast", Color(0xFFFF9800), "cash_flow")
)

private data class FinancialSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val netBalance: Double
)

private fun calculateFinancialSummary(parsedMap: Map<String, String>): FinancialSummary {
    var income = 0.0
    var expenses = 0.0

    parsedMap.values.forEach { jsonStr ->
        try {
            val json = org.json.JSONObject(jsonStr)
            val amount = json.optDouble("amount", 0.0)
            val type = json.optString("type", "")

            when (type) {
                "credit" -> income += amount
                "debit" -> expenses += amount
            }
        } catch (e: Exception) {
            // Skip invalid JSON
        }
    }

    return FinancialSummary(income, expenses, income - expenses)
}
