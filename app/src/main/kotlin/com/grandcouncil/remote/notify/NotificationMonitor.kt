package com.grandcouncil.remote.notify

import android.content.Context
import com.grandcouncil.remote.api.SseClient
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A3 后台通知监听：App 进入后台（MainActivity.onStop）时订阅全部连接的 SSE 事件流，
 * 按事件类型发分类通知；前台（onStart）停止监听（前台不弹）。
 * 事件映射：approval_request/ask_request → 审批/问答；turn_done → 完成/错误；SSE 断开 → 连接。
 * 深链：通知携带 open_session（连接名），MainActivity 读取后打开会话栏。
 */
object NotificationMonitor {

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    @Synchronized
    fun start(context: Context) {
        if (scope != null) return
        val app = context.applicationContext
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        job = s.launch {
            val store = ConnectionStore(app)
            val profiles = runCatching { store.profiles.first() }.getOrDefault(emptyList())
            if (profiles.isEmpty()) return@launch
            profiles.forEach { profile ->
                launch { watchProfile(app, profile) }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
    }

    private suspend fun watchProfile(context: Context, profile: ConnectionProfile) {
        val client = SseClient.forProfile(profile)
        var attempts = 0
        while (attempts < 3) { // 后台监听最多重连 3 次，避免无限耗电
            try {
                client.events(SseClient.eventsUrl(profile)).collect { ev ->
                    attempts = 0
                    handleEvent(context, profile, ev)
                }
            } catch (t: Throwable) {
                attempts++
                if (attempts >= 3) {
                    AppNotifications.notify(
                        context,
                        NotifyType.CONNECTION,
                        "连接已断开",
                        "${profile.name}：事件流断开（${t.message ?: "未知原因"}），打开 App 检查",
                        dedupeKey = "conn-${profile.id}",
                    )
                }
                delay(5_000L * attempts)
            }
        }
    }

    private fun handleEvent(context: Context, profile: ConnectionProfile, ev: com.grandcouncil.remote.api.dto.ServeEventDto) {
        // 任务 702 A①：映射逻辑抽至 EventNotificationMapper（纯函数可单测），此处只负责发送
        val n = EventNotificationMapper.map(profile.name, ev) ?: return
        AppNotifications.notify(
            context,
            n.type,
            n.title,
            n.body,
            dedupeKey = n.dedupeKey,
            sessionName = n.sessionName,
        )
    }
}
