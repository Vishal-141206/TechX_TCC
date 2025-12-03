package com.runanywhere.startup_hackathon20

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.max
import kotlin.math.abs

/**
 * CashFlowPredictor with progress callback support:
 * fun predictCashFlow(parsedJsonMap, smsMap, progressCb: (status: String, progress: Float) -> Unit)
 */
class CashFlowPredictor {

    data class Transaction(
        val amount: Double,
        val type: String,
        val dateIso: String?,       // YYYY-MM-DD
        val merchant: String?,
        val category: String = "Uncategorized"
    )

    /**
     * Main entry: analyzes parsed JSON map and smsMap and returns CashFlowPrediction.
     * progressCb called with friendly status and progress between 0..1.
     */
    fun predictCashFlow(
        parsedJsonMap: Map<String, String>,
        smsMap: Map<String, RawSms>,
        progressCb: (String, Float) -> Unit = { _, _ -> }
    ): CashFlowPrediction {
        // Defensive: quick early return
        if (parsedJsonMap.isEmpty()) {
            progressCb("No parsed transactions available", 0f)
            return emptyPrediction()
        }

        progressCb("Preparing transactions...", 0.05f)

        val transactions = mutableListOf<Transaction>()
        val categorySpending = mutableMapOf<String, Double>()

        // Track most recent known balance (from parsed JSON) and its timestamp
        var latestKnownBalance: Double? = null
        var latestBalanceTime: Long = Long.MIN_VALUE

        val totalItems = parsedJsonMap.size
        var processed = 0

        // Iterate parsed JSON and build transactions
        for ((smsId, json) in parsedJsonMap) {
            processed++
            try {
                // try to parse JSON safely
                val obj = try {
                    JSONObject(json)
                } catch (_: Exception) {
                    null
                } ?: continue

                val amount = obj.optDouble("amount", Double.NaN)
                if (amount.isNaN()) {
                    // ignore non-transaction entries
                    continue
                }

                val type = obj.optString("type", "info").lowercase(Locale.getDefault())
                val dateStr = obj.optString("date", null)
                val merchant = obj.optString("merchant", null)

                // Normalize date: prefer parsed ISO date; fallback to SMS timestamp (smsMap)
                val dateIso = when {
                    !dateStr.isNullOrBlank() -> {
                        if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) dateStr
                        else normalizeDateToIso(dateStr)
                    }
                    else -> {
                        smsMap[smsId]?.let { sms ->
                            try {
                                val sdfIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                sdfIso.format(Date(sms.date))
                            } catch (_: Exception) { null }
                        }
                    }
                }

                // Track latest known balance if present (parsed JSON may contain "balance")
                if (obj.has("balance")) {
                    val bal = obj.optDouble("balance", Double.NaN)
                    if (!bal.isNaN()) {
                        val smsTime = smsMap[smsId]?.date ?: 0L
                        if (smsTime >= latestBalanceTime) {
                            latestKnownBalance = bal
                            latestBalanceTime = smsTime
                        }
                    }
                }

                val category = categorizeTransaction(merchant, type)
                val tx = Transaction(amount, type, dateIso, merchant, category)
                transactions.add(tx)

                if (type == "debit") {
                    categorySpending[category] = categorySpending.getOrDefault(category, 0.0) + amount
                }

            } catch (_: Exception) {
                // ignore single json parse errors, continue
            } finally {
                // update progress while parsing
                val prog = 0.05f + 0.4f * (processed.toFloat() / totalItems.toFloat()) // 5%->45%
                progressCb("Parsing transactions... ($processed/$totalItems)", prog.coerceIn(0f, 1f))
            }
        }

        // Totals
        progressCb("Calculating totals...", 0.5f)
        val totalIncome = transactions.filter { it.type == "credit" }.sumOf { it.amount }
        val totalExpenses = transactions.filter { it.type == "debit" }.sumOf { it.amount }
        val netCashFlow = totalIncome - totalExpenses

        progressCb("Analyzing daily patterns...", 0.6f)
        val dailySpending = analyzeDailyPatterns(transactions)

        progressCb("Identifying risky days...", 0.72f)
        val riskyDays = identifyRiskyDays(dailySpending)

        progressCb("Selecting top categories...", 0.8f)
        val topCategories = categorySpending.toList()
            .sortedByDescending { (_, amount) -> amount }
            .take(5)
            .toMap()

        progressCb("Generating recommendation...", 0.9f)
        val recommendation = generateRecommendation(totalIncome, totalExpenses, netCashFlow, dailySpending)

        progressCb("Predicting future balance...", 0.95f)
        val predictedBalance = predictFutureBalance(totalIncome, totalExpenses, transactions, latestKnownBalance)

        progressCb("Finalizing prediction...", 1.0f)

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

    private fun emptyPrediction() = CashFlowPrediction(
        totalIncome = 0.0,
        totalExpenses = 0.0,
        netCashFlow = 0.0,
        predictedBalance = 0.0,
        topCategories = emptyMap(),
        riskyDays = emptyList(),
        recommendation = "No data"
    )

    private fun categorizeTransaction(merchant: String?, type: String): String {
        if (type != "debit") return "Income"

        val merchantLower = merchant?.lowercase(Locale.getDefault()) ?: return "Uncategorized"

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
        transactions.filter { it.type == "debit" }.forEach { transaction ->
            val day = transaction.dateIso ?: return@forEach
            dailySpending[day] = dailySpending.getOrDefault(day, 0.0) + transaction.amount
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
            dailySpending.isNotEmpty() && dailySpending.values.any { it > income * 0.1 } -> {
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
        transactions: List<Transaction>,
        latestKnownBalance: Double?
    ): Double {
        if (transactions.isEmpty()) return latestKnownBalance ?: 0.0

        val debits = transactions.filter { it.type == "debit" }
        val credits = transactions.filter { it.type == "credit" }

        // Determine date span (earliest to latest) in days to compute averages
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dates = transactions.mapNotNull { it.dateIso }.mapNotNull { d ->
            try { dateFormat.parse(d)?.time } catch (_: Exception) { null }
        }.sorted()

        val daysWithData = if (dates.isEmpty()) {
            1
        } else {
            val spanMs = (dates.last() - dates.first()).coerceAtLeast(0L)
            val spanDays = (spanMs / (24 * 60 * 60 * 1000)).toInt() + 1
            if (spanDays <= 0) 1 else spanDays
        }

        val avgDailyIncome = if (daysWithData > 0) credits.sumOf { it.amount } / daysWithData else 0.0
        val avgDailyExpense = if (daysWithData > 0) debits.sumOf { it.amount } / daysWithData else 0.0

        // Predict 30-day balance change
        val daysToPredict = 30
        val predictedIncome = avgDailyIncome * daysToPredict
        val predictedExpenses = avgDailyExpense * daysToPredict

        val base = latestKnownBalance ?: 0.0
        return base + predictedIncome - predictedExpenses
    }

    // Normalize common date formats to yyyy-MM-dd where possible
    private fun normalizeDateToIso(s: String): String? {
        try {
            val trimmed = s.trim()
            // try yyyy-MM-dd
            if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return trimmed
            // dd/MM/yyyy or dd-MM-yyyy or dd/MM/yy
            val parts = trimmed.split('/', '-')
            if (parts.size == 3) {
                var d = parts[0]
                var m = parts[1]
                var y = parts[2]
                if (y.length == 2) y = "20$y"
                // assume dd/mm/yyyy (India)
                val dd = d.padStart(2, '0').toIntOrNull() ?: return null
                val mm = m.padStart(2, '0').toIntOrNull() ?: return null
                val yy = y.padStart(4, '0').toIntOrNull() ?: return null
                return String.format(Locale.getDefault(), "%04d-%02d-%02d", yy, mm, dd)
            }
        } catch (_: Exception) { }
        return null
    }

    // Helper math functions
    private fun Double.pow(n: Int): Double {
        var result = 1.0
        repeat(n) { result *= this }
        return result
    }

    private fun sqrt(x: Double): Double {
        if (x <= 0.0) return 0.0
        var guess = x / 2.0
        repeat(20) {
            if (guess == 0.0) { guess = x; return@repeat }
            guess = (guess + x / guess) / 2.0
        }
        return guess
    }
}
