package com.smsai.smsfrauddetector.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.smsai.smsfrauddetector.data.remote.dto.AuthResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sms_fraud_session")

data class SessionSnapshot(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: UserDto? = null,
    val baseUrl: String? = null,
    val darkMode: Boolean = true,
    val smsMonitoringEnabled: Boolean = false,
)

class SessionStore(
    private val context: Context,
    private val gson: Gson = Gson(),
) {
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val userKey = stringPreferencesKey("user_json")
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val smsMonitoringKey = booleanPreferencesKey("sms_monitoring_enabled")

    val sessionFlow: Flow<SessionSnapshot> = context.dataStore.data.map { prefs ->
        SessionSnapshot(
            accessToken = prefs[accessTokenKey],
            refreshToken = prefs[refreshTokenKey],
            user = prefs[userKey]?.let { runCatching { gson.fromJson(it, UserDto::class.java) }.getOrNull() },
            baseUrl = prefs[baseUrlKey],
            darkMode = prefs[darkModeKey] ?: true,
            smsMonitoringEnabled = prefs[smsMonitoringKey] ?: false,
        )
    }

    suspend fun saveSession(authResponse: AuthResponseDto, baseUrl: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[accessTokenKey] = authResponse.access
            prefs[refreshTokenKey] = authResponse.refresh
            prefs[userKey] = gson.toJson(authResponse.user)
            if (baseUrl != null) {
                prefs[baseUrlKey] = baseUrl
            }
        }
    }

    suspend fun saveProfile(user: UserDto) {
        context.dataStore.edit { prefs ->
            prefs[userKey] = gson.toJson(user)
        }
    }

    suspend fun updateBaseUrl(baseUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl.trim().trimEnd('/')
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[darkModeKey] = enabled
        }
    }

    suspend fun setSmsMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[smsMonitoringKey] = enabled
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(accessTokenKey)
            prefs.remove(refreshTokenKey)
            prefs.remove(userKey)
        }
    }

    suspend fun accessToken(): String? = context.dataStore.data.map { it[accessTokenKey] }.first()
    suspend fun refreshToken(): String? = context.dataStore.data.map { it[refreshTokenKey] }.first()
    suspend fun baseUrl(default: String): String = context.dataStore.data.map { it[baseUrlKey] ?: default }.first()
    suspend fun darkMode(): Boolean = context.dataStore.data.map { it[darkModeKey] ?: true }.first()
    suspend fun smsMonitoringEnabled(): Boolean = context.dataStore.data.map { it[smsMonitoringKey] ?: false }.first()
}
