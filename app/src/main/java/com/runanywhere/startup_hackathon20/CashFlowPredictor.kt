package com.runanywhere.startup_hackathon20

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Cash Flow Prediction Engine
 * Analyzes transaction patterns and predicts future cash flows
 */

data class CashFlowPrediction(
    val nextMonthIncome: Double,
    val nextMonthExpenses: Double,
    val predictedBalance: Double,
    val recurringTransactions: List<RecurringTransaction>,
    val categoryBreakdown: Map<String, CategorySpend>,
    val insights: List<String>,
    val confidence: String // "High", "Medium", "Low"
)

data class RecurringTransaction(
    val merchant: String,
    val averageAmount: Double,
    val frequency: String, // "Monthly", "Weekly", "Daily"
    val category: String,
    val nextExpectedDate: String?,
    val confidence: Int // 0-100
)

data class CategorySpend(
    val category: String,
    val totalSpent: Double,
    val transactionCount: Int,
    val averagePerTransaction: Double,
    val percentageOfTotal: Double,
    val trend: String // "Increasing", "Stable", "Decreasing"
)

class CashFlowPredictor {

    /**
     * Main prediction function
     * Analyzes parsed SMS transactions and generates predictions
     */
    suspend fun predictCashFlow(
        parsedJsonMap: Map<String, String>,
        smsListMap: Map<String, RawSms>
    ): CashFlowPrediction = withContext(Dispatchers.Default) {

        // Parse all transactions
        val transactions = parseTransactions(parsedJsonMap, smsListMap)
        
        if (transactions.isEmpty()) {
            return@withContext CashFlowPrediction(
                nextMonthIncome = 0.0,
                nextMonthExpenses = 0.0,
                predictedBalance = 0.0,
                recurringTransactions = emptyList(),
                categoryBreakdown = emptyMap(),
                insights = listOf("Not enough transaction data to make predictions"),
                confidence = "Low"
            )
        }

        // Calculate metrics
        val totalIncome = transactions.filter { it.type == "credit" }.sumOf { it.amount }
        val totalExpenses = transactions.filter { it.type == "debit" }.sumOf { it.amount }
        
        // Analyze patterns
        val recurringTransactions = detectRecurringTransactions(transactions)
        val categoryBreakdown = analyzeCategorySpending(transactions)
        
        // Predict next month
        val predictedIncome = predictNextMonthIncome(transactions)
        val predictedExpenses = predictNextMonthExpenses(transactions, recurringTransactions)
        val predictedBalance = predictedIncome - predictedExpenses
        
        // Generate insights
        val insights = generateInsights(
            totalIncome,
            totalExpenses,
            predictedBalance,
            recurringTransactions,
            categoryBreakdown
        )
        
        // Calculate confidence based on data quality
        val confidence = calculateConfidence(transactions)

        return@withContext CashFlowPrediction(
            nextMonthIncome = predictedIncome,
            nextMonthExpenses = predictedExpenses,
            predictedBalance = predictedBalance,
            recurringTransactions = recurringTransactions,
            categoryBreakdown = categoryBreakdown,
            insights = insights,
            confidence = confidence
        )
    }

    private data class ParsedTransaction(
        val amount: Double,
        val merchant: String?,
        val category: String?,
        val type: String, // debit/credit/info
        val date: Date?,
        val balance: Double?
    )

    private fun parseTransactions(
        parsedJsonMap: Map<String, String>,
        smsListMap: Map<String, RawSms>
    ): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        
        for ((smsId, jsonString) in parsedJsonMap) {
            if (jsonString == "Parsing..." || jsonString.isBlank()) continue
            
            try {
                val json = JSONObject(jsonString)
                val amount = json.optDouble("amount", 0.0)
                if (amount <= 0) continue
                
                val type = json.optString("type", "info")
                if (type == "info") continue
                
                val merchant = json.optString("merchant").takeIf { it.isNotEmpty() }
                val category = json.optString("category", "Other")
                val balance = json.optDouble("balance", Double.NaN)
                
                // Parse date
                val dateStr = json.optString("date").takeIf { it.isNotEmpty() }
                val date = parseDate(dateStr) ?: smsListMap[smsId]?.date?.let { Date(it) }
                
                transactions.add(
                    ParsedTransaction(
                        amount = amount,
                        merchant = merchant,
                        category = category,
                        type = type,
                        date = date,
                        balance = if (balance.isNaN()) null else balance
                    )
                )
            } catch (_: Exception) {
                continue
            }
        }
        
        return transactions.sortedByDescending { it.date }
    }

    private fun parseDate(dateStr: String?): Date? {
        if (dateStr == null) return null
        
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        )
        
        for (format in formats) {
            try {
                return format.parse(dateStr)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun detectRecurringTransactions(transactions: List<ParsedTransaction>): List<RecurringTransaction> {
        // Group by merchant
        val merchantGroups = transactions
            .filter { it.merchant != null && it.type == "debit" }
            .groupBy { it.merchant!! }
        
        val recurring = mutableListOf<RecurringTransaction>()
        
        for ((merchant, txns) in merchantGroups) {
            if (txns.size < 2) continue
            
            val avgAmount = txns.map { it.amount }.average()
            val category = txns.firstOrNull()?.category ?: "Other"
            
            // Calculate frequency
            val dates = txns.mapNotNull { it.date }.sorted()
            if (dates.size < 2) continue
            
            val intervals = dates.zipWithNext { d1, d2 ->
                ((d2.time - d1.time) / (1000 * 60 * 60 * 24)).toInt()
            }
            
            val avgInterval = intervals.average()
            val frequency = when {
                avgInterval <= 7 -> "Weekly"
                avgInterval <= 35 -> "Monthly"
                avgInterval <= 95 -> "Quarterly"
                else -> "Yearly"
            }
            
            // Calculate confidence based on consistency
            val intervalVariance = intervals.map { abs(it - avgInterval) }.average()
            val confidence = when {
                intervalVariance < 3 -> 90
                intervalVariance < 7 -> 75
                intervalVariance < 14 -> 60
                else -> 45
            }.coerceIn(0, 100)
            
            // Predict next date
            val lastDate = dates.last()
            val nextDate = Calendar.getInstance().apply {
                time = lastDate
                add(Calendar.DAY_OF_YEAR, avgInterval.toInt())
            }
            val nextDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(nextDate.time)
            
            recurring.add(
                RecurringTransaction(
                    merchant = merchant,
                    averageAmount = avgAmount,
                    frequency = frequency,
                    category = category,
                    nextExpectedDate = nextDateStr,
                    confidence = confidence
                )
            )
        }
        
        return recurring.sortedByDescending { it.confidence }
    }

    private fun analyzeCategorySpending(transactions: List<ParsedTransaction>): Map<String, CategorySpend> {
        val debits = transactions.filter { it.type == "debit" }
        val totalSpent = debits.sumOf { it.amount }
        
        val categoryMap = debits
            .groupBy { it.category ?: "Other" }
            .mapValues { (category, txns) ->
                val categoryTotal = txns.sumOf { it.amount }
                val count = txns.size
                val avg = categoryTotal / count
                val percentage = if (totalSpent > 0) (categoryTotal / totalSpent) * 100 else 0.0
                
                // Simple trend analysis (compare first half vs second half)
                val firstHalf = txns.take(txns.size / 2).sumOf { it.amount }
                val secondHalf = txns.drop(txns.size / 2).sumOf { it.amount }
                val trend = when {
                    txns.size < 4 -> "Stable"
                    secondHalf > firstHalf * 1.2 -> "Increasing"
                    secondHalf < firstHalf * 0.8 -> "Decreasing"
                    else -> "Stable"
                }
                
                CategorySpend(
                    category = category,
                    totalSpent = categoryTotal,
                    transactionCount = count,
                    averagePerTransaction = avg,
                    percentageOfTotal = percentage,
                    trend = trend
                )
            }
        
        return categoryMap
    }

    private fun predictNextMonthIncome(transactions: List<ParsedTransaction>): Double {
        val credits = transactions.filter { it.type == "credit" }
        if (credits.isEmpty()) return 0.0
        
        // Use average of last 3 months or all available data
        val recentCredits = credits.take(minOf(30, credits.size))
        return recentCredits.sumOf { it.amount } / (recentCredits.size / 5.0).coerceAtLeast(1.0)
    }

    private fun predictNextMonthExpenses(
        transactions: List<ParsedTransaction>,
        recurring: List<RecurringTransaction>
    ): Double {
        // Base prediction on recent spending patterns
        val debits = transactions.filter { it.type == "debit" }
        val recentDebits = debits.take(minOf(30, debits.size))
        val baseExpenses = recentDebits.sumOf { it.amount }
        
        // Add recurring expenses expected next month
        val recurringExpenses = recurring
            .filter { it.frequency == "Monthly" || it.frequency == "Weekly" }
            .sumOf { 
                when (it.frequency) {
                    "Weekly" -> it.averageAmount * 4
                    "Monthly" -> it.averageAmount
                    else -> 0.0
                }
            }
        
        // Weight between historical and recurring
        return (baseExpenses * 0.6) + (recurringExpenses * 0.4)
    }

    private fun generateInsights(
        totalIncome: Double,
        totalExpenses: Double,
        predictedBalance: Double,
        recurring: List<RecurringTransaction>,
        categoryBreakdown: Map<String, CategorySpend>
    ): List<String> {
        val insights = mutableListOf<String>()
        val locale = Locale.US // Fixed locale for formatting
        
        // Balance insight
        if (predictedBalance > 0) {
            insights.add("💰 Expected surplus of ₹${String.format(locale, "%.2f", predictedBalance)} next month")
        } else {
            insights.add("⚠️ Expected deficit of ₹${String.format(locale, "%.2f", abs(predictedBalance))} next month")
        }
        
        // Savings rate
        if (totalIncome > 0) {
            val savingsRate = ((totalIncome - totalExpenses) / totalIncome) * 100
            insights.add("📊 Current savings rate: ${String.format(locale, "%.1f", savingsRate)}%")
        }
        
        // Top spending category
        val topCategory = categoryBreakdown.maxByOrNull { it.value.totalSpent }
        if (topCategory != null) {
            insights.add("🛒 Highest spending: ${topCategory.key} (${String.format(locale, "%.1f", topCategory.value.percentageOfTotal)}%)")
        }
        
        // Recurring subscriptions
        val subscriptions = recurring.filter { it.category == "Subscription" }
        if (subscriptions.isNotEmpty()) {
            val subTotal = subscriptions.sumOf { it.averageAmount }
            insights.add("🔄 ${subscriptions.size} recurring subscriptions costing ₹${String.format(locale, "%.2f", subTotal)}/month")
        }
        
        // Trend warnings
        val increasingCategories = categoryBreakdown.filter { it.value.trend == "Increasing" }
        if (increasingCategories.isNotEmpty()) {
            insights.add("📈 Spending increasing in: ${increasingCategories.keys.joinToString(", ")}")
        }
        
        return insights
    }

    private fun calculateConfidence(transactions: List<ParsedTransaction>): String {
        return when {
            transactions.size >= 30 -> "High"
            transactions.size >= 15 -> "Medium"
            else -> "Low"
        }
    }
}