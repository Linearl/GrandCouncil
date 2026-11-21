package com.grandcouncil.remote.widget

import com.grandcouncil.remote.model.RemoteSession

/**
 * 任务 702 B④：桌面 Widget 会话状态概览（纯数据 + 纯函数，可 JVM 单测）。
 * 最小面：设备数 / 会话总数 / 运行中数（isCurrent——serve 标记的当前回合会话）。
 * 数据由 SessionSummaryWidgetProvider 定期（系统 30min 下限）拉取各连接 /sessions 汇总。
 */
data class WidgetSummary(
    val deviceCount: Int,
    val sessionCount: Int,
    val activeCount: Int,
) {
    companion object {

        /** 按设备的会话列表聚合（设备数 = 列表条数；拉取失败的设备会话按空列表计，设备仍计数） */
        fun ofDevices(sessionsPerDevice: List<List<RemoteSession>>): WidgetSummary = WidgetSummary(
            deviceCount = sessionsPerDevice.size,
            sessionCount = sessionsPerDevice.sumOf { it.size },
            activeCount = sessionsPerDevice.sumOf { list -> list.count { it.isCurrent } },
        )

        /** 主行文案：0 台设备给引导文案，其余给统计 */
        fun headline(s: WidgetSummary): String =
            if (s.deviceCount == 0) "尚未配置设备" else "⚔ ${s.deviceCount} 台设备 · ${s.sessionCount} 会话 · ${s.activeCount} 运行中"

        /** 副行文案：点按行为提示 */
        fun detail(s: WidgetSummary): String =
            if (s.deviceCount == 0) "点按添加连接" else "点按打开 GrandCouncil"
    }
}
