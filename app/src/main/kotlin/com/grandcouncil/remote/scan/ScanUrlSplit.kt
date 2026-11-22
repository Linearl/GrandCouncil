package com.grandcouncil.remote.scan

/**
 * tl1539 ②：扫码结果拆两字段（纯 JVM 实现，可单测）。
 *
 * 口径（tl1536 交付报告联调说明②）：Base URL 字段不收 query——
 * 扫到 `https://host?token=abc` 时 Base URL 放干净地址、token 拆进
 * 认证字段；无 query 或无 token 参数时原样返回。
 * 非 token 的 query 参数无表单承载字段，按口径丢弃。
 *
 * @return (干净 Base URL, token 或 null)
 */
fun splitScannedUrl(url: String): Pair<String, String?> {
    val qIdx = url.indexOf('?')
    if (qIdx < 0) return url to null
    val base = url.substring(0, qIdx)
    val query = url.substring(qIdx + 1).substringBefore('#')
    val token = query.split('&')
        .firstOrNull { it.startsWith("token=") }
        ?.substringAfter('=')
    return base to token?.takeIf { it.isNotBlank() }
}
