package com.grandcouncil.remote.agent

import com.grandcouncil.remote.api.ReasonixApi
import com.grandcouncil.remote.model.AgentType
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteSession

/**
 * Reasonix serve 适配实现（v1，26 端点协议 M0 已实测验证）。
 * M1 实现 listSessions；其余方法走 AgentAdapter 默认实现（M2/M3 启用）。
 */
class ReasonixAdapter(
    private val api: ReasonixApi,
) : AgentAdapter {

    override val agentType: AgentType = AgentType.REASONIX

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
}
