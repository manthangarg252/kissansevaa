package com.kissansevaai.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kissansevaai_prefs")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureTokenStorage: SecureTokenStorage
) {
    companion object {
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val ENGINE_MODE_KEY = stringPreferencesKey("engine_mode")
        private val AUTH_TOKEN_KEY = byteArrayPreferencesKey("auth_jwt_token_encrypted")
    }

    // Keystore-encrypted JWT token stored as encrypted bytes in DataStore, decrypted on emission
    val token: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AUTH_TOKEN_KEY]?.let { encryptedBytes ->
            secureTokenStorage.decrypt(encryptedBytes)
        }
    }

    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME_KEY] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL_KEY] }
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val baseUrl: Flow<String?> = context.dataStore.data.map { it[BASE_URL_KEY] }
    val language: Flow<String> = context.dataStore.data.map { it[LANGUAGE_KEY] ?: "en" }
    val engineMode: Flow<String> = context.dataStore.data.map { it[ENGINE_MODE_KEY] ?: "auto" }

    suspend fun saveAuth(token: String, name: String, email: String, id: String) {
        val encryptedTokenBytes = secureTokenStorage.encrypt(token)
        context.dataStore.edit {
            it[AUTH_TOKEN_KEY] = encryptedTokenBytes
            it[USER_NAME_KEY] = name
            it[USER_EMAIL_KEY] = email
            it[USER_ID_KEY] = id
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit {
            it.remove(AUTH_TOKEN_KEY)
            it.remove(USER_NAME_KEY)
            it.remove(USER_EMAIL_KEY)
            it.remove(USER_ID_KEY)
        }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[BASE_URL_KEY] = url }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[LANGUAGE_KEY] = lang }
    }

    suspend fun setEngineMode(mode: String) {
        context.dataStore.edit { it[ENGINE_MODE_KEY] = mode }
    }
}
