package com.grandcouncil.remote.api

import com.grandcouncil.remote.api.dto.ServeEventDto
import com.grandcouncil.remote.connection.AuthMode
import com.grandcouncil.remote.connection.ConnectionProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * OkHttp SSE 事件流客户端（P0-2：聊天流式 + 审批）。
 *
 * Reasonix serve SSE：GET /events（keepalive `: ping` 注释行），
 * 事件 JSON：{"kind": "reasoning|text|message|tool_dispatch|tool_result|approval_request|turn_done|..."}。
 *
 * GC-23：断线自动重连 + 指数退避（原 TODO(M2) 前两项）——
 * 重连在本层内循环（1s 起倍增、30s 封顶、收到事件即重置、无限自愈），
 * 断线不再关闭 flow：前台 ViewModel / 后台 NotificationMonitor 的
 * 外层重连（固定 3s / 3 次封顶）降为兑底。耗电：封顶后每 30s 一次
 * 连接尝试，成功即停。TODO 第三项「busy 会话重同步」（断线期间错过的
 * turn_done 等事件补偿）仍开放，需业务层重拉 /history，不在本件范围。
 */
class SseClient(
    private val client: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 订阅 serve 事件流，解析为 [ServeEventDto]。
     *  断线（onFailure/onClosed）不关 flow：退避后自动重建连接（GC-23）。
     *  每轮独立断开信号 Channel（代际隔离）——cancel() 自触发的 onFailure
     *  残留信号只进本轮 channel，不会被下一轮误消费致新连接秒被 cancel；
     *  backoff/current 用原子类（OkHttp 回调线程与重连协程跨线程共享）。 */
    fun events(eventsUrl: String): Flow<ServeEventDto> = callbackFlow {
        val request = Request.Builder().url(eventsUrl).build()
        val backoffMs = java.util.concurrent.atomic.AtomicLong(INITIAL_BACKOFF_MS)
        val current = java.util.concurrent.atomic.AtomicReference<EventSource?>(null)
        // 重连循环（ProducerScope 内 launch；外层调用方的 while(true)/attempts
        // 重连降为兑底，仅在本层自身异常关流时接管）
        val reconnectJob = launch {
            while (true) {
                // 本轮专属断开信号（代际隔离：旧轮残留不跨轮）
                val disconnected = kotlinx.coroutines.channels.Channel<Unit>(
                    kotlinx.coroutines.channels.Channel.CONFLATED,
                )
                val listener = object : EventSourceListener() {
                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        backoffMs.set(INITIAL_BACKOFF_MS) // 收到事件=连接健康，退避重置
                        runCatching { json.decodeFromString(ServeEventDto.serializer(), data) }
                            .onSuccess { trySend(it) }
                            .onFailure { /* 未知事件结构，忽略（serve 协议演进容错） */ }
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        // 不关 flow：发断开信号，由本轮 receive 唤醒退避重建
                        disconnected.trySend(Unit)
                    }

                    override fun onClosed(eventSource: EventSource) {
                        disconnected.trySend(Unit)
                    }
                }
                val es = EventSources.createFactory(client).newEventSource(request, listener)
                current.set(es)
                disconnected.receive()
                es.cancel() // 触发的 onFailure 入本轮 channel（已消费完，废弃无害）
                current.compareAndSet(es, null)
                delay(backoffMs.get())
                backoffMs.updateAndGet { (it * 2).coerceAtMost(MAX_BACKOFF_MS) }
            }
        }
        awaitClose {
            reconnectJob.cancel()
            current.getAndSet(null)?.cancel()
        }
    }

    companion object {
        /** 指数退避参数：1s 起倍增，30s 封顶（收到事件即重置） */
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L

        /** SSE 专用客户端：读超时无限（长连接安静期不能按业务超时断） */
        fun forProfile(profile: ConnectionProfile): SseClient =
            SseClient(
                HttpClientFactory.createOkHttp(profile)
                    .newBuilder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build(),
            )

        /** 事件流 URL（token 模式附加 ?token=） */
        fun eventsUrl(profile: ConnectionProfile): String {
            val base = profile.normalizedBaseUrl()
            val url = "${base}events"
            return if (profile.authMode == AuthMode.TOKEN && profile.token.isNotBlank()) {
                "$url?token=${profile.token}"
            } else url
        }
    }
}
