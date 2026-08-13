package com.grandcouncil.remote.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grandcouncil.remote.ui.theme.DensityPreset
import com.grandcouncil.remote.ui.theme.ThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore by preferencesDataStore(name = "app_preferences")

/** App 级偏好（主题/密度），DataStore 持久化 */
class AppPreferences(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_preset")
    private val densityKey = stringPreferencesKey("density_preset")

    val theme: Flow<ThemePreset> = context.appDataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() }
            ?: ThemePreset.WARM // 默认暖阳
    }

    val density: Flow<DensityPreset> = context.appDataStore.data.map { prefs ->
        prefs[densityKey]?.let { runCatching { DensityPreset.valueOf(it) }.getOrNull() }
            ?: DensityPreset.COMFORTABLE // 默认清爽
    }

    suspend fun setTheme(preset: ThemePreset) {
        context.appDataStore.edit { it[themeKey] = preset.name }
    }

    suspend fun setDensity(preset: DensityPreset) {
        context.appDataStore.edit { it[densityKey] = preset.name }
    }
}
