package com.grandcouncil.remote.ui.sessions

import com.grandcouncil.remote.model.HeldBy

/**
 * tl538：发送的设备级所有权门与失败文案分流（纯函数，JVM 可测）。
 *
 * 背景：GC 端「发送消息」必须建立在设备级所有权之上（用户 2026-10-06 裁决，
 * 只做设备级接管）。send() 的拦截发生在任何网络请求之前——非持有态零请求
 * （关闭态零行为），与 UI 层 readOnly + takeoverThenSend 显式路径一致。
 */

/** 快照层发送门：仅持有者（ME）放行；非持有（OTHER/FREE）与未知（null，未进入会话）一律拦截。 */
fun canSendBySnapshot(heldBy: HeldBy?): Boolean = heldBy == HeldBy.ME

/**
 * send 失败文案分流（任务书 3：失败可读，不得裸报错）：
 * - HTTP 409 = serve 设备级守卫拒绝（快照陈旧/租约已过期的兜底路径）
 *   → 明确引导接管，与非持有拦截文案一致；
 * - 其余错误保留原始信息（发送失败：…）。
 */
fun sendFailureMessage(err: Throwable?): String {
    val msg = err?.message ?: return "发送失败：未知错误"
    return if (msg.contains("HTTP 409")) {
        "本设备未持有所有权，点「接管」获取所有权后再发送"
    } else {
        "发送失败：$msg"
    }
}
