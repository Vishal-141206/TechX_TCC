package com.runanywhere.startup_hackathon20

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * Smart Financial Insights generator
 */
object FinancialInsights {

    data class Insight(
        val title: String,
        val message: String,
        val type: InsightType,
        val icon: ImageVector,
        val actionLabel: String? = null
    )

    enum class InsightType {
        POSITIVE, WARNING, INFO, CRITICAL
    }

    /**
     * Generate personalized insights based on transaction data
     */
    fun generateInsights(
        parsedTransactions: Map<String, String>,
        cashFlowPrediction: CashFlowPrediction?
    ): List<Insight> {
        val insights = mutableListOf<Insight>()

        if (parsedTransactions.isEmpty()) {
            return listOf(
                Insight(
                    title = "Get Started",
                    message = "Import your SMS messages to start tracking your finances automatically",
                    type = InsightType.INFO,
                    icon = Icons.Default.Analytics,
                    actionLabel = "Import SMS"
                )
            )
        }

        // Calculate totals
        var totalDebit = 0.0
        var totalCredit = 0.0
        var transactionCount = 0
        val categories = mutableMapOf<String, Double>()

        parsedTransactions.values.forEach { jsonStr ->
            try {
                val json = JSONObject(jsonStr)
                val amount = json.optDouble("amount", 0.0)
                val type = json.optString("type", "")
                val merchant = json.optString("merchant", "Unknown")

                transactionCount++

                when (type) {
                    "debit" -> totalDebit += amount
                    "credit" -> totalCredit += amount
                }

                // Categorize
                val category = categorizeTransaction(merchant)
                categories[category] = categories.getOrDefault(category, 0.0) + amount
            } catch (e: Exception) {
                // Skip invalid JSON
            }
        }

        // Insight 1: Spending Overview
        if (totalDebit > 0) {
            val avgDaily = totalDebit / 30
            insights.add(
                Insight(
                    title = "Daily Spending Average",
                    message = "You're spending about ₹${
                        String.format(
                            "%.0f",
                            avgDaily
                        )
                    } per day on average",
                    type = if (cashFlowPrediction != null && cashFlowPrediction.netCashFlow < 0)
                        InsightType.WARNING else InsightType.INFO,
                    icon = Icons.Default.MonetizationOn
                )
            )
        }

        // Insight 2: Top Spending Category
        val topCategory = categories.maxByOrNull { it.value }
        if (topCategory != null && topCategory.value > 0) {
            val percentage = (topCategory.value / totalDebit * 100).toInt()
            insights.add(
                Insight(
                    title = "Top Spending: ${topCategory.key}",
                    message = "₹${
                        String.format(
                            "%.0f",
                            topCategory.value
                        )
                    } ($percentage% of expenses)",
                    type = if (percentage > 50) InsightType.WARNING else InsightType.INFO,
                    icon = getCategoryIcon(topCategory.key),
                    actionLabel = "View Details"
                )
            )
        }

        // Insight 3: Savings Rate
        if (totalCredit > 0 && totalDebit > 0) {
            val savingsRate = ((totalCredit - totalDebit) / totalCredit * 100).toInt()
            if (savingsRate > 0) {
                insights.add(
                    Insight(
                        title = "Great Savings!",
                        message = "You're saving $savingsRate% of your income. Keep it up!",
                        type = InsightType.POSITIVE,
                        icon = Icons.Default.TrendingUp
                    )
                )
            } else {
                insights.add(
                    Insight(
                        title = "Spending Alert",
                        message = "You're spending ${-savingsRate}% more than your income",
                        type = InsightType.CRITICAL,
                        icon = Icons.Default.Warning,
                        actionLabel = "Create Budget"
                    )
                )
            }
        }

        // Insight 4: Transaction Activity
        if (transactionCount > 30) {
            insights.add(
                Insight(
                    title = "High Activity",
                    message = "$transactionCount transactions this month. You're actively managing your money!",
                    type = InsightType.INFO,
                    icon = Icons.Default.Analytics
                )
            )
        }

        // Insight 5: Cash Flow Prediction
        if (cashFlowPrediction != null) {
            if (cashFlowPrediction.netCashFlow > 0) {
                insights.add(
                    Insight(
                        title = "Positive Forecast",
                        message = "Projected surplus of ₹${
                            String.format(
                                "%.0f",
                                cashFlowPrediction.netCashFlow
                            )
                        } next month",
                        type = InsightType.POSITIVE,
                        icon = Icons.Default.TrendingUp
                    )
                )
            } else {
                insights.add(
                    Insight(
                        title = "Budget Warning",
                        message = "Projected deficit of ₹${
                            String.format(
                                "%.0f",
                                -cashFlowPrediction.netCashFlow
                            )
                        } next month",
                        type = InsightType.WARNING,
                        icon = Icons.Default.TrendingDown,
                        actionLabel = "Plan Budget"
                    )
                )
            }
        }

        return insights
    }

    private fun categorizeTransaction(merchant: String): String {
        val lowerMerchant = merchant.lowercase()
        return when {
            lowerMerchant.contains("amazon") || lowerMerchant.contains("flipkart") ||
                    lowerMerchant.contains("shopping") -> "Shopping"

            lowerMerchant.contains("zomato") || lowerMerchant.contains("swiggy") ||
                    lowerMerchant.contains("restaurant") || lowerMerchant.contains("food") -> "Food"

            lowerMerchant.contains("uber") || lowerMerchant.contains("ola") ||
                    lowerMerchant.contains("metro") || lowerMerchant.contains("petrol") -> "Transport"

            lowerMerchant.contains("netflix") || lowerMerchant.contains("spotify") ||
                    lowerMerchant.contains("prime") -> "Entertainment"

            lowerMerchant.contains("electricity") || lowerMerchant.contains("gas") ||
                    lowerMerchant.contains("water") || lowerMerchant.contains("internet") -> "Utilities"

            lowerMerchant.contains("hospital") || lowerMerchant.contains("medical") ||
                    lowerMerchant.contains("pharmacy") -> "Healthcare"

            else -> "Other"
        }
    }

    private fun getCategoryIcon(category: String): ImageVector {
        return when (category) {
            "Shopping" -> Icons.Default.ShoppingCart
            "Food" -> Icons.Default.Restaurant
            "Transport" -> Icons.Default.DirectionsCar
            "Entertainment" -> Icons.Default.Movie
            "Utilities" -> Icons.Default.Bolt
            "Healthcare" -> Icons.Default.LocalHospital
            else -> Icons.Default.Category
        }
    }

    fun getColorForType(type: InsightType): Color {
        return when (type) {
            InsightType.POSITIVE -> Color(0xFF4CAF50)
            InsightType.WARNING -> Color(0xFFFF9800)
            InsightType.CRITICAL -> Color(0xFFEF5350)
            InsightType.INFO -> Color(0xFF2196F3)
        }
    }
}

/**
 * Animated Insight Card
 */
@Composable
fun InsightCard(
    insight: FinancialInsights.Insight,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val color = FinancialInsights.getColorForType(insight.type)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = insight.icon,
                    contentDescription = insight.title,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                insight.actionLabel?.let { label ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onClick?.invoke() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        }
    }
}
