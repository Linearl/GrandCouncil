package com.grandcouncil.remote.ui.sessions

import com.grandcouncil.remote.model.HeldBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * tl538：send 设备级校验两态测试（任务书 4：持有/非持有两态 + 关闭态零行为）。
 *
 * 拦截发生在 send() 网络请求之前（canSendBySnapshot=false 即 return），
 * 故非持有趣谓「零行为」= 不发出任何 submit 请求。
 */
class SendGuardTest {

    // ---- 发送门：持有态放行 ----

    @Test
    fun `持有者 ME 放行发送`() {
        assertTrue(canSendBySnapshot(HeldBy.ME))
    }

    // ---- 发送门：非持有态拦截（零行为）----

    @Test
    fun `非持有 OTHER 拦截`() {
        assertFalse(canSendBySnapshot(HeldBy.OTHER))
    }

    @Test
    fun `空闲 FREE 拦截`() {
        assertFalse(canSendBySnapshot(HeldBy.FREE))
    }

    @Test
    fun `未知 null（未进入会话）拦截`() {
        assertFalse(canSendBySnapshot(null))
    }

    // ---- 失败文案：409 可读引导，其余原样 ----

    @Test
    fun `submit 409 转可读接管引导`() {
        val msg = sendFailureMessage(
            RuntimeException("HTTP 409: write authority held by remote device desktop-1"),
        )
        assertEquals("本设备未持有所有权，点「接管」获取所有权后再发送", msg)
    }

    @Test
    fun `非 409 错误保留原始信息`() {
        val msg = sendFailureMessage(RuntimeException("HTTP 500: internal error"))
        assertEquals("发送失败：HTTP 500: internal error", msg)
    }

    @Test
    fun `空错误给未知错误兜底`() {
        assertEquals("发送失败：未知错误", sendFailureMessage(null))
        assertEquals("发送失败：未知错误", sendFailureMessage(RuntimeException()))
    }
}
