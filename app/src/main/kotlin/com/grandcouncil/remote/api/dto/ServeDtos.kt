package com.grandcouncil.remote.api.dto

import kotlinx.serialization.Serializable

/**
 * serve 协议 DTO（v1.21.3+ 源码确认：internal/serve/serve.go sessions/status handler）。
 * 统一 ignoreUnknownKeys：serve 协议随版本演进，客户端做 DTO 容错（风险对策）。
 */

/** GET /manifest 响应元素（网关 /manifest：servepool.ProjectState） */
@Serializable
data class ProjectEntryDto(
    val id: String,
    val name: String = "",
    val root: String = "",
    /** stopped | starting | running | degraded | failed */
    val state: String = "stopped",
    val color: String = "",
    val sessions: Int? = null,
    val err: String? = null,
)

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

/** GET /projects 响应元素（serve.go projects.go projectEntry）：一个项目的全部会话 */
@Serializable
data class ProjectSessionsDto(
    val root: String = "",
    val name: String = "",
    val sessions: List<SessionEntryDto> = emptyList(),
)

/** POST /attachments 响应（serve.go attachments.go）：图片上传后返回 @ref 引用路径 */
@Serializable
data class AttachmentRefDto(
    val ref: String = "",
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
    /** 实测为整数（token 数），源码 ContextSnapshot 返回 map[string]int */
    val used: Int? = null,
    val window: Int? = null,
    /** 工具审批模式：ask | auto | yolo（POST /tool-approval-mode 切换） */
    val toolApprovalMode: String? = null,
)

/** GET /history 响应元素（serve.go:838 historyMessage，JSON 数组） */
@Serializable
data class HistoryMessageDto(
    val role: String = "",
    val content: String = "",
    val reasoning: String? = null,
    val toolCalls: List<HistoryToolCallDto>? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
)

@Serializable
data class HistoryToolCallDto(
    val id: String = "",
    val name: String = "",
    val arguments: String = "",
)

// ---- SSE 事件（serve /events，源码 internal/event/event.go + Web UI 解析确认） ----

/** /events 流事件（扁平结构：kind + 各 payload 字段共存） */
@Serializable
data class ServeEventDto(
    val kind: String = "",
    val text: String = "",
    val reasoning: String = "",
    val tool: ToolEventDto? = null,
    val approval: ApprovalEventDto? = null,
    val err: String? = null,
    val outcome: String? = null,
)

/** tool_dispatch / tool_result 的 tool 字段 */
@Serializable
data class ToolEventDto(
    val id: String = "",
    val name: String = "",
    val args: String = "",
    val output: String = "",
    val err: String = "",
    val readOnly: Boolean = false,
    val durationMs: Long = 0,
)

/** approval_request 的 approval 字段 */
@Serializable
data class ApprovalEventDto(
    val id: String = "",
    val tool: String = "",
    val subject: String = "",
)

/** 事件 kind 常量（serve Web UI index.html switch 确认） */
object ServeEventKind {
    const val TURN_STARTED = "turn_started"
    const val REASONING = "reasoning"
    const val TEXT = "text"
    const val MESSAGE = "message"
    const val TOOL_DISPATCH = "tool_dispatch"
    const val TOOL_RESULT = "tool_result"
    const val TOOL_PROGRESS = "tool_progress"
    const val USAGE = "usage"
    const val NOTICE = "notice"
    const val PHASE = "phase"
    const val APPROVAL_REQUEST = "approval_request"
    const val ASK_REQUEST = "ask_request"
    const val TURN_DONE = "turn_done"
}

/** GET /models 响应元素（serve.go:1258 modelEntry） */
@Serializable
data class ModelEntryDto(
    val ref: String = "",
    val provider: String = "",
    val model: String = "",
    val kind: String? = null,
    val active: Boolean = false,
    val default: Boolean = false,
)
