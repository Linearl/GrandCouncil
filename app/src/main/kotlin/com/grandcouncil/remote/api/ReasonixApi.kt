package com.grandcouncil.remote.api

import com.grandcouncil.remote.api.dto.SessionEntryDto
import com.grandcouncil.remote.api.dto.StatusDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Reasonix serve REST 接口（附录 A：26 端点）。
 * M1 实现会话列表 + 状态握手；其余端点按 M2/M3 里程碑逐步启用，
 * 接口先声明以保证协议差异在 api 层内隔离（计划书 3.2）。
 *
 * 认证：token 模式由 HttpClientFactory 拦截器注入 ?token=；
 * password 模式需先 POST /login 建立 session cookie（M2 细化）。
 */
interface ReasonixApi {

    // ---- 会话管理 ----

    /** GET /sessions — 会话列表（M1 核心） */
    @GET("sessions")
    suspend fun listSessions(): List<SessionEntryDto>

    /** GET /sessions/{id} — Web UI 页面（原生 App 用 /history，见下） */

    /** POST /new — 新建会话（M2） */
    @POST("new")
    suspend fun newSession(): JsonObject

    /** POST /delete-session — 删除会话（M2，需确认弹窗） */
    @POST("delete-session")
    suspend fun deleteSession(@Body body: JsonObject): JsonObject

    // ---- 对话 ----

    /** POST /submit — 发送消息（M2，state-changing 必须 JSON Content-Type，计划书 2.3） */
    @POST("submit")
    suspend fun submit(@Body body: JsonObject): JsonObject

    /** POST /cancel — 取消当前（M2） */
    @POST("cancel")
    suspend fun cancel(@Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    /** GET /history — 对话历史（M2 会话切换加载） */
    @GET("history")
    suspend fun history(@Query("session") sessionId: String?): JsonObject

    /** GET /context — 上下文（M3） */
    @GET("context")
    suspend fun context(@Query("session") sessionId: String?): JsonObject

    // ---- 审批 ----

    /** POST /approve — 审批通过（M2） */
    @POST("approve")
    suspend fun approve(@Body body: JsonObject): JsonObject

    /** POST /answer — 回答审批问题（M2） */
    @POST("answer")
    suspend fun answer(@Body body: JsonObject): JsonObject

    /** POST /bypass — 绕过审批（M2） */
    @POST("bypass")
    suspend fun bypass(@Body body: JsonObject): JsonObject

    // ---- 历史操作（M3：rewind/fork/compact/summarize/checkpoints/branches） ----
    // ---- Goal/Todo（M3：goal/todos） ----

    // ---- 系统 ----

    /** GET /status — serve 状态握手（M1 连接测试第三层） */
    @GET("status")
    suspend fun getStatus(): StatusDto

    /** GET /models — 模型列表（M3） */
    @GET("models")
    suspend fun models(): JsonObject
}
