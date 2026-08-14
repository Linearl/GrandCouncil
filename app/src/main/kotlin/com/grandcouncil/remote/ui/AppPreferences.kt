package com.grandcouncil.remote.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    // A4 生物识别锁开关
    private val biometricLockKey = booleanPreferencesKey("biometric_lock")
    // P2 AMOLED 纯黑（dark 模式背景强制纯黑）
    private val amoledKey = booleanPreferencesKey("amoled_dark")
    // P2 动态取色（Android 12+ 跟随壁纸）
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    // C5 启动恢复：上次连接与会话（浏览位置）
    private val lastProfileIdKey = stringPreferencesKey("last_profile_id")
    private val lastSessionIdKey = stringPreferencesKey("last_session_id")

    val theme: Flow<ThemePreset> = context.appDataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() }
            ?: ThemePreset.WARM // 默认暖阳
    }

    val density: Flow<DensityPreset> = context.appDataStore.data.map { prefs ->
        prefs[densityKey]?.let { runCatching { DensityPreset.valueOf(it) }.getOrNull() }
            ?: DensityPreset.COMFORTABLE // 默认清爽
    }

    val lastProfileId: Flow<String?> = context.appDataStore.data.map { prefs -> prefs[lastProfileIdKey] }
    val lastSessionId: Flow<String?> = context.appDataStore.data.map { prefs -> prefs[lastSessionIdKey] }

    val biometricLock: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[biometricLockKey] ?: false // 默认关闭
    }

    /** P2 AMOLED 纯黑：dark 模式背景/表面强制 0xFF000000（默认关闭） */
    val amoledDark: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[amoledKey] ?: false
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.appDataStore.edit { it[amoledKey] = enabled }
    }

    /** P2 动态取色：Android 12+ 跟随壁纸生成 Material You 配色（默认关闭） */
    val dynamicColor: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.appDataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.appDataStore.edit { it[biometricLockKey] = enabled }
    }

    suspend fun setLastPosition(profileId: String?, sessionId: String?) {
        context.appDataStore.edit {
            if (profileId == null) it.remove(lastProfileIdKey) else it[lastProfileIdKey] = profileId
            if (sessionId == null) it.remove(lastSessionIdKey) else it[lastSessionIdKey] = sessionId
        }
    }

    suspend fun setTheme(preset: ThemePreset) {
        context.appDataStore.edit { it[themeKey] = preset.name }
    }

    suspend fun setDensity(preset: DensityPreset) {
        context.appDataStore.edit { it[densityKey] = preset.name }
    }
}
