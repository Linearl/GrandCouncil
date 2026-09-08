package com.grandcouncil.remote.repository

import com.grandcouncil.remote.agent.AgentAdapterFactory
import com.grandcouncil.remote.api.HttpClientFactory
import com.grandcouncil.remote.api.dto.ModelEntryDto
import com.grandcouncil.remote.api.dto.ProjectEntryDto
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

    /** 列出 serve pool 网关的项目列表（GET /manifest），供设备→项目分层展示 */
    // /manifest 通常很小（项目名/颜色），但节点小宝 2Mbps 下也需容忍；用专属 45s
    suspend fun listProjects(profile: ConnectionProfile): Result<List<ProjectEntryDto>> =
        runCatching {
            HttpClientFactory.createApi(profile.copy(timeoutMs = 45_000L)).manifest()
        }

    /** 懒加载某项目的会话列表（经 /p/<projectId>/sessions，只读取不复用/不接管） */
    // 项目会话列表承载会话标题/时间/状态，慢速链路（节点小宝 2Mbps）下数据量最大，最容易超时。
    // 用专属 45s，且不依赖用户保存的 timeoutMs（旧连接可能是 10s/20s）。
    suspend fun listSessionsForProject(profile: ConnectionProfile, projectId: String): Result<List<RemoteSession>> =
        runCatching {
            AgentAdapterFactory.create(profile.copy(projectId = projectId, timeoutMs = 45_000L)).listSessions()
                .filter { s -> !s.path.contains("-recovery-") }
        }

    /** 显式接管会话（用户点「接管」按钮才触发；POST /p/<projectId>/takeover-session，body {"name","from"}） */
    /** POST /heartbeat — 远程持有会话存活心跳（serve 端 90s 无心跳自动释放 lease） */
    suspend fun heartbeat(profile: ConnectionProfile, projectId: String?, sessionName: String): Result<Unit> =
        runCatching {
            HttpClientFactory.createApi(profile.copy(projectId = projectId)).heartbeat(
                buildJsonObject { put("name", JsonPrimitive(sessionName)) },
            )
        }

    /** POST /release-session — 主动释放所有权（to 缺省 = 不带 handoff 预约的纯释放；桌面端立即可重新获取） */
    suspend fun releaseOwnership(profile: ConnectionProfile, projectId: String?, sessionName: String): Result<Unit> =
        runCatching {
            HttpClientFactory.createApi(profile.copy(projectId = projectId)).releaseSession(
                buildJsonObject { put("name", JsonPrimitive(sessionName)) },
            )
        }

    suspend fun takeoverSession(profile: ConnectionProfile, projectId: String?, session: RemoteSession): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                HttpClientFactory.createApi(profile.copy(projectId = projectId)).takeoverSession(
                    buildJsonObject { put("name", JsonPrimitive(session.id)) },
                )
            }.map { }
        }

    /** 只读加载会话历史（内部切换 serve 当前会话） */
    suspend fun loadHistory(
        profile: ConnectionProfile,
        session: RemoteSession,
    ): Result<List<RemoteMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            // 会话历史承载完整消息正文，慢速链路（节点小宝 2Mbps）下数据量最大，
            // 用专属 45s 覆盖首拉，避免 loading 卡住或误报超时。
            AgentAdapterFactory.create(profile.copy(timeoutMs = 45_000L)).loadSessionHistory(session)
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

    /** 思考档位命令：POST /submit {"input": "/effort <level>"}，返回 HTTP 状态码（null=网络异常） */
    @Volatile
    var lastEffortError: String? = null

    suspend fun effortCommand(profile: ConnectionProfile, level: String): Int? =
        withContext(Dispatchers.IO) {
            val resp = runCatching {
                HttpClientFactory.createApi(profile).submitRaw(
                    buildJsonObject { put("input", JsonPrimitive("/effort $level")) },
                )
            }
            resp.fold(
                onSuccess = { r ->
                    lastEffortError = if (r.code() >= 400) {
                        runCatching { r.errorBody()?.string() }.getOrNull()
                    } else {
                        null
                    }
                    r.code()
                },
                onFailure = { null },
            )
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
