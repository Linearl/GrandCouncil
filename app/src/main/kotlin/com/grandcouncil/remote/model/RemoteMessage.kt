package com.grandcouncil.remote.model

/**
 * 消息模型（含 tool use/审批状态）。M2 聊天功能使用，M1 先定结构。
 * 计划书 3.2：RemoteMessage — 消息模型（含 tool use/审批状态）
 */
data class RemoteMessage(
    val id: String,
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    /** 是否等待审批 */
    val pendingApproval: Boolean = false,
    val timestamp: Long? = null,
)

enum class Role { USER, ASSISTANT, TOOL }

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val status: ToolCallStatus = ToolCallStatus.PENDING,
)

enum class ToolCallStatus { PENDING, RUNNING, APPROVED, DENIED, DONE }
