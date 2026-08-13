package com.grandcouncil.remote.api

import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.model.RemoteEvent
import com.grandcouncil.remote.model.RemoteEventType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * OkHttp SSE 事件流客户端（M2 聊天流使用，M1 骨架）。
 *
 * Reasonix serve SSE：GET /events（15s keepalive），事件序列
 * reasoning → text → message → usage → turn_done（实测）。
 *
 * TODO(M2)：断线自动重连 + 指数退避 + last-event-id 游标续传（风险对策）。
 */
class SseClient(
    private val client: OkHttpClient,
) {

    /**
     * 订阅 serve 事件流。
     * [eventsUrl] 完整事件地址（含 token 查询参数，由调用方经 ConnectionProfile 构造）。
     */
    fun events(eventsUrl: String): Flow<RemoteEvent> = callbackFlow {
        val request = Request.Builder().url(eventsUrl).build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                trySend(
                    RemoteEvent(
                        type = when (type) {
                            "reasoning" -> RemoteEventType.REASONING
                            "text" -> RemoteEventType.TEXT
                            "message" -> RemoteEventType.MESSAGE
                            "usage" -> RemoteEventType.USAGE
                            "turn_done" -> RemoteEventType.TURN_DONE
                            "approval", "permission" -> RemoteEventType.APPROVAL
                            else -> RemoteEventType.UNKNOWN
                        },
                        data = data,
                        lastEventId = id,
                    )
                )
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
        fun forProfile(profile: ConnectionProfile): SseClient =
            SseClient(HttpClientFactory.createOkHttp(profile))

        /** 事件流 URL（token 模式附加 ?token=） */
        fun eventsUrl(profile: ConnectionProfile, sessionId: String? = null): String {
            val base = profile.normalizedBaseUrl()
            val url = if (sessionId.isNullOrBlank()) {
                "${base}events"
            } else {
                "${base}events?session=$sessionId"
            }
            return if (profile.authMode == com.grandcouncil.remote.connection.AuthMode.TOKEN &&
                profile.token.isNotBlank()
            ) {
                val sep = if ('?' in url) '&' else '?'
                "$url${sep}token=${profile.token}"
            } else url
        }
    }
}
