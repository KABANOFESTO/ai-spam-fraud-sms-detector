package com.smsai.smsfrauddetector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smsai.smsfrauddetector.worker.SmsAnalysisWorker

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent?.action) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = messages.joinToString(separator = " ") { it.messageBody.orEmpty() }.trim()
        if (body.isBlank()) return

        val request = OneTimeWorkRequestBuilder<SmsAnalysisWorker>()
            .setInputData(
                Data.Builder()
                    .putString(SmsAnalysisWorker.KEY_SMS_BODY, body)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

