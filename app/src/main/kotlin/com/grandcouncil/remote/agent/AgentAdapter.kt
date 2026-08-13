package com.grandcouncil.remote.agent

import com.grandcouncil.remote.model.AgentType
import com.grandcouncil.remote.model.RemoteEvent
import com.grandcouncil.remote.model.RemoteSession
import kotlinx.coroutines.flow.Flow

/**
 * 多 agent 适配层抽象（核心差异化）：
 * 覆盖 Reasonix 与 MiMo Code serve 的共同协议子集
 * （session/message/event/permission/abort/fork/revert/summarize/todo）。
 * 协议差异在 adapter 内隔离，UI/Repository 层只见 agent 无关的 model。
 *
 * v1 只实现 ReasonixAdapter；MiMoCodeAdapter 为 v2 接入目标。
 */
interface AgentAdapter {

    val agentType: AgentType

    /** 会话列表（M1 实现） */
    suspend fun listSessions(): List<RemoteSession>

    /** 会话详情/历史加载（只读；内部会切换 serve 当前会话） */
    suspend fun loadSessionHistory(session: RemoteSession): List<com.grandcouncil.remote.model.RemoteMessage> =
        throw UnsupportedOperationException("loadSessionHistory 待实现")

    /** 发送消息（M2） */
    suspend fun submitMessage(sessionId: String?, message: String): Unit =
        throw UnsupportedOperationException("submitMessage 在 M2 实现")

    /** 事件流（M2：GET /events SSE 流式聊天） */
    fun streamEvents(sessionId: String?): Flow<RemoteEvent> =
        throw UnsupportedOperationException("streamEvents 在 M2 实现")

    /** 审批通过（M2） */
    suspend fun approve(requestId: String): Unit =
        throw UnsupportedOperationException("approve 在 M2 实现")

    /** 取消当前任务（M2） */
    suspend fun abort(): Unit =
        throw UnsupportedOperationException("abort 在 M2 实现")

    /** 历史操作（M3：fork/rewind/summarize） */
    suspend fun fork(sessionId: String): Unit =
        throw UnsupportedOperationException("fork 在 M3 实现")

    /** Todo 列表（M3） */
    suspend fun getTodos(): List<String> =
        throw UnsupportedOperationException("getTodos 在 M3 实现")
}
