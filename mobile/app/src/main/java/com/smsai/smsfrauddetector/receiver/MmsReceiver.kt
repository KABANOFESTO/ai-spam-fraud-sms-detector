package com.smsai.smsfrauddetector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        // MMS delivery support is registered so the app is recognized as a full SMS handler.
        // The app currently focuses on SMS fraud analysis, so MMS payload parsing is intentionally deferred.
    }
}
