package com.grandcouncil.remote.connection

import java.net.URLDecoder

/**
 * tl1552：serve 配对 URL 形态解析（tl1536 扫码交付同形态）。
 * 形态：`http(s)://host[:port][/路径]?token=xxx[&其他参数]`——token 字段名与
 * gateway checkToken 一致。解析结果供连接表单回填：baseUrl 去 query、token 拆到凭据字段。
 * 纯 Kotlin 实现（不依赖 android.net.Uri），可在本地单元测试直接覆盖。
 */
data class ParsedServeUrl(
    /** 去 query 后的基础地址（scheme/host/port/路径原样保留） */
    val baseUrl: String,
    /** query 中的 token（无则空串） */
    val token: String,
)

fun parseServePairingUrl(raw: String): ParsedServeUrl {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ParsedServeUrl(baseUrl = "", token = "")
    val queryStart = trimmed.indexOf('?')
    if (queryStart < 0) return ParsedServeUrl(baseUrl = trimmed, token = "")
    val base = trimmed.substring(0, queryStart)
    val query = trimmed.substring(queryStart + 1)
    var token = ""
    query.split('&').forEach { pair ->
        if (pair.isEmpty()) return@forEach
        val eq = pair.indexOf('=')
        val name = if (eq < 0) pair else pair.substring(0, eq)
        val value = if (eq < 0) "" else pair.substring(eq + 1)
        if (decodeSafe(name) == "token") {
            token = decodeSafe(value)
        }
    }
    return ParsedServeUrl(baseUrl = base, token = token)
}

/** URLDecoder 对非法 % 序列抛 IllegalArgumentException——扫码/手输不可控，降级取原样 */
private fun decodeSafe(s: String): String =
    runCatching { URLDecoder.decode(s, Charsets.UTF_8) }.getOrDefault(s)
