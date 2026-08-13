package com.grandcouncil.remote.api

import com.grandcouncil.remote.api.dto.ServeEventDto
import com.grandcouncil.remote.connection.AuthMode
import com.grandcouncil.remote.connection.ConnectionProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
 * TODO(M2)：断线自动重连 + 指数退避 + busy 会话重同步（P0-2 二期）
 */
class SseClient(
    private val client: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 订阅 serve 事件流，解析为 [ServeEventDto] */
    fun events(eventsUrl: String): Flow<ServeEventDto> = callbackFlow {
        val request = Request.Builder().url(eventsUrl).build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                runCatching { json.decodeFromString(ServeEventDto.serializer(), data) }
                    .onSuccess { trySend(it) }
                    .onFailure { /* 未知事件结构，忽略（serve 协议演进容错） */ }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: RuntimeException("SSE connection failed: ${response?.code}"))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }
        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    companion object {
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
