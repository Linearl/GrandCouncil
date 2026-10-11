package com.grandcouncil.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 任务 702 B⑤：系统分享文本规整单测（纯 JVM）。
 */
class SharePayloadTest {

    @Test
    fun `null与空白不预填`() {
        assertNull(SharePayload.normalize(null))
        assertNull(SharePayload.normalize(""))
        assertNull(SharePayload.normalize("   \n\t "))
    }

    @Test
    fun `普通文本trim后原样通过`() {
        assertEquals("分享一段内容", SharePayload.normalize("  分享一段内容 \n"))
    }

    @Test
    fun `超长文本截断到上限并加省略号`() {
        val long = "字".repeat(3000)
        val out = SharePayload.normalize(long)!!
        assertEquals(SharePayload.MAX_LENGTH + 1, out.length) // 2000 字 + 省略号
        assertEquals("…", out.last().toString())
    }

    @Test
    fun `恰好等于上限不截断`() {
        val exact = "字".repeat(SharePayload.MAX_LENGTH)
        assertEquals(exact, SharePayload.normalize(exact))
    }
}
