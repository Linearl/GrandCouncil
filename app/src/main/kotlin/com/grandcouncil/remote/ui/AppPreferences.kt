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
    // 聊天滚动跟随：生成时自动滚动到底（默认开）
    private val autoScrollKey = booleanPreferencesKey("auto_scroll_follow")
    // C5 启动恢复：上次连接与会话（浏览位置）
    private val lastProfileIdKey = stringPreferencesKey("last_profile_id")
    private val lastSessionIdKey = stringPreferencesKey("last_session_id")
    // 联网搜索意图注入：开关 + 可自定义注入文本
    private val webSearchKey = booleanPreferencesKey("web_search_inject")
    private val webSearchPromptKey = stringPreferencesKey("web_search_prompt")
    // 思考档位：auto / disabled / low / high / max（对标 desktop /effort）
    private val effortKey = stringPreferencesKey("effort_level")

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

    /** 联网搜索意图注入开关（默认关；开启后发送时消息末尾追加注入文本） */
    val webSearch: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[webSearchKey] ?: false
    }

    suspend fun setWebSearch(enabled: Boolean) {
        context.appDataStore.edit { it[webSearchKey] = enabled }
    }

    /** 联网注入文本（默认值见 DEFAULT_WEB_SEARCH_PROMPT，设置页可自定义） */
    val webSearchPrompt: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[webSearchPromptKey] ?: DEFAULT_WEB_SEARCH_PROMPT
    }

    suspend fun setWebSearchPrompt(text: String) {
        context.appDataStore.edit { it[webSearchPromptKey] = text }
    }

    /** 思考档位（默认 auto=不干预；对应 /effort 斜杠命令） */
    val effortLevel: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[effortKey] ?: "auto"
    }

    suspend fun setEffortLevel(level: String) {
        context.appDataStore.edit { it[effortKey] = level }
    }

    companion object {
        /** 联网注入默认文案（文档 §2.2；开启联网开关时追加到用户消息末尾） */
        const val DEFAULT_WEB_SEARCH_PROMPT =
            "（请优先联网搜索最新信息后再回答：涉及时效性、新闻、价格、规格、事实核实时必须搜索；回答中标注信息来源/链接；搜索不到再基于已有知识回答并说明）"
    }

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

    /** 聊天滚动跟随：生成时自动滚动到底（默认开启） */
    val autoScroll: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[autoScrollKey] ?: true
    }

    suspend fun setAutoScroll(enabled: Boolean) {
        context.appDataStore.edit { it[autoScrollKey] = enabled }
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
