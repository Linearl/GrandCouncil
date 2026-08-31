package com.grandcouncil.remote.api

import com.grandcouncil.remote.api.dto.HistoryMessageDto
import com.grandcouncil.remote.api.dto.ProjectEntryDto
import com.grandcouncil.remote.api.dto.ProjectSessionsDto
import com.grandcouncil.remote.api.dto.ModelEntryDto
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
 * 接口先声明以保证协议差异在 api 层内隔离。
 *
 * 认证：token 模式由 HttpClientFactory 拦截器注入 ?token=；
 * password 模式需先 POST /login 建立 session cookie（M2 细化）。
 */
interface ReasonixApi {

    // ---- 网关（单入口远程网关：servepool /manifest）----

    /** GET /manifest — 入口连接下的项目列表（网关 Bearer token 认证） */
    @GET("manifest")
    suspend fun manifest(): List<ProjectEntryDto>

    // ---- 会话管理 ----

    /** GET /sessions — 会话列表（M1 核心） */
    @GET("sessions")
    suspend fun listSessions(): List<SessionEntryDto>

    /** GET /projects — 所有项目的会话列表（单个 serve 浏览全部项目；#9440） */
    @GET("projects")
    suspend fun listProjects(): List<ProjectSessionsDto>

    /** GET /sessions/{id} — Web UI 页面（原生 App 用 /history，见下） */

    /** POST /new — 新建会话（返回 204 无 body；空 body 确保 application/json Content-Type，serve CSRF 守卫要求） */
    @POST("new")
    suspend fun newSession(@Body body: JsonObject = JsonObject(emptyMap())): Unit

    /** POST /delete-session — 删除会话（M2，需确认弹窗） */
    @POST("delete-session")
    suspend fun deleteSession(@Body body: JsonObject): JsonObject

    // ---- 对话 ----

    /** POST /submit — 发送消息（M2，state-changing 必须 JSON Content-Type） */
    @POST("submit")
    suspend fun submit(@Body body: JsonObject): JsonObject

    /** POST /submit — 原始响应（用于 /effort 斜杠命令：204 无 body 需走 Response 拿状态码） */
    @POST("submit")
    suspend fun submitRaw(@Body body: JsonObject): retrofit2.Response<Unit>

    /** POST /cancel — 取消当前（M2） */
    @POST("cancel")
    suspend fun cancel(@Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    /** GET /history — 当前绑定会话的对话历史（只读；切换会话用 /resume） */
    @GET("history")
    suspend fun history(): List<HistoryMessageDto>

    /** POST /resume — 切换到指定会话（body: {"path": "<会话文件绝对路径>"}，来自 /sessions 的 path；返回 204） */
    @POST("resume")
    suspend fun resume(@Body body: JsonObject): Unit

    /** POST /release-session — 释放当前绑定的会话并给目标 writer 预留（body: {"name","to"}；204；非本运行时持有/回合运行中 → 409） */
    @POST("release-session")
    suspend fun releaseSession(@Body body: JsonObject): Unit

    /** POST /takeover-session — 接管一个由 /release-session 预留的会话并重绑 controller（body: {"name","from"}；204；无有效预留 → 409） */
    @POST("takeover-session")
    suspend fun takeoverSession(@Body body: JsonObject): Unit

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

    /** POST /tool-approval-mode — 切换工具审批模式：ask | auto | yolo（PC 端三档） */
    @POST("tool-approval-mode")
    suspend fun toolApprovalMode(@Body body: JsonObject): Unit

    /** POST /plan — plan 模式开关（执行-规划双模型；body: {"on": bool}） */
    @POST("plan")
    suspend fun plan(@Body body: JsonObject): Unit

    /** GET /models — 模型列表（ref/kind/active/default） */
    @GET("models")
    suspend fun models(): List<ModelEntryDto>
}
