package com.smsai.smsfrauddetector.di

import android.content.Context
import com.google.gson.Gson
import com.smsai.smsfrauddetector.data.local.datastore.SessionStore
import com.smsai.smsfrauddetector.data.repository.AppRepository

class AppContainer(context: Context) {
    private val gson = Gson()
    private val sessionStore = SessionStore(context.applicationContext, gson)
    val repository = AppRepository(context.applicationContext, sessionStore)
    val sessionStoreRef = sessionStore
}

