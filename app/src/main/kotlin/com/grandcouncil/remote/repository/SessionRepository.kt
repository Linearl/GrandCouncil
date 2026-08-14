package com.grandcouncil.remote.repository

import com.grandcouncil.remote.agent.AgentAdapterFactory
import com.grandcouncil.remote.api.HttpClientFactory
import com.grandcouncil.remote.api.dto.ModelEntryDto
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

    /** 列出指定远端连接的会话（M1 核心链路；过滤 -recovery- 恢复副本，serve 端未过滤的兜底） */
    suspend fun listSessions(profile: ConnectionProfile): Result<List<RemoteSession>> =
        runCatching {
            AgentAdapterFactory.create(profile).listSessions()
                .filter { s -> !s.path.contains("-recovery-") }
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
        runCatching {            HttpClientFactory.createApi(profile).getStatus()
        }

    /** T7 上下文用量：GET /context → (used, window) token 数 */
    suspend fun getContext(profile: ConnectionProfile): Result<Pair<Int, Int>> =
        runCatching {
            val json = HttpClientFactory.createApi(profile).context(null)
            val used = (json["used"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            val window = (json["window"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            used to window
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

    /** 新建会话（POST /new，204） */
    suspend fun newSession(profile: ConnectionProfile): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                HttpClientFactory.createApi(profile).newSession()
            }.map { }
        }

    /** 删除会话（body: {"name": "<会话名不带 .jsonl>"}，源码确认） */
    suspend fun deleteSession(profile: ConnectionProfile, name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                HttpClientFactory.createApi(profile).deleteSession(
                    buildJsonObject { put("name", JsonPrimitive(name)) },
                )
            }.map { }
        }

    /** plan 模式开关（执行-规划双模型） */
    suspend fun setPlanMode(profile: ConnectionProfile, on: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                HttpClientFactory.createApi(profile).plan(
                    buildJsonObject { put("on", JsonPrimitive(on)) },
                )
            }.map { }
        }

    /** 模型列表（GET /models） */
    suspend fun models(profile: ConnectionProfile): Result<List<ModelEntryDto>> =
        withContext(Dispatchers.IO) {
            runCatching { HttpClientFactory.createApi(profile).models() }
        }

    /** 切换模型（serve 拦截 submit 的 /model <ref> 斜杠命令） */
    suspend fun switchModel(profile: ConnectionProfile, ref: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                HttpClientFactory.createApi(profile).submit(
                    buildJsonObject { put("input", JsonPrimitive("/model $ref")) },
                )
            }.map { }
        }
}
