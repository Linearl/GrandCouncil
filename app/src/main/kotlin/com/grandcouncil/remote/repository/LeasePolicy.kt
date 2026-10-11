package com.grandcouncil.remote.repository

/**
 * 任务 702 A②：设备租约（接管链）心跳策略——客户端侧全部时序常量与推导集中此处，
 * 可 JVM 单测。serve 端语义（task 36 Phase 2）：按 device_id 分组，
 * 90s 无心跳自动释放整个设备租约；客户端 onStop→onCleared 主动 release 提前让位。
 *
 * 「锁屏/切后台接管链不断」的行为矩阵（详见交付报告）：
 * - 切后台（进程存活）：ViewModel 未销毁 → 15s 心跳协程继续 → 接管链不断（受 Doze 影响可能延迟，90s TTL 内容忍）
 * - 切后台（进程被 ROM 杀）：心跳停止 → serve 端 90s 后自动释放 → 桌面端恢复可写（降级路径，非断链事故）
 * - 离开会话详情页 / App 退出：onCleared → releaseOwnership + releaseDeviceLease → 桌面端立即恢复可写
 */
object LeasePolicy {

    /** 心跳间隔：15s（task 36 Phase 2 从 30s 收紧，任务书同款） */
    const val HEARTBEAT_INTERVAL_MS = 15_000L

    /** serve 端租约 TTL：90s 无心跳即释放整个设备租约（客户端只读此值做推导，不修改 serve 行为） */
    const val SERVE_LEASE_TTL_MS = 90_000L

    /** 后台 SSE 监听最大重连尝试（避免无限耗电；超过后发「连接已断开」通知） */
    const val MAX_RECONNECT_ATTEMPTS = 3

    /**
     * 不变式：心跳间隔必须严格小于 TTL 的 1/3——
     * 连续丢 2 拍（网络抖动/Doze 短暂延迟）不会误触发 serve 端 90s 释放。
     * 违反即返回 false（单测锁定，防止后续调参调坏接管链）。
     */
    fun isIntervalSafe(intervalMs: Long = HEARTBEAT_INTERVAL_MS, ttlMs: Long = SERVE_LEASE_TTL_MS): Boolean =
        intervalMs in 1..(ttlMs / 3 - 1)

    /** TTL 内最多容忍的丢拍数（推导值：90s / 15s = 6 拍，实际抖动余量 2 拍） */
    fun maxMissedBeats(intervalMs: Long = HEARTBEAT_INTERVAL_MS, ttlMs: Long = SERVE_LEASE_TTL_MS): Int =
        if (intervalMs <= 0) 0 else (ttlMs / intervalMs).toInt()

    /**
     * 后台 SSE 断流重连退避：第 n 次失败后等 5s*n（1→5s、2→10s、3→15s 后放弃并通知）。
     * 既有行为（NotificationMonitor 原实现）抽取为纯函数。
     */
    fun reconnectBackoffMs(failedAttempts: Int): Long =
        5_000L * failedAttempts.coerceIn(1, MAX_RECONNECT_ATTEMPTS)
}
