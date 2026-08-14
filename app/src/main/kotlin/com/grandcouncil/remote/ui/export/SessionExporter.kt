package com.grandcouncil.remote.ui.export

import com.grandcouncil.remote.model.RemoteMessage
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.model.Role
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 会话导出（md / json 纯函数）。
 * md：可读对话记录（保留代码块原文）；json：结构化消息数组（可再导入/分析）。
 */
object SessionExporter {

    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun toMarkdown(session: RemoteSession?, messages: List<RemoteMessage>): String {
        val sb = StringBuilder()
        sb.append("# ").append(session?.title ?: "会话导出").append("\n\n")
        messages.forEach { m ->
            when (m.role) {
                Role.USER -> sb.append("## 🧑 用户\n\n")
                Role.ASSISTANT -> sb.append("## 🤖 助手\n\n")
                Role.TOOL -> sb.append("## 🔧 工具\n\n")
                Role.NOTICE -> sb.append("## 📌 提示\n\n")
            }
            if (m.reasoning?.isNotBlank() == true) {
                sb.append("> 💭 ").append(m.reasoning.replace("\n", "\n> ")).append("\n\n")
            }
            sb.append(m.content.ifBlank { "…" }).append("\n\n")
            m.toolCalls.forEach { t ->
                sb.append("工具调用 `").append(t.name).append("`").append("\n\n")
            }
        }
        return sb.toString()
    }

    fun toJson(session: RemoteSession?, messages: List<RemoteMessage>): String {
        val arr = messages.map { m ->
            JsonObject(
                buildMap {
                    put("role", JsonPrimitive(m.role.name.lowercase()))
                    put("content", JsonPrimitive(m.content))
                    m.reasoning?.let { put("reasoning", JsonPrimitive(it)) }
                    if (m.toolCalls.isNotEmpty()) {
                        put(
                            "toolCalls",
                            JsonArray(m.toolCalls.map { t ->
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive(t.id),
                                        "name" to JsonPrimitive(t.name),
                                        "arguments" to JsonPrimitive(t.arguments),
                                    ),
                                )
                            }),
                        )
                    }
                    if (m.timestamp != null) {
                        put("timestamp", JsonPrimitive(m.timestamp))
                    } else {
                        put("timestamp", JsonNull)
                    }
                },
            )
        }
        val root = JsonObject(
            mapOf(
                "session" to JsonPrimitive(session?.title ?: ""),
                "exportedAt" to JsonPrimitive(
                    timeFmt.format(Instant.now().atZone(ZoneId.systemDefault())),
                ),
                "messages" to JsonArray(arr),
            ),
        )
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
    }
}
