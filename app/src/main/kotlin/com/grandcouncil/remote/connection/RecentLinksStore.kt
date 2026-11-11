package com.grandcouncil.remote.connection

import android.content.Context
import com.grandcouncil.remote.security.CredentialCipher
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 最近链接条目（tl1552 子项A：GC-20 v1 只有纯地址，v2 升级地址+时间+token）。
 * [token] 字段语义随层变化：存盘层=密文（CredentialCipher AES/GCM，失败回退明文）；
 * [RecentLinksStore.list] 读出层=明文（解密失败按原文，兼容明文迁移）。
 */
@Serializable
data class RecentLinkEntry(
    /** 基础地址（无 query；?token= 已拆出） */
    val url: String,
    /** token（存盘=密文；list() 读出=明文；空=无认证） */
    val token: String = "",
    /** 记录时间 epoch ms（0=GC-20 老数据迁移，无时间可显示） */
    val savedAt: Long = 0,
)

/**
 * tl1552 子项A 最近链接（GC-20 v2 规格）：连接页存最近 5 条（私有 SharedPreferences，
 * JSON 列表，对标 zcode 首页最近 5 条链接）。相对 v1 升级：
 * ①地址+时间 ②tl1536 配对 URL（?token=xxx）token 拆出加密落盘 ③清空入口。
 * 新在前、去重（同地址刷新时间/token）、超限丢弃最旧。
 */
class RecentLinksStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gc_recent_links", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    /** 记录一次连接地址（保存成功后调用；兼容带 ?token= 的配对 URL，自动拆 token） */
    fun add(rawUrl: String, nowMs: Long = System.currentTimeMillis()) {
        val parsed = parseServePairingUrl(rawUrl)
        if (parsed.baseUrl.isBlank()) return
        val cipheredToken = if (parsed.token.isBlank()) {
            ""
        } else {
            // 加密边界与 ConnectionStore 同款：失败保留明文，宁可降级不丢凭据
            CredentialCipher.encrypt(parsed.token) ?: parsed.token
        }
        val entry = RecentLinkEntry(url = parsed.baseUrl, token = cipheredToken, savedAt = nowMs)
        write(mergeRecentLink(readEntries(), entry))
        // v2 落盘后清理 v1 明文历史（一次性迁移）
        prefs.edit().remove(KEY_LINKS_V1).apply()
    }

    /** 最近链接（新在前，最多 5 条；token 已解密回明文） */
    fun list(): List<RecentLinkEntry> = readEntries().map { e ->
        if (e.token.isBlank()) e else e.copy(token = CredentialCipher.decrypt(e.token) ?: e.token)
    }

    /** 清空全部最近链接（tl1552 验收①：清空入口） */
    fun clear() {
        write(emptyList())
        prefs.edit().remove(KEY_LINKS_V1).apply()
    }

    /** 读原始条目（token 保持密文；含 GC-20 v1 纯地址历史的一次性迁移） */
    private fun readEntries(): List<RecentLinkEntry> {
        val v2 = prefs.getString(KEY_ENTRIES_V2, null)
        if (v2 != null) {
            return runCatching { json.decodeFromString(serializer, v2) }.getOrDefault(emptyList())
        }
        val legacy = prefs.getString(KEY_LINKS_V1, null) ?: return emptyList()
        // v1 行序即新在前，原样保留顺序；无 token 无时间（UI 显示"—"）
        return legacy.split('\n').filter { it.isNotBlank() }
            .map { RecentLinkEntry(url = it.trim()) }
    }

    private fun write(entries: List<RecentLinkEntry>) {
        prefs.edit().putString(KEY_ENTRIES_V2, json.encodeToString(serializer, entries)).apply()
    }

    companion object {
        private const val KEY_LINKS_V1 = "links"
        private const val KEY_ENTRIES_V2 = "entries_v2"
        private const val MAX = 5
        private val serializer = ListSerializer(RecentLinkEntry.serializer())
    }
}

/**
 * 纯函数（可单测）：新条目置顶合并——同地址（忽略尾斜杠差异）刷新为最新并更新
 * token/时间，其余保序，超限丢弃最旧。
 */
internal fun mergeRecentLink(
    entries: List<RecentLinkEntry>,
    entry: RecentLinkEntry,
    max: Int = 5,
): List<RecentLinkEntry> {
    val key = entry.url.trim().trimEnd('/')
    val rest = entries.filter { it.url.trim().trimEnd('/') != key }
    return (listOf(entry) + rest).take(max)
}
