package com.grandcouncil.remote.repository

import com.grandcouncil.remote.agent.AgentAdapterFactory
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.model.RemoteSession

/**
 * 会话仓库：会话列表/切换/删除，经 AgentAdapter——
 * 仓库与 UI 不感知具体 agent 协议，协议差异在 adapter 内隔离。
 */
class SessionRepository {

    /** 列出指定远端连接的会话（M1 核心链路） */
    suspend fun listSessions(profile: ConnectionProfile): Result<List<RemoteSession>> =
        runCatching {
            AgentAdapterFactory.create(profile).listSessions()
        }
}
