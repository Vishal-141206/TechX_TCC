package com.runanywhere.startup_hackathon20

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Cash Flow Predictor that analyzes parsed transaction data
 * and provides financial insights and predictions
 */
class CashFlowPredictor {

    data class Transaction(
        val amount: Double,
        val type: String,
        val date: String?,
        val merchant: String?,
        val category: String = "Uncategorized"
    )

    fun predictCashFlow(
        parsedJsonMap: Map<String, String>,
        smsMap: Map<String, RawSms>
    ): CashFlowPrediction {
        // Parse all transactions from JSON
        val transactions = mutableListOf<Transaction>()
        val categorySpending = mutableMapOf<String, Double>()

        parsedJsonMap.forEach { (smsId, json) ->
            try {
                val obj = JSONObject(json)
                val amount = obj.optDouble("amount", 0.0)
                if (amount > 0) {
                    val type = obj.optString("type", "info")
                    val date = obj.optString("date")
                    val merchant = obj.optString("merchant")

                    val category = categorizeTransaction(merchant, type)
                    transactions.add(Transaction(amount, type, date, merchant, category))

                    // Track spending by category
                    if (type == "debit") {
                        categorySpending[category] = categorySpending.getOrDefault(category, 0.0) + amount
                    }
                }
            } catch (e: Exception) {
                // Skip invalid JSON
            }
        }

        // Calculate totals
        val totalIncome = transactions
            .filter { it.type == "credit" }
            .sumOf { it.amount }

        val totalExpenses = transactions
            .filter { it.type == "debit" }
            .sumOf { it.amount }

        val netCashFlow = totalIncome - totalExpenses

        // Analyze transaction patterns
        val dailySpending = analyzeDailyPatterns(transactions)
        val riskyDays = identifyRiskyDays(dailySpending)

        // Get top spending categories
        val topCategories = categorySpending
            .toList()
            .sortedByDescending { (_, amount) -> amount }
            .take(5)
            .toMap()

        // Generate recommendation
        val recommendation = generateRecommendation(
            totalIncome,
            totalExpenses,
            netCashFlow,
            dailySpending
        )

        // Predict balance (simple prediction based on average daily spending)
        val predictedBalance = predictFutureBalance(
            totalIncome,
            totalExpenses,
            transactions
        )

        return CashFlowPrediction(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netCashFlow = netCashFlow,
            predictedBalance = predictedBalance,
            topCategories = topCategories,
            riskyDays = riskyDays,
            recommendation = recommendation
        )
    }

    private fun categorizeTransaction(merchant: String?, type: String): String {
        if (type != "debit") return "Income"

        val merchantLower = merchant?.lowercase() ?: return "Uncategorized"

        return when {
            merchantLower.contains("zomato") || merchantLower.contains("swiggy") ||
                    merchantLower.contains("restaurant") || merchantLower.contains("cafe") -> "Food & Dining"

            merchantLower.contains("amazon") || merchantLower.contains("flipkart") ||
                    merchantLower.contains("myntra") || merchantLower.contains("shopping") -> "Shopping"

            merchantLower.contains("uber") || merchantLower.contains("ola") ||
                    merchantLower.contains("petrol") || merchantLower.contains("fuel") -> "Transport"

            merchantLower.contains("netflix") || merchantLower.contains("prime") ||
                    merchantLower.contains("hotstar") || merchantLower.contains("subscription") -> "Entertainment"

            merchantLower.contains("electricity") || merchantLower.contains("water") ||
                    merchantLower.contains("bill") -> "Utilities"

            merchantLower.contains("med") || merchantLower.contains("hospital") ||
                    merchantLower.contains("pharma") -> "Healthcare"

            else -> "Other Expenses"
        }
    }

    private fun analyzeDailyPatterns(transactions: List<Transaction>): Map<String, Double> {
        val dailySpending = mutableMapOf<String, Double>()

        transactions.filter { it.type == "debit" && it.date != null }.forEach { transaction ->
            val day = transaction.date?.substring(0, 10) // YYYY-MM-DD
            if (day != null) {
                dailySpending[day] = dailySpending.getOrDefault(day, 0.0) + transaction.amount
            }
        }

        return dailySpending
    }

    private fun identifyRiskyDays(dailySpending: Map<String, Double>): List<String> {
        if (dailySpending.isEmpty()) return emptyList()

        val averageSpending = dailySpending.values.average()
        val spendingStdDev = calculateStandardDeviation(dailySpending.values.toList())

        return dailySpending.filter { (_, amount) ->
            amount > averageSpending + (spendingStdDev * 1.5)
        }.keys.toList()
    }

    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0

        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    private fun generateRecommendation(
        income: Double,
        expenses: Double,
        netCashFlow: Double,
        dailySpending: Map<String, Double>
    ): String {
        val savingsRate = if (income > 0) ((income - expenses) / income * 100) else 0.0

        return when {
            netCashFlow < 0 -> {
                "⚠️ You're spending more than you earn! Consider reducing expenses in top categories."
            }
            savingsRate < 10 -> {
                "💡 Low savings rate. Try to save at least 20% of your income for better financial health."
            }
            dailySpending.size > 0 && dailySpending.values.any { it > income * 0.1 } -> {
                "📊 High daily spending detected. Watch out for impulse purchases."
            }
            netCashFlow > income * 0.3 -> {
                "✅ Excellent! You're saving more than 30% of your income. Keep it up!"
            }
            else -> {
                "👍 Good financial health. Maintain your spending habits."
            }
        }
    }

    private fun predictFutureBalance(
        totalIncome: Double,
        totalExpenses: Double,
        transactions: List<Transaction>
    ): Double {
        if (transactions.isEmpty()) return 0.0

        val debits = transactions.filter { it.type == "debit" }
        val credits = transactions.filter { it.type == "credit" }

        // Calculate average daily income and expenses
        val daysWithData = transactions.mapNotNull { it.date }.distinct().size
        val avgDailyIncome = if (daysWithData > 0) credits.sumOf { it.amount } / daysWithData else 0.0
        val avgDailyExpense = if (daysWithData > 0) debits.sumOf { it.amount } / daysWithData else 0.0

        // Predict 30-day balance
        val daysToPredict = 30
        val predictedIncome = avgDailyIncome * daysToPredict
        val predictedExpenses = avgDailyExpense * daysToPredict

        return predictedIncome - predictedExpenses
    }

    // Helper math functions since we can't import kotlin.math in some contexts
    private fun Double.pow(n: Int): Double {
        var result = 1.0
        repeat(n) { result *= this }
        return result
    }

    private fun sqrt(x: Double): Double {
        if (x < 0) return 0.0
        var guess = x / 2.0
        for (i in 1..10) {
            guess = (guess + x / guess) / 2.0
        }
        return guess
    }
}