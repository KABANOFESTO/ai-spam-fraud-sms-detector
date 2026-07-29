package com.smsai.smsfrauddetector.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Declares the SMS respond-via-message capability so Android can recognize the app
 * as a valid SMS handler when the user selects the default SMS role.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
