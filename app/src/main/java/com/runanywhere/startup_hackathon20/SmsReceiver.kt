package com.runanywhere.startup_hackathon20


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "SMS received")

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            var fullMessage = ""
            var sender = ""

            for (sms in smsMessages) {
                if (sender.isEmpty()) {
                    sender = sms.originatingAddress ?: "Unknown"
                }
                fullMessage += sms.messageBody
            }

            Log.d("SmsReceiver", "From: $sender, Message: $fullMessage")

            // Check if it's a bank SMS (you can customize this logic)
            if (isBankSms(sender, fullMessage)) {
                // Process in background using WorkManager
                processSmsInBackground(context, sender, fullMessage)
            }
        }
    }

    private fun isBankSms(sender: String, message: String): Boolean {
        // Check for common bank SMS patterns
        val bankKeywords = listOf(
            "bank", "debit", "credit", "withdrawal", "deposit",
            "transaction", "balance", "upi", "neft", "imps",
            "account", "rs.", "inr", "card"
        )

        val senderLower = sender.toLowerCase()
        val messageLower = message.toLowerCase()

        // Check if sender contains bank keywords
        val commonBankSenders = listOf(
            "axisbank", "hdfc", "icici", "sbi", "kotak",
            "yesbank", "indusind", "pnb", "bob", "boi"
        )

        return commonBankSenders.any { senderLower.contains(it) } ||
                bankKeywords.any { messageLower.contains(it) }
    }

    private fun processSmsInBackground(context: Context, sender: String, message: String) {
        val workRequest = OneTimeWorkRequestBuilder<SmsProcessingWorker>()
            .setInputData(
                workDataOf(
                    "sender" to sender,
                    "message" to message,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d("SmsReceiver", "Scheduled SMS processing in background")
    }
}