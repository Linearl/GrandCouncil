package com.grandcouncil.remote.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 任务 542：连接身份键归一化——同一 serve 只算一台设备。
 * 验收用例直接取自任务正文的等价类清单。
 */
class ConnectionProfileTest {

    private fun profile(baseUrl: String) = ConnectionProfile(
        id = ConnectionProfile.newId(),
        name = "test",
        baseUrl = baseUrl,
    )

    @Test
    fun `尾斜杠不改变身份`() {
        assertEquals(
            profile("http://a:1").identityKey(),
            profile("http://a:1/").identityKey(),
        )
    }

    @Test
    fun `host 大小写不改变身份`() {
        assertEquals(
            profile("http://A:1").identityKey(),
            profile("http://a:1").identityKey(),
        )
    }

    @Test
    fun `http 默认端口 80 省略`() {
        assertEquals(
            profile("http://a:80").identityKey(),
            profile("http://a").identityKey(),
        )
    }

    @Test
    fun `https 默认端口 443 省略`() {
        assertEquals(
            profile("https://a:443").identityKey(),
            profile("https://a").identityKey(),
        )
    }

    @Test
    fun `scheme 不同身份不同`() {
        assertNotEquals(
            profile("http://a").identityKey(),
            profile("https://a").identityKey(),
        )
    }

    @Test
    fun `无 scheme 视作 http`() {
        assertEquals(
            profile("a:1").identityKey(),
            profile("http://a:1").identityKey(),
        )
    }

    @Test
    fun `路径不参与设备身份`() {
        assertEquals(
            profile("http://a:1/").identityKey(),
            profile("http://a:1/some/path").identityKey(),
        )
    }

    @Test
    fun `非默认端口保留`() {
        assertEquals(
            "http://10.0.2.2:18789",
            profile("http://10.0.2.2:18789/").identityKey(),
        )
    }

    @Test
    fun `去重保留首条合并更新保留原id`() {
        val first = profile("http://a:1")
        val second = profile("http://a:1/")
        val third = profile("http://b:2")
        val merged = ConnectionProfile.dedupeByIdentity(listOf(first, second, third))
        assertEquals(2, merged.size)
        assertEquals(first.id, merged[0].id)

        val updated = ConnectionProfile.mergeByIdentity(listOf(first, third), second)
        assertEquals(2, updated.size)
        assertEquals(first.id, updated[0].id)
        assertEquals("http://a:1/", updated[0].baseUrl)
    }

    @Test
    fun `merge 新地址追加`() {
        val first = profile("http://a:1")
        val added = profile("https://b:2")
        val merged = ConnectionProfile.mergeByIdentity(listOf(first), added)
        assertEquals(2, merged.size)
        assertEquals(added.id, merged[1].id)
    }
}
