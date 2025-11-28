package com.runanywhere.startup_hackathon20

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// 1. Define the clean data model (The "Goal" output)
data class Transaction(
    val amount: Double,
    val merchant: String,
    val category: String,
    val isSuspicious: Boolean,
    val riskReason: String?,
    val date: Long
)

class TransactionRepository {

    // 2. The JSON Schema we force the AI to follow
    // This strictly enforces the "Structured Output" requirement of the hackathon
    @Suppress("unused")
    private val extractionSchema = """
    {
      "type": "object",
      "properties": {
        "is_transaction": { "type": "boolean" },
        "amount": { "type": "number" },
        "merchant": { "type": "string" },
        "category": { "type": "string", "enum": ["Food", "Transport", "Bills", "Shopping", "Health", "Subscription", "Transfer", "Other"] },
        "is_suspicious": { "type": "boolean" },
        "risk_reason": { "type": "string" }
      },
      "required": ["is_transaction", "amount", "merchant", "category", "is_suspicious"]
    }
    """.trimIndent()

    suspend fun processMessages(rawMessages: List<RawSms>): List<Transaction> = withContext(Dispatchers.IO) {
        val cleanTransactions = mutableListOf<Transaction>()

        for (sms in rawMessages) {
            try {
                // 3. Construct the prompt for the Local LLM
                val prompt = "Extract financial details from this SMS. If it is a scam, flag it. SMS: \"${sms.body}\""

                // --- START AI SDK INTEGRATION ---
                // TODO: Replace 'RunAnywhere.infer' with the actual function from the starter repo/documentation.
                // We are passing the schema to force valid JSON output.

                // val jsonResponse = RunAnywhereSDK.generateStructured(
                //     prompt = prompt,
                //     schema = extractionSchema
                // )

                // MOCK for testing until you connect the SDK:
                // (Delete this mock block when real AI is connected)
                val jsonResponse = mockAiResponse(sms.body)
                // --- END AI SDK INTEGRATION ---

                // 4. Parse the JSON result
                val json = JSONObject(jsonResponse)

                if (json.optBoolean("is_transaction")) {
                    cleanTransactions.add(
                        Transaction(
                            amount = json.optDouble("amount", 0.0),
                            merchant = json.optString("merchant", "Unknown"),
                            category = json.optString("category", "Other"),
                            isSuspicious = json.optBoolean("is_suspicious"),
                            riskReason = json.optString("risk_reason"),
                            date = sms.date
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e("TransactionRepo", "AI Extraction Failed for msg: ${sms.id}", e)
            }
        }
        return@withContext cleanTransactions
    }

    // Temporary Mock function to test your app logic without waiting for the AI
    private fun mockAiResponse(body: String?): String {
        return """
            {
                "is_transaction": true,
                "amount": 150.00,
                "merchant": "MOCK_MERCHANT",
                "category": "Food",
                "is_suspicious": false,
                "risk_reason": ""
            }
        """.trimIndent()
    }
}