package com.grandcouncil.remote.widget

import com.grandcouncil.remote.model.AgentType
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteSession
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 任务 702 B④：Widget 会话概要聚合与文案单测（纯 JVM）。
 */
class WidgetSummaryTest {

    private fun session(id: String, isCurrent: Boolean = false) = RemoteSession(
        id = id, title = id, turns = 1, isCurrent = isCurrent,
        heldBy = HeldBy.FREE, agent = AgentType.REASONIX, path = "/s/$id",
    )

    @Test
    fun `多设备聚合——设备数会话数运行中数`() {
        val s = WidgetSummary.ofDevices(
            listOf(
                listOf(session("a"), session("b", isCurrent = true)),
                listOf(session("c"), session("d", isCurrent = true), session("e", isCurrent = true)),
                emptyList(), // 第三台设备拉取失败/无会话：设备仍计数
            ),
        )
        assertEquals(3, s.deviceCount)
        assertEquals(5, s.sessionCount)
        assertEquals(3, s.activeCount)
    }

    @Test
    fun `零设备给引导文案`() {
        val s = WidgetSummary.ofDevices(emptyList())
        assertEquals("尚未配置设备", WidgetSummary.headline(s))
        assertEquals("点按添加连接", WidgetSummary.detail(s))
    }

    @Test
    fun `有设备给统计文案与打开提示`() {
        val s = WidgetSummary.ofDevices(listOf(listOf(session("a")), listOf(session("b", isCurrent = true))))
        assertEquals("⚔ 2 台设备 · 2 会话 · 1 运行中", WidgetSummary.headline(s))
        assertEquals("点按打开 GrandCouncil", WidgetSummary.detail(s))
    }
}
