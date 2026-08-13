package com.grandcouncil.remote.model

/**
 * Agent 类型（计划书 3.2：多 agent 适配层）。
 * v1 只实现 Reasonix；MiMo Code 为 v2 接入目标（opencode 系 serve 协议）。
 */
enum class AgentType(val label: String) {
    REASONIX("Reasonix"),
    MIMO_CODE("MiMo Code"),
}

/**
 * 远端会话（agent 无关模型，UI/Repository 层只见此模型）。
 *
 * 会话所有权语义（计划书 2.2.1）：单 writer lease——
 * App 与桌面版不能同时写同一会话；heldBy=OTHER 时只能只读查看。
 */
data class RemoteSession(
    /** 会话名（serve 返回的 name，无 .jsonl 后缀），作为稳定 id */
    val id: String,
    val title: String,
    val turns: Int,
    val isCurrent: Boolean,
    /** lease 归属：ME（本 serve）/OTHER（桌面版等其他进程）/FREE（空闲） */
    val heldBy: HeldBy,
    /** 来源 agent */
    val agent: AgentType,
    /** serve 返回的会话文件路径（调试/未来操作备用） */
    val path: String,
)

enum class HeldBy { ME, OTHER, FREE }
