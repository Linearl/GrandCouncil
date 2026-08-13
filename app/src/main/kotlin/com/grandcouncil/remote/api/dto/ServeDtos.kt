package com.grandcouncil.remote.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * serve 协议 DTO（v1.21.3+ 源码确认：internal/serve/serve.go sessions/status handler）。
 * 统一 ignoreUnknownKeys：serve 协议随版本演进，客户端做 DTO 容错（风险对策）。
 */

/** GET /sessions 响应元素（serve.go:1477 sessionEntry） */
@Serializable
data class SessionEntryDto(
    val name: String,
    val path: String = "",
    val title: String? = null,
    val turns: Int? = null,
    val current: Boolean? = null,
    /** lease 归属："me"（本 serve）/ "other"（桌面版等其他进程）/ ""（空闲） */
    val heldBy: String? = null,
)

/** GET /status 响应（serve.go:1369，宽松解析——只取握手特征字段） */
@Serializable
data class StatusDto(
    val label: String? = null,
    val running: Boolean? = null,
    val plan: Boolean? = null,
    val goal: String? = null,
    val goalStatus: String? = null,
    val cwd: String? = null,
    val used: JsonObject? = null,
    val window: JsonObject? = null,
)
