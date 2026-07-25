package com.smsai.smsfrauddetector

import android.app.Application
import com.smsai.smsfrauddetector.core.notification.NotificationHelper
import com.smsai.smsfrauddetector.di.AppContainer

class SmsFraudApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
    }
}
