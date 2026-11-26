package com.gizwits.smart.app.core.data.repository

import com.gizwits.smart.app.core.datastore.LocalPreferencesDataSource
import com.gizwits.smart.app.core.datastore.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppPreferencesRepository @Inject constructor(
    private val localPreferencesDataSource: LocalPreferencesDataSource
) {

    val themeMode: Flow<ThemeMode> = localPreferencesDataSource.themeMode

    suspend fun setThemeMode(mode: ThemeMode) = localPreferencesDataSource.setThemeMode(mode)

    val isAgreePrivacyPolicy: Flow<Boolean> = localPreferencesDataSource.isAgreePrivacyPolicy

    val defaultLoginCredentials: Flow<Pair<String, String>?> = localPreferencesDataSource.defaultLoginCredentials

    suspend fun agreePrivacyPolicy() = localPreferencesDataSource.agreePrivacyPolicy()

    suspend fun setDefaultLoginCredentials(userName: String, userPassword: String) =
        localPreferencesDataSource.setDefaultLoginCredentials(userName, userPassword)

    suspend fun resetDefaultLoginCredentials() =
        localPreferencesDataSource.resetDefaultLoginCredentials()

}
