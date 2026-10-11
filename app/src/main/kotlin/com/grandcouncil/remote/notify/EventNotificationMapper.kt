package com.grandcouncil.remote.notify

import com.grandcouncil.remote.api.dto.ServeEventDto
import com.grandcouncil.remote.api.dto.ServeEventKind

/**
 * 事件 → 通知内容的映射结果（纯数据）。
 * [type] 决定渠道与优先级；[dedupeKey] 用于 5s 冷却去重；[sessionName] 深链进会话。
 */
data class EventNotification(
    val type: NotifyType,
    val title: String,
    val body: String,
    val dedupeKey: String?,
    val sessionName: String?,
)

/**
 * 任务 702 A①：serve 事件 → 系统通知的映射（纯函数，无 Android 依赖，可 JVM 单测）。
 *
 * 覆盖（与 serve 事件流 kind 白名单对齐）：
 * - approval_request → 审批请求（高优先级）
 * - ask_request      → 问答请求
 * - turn_done        → 完成 / 错误（按 outcome）
 * - 其他 kind        → null（不发通知；未知 kind 静默忽略）
 *
 * 文案与去重键保持既有行为逐字不变（从 NotificationMonitor.handleEvent 抽取）。
 */
object EventNotificationMapper {

    fun map(profileName: String, ev: ServeEventDto): EventNotification? = when (ev.kind) {
        ServeEventKind.APPROVAL_REQUEST -> EventNotification(
            type = NotifyType.APPROVAL,
            title = "🛡 Agent 需要审批",
            body = "$profileName：${ev.approval?.tool ?: "工具"} 请求权限（${ev.approval?.id?.take(8) ?: ""}）",
            dedupeKey = "appr-${ev.approval?.id}",
            sessionName = profileName,
        )

        ServeEventKind.ASK_REQUEST -> EventNotification(
            type = NotifyType.QUESTION,
            title = "❓ Agent 需要回答",
            body = "$profileName：${ev.text?.takeIf { it.isNotBlank() } ?: "请回答一个问题"}",
            dedupeKey = "ask-${ev.approval?.id ?: ev.text?.take(12)}",
            sessionName = profileName,
        )

        ServeEventKind.TURN_DONE ->
            if (ev.outcome == "error") {
                EventNotification(
                    type = NotifyType.ERROR,
                    title = "⚠️ 任务出错",
                    body = "$profileName：回合以错误结束",
                    dedupeKey = "err-${System.currentTimeMillis() / 10_000}",
                    sessionName = profileName,
                )
            } else {
                EventNotification(
                    type = NotifyType.COMPLETED,
                    title = "✅ 任务完成",
                    body = "$profileName：agent 已完成回合",
                    dedupeKey = "done-${System.currentTimeMillis() / 10_000}",
                    sessionName = profileName,
                )
            }

        else -> null
    }
}
