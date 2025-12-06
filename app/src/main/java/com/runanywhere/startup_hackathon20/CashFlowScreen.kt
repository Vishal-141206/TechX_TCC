package com.runanywhere.startup_hackathon20

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(viewModel: ChatViewModel = viewModel()) {
    val cashFlowPrediction by viewModel.cashFlowPrediction.collectAsState()
    val isPredicting by viewModel.isPredicting.collectAsState()
    val predictionStatus by viewModel.predictionStatus.collectAsState()
    val predictionProgress by viewModel.predictionProgress.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val locale = Locale.getDefault()

    // Auto-predict on first load if no data exists
    LaunchedEffect(Unit) {
        if (cashFlowPrediction == null && !isPredicting) {
            viewModel.predictCashFlow()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Cash Flow",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "30-Day Financial Forecast",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Voice summary button
                    if (cashFlowPrediction != null) {
                        IconButton(
                            onClick = { viewModel.speakCashFlowSummary() },
                            enabled = !isSpeaking
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Voice Summary",
                                tint = if (isSpeaking)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!isPredicting && cashFlowPrediction != null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.predictCashFlow() },
                    icon = {
                        Icon(Icons.Default.Refresh, "Refresh")
                    },
                    text = { Text("Refresh") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isPredicting -> PredictingView(predictionStatus, predictionProgress)
                cashFlowPrediction == null -> EmptyPredictionView { viewModel.predictCashFlow() }
                else -> cashFlowPrediction?.let { prediction ->
                    PredictionResultsView(prediction, locale)
                }
            }
        }
    }
}

@Composable
private fun PredictingView(status: String, progress: Float) {
    // Animated pulse effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated circular progress with gradient ring
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background ring
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(160.dp),
                strokeWidth = 12.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                trackColor = Color.Transparent
            )

            // Animated progress ring
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale),
                strokeWidth = 12.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )

            // Center content
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Analyzing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "🔮 AI Analysis in Progress",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = status.ifBlank { "Processing your transaction data..." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Progress stages with animations
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ProgressStage(
                "📊 Parsing transactions",
                progress >= 0.25f,
                progress >= 0.25f && progress < 0.5f
            )
            ProgressStage(
                "🔍 Analyzing patterns",
                progress >= 0.5f,
                progress >= 0.5f && progress < 0.75f
            )
            ProgressStage(
                "📈 Calculating trends",
                progress >= 0.75f,
                progress >= 0.75f && progress < 0.9f
            )
            ProgressStage("✨ Generating forecast", progress >= 0.9f, progress >= 0.9f)
        }
    }
}

@Composable
private fun ProgressStage(text: String, completed: Boolean, active: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "active")
    val activeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "activeAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = when {
                        completed -> Color(0xFF4CAF50)
                        active -> MaterialTheme.colorScheme.primary.copy(alpha = activeAlpha)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (completed) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else if (active) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (completed || active) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                completed -> Color(0xFF4CAF50)
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun EmptyPredictionView(onPredict: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Gradient icon background
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF667EEA).copy(alpha = 0.2f),
                            Color(0xFF764BA2).copy(alpha = 0.2f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(70.dp),
                tint = Color(0xFF667EEA)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "No Forecast Available",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Analyze your SMS transactions first, then we'll generate your personalized financial forecast",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onPredict,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF667EEA)
            )
        ) {
            Icon(Icons.Default.Analytics, null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text("Generate Forecast", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PredictionResultsView(prediction: CashFlowPrediction, locale: Locale) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Premium Hero Card (Revolut/Cash App style)
        item {
            PremiumHeroCard(prediction, locale)
        }

        // Money Flow Row (Income & Expenses side by side)
        item {
            MoneyFlowRow(prediction, locale)
        }

        // Financial Health Score Card - Moved above Quick Insights
        item {
            FinancialHealthCard(prediction)
        }

        // Quick Insights (Horizontal scroll) - Now with better layout
        item {
            QuickInsightsRow(prediction, locale)
        }

        // AI Recommendation Card
        item {
            AIRecommendationCard(prediction.recommendation)
        }

        // Top Spending Categories Section
        if (prediction.topCategories.isNotEmpty()) {
            item {
                Text(
                    text = "💸 Spending Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(prediction.topCategories.entries.sortedByDescending { it.value }
                .take(5)) { entry ->
                SpendingCategoryCard(entry.key, entry.value, prediction.totalExpenses, locale)
            }
        }

        // Alerts Section
        if (prediction.riskyDays.isNotEmpty()) {
            item {
                Text(
                    text = "⚠️ Alerts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(prediction.riskyDays.take(3)) { day ->
                AlertCard(day)
            }
        }

        // Bottom spacing for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PremiumHeroCard(prediction: CashFlowPrediction, locale: Locale) {
    val isPositive = prediction.netCashFlow >= 0

    // Animated value counter
    var animatedValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(prediction.netCashFlow) {
        animate(
            initialValue = 0f,
            targetValue = prediction.netCashFlow.toFloat(),
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animatedValue = value
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isPositive) {
                            listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                        } else {
                            listOf(Color(0xFFf093fb), Color(0xFFf5576c))
                        }
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (isPositive) "Saving Mode" else "Spending Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "30 Days",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Main amount
                Text(
                    text = "Net Cash Flow",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = formatCurrency(animatedValue.toDouble(), locale),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 32.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Predicted Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatCurrencyClean(prediction.predictedBalance, locale),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyFlowRow(prediction: CashFlowPrediction, locale: Locale) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Income Card
        MoneyFlowCard(
            modifier = Modifier.weight(1f),
            title = "Income",
            amount = prediction.totalIncome,
            locale = locale,
            icon = Icons.Default.ArrowUpward,
            color = Color(0xFF00C853),
            isPositive = true
        )

        // Expenses Card
        MoneyFlowCard(
            modifier = Modifier.weight(1f),
            title = "Expenses",
            amount = prediction.totalExpenses,
            locale = locale,
            icon = Icons.Default.ArrowDownward,
            color = Color(0xFFFF5252),
            isPositive = false
        )
    }
}

@Composable
private fun MoneyFlowCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Double,
    locale: Locale,
    icon: ImageVector,
    color: Color,
    isPositive: Boolean
) {
    // Animated counter
    var animatedValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(amount) {
        animate(
            initialValue = 0f,
            targetValue = amount.toFloat(),
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animatedValue = value
        }
    }

    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = formatCurrencyClean(animatedValue.toDouble(), locale),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun QuickInsightsRow(prediction: CashFlowPrediction, locale: Locale) {
    val savingsRate = if (prediction.totalIncome > 0) {
        ((prediction.netCashFlow / prediction.totalIncome) * 100).toInt()
    } else 0

    val avgDailySpend = prediction.totalExpenses / 30

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Key Metrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            item {
                FintechMetricCard(
                    label = "Savings Rate",
                    value = "${if (savingsRate >= 0) savingsRate else 0}%",
                    color = if (savingsRate >= 20) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    icon = "💰"
                )
            }
            item {
                FintechMetricCard(
                    label = "Daily Spend",
                    value = "₹${String.format(locale, "%,.0f", avgDailySpend)}",
                    color = Color(0xFF2196F3),
                    icon = "📅"
                )
            }
            item {
                FintechMetricCard(
                    label = "Categories",
                    value = "${prediction.topCategories.size}",
                    color = Color(0xFF9C27B0),
                    icon = "📊"
                )
            }
            item {
                FintechMetricCard(
                    label = "Alerts",
                    value = "${prediction.riskyDays.size}",
                    color = if (prediction.riskyDays.isEmpty()) Color(0xFF4CAF50) else Color(
                        0xFFFF9800
                    ),
                    icon = "⚠️"
                )
            }
        }
    }
}

@Composable
private fun FintechMetricCard(
    label: String,
    value: String,
    color: Color,
    icon: String
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.08f),
                            color.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row - Icon and label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = icon,
                        fontSize = 20.sp
                    )
                }

                // Bottom - Value
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 24.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FinancialHealthCard(prediction: CashFlowPrediction) {
    val savingsRate = if (prediction.totalIncome > 0) {
        ((prediction.netCashFlow / prediction.totalIncome) * 100)
    } else 0.0

    val (healthLabel, healthColor, healthEmoji, healthMessage) = when {
        savingsRate >= 30 -> listOf("Excellent", Color(0xFF4CAF50), "🏆", "Savings champion!")
        savingsRate >= 20 -> listOf("Good", Color(0xFF66BB6A), "💪", "Keep it up!")
        savingsRate >= 10 -> listOf("Fair", Color(0xFFFFA726), "📈", "Room to improve")
        savingsRate >= 0 -> listOf("Needs Work", Color(0xFFEF5350), "🎯", "Let's optimize")
        else -> listOf("Critical", Color(0xFFD32F2F), "⚠️", "Action needed!")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = (healthColor as Color).copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = healthEmoji as String,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Financial Health",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = healthLabel as String,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = healthColor,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = healthMessage as String,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(healthColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${savingsRate.toInt().coerceAtLeast(0)}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = healthColor,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Rate",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = healthColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AIRecommendationCard(recommendation: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF11998e), Color(0xFF38ef7d))
                    )
                )
                .padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "💡", fontSize = 16.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Tip",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.95f),
                        lineHeight = 18.sp,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendingCategoryCard(
    category: String,
    amount: Double,
    totalExpenses: Double,
    locale: Locale
) {
    val percentage = if (totalExpenses > 0) ((amount / totalExpenses) * 100).toInt() else 0
    val categoryEmoji = getCategoryEmoji(category)
    val categoryColor = getCategoryColor(category)

    // Animated progress
    var animatedProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(percentage) {
        animate(
            initialValue = 0f,
            targetValue = percentage / 100f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animatedProgress = value
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(categoryColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = categoryEmoji, fontSize = 14.sp)
                    }

                    Column {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = formatCurrencyClean(amount, locale),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = categoryColor,
                trackColor = categoryColor.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun AlertCard(day: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFF9800).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚠️", fontSize = 16.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "High spending detected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// Helper Functions
private fun formatCurrency(amount: Double, locale: Locale): String {
    val absAmount = abs(amount)
    val sign = if (amount < 0) "-" else "+"
    return "$sign₹${String.format(locale, "%,.0f", absAmount)}"
}

private fun formatCurrencyClean(amount: Double, locale: Locale): String {
    return "₹${String.format(locale, "%,.0f", abs(amount))}"
}

private fun getCategoryColor(category: String): Color {
    return when {
        category.contains("Food", ignoreCase = true) -> Color(0xFFFF9800)
        category.contains("Shopping", ignoreCase = true) -> Color(0xFFE91E63)
        category.contains("Transport", ignoreCase = true) -> Color(0xFF2196F3)
        category.contains("Entertainment", ignoreCase = true) -> Color(0xFF9C27B0)
        category.contains("Utilities", ignoreCase = true) -> Color(0xFF607D8B)
        category.contains("Healthcare", ignoreCase = true) -> Color(0xFFF44336)
        else -> Color(0xFF4CAF50)
    }
}

private fun getCategoryEmoji(category: String): String {
    return when {
        category.contains("Food", ignoreCase = true) -> "🍔"
        category.contains("Shopping", ignoreCase = true) -> "🛍️"
        category.contains("Transport", ignoreCase = true) -> "🚗"
        category.contains("Entertainment", ignoreCase = true) -> "🎬"
        category.contains("Utilities", ignoreCase = true) -> "💡"
        category.contains("Healthcare", ignoreCase = true) -> "🏥"
        category.contains("Transfer", ignoreCase = true) -> "💸"
        category.contains("Investment", ignoreCase = true) -> "📈"
        else -> "💳"
    }
}
