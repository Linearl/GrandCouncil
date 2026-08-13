package com.grandcouncil.remote.repository

import com.grandcouncil.remote.agent.AgentAdapterFactory
import com.grandcouncil.remote.api.HttpClientFactory
import com.grandcouncil.remote.api.dto.StatusDto
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.model.RemoteMessage
import com.grandcouncil.remote.model.RemoteSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 会话仓库：会话列表/只读历史/服务器状态，经 AgentAdapter——
 * 仓库与 UI 不感知具体 agent 协议，协议差异在 adapter 内隔离。
 */
class SessionRepository {

    /** 列出指定远端连接的会话（M1 核心链路） */
    suspend fun listSessions(profile: ConnectionProfile): Result<List<RemoteSession>> =
        runCatching {
            AgentAdapterFactory.create(profile).listSessions()
        }

    /** 只读加载会话历史（内部切换 serve 当前会话） */
    suspend fun loadHistory(
        profile: ConnectionProfile,
        session: RemoteSession,
    ): Result<List<RemoteMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            AgentAdapterFactory.create(profile).loadSessionHistory(session)
        }
    }

    /** serve 状态（会话页服务器信息行） */
    suspend fun getStatus(profile: ConnectionProfile): Result<StatusDto> =
        runCatching {
            HttpClientFactory.createApi(profile).getStatus()
        }

    /** 发送消息（state-changing，JSON Content-Type） */
    suspend fun submit(profile: ConnectionProfile, input: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                HttpClientFactory.createApi(profile).submit(
                    buildJsonObject { put("input", JsonPrimitive(input)) },
                )
            }.map { }
        }

    /** 审批回复 */
    suspend fun approve(profile: ConnectionProfile, body: JsonObject): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { HttpClientFactory.createApi(profile).approve(body) }.map { }
        }

    /** 取消当前回合 */
    suspend fun cancel(profile: ConnectionProfile): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { HttpClientFactory.createApi(profile).cancel() }.map { }
        }

    /** 切换工具审批模式（ask/auto/yolo，对应 PC 端三档） */
    suspend fun setApprovalMode(profile: ConnectionProfile, mode: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                HttpClientFactory.createApi(profile).toolApprovalMode(
                    buildJsonObject { put("mode", JsonPrimitive(mode)) },
                )
            }.map { }
        }
}
