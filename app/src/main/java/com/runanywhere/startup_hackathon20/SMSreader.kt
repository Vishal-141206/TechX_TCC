package com.runanywhere.startup_hackathon20

import android.content.Context
import android.database.Cursor
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*

data class RawSms(
    val id: String,
    val address: String?,
    val body: String?,
    val date: Long
)

/**
 * Reads SMS from Inbox.
 * @param limit Maximum number of messages to read.
 * @param daysLookBack If set, only read messages from the last N days.
 */
fun readSmsInbox(context: Context, limit: Int = 1000, daysLookBack: Int? = null): List<RawSms> {
    val uriSms: Uri = Uri.parse("content://sms/inbox")
    
    var selection: String? = null
    var selectionArgs: Array<String>? = null

    if (daysLookBack != null) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysLookBack)
        val minDate = calendar.timeInMillis
        selection = "date >= ?"
        selectionArgs = arrayOf(minDate.toString())
    }

    val cursor: Cursor? = context.contentResolver.query(
        uriSms,
        arrayOf("_id", "address", "body", "date"),
        selection,
        selectionArgs,
        "date DESC"
    )

    val results = mutableListOf<RawSms>()

    cursor?.use {
        var count = 0
        while (it.moveToNext() && count < limit) {
            val id = it.getString(it.getColumnIndexOrThrow("_id"))
            val address = it.getString(it.getColumnIndexOrThrow("address"))
            val body = it.getString(it.getColumnIndexOrThrow("body"))
            val date = it.getLong(it.getColumnIndexOrThrow("date"))

            results.add(RawSms(id, address, body, date))
            count++
        }
    }

    return results
}

fun formatDate(millis: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(millis))
    } catch (e: Exception) {
        ""
    }
}