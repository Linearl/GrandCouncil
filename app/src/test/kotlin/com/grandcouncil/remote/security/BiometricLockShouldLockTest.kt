package com.grandcouncil.remote.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务 702 A③：生物识别锁置锁判定单测（纯 JVM）。
 * 用户口径：指纹/面容/密码三选一解锁即可（BIOMETRIC_STRONG or DEVICE_CREDENTIAL，
 * 由 isAvailable/authenticate 的组合 authenticator 落实，设备 API 无法 JVM 单测，走真机清单）；
 * 开关默认关闭、设置项开启；开启后冷启动/回前台都必须验证。
 */
class BiometricLockShouldLockTest {

    @Test
    fun `开关开且设备可用且未解锁过——置锁（冷启动核心路径）`() {
        assertTrue(BiometricLock.shouldLock(enabled = true, available = true, sessionUnlocked = false))
    }

    @Test
    fun `开关开但本会话已解锁——不置锁（避免解锁后被开关流重复上锁）`() {
        assertFalse(BiometricLock.shouldLock(enabled = true, available = true, sessionUnlocked = true))
    }

    @Test
    fun `开关关——永不置锁（默认关闭不扰主流程）`() {
        assertFalse(BiometricLock.shouldLock(enabled = false, available = true, sessionUnlocked = false))
        assertFalse(BiometricLock.shouldLock(enabled = false, available = true, sessionUnlocked = true))
    }

    @Test
    fun `设备不可用生物识别与设备凭据——不置锁（否则锁屏无法解开）`() {
        assertFalse(BiometricLock.shouldLock(enabled = true, available = false, sessionUnlocked = false))
    }
}
