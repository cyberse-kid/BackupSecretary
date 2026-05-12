package com.clickwise.backupsecretary.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "backup_secretary_prefs")

object TokenManager {

    private val ACCESS_TOKEN  = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")

    suspend fun saveTokens(context: Context, access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN]  = access
            prefs[REFRESH_TOKEN] = refresh
        }
    }

    suspend fun getAccessToken(context: Context): String? {
        return context.dataStore.data
            .map { it[ACCESS_TOKEN] }
            .first()
    }

    suspend fun clearTokens(context: Context) {
        context.dataStore.edit { it.clear() }
    }

    fun bearerToken(token: String) = "Bearer $token"
}