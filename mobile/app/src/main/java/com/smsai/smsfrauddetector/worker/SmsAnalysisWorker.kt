package com.smsai.smsfrauddetector.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsai.smsfrauddetector.MainActivity
import com.smsai.smsfrauddetector.SmsFraudApplication
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.core.notification.NotificationHelper

class SmsAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val body = inputData.getString(KEY_SMS_BODY)?.trim().orEmpty()
        if (body.isBlank()) return Result.success()

        val app = applicationContext as SmsFraudApplication
        val repository = app.container.repository
        if (!repository.currentSmsMonitoring()) {
            return Result.success()
        }

        return when (val result = repository.analyze(body)) {
            is ApiResult.Success -> {
                if (result.data.isSuspicious) {
                    postSuspiciousNotification(
                        title = "Suspicious SMS detected",
                        text = result.data.explanation ?: "The message was classified as ${result.data.prediction}.",
                    )
                }
                Result.success()
            }
            is ApiResult.Error -> Result.retry()
            else -> Result.success()
        }
    }

    private fun postSuspiciousNotification(title: String, text: String) {
        NotificationHelper.ensureChannel(applicationContext)

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_SMS_BODY = "sms_body"
        private const val NOTIFICATION_ID = 4001
    }
}
