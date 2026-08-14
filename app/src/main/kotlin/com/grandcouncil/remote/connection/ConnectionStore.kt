package com.grandcouncil.remote.connection

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grandcouncil.remote.security.CredentialCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.connectionDataStore by preferencesDataStore(name = "connections")

/**
 * 连接配置持久化（DataStore Preferences，JSON 序列化）。
 * A4 安全基线：token/password 在 Store 边界用 Android Keystore AES/GCM 加密后落盘
 * （JSON 里只有密文）；读取时解密回明文。密钥随设备 Keystore，卸载重装自动清除。
 * 老版本明文数据：解密失败时保留原文（平滑迁移）。
 */
class ConnectionStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val profilesKey = stringPreferencesKey("connection_profiles")
    private val activeIdKey = stringPreferencesKey("active_connection_id")

    private val serializer = ListSerializer(ConnectionProfile.serializer())

    /** 连接列表流（token/password 已解密为明文） */
    val profiles: Flow<List<ConnectionProfile>> = context.connectionDataStore.data.map { prefs ->
        val raw = prefs[profilesKey] ?: return@map emptyList()
        runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .map { it.decryptCredentials() }
    }

    /** 当前使用的连接 id 流 */
    val activeId: Flow<String?> = context.connectionDataStore.data.map { it[activeIdKey] }

    suspend fun save(profiles: List<ConnectionProfile>) {
        val encrypted = profiles.map { it.encryptCredentials() }
        context.connectionDataStore.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(serializer, encrypted)
        }
    }

    suspend fun setActive(id: String?) {
        context.connectionDataStore.edit { prefs ->
            if (id == null) prefs.remove(activeIdKey) else prefs[activeIdKey] = id
        }
    }

    /** 加密边界：明文 → 密文（空凭据跳过；加密失败保留原文避免数据丢失） */
    private fun ConnectionProfile.encryptCredentials(): ConnectionProfile = copy(
        token = if (token.isNotBlank()) CredentialCipher.encrypt(token) ?: token else token,
        password = if (password.isNotBlank()) CredentialCipher.encrypt(password) ?: password else password,
    )

    /** 解密边界：密文 → 明文（解密失败视为老明文，保留原文） */
    private fun ConnectionProfile.decryptCredentials(): ConnectionProfile = copy(
        token = if (token.isNotBlank()) CredentialCipher.decrypt(token) ?: token else token,
        password = if (password.isNotBlank()) CredentialCipher.decrypt(password) ?: password else password,
    )
}
