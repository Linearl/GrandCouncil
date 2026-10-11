package com.grandcouncil.remote.notify

import com.grandcouncil.remote.api.dto.ApprovalEventDto
import com.grandcouncil.remote.api.dto.ServeEventDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务 702 A①：serve 事件 → 系统通知映射的单测（纯 JVM，无 Android 依赖）。
 * 锁定既有文案与去重键行为——后续改文案须同步改这里（通知文案回归网）。
 */
class EventNotificationMapperTest {

    private val device = "我的电脑"

    @Test
    fun `审批请求事件映射为高优先级审批通知`() {
        val n = EventNotificationMapper.map(
            device,
            ServeEventDto(kind = "approval_request", approval = ApprovalEventDto(id = "abc12345xyz", tool = "Bash")),
        )
        assertNotNull(n)
        assertEquals(NotifyType.APPROVAL, n!!.type)
        assertEquals("🛡 Agent 需要审批", n.title)
        assertEquals("我的电脑：Bash 请求权限（abc12345）", n.body) // id 只取前 8 位
        assertEquals("appr-abc12345xyz", n.dedupeKey)
        assertEquals(device, n.sessionName) // 深链进会话
    }

    @Test
    fun `审批事件缺 approval 载荷时走工具兜底文案`() {
        val n = EventNotificationMapper.map(device, ServeEventDto(kind = "approval_request"))
        assertEquals("我的电脑：工具 请求权限（）", n!!.body)
        assertEquals("appr-null", n.dedupeKey) // 既有行为：null 插值锁定
    }

    @Test
    fun `问答事件用事件文本作为通知正文`() {
        val n = EventNotificationMapper.map(device, ServeEventDto(kind = "ask_request", text = "要继续安装吗？"))
        assertEquals(NotifyType.QUESTION, n!!.type)
        assertEquals("❓ Agent 需要回答", n.title)
        assertEquals("我的电脑：要继续安装吗？", n.body)
        assertEquals("ask-要继续安装吗？", n.dedupeKey) // 无 approval 载荷 → 退化为 text 前 12 字符
    }

    @Test
    fun `问答事件文本为空时走兜底文案`() {
        val n = EventNotificationMapper.map(device, ServeEventDto(kind = "ask_request", text = "  "))
        assertEquals("我的电脑：请回答一个问题", n!!.body)
    }

    @Test
    fun `回合正常结束映射为完成通知`() {
        val n = EventNotificationMapper.map(device, ServeEventDto(kind = "turn_done", outcome = "ok"))
        assertEquals(NotifyType.COMPLETED, n!!.type)
        assertEquals("✅ 任务完成", n.title)
        assertEquals("我的电脑：agent 已完成回合", n.body)
    }

    @Test
    fun `回合错误结束映射为错误通知`() {
        val n = EventNotificationMapper.map(device, ServeEventDto(kind = "turn_done", outcome = "error"))
        assertEquals(NotifyType.ERROR, n!!.type)
        assertEquals("⚠️ 任务出错", n.title)
        assertEquals("我的电脑：回合以错误结束", n.body)
    }

    @Test
    fun `未知事件类型不发通知`() {
        assertNull(EventNotificationMapper.map(device, ServeEventDto(kind = "tool_dispatch")))
        assertNull(EventNotificationMapper.map(device, ServeEventDto(kind = "text")))
        assertNull(EventNotificationMapper.map(device, ServeEventDto(kind = "")))
    }

    @Test
    fun `完成与错误通知的去重键带时间片（10s 窗口防轰炸）`() {
        val n = EventNotificationMapper.map(device, ServeEventDto(kind = "turn_done"))
        assertTrue(n!!.dedupeKey!!.startsWith("done-"))
        val e = EventNotificationMapper.map(device, ServeEventDto(kind = "turn_done", outcome = "error"))
        assertTrue(e!!.dedupeKey!!.startsWith("err-"))
    }
}
