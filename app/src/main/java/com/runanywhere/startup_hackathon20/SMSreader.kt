package com.runanywhere.startup_hackathon20

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

// Simple data holder (unchanged)
data class RawSms(
    val id: String,
    val address: String?,
    val body: String?,
    val date: Long
)

/**
 * Reads SMS from Inbox with more tolerant filtering suited for Indian senders.
 *
 * - Recognizes numeric shortcodes (e.g., 575756), alphanumeric sender IDs (e.g., HDFCBK),
 *   and normal phone numbers when message body contains banking keywords.
 * - Excludes OTP/verification messages unless you want them for scam analysis.
 */
fun readSmsInbox(context: Context, limit: Int = 3000, daysLookBack: Int? = null): List<RawSms> {
    val uriSms: Uri = Uri.parse("content://sms/inbox")
    var selection: String? = null
    var selectionArgs: Array<String>? = null

    Log.d("SMSReader", "Starting SMS Scan... limit=$limit daysLookBack=$daysLookBack")

    if (daysLookBack != null) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysLookBack)
        val minDate = calendar.timeInMillis
        selection = "date >= ?"
        selectionArgs = arrayOf(minDate.toString())
    }

    val cursor: Cursor? = try {
        context.contentResolver.query(
            uriSms,
            arrayOf("_id", "address", "body", "date"),
            selection,
            selectionArgs,
            "date DESC"
        )
    } catch (e: Exception) {
        Log.e("SMSReader", "Error querying SMS: ${e.message}")
        return emptyList()
    }

    val results = mutableListOf<RawSms>()

    // Keywords that indicate a transaction (used both for content classification and fallback)
    val financialKeywords = listOf(
        "credit", "credited", "debit", "debited", "txn", "transaction", "acct", "account",
        "spent", "received", "bank", "pay", "upi", "inr", "rs.", "avl", "avl bal", "available balance",
        "withdraw", "purchase", "stmt", "neft", "imps", "rtgs", "credited to your account", "debited from"
    )

    // Exclude OTPs to reduce noise (unless you want them for scam detection)
    val exclusionKeywords = listOf(
        "otp is", "otp:", "one time password", "verification code", "auth code", "use otp"
    )

    cursor?.use {
        val idIdx = it.getColumnIndexOrThrow("_id")
        val addressIdx = it.getColumnIndexOrThrow("address")
        val bodyIdx = it.getColumnIndexOrThrow("body")
        val dateIdx = it.getColumnIndexOrThrow("date")

        var totalScanned = 0
        while (it.moveToNext() && results.size < limit) {
            totalScanned++
            val address = it.getString(addressIdx)
            val body = it.getString(bodyIdx) ?: ""
            val cleanBody = body.lowercase(Locale.getDefault())

            // 1. PRIMARY FILTER: Must be a Business Sender (Bank/Service) OR content strongly indicates financial txn
            val senderIsBusiness = isBusinessSender(address, cleanBody)
            val isFinancialByContent = financialKeywords.any { k -> cleanBody.contains(k) }
            val isExcluded = exclusionKeywords.any { k -> cleanBody.contains(k) }

            // If neither business-like sender nor clear financial content, skip
            if (!senderIsBusiness && !isFinancialByContent) {
                continue
            }

            // If explicitly excluded (OTPs etc.) skip
            if (isExcluded) continue

            val id = it.getString(idIdx) ?: continue
            val date = it.getLong(dateIdx)
            results.add(RawSms(id, address, body, date))
        }
        Log.d("SMSReader", "Scan Complete. Scanned: $totalScanned. Imported: ${results.size}")
    }

    return results
}

/**
 * Robust heuristic to decide if a sender is business/bank-like.
 *
 * Uses both `address` and `messageBody` to decide:
 * - Numeric shortcodes (3-6 digits) -> business
 * - Alphanumeric shortcodes (4-8 chars) -> business
 * - Long phone numbers -> business only if message contains banking keywords
 * - If sender contains common bank words (BANK, HDFC, SBI, ICICI, etc.) -> business
 */
fun isBusinessSender(address: String?, messageBody: String? = null): Boolean {
    if (address.isNullOrBlank()) return false

    val s = address.trim()
    val sUpper = s.uppercase(Locale.getDefault())
    val body = messageBody?.lowercase(Locale.getDefault()) ?: ""

    // Patterns
    val numericShort = Regex("^\\d{3,6}\$") // e.g., 575756
    val alphaNumShort = Regex("^[A-Z0-9\\-]{3,12}\$") // allow hyphenated shortcodes
    val phoneLike = Regex("^\\+?\\d{7,15}\$") // e.g., +919876543210 or 9876543210

    // 1) If sender looks like a short numeric shortcode -> business
    if (numericShort.matches(s)) {
        Log.d("SMSReader", "Sender treated as business (numeric shortcode): $s")
        return true
    }

    // 2) If sender is an alphanumeric short ID (HDFCBK, AXISBK, JM-AMZPAY) -> business
    if (alphaNumShort.matches(sUpper)) {
        // If there is absolutely no body and it's a short alnum, still consider business
        Log.d("SMSReader", "Sender treated as business (alnum shortcode): $sUpper")
        return true
    }

    // 3) If sender contains clear bank names or tokens -> business
    val bankTokens = listOf("BANK", "HDFC", "SBI", "ICICI", "AXIS", "PNB", "IDBI", "BOB", "PAY", "RUPEE", "AMAZON", "GOOGLE", "PAYTM", "PHONEPE")
    if (bankTokens.any { sUpper.contains(it) }) {
        Log.d("SMSReader", "Sender contains bank token, treated as business: $sUpper")
        return true
    }

    // 4) If it's phone-like (personal-looking) then only treat as business when body contains financial keywords
    if (phoneLike.matches(s)) {
        if (body.isNotBlank()) {
            val financialKeywords = listOf(
                "credit", "credited", "debit", "debited", "txn", "transaction", "acct", "account",
                "upi", "inr", "rs.", "avl", "available balance", "neft", "imps", "rtgs"
            )
            val hasKeyword = financialKeywords.any { body.contains(it) }
            Log.d("SMSReader", "Phone-like sender $s -> body financial keyword present? $hasKeyword")
            return hasKeyword
        }
        return false
    }

    // Default: not business
    return false
}

fun formatDate(millis: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(millis))
    } catch (e: Exception) {
        ""
    }
}
