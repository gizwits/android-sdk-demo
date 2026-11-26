package com.gizwits.smart.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalPreferencesDataSource @Inject constructor(
    private val appPreferences: DataStore<Preferences>
) {

    val themeMode: Flow<ThemeMode> = appPreferences.data.map { preferences ->
        ThemeMode.values().firstOrNull { it.preferenceValue == preferences[KEY_THEME_MODE] } ?: ThemeMode.System
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appPreferences.edit { it[KEY_THEME_MODE] = mode.preferenceValue }
    }

    val isAgreePrivacyPolicy: Flow<Boolean> = appPreferences.data
        .map { it[KEY_IS_AGREE_PRIVACY_POLICY] ?: false }

    val defaultLoginCredentials: Flow<Pair<String, String>?> = appPreferences.data
        .map {
            val userName: String? = it[KEY_DEFAULT_USER_NAME]
            val userPassword: String? = it[KEY_DEFAULT_USER_PASSWORD]
            if (userName != null && userPassword != null) {
                Pair(userName, userPassword)
            } else {
                null
            }
        }

    suspend fun agreePrivacyPolicy() {
        appPreferences.edit {
            it[KEY_IS_AGREE_PRIVACY_POLICY] = true
        }
    }

    suspend fun setDefaultLoginCredentials(userName: String, userPassword: String) {
        appPreferences.edit {
            it[KEY_DEFAULT_USER_NAME] = userName
            it[KEY_DEFAULT_USER_PASSWORD] = userPassword
        }
    }

    suspend fun resetDefaultLoginCredentials() {
        appPreferences.edit {
            it.remove(KEY_DEFAULT_USER_NAME)
            it.remove(KEY_DEFAULT_USER_PASSWORD)
        }
    }

    private companion object {

        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        val KEY_IS_AGREE_PRIVACY_POLICY = booleanPreferencesKey("is_agree_privacy_policy")

        val KEY_DEFAULT_USER_NAME = stringPreferencesKey("default_user_name")

        val KEY_DEFAULT_USER_PASSWORD = stringPreferencesKey("default_user_password")

    }

}
