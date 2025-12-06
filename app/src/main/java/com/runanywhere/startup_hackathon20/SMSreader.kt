package com.runanywhere.startup_hackathon20

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class RawSms(
    val id: String,
    val address: String?,
    val body: String?,
    val date: Long
)

/**
 * Read SMS Inbox but strictly import only bank/payment transaction messages.
 *
 * Rules:
 * - Exclude OTP/verification/login/reset messages.
 * - Include when BOTH: (body contains a transaction verb) AND (body contains an amount pattern)
 * - OR include when sender is a bank/business short code / contains known bank token AND body contains a transaction verb.
 */
fun readSmsInbox(context: Context, limit: Int = 3000, daysLookBack: Int? = null): List<RawSms> {
    val uriSms: Uri = Uri.parse("content://sms/inbox")
    var selection: String? = null
    var selectionArgs: Array<String>? = null

    Log.d("SMSReader", "Starting strict SMS Scan... limit=$limit daysLookBack=$daysLookBack")

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

    // Transaction verbs (case-insensitive)
    val transactionVerbs = listOf(
        "debited", "credited", "credit", "debit", "txn", "transaction", "transferred",
        "withdrawn", "withdrawal", "paid", "purchase", "spent", "sent to", "received",
        "payment", "failed", "reversed", "refund", "credited to", "debited from", "utR",
        "ref", "via neft", "via imps", "via rtgs", "upi"
    )

    // Exclusion keywords (otp/verification/login/etc.)
    val exclusionKeywords = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "use otp", "expires", "valid for", "password reset", "login", "logged in"
    )

    // Bank/payment sender tokens (common)
    val bankTokens = listOf(
        "BANK", "HDFC", "SBI", "ICICI", "AXIS", "PNB", "IDBI", "BOB", "KOTAK","IOB","CENTBK","BOI",
        "YESBNK", "CITI", "PAYTM", "PHONEPE", "GPAY", "AMAZON", "FLIPKART",
        "AXISBK", "HDFCBK", "SBIINB", "KOTAKBNK"
    )

    // Regex to detect amount patterns (INR / Rs / ₹ or numeric amounts like 1,234.56)
    val amountPattern = Pattern.compile("(?i)(?:inr|rs\\.?|₹)\\s*([0-9]{1,3}(?:[,\\s][0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)")
    val anyNumberPattern = Pattern.compile("\\b[0-9]{2,}(?:[,\\.][0-9]{2,})?\\b") // fallback numeric detection

    cursor?.use { c ->
        val idIdx = c.getColumnIndexOrThrow("_id")
        val addressIdx = c.getColumnIndexOrThrow("address")
        val bodyIdx = c.getColumnIndexOrThrow("body")
        val dateIdx = c.getColumnIndexOrThrow("date")

        var totalScanned = 0
        while (c.moveToNext() && results.size < limit) {
            totalScanned++
            val id = c.getString(idIdx) ?: continue
            val rawAddress = c.getString(addressIdx)
            val body = c.getString(bodyIdx) ?: ""
            val cleanBody = body.lowercase(Locale.getDefault()).trim()

            // Skip empty bodies
            if (cleanBody.isEmpty()) continue

            // Exclude OTP/verification/login messages aggressively
            if (exclusionKeywords.any { cleanBody.contains(it) }) continue

            // Check transaction verb presence
            val hasTransactionVerb = transactionVerbs.any { cleanBody.contains(it, ignoreCase = true) }

            // Check amount presence (INR/Rs/₹ pattern)
            val amountMatcher = amountPattern.matcher(body)
            val hasAmount = amountMatcher.find() || anyNumberPattern.matcher(body).find().and(hasTransactionVerb) // require verb for pure numeric fallback

            // Sender heuristics
            val senderIsBusiness = isBusinessSenderStrict(rawAddress, bankTokens)

            // Decision logic:
            // - Strict path: require a transaction verb AND an amount
            // - Allow: sender is bank-like AND body has a transaction verb (amount may be absent in some bank alerts)
            val accept = when {
                hasTransactionVerb && hasAmount -> true
                senderIsBusiness && hasTransactionVerb -> true
                else -> false
            }

            if (!accept) continue

            val date = c.getLong(dateIdx)
            results.add(RawSms(id, rawAddress, body, date))
        }

        Log.d("SMSReader", "Strict Scan Complete. Scanned: $totalScanned. Imported: ${results.size}")
    }

    return results
}

/**
 * More conservative business-sender heuristic:
 * - Numeric shortcodes (3-6 digits) -> business
 * - Alphanumeric shortcodes (3-12 chars) -> business
 * - Contains known bank tokens -> business
 */
private fun isBusinessSenderStrict(address: String?, bankTokens: List<String>): Boolean {
    if (address.isNullOrBlank()) return false
    val s = address.trim()
    val sUpper = s.uppercase(Locale.getDefault())

    // numeric shortcode e.g., 575756
    if (s.matches(Regex("^\\d{3,6}\$"))) return true

    // alphanumeric shortcodes (HDFCBK, AXISBK, AMZ-PAY)
    if (sUpper.matches(Regex("^[A-Z0-9\\-]{3,12}\$"))) return true

    // contains bank token
    if (bankTokens.any { sUpper.contains(it) }) return true

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
