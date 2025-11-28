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

fun readSmsInbox(context: Context, limit: Int = 200): List<RawSms> {
    val uriSms: Uri = Uri.parse("content://sms/inbox")
    val cursor: Cursor? = context.contentResolver.query(
        uriSms,
        arrayOf("_id", "address", "body", "date"),
        null,
        null,
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
