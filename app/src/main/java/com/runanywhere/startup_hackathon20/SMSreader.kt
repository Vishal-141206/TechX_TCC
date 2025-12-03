package com.runanywhere.startup_hackathon20

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

data class RawSms(
    val id: String,
    val address: String?,
    val body: String?,
    val date: Long
)

/**
 * Read SMS Inbox using robust heuristics tuned for Indian banking messages.
 *
 * - Numeric shortcodes (3-6 digits) -> treated as business
 * - Alphanumeric shortcodes (3-12 chars, must contain letter) -> business
 * - Phone-like numbers -> treated as business only if message contains financial keywords
 * - If address is blank but body strongly matches financial keywords -> include as fallback
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
        "otp is", "otp:", "one time password", "verification code", "auth code", "use otp", "pin is", "pin:"
    )

    cursor?.use {
        val idIdx = it.getColumnIndexOrThrow("_id")
        val addressIdx = it.getColumnIndexOrThrow("address")
        val bodyIdx = it.getColumnIndexOrThrow("body")
        val dateIdx = it.getColumnIndexOrThrow("date")

        var totalScanned = 0
        while (it.moveToNext() && results.size < limit) {
            totalScanned++
            val rawAddress = it.getString(addressIdx)
            val body = it.getString(bodyIdx) ?: ""
            val cleanBody = body.lowercase(Locale.getDefault())

            // Heuristics
            val senderIsBusiness = isBusinessSender(rawAddress, cleanBody)
            val isFinancialByContent = financialKeywords.any { k -> cleanBody.contains(k) }
            val isExcluded = exclusionKeywords.any { k -> cleanBody.contains(k) }

            // If explicitly excluded (OTPs etc.) skip
            if (isExcluded) continue

            // Primary acceptance: business sender OR strong content match
            val accept = senderIsBusiness || isFinancialByContent

            if (!accept) {
                // not business-like and not financial content: skip
                continue
            }

            // Now safe to add
            val id = it.getString(idIdx) ?: continue
            val date = try { it.getLong(dateIdx) } catch (e: Exception) { 0L }
            results.add(RawSms(id, rawAddress, body, date))
        }
        Log.d("SMSReader", "Scan Complete. Scanned: $totalScanned. Imported: ${results.size}")
    }

    return results
}

/**
 * Enhanced heuristic to decide if a sender is business/bank-like.
 *
 * - numeric shortcodes (3-6 digits) -> business
 * - alnum shortcodes (3-12 chars, must contain at least one letter) -> business
 * - phone-like numbers: strip non-digits, then consider business if message contains financial keywords
 * - explicit bank tokens in the sender string -> business
 */
fun isBusinessSender(address: String?, messageBody: String? = null): Boolean {
    if (address.isNullOrBlank()) return false

    val s = address.trim()
    val sUpper = s.uppercase(Locale.getDefault())
    val body = messageBody?.lowercase(Locale.getDefault()) ?: ""

    // Patterns
    val numericShort = Regex("^\\d{3,6}\$") // 3-6 digit shortcode
    // require at least one letter to avoid matching purely-numeric shortcodes here
    val alphaNumShort = Regex("^(?=.*[A-Z])[A-Z0-9\\-]{3,12}\$")
    // we'll treat phone-like by stripping non-digits and checking length
    val digitsOnly = s.replace(Regex("\\D"), "")
    val phoneLikeLen = digitsOnly.length in 7..15

    // 1) numeric shortcode -> business
    if (numericShort.matches(s)) {
        Log.d("SMSReader", "Sender business (numeric shortcode): $s")
        return true
    }

    // 2) alphanumeric shortcode that contains letter -> business
    if (alphaNumShort.matches(sUpper)) {
        Log.d("SMSReader", "Sender business (alnum shortcode): $sUpper")
        return true
    }

    // 3) explicit bank tokens in sender text
    val bankTokens = listOf(
        "BANK", "HDFC", "SBI", "ICICI", "AXIS", "PNB", "IDBI", "BOB", "KOTAK", "YESBANK",
        "PAY", "RUPEE", "AMAZON", "GOOGLE", "PAYTM", "PHONEPE", "BHIM", "NMBL", "CITI"
    )
    if (bankTokens.any { sUpper.contains(it) }) {
        Log.d("SMSReader", "Sender contains bank token, treated as business: $sUpper")
        return true
    }

    // 4) phone-like: only business if body contains financial keywords
    if (phoneLikeLen) {
        if (body.isNotBlank()) {
            val financialKeywords = listOf(
                "credit", "credited", "debit", "debited", "txn", "transaction", "acct", "account",
                "upi", "inr", "rs.", "avl", "available balance", "neft", "imps", "rtgs", "paid", "paid to"
            )
            val hasKeyword = financialKeywords.any { body.contains(it) }
            Log.d("SMSReader", "Phone-like sender ${digitsOnly} -> body financial keyword present? $hasKeyword")
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
