package com.grandcouncil.remote.connection

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.connectionDataStore by preferencesDataStore(name = "connections")

/**
 * 连接配置持久化（DataStore Preferences，JSON 序列化）。
 *
 * TODO(M2 安全基线)：token/password 目前随 JSON 明文落盘（计划书 3.5.6 第 3 条：
 * token 存 Android Keystore/EncryptedSharedPreferences，不落日志）。
 * M1 先以 DataStore 打通链路，M2 迁移加密存储。
 */
class ConnectionStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val profilesKey = stringPreferencesKey("connection_profiles")
    private val activeIdKey = stringPreferencesKey("active_connection_id")

    private val serializer = ListSerializer(ConnectionProfile.serializer())

    /** 连接列表流 */
    val profiles: Flow<List<ConnectionProfile>> = context.connectionDataStore.data.map { prefs ->
        val raw = prefs[profilesKey] ?: return@map emptyList()
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    /** 当前使用的连接 id 流 */
    val activeId: Flow<String?> = context.connectionDataStore.data.map { it[activeIdKey] }

    suspend fun save(profiles: List<ConnectionProfile>) {
        context.connectionDataStore.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(serializer, profiles)
        }
    }

    suspend fun setActive(id: String?) {
        context.connectionDataStore.edit { prefs ->
            if (id == null) prefs.remove(activeIdKey) else prefs[activeIdKey] = id
        }
    }
}
