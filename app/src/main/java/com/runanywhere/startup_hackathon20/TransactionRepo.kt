package com.runanywhere.startup_hackathon20

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for managing transaction data persistence
 */
class TransactionRepo(private val context: Context) {

    companion object {
        private const val TRANSACTIONS_FILE = "transactions.json"
        private const val CACHE_FILE = "parsed_sms_cache.json"
    }

    /**
     * Save parsed transactions to local storage
     */
    fun saveTransactions(transactions: Map<String, String>): Boolean {
        return try {
            val jsonObject = JSONObject()
            transactions.forEach { (key, value) ->
                jsonObject.put(key, value)
            }

            context.openFileOutput(TRANSACTIONS_FILE, Context.MODE_PRIVATE).use { output ->
                output.write(jsonObject.toString().toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load parsed transactions from local storage
     */
    fun loadTransactions(): Map<String, String> {
        return try {
            val file = File(context.filesDir, TRANSACTIONS_FILE)
            if (!file.exists()) return emptyMap()

            val jsonString = context.openFileInput(TRANSACTIONS_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, String>()

            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }

            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Save parsed SMS cache
     */
    fun saveParsedCache(parsedMap: Map<String, String>): Boolean {
        return try {
            val jsonObject = JSONObject()
            parsedMap.forEach { (key, value) ->
                jsonObject.put(key, value)
            }

            context.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE).use { output ->
                output.write(jsonObject.toString().toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load parsed SMS cache
     */
    fun loadParsedCache(): Map<String, String> {
        return try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return emptyMap()

            val jsonString = context.openFileInput(CACHE_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, String>()

            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }

            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Export transactions to CSV for sharing
     */
    fun exportToCsv(transactions: Map<String, String>): String {
        val csv = StringBuilder()
        csv.append("Date,Amount,Type,Merchant,Category,Balance\n")

        transactions.values.forEach { json ->
            try {
                val obj = JSONObject(json)
                val date = obj.optString("date", "Unknown")
                val amount = obj.optDouble("amount", 0.0)
                val type = obj.optString("type", "info")
                val merchant = obj.optString("merchant", "Unknown")
                val balance = obj.optDouble("balance", 0.0)

                // Simple category detection
                val category = when {
                    merchant.contains("amazon", true) || merchant.contains("flipkart", true) -> "Shopping"
                    merchant.contains("zomato", true) || merchant.contains("swiggy", true) -> "Food"
                    merchant.contains("uber", true) || merchant.contains("ola", true) -> "Transport"
                    else -> "Other"
                }

                csv.append("$date,$amount,$type,\"$merchant\",$category,$balance\n")
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }

        return csv.toString()
    }

    /**
     * Get monthly summary
     */
    fun getMonthlySummary(transactions: Map<String, String>): Map<String, Double> {
        val monthlySummary = mutableMapOf<String, Double>()
        val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        transactions.values.forEach { json ->
            try {
                val obj = JSONObject(json)
                val dateStr = obj.optString("date")
                val amount = obj.optDouble("amount", 0.0)
                val type = obj.optString("type")

                if (dateStr.isNotBlank() && amount > 0) {
                    val month = dateStr.substring(0, 7) // YYYY-MM
                    val key = if (type == "debit") "expense_$month" else "income_$month"

                    monthlySummary[key] = monthlySummary.getOrDefault(key, 0.0) + amount
                }
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }

        return monthlySummary
    }

    /**
     * Clear all stored data
     */
    fun clearAllData(): Boolean {
        return try {
            arrayOf(TRANSACTIONS_FILE, CACHE_FILE).forEach { fileName ->
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    file.delete()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}