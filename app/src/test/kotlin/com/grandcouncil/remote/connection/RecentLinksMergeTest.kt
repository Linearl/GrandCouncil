package com.grandcouncil.remote.connection

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * tl1552 子项A：最近链接合并/序列化纯逻辑（存储层 SharedPreferences 不可本地单测，
 * 合并规则与 JSON 编解码在此覆盖——去重置顶 / 超限丢最旧 / v1 兼容字段缺省）。
 */
class RecentLinksMergeTest {

    private fun entry(url: String, savedAt: Long, token: String = "") =
        RecentLinkEntry(url = url, token = token, savedAt = savedAt)

    @Test
    fun `新条目置顶`() {
        val merged = mergeRecentLink(
            listOf(entry("http://a", 1)),
            entry("http://b", 2),
        )
        assertEquals(listOf("http://b", "http://a"), merged.map { it.url })
    }

    @Test
    fun `同地址刷新为最新且更新 token 与时间`() {
        val merged = mergeRecentLink(
            listOf(entry("http://a", 1), entry("http://b", 2)),
            entry("http://a", 9, token = "t"),
        )
        assertEquals(listOf("http://a", "http://b"), merged.map { it.url })
        assertEquals(9, merged[0].savedAt)
        assertEquals("t", merged[0].token)
    }

    @Test
    fun `尾斜杠差异视为同地址`() {
        val merged = mergeRecentLink(
            listOf(entry("http://a/", 1)),
            entry("http://a", 9),
        )
        assertEquals(1, merged.size)
        assertEquals(9, merged[0].savedAt)
    }

    @Test
    fun `超限丢弃最旧（上限 5）`() {
        var list = listOf<RecentLinkEntry>()
        for (i in 1..6) {
            list = mergeRecentLink(list, entry("http://n$i", i.toLong()))
        }
        assertEquals(5, list.size)
        assertEquals("http://n6", list.first().url)
        assertEquals("http://n2", list.last().url)
    }

    @Test
    fun `自定义上限生效`() {
        val merged = mergeRecentLink(
            listOf(entry("http://a", 1), entry("http://b", 2)),
            entry("http://c", 3),
            max = 2,
        )
        assertEquals(listOf("http://c", "http://b"), merged.map { it.url })
    }

    @Test
    fun `JSON 编解码往返保真且缺省字段兼容`() {
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(RecentLinkEntry.serializer())
        val entries = listOf(entry("http://a", 100, token = "t"), entry("http://b", 0))
        val decoded = json.decodeFromString(serializer, json.encodeToString(serializer, entries))
        assertEquals(entries, decoded)
        // GC-20 v1 时代语义：只有 url 字段也能解码（token/savedAt 缺省）
        val legacy = json.decodeFromString(serializer, """[{"url":"http://old"}]""")
        assertEquals(1, legacy.size)
        assertEquals("http://old", legacy[0].url)
        assertEquals("", legacy[0].token)
        assertEquals(0, legacy[0].savedAt)
    }
}
