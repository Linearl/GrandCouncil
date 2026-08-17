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
    /** http(s)://host:port，如 https://xxx.iepose.cn / http://100.x.x.x:8787 */
    val baseUrl: String,
    val authMode: AuthMode = AuthMode.NONE,
    /** 网关项目路由前缀（单入口网关场景）：非空时 baseUrl 拼接 /p/<projectId>/ */
    val projectId: String? = null,
    // TODO(M2 安全基线)：token/password 迁移到 Android Keystore/EncryptedSharedPreferences，
    //  禁止明文落盘（第 3 条）
    val token: String = "",
    val password: String = "",
    val timeoutMs: Long = 10_000L,
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

    companion object {
        fun newId(): String = "conn-${java.util.UUID.randomUUID()}"
    }
}

/** serve 认证模式（auth_mode = none | token | password） */
@Serializable
enum class AuthMode(val label: String) {
    NONE("无认证"),
    TOKEN("Token"),
    PASSWORD("密码"),
}
