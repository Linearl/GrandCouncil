package com.grandcouncil.remote.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务 702 A②：设备租约心跳策略单测（纯 JVM）。
 * 锁定「锁屏/切后台接管链不断」依赖的时序不变式——调参前先看这里会不会红。
 */
class LeasePolicyTest {

    @Test
    fun `默认心跳间隔为15秒且对serve90秒TTL安全`() {
        assertEquals(15_000L, LeasePolicy.HEARTBEAT_INTERVAL_MS)
        assertEquals(90_000L, LeasePolicy.SERVE_LEASE_TTL_MS)
        assertTrue(LeasePolicy.isIntervalSafe())
    }

    @Test
    fun `心跳间隔超过TTL三分之一判为不安全（连续丢拍会误释放）`() {
        assertFalse(LeasePolicy.isIntervalSafe(intervalMs = 30_000L)) // 90s/30s：丢 2 拍即触发释放
        assertFalse(LeasePolicy.isIntervalSafe(intervalMs = 90_000L))
        assertFalse(LeasePolicy.isIntervalSafe(intervalMs = 0L))
        assertFalse(LeasePolicy.isIntervalSafe(intervalMs = -1L))
    }

    @Test
    fun `TTL内最多容忍6拍丢失`() {
        assertEquals(6, LeasePolicy.maxMissedBeats())
        assertEquals(3, LeasePolicy.maxMissedBeats(intervalMs = 30_000L))
        assertEquals(0, LeasePolicy.maxMissedBeats(intervalMs = 0L))
    }

    @Test
    fun `后台监听重连退避为5秒乘尝试次数`() {
        assertEquals(5_000L, LeasePolicy.reconnectBackoffMs(1))
        assertEquals(10_000L, LeasePolicy.reconnectBackoffMs(2))
        assertEquals(15_000L, LeasePolicy.reconnectBackoffMs(3))
    }

    @Test
    fun `重连退避对越界输入收敛到合法区间`() {
        assertEquals(5_000L, LeasePolicy.reconnectBackoffMs(0)) // 防御：0 次失败不该出现，按 1 处理
        assertEquals(15_000L, LeasePolicy.reconnectBackoffMs(10)) // 超上限封顶
    }

    @Test
    fun `最大重连尝试为3次（与后台监听既有行为一致）`() {
        assertEquals(3, LeasePolicy.MAX_RECONNECT_ATTEMPTS)
    }
}
