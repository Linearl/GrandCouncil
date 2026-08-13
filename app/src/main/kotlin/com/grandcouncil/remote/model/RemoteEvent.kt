package com.grandcouncil.remote.model

/**
 * SSE 事件模型（agent 无关，M2 聊天流使用）。
 * Reasonix serve SSE 事件序列（HANDOFF 6.1 实测）：
 * reasoning → text → message → usage → turn_done
 */
data class RemoteEvent(
    val type: RemoteEventType,
    val data: String? = null,
    /** 事件游标（断线重连续传用，M4 细化） */
    val lastEventId: String? = null,
)

enum class RemoteEventType {
    /** 推理过程增量 */
    REASONING,
    /** 文本增量 */
    TEXT,
    /** 完整消息（含 tool use/审批） */
    MESSAGE,
    /** 用量统计 */
    USAGE,
    /** 本轮结束 */
    TURN_DONE,
    /** 审批请求（tool approval 卡片） */
    APPROVAL,
    /** 未识别事件 */
    UNKNOWN,
}
