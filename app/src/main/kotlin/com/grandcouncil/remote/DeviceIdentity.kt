package com.grandcouncil.remote

import java.util.UUID

/**
 * 进程级设备标识（任务 36 Phase 2）：serve 端心跳按 deviceID 分组，本标识在
 * 进程生命周期内稳定即可——重启后换新 id 无害（旧设备条目 90s 无心跳自动过期）。
 * 不持久化、不读硬件号，零 Context 依赖。
 */
object DeviceIdentity {
    @Volatile
    private var cached: String? = null

    @JvmStatic
    fun id(): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val fresh = UUID.randomUUID().toString()
            cached = fresh
            return fresh
        }
    }
}
