package com.grandcouncil.remote.agent

import com.grandcouncil.remote.api.ReasonixApi
import com.grandcouncil.remote.api.dto.HistoryMessageDto
import com.grandcouncil.remote.model.AgentType
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteMessage
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.model.Role
import com.grandcouncil.remote.model.ToolCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reasonix serve 适配实现（v1，26 端点协议 M0 已实测验证）。
 * M1 实现 listSessions + loadSessionHistory（只读）；其余方法走 AgentAdapter 默认实现。
 */
class ReasonixAdapter(
    private val api: ReasonixApi,
) : AgentAdapter {

    override val agentType: AgentType = AgentType.REASONIX

    /** A1 能力字典：Reasonix serve 实际支持集 */
    override fun supports(feature: Feature): Boolean = when (feature) {
        Feature.STREAMING, Feature.APPROVAL, Feature.MODELS, Feature.CONTEXT,
        Feature.DELETE_SESSION, Feature.NEW_SESSION, Feature.EXPORT -> true
        // serve 已具备 /release-session + /takeover-session（fork 合入 #8749，2026-08-31）
        Feature.TAKEOVER -> true
    }

    override suspend fun listSessions(): List<RemoteSession> =
        api.listSessions().map { dto ->
            RemoteSession(
                id = dto.name,
                title = dto.title?.takeIf { it.isNotBlank() } ?: dto.name,
                turns = dto.turns ?: 0,
                isCurrent = dto.current ?: false,
                heldBy = when (dto.heldBy) {
                    "me" -> HeldBy.ME
                    "other" -> HeldBy.OTHER
                    else -> HeldBy.FREE
                },
                agent = AgentType.REASONIX,
                path = dto.path,
            )
        }

    override suspend fun loadSessionHistory(session: RemoteSession): List<RemoteMessage> {
        // /history 返回「当前绑定会话」的历史——先 /resume 切换到目标会话
        // （会话被其他进程占用时 serve 返回 409，此处向上抛出由 UI 提示）
        api.resume(JsonObject(mapOf("path" to JsonPrimitive(session.path))))
        return api.history()
            // system 消息（如 20KB 的系统提示）不进入 UI
            .filter { it.role != "system" }
            .map { it.toRemoteMessage() }
    }
}

private fun HistoryMessageDto.toRemoteMessage(): RemoteMessage = RemoteMessage(
    id = toolCallId?.takeIf { it.isNotBlank() } ?: "msg-${content.hashCode()}",
    role = when (role) {
        "user" -> Role.USER
        "assistant" -> Role.ASSISTANT
        "tool" -> Role.TOOL
        "notice" -> Role.NOTICE
        else -> Role.NOTICE
    },
    content = content,
    reasoning = reasoning,
    toolCalls = toolCalls?.map { ToolCall(id = it.id, name = it.name, arguments = it.arguments) }
        ?: emptyList(),
)
