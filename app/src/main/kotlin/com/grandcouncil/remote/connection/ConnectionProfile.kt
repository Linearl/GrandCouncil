package com.grandcouncil.remote.connection

import com.grandcouncil.remote.model.AgentType
import kotlinx.serialization.Serializable

/**
 * 连接配置：对 App 透明——无论哪种穿透方式，
 * App 只按 Base URL 发起 HTTP/SSE，穿透差异全在 URL 层面。
 */
@Serializable
data class ConnectionProfile(
    val id: String,
    val name: String,
    val agentType: AgentType = AgentType.REASONIX,
    /** 穿透方式预设（向导/帮助文案用） */
    val type: ConnectionType = ConnectionType.LAN,
    /** http(s)://host:port，如 https://xxx.iepose.cn / http://100.x.x.x:18789 */
    val baseUrl: String,
    val authMode: AuthMode = AuthMode.NONE,
    /** 网关项目路由前缀（单入口网关场景）：非空时 baseUrl 拼接 /p/<projectId>/ */
    val projectId: String? = null,
    // TODO(M2 安全基线)：token/password 迁移到 Android Keystore/EncryptedSharedPreferences，
    //  禁止明文落盘（第 3 条）
    val token: String = "",
    val password: String = "",
    /** serve pool 项目颜色（来自 /manifest color），用于会话列表着色 */
    val color: String = "",
    val timeoutMs: Long = 20_000L,
    // TODO(M2)：自签名 TLS 开关——需自定义 TrustManager（穿透域名证书可选）
    val allowSelfSignedTls: Boolean = false,
) {
    /** 规范化 baseUrl：补 scheme、确保以 / 结尾（Retrofit 要求） */
    fun normalizedBaseUrl(): String {
        var url = baseUrl.trim()
        if (!projectId.isNullOrBlank()) {
            val prefix = "/p/" + projectId.trim().trim('/')
            url = url.trimEnd('/') + prefix + "/"
        }
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) url = "$url/"
        return url
    }

    /**
     * 连接身份键（任务 542）：baseUrl 归一化后的比较键——同一 serve 只算一台设备。
     * 规则：scheme/host 小写、去尾斜杠与路径、默认端口省略（http:80 / https:443）、
     * 保留 scheme 语义（http ≠ https）。projectId 不参与（运行时注入的路由前缀）。
     */
    fun identityKey(): String {
        var url = baseUrl.trim()
        if (url.isEmpty()) return url
        val scheme: String
        val rest: String
        when {
            url.startsWith("http://") -> { scheme = "http"; rest = url.removePrefix("http://") }
            url.startsWith("https://") -> { scheme = "https"; rest = url.removePrefix("https://") }
            else -> { scheme = "http"; rest = url }
        }
        // 去尾斜杠与路径（设备身份只看 host:port；/p/<id> 前缀是运行时拼接的）
        var hostPort = rest.trimEnd('/').substringBefore('/')
        val host: String
        var port: String? = null
        val colon = hostPort.lastIndexOf(':')
        if (colon > hostPort.lastIndexOf(']')) {
            host = hostPort.substring(0, colon)
            port = hostPort.substring(colon + 1).takeIf { it.isNotEmpty() }
        } else {
            host = hostPort
        }
        if ((scheme == "http" && port == "80") || (scheme == "https" && port == "443")) {
            port = null
        }
        return "$scheme://${host.lowercase()}${if (port != null) ":$port" else ""}"
    }

    companion object {
        fun newId(): String = "conn-${java.util.UUID.randomUUID()}"

        /** 按身份键去重（任务 542）：同一 serve 只保留最早创建的一条。 */
        fun dedupeByIdentity(profiles: List<ConnectionProfile>): List<ConnectionProfile> {
            val seen = mutableSetOf<String>()
            return profiles.filter { seen.add(it.identityKey()) }
        }

        /** 合并保存（任务 542）：身份键相同的条目被更新（保留原 id），否则追加。 */
        fun mergeByIdentity(
            current: List<ConnectionProfile>,
            profile: ConnectionProfile,
        ): List<ConnectionProfile> {
            val key = profile.identityKey()
            val existing = current.firstOrNull { it.identityKey() == key }
            return if (existing != null) {
                current.map { if (it.id == existing.id) profile.copy(id = existing.id) else it }
            } else {
                current + profile
            }
        }
    }
}

/** serve 认证模式（auth_mode = none | token | password） */
@Serializable
enum class AuthMode(val label: String) {
    NONE("无认证"),
    TOKEN("Token"),
    PASSWORD("密码"),
}
