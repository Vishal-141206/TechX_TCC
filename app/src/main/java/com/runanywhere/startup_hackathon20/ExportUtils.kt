package com.runanywhere.startup_hackathon20

import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Share financial report as text
 */
fun shareFinancialReport(
    context: Context,
    parsedTransactions: Map<String, String>,
    cashFlow: CashFlowPrediction?
) {
    try {
        val report = generateFinancialReport(parsedTransactions, cashFlow)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Finance AI Report")
            putExtra(Intent.EXTRA_TEXT, report)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing report", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Export transactions as CSV
 */
fun exportToCsv(context: Context, parsedTransactions: Map<String, String>) {
    try {
        val csv = generateCsvReport(parsedTransactions)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Transactions Export")
            putExtra(Intent.EXTRA_TEXT, csv)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Export CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting CSV", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Generate detailed financial report
 */
private fun generateFinancialReport(
    parsedTransactions: Map<String, String>,
    cashFlow: CashFlowPrediction?
): String {
    val sb = StringBuilder()
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val currentDate = dateFormat.format(Date())

    sb.appendLine("═══════════════════════════════════════")
    sb.appendLine("     FINANCE AI - FINANCIAL REPORT")
    sb.appendLine("═══════════════════════════════════════")
    sb.appendLine("Generated: $currentDate")
    sb.appendLine("Report Period: Last 30 Days")
    sb.appendLine("═══════════════════════════════════════\n")

    // Calculate totals
    var totalIncome = 0.0
    var totalExpense = 0.0
    val categories = mutableMapOf<String, Double>()

    parsedTransactions.values.forEach { jsonStr ->
        try {
            val json = JSONObject(jsonStr)
            val amount = json.optDouble("amount", 0.0)
            val type = json.optString("type", "")
            val merchant = json.optString("merchant", "Unknown")

            when (type) {
                "debit" -> totalExpense += amount
                "credit" -> totalIncome += amount
            }

            // Categorize
            val category = categorizeForReport(merchant)
            if (type == "debit") {
                categories[category] = categories.getOrDefault(category, 0.0) + amount
            }
        } catch (e: Exception) {
            // Skip invalid
        }
    }

    val netFlow = totalIncome - totalExpense

    sb.appendLine("📊 SUMMARY")
    sb.appendLine("─────────────────────────────────────")
    sb.appendLine("Total Transactions: ${parsedTransactions.size}")
    sb.appendLine("Total Income:       ₹${String.format("%,.2f", totalIncome)}")
    sb.appendLine("Total Expenses:     ₹${String.format("%,.2f", totalExpense)}")
    sb.appendLine("Net Cash Flow:      ₹${String.format("%,.2f", netFlow)}")
    sb.appendLine()

    // Category Breakdown
    if (categories.isNotEmpty()) {
        sb.appendLine("📈 SPENDING BY CATEGORY")
        sb.appendLine("─────────────────────────────────────")
        categories.entries.sortedByDescending { it.value }.forEach { (category, total) ->
            val percentage = (total / totalExpense * 100).toInt()
            sb.appendLine("$category: ₹${String.format("%,.0f", total)} ($percentage%)")
        }
        sb.appendLine()
    }

    // Cash Flow Forecast
    if (cashFlow != null) {
        sb.appendLine("🔮 30-DAY FORECAST")
        sb.appendLine("─────────────────────────────────────")
        sb.appendLine("Predicted Income:   ₹${String.format("%,.0f", cashFlow.totalIncome)}")
        sb.appendLine("Predicted Expenses: ₹${String.format("%,.0f", cashFlow.totalExpenses)}")
        sb.appendLine("Expected Balance:   ₹${String.format("%,.0f", cashFlow.netCashFlow)}")

        val healthScore = when {
            cashFlow.netCashFlow > totalIncome * 0.2 -> "Excellent"
            cashFlow.netCashFlow > 0 -> "Good"
            cashFlow.netCashFlow > -totalExpense * 0.2 -> "Fair"
            else -> "Needs Attention"
        }
        sb.appendLine("Financial Health:   $healthScore")
        sb.appendLine()
    }

    // Insights
    sb.appendLine("💡 KEY INSIGHTS")
    sb.appendLine("─────────────────────────────────────")
    if (netFlow > 0) {
        val savingsRate = (netFlow / totalIncome * 100).toInt()
        sb.appendLine("✓ Positive cash flow! Savings rate: $savingsRate%")
        if (savingsRate > 20) {
            sb.appendLine("✓ Excellent! You're saving more than 20% of income")
        }
    } else {
        sb.appendLine("⚠ Negative cash flow. Expenses exceed income")
        sb.appendLine("  Consider: Review subscriptions, reduce dining out")
    }

    val topCategory = categories.maxByOrNull { it.value }
    if (topCategory != null) {
        val percentage = (topCategory.value / totalExpense * 100).toInt()
        sb.appendLine("• Top spending: ${topCategory.key} ($percentage% of expenses)")
        if (percentage > 40) {
            sb.appendLine("  Consider: This category is high, look for savings")
        }
    }

    sb.appendLine()
    sb.appendLine("─────────────────────────────────────")
    sb.appendLine("Generated by Finance AI")
    sb.appendLine("100% On-Device • Privacy First")
    sb.appendLine("═══════════════════════════════════════")

    return sb.toString()
}

/**
 * Generate CSV export
 */
private fun generateCsvReport(parsedTransactions: Map<String, String>): String {
    val sb = StringBuilder()
    sb.appendLine("Date,Type,Amount,Merchant,Category,Account,Balance")

    parsedTransactions.values.forEach { jsonStr ->
        try {
            val json = JSONObject(jsonStr)
            val date = json.optString("date", "")
            val type = json.optString("type", "")
            val amount = json.optDouble("amount", 0.0)
            val merchant = json.optString("merchant", "Unknown")
            val account = json.optString("accountNumber", "")
            val balance = json.optDouble("balance", 0.0)
            val category = categorizeForReport(merchant)

            sb.appendLine("$date,$type,$amount,\"$merchant\",$category,$account,$balance")
        } catch (e: Exception) {
            // Skip invalid
        }
    }

    return sb.toString()
}

private fun categorizeForReport(merchant: String): String {
    val lowerMerchant = merchant.lowercase()
    return when {
        lowerMerchant.contains("amazon") || lowerMerchant.contains("flipkart") ||
                lowerMerchant.contains("shopping") -> "Shopping"

        lowerMerchant.contains("zomato") || lowerMerchant.contains("swiggy") ||
                lowerMerchant.contains("restaurant") -> "Food"

        lowerMerchant.contains("uber") || lowerMerchant.contains("ola") -> "Transport"
        lowerMerchant.contains("netflix") || lowerMerchant.contains("spotify") -> "Entertainment"
        lowerMerchant.contains("electricity") || lowerMerchant.contains("bill") -> "Utilities"
        else -> "Other"
    }
}
